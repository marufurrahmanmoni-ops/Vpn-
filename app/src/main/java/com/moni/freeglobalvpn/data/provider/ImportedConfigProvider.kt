package com.moni.freeglobalvpn.data.provider

import com.moni.freeglobalvpn.data.Countries
import com.moni.freeglobalvpn.data.SecureStore
import com.moni.freeglobalvpn.data.VpnServer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * Configs the user imports themselves (e.g. the free OpenVPN configs from Proton VPN's free plan,
 * their own server, or a community server they have permission to use).
 * Stored encrypted with an Android Keystore key – see [SecureStore].
 */
class ImportedConfigProvider(private val store: SecureStore) : ServerProvider {
    override val id = PROVIDER_ID
    override val displayName = "My imported configs"
    override val engine = ServerProvider.Engine.OPENVPN
    override val loggingDisclosure =
        "Imported servers are operated by whoever issued the config file. Their logging policy is set " +
            "by that operator, not by this app."

    @Serializable
    data class Entry(
        val id: String,
        val name: String,
        val countryCode: String,
        val config: String,
        val username: String? = null,
        val password: String? = null,
    )

    private val json = Json { ignoreUnknownKeys = true }

    fun entries(): List<Entry> =
        store.read(FILE)?.let { runCatching { json.decodeFromString<List<Entry>>(it) }.getOrNull() } ?: emptyList()

    fun add(name: String, countryCode: String, configText: String, user: String?, pass: String?): Result<Entry> {
        val remote = OvpnConfig.firstRemote(configText)
            ?: return Result.failure(IllegalArgumentException("No 'remote' line found – is this an OpenVPN (.ovpn) file?"))
        val cc = countryCode.uppercase().takeIf { Countries.get(it) != null } ?: "ZZ"
        val entry = Entry(
            id = "$PROVIDER_ID:${System.currentTimeMillis()}:${remote.host}:${remote.port}:${remote.proto}",
            name = name.ifBlank { remote.host },
            countryCode = cc,
            config = configText,
            username = user?.takeIf { it.isNotBlank() },
            password = pass?.takeIf { it.isNotBlank() },
        )
        store.write(FILE, json.encodeToString(entries() + entry))
        return Result.success(entry)
    }

    fun remove(id: String) {
        store.write(FILE, json.encodeToString(entries().filterNot { it.id == id }))
    }

    override suspend fun fetch(): ProviderResult {
        val list = entries()
        val servers = list.mapNotNull { e ->
            val r = OvpnConfig.firstRemote(e.config) ?: return@mapNotNull null
            VpnServer(
                id = e.id, providerId = PROVIDER_ID, hostName = e.name, ip = r.host, port = r.port,
                protocol = r.proto, countryCode = e.countryCode,
                countryName = if (e.countryCode == "ZZ") "My servers" else Countries.name(e.countryCode),
                operator = "Imported by you",
            )
        }
        return ProviderResult(servers, list.associate { it.id to it.config })
    }

    override fun defaultCredentials(server: VpnServer): Pair<String, String>? {
        val e = entries().firstOrNull { it.id == server.id } ?: return null
        return if (e.username != null && e.password != null) e.username to e.password else null
    }

    companion object {
        const val PROVIDER_ID = "imported"
        private const val FILE = "imported_configs.bin"
    }
}
