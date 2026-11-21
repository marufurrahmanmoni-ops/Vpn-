# Test plan (real device / emulator)

No mock data is used anywhere in the app. These checks must be run on a real phone (recommended) or
an emulator with internet access. Record results in the table at the bottom.

Automated JVM tests (`./gradlew :app:testDebugUnitTest`) cover the parts that don't need a device:
VPN Gate CSV parsing, config sanitising (script removal), remote/proto parsing, server ranking,
failure penalties, and "Currently unavailable" handling for requested countries. **9/9 pass.**

Real-data checks (no device needed):

```bash
curl -o /tmp/vpngate.csv https://www.vpngate.net/api/iphone/
# parser on the live list (pure JVM)
./gradlew :app:testDebugUnitTest -Pvpngate.snapshot=/tmp/vpngate.csv
# + every live config through the real ics-openvpn ConfigParser/VpnProfile (Robolectric)
./gradlew :app:testDebugUnitTest -Pvpngate.snapshot=/tmp/vpngate.csv -Pvpngate.robolectric=true
```

Result on the VPN Gate list of 21 Nov 2026 (17:29 UTC): all 96 published servers parsed; live countries
JP 44, KR 36, VN 5, TH 5, RU 3, US 1, RO 1, IN 1. (The Robolectric engine test could not be run in the
build sandbox because its runtime jar is served from a blocked mirror – run it on your PC.)

Useful commands while testing:

```bash
adb logcat -s VpnController:* OpenVPN:* "openvpn":* AndroidRuntime:*
adb shell dumpsys connectivity | grep -A3 VPN
```

## 1. Actual VPN connection
1. Fresh install → accept disclosure → wait for the list (Locations shows "updated just now").
2. Tap the big button → accept Android's *Connection request*.
3. ✅ Status goes *Connecting…* → *Connected*, key icon appears in the status bar, a notification shows
   the server and traffic.

## 2. Public IP change
1. Before connecting note the IP on the Home card ("Your real IP is visible").
2. Connect. ✅ After ~2 s the card shows a different IP and the server's country.
3. Cross-check in Chrome: https://ipinfo.io or https://www.whatismyip.com shows the same VPN IP.

## 3. DNS leak protection
1. Connect (DNS setting = Cloudflare). ✅ Home card: "DNS via tunnel: 1.1.1.1, 1.0.0.1".
2. In Chrome open https://dnsleaktest.com → *Extended test*. ✅ Only Cloudflare (or the chosen resolver)
   appears; **no Grameenphone/Robi/Banglalink/your ISP resolvers**.
3. https://ipv6-test.com or https://test-ipv6.com: ✅ no IPv6 address of your ISP is visible.

## 4. Connection speed measurement
1. Connected, open YouTube or download something. ✅ Download/Upload tiles change every ~2 s.
2. Tap *Speed test → Run*. ✅ Shows ↓/↑ Mbps. Compare with fast.com through the VPN (same order of magnitude).
3. ✅ Ping tile shows a value (tunnel RTT) that changes over time.

## 5. Server availability
1. Locations tab: ✅ countries with servers show count + latency; requested countries without servers
   (often Bangladesh, UAE, Norway…) show **"Currently unavailable – no free server online"** and cannot be tapped.
2. Tap refresh: ✅ "updated just now"; servers that stopped being listed disappear.
3. Airplane mode → refresh: ✅ error banner "Could not update…", the last known list stays.

## 6. Reconnection after network interruption
1. Connected on Wi-Fi → turn Wi-Fi off (mobile data on). ✅ *Reconnecting…* then *Connected* again
   (timer continues), public IP is still the VPN IP.
2. Airplane mode for 30 s. ✅ *Waiting for network*; turn it off → reconnects automatically.
3. With "Block traffic while reconnecting" ON, during step 1 open a website: ✅ it waits/fails instead
   of loading over the raw connection.

## 7. Connection failure handling
1. Pick a server marked "failed recently" or a slow country and connect. If it doesn't answer:
   ✅ "… did not respond – trying <next server>… (server 2/4)", eventually *Connected* or a clear error.
2. Turn **Smart failover** off and repeat: ✅ a single attempt then *Connection failed* with the reason.
3. Import a config that needs a password without giving one: ✅ "This server needs a username/password".
4. Deny the Android VPN consent dialog: ✅ "VPN permission is required to connect".

## 8. Battery & memory
1. Connect, keep the screen off for 1 hour with normal background use.
2. *Settings → Battery → App usage*: note % for Free Global VPN. Expected: comparable to other OpenVPN
   apps (the encryption work happens in the OpenVPN native process).
3. `adb shell dumpsys meminfo com.moni.freeglobalvpn` and `com.moni.freeglobalvpn:openvpn`
   (note TOTAL PSS of both processes).
4. Note: the app stops its own polling (ping every 5 s, health checks every 10 min) when it is not visible.

## 9. Banking apps bypass
1. Settings → Apps that bypass the VPN: ✅ bKash / Nagad / banks (if installed) are pre-checked.
2. Connect to Japan. Open bKash: ✅ it works as usual (it uses your BD IP).
3. In Chrome (not excluded) the IP is still the Japanese one.

## 10. Kill switch (system)
1. Android Settings → Network → VPN → ⚙ Free Global VPN → enable *Always-on* + *Block connections without VPN*.
2. Disconnect inside the app: ✅ no app (including excluded ones) can reach the internet until the VPN reconnects.

| # | Test | Device / Android | Result | Notes |
|---|---|---|---|---|
| 1 | Real connection | | | |
| 2 | IP change | | | |
| 3 | DNS leak | | | |
| 4 | Speed | | | |
| 5 | Availability | | | |
| 6 | Reconnect | | | |
| 7 | Failure handling | | | |
| 8 | Battery/memory | | | |
| 9 | Bypass apps | | | |
| 10 | Kill switch | | | |
