package com.moni.freeglobalvpn

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.moni.freeglobalvpn.data.Settings
import com.moni.freeglobalvpn.data.provider.VpnGateParser
import com.moni.freeglobalvpn.vpn.ProfileFactory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Feeds a REAL VPN Gate API response through our parser and the actual ics-openvpn ConfigParser /
 * VpnProfile code, and checks that every server becomes a valid, hardened OpenVPN configuration.
 * Skipped unless -Pvpngate.snapshot=<file> is given (no network access in unit tests).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = android.app.Application::class)
class RealVpnGateProfileTest {
    @Test fun everyRealServerProducesAValidHardenedProfile() {
        val path = System.getProperty("vpngate.snapshot")
        assumeTrue("set -Pvpngate.snapshot to run", path != null && File(path).exists())
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        val result = VpnGateParser.parse(File(path!!).readText())
        assertTrue("snapshot should contain servers", result.servers.isNotEmpty())
        println("Parsed ${result.servers.size} real servers in ${result.servers.map { it.countryCode }.toSet().sorted()}")

        val settings = Settings()
        for (s in result.servers) {
            val p = ProfileFactory.build(s, result.configs.getValue(s.id), settings, setOf("com.bKash.customerapp"), "vpn" to "vpn")
            val check = p.checkProfile(ctx, false)
            assertEquals("${s.id}: ${ctx.getString(check)}", de.blinkt.openvpn.R.string.no_error_found, check)
            val cfg = p.getConfigFile(ctx, false)
            assertTrue(s.id, cfg.lineSequence().any { it.startsWith("remote ") && it.contains(s.ip) })
            assertTrue("${s.id} must allow server cipher", cfg.contains("data-ciphers AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305:AES-128-CBC"))
            assertTrue(s.id, cfg.contains("data-ciphers-fallback AES-128-CBC"))
            assertTrue("DNS forced", cfg.contains("dhcp-option DNS 1.1.1.1") || p.mOverrideDNS)
            assertTrue("server cert name pinned", cfg.contains("verify-x509-name opengw.net name") && cfg.contains("remote-cert-tls server"))
            assertFalse("no scripts", cfg.lineSequence().any { it.trim().startsWith("up ") || it.startsWith("script-security 2") })
            assertTrue("bypass list carried", "com.bKash.customerapp" in p.mAllowedAppsVpn && p.mAllowedAppsVpnAreDisallowed)
        }
        println("All ${result.servers.size} profiles valid. Sample generated config:\n" +
            ProfileFactory.build(result.servers.first(), result.configs.getValue(result.servers.first().id), settings, emptySet(), null)
                .getConfigFile(ctx, false).lineSequence().filterNot { it.startsWith("-----") || it.length > 60 && !it.contains(' ') }.take(45).joinToString("\n"))
    }
}
