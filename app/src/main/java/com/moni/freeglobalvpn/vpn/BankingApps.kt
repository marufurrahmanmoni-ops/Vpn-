package com.moni.freeglobalvpn.vpn

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable

/**
 * Banking / mobile-financial-service apps usually block or flag VPN IPs (bKash, Nagad, banks...).
 * Apps in the bypass list are excluded from the tunnel with VpnService.Builder.addDisallowedApplication,
 * so they keep using your normal Bangladeshi connection while every other app stays on the VPN.
 */
object BankingApps {
    /** Known package names (only matched against apps that are actually installed). */
    val known = mapOf(
        "com.bKash.customerapp" to "bKash",
        "com.bkash.businessapp" to "bKash Merchant",
        "com.konasl.nagad" to "Nagad",
        "com.dbbl.mbs.apps.main" to "Rocket (DBBL)",
        "com.ibbl.cellfin" to "CellFin (Islami Bank)",
        "com.ibbl.merchant" to "CellFin Merchant",
        "com.bracbank.astha" to "BRAC Bank Astha",
        "com.thecitybank.citytouch" to "City Bank Citytouch",
        "com.upay.android" to "Upay",
        "com.ebl.skybanking" to "EBL Skybanking",
        "com.sonalibank.sonalieservice" to "Sonali eWallet",
        "com.mtb.mobilebanking" to "MTB Smart Banking",
        "com.paypal.android.p2pmobile" to "PayPal",
        "com.google.android.apps.walletnfcrel" to "Google Wallet",
    )

    private val keywords = listOf("bank", "bkash", "nagad", "rocket", "upay", "cellfin", "wallet", "pay", "finance", "mfs")

    data class AppInfo(val packageName: String, val label: String, val suggested: Boolean, val icon: Drawable?)

    /** All launchable apps (needs the <queries> LAUNCHER entry in the manifest – no QUERY_ALL_PACKAGES). */
    fun installedApps(context: Context): List<AppInfo> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        @Suppress("DEPRECATION")
        val list = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
        return list.map { it.activityInfo.applicationInfo }
            .distinctBy { it.packageName }
            .filter { it.packageName != context.packageName }
            .map { ai ->
                val label = ai.loadLabel(pm).toString()
                AppInfo(ai.packageName, label, isLikelyFinancial(ai.packageName, label), runCatching { ai.loadIcon(pm) }.getOrNull())
            }
            .sortedWith(compareBy<AppInfo> { !it.suggested }.thenBy { it.label.lowercase() })
    }

    fun isLikelyFinancial(pkg: String, label: String): Boolean {
        if (pkg in known) return true
        val p = pkg.lowercase(); val l = label.lowercase()
        return keywords.any { p.contains(it) || l.contains(it) }
    }

    /** Default bypass set on first launch: installed apps we are confident are banking/MFS apps. */
    fun defaultBypass(context: Context): Set<String> =
        installedApps(context).filter { it.packageName in known || it.label.lowercase().let { l -> "bank" in l || "bkash" in l || "nagad" in l } }
            .map { it.packageName }.toSet()
}
