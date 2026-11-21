package com.moni.freeglobalvpn.vpn

import com.moni.freeglobalvpn.data.Countries
import com.moni.freeglobalvpn.data.DnsChoice
import com.moni.freeglobalvpn.data.Settings
import com.moni.freeglobalvpn.data.VpnServer
import com.moni.freeglobalvpn.data.provider.OvpnConfig
import de.blinkt.openvpn.VpnProfile
import de.blinkt.openvpn.core.ConfigParser
import java.io.StringReader

/** Turns a provider's OpenVPN config into a hardened ics-openvpn [VpnProfile]. */
object ProfileFactory {
    private val modernCiphers = setOf("AES-256-GCM", "AES-128-GCM", "CHACHA20-POLY1305")

    fun build(
        server: VpnServer,
        rawConfig: String,
        settings: Settings,
        bypassApps: Set<String>,
        credentials: Pair<String, String>?,
    ): VpnProfile {
        val config = OvpnConfig.sanitize(rawConfig)
        val parser = ConfigParser()
        parser.parseConfig(StringReader(config))
        val p = parser.convertProfile()

        p.mName = "${Countries.flag(server.countryCode)} ${server.countryName} · ${server.hostName}"

        // VPN Gate / SoftEther servers do not negotiate data ciphers (no NCP) and use AES-128-CBC.
        // OpenVPN 2.6 only accepts a legacy server cipher if it is listed in --data-ciphers, so
        // append it (same rule ics-openvpn applies when upgrading old profiles) and set it as fallback.
        val legacy = p.mCipher?.uppercase()?.takeIf { it.isNotEmpty() && it !in modernCiphers }
        if (legacy != null && p.mDataCiphers.isNullOrEmpty()) {
            p.mDataCiphers = "AES-256-GCM:AES-128-GCM:CHACHA20-POLY1305:$legacy"
            p.mUseCustomConfig = true
            p.mCustomConfigOptions = (p.mCustomConfigOptions ?: "") + "\ndata-ciphers-fallback $legacy\n"
        }

        // DNS leak protection: force resolvers that are reached *through* the tunnel.
        if (settings.dns != DnsChoice.SERVER) {
            p.mOverrideDNS = true
            p.mDNS1 = settings.dns.primary
            p.mDNS2 = settings.dns.secondary
        }
        // IPv6 leak protection: address families the server does not route are blocked, not bypassed.
        p.mBlockUnusedAddressFamilies = true
        p.mAllowAppVpnBypass = false
        p.mAllowLocalLAN = settings.allowLan
        // Keep the tun device up while reconnecting so traffic is blocked instead of leaking.
        p.mPersistTun = settings.blockWhileReconnecting
        // Banking / MFS apps (bKash, Nagad, banks…) bypass the tunnel and keep the real IP.
        p.mAllowedAppsVpnAreDisallowed = true
        p.mAllowedAppsVpn = HashSet(bypassApps)

        if (OvpnConfig.needsUserPass(config) && p.mUsername.isNullOrEmpty() && credentials != null) {
            p.mUsername = credentials.first
            p.mPassword = credentials.second
        }
        return p
    }
}
