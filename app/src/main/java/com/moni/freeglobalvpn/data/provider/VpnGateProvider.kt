package com.moni.freeglobalvpn.data.provider

import com.moni.freeglobalvpn.data.VpnServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * VPN Gate Academic Experiment (University of Tsukuba, Japan).
 * Free, volunteer-operated servers, public list API, no account required.
 */
class VpnGateProvider : ServerProvider {
    override val id = VpnGateParser.PROVIDER_ID
    override val displayName = "VPN Gate (University of Tsukuba)"
    override val engine = ServerProvider.Engine.OPENVPN
    override val loggingDisclosure =
        "VPN Gate servers are run by volunteers worldwide as part of an academic experiment at the " +
            "University of Tsukuba. Each operator declares their own logging policy (shown per server, " +
            "e.g. \"2weeks\"), and the VPN Gate project itself may keep connection logs. Traffic is " +
            "encrypted between your phone and the server, but the volunteer operator can see traffic " +
            "that leaves their server unencrypted. Do NOT treat these servers as \"no-log\"."

    private val endpoints = listOf(
        "https://www.vpngate.net/api/iphone/",
        "http://www.vpngate.net/api/iphone/", // fallback; configs are still validated & sanitised
    )

    override suspend fun fetch(): ProviderResult = withContext(Dispatchers.IO) {
        var last: Exception? = null
        for (url in endpoints) {
            try {
                val body = download(url)
                val result = VpnGateParser.parse(body)
                if (result.servers.isNotEmpty()) return@withContext result
                last = IOException("VPN Gate list from $url contained no usable servers")
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("VPN Gate list unavailable")
    }

    // VPN Gate configs use certificate auth; the legacy `auth-user-pass` (if present) expects vpn/vpn.
    override fun defaultCredentials(server: VpnServer) = "vpn" to "vpn"

    private fun download(url: String): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 12_000
        conn.readTimeout = 25_000
        conn.setRequestProperty("User-Agent", "FreeGlobalVPN/1.0 (Android)")
        try {
            if (conn.responseCode != 200) throw IOException("HTTP ${conn.responseCode} from $url")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
