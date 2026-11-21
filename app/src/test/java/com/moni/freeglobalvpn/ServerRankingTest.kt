package com.moni.freeglobalvpn

import com.moni.freeglobalvpn.data.Availability
import com.moni.freeglobalvpn.data.ServerHealth
import com.moni.freeglobalvpn.data.ServerRanking
import com.moni.freeglobalvpn.data.ServerRepository
import com.moni.freeglobalvpn.data.VpnServer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerRankingTest {
    private fun s(id: String, cc: String = "JP", ping: Int? = 50, speed: Long = 50_000_000, uptimeDays: Long = 5, sessions: Int = 10) =
        VpnServer(id, "vpngate", id, "10.0.0.1", 443, "tcp", cc, cc, ping, speed, sessions, uptimeDays * 86_400_000)

    @Test fun unreachableServersAreExcluded() {
        val now = System.currentTimeMillis()
        val ranked = ServerRanking.rank(listOf(s("a"), s("b")), mapOf("a" to ServerHealth(false, null, now)))
        assertEquals(listOf("b"), ranked.map { it.id })
    }

    @Test fun lowerMeasuredLatencyWins() {
        val now = System.currentTimeMillis()
        val h = mapOf("a" to ServerHealth(true, 300, now), "b" to ServerHealth(true, 40, now))
        assertEquals("b", ServerRanking.rank(listOf(s("a"), s("b")), h).first().id)
    }

    @Test fun recentFailuresArePenalised() {
        val now = System.currentTimeMillis()
        val h = mapOf("a" to ServerHealth(true, 40, now, failures = 1, lastFailureAt = now), "b" to ServerHealth(true, 90, now))
        assertEquals("b", ServerRanking.rank(listOf(s("a"), s("b")), h).first().id)
    }

    @Test fun stableServerPreferredWhenLatencySimilar() {
        val now = System.currentTimeMillis()
        val h = mapOf("new" to ServerHealth(true, 60, now), "old" to ServerHealth(true, 70, now))
        assertEquals("old", ServerRanking.rank(listOf(s("new", uptimeDays = 0), s("old", uptimeDays = 25)), h).first().id)
    }

    @Test fun requestedCountriesWithoutServersAreUnavailable() {
        val groups = ServerRepository.countriesOf(ServerRepository.State(servers = listOf(s("a", "JP"))))
        val jp = groups.first { it.code == "JP" }
        val bd = groups.first { it.code == "BD" }
        assertTrue(jp.isAvailable)
        assertFalse(bd.isAvailable)
        assertEquals(Availability.UNAVAILABLE, bd.availability)
        assertTrue(bd.servers.isEmpty())
        assertEquals("JP", groups.first().code) // available countries are listed first
    }
}
