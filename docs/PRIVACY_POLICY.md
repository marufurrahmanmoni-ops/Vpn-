# Privacy Policy – Free Global VPN

*Template – replace the bracketed parts, have it reviewed for your jurisdiction, and host it at a public
URL (required by Google Play for VPN apps).*

**Effective date:** [DATE]
**Developer:** Md. Marufur Rahman Moni — [contact e-mail] — [address/country]

## 1. Summary
Free Global VPN connects your device to free, public VPN servers operated by third parties. **The app
itself does not collect, store or sell personal data and does not record your browsing history.** The
VPN servers are not operated by us; their operators set their own logging practices (Section 4).

## 2. Data the app does NOT collect
- No account, name, e-mail or phone number.
- No browsing history, DNS queries, or contents of your traffic. The app contains no code that inspects traffic.
- No advertising IDs, analytics, crash-reporting or tracking SDKs.

## 3. Data stored only on your device
- Settings (theme, DNS choice, reconnect options), favourite servers/countries, the last 10 server names you connected to.
- A cached copy of the public server list.
- The list of apps you chose to exclude from the VPN (split tunnelling).
- VPN configuration files you import, and their usernames/passwords, encrypted with AES-256-GCM using a key held in the Android Keystore.
This data never leaves your device, is excluded from Android cloud backup, and is deleted when you uninstall the app or clear its data.

## 4. Third-party VPN servers (important)
- Servers listed from **VPN Gate** (an academic experiment of the University of Tsukuba, Japan) are run by
  volunteers. Each operator declares a logging policy (shown in the app, e.g. "2 weeks"), and VPN Gate keeps
  connection logs to handle abuse. These logs may include your IP address and connection times.
- Your traffic is encrypted between your device and the VPN server. After it leaves the VPN server it travels
  over the internet like normal traffic; the operator could observe traffic that is not otherwise encrypted (e.g. plain HTTP).
- Servers you import are operated by whoever issued the configuration file.
- **We do not claim that any server is "no-log".** Do not use free public servers for sensitive activities.

## 5. Network requests made by the app
| Service | Purpose | Data visible to them |
|---|---|---|
| www.vpngate.net | download the public server list | your IP address, request time |
| ipwho.is, api.ipify.org | show your current public IP / location | your (VPN or real) IP address |
| speed.cloudflare.com | speed test, only when you tap "Run" | your IP address, test data |
| 1.1.1.1 / 8.8.8.8 (TCP 443 handshake) | measure latency | your IP address |
| The selected VPN server | the VPN connection itself | see Section 4 |

## 6. Permissions
- **VPN (BIND_VPN_SERVICE)** – to create the VPN tunnel. Android asks you to confirm.
- **Internet / network state** – to connect and detect network changes.
- **Foreground service & notifications** – to keep the VPN running and show its status.
- **Package visibility (launchable apps only)** – to let you choose apps that bypass the VPN. The list is processed on-device only.

## 7. Children
The app is not directed at children under 13 [or the age required in your country].

## 8. Open source
The app is licensed under the GNU GPL v2. Source code: [URL of your public repository].

## 9. Changes
We will post any changes on this page and update the effective date.

## 10. Contact
[e-mail]
