package com.moni.freeglobalvpn.vpn

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import com.moni.freeglobalvpn.data.HealthChecker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.net.HttpURLConnection
import java.net.URL

/** Real measurements of what the network looks like from this device right now. */
object NetworkProbe {
    data class PublicIp(val ip: String, val countryCode: String?, val country: String?, val city: String?, val isp: String?)

    private val json = Json { ignoreUnknownKeys = true }

    /** Public IP as seen by the internet (goes through the tunnel when the VPN is up). */
    suspend fun publicIp(): PublicIp? = withContext(Dispatchers.IO) {
        runCatching {
            val o = json.parseToJsonElement(get("https://ipwho.is/")).jsonObject
            if (o["success"]?.jsonPrimitive?.content == "false") error("ipwho.is failed")
            PublicIp(
                ip = o["ip"]!!.jsonPrimitive.content,
                countryCode = o["country_code"]?.jsonPrimitive?.content,
                country = o["country"]?.jsonPrimitive?.content,
                city = o["city"]?.jsonPrimitive?.content,
                isp = o["connection"]?.jsonObject?.get("isp")?.jsonPrimitive?.content,
            )
        }.recoverCatching {
            val o = json.parseToJsonElement(get("https://api.ipify.org?format=json")).jsonObject
            PublicIp(o["ip"]!!.jsonPrimitive.content, null, null, null, null)
        }.getOrNull()
    }

    /** DNS resolvers the system is actually using on the VPN network (empty when no VPN). */
    fun vpnDnsServers(context: Context): List<String> {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return emptyList()
        @Suppress("DEPRECATION")
        val vpn = cm.allNetworks.firstOrNull {
            cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } ?: return emptyList()
        return cm.getLinkProperties(vpn)?.dnsServers?.mapNotNull { it.hostAddress } ?: emptyList()
    }

    /** Round-trip latency through whatever route is active (the tunnel when connected). */
    suspend fun tunnelLatencyMs(): Int? = withContext(Dispatchers.IO) {
        HealthChecker.probe("1.1.1.1", 443, 4_000) ?: HealthChecker.probe("8.8.8.8", 443, 4_000)
    }

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 8_000; c.readTimeout = 8_000
        c.setRequestProperty("User-Agent", "FreeGlobalVPN/1.0")
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }
}
