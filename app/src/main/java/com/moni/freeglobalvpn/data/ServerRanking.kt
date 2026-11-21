package com.moni.freeglobalvpn.data

import kotlin.math.min

/**
 * Smart server selection. Lower cost = better. Pure function so it is unit-tested.
 * Weighs (in order of importance): reachability, measured latency, recent failures,
 * long uptime (stability), provider-reported bandwidth, and load (active sessions).
 */
object ServerRanking {
    private const val FAILURE_DECAY_MS = 30 * 60_000L

    fun cost(s: VpnServer, h: ServerHealth?, now: Long = System.currentTimeMillis()): Double {
        if (h != null && !h.reachable && now - h.checkedAt < 10 * 60_000L) return Double.MAX_VALUE
        val latency = (h?.latencyMs ?: s.reportedPingMs?.takeIf { it > 0 }?.plus(80) ?: 350).toDouble()
        val speedMbps = (s.reportedSpeedBps ?: 0L) / 1_000_000.0
        val uptimeDays = (s.uptimeMs ?: 0L) / 86_400_000.0
        val sessions = (s.sessions ?: 0).toDouble()
        val recentFailures = if (h != null && now - h.lastFailureAt < FAILURE_DECAY_MS) h.failures else 0
        if (recentFailures >= 2) return Double.MAX_VALUE   // failed twice from this phone recently: hide it

        var cost = latency
        cost -= min(speedMbps, 200.0) * 0.6      // up to -120
        cost -= min(uptimeDays, 30.0) * 3.0       // up to -90  (stability)
        cost += min(sessions, 200.0) * 0.8        // busy servers are slower
        cost += recentFailures * 400.0            // avoid servers that just failed
        if (s.protocol == "tcp") cost -= 10.0      // TCP traverses restrictive networks more reliably
        if (s.providerId == "live") cost += 40.0   // handshake-checked only: prefer fully tested servers
        return cost
    }

    fun rank(servers: List<VpnServer>, health: Map<String, ServerHealth>): List<VpnServer> =
        servers.map { it to cost(it, health[it.id]) }
            .filter { it.second != Double.MAX_VALUE }
            .sortedBy { it.second }
            .map { it.first }

    fun availability(servers: List<VpnServer>, health: Map<String, ServerHealth>): Availability {
        if (servers.isEmpty()) return Availability.UNAVAILABLE
        val probed = servers.mapNotNull { health[it.id] }
        if (probed.isNotEmpty() && probed.none { it.reachable }) return Availability.UNAVAILABLE
        val best = probed.filter { it.reachable }.mapNotNull { it.latencyMs }.minOrNull()
            ?: servers.mapNotNull { it.reportedPingMs }.minOrNull()
        return when {
            best == null -> Availability.FAIR
            best < 150 -> Availability.GOOD
            best < 350 -> Availability.FAIR
            else -> Availability.POOR
        }
    }
}
