# Server test results – 2026-11-21T21:21:59Z

Every candidate server got a **real OpenVPN tunnel** from a GitHub Actions machine (USA), plus security checks.

* Tested: **795** (live VPN Gate list + VPN Gate servers seen in the last 36 h)
* Worked: **282**
* Good **and** secure: **218**
* Kept in the app: **179** servers in **29** countries (best 40 per country for Japan/Korea)

A server is kept only if: internet works through the tunnel, the IP changes, download ≥ 3 Mbps, handshake ≤ 12 s, server certificate name is `opengw.net` (pinned), TLS ≥ 1.2, RSA ≥ 2048 bit, AES data cipher, plain-HTTP pages arrive unmodified and DNS answers are not forged.

## Kept servers by country

| Country | Kept | Tested | Worked | Good |
|---|---|---|---|---|
| JP Japan | 40 | 157 | 74 | 58 |
| KR Korea Republic of | 40 | 154 | 71 | 61 |
| RU Russian Federation | 26 | 122 | 45 | 26 |
| TH Thailand | 22 | 126 | 25 | 22 |
| US United States | 8 | 61 | 12 | 8 |
| VN Viet Nam | 8 | 44 | 11 | 8 |
| CA Canada | 3 | 8 | 4 | 3 |
| MX Mexico | 3 | 3 | 3 | 3 |
| BR Brazil | 3 | 6 | 3 | 3 |
| FR France | 3 | 5 | 3 | 3 |
| HU Hungary | 2 | 2 | 2 | 2 |
| PE Peru | 2 | 5 | 2 | 2 |
| IN India | 2 | 9 | 2 | 2 |
| AU Australia | 2 | 7 | 3 | 2 |
| ES Spain | 1 | 2 | 2 | 1 |
| VE Venezuela | 1 | 3 | 1 | 1 |
| AR Argentina | 1 | 3 | 1 | 1 |
| UA Ukraine | 1 | 7 | 1 | 1 |
| PH Philippines | 1 | 1 | 1 | 1 |
| GB United Kingdom | 1 | 10 | 2 | 1 |
| CL Chile | 1 | 2 | 1 | 1 |
| PT Portugal | 1 | 1 | 1 | 1 |
| GD Grenada | 1 | 1 | 1 | 1 |
| MA Morocco | 1 | 2 | 1 | 1 |
| ID Indonesia | 1 | 8 | 1 | 1 |
| AE United Arab Emirates | 1 | 2 | 2 | 1 |
| EU European Union | 1 | 2 | 1 | 1 |
| CO Colombia | 1 | 2 | 1 | 1 |
| GE Georgia | 1 | 1 | 1 | 1 |

## Why the others were removed

| Reason | Servers |
|---|---|
| TCP connect timed out | 242 |
| no response from server | 173 |
| server refused the session | 61 |
| slow | 51 |
| tunnel up but no internet through it | 17 |
| connection refused | 11 |
| no tunnel within s | 5 |
| forges or blocks DNS answers | 5 |
| forges or blocks DNS answers; slow | 4 |
| connection reset by server | 2 |
| TLS handshake timed out | 2 |
| slow handshake | 1 |
| modifies plain-HTTP traffic; forges or blocks DNS answers; slow | 1 |
| modifies plain-HTTP traffic; slow | 1 |
| modifies plain-HTTP traffic; forges or blocks DNS answers | 1 |

## Requested countries

Available: AE, AU, CA, ES, FR, GB, IN, JP, KR, US

Not available in this test (shown as *Currently unavailable*): BD, CH, DE, IT, NL, NO, SE, SG, TR

Free volunteer servers change constantly. Re-run *Actions → VPN server test* and rebuild to refresh the list, or enable `tools/vpn-server-test/update-servers.yml.example` for automatic updates.
