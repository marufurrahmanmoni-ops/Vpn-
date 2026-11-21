#!/usr/bin/env python3
"""
Real end-to-end test of free VPN servers, and publisher of the app's "verified good servers" list.

Candidates (all free, no accounts):
  * the live VPN Gate API (https://www.vpngate.net/api/iphone/) – it only shows ~100 servers at a time,
  * VPN Gate servers seen in the last --pool-max-age hours, from an MIT-licensed public mirror that keeps
    every server the API has returned (https://github.com/GeorgeXie2333/vpngate-list-mirror) – thousands
    of servers in 50+ countries,
  * servers that passed the previous run (servers/verified.json), so good servers are re-checked,
  * community / own servers listed in servers/community.json (e.g. an Oracle Cloud Always Free VM).

For each candidate a REAL OpenVPN tunnel is started on its own tun device (routes are not installed in
the main table; traffic from the tunnel address is policy-routed into it), then:
  exit IP through the tunnel (must differ from the tester's IP), TCP latency through the tunnel,
  download throughput through the tunnel. The tunnel is then torn down.

A server is GOOD when: tunnel up, internet works through it, IP changed, download >= --min-mbps,
handshake <= --max-handshake seconds, AND it is SECURE: VPN Gate certificate name pinned to opengw.net
(the stock configs would accept any Let's Encrypt certificate), TLS >= 1.2, RSA key >= 2048 bit, an AES /
ChaCha20 data cipher, plain-HTTP content arrives unmodified, DNS answers are not forged.

Usage (Linux, root, openvpn + curl installed):
  sudo python3 test_servers.py [--pool DIR] [--previous verified.json] [--community community.json]
                               [--parallel 32] [--publish OUTDIR]
"""
import argparse, base64, datetime, hashlib, json, os, re, shutil, subprocess, sys, tempfile, threading, time
import urllib.request
from concurrent.futures import ThreadPoolExecutor

FORBIDDEN = {"up", "down", "route-up", "route-pre-down", "ipchange", "tls-verify", "client-connect",
             "client-disconnect", "learn-address", "auth-user-pass-verify", "script-security", "plugin",
             "management", "management-client", "log", "log-append", "status", "writepid", "cd", "chroot",
             "setenv", "setenv-safe", "daemon", "tmp-dir", "iproute", "dhcp-option-script"}
MODERN = {"AES-256-GCM", "AES-128-GCM", "CHACHA20-POLY1305"}
NOW = datetime.datetime.now(datetime.timezone.utc)


# ----------------------------------------------------------------------------- config helpers
def strip_endpoint(cfg):
    """Config without its proto/remote lines (VPN Gate configs are otherwise identical)."""
    return re.sub(r"^[ \t]*(proto|remote)[ \t].*\n?", "", cfg, flags=re.M)


def body_hash(cfg):
    body = re.sub(r"^#.*$", "", strip_endpoint(cfg), flags=re.M)
    return hashlib.sha256(re.sub(r"\s+", "", body).encode()).hexdigest()


def endpoint(cfg):
    m = re.search(r"^[ \t]*remote[ \t]+(\S+)[ \t]+(\d+)(?:[ \t]+(\S+))?", cfg, re.M)
    if not m:
        return None
    pm = re.search(r"^[ \t]*proto[ \t]+(\S+)", cfg, re.M)
    p = m.group(3) or (pm.group(1) if pm else "udp")
    return m.group(1), int(m.group(2)), ("tcp" if p.lower().startswith("tcp") else "udp")


VPNGATE_CERT_NAME = "opengw.net"
SAFE_DATA_CIPHERS = {"AES-256-GCM", "AES-128-GCM", "CHACHA20-POLY1305", "AES-256-CBC", "AES-192-CBC", "AES-128-CBC"}


def harden(cfg, vpngate=False):
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
    if vpngate and "verify-x509-name" not in text:
        # VPN Gate configs trust the whole Let's Encrypt root without a name check, so ANY Let's Encrypt
        # certificate would be accepted (MITM). Require the VPN Gate server certificate name.
        text += f"verify-x509-name {VPNGATE_CERT_NAME} name\nremote-cert-tls server\n"
    return text


# ----------------------------------------------------------------------------- candidate sources
def fetch(url, timeout=30):
    req = urllib.request.Request(url, headers={"User-Agent": "FreeGlobalVPN-servertest/2.0"})
    return urllib.request.urlopen(req, timeout=timeout).read()


def from_api(csv_text):
    out, header = [], False
    for line in csv_text.splitlines():
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
        ep = endpoint(cfg)
        if not ep:
            continue
        out.append(dict(source="api", ip=c[1], port=ep[1], proto=ep[2], cc=c[6].upper(), country=c[5],
                        host=c[0], uptime_h=round(int(c[8] or 0) / 3.6e6, 1), log=c[11] or None,
                        score=int(c[2] or 0), config=cfg))
    return out


def from_pool(pool_dir, max_age_h, cap_per_country):
    path = os.path.join(pool_dir, "pool", "servers.json")
    if not os.path.exists(path):
        return []
    data = json.load(open(path))
    rows = []
    for s in data.get("servers", []):
        seen = s.get("last_seen_at") or s.get("first_seen_at")
        try:
            age = (NOW - datetime.datetime.fromisoformat(seen.replace("Z", "+00:00"))).total_seconds() / 3600
        except Exception:
            continue
        probe = (s.get("tcp_probe") or {}).get("last_attempt_status")
        if age > max_age_h or probe == "unreachable":
            continue
        rows.append((s, age, probe))
    # Japan/Korea have thousands of servers: keep the most promising few per country.
    rows.sort(key=lambda r: (r[0].get("present_in_latest_source") is not True, r[2] != "reachable", r[1], -(r[0].get("score") or 0)))
    per, out = {}, []
    for s, age, probe in rows:
        cc = (s.get("country_code") or "").upper()
        if per.get(cc, 0) >= cap_per_country:
            continue
        cfg_path = os.path.join(pool_dir, (s.get("config") or {}).get("path", ""))
        try:
            cfg = base64.b64decode(json.load(open(cfg_path))["openvpn_config_base64"]).decode("utf-8", "replace")
        except Exception:
            continue
        ep = endpoint(cfg)
        if not ep:
            continue
        per[cc] = per.get(cc, 0) + 1
        out.append(dict(source="pool", ip=s["ip"], port=ep[1], proto=ep[2], cc=cc, country=s.get("country_name"),
                        host=s.get("hostname"), uptime_h=None, log=None, score=s.get("score") or 0, config=cfg))
    return out


def from_previous(prev):
    out = []
    tmpl = prev.get("template")
    for s in prev.get("servers", []):
        cfg = s.get("config") or (tmpl and f"proto {s['proto']}\nremote {s['ip']} {s['port']}\n{tmpl}")
        if cfg:
            out.append(dict(source="previous", ip=s["ip"], port=s["port"], proto=s["proto"], cc=s["cc"],
                            country=s.get("country"), host=s.get("host"), uptime_h=s.get("uptime_h"),
                            log=s.get("log"), score=0, config=cfg))
    return out


def from_community(path):
    out = []
    for e in json.load(open(path)).get("servers", []):
        cfg = e.get("config", "")
        ep = endpoint(cfg)
        if not ep:
            continue
        out.append(dict(source="community", ip=ep[0], port=ep[1], proto=ep[2], cc=e.get("cc", "ZZ").upper(),
                        country=e.get("country"), host=e.get("name"), uptime_h=None, log=e.get("log_policy"),
                        operator=e.get("operator"), score=0, config=cfg, user=e.get("username"), password=e.get("password")))
    return out


# ----------------------------------------------------------------------------- the real test
def run(cmd, timeout=20):
    try:
        return subprocess.run(cmd, capture_output=True, text=True, timeout=timeout)
    except subprocess.TimeoutExpired:
        return subprocess.CompletedProcess(cmd, 1, "", "timeout")


def classify(txt, timeout):
    rules = [
        (r"AUTH_FAILED", "server refused the session (AUTH_FAILED: full, or blocks data-centre IPs)"),
        (r"Connection refused|ECONNREFUSED", "connection refused (server offline / port closed)"),
        (r"TCP: connect to .* failed|Connection timed out|ETIMEDOUT|Network is unreachable", "TCP connect timed out (server offline)"),
        (r"TLS key negotiation failed|TLS handshake failed", "TLS handshake timed out"),
        (r"VERIFY X509NAME ERROR", "certificate name mismatch (possible impersonation)"),
        (r"VERIFY KU ERROR|VERIFY EKU ERROR", "server certificate not valid for a VPN server"),
        (r"VERIFY ERROR|certificate verify failed", "server certificate rejected"),
        (r"OPTIONS ERROR: failed to negotiate cipher", "cipher negotiation failed"),
        (r"Connection reset|ECONNRESET", "connection reset by server"),
        (r"Options error|Unrecognized option", "config option error"),
        (r"connect-retry-max .* unsuccessful|SIGUSR1\[soft", "no response from server (offline or blocked)"),
    ]
    for pat, why in rules:
        if re.search(pat, txt, re.I):
            return why
    return f"no tunnel within {timeout}s"


def security_checks(log, tip):
    """Checks that the tunnel itself is strong and that the exit server does not tamper with traffic."""
    sec, problems = {}, []
    m = re.search(r"Control Channel: (TLSv[\d.]+), cipher \S+ (\S+), peer certificate: (\d+) bits? (\w+)", log)
    if m:
        sec.update(tls=m.group(1), tls_cipher=m.group(2), key_bits=int(m.group(3)), key_type=m.group(4))
        if m.group(1) not in ("TLSv1.2", "TLSv1.3"):
            problems.append(f"old TLS {m.group(1)}")
        if m.group(4).upper() == "RSA" and int(m.group(3)) < 2048:
            problems.append(f"weak {m.group(3)}-bit RSA key")
    else:
        problems.append("could not confirm TLS parameters")
    m = re.search(r"Data Channel: cipher '([^']+)'", log) or re.search(r"Data Channel: using negotiated cipher '([^']+)'", log)
    if m:
        sec["data_cipher"] = m.group(1).upper()
        if sec["data_cipher"] not in SAFE_DATA_CIPHERS:
            problems.append(f"weak data cipher {sec['data_cipher']}")
    cn = re.findall(r"VERIFY OK: depth=0, (?:.*?)CN=([^,\s]+)", log)
    if cn:
        sec["server_cn"] = cn[-1]
    # Traffic tampering: a plain-HTTP page with known content must arrive unmodified …
    body = run(["curl", "-s", "--interface", tip, "-m", "8", "http://detectportal.firefox.com/success.txt"], 12).stdout
    sec["http_untouched"] = body.strip() == "success"
    if not sec["http_untouched"]:
        problems.append("modifies plain-HTTP traffic")
    # … and DNS answers must not be forged (the app sends DNS to 1.1.1.1 through the tunnel).
    dns = run(["dig", "+short", "+time=3", "+tries=1", "-b", tip, "@1.1.1.1", "one.one.one.one", "A"], 10).stdout.split()
    sec["dns_untouched"] = bool(dns) and set(dns) <= {"1.1.1.1", "1.0.0.1"}
    if not sec["dns_untouched"]:
        problems.append("forges or blocks DNS answers")
    sec["https_valid"] = True  # every HTTPS request above verified the site's certificate
    sec["problems"] = problems
    return sec


def openvpn_probe(ip, port, proto, timeout=3.0):
    """Same check as the app's OpenVpnProbe: send P_CONTROL_HARD_RESET_CLIENT_V2, expect SERVER_V2."""
    import socket, struct
    pkt = bytes([7 << 3]) + os.urandom(8) + b"\x00" + b"\x00\x00\x00\x00"
    try:
        if proto == "tcp":
            with socket.create_connection((ip, port), timeout) as so:
                so.settimeout(timeout)
                so.sendall(struct.pack(">H", len(pkt)) + pkt)
                hdr = so.recv(3)
                while len(hdr) < 3:
                    more = so.recv(3 - len(hdr))
                    if not more:
                        return False
                    hdr += more
                return 10 <= struct.unpack(">H", hdr[:2])[0] <= 2048 and hdr[2] >> 3 == 8
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as so:
            so.settimeout(timeout)
            for _ in range(2):
                so.sendto(pkt, (ip, port))
                try:
                    data, _ = so.recvfrom(2048)
                    if len(data) >= 10 and data[0] >> 3 == 8:
                        return True
                except socket.timeout:
                    pass
    except OSError:
        return False
    return False


slot_lock = threading.Lock()
free_slots = []


def test_one(s, a):
    with slot_lock:
        slot = free_slots.pop()
    dev, table = f"tun{slot}", 1000 + slot
    work = tempfile.mkdtemp(prefix=f"vg{slot}_")
    res = {k: v for k, v in s.items() if k not in ("config", "user", "password")}
    res.update(ok=False, good=False, stage="handshake")
    proc = None
    res["probe_ok"] = openvpn_probe(s["ip"], s["port"], s["proto"])
    try:
        up = os.path.join(work, "up.sh")
        with open(up, "w") as f:
            f.write(f"#!/bin/sh\nip rule add from \"$ifconfig_local\" table {table} 2>/dev/null\n"
                    f"ip route replace default dev \"$dev\" table {table}\n"
                    f"echo \"$ifconfig_local\" > {work}/ip\n")
        os.chmod(up, 0o755)
        conf = os.path.join(work, "c.ovpn")
        with open(conf, "w") as f:
            f.write(harden(s["config"], vpngate=s["source"] != "community"))
        args = ["openvpn", "--config", conf, "--dev", dev, "--dev-type", "tun", "--route-noexec",
                "--pull-filter", "ignore", "redirect-gateway", "--pull-filter", "ignore", "dhcp-option",
                "--pull-filter", "ignore", "route-ipv6", "--pull-filter", "ignore", "ifconfig-ipv6",
                "--script-security", "2", "--up", up, "--connect-retry-max", "1", "--connect-timeout", "10",
                "--verb", "3", "--log", os.path.join(work, "log")]
        auth = s.get("user") or ("vpn" if re.search(r"^\s*auth-user-pass\s*$", s["config"], re.M) else None)
        if auth:
            with open(os.path.join(work, "auth"), "w") as f:
                f.write(f"{auth}\n{s.get('password') or 'vpn'}\n")
            args += ["--auth-user-pass", os.path.join(work, "auth")]
        t0 = time.time()
        proc = subprocess.Popen(args)
        log = os.path.join(work, "log")
        done = False
        while time.time() - t0 < a.timeout:
            time.sleep(0.5)
            txt = open(log, errors="replace").read() if os.path.exists(log) else ""
            if "Initialization Sequence Completed" in txt:
                done = True
                break
            if proc.poll() is not None or "AUTH_FAILED" in txt:
                break
        txt = open(log, errors="replace").read() if os.path.exists(log) else ""
        if not done:
            res["error"] = classify(txt, a.timeout)
            return res
        res["handshake_s"] = round(time.time() - t0, 1)
        tip = open(os.path.join(work, "ip")).read().strip()
        res["stage"] = "traffic"
        exit_ip = run(["curl", "-s", "--interface", tip, "-m", "10", "https://api.ipify.org"], 14).stdout.strip()
        if not re.match(r"^[0-9a-fA-F.:]+$", exit_ip or ""):
            res["error"] = "tunnel up but no internet through it"
            return res
        res["exit_ip"] = exit_ip
        res["ip_changed"] = exit_ip != a.tester_ip
        r = run(["curl", "-s", "-o", "/dev/null", "--interface", tip, "-m", "8", "-w", "%{time_connect}", "https://1.1.1.1/"], 12)
        try:
            res["latency_ms"] = int(float(r.stdout) * 1000) or None
        except ValueError:
            res["latency_ms"] = None
        r = run(["curl", "-s", "-o", "/dev/null", "--interface", tip, "-m", "8", "-w", "%{speed_download}",
                 f"https://speed.cloudflare.com/__down?bytes={a.speed_bytes}"], 12)
        try:
            res["down_mbps"] = round(float(r.stdout) * 8 / 1e6, 2)
        except ValueError:
            res["down_mbps"] = 0.0
        res["ok"] = True
        res["stage"] = "done"
        res["security"] = sec = security_checks(txt, tip)
        why = list(sec["problems"])
        if not res["ip_changed"]:
            why.append("IP did not change")
        if res["down_mbps"] < a.min_mbps:
            why.append(f"slow ({res['down_mbps']} Mbps < {a.min_mbps})")
        if res["handshake_s"] > a.max_handshake:
            why.append(f"slow handshake ({res['handshake_s']} s)")
        res["good"] = not why
        if why:
            res["error"] = "; ".join(why)
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
        mark = "GOOD" if res.get("good") else ("WORK" if res.get("ok") else "FAIL")
        print(f"[{mark}] {res['cc']} {res['ip']}:{res['port']}/{res['proto']:<3} {res['source']:<9} "
              f"hs={res.get('handshake_s', '-')}s lat={res.get('latency_ms', '-')}ms "
              f"down={res.get('down_mbps', '-')}Mbps {res.get('error', '')}", flush=True)


# ----------------------------------------------------------------------------- main
def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--csv", help="use this VPN Gate API response instead of downloading it")
    ap.add_argument("--pool", help="directory of a clone of the vpngate-list-mirror repository")
    ap.add_argument("--pool-max-age", type=float, default=36, help="hours since the server was last listed")
    ap.add_argument("--cap", type=int, default=120, help="max pool candidates per country")
    ap.add_argument("--previous", help="previous verified.json (file or URL)")
    ap.add_argument("--community", help="community.json with own/community servers")
    ap.add_argument("--parallel", type=int, default=32)
    ap.add_argument("--timeout", type=int, default=25)
    ap.add_argument("--min-mbps", type=float, default=3.0)
    ap.add_argument("--max-handshake", type=float, default=12.0)
    ap.add_argument("--speed-bytes", type=int, default=3_000_000)
    ap.add_argument("--max-per-country", type=int, default=40, help="good servers kept per country in verified.json")
    ap.add_argument("--publish", help="output directory for verified.json / results.md / results.json")
    ap.add_argument("--print-verified", action="store_true", help="also print verified.json (gzip+base64) to stdout")
    a = ap.parse_args()
    if os.geteuid() != 0:
        sys.exit("run as root (needs tun devices and policy routing)")

    cands, template = [], None
    try:
        csv = open(a.csv, encoding="utf-8").read() if a.csv else fetch("https://www.vpngate.net/api/iphone/").decode("utf-8", "replace")
        api = from_api(csv)
        cands += api
        if api:
            template = strip_endpoint(api[0]["config"])
        print(f"live API: {len(api)} servers")
    except Exception as e:
        print("live API unavailable:", e)
    if a.pool:
        pool = from_pool(a.pool, a.pool_max_age, a.cap)
        cands += pool
        if pool and not template:
            template = strip_endpoint(pool[0]["config"])
        print(f"mirror pool: {len(pool)} candidates")
    prev = None
    if a.previous:
        try:
            prev = json.loads(fetch(a.previous) if a.previous.startswith("http") else open(a.previous).read())
            p = from_previous(prev)
            cands += p
            template = template or prev.get("template")
            print(f"previous good: {len(p)} servers")
        except Exception as e:
            print("no previous list:", e)
    if a.community and os.path.exists(a.community):
        c = from_community(a.community)
        cands += c
        print(f"community: {len(c)} servers")

    seen, servers = set(), []
    for s in cands:  # first occurrence wins (API > pool > previous > community)
        key = (s["ip"], s["port"], s["proto"])
        if key not in seen:
            seen.add(key)
            servers.append(s)
    a.tester_ip = run(["curl", "-s", "-m", "10", "https://api.ipify.org"]).stdout.strip()
    print(f"{len(servers)} unique servers to test, parallel={a.parallel}, tester IP {a.tester_ip}", flush=True)
    free_slots.extend(range(10, 10 + a.parallel))
    started = time.time()
    with ThreadPoolExecutor(a.parallel) as ex:
        results = list(ex.map(lambda s: test_one(s, a), servers))
    retry = [i for i, r in enumerate(results) if not r["ok"] and r.get("error", "").startswith(("no response", "TLS handshake", "no tunnel"))]
    if retry:
        print(f"\nRetrying {len(retry)} servers that timed out…", flush=True)
        with ThreadPoolExecutor(a.parallel) as ex:
            again = list(ex.map(lambda i: test_one(servers[i], a), retry))
        for i, r in zip(retry, again):
            if r["ok"]:
                results[i] = r

    good = [(s, r) for s, r in zip(servers, results) if r["good"]]
    good.sort(key=lambda sr: -(sr[1].get("down_mbps") or 0))
    per, keep = {}, []
    for s, r in good:
        if s["source"] != "community" and per.get(s["cc"], 0) >= a.max_per_country:
            continue
        per[s["cc"]] = per.get(s["cc"], 0) + 1
        keep.append((s, r))

    stamp = NOW.strftime("%Y-%m-%dT%H:%M:%SZ")
    th = body_hash(template) if template else None
    pub = []
    for s, r in keep:
        e = dict(ip=s["ip"], port=s["port"], proto=s["proto"], cc=s["cc"], country=s.get("country"),
                 host=s.get("host"), down_mbps=r.get("down_mbps"), latency_ms=r.get("latency_ms"),
                 handshake_s=r.get("handshake_s"), uptime_h=s.get("uptime_h"), log=s.get("log"),
                 operator=s.get("operator"), source=s["source"], verified_at=stamp,
                 tls=r["security"].get("tls"), data_cipher=r["security"].get("data_cipher"))
        if s["source"] == "community" or body_hash(s["config"]) != th:
            e["config"] = s["config"]  # only when it differs from the shared VPN Gate template
            if s.get("user"):
                e["username"], e["password"] = s["user"], s.get("password")
        pub.append(e)
    summary = dict(generated_at=stamp, tester_ip=a.tester_ip, tested=len(results),
                   working=sum(r["ok"] for r in results), good=len(good), published=len(pub),
                   countries=len({e["cc"] for e in pub}), duration_s=int(time.time() - started),
                   criteria=dict(min_mbps=a.min_mbps, max_handshake_s=a.max_handshake, ip_must_change=True,
                                 vpngate_cert_name=VPNGATE_CERT_NAME, tls_min="TLSv1.2", rsa_min_bits=2048,
                                 http_untouched=True, dns_untouched=True))
    pr = [(r.get("probe_ok"), r["ok"]) for r in results if "probe_ok" in r]
    agree = {k: sum(1 for p, o in pr if (p, o) == k) for k in [(True, True), (True, False), (False, True), (False, False)]}
    summary["probe_vs_tunnel"] = {f"probe={k[0]},tunnel={k[1]}": v for k, v in agree.items()}
    print("PROBE-AGREEMENT", json.dumps(summary["probe_vs_tunnel"]))
    print(f"\nDONE: {summary['working']}/{summary['tested']} work, {summary['good']} good, "
          f"{len(pub)} published in {summary['countries']} countries")

    out = a.publish or "."
    os.makedirs(out, exist_ok=True)
    json.dump(dict(schema=1, summary=summary, template=template, servers=pub),
              open(os.path.join(out, "verified.json"), "w"), separators=(",", ":"))
    json.dump(dict(summary=summary, results=results), open(os.path.join(out, "results.json"), "w"), indent=1)

    by = {}
    for s, r in zip(servers, results):
        d = by.setdefault(s["cc"], [s.get("country") or s["cc"], 0, 0, 0])
        d[1] += 1
        d[2] += r["ok"]
        d[3] += r["good"]
    reasons = {}
    for r in results:
        if not r["good"]:
            k = re.sub(r"\d+(\.\d+)?", "N", r.get("error", "?"))
            reasons[k] = reasons.get(k, 0) + 1
    md = [f"# Verified free VPN servers – {stamp}", "",
          f"Tested **{summary['tested']}** servers with a real OpenVPN tunnel: **{summary['working']} worked**, "
          f"**{summary['good']} were good** (internet through the tunnel, IP changed, ≥ {a.min_mbps} Mbps, "
          f"handshake ≤ {a.max_handshake} s, server certificate name verified, TLS ≥ 1.2, RSA ≥ 2048 bit, "
          f"strong data cipher, no HTTP or DNS tampering). Published **{len(pub)}** in **{summary['countries']}** countries. "
          f"Tester IP `{a.tester_ip}` (GitHub Actions); duration {summary['duration_s']} s.", "",
          "## By country", "", "| Country | Tested | Worked | Good |", "|---|---|---|---|"]
    for cc, (name, n, w, g) in sorted(by.items(), key=lambda x: (-x[1][3], x[0])):
        md.append(f"| {cc} {name} | {n} | {w} | {g} |")
    md += ["", "## Why the rest were left out", "", "| Reason | Servers |", "|---|---|"]
    md += [f"| {k} | {v} |" for k, v in sorted(reasons.items(), key=lambda x: -x[1])]
    md += ["", "## Published (fastest first, first 150)", "", "| Server | Proto | Download | Latency* | Handshake | Source |",
           "|---|---|---|---|---|---|"]
    for e in pub[:150]:
        md.append(f"| {e['cc']} {e['ip']}:{e['port']} | {e['proto']} | {e['down_mbps']} Mbps | {e['latency_ms']} ms | "
                  f"{e['handshake_s']} s | {e['source']} |")
    md += ["", "*Latency is measured from the GitHub runner (USA), not from Bangladesh. The app measures its own latency."]
    open(os.path.join(out, "results.md"), "w").write("\n".join(md) + "\n")
    if a.print_verified:
        import gzip, textwrap
        blob = base64.b64encode(gzip.compress(open(os.path.join(out, "verified.json"), "rb").read())).decode()
        print("===VERIFIED-BEGIN===")
        for line in textwrap.wrap(blob, 4000):
            print("V:" + line)
        print("===VERIFIED-END===")


if __name__ == "__main__":
    main()
