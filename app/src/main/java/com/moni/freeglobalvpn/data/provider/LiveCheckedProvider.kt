package com.moni.freeglobalvpn.data.provider

import com.moni.freeglobalvpn.data.VpnServer
import com.moni.freeglobalvpn.vpn.OpenVpnProbe
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Automatic server discovery that works without any server-side job: downloads the newest VPN Gate
 * list (official API, or a public mirror when vpngate.net is blocked), then checks every server FROM
 * THIS PHONE with a real OpenVPN handshake ([OpenVpnProbe]). Only servers that answer are offered,
 * marked "live-checked", with the same certificate pinning and config hardening as tested servers.
 */
class LiveCheckedProvider(
    private val listUrls: List<String> = DEFAULT_URLS,
    private val parallelism: Int = 16,
) : ServerProvider {
    override val id = ID
    override val displayName = "Live-checked VPN Gate servers"
    override val engine = ServerProvider.Engine.OPENVPN
    override val loggingDisclosure =
        "Newest VPN Gate volunteer servers, checked from your phone with a real OpenVPN handshake. VPN Gate keeps " +
            "connection logs for abuse handling and each volunteer declares its own logging policy (shown per server)."

    override suspend fun fetch(): ProviderResult = withContext(Dispatchers.IO) {
        val parsed = download()
        val gate = Semaphore(parallelism)
        val alive = coroutineScope {
            parsed.servers.map { s ->
                async { gate.withPermit { s to OpenVpnProbe.probe(s.ip, s.port, s.protocol) } }
            }.awaitAll()
        }.filter { it.second != null }
        val now = System.currentTimeMillis()
        val servers = alive.map { (s, ms) ->
            s.copy(id = s.id.replaceFirst(VpnGateParser.PROVIDER_ID + ":", "$ID:"), providerId = ID,
                reportedPingMs = ms, verifiedAt = now)
        }
        val configs = alive.associate { (s, _) -> s.id.replaceFirst(VpnGateParser.PROVIDER_ID + ":", "$ID:") to parsed.configs.getValue(s.id) }
        ProviderResult(servers, configs, now)
    }

    override fun defaultCredentials(server: VpnServer) = "vpn" to "vpn"

    private fun download(): ProviderResult {
        var last: Exception? = null
        for (u in listUrls) {
            try {
                val c = URL(u).openConnection() as HttpURLConnection
                c.connectTimeout = 10_000; c.readTimeout = 25_000
                c.setRequestProperty("User-Agent", "FreeGlobalVPN/1.3 (Android)")
                val text = try {
                    if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode} from $u")
                    c.inputStream.bufferedReader().use { it.readText() }
                } finally { c.disconnect() }
                val r = VpnGateParser.parse(text)
                if (r.servers.isNotEmpty()) return r
            } catch (e: Exception) { last = e }
        }
        throw last ?: IOException("no VPN Gate list reachable")
    }

    companion object {
        const val ID = "live"
        val DEFAULT_URLS = listOf(
            "https://www.vpngate.net/api/iphone/",
            "https://cdn.jsdelivr.net/gh/GeorgeXie2333/vpngate-list-mirror@latest/data/vpngate.csv",
            "https://fastly.jsdelivr.net/gh/GeorgeXie2333/vpngate-list-mirror@latest/data/vpngate.csv",
            "http://www.vpngate.net/api/iphone/",
        )
    }
}
