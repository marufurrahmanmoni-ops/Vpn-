package com.moni.freeglobalvpn.data

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Disk cache so the server list appears instantly on launch (configs are loaded lazily, only when
 * connecting, to keep start-up fast).
 */
class ServerCache(context: Context) {
    private val dir = File(context.cacheDir, "servers").apply { mkdirs() }
    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    data class Snapshot(
        val fetchedAt: Long,
        val servers: List<VpnServer>,
        val health: Map<String, ServerHealth> = emptyMap(),
        val verifiedAt: Long = 0,
    )

    fun loadSnapshot(): Snapshot? = runCatching {
        json.decodeFromString<Snapshot>(File(dir, "list.json").readText())
    }.getOrNull()

    fun saveSnapshot(s: Snapshot) = atomicWrite("list.json", json.encodeToString(s))

    fun saveConfigs(configs: Map<String, String>) = atomicWrite("configs.json", json.encodeToString(configs))

    fun loadConfig(serverId: String): String? = runCatching {
        json.decodeFromString<Map<String, String>>(File(dir, "configs.json").readText())[serverId]
    }.getOrNull()

    private fun atomicWrite(name: String, text: String) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        tmp.renameTo(File(dir, name))
    }
}
