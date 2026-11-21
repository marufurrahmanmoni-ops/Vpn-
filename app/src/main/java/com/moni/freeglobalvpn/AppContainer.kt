package com.moni.freeglobalvpn

import android.app.Application
import com.moni.freeglobalvpn.data.SecureStore
import com.moni.freeglobalvpn.data.ServerCache
import com.moni.freeglobalvpn.data.ServerRepository
import com.moni.freeglobalvpn.data.SettingsStore
import com.moni.freeglobalvpn.data.provider.ImportedConfigProvider
import com.moni.freeglobalvpn.data.provider.VerifiedListProvider
import com.moni.freeglobalvpn.vpn.VpnController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

/** Manual dependency container – no DI framework, which keeps cold start fast. */
class AppContainer(app: Application) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsStore(app)
    val secureStore = SecureStore(app)
    val importedProvider = ImportedConfigProvider(secureStore)
    val repository = ServerRepository(
        // Only servers that passed the real tunnel + security test are offered (no untested live list).
        providers = listOf(
            VerifiedListProvider(
                publicKeyB64 = BuildConfig.LIST_PUBLIC_KEY,
                bundled = { runCatching { app.assets.open("verified_servers.json").bufferedReader().use { it.readText() } }.getOrNull() },
            ),
            importedProvider,
        ),
        cache = ServerCache(app),
        scope = scope,
        // If a VPN is already up (e.g. app restarted while connected) don't probe through the tunnel.
        initialProbesPaused = hasVpnNetwork(app),
    )
    val vpn = VpnController(app, repository, settings, scope)
    /** True while any activity of the app is in the foreground (used to stop polling in background). */
    val appVisible = MutableStateFlow(false)
}

private fun hasVpnNetwork(context: Context): Boolean {
    val cm = context.getSystemService(ConnectivityManager::class.java) ?: return false
    @Suppress("DEPRECATION")
    return cm.allNetworks.any { cm.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true }
}
