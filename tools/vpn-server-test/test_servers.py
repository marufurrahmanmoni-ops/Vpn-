#!/usr/bin/env python3
"""
Real end-to-end test of every VPN Gate server (Linux, needs root + openvpn + curl).

For each server it:
  1. builds the same hardened config the app uses (scripts stripped, AES-128-CBC allowed in data-ciphers),
  2. starts a real OpenVPN tunnel on its own tun device (routes are NOT pushed into the main table;
     traffic from the tunnel address is policy-routed into it, so the test machine keeps working),
  3. measures: handshake time, exit IP + country (through the tunnel), TCP latency through the tunnel,
     download throughput through the tunnel,
  4. tears the tunnel down.

Usage:  sudo python3 test_servers.py [--csv list.csv] [--parallel 8] [--timeout 30]
Without --csv the live list is downloaded from https://www.vpngate.net/api/iphone/
Outputs results.json and results.md in the current directory.
"""
import argparse, base64, json, os, re, shutil, subprocess, sys, tempfile, threading, time, urllib.request
from concurrent.futures import ThreadPoolExecutor

FORBIDDEN = {"up", "down", "route-up", "route-pre-down", "ipchange", "tls-verify", "client-connect",
             "client-disconnect", "learn-address", "auth-user-pass-verify", "script-security", "plugin",
             "management", "management-client", "log", "log-append", "status", "writepid", "cd", "chroot",
             "setenv", "setenv-safe", "daemon", "tmp-dir", "iproute", "dhcp-option-script"}
MODERN = {"AES-256-GCM", "AES-128-GCM", "CHACHA20-POLY1305"}


def fetch_list():
    for url in ("https://www.vpngate.net/api/iphone/", "http://www.vpngate.net/api/iphone/"):
        try:
            req = urllib.request.Request(url, headers={"User-Agent": "FreeGlobalVPN-servertest/1.0"})
            return urllib.request.urlopen(req, timeout=30).read().decode("utf-8", "replace")
        except Exception as e:
            print("list fetch failed:", url, e, file=sys.stderr)
    sys.exit("could not download VPN Gate list")


def parse(csv):
    out, seen, header = [], set(), False
    for line in csv.splitlines():
        line = line.strip()
        if not line or line.startswith("*"):
            continue
        if line.startswith("#"):
            header = True
            continue
        c = line.split(",")
        if not header or len(c) < 15:
            continue
        try:
            cfg = base64.b64decode(c[-1]).decode("utf-8", "replace")
        except Exception:
            continue
        m = re.search(r"^remote\s+(\S+)\s+(\d+)", cfg, re.M)
        p = re.search(r"^proto\s+(\S+)", cfg, re.M)
        if not m:
            continue
        proto = "tcp" if p and p.group(1).startswith("tcp") else "udp"
        sid = f"{c[1]}:{m.group(2)}:{proto}"
        if sid in seen:
            continue
        seen.add(sid)
        out.append(dict(id=sid, host=c[0], ip=c[1], port=int(m.group(2)), proto=proto, cc=c[6],
                        country=c[5], reported_ping=c[3], reported_speed=int(c[4] or 0),
                        sessions=c[7], uptime_h=round(int(c[8] or 0) / 3.6e6, 1), log=c[11], config=cfg))
    return out


def harden(cfg):
    lines, block = [], False
    for raw in cfg.splitlines():
        t = raw.strip()
        if t.startswith("<") and not t.startswith("</"):
            block = True
        if t.startswith("</"):
            block = False
            lines.append(raw)
            continue
        if not block and t.split(" ")[0].lower() in FORBIDDEN:
            continue
        lines.append(raw)
    text = "\n".join(lines) + "\n"
    m = re.search(r"^cipher\s+(\S+)", text, re.M)
    if m and m.group(1).upper() not in MODERN and "data-ciphers" not in text:
        text += f"data-ciphers AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305:{m.group(1)}\n"
        text += f"data-ciphers-fallback {m.group(1)}\n"
    return text


def run(cmd, timeout=20):
    return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)


slot_lock = threading.Lock()
free_slots = []


def test_one(s, timeout):
    with slot_lock:
        slot = free_slots.pop()
    dev, table = f"tun{slot}", 1000 + slot
    work = tempfile.mkdtemp(prefix=f"vg{slot}_")
    res = {k: s[k] for k in s if k != "config"}
    res.update(ok=False, stage="start")
    proc = None
    try:
        up = os.path.join(work, "up.sh")
        with open(up, "w") as f:
            f.write(f"#!/bin/sh\nip rule add from \"$ifconfig_local\" table {table} 2>/dev/null\n"
                    f"ip route replace default dev \"$dev\" table {table}\n"
                    f"echo \"$ifconfig_local\" > {work}/ip\n")
        os.chmod(up, 0o755)
        conf = os.path.join(work, "c.ovpn")
        with open(conf, "w") as f:
            f.write(harden(s["config"]))
        log = os.path.join(work, "log")
        t0 = time.time()
        proc = subprocess.Popen(
            ["openvpn", "--config", conf, "--dev", dev, "--dev-type", "tun", "--route-noexec",
             "--pull-filter", "ignore", "redirect-gateway", "--pull-filter", "ignore", "dhcp-option",
             "--script-security", "2", "--up", up, "--connect-retry-max", "1", "--connect-timeout", "12",
             "--verb", "3", "--log", log],
        )
        done = False
        while time.time() - t0 < timeout:
            time.sleep(0.5)
            txt = open(log, errors="replace").read() if os.path.exists(log) else ""
            if "Initialization Sequence Completed" in txt:
                done = True
                break
            if proc.poll() is not None or "AUTH_FAILED" in txt:
                break
        txt = open(log, errors="replace").read() if os.path.exists(log) else ""
        if not done:
            res["stage"] = "handshake"
            err = [l for l in txt.splitlines() if re.search(r"error|fail|refused|timeout|TLS|Exiting|cipher", l, re.I)]
            res["error"] = (err[-1].split(" ", 5)[-1] if err else "no handshake within %ss" % timeout)[:160]
            return res
        res["handshake_s"] = round(time.time() - t0, 1)
        m = re.search(r"Data Channel: cipher '([^']+)'", txt) or re.search(r"Outgoing Data Channel: Cipher '([^']+)'", txt)
        res["cipher"] = m.group(1) if m else None
        tip = open(os.path.join(work, "ip")).read().strip()
        res["tunnel_ip"] = tip
        res["stage"] = "traffic"
        # exit IP + geolocation through the tunnel
        r = run(["curl", "-s", "--interface", tip, "-m", "10", "https://ipwho.is/"], 15)
        try:
            j = json.loads(r.stdout)
            res["exit_ip"], res["exit_cc"], res["exit_country"] = j.get("ip"), j.get("country_code"), j.get("country")
        except Exception:
            r = run(["curl", "-s", "--interface", tip, "-m", "10", "https://api.ipify.org"], 15)
            res["exit_ip"] = r.stdout.strip() or None
        if not res.get("exit_ip"):
            res["error"] = "tunnel up but no internet through it"
            return res
        # latency through the tunnel (TCP connect to 1.1.1.1:443)
        r = run(["curl", "-s", "-o", "/dev/null", "--interface", tip, "-m", "8", "-w", "%{time_connect}", "https://1.1.1.1/"], 12)
        try:
            res["latency_ms"] = int(float(r.stdout) * 1000)
        except Exception:
            res["latency_ms"] = None
        # download throughput through the tunnel (max 10 s)
        r = run(["curl", "-s", "-o", "/dev/null", "--interface", tip, "-m", "10", "-w", "%{speed_download}",
                 "https://speed.cloudflare.com/__down?bytes=20000000"], 15)
        try:
            res["down_mbps"] = round(float(r.stdout) * 8 / 1e6, 2)
        except Exception:
            res["down_mbps"] = None
        res["ok"] = True
        res["stage"] = "done"
        return res
    except Exception as e:
        res["error"] = f"{type(e).__name__}: {e}"[:160]
        return res
    finally:
        if proc and proc.poll() is None:
            proc.terminate()
            try:
                proc.wait(5)
            except Exception:
                proc.kill()
        subprocess.run(["ip", "rule", "del", "table", str(table)], capture_output=True)
        subprocess.run(["ip", "route", "flush", "table", str(table)], capture_output=True)
        shutil.rmtree(work, ignore_errors=True)
        with slot_lock:
            free_slots.append(slot)
        mark = "OK " if res.get("ok") else "FAIL"
        print(f"[{mark}] {res['cc']} {res['id']:<28} {res.get('handshake_s','-')}s "
              f"exit={res.get('exit_ip','-')}({res.get('exit_cc','-')}) "
              f"lat={res.get('latency_ms','-')}ms down={res.get('down_mbps','-')}Mbps {res.get('error','')}", flush=True)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--csv")
    ap.add_argument("--parallel", type=int, default=8)
    ap.add_argument("--timeout", type=int, default=30)
    a = ap.parse_args()
    if os.geteuid() != 0:
        sys.exit("run as root (needs tun devices and policy routing)")
    csv = open(a.csv, encoding="utf-8").read() if a.csv else fetch_list()
    servers = parse(csv)
    direct = run(["curl", "-s", "-m", "10", "https://api.ipify.org"]).stdout.strip()
    print(f"{len(servers)} servers to test, parallel={a.parallel}, tester IP {direct}", flush=True)
    free_slots.extend(range(10, 10 + a.parallel))
    started = time.time()
    with ThreadPoolExecutor(a.parallel) as ex:
        results = list(ex.map(lambda s: test_one(s, a.timeout), servers))
    for r in results:
        r["ip_changed"] = bool(r.get("exit_ip")) and r.get("exit_ip") != direct
    ok = [r for r in results if r["ok"]]
    summary = dict(tested_at=time.strftime("%Y-%m-%d %H:%M UTC", time.gmtime()), tester_ip=direct,
                   total=len(results), working=len(ok), duration_s=int(time.time() - started))
    json.dump(dict(summary=summary, results=results), open("results.json", "w"), indent=1)

    by_cc = {}
    for r in results:
        d = by_cc.setdefault(r["cc"], [0, 0, r["country"]])
        d[0] += 1
        d[1] += r["ok"]
    md = [f"# VPN Gate server test – {summary['tested_at']}", "",
          f"**{len(ok)} of {len(results)} servers worked** (real OpenVPN tunnel + traffic through it). "
          f"Tester IP: `{direct}`. Duration {summary['duration_s']} s.", "",
          "## By country", "", "| Country | Working / listed |", "|---|---|"]
    for cc, (n, w, name) in sorted(by_cc.items(), key=lambda x: -x[1][1]):
        md.append(f"| {cc} {name} | {w} / {n} |")
    md += ["", "## Working servers (fastest first)", "",
           "| Server | Proto | Handshake | Exit IP (country) | Latency | Download | Uptime | Log policy |",
           "|---|---|---|---|---|---|---|---|"]
    for r in sorted(ok, key=lambda r: -(r.get("down_mbps") or 0)):
        md.append(f"| {r['cc']} {r['id']} | {r['proto']} | {r['handshake_s']} s | {r.get('exit_ip')} ({r.get('exit_cc')}) | "
                  f"{r.get('latency_ms')} ms | {r.get('down_mbps')} Mbps | {r['uptime_h']} h | {r['log']} |")
    md += ["", "## Failed servers", "", "| Server | Stage | Reason |", "|---|---|---|"]
    for r in results:
        if not r["ok"]:
            md.append(f"| {r['cc']} {r['id']} | {r['stage']} | {r.get('error','')} |")
    open("results.md", "w").write("\n".join(md) + "\n")
    print(f"\nDONE: {len(ok)}/{len(results)} working")


if __name__ == "__main__":
    main()
