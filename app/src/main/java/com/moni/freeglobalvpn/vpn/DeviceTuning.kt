package com.moni.freeglobalvpn.vpn

import android.annotation.SuppressLint
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings

/**
 * Realme/OPPO (ColorOS / realme UI), Xiaomi/Redmi/POCO (MIUI / HyperOS) and Samsung (One UI) stop
 * background apps aggressively, which kills a VPN. These helpers open the exact screens where the
 * user can let the VPN keep running. Every intent is tried in order and falls back safely.
 */
object DeviceTuning {
    enum class Brand(val label: String) {
        XIAOMI("Xiaomi / Redmi / POCO"), REALME_OPPO("Realme / OPPO / OnePlus"), SAMSUNG("Samsung"),
        VIVO("vivo / iQOO"), OTHER("Android")
    }

    val brand: Brand by lazy {
        val m = (Build.MANUFACTURER + " " + Build.BRAND).lowercase()
        when {
            listOf("xiaomi", "redmi", "poco").any { it in m } -> Brand.XIAOMI
            listOf("realme", "oppo", "oneplus").any { it in m } -> Brand.REALME_OPPO
            "samsung" in m -> Brand.SAMSUNG
            listOf("vivo", "iqoo").any { it in m } -> Brand.VIVO
            else -> Brand.OTHER
        }
    }

    fun isUnrestricted(context: Context): Boolean =
        context.getSystemService(PowerManager::class.java)?.isIgnoringBatteryOptimizations(context.packageName) == true

    /** Step-by-step instructions for this phone. */
    fun steps(): List<String> = when (brand) {
        Brand.XIAOMI -> listOf(
            "Allow background running → Battery saver: choose \"No restrictions\".",
            "Autostart: turn ON for Free Global VPN.",
            "Open Recents, pull the app card down (or long-press) and tap the lock so it is never cleared.",
        )
        Brand.REALME_OPPO -> listOf(
            "Allow background running → Battery: choose \"Allow background activity\" / \"Don't optimise\".",
            "Auto launch / Startup manager: turn ON for Free Global VPN.",
            "Open Recents, tap ⋮ on the app card and choose \"Lock\".",
        )
        Brand.SAMSUNG -> listOf(
            "Allow background running → Battery: choose \"Unrestricted\".",
            "Settings → Battery → Background usage limits: make sure the app is NOT in \"Sleeping\" or \"Deep sleeping\" apps; add it to \"Never sleeping apps\".",
        )
        Brand.VIVO -> listOf(
            "Allow background running → choose \"Allow high background power consumption\".",
            "i Manager → App manager → Autostart: turn ON.",
        )
        Brand.OTHER -> listOf("Allow background running → choose \"Unrestricted\" / \"Don't optimise\".")
    }

    /** Ask Android to exempt the app from battery optimisation (system dialog). */
    @SuppressLint("BatteryLife")
    fun openBatteryExemption(context: Context) {
        val pkg = context.packageName
        start(context,
            Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:$pkg")),
            Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS),
            appDetails(context))
    }

    /** The manufacturer's own auto-start / background screen. */
    fun openOemBackgroundSettings(context: Context) {
        val pkg = context.packageName
        val label = "Free Global VPN"
        val list = when (brand) {
            Brand.XIAOMI -> listOf(
                Intent("miui.intent.action.HIDDEN_APPS_CONFIG_ACTIVITY").putExtra("package_name", pkg).putExtra("package_label", label),
                cmp("com.miui.powerkeeper", "com.miui.powerkeeper.ui.HiddenAppsConfigActivity").putExtra("package_name", pkg).putExtra("package_label", label),
                cmp("com.miui.securitycenter", "com.miui.permcenter.autostart.AutoStartManagementActivity"),
            )
            Brand.REALME_OPPO -> listOf(
                cmp("com.coloros.safecenter", "com.coloros.safecenter.permission.startup.StartupAppListActivity"),
                cmp("com.coloros.safecenter", "com.coloros.safecenter.startupapp.StartupAppListActivity"),
                cmp("com.oplus.safecenter", "com.oplus.safecenter.startupapp.view.StartupAppListActivity"),
                cmp("com.oppo.safe", "com.oppo.safe.permission.startup.StartupAppListActivity"),
                cmp("com.coloros.oppoguardelf", "com.coloros.powermanager.fuelgaue.PowerUsageModelActivity"),
            )
            Brand.SAMSUNG -> listOf(
                cmp("com.samsung.android.lool", "com.samsung.android.sm.battery.ui.BatteryActivity"),
                cmp("com.samsung.android.lool", "com.samsung.android.sm.ui.battery.BatteryActivity"),
                cmp("com.samsung.android.sm", "com.samsung.android.sm.battery.ui.BatteryActivity"),
            )
            Brand.VIVO -> listOf(
                cmp("com.iqoo.secure", "com.iqoo.secure.ui.phoneoptimize.AddWhiteListActivity"),
                cmp("com.vivo.permissionmanager", "com.vivo.permissionmanager.activity.BgStartUpManagerActivity"),
            )
            Brand.OTHER -> emptyList()
        }
        start(context, *(list + appDetails(context)).toTypedArray())
    }

    fun openVpnSettings(context: Context) =
        start(context, Intent("android.settings.VPN_SETTINGS"), Intent(Settings.ACTION_WIRELESS_SETTINGS))

    private fun cmp(pkg: String, cls: String) = Intent().setComponent(ComponentName(pkg, cls))

    private fun appDetails(context: Context) =
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + context.packageName))

    private fun start(context: Context, vararg intents: Intent) {
        for (i in intents) {
            try {
                context.startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: Exception) { /* not on this phone – try the next one */ }
        }
    }
}
