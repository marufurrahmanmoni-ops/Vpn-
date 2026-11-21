package com.moni.freeglobalvpn

import com.moni.freeglobalvpn.data.provider.OvpnConfig
import com.moni.freeglobalvpn.data.provider.VpnGateParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Base64

class VpnGateParserTest {
    private fun cfg(proto: String, ip: String, port: Int) = """
        dev tun
        proto $proto
        remote $ip $port
        cipher AES-128-CBC
        auth SHA1
        script-security 2
        up /system/bin/sh
        <ca>
        -----BEGIN CERTIFICATE-----
        MIIFAKE
        -----END CERTIFICATE-----
        </ca>
    """.trimIndent() + "\n" + "#".repeat(80)

    private fun row(host: String, ip: String, cc: String, country: String, op: String, c: String) =
        listOf(host, ip, "1234567", "12", "98765432", country, cc, "15", "864000000", "100", "1000", "2weeks", op, "",
            Base64.getEncoder().encodeToString(c.toByteArray())).joinToString(",")

    @Test fun parsesServersAndSkipsGarbage() {
        val csv = listOf(
            "*vpn_servers",
            "#HostName,IP,Score,Ping,Speed,CountryLong,CountryShort,NumVpnSessions,Uptime,TotalUsers,TotalTraffic,LogType,Operator,Message,OpenVPN_ConfigData_Base64",
            row("public-vpn-1", "219.100.37.1", "JP", "Japan", "Daiyuu Nobori, Japan. Academic Use Only.", cfg("tcp", "219.100.37.1", 443)),
            row("vpn2", "1.2.3.4", "KR", "Korea Republic of", "Volunteer", cfg("udp", "1.2.3.4", 1194)),
            "broken,row,without,enough,columns",
            row("dup", "219.100.37.1", "JP", "Japan", "x", cfg("tcp", "219.100.37.1", 443)), // duplicate id
            "*",
        ).joinToString("\r\n")
        val r = VpnGateParser.parse(csv)
        assertEquals(2, r.servers.size)
        val jp = r.servers[0]
        assertEquals("vpngate:219.100.37.1:443:tcp", jp.id)
        assertEquals("JP", jp.countryCode)
        assertEquals("tcp", jp.protocol)
        assertEquals(12, jp.reportedPingMs)
        assertEquals("2weeks", jp.logPolicy)
        assertEquals("Daiyuu Nobori, Japan. Academic Use Only.", jp.operator)
        assertEquals("South Korea", r.servers[1].countryName)
        assertEquals("udp", r.servers[1].protocol)
        assertTrue(r.configs.getValue(jp.id).contains("remote 219.100.37.1 443"))
    }

    @Test fun sanitizeRemovesScriptsButKeepsInlineBlocks() {
        val clean = OvpnConfig.sanitize(cfg("tcp", "5.6.7.8", 995))
        assertFalse(clean.contains("script-security"))
        assertFalse(clean.lines().any { it.trim().startsWith("up ") })
        assertTrue(clean.contains("<ca>") && clean.contains("</ca>") && clean.contains("MIIFAKE"))
        assertTrue(clean.contains("remote 5.6.7.8 995"))
    }

    @Test fun remoteParsing() {
        assertEquals(OvpnConfig.Remote("h.example", 1194, "udp"), OvpnConfig.firstRemote("client\nremote h.example"))
        assertEquals("tcp", OvpnConfig.firstRemote("remote a 443 tcp-client")?.proto)
        assertNull(OvpnConfig.firstRemote("client\ndev tun"))
        assertTrue(OvpnConfig.needsUserPass("auth-user-pass\nremote a 1"))
        assertFalse(OvpnConfig.needsUserPass("remote a 1"))
    }
}
