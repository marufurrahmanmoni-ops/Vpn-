package com.moni.freeglobalvpn.data

import kotlinx.serialization.Serializable

/** One real, connectable VPN endpoint published by a [com.moni.freeglobalvpn.data.provider.ServerProvider]. */
@Serializable
data class VpnServer(
    /** Stable id: "<provider>:<host-or-ip>:<port>:<proto>" */
    val id: String,
    val providerId: String,
    val hostName: String,
    val ip: String,
    val port: Int,
    /** "tcp" or "udp" */
    val protocol: String,
    val countryCode: String,
    val countryName: String,
    /** Values reported by the provider's directory (may be stale or absent). */
    val reportedPingMs: Int? = null,
    val reportedSpeedBps: Long? = null,
    val sessions: Int? = null,
    val uptimeMs: Long? = null,
    val operator: String? = null,
    /** Logging policy declared by the server operator, shown to the user verbatim. */
    val logPolicy: String? = null,
    /** Download speed measured through a real tunnel by the verification job (null = not verified). */
    val verifiedMbps: Double? = null,
    /** When the verification job last proved this server good (epoch ms). */
    val verifiedAt: Long? = null,
    /**
     * Required server-certificate name. VPN Gate configs trust the whole Let's Encrypt root without a
     * name check (any Let's Encrypt certificate would be accepted), so VPN Gate servers are pinned to
     * "opengw.net" – the name on VPN Gate's server certificates.
     */
    val pinnedCertName: String? = null,
)

/** Result of our own reachability probe from this device. */
@Serializable
data class ServerHealth(
    val reachable: Boolean,
    val latencyMs: Int? = null,
    val checkedAt: Long = 0,
    /** Connection attempts that failed recently (used to deprioritise unstable servers). */
    val failures: Int = 0,
    val lastFailureAt: Long = 0,
)

@Serializable
data class RecentConnection(
    val serverId: String,
    val countryCode: String,
    val hostName: String,
    val at: Long,
)

enum class Availability { GOOD, FAIR, POOR, UNAVAILABLE }

/** Aggregated view of a country for the server list. */
data class CountryGroup(
    val code: String,
    val name: String,
    val servers: List<VpnServer>,
    val bestLatencyMs: Int?,
    val availability: Availability,
) {
    val isAvailable get() = servers.isNotEmpty() && availability != Availability.UNAVAILABLE
}
