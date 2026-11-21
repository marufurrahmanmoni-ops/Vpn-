package com.moni.freeglobalvpn.data.provider

import com.moni.freeglobalvpn.data.Countries
import com.moni.freeglobalvpn.data.VpnServer
import java.util.Base64

/**
 * Parser for the public VPN Gate list (https://www.vpngate.net/api/iphone/).
 *
 * Format (CSV, no quoting):
 * ```
 * *vpn_servers
 * #HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,NumVpnSessions,Uptime,TotalUsers,TotalTraffic,LogType,Operator,Message,OpenVPN_ConfigData_Base64
 * ...
 * *
 * ```
 * Pure Kotlin so it can be unit-tested on the JVM.
 */
object VpnGateParser {
    const val PROVIDER_ID = "vpngate"
    private const val FIXED_LEFT = 12 // HostName .. LogType

    fun parse(csv: String): ProviderResult {
        val servers = ArrayList<VpnServer>()
        val configs = HashMap<String, String>()
        val seen = HashSet<String>()
        var headerSeen = false

        for (raw in csv.lineSequence()) {
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("*")) continue
            if (line.startsWith("#")) { headerSeen = true; continue }
            if (!headerSeen) continue

            val cols = line.split(',')
            if (cols.size < FIXED_LEFT + 3) continue
            val b64 = cols.last().trim()
            if (b64.length < 100) continue

            val config = try {
                String(Base64.getMimeDecoder().decode(b64), Charsets.UTF_8)
            } catch (_: IllegalArgumentException) {
                continue
            }
            val endpoint = OvpnConfig.firstRemote(config) ?: continue

            val host = cols[0].trim()
            val ip = cols[1].trim()
            val cc = cols[6].trim().uppercase()
            if (cc.length != 2 || ip.isEmpty()) continue
            // Middle columns: Operator,Message (operator text may itself contain commas)
            val middle = cols.subList(FIXED_LEFT, cols.size - 1)
            val operator = middle.dropLast(1).joinToString(",").trim().ifEmpty { middle.firstOrNull()?.trim() }

            val id = "$PROVIDER_ID:$ip:${endpoint.port}:${endpoint.proto}"
            if (!seen.add(id)) continue

            servers += VpnServer(
                id = id,
                providerId = PROVIDER_ID,
                hostName = if (host.isNotEmpty()) "$host.opengw.net" else ip,
                ip = ip,
                port = endpoint.port,
                protocol = endpoint.proto,
                countryCode = cc,
                countryName = Countries.get(cc)?.name ?: cols[5].trim(),
                reportedPingMs = cols[3].trim().toIntOrNull(),
                reportedSpeedBps = cols[4].trim().toLongOrNull(),
                sessions = cols[7].trim().toIntOrNull(),
                uptimeMs = cols[8].trim().toLongOrNull(),
                operator = operator?.takeIf { it.isNotBlank() },
                logPolicy = cols[11].trim().ifEmpty { null },
            )
            configs[id] = config
        }
        return ProviderResult(servers, configs)
    }
}
