package com.moni.freeglobalvpn.vpn

import com.moni.freeglobalvpn.data.VpnServer

sealed interface ConnectionState {
    data object Disconnected : ConnectionState
    data class Connecting(val server: VpnServer, val detail: String, val attempt: Int = 1, val maxAttempts: Int = 1) : ConnectionState
    data class Connected(val server: VpnServer, val since: Long) : ConnectionState
    data class Reconnecting(val server: VpnServer, val detail: String) : ConnectionState
    data class WaitingForNetwork(val server: VpnServer?) : ConnectionState
    data class Paused(val server: VpnServer?) : ConnectionState
    data class Failed(val server: VpnServer?, val reason: String) : ConnectionState
    data object Disconnecting : ConnectionState

    val isActive: Boolean
        get() = this is Connecting || this is Connected || this is Reconnecting || this is WaitingForNetwork || this is Paused
}

data class TrafficStats(
    val downBps: Long = 0,
    val upBps: Long = 0,
    val totalDown: Long = 0,
    val totalUp: Long = 0,
)
