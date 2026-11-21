package com.moni.freeglobalvpn.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

/**
 * Real reachability + latency probe: measures the TCP handshake time to the server.
 * For UDP OpenVPN endpoints we probe TCP/443 on the same host (VPN Gate/SoftEther servers usually
 * listen there too) to estimate latency. A failed TCP probe marks TCP servers unreachable.
 */
object HealthChecker {
    private const val TIMEOUT_MS = 2_500

    fun probe(host: String, port: Int, timeoutMs: Int = TIMEOUT_MS): Int? = try {
        Socket().use { s ->
            val t0 = System.nanoTime()
            s.connect(InetSocketAddress(host, port), timeoutMs)
            ((System.nanoTime() - t0) / 1_000_000).toInt().coerceAtLeast(1)
        }
    } catch (_: Exception) {
        null
    }

    suspend fun checkAll(
        servers: List<VpnServer>,
        previous: Map<String, ServerHealth>,
        parallelism: Int = 16,
    ): Map<String, ServerHealth> = withContext(Dispatchers.IO) {
        val gate = Semaphore(parallelism)
        coroutineScope {
            servers.map { s ->
                async {
                    gate.withPermit {
                        val port = if (s.protocol == "tcp") s.port else 443
                        val ms = probe(s.ip, port)
                        val prev = previous[s.id]
                        s.id to ServerHealth(
                            // A UDP endpoint may simply not listen on TCP/443 – treat as "latency unknown",
                            // not as dead (the connect watchdog + failover handle real failures).
                            reachable = ms != null || s.protocol == "udp",
                            latencyMs = ms,
                            checkedAt = System.currentTimeMillis(),
                            failures = prev?.failures ?: 0,
                            lastFailureAt = prev?.lastFailureAt ?: 0,
                        )
                    }
                }
            }.awaitAll().toMap()
        }
    }
}
