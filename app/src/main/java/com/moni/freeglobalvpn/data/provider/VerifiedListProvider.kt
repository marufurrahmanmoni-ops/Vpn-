package com.moni.freeglobalvpn.data.provider

import com.moni.freeglobalvpn.data.Countries
import com.moni.freeglobalvpn.data.VpnServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * "Verified good servers": every 4 hours a GitHub Actions job in this app's repository connects to
 * every candidate free server (the live VPN Gate list, VPN Gate servers seen in the last 36 h, and
 * self-hosted community servers) with a REAL OpenVPN tunnel, checks that internet works through it,
 * that the IP changes and that it is fast enough, and publishes only the good ones as JSON.
 * See tools/vpn-server-test/test_servers.py and .github/workflows/update-servers.yml.
 */
class VerifiedListProvider(
    private val urls: List<String> = DEFAULT_URLS,
    /**
     * Base64 X.509 (DER) ECDSA P-256 public key. When set, the list is accepted only with a valid
     * signature (verified.json.sig) made by the repository's signing key – so a tampered copy on a
     * CDN or network path is rejected. Empty = rely on HTTPS only. See docs/SERVER_INTEGRATION.md.
     */
    private val publicKeyB64: String = "",
    /** The tested-good list shipped inside the APK (used when no newer list can be downloaded). */
    private val bundled: () -> String? = { null },
) : ServerProvider {
    override val id = ID
    override val displayName = "Tested free servers (VPN Gate + community)"
    override val engine = ServerProvider.Engine.OPENVPN
    override val loggingDisclosure =
        "These are VPN Gate volunteer servers (University of Tsukuba academic experiment) and self-hosted " +
            "community servers that passed a real connection + speed test in the last few hours. Testing proves " +
            "they work – it says nothing about their logging. VPN Gate keeps connection logs for abuse handling " +
            "and each volunteer declares its own logging policy (shown per server). Community servers show their " +
            "operator's own policy. Do NOT treat any of them as \"no-log\"."

    @Volatile private var credentials: Map<String, Pair<String, String>> = emptyMap()

    override suspend fun fetch(): ProviderResult = withContext(Dispatchers.IO) {
        val shipped = runCatching { bundled()?.let { VerifiedListParser.parse(it) } }.getOrNull()
        val online = runCatching { fetchOnline() }.getOrNull()
        // Use the newest tested list: a freshly published one if available, otherwise the one in the APK.
        val best = listOfNotNull(online, shipped).filter { it.result.servers.isNotEmpty() }
            .maxByOrNull { it.result.generatedAt ?: 0L }
            ?: throw IOException("no tested server list available")
        credentials = best.credentials
        best.result
    }

    private fun fetchOnline(): VerifiedListParser.Parsed {
        var last: Exception? = null
        for (u in urls) {
            try {
                val text = download(u)
                if (publicKeyB64.isNotBlank()) {
                    val sig = download("$u.sig").trim()
                    if (!ListSignature.verify(text.toByteArray(Charsets.UTF_8), sig, publicKeyB64))
                        throw SecurityException("verified list signature is invalid at $u")
                }
                val parsed = VerifiedListParser.parse(text)
                if (parsed.result.servers.isEmpty()) throw IOException("empty verified list at $u")
                return parsed
            } catch (e: Exception) {
                last = e
            }
        }
        throw last ?: IOException("verified list unavailable")
    }

    override fun defaultCredentials(server: VpnServer): Pair<String, String> =
        credentials[server.id] ?: ("vpn" to "vpn")

    private fun download(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10_000; c.readTimeout = 20_000
        c.setRequestProperty("User-Agent", "FreeGlobalVPN/1.1 (Android)")
        try {
            if (c.responseCode != 200) throw IOException("HTTP ${c.responseCode} from $url")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally { c.disconnect() }
    }

    companion object {
        const val ID = "verified"
        private const val REPO = "marufurrahmanmoni-ops/Vpn-"
        val DEFAULT_URLS = listOf(
            "https://raw.githubusercontent.com/$REPO/servers/verified.json",
            "https://cdn.jsdelivr.net/gh/$REPO@servers/verified.json",
            "https://fastly.jsdelivr.net/gh/$REPO@servers/verified.json",
        )
    }
}

/** Pure parser for verified.json (unit-tested). */
object VerifiedListParser {
    @Serializable
    data class File(
        val schema: Int = 1,
        val summary: Summary? = null,
        val template: String? = null,
        val servers: List<Entry> = emptyList(),
    )

    @Serializable
    data class Summary(@SerialName("generated_at") val generatedAt: String? = null)

    @Serializable
    data class Entry(
        val ip: String,
        val port: Int,
        val proto: String,
        val cc: String,
        val country: String? = null,
        val host: String? = null,
        @SerialName("down_mbps") val downMbps: Double? = null,
        @SerialName("latency_ms") val latencyMs: Int? = null,
        @SerialName("uptime_h") val uptimeH: Double? = null,
        val log: String? = null,
        val operator: String? = null,
        val source: String? = null,
        @SerialName("verified_at") val verifiedAt: String? = null,
        val config: String? = null,
        val username: String? = null,
        val password: String? = null,
    )

    data class Parsed(val result: ProviderResult, val credentials: Map<String, Pair<String, String>>)

    private val json = Json { ignoreUnknownKeys = true }

    /** Parses "2026-11-21T21:21:59Z" (works on every Android version, unlike java.time). */
    private fun instant(s: String?): Long? = s?.let {
        runCatching {
            java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", java.util.Locale.US)
                .apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }.parse(it)!!.time
        }.getOrNull()
    }

    fun parse(text: String): Parsed {
        val f = json.decodeFromString<File>(text)
        val servers = ArrayList<VpnServer>()
        val configs = HashMap<String, String>()
        val creds = HashMap<String, Pair<String, String>>()
        val seen = HashSet<String>()
        for (e in f.servers) {
            val proto = if (e.proto.lowercase().startsWith("tcp")) "tcp" else "udp"
            val config = e.config ?: f.template?.let { "proto $proto\nremote ${e.ip} ${e.port}\n$it" } ?: continue
            if (OvpnConfig.firstRemote(config) == null) continue
            val id = "$ID:${e.ip}:${e.port}:$proto"
            if (!seen.add(id)) continue
            val cc = e.cc.uppercase()
            val community = e.source == "community"
            servers += VpnServer(
                id = id,
                providerId = ID,
                hostName = when {
                    community -> e.host ?: e.ip
                    e.host.isNullOrBlank() -> e.ip
                    else -> "${e.host}.opengw.net"
                },
                ip = e.ip,
                port = e.port,
                protocol = proto,
                countryCode = cc,
                countryName = Countries.get(cc)?.name ?: e.country ?: cc,
                reportedSpeedBps = e.downMbps?.let { (it * 1_000_000).toLong() },
                uptimeMs = e.uptimeH?.let { (it * 3_600_000).toLong() },
                operator = e.operator ?: if (community) "Community server" else "VPN Gate volunteer",
                logPolicy = e.log,
                verifiedMbps = e.downMbps,
                verifiedAt = instant(e.verifiedAt),
                pinnedCertName = if (community) null else VpnGateParser.CERT_NAME,
            )
            configs[id] = config
            if (e.username != null && e.password != null) creds[id] = e.username to e.password
        }
        return Parsed(ProviderResult(servers, configs, instant(f.summary?.generatedAt)), creds)
    }

    private const val ID = VerifiedListProvider.ID
}

/** ECDSA P-256 / SHA-256 signature check for the published server list. */
object ListSignature {
    @OptIn(kotlin.io.encoding.ExperimentalEncodingApi::class)
    private fun b64(s: String) = kotlin.io.encoding.Base64.decode(s.trim())

    fun verify(data: ByteArray, signatureB64: String, publicKeyB64: String): Boolean = runCatching {
        val key = java.security.KeyFactory.getInstance("EC")
            .generatePublic(java.security.spec.X509EncodedKeySpec(b64(publicKeyB64)))
        java.security.Signature.getInstance("SHA256withECDSA").run {
            initVerify(key)
            update(data)
            verify(b64(signatureB64))
        }
    }.getOrDefault(false)
}
