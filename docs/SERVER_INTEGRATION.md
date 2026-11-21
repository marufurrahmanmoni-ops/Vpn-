# Server integration

## Infrastructure investigation (summary)

| Source | Cost | Country choice | Protocol | Automatic list | Used |
|---|---|---|---|---|---|
| **VPN Gate** (University of Tsukuba academic experiment) | Free, no account | Yes – whatever volunteers run (mostly JP/KR, also US, VN, TH, RU and others, changes hourly) | OpenVPN TCP/UDP (also L2TP, SSTP, SoftEther) | Yes – public CSV API | ✅ built-in provider |
| Proton VPN free plan | Free account | A limited set of free countries | OpenVPN / WireGuard | No public API for free users (configs are downloaded from the account page) | ✅ via **Import .ovpn** |
| Your own server (e.g. Oracle/Google free-tier VM running OpenVPN) | Free tiers exist, with limits | The VM's region | OpenVPN | You add it | ✅ via Import |
| Community servers | Free with operator's permission | – | OpenVPN | Depends | ✅ via Import, or a new provider class |
| Cloudflare WARP | Free | **No country choice** (nearest PoP) | WireGuard/MASQUE | – | ❌ not useful for location selection; unofficial clients are not sanctioned |

Commercial "free VPN" APIs that require paid keys were deliberately not used.

### What VPN Gate actually offered (snapshot 21 Nov 2026, 17:29 UTC)

96 servers in **8 countries**: Japan 44, South Korea 36, Vietnam 5, Thailand 5, Russia 3, United States 1,
Romania 1, India 1. All 96 configs: AES-128-CBC data cipher, certificate auth (no password), 85 TCP / 11 UDP.

Of the 19 requested countries only **US, JP, IN, KR** were live; GB, DE, FR, NL, CA, SG, AU, CH, SE, NO,
BD, AE, TR, IT, ES were not. The list changes hourly, so the app decides this at runtime – but plan for
Japan/Korea being the reliable core. To add more countries for free, import configs from a free account
(e.g. Proton VPN's free plan) or run your own OpenVPN server on a free-tier cloud VM in that region.

### VPN Gate details

* List endpoint: `https://www.vpngate.net/api/iphone/` (HTTP fallback). CSV columns:
  `HostName, IP, Score, Ping, Speed, CountryLong, CountryShort, NumVpnSessions, Uptime, TotalUsers,
  TotalTraffic, LogType, Operator, Message, OpenVPN_ConfigData_Base64`.
* Each row contains a complete OpenVPN client config (certificate based). Some configs contain a legacy
  `auth-user-pass`; VPN Gate's documented credentials are `vpn` / `vpn`.
* Terms & logging: VPN Gate is an academic research project. Server **operators declare their own
  logging policy** (column `LogType`, shown in the app), and VPN Gate's anti-abuse policy states that
  connection logs are stored. The app therefore shows a disclosure and never claims "no logs".
* Some ISPs/countries block `vpngate.net`. When both endpoints fail, the app keeps the last cached list and
  shows an error instead of inventing servers. Optional: add an official VPN Gate mirror URL to
  `VpnGateProvider.endpoints` (VPN Gate publishes mirror lists by e-mail/website).
* Please respect VPN Gate's usage: do not hammer the API – the app refreshes at most every 2 hours
  (`ServerRepository.STALE_MS`) or when the user taps refresh.

## Architecture

```
ServerProvider (interface)            data/provider/ServerProvider.kt
 ├─ VpnGateProvider                   live public list
 └─ ImportedConfigProvider            user .ovpn files (encrypted SecureStore)
        │  fetch(): ProviderResult(servers, configs)
        ▼
ServerRepository                      merge, disk cache (instant start), health checks, ranking
        ▼
VpnController                         builds an ics-openvpn VpnProfile, starts OpenVPNService,
                                      watchdog + smart failover + reconnect handling
```

### Adding a new provider

1. Create `class MyProvider : ServerProvider` in `data/provider/`:

```kotlin
class MyCommunityProvider : ServerProvider {
    override val id = "mycommunity"                // prefix of every server id
    override val displayName = "My Community VPN"
    override val engine = ServerProvider.Engine.OPENVPN
    override val loggingDisclosure = "Run by … – logs connection time for 7 days."

    override suspend fun fetch(): ProviderResult = withContext(Dispatchers.IO) {
        val json = URL("https://example.org/servers.json").readText()   // must THROW on failure
        val servers = /* parse */ listOf<VpnServer>()                       // id = "mycommunity:<ip>:<port>:<proto>"
        val configs = servers.associate { it.id to /* full .ovpn text */ "" }
        ProviderResult(servers, configs)
    }
    override fun defaultCredentials(server: VpnServer) = null           // or user/pass pair
}
```

2. Register it in `AppContainer.kt`: `providers = listOf(VpnGateProvider(), MyCommunityProvider(), importedProvider)`.
3. If the host uses plain HTTP, add its domain to `res/xml/network_security_config.xml` (prefer HTTPS).

Rules for providers: never return placeholder servers; ids must be stable; country codes are ISO-3166
alpha-2; configs go through `OvpnConfig.sanitize()` (scripts/plugins/management directives are removed)
before reaching the engine.

### WireGuard (optional future engine)

The official `com.wireguard.android:tunnel` library (Apache-2.0, Maven Central) can be added as a
second engine: add `Engine.WIREGUARD`, a `WireGuardController` using `GoBackend`, and branch in
`MainViewModel` on `server.engine`. It was not enabled in v1 because no free WireGuard source offers a
public, keyless, per-country server list.

## Smart selection & stability

`ServerRanking.cost()` (unit-tested) – lower is better:

* our own TCP-handshake latency (or provider ping + 80 ms when not yet probed)
* − bandwidth bonus (max −120), − uptime bonus (max −90; long-running volunteers are more stable)
* + load penalty (sessions), + 400 per failure in the last 30 min, small TCP bonus (passes restrictive networks)
* unreachable servers (probe failed in the last 10 min) are skipped

Connection flow: try best server → 25 s handshake watchdog → on failure mark server failed and try the
next best (same country, or worldwide for Quick Connect), max 4 attempts → honest error.
While connected: the engine itself reconnects after network changes; if an established tunnel can't
recover within 45 s, smart failover switches server. While offline the app waits instead of failing.
