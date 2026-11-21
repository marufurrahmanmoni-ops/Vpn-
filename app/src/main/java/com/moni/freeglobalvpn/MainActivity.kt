package com.moni.freeglobalvpn

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.moni.freeglobalvpn.ui.MainViewModel
import com.moni.freeglobalvpn.ui.screens.AboutScreen
import com.moni.freeglobalvpn.ui.screens.BypassAppsScreen
import com.moni.freeglobalvpn.ui.screens.HomeScreen
import com.moni.freeglobalvpn.ui.screens.PrivacyScreen
import com.moni.freeglobalvpn.ui.screens.ServersScreen
import com.moni.freeglobalvpn.ui.screens.SettingsScreen
import com.moni.freeglobalvpn.ui.theme.FreeVpnTheme

class MainActivity : ComponentActivity() {
    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen() // dismissed on first frame – UI renders immediately from the disk cache
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val settings by vm.settings.collectAsStateWithLifecycle()
            FreeVpnTheme(settings.theme) { AppRoot(vm, showDisclosure = !settings.acceptedDisclosure) }
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun AppRoot(vm: MainViewModel, showDisclosure: Boolean) {
    val context = LocalContext.current
    val nav = rememberNavController()
    val tabs = remember {
        listOf(Tab("home", "Home", Icons.Filled.Home), Tab("servers", "Locations", Icons.Filled.LocationOn), Tab("settings", "Settings", Icons.Filled.Settings))
    }
    val backStack by nav.currentBackStackEntryAsState()
    val route = backStack?.destination?.route

    // --- VPN permission (system consent dialog) + notification permission, then run the action.
    var pending by remember { mutableStateOf<(() -> Unit)?>(null) }
    val vpnConsent = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) pending?.invoke()
        else Toast.makeText(context, "VPN permission is required to connect", Toast.LENGTH_LONG).show()
        pending = null
    }
    val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    val requestConnect: (() -> Unit) -> Unit = { action ->
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
        val intent = VpnService.prepare(context)
        if (intent == null) action() else { pending = action; vpnConsent.launch(intent) }
    }
    val goHome = { nav.navigate("home") { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } }
    val toast: (String) -> Unit = { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (route in tabs.map { it.route }) NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                tabs.forEach { t ->
                    NavigationBarItem(
                        selected = route == t.route,
                        onClick = { nav.navigate(t.route) { popUpTo(nav.graph.findStartDestination().id) { saveState = true }; launchSingleTop = true; restoreState = true } },
                        icon = { Icon(t.icon, null) }, label = { Text(t.label) },
                    )
                }
            }
        },
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            NavHost(nav, startDestination = "home") {
                composable("home") { HomeScreen(vm, requestConnect, openServers = { nav.navigate("servers") }, openBypass = { nav.navigate("bypass") }) }
                composable("servers") { ServersScreen(vm, requestConnect, onConnected = goHome) }
                composable("settings") {
                    SettingsScreen(vm, openBypass = { nav.navigate("bypass") }, openAbout = { nav.navigate("about") },
                        openPrivacy = { nav.navigate("privacy") }, toast = toast)
                }
                composable("bypass") { BypassAppsScreen(vm) { nav.popBackStack() } }
                composable("about") { AboutScreen { nav.popBackStack() } }
                composable("privacy") { PrivacyScreen(vm) { nav.popBackStack() } }
            }
        }
    }

    if (showDisclosure) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Before you start") },
            text = {
                Column {
                    Text(
                        "Free Global VPN connects you to free public servers run by volunteers (VPN Gate, University of Tsukuba). " +
                            "Your traffic is encrypted to the server, but each server's operator sets their own logging policy and can " +
                            "see traffic that leaves their server unencrypted. This app itself collects no personal data and no browsing history.\n\n" +
                            "Banking and payment apps (bKash, Nagad, banks) bypass the VPN by default so they keep working normally.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            },
            confirmButton = { TextButton(onClick = vm::acceptDisclosure) { Text("I understand") } },
        )
    }
}
