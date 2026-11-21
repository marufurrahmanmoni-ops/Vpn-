package com.moni.freeglobalvpn.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.Json

private val Context.dataStore by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

enum class DnsChoice(val label: String, val primary: String, val secondary: String) {
    CLOUDFLARE("Cloudflare (1.1.1.1)", "1.1.1.1", "1.0.0.1"),
    QUAD9("Quad9 (9.9.9.9)", "9.9.9.9", "149.112.112.112"),
    GOOGLE("Google (8.8.8.8)", "8.8.8.8", "8.8.4.4"),
    SERVER("VPN server's DNS", "", ""),
}

data class Settings(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val autoReconnect: Boolean = true,
    val smartFailover: Boolean = true,
    val blockWhileReconnecting: Boolean = true,
    val dns: DnsChoice = DnsChoice.CLOUDFLARE,
    val allowLan: Boolean = false,
    val bypassApps: Set<String> = emptySet(),
    val bypassInitialized: Boolean = false,
    val favorites: Set<String> = emptySet(),
    val recents: List<RecentConnection> = emptyList(),
    val lastServerId: String? = null,
    val connectedSince: Long = 0,
    val acceptedDisclosure: Boolean = false,
)

class SettingsStore(private val context: Context) {
    private object K {
        val theme = stringPreferencesKey("theme")
        val autoReconnect = booleanPreferencesKey("auto_reconnect")
        val smartFailover = booleanPreferencesKey("smart_failover")
        val blockWhileReconnecting = booleanPreferencesKey("block_reconnect")
        val dns = stringPreferencesKey("dns")
        val allowLan = booleanPreferencesKey("allow_lan")
        val bypass = stringSetPreferencesKey("bypass_apps")
        val bypassInit = booleanPreferencesKey("bypass_init")
        val favorites = stringSetPreferencesKey("favorites")
        val recents = stringPreferencesKey("recents")
        val lastServer = stringPreferencesKey("last_server")
        val connectedSince = longPreferencesKey("connected_since")
        val disclosure = booleanPreferencesKey("disclosure_ok")
    }

    private val json = Json { ignoreUnknownKeys = true }

    val settings: Flow<Settings> = context.dataStore.data.map { it.toSettings() }

    suspend fun current(): Settings = settings.first()

    private fun Preferences.toSettings() = Settings(
        theme = this[K.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
        autoReconnect = this[K.autoReconnect] ?: true,
        smartFailover = this[K.smartFailover] ?: true,
        blockWhileReconnecting = this[K.blockWhileReconnecting] ?: true,
        dns = this[K.dns]?.let { runCatching { DnsChoice.valueOf(it) }.getOrNull() } ?: DnsChoice.CLOUDFLARE,
        allowLan = this[K.allowLan] ?: false,
        bypassApps = this[K.bypass] ?: emptySet(),
        bypassInitialized = this[K.bypassInit] ?: false,
        favorites = this[K.favorites] ?: emptySet(),
        recents = this[K.recents]?.let { runCatching { json.decodeFromString<List<RecentConnection>>(it) }.getOrNull() } ?: emptyList(),
        lastServerId = this[K.lastServer],
        connectedSince = this[K.connectedSince] ?: 0,
        acceptedDisclosure = this[K.disclosure] ?: false,
    )

    suspend fun setTheme(v: ThemeMode) = context.dataStore.edit { it[K.theme] = v.name }
    suspend fun setAutoReconnect(v: Boolean) = context.dataStore.edit { it[K.autoReconnect] = v }
    suspend fun setSmartFailover(v: Boolean) = context.dataStore.edit { it[K.smartFailover] = v }
    suspend fun setBlockWhileReconnecting(v: Boolean) = context.dataStore.edit { it[K.blockWhileReconnecting] = v }
    suspend fun setDns(v: DnsChoice) = context.dataStore.edit { it[K.dns] = v.name }
    suspend fun setAllowLan(v: Boolean) = context.dataStore.edit { it[K.allowLan] = v }
    suspend fun setDisclosureAccepted() = context.dataStore.edit { it[K.disclosure] = true }
    suspend fun setConnectedSince(v: Long) = context.dataStore.edit { it[K.connectedSince] = v }

    suspend fun setBypassApps(v: Set<String>) = context.dataStore.edit { it[K.bypass] = v; it[K.bypassInit] = true }

    suspend fun toggleFavorite(key: String) = context.dataStore.edit {
        val cur = it[K.favorites] ?: emptySet()
        it[K.favorites] = if (key in cur) cur - key else cur + key
    }

    suspend fun addRecent(server: VpnServer) = context.dataStore.edit {
        val cur = it[K.recents]?.let { s -> runCatching { json.decodeFromString<List<RecentConnection>>(s) }.getOrNull() } ?: emptyList()
        val next = (listOf(RecentConnection(server.id, server.countryCode, server.hostName, System.currentTimeMillis())) +
            cur.filterNot { r -> r.serverId == server.id }).take(10)
        it[K.recents] = json.encodeToString(next)
        it[K.lastServer] = server.id
    }

    suspend fun clearRecents() = context.dataStore.edit { it.remove(K.recents) }
}
