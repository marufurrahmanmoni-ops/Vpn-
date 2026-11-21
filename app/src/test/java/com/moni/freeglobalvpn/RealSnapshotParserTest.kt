package com.moni.freeglobalvpn

import com.moni.freeglobalvpn.data.ServerRepository
import com.moni.freeglobalvpn.data.provider.OvpnConfig
import com.moni.freeglobalvpn.data.provider.VpnGateParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/** Pure-JVM check of the parser against a real VPN Gate API response (-Pvpngate.snapshot=<file>). */
class RealSnapshotParserTest {
    @Test fun parsesEveryRealServer() {
        val path = System.getProperty("vpngate.snapshot")
        assumeTrue(path != null && File(path).exists())
        val text = File(path!!).readText()
        val rows = text.lines().count { it.isNotBlank() && !it.startsWith("*") && !it.startsWith("#") }
        val r = VpnGateParser.parse(text)
        assertEquals("every published row is a usable server", rows, r.servers.size)
        r.servers.forEach { s ->
            val cfg = OvpnConfig.sanitize(r.configs.getValue(s.id))
            assertTrue(cfg.contains("<ca>") && cfg.contains("<cert>") && cfg.contains("<key>"))
            assertEquals(s.port, OvpnConfig.firstRemote(cfg)!!.port)
        }
        val groups = ServerRepository.countriesOf(ServerRepository.State(servers = r.servers))
        println("REAL SNAPSHOT: ${r.servers.size} servers; available countries: " +
            groups.filter { it.isAvailable }.joinToString { "${it.code}(${it.servers.size})" })
        println("Requested but unavailable: " + groups.filterNot { it.isAvailable }.joinToString { it.code })
    }
}
