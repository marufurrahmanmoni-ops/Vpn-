package com.moni.freeglobalvpn

import com.moni.freeglobalvpn.data.provider.OvpnConfig
import com.moni.freeglobalvpn.data.provider.VerifiedListParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** The tested-good list shipped in the APK must be complete, pinned and usable. */
class BundledListTest {
    @Test fun bundledListIsUsable() {
        val f = listOf("src/main/assets/verified_servers.json", "app/src/main/assets/verified_servers.json").map(::File).first { it.exists() }
        val p = VerifiedListParser.parse(f.readText())
        val r = p.result
        assertTrue("has servers", r.servers.size >= 50)
        assertTrue("has a timestamp", (r.generatedAt ?: 0) > 0)
        r.servers.forEach { s ->
            assertEquals("${s.id} pinned", "opengw.net", s.pinnedCertName)
            assertTrue("${s.id} tested speed", (s.verifiedMbps ?: 0.0) >= 3.0)
            val cfg = r.configs.getValue(s.id)
            assertEquals(s.port, OvpnConfig.firstRemote(cfg)!!.port)
            assertTrue(cfg.contains("<ca>") && cfg.contains("<cert>") && cfg.contains("<key>"))
        }
        println("Bundled: ${r.servers.size} servers in ${r.servers.map { it.countryCode }.toSet().size} countries")
    }
}
