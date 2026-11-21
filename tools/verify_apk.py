#!/usr/bin/env python3
"""
Pre-release APK checks that mirror what Android's package installer enforces, so an APK that
would show "There was a problem parsing the package" never gets published.

  python3 tools/verify_apk.py app/build/outputs/apk/release/app-release.apk

Checks: manifest structure (activity-alias targets, components exist in dex, exported flags on
intent-filter components), min/target SDK, ABIs, signature, zip alignment and zip64.
Needs ANDROID_HOME (build-tools: aapt2, apksigner, zipalign, dexdump).
"""
import glob, os, re, subprocess, sys, zipfile, tempfile

apk = sys.argv[1]
home = os.environ.get("ANDROID_HOME") or os.path.expanduser("~/Android/Sdk")
bt = sorted(glob.glob(os.path.join(home, "build-tools", "*")))[-1]
tool = lambda n: os.path.join(bt, n)
errors, notes = [], []

def run(*a):
    return subprocess.run(a, capture_output=True, text=True).stdout

# ---- manifest tree
tree = run(tool("aapt2"), "dump", "xmltree", "--file", "AndroidManifest.xml", apk)
elems, cur = [], None
for line in tree.splitlines():
    m = re.match(r"(\s*)E: ([\w-]+)", line)
    if m:
        cur = {"tag": m.group(2), "depth": len(m.group(1)), "attrs": {}}
        elems.append(cur)
        continue
    m = re.match(r"\s*A: (?:http://schemas.android.com/apk/res/android:)?(\w+)(?:\(0x[0-9a-f]+\))?=(.*)", line)
    if m and cur is not None:
        v = m.group(2)
        raw = re.search(r'\(Raw: "([^"]*)"\)', v)
        cur["attrs"][m.group(1)] = raw.group(1) if raw else v.strip('"')
pkg = re.search(r'package="([^"]+)"', tree).group(1)
full = lambda n: (pkg + n) if n.startswith(".") else n

activities = {full(e["attrs"].get("name", "")) for e in elems if e["tag"] == "activity"}
components = []
declared_before = set()
for i, e in enumerate(elems):
    if e["tag"] == "activity":
        declared_before.add(full(e["attrs"].get("name", "")))
    if e["tag"] in ("activity", "activity-alias", "service", "receiver", "provider"):
        name = full(e["attrs"].get("name", ""))
        has_filter = any(x["tag"] == "intent-filter" for x in elems[i + 1:] if x["depth"] > e["depth"])  # approx
        nxt = next((x for x in elems[i + 1:] if x["depth"] <= e["depth"]), None)
        children = elems[i + 1: elems.index(nxt)] if nxt else elems[i + 1:]
        has_filter = any(x["tag"] == "intent-filter" for x in children)
        components.append((e["tag"], name, e["attrs"], has_filter))
        if e["tag"] == "activity-alias":
            t = e["attrs"].get("targetActivity")
            if not t:
                errors.append(f"activity-alias {name} has no android:targetActivity (installer rejects the APK)")
            elif full(t) not in activities:
                errors.append(f"activity-alias {name} targets {t}, which is not a declared activity")
            elif full(t) not in declared_before:
                errors.append(f"activity-alias {name} must come AFTER its target activity {t} in the manifest")
        if has_filter and "exported" not in e["attrs"]:
            errors.append(f"{e['tag']} {name} has an intent-filter but no android:exported (rejected on Android 12+)")

# ---- classes exist in dex
with tempfile.TemporaryDirectory() as d:
    with zipfile.ZipFile(apk) as z:
        dexes = [n for n in z.namelist() if re.match(r"classes\d*\.dex$", n)]
        for n in dexes:
            z.extract(n, d)
    classes = set()
    for n in dexes:
        for m in re.finditer(r"Class descriptor\s*:\s*'L([^;]+);'", run(tool("dexdump"), os.path.join(d, n))):
            classes.add(m.group(1).replace("/", "."))
for tag, name, attrs, _ in components:
    if tag != "activity-alias" and name not in classes and not name.startswith("android."):
        # platform / optional library classes are fine; everything else must be in our dex
        if not name.startswith(("androidx.window", "com.google.android")):
            errors.append(f"{tag} {name} is declared but its class is not in the APK")
app = next((e for e in elems if e["tag"] == "application"), None)
if app and full(app["attrs"].get("name", "")) not in classes:
    errors.append(f"application class {app['attrs'].get('name')} not in dex")

# ---- sdk / abi / signature / zip
badging = run(tool("aapt2"), "dump", "badging", apk)
minsdk = int(re.search(r"minSdkVersion:'(\d+)'", badging).group(1))
notes.append(f"minSdk {minsdk}, targetSdk {re.search(r'targetSdkVersion:.(\d+)', badging).group(1)}")
abis = re.search(r"native-code: (.*)", badging)
notes.append("ABIs " + (abis.group(1) if abis else "none"))
if abis and "arm64-v8a" not in abis.group(1):
    errors.append("no arm64-v8a native code (most phones)")
sig = subprocess.run([tool("apksigner"), "verify", "--verbose", apk], capture_output=True, text=True)
if "Verifies" not in sig.stdout:
    errors.append("signature does not verify: " + sig.stderr.strip()[:200])
else:
    notes.append("signature " + ", ".join(l.split("(")[0].replace("Verified using ", "").strip()
                                       for l in sig.stdout.splitlines() if l.endswith("true")))
if subprocess.run([tool("zipalign"), "-c", "-P", "16", "4", apk], capture_output=True).returncode:
    errors.append("zip is not aligned")
data = open(apk, "rb").read()
if b"PK\x06\x06" in data[-200000:]:
    errors.append("zip64 archive (old installers cannot read it)")

print("APK:", apk)
for n in notes:
    print("  ok:", n)
print(f"  ok: {len(components)} components, {len(classes)} classes")
for e in errors:
    print("  ERROR:", e)
sys.exit(1 if errors else 0)
