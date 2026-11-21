# Free Global VPN (Android)

A native Android VPN app (Kotlin + Jetpack Compose) that connects to **real, free, public VPN servers**
in many countries using **OpenVPN** through Android's `VpnService`.

Developer: **Md. Marufur Rahman Moni**

| | |
|---|---|
| VPN engine | OpenVPN 2.x via [ics-openvpn](https://github.com/schwabe/ics-openvpn) (vendored, GPLv2) |
| Free servers | [VPN Gate](https://www.vpngate.net/) public relay list (University of Tsukuba) + your own imported `.ovpn` files |
| UI | Jetpack Compose, Material 3, dark/light, dotted world map, emoji flags |
| Min / target SDK | 26 (Android 8.0) / 37 |
| License | **GNU GPL v2** (required by ics-openvpn) |

## What is real (nothing is simulated)

* The server list is downloaded live from VPN Gate's public API. Only servers that the API actually
  publishes are shown. Requested countries with no live server are listed as **"Currently unavailable"**.
* "Connected" is shown only when the OpenVPN engine reports `LEVEL_CONNECTED` (handshake done,
  tunnel interface up, routes installed).
* Your **public IP** is re-checked after connecting (ipwho.is → api.ipify.org fallback) so you can see it change.
* Download/upload speeds come from the engine's real byte counters; ping is a live TCP handshake
  through the tunnel; the speed test downloads/uploads real data (speed.cloudflare.com).
* DNS servers shown are read from Android's `LinkProperties` of the VPN network.

## Feature list

* One-tap connect / disconnect, **Quick Connect** (smart selection), country & server selection, search
* Favourites (countries + servers), recent connections
* Automatic server **health checks** (TCP latency probe), ranking by latency + uptime + load + recent failures
* **Smart failover**: if a free server doesn't answer within 25 s, the next best one is tried (up to 4)
* **Auto-reconnect** after drops / network changes; waits (instead of failing) while offline
* **DNS leak protection** (forced resolver through the tunnel) and **IPv6 leak blocking**
* "Block traffic while reconnecting" (persistent tun) + guidance for Android's system **kill switch**
* **Split tunnelling for bKash, Nagad, Rocket, CellFin & banks** – these apps bypass the VPN automatically
  (see "Banking apps" below)
* Live notification with status & traffic (provided by the OpenVPN service)
* Import your own OpenVPN configs (e.g. Proton VPN free plan) – stored **encrypted** with an Android Keystore key
* All saved VPN profiles are AES-256-GCM encrypted (Keystore key); app data is excluded from backups
* No accounts, no ads, no analytics, no tracking SDKs
* About screen with the developer's photo and name

## Banking apps (bKash / Nagad / banks) – "auto pause"

Bangladeshi MFS and banking apps often refuse to work over a foreign VPN IP. Instead of switching the
whole VPN off every time you open them (slow, and it would leave other apps exposed), the app uses
Android's per-app VPN exclusion (`VpnService.Builder.addDisallowedApplication`). Excluded apps **always
use your normal Bangladeshi connection** while the VPN stays on for everything else. On first launch the
app pre-selects installed apps it recognises (bKash, Nagad, Rocket, CellFin, banks, anything with
"bank"/"pay"/"wallet" in its name as a suggestion); you can change the list in
**Settings → Apps that bypass the VPN**.

## Honest limitations of free infrastructure

* VPN Gate servers are **volunteer PCs**. They appear and disappear; some are slow; reviewers report
  that a noticeable share of listed servers fail to connect. The app compensates with health checks,
  ranking by uptime, and automatic failover, but it **cannot guarantee** a stable server in every country.
* Most VPN Gate servers are in **Japan and South Korea**, with others in the US, Vietnam, Thailand,
  Russia and elsewhere. Countries like Bangladesh, UAE, Switzerland or Norway are often absent – the app
  then shows them as *Currently unavailable* rather than faking them.
* Logging: VPN Gate keeps connection logs for abuse handling and each operator declares its own logging
  policy (shown per server). **This app never claims "no logs".**
* Free public servers should not be used for sensitive logins; that is another reason banking apps bypass the VPN.

## Project layout

```
app/                      Compose app (UI, server management, VPN controller)
  data/                   models, providers, cache, ranking, settings, encrypted store
  data/provider/          VPN Gate provider + parser, imported-config provider, config sanitiser
  vpn/                    VpnController (engine state machine, failover), IP/DNS/latency probes, speed test, banking-app detection
  ui/                     theme, components (world map, connect button), screens
openvpn/                  Android library module that builds the vendored ics-openvpn sources
  upstream/               ics-openvpn @ commit in UPSTREAM_COMMIT (OpenVPN3/ASIO/mbedTLS removed – not used)
  src/main/java/...       ProfileEncryption.java (Keystore AES-GCM profile encryption)
docs/                     BUILD.md, SERVER_INTEGRATION.md, TESTING.md, PRIVACY_POLICY.md
```

See **docs/BUILD.md** to build the APK.
