package com.moni.freeglobalvpn

import com.moni.freeglobalvpn.data.provider.OvpnConfig
import com.moni.freeglobalvpn.data.provider.VerifiedListParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VerifiedListParserTest {
    private val template = "client\ndev tun\ncipher AES-128-CBC\ndata-ciphers AES-128-CBC\n<ca>\nX\n</ca>\n"

    private val json = """
    {"schema":1,"summary":{"generated_at":"2026-11-22T00:00:00Z"},"template":"$template",
     "servers":[
      {"ip":"1.2.3.4","port":443,"proto":"tcp","cc":"jp","host":"public-vpn-1","down_mbps":42.5,"uptime_h":48,"log":"2weeks","source":"api","verified_at":"2026-11-22T00:00:00Z"},
      {"ip":"1.2.3.4","port":443,"proto":"tcp","cc":"JP","source":"pool"},
      {"ip":"5.6.7.8","port":1194,"proto":"udp","cc":"SG","host":"Singapore Free (UDP)","source":"community",
       "config":"client\nproto udp\nremote 5.6.7.8 1194\n","username":"u","password":"p","operator":"Me"},
      {"ip":"9.9.9.9","port":1,"proto":"tcp","cc":"US","source":"community","config":"client\n"},
      {"newField":"ignored","ip":"7.7.7.7","port":995,"proto":"tcp-client","cc":"GB","source":"pool"}
     ]}
    """.trimIndent()

    @Test fun buildsConfigsFromTemplateAndDedupes() {
        val p = VerifiedListParser.parse(json)
        val r = p.result
        assertEquals(listOf("verified:1.2.3.4:443:tcp", "verified:5.6.7.8:1194:udp", "verified:7.7.7.7:995:tcp"), r.servers.map { it.id })
        val jp = r.servers[0]
        assertEquals("public-vpn-1.opengw.net", jp.hostName)
        assertEquals(42.5, jp.verifiedMbps!!, 0.001)
        assertEquals(48 * 3_600_000L, jp.uptimeMs)
        assertEquals("Japan", jp.countryName)
        val cfg = r.configs.getValue(jp.id)
        assertEquals(OvpnConfig.Remote("1.2.3.4", 443, "tcp"), OvpnConfig.firstRemote(cfg))
        assertTrue(cfg.contains("<ca>") && cfg.contains("data-ciphers AES-128-CBC"))
        assertEquals("Singapore Free (UDP)", r.servers[1].hostName)
        assertEquals("u" to "p", p.credentials["verified:5.6.7.8:1194:udp"])
        assertEquals("tcp", r.servers[2].protocol)
        assertNotNull(r.generatedAt)
    }
}
