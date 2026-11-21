package com.moni.freeglobalvpn

import android.app.ActivityManager
import android.app.Application
import android.os.Build
import android.os.Process
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.moni.freeglobalvpn.vpn.BankingApps
import de.blinkt.openvpn.core.ICSOpenVPNApplication
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Extends ics-openvpn's Application (sets up its notification channels and the cross-process
 * status bridge to the ":openvpn" service process). App-level objects are created only in the
 * main UI process so the VPN process stays lean.
 */
class FreeVpnApp : ICSOpenVPNApplication() {
    lateinit var container: AppContainer
        private set
    val isReady get() = ::container.isInitialized

    override fun onCreate() {
        super.onCreate()
        if (!isMainProcess()) return
        container = AppContainer(this)

        ProcessLifecycleOwner.get().lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onStart(owner: LifecycleOwner) { container.appVisible.value = true }
            override fun onStop(owner: LifecycleOwner) { container.appVisible.value = false }
        })
        container.repository.startAutoHealth { container.appVisible.value }
        // Automatic server-list updates in the background (every 6 h).
        com.moni.freeglobalvpn.data.ServerUpdateWorker.schedule(this)

        // First launch: pre-select installed banking / MFS apps (bKash, Nagad, banks…) to bypass the VPN.
        container.scope.launch {
            if (!container.settings.current().bypassInitialized) {
                val defaults = withContext(Dispatchers.IO) { BankingApps.defaultBypass(this@FreeVpnApp) }
                container.settings.setBypassApps(defaults)
            }
        }
    }

    private fun isMainProcess(): Boolean {
        val name = if (Build.VERSION.SDK_INT >= 28) Application.getProcessName() else {
            val am = getSystemService(ActivityManager::class.java)
            am?.runningAppProcesses?.firstOrNull { it.pid == Process.myPid() }?.processName
        }
        return name == null || name == packageName
    }
}
