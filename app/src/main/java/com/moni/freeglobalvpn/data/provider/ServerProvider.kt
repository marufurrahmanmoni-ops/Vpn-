package com.moni.freeglobalvpn.data.provider

import com.moni.freeglobalvpn.data.VpnServer

/**
 * A source of real VPN endpoints. Add new free/community sources by implementing this
 * interface and registering it in [com.moni.freeglobalvpn.AppContainer]. See docs/SERVER_INTEGRATION.md.
 */
interface ServerProvider {
    val id: String
    val displayName: String
    /** Plain-language statement about who runs the servers and what they may log. */
    val loggingDisclosure: String
    /** Which tunnel engine the configs are for. */
    val engine: Engine

    /** Fetch the live list. Must throw on network/parse failure – never return fake data. */
    suspend fun fetch(): ProviderResult

    /**
     * Credentials to inject when a config contains `auth-user-pass` without inline credentials.
     * Return null when the user must supply them.
     */
    fun defaultCredentials(server: VpnServer): Pair<String, String>? = null

    enum class Engine { OPENVPN }
}

data class ProviderResult(
    val servers: List<VpnServer>,
    /** serverId -> full OpenVPN config text */
    val configs: Map<String, String>,
    /** When the list itself was produced (for lists published by a job), epoch ms. */
    val generatedAt: Long? = null,
)
