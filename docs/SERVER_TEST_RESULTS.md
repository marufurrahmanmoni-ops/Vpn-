# Server test results – 2026-11-21T22:38:02Z

Every candidate server got a **real OpenVPN tunnel** from a GitHub Actions machine (USA), plus security checks.

* Tested: **738** (live VPN Gate list + VPN Gate servers seen in the last 36 h)
* Worked: **265**
* Good **and** secure: **176**
* Shipped in the app: **160** servers in **28** countries (best 40 per country)

Kept only if: internet works through the tunnel, the IP changes, download ≥ 3 Mbps, handshake ≤ 12 s, server certificate name `opengw.net` (pinned), TLS ≥ 1.2, RSA ≥ 2048 bit, AES data cipher, plain-HTTP pages unmodified, DNS answers not forged.

## Live-check accuracy (the app's on-phone OpenVPN handshake probe vs. the full tunnel test)

`{"probe=True,tunnel=True": 264, "probe=True,tunnel=False": 87, "probe=False,tunnel=True": 1, "probe=False,tunnel=False": 386}` – servers that fail the probe almost never work (386 of 387), so the app's automatic discovery only adds servers that answer the real OpenVPN handshake.

## Shipped servers by country

| Country | Shipped | Tested | Worked | Good |
|---|---|---|---|---|
| JP Japan | 40 | 131 | 79 | 56 |
| KR Korea Republic of | 34 | 125 | 54 | 34 |
| RU Russian Federation | 23 | 120 | 42 | 23 |
| TH Thailand | 16 | 120 | 18 | 16 |
| US United States | 7 | 65 | 18 | 7 |
| VN Viet Nam | 7 | 43 | 11 | 7 |
| CA Canada | 3 | 8 | 4 | 3 |
| MX Mexico | 3 | 3 | 3 | 3 |
| PE Peru | 2 | 6 | 3 | 2 |
| GB United Kingdom | 2 | 12 | 2 | 2 |
| IN India | 2 | 8 | 2 | 2 |
| FR France | 2 | 4 | 2 | 2 |
| AU Australia | 2 | 7 | 3 | 2 |
| BR Brazil | 2 | 5 | 2 | 2 |
| CO Colombia | 2 | 3 | 2 | 2 |
| NZ New Zealand | 1 | 2 | 1 | 1 |
| HU Hungary | 1 | 2 | 1 | 1 |
| VE Venezuela | 1 | 2 | 1 | 1 |
| UA Ukraine | 1 | 8 | 1 | 1 |
| PH Philippines | 1 | 1 | 1 | 1 |
| AR Argentina | 1 | 3 | 1 | 1 |
| PT Portugal | 1 | 1 | 1 | 1 |
| CL Chile | 1 | 2 | 1 | 1 |
| GD Grenada | 1 | 1 | 1 | 1 |
| EU European Union | 1 | 2 | 1 | 1 |
| TR Turkey | 1 | 3 | 1 | 1 |
| AE United Arab Emirates | 1 | 2 | 1 | 1 |
| ID Indonesia | 1 | 8 | 2 | 1 |

## Why the others were removed

| Reason | Servers |
|---|---|
| TCP connect timed out | 218 |
| no response from server | 138 |
| slow | 71 |
| server refused the session | 62 |
| connection refused | 27 |
| tunnel up but no internet through it | 18 |
| modifies plain-HTTP traffic; slow | 7 |
| TLS handshake timed out | 4 |
| forges or blocks DNS answers; slow | 4 |
| no tunnel within s | 4 |
| forges or blocks DNS answers | 3 |
| modifies plain-HTTP traffic | 2 |
| modifies plain-HTTP traffic; forges or blocks DNS answers; slow | 2 |
| connection reset by server | 2 |

## Requested countries

Available: AE, AU, CA, FR, GB, IN, JP, KR, TR, US

Not available in this test (shown as *Currently unavailable*): BD, CH, DE, ES, IT, NL, NO, SE, SG

The app updates its servers automatically every 6 hours (newest tested list + live-checked VPN Gate servers).
