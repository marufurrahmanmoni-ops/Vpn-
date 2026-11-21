package com.moni.freeglobalvpn.ui.screens

import android.content.Intent
import android.net.Uri
import android.provider.Settings as AndroidSettings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moni.freeglobalvpn.data.DnsChoice
import com.moni.freeglobalvpn.data.ThemeMode
import com.moni.freeglobalvpn.ui.MainViewModel
import com.moni.freeglobalvpn.ui.components.AppCard
import com.moni.freeglobalvpn.ui.components.SectionTitle

@Composable
fun SettingsScreen(vm: MainViewModel, openBypass: () -> Unit, openAbout: () -> Unit, openPrivacy: () -> Unit, toast: (String) -> Unit) {
    val s by vm.settings.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var pendingUri by remember { mutableStateOf<Uri?>(null) }
    var importsVersion by remember { mutableIntStateOf(0) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> pendingUri = uri }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.padding(top = 14.dp, start = 4.dp))

        SectionTitle("Appearance")
        AppCard {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemeMode.entries.forEach { m ->
                    FilterChip(s.theme == m, { vm.setTheme(m) }, { Text(m.name.lowercase().replaceFirstChar { it.uppercase() }) })
                }
            }
        }

        SectionTitle("Connection")
        AppCard {
            ToggleRow("Auto-reconnect", "Restart the tunnel automatically if it drops", s.autoReconnect, vm::setAutoReconnect)
            Divider()
            ToggleRow("Smart failover", "If a free server doesn't answer, try the next best one (up to 4)", s.smartFailover, vm::setSmartFailover)
            Divider()
            ToggleRow("Block traffic while reconnecting", "Keeps the tunnel interface up during reconnects so apps can't leak outside the VPN", s.blockWhileReconnecting, vm::setBlockWhileReconnecting)
            Divider()
            ToggleRow("Allow local network", "Reach printers / Chromecast on your Wi-Fi while connected", s.allowLan, vm::setAllowLan)
        }

        SectionTitle("DNS leak protection")
        AppCard {
            Text("DNS queries are sent through the encrypted tunnel to:", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.height(6.dp))
            DnsChoice.entries.forEach { d ->
                Row(Modifier.fillMaxWidth().clickable { vm.setDns(d) }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    androidx.compose.material3.RadioButton(selected = s.dns == d, onClick = { vm.setDns(d) })
                    Text(d.label)
                }
            }
            Text("IPv6 is blocked inside the tunnel when the server has no IPv6, so it can't leak.", style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        SectionTitle("Kill switch")
        AppCard {
            Text(
                "Android's built-in kill switch blocks ALL internet whenever the VPN is off. Open VPN settings → Free Global VPN → " +
                    "enable \"Always-on VPN\" and \"Block connections without VPN\". Note: with this on, apps you excluded " +
                    "(e.g. bKash) are blocked too while the VPN is down.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(onClick = { runCatching { context.startActivity(Intent(AndroidSettings.ACTION_VPN_SETTINGS)) } }) {
                Text("Open Android VPN settings")
            }
        }

        SectionTitle("Split tunnelling")
        AppCard(Modifier.clickable(onClick = openBypass)) {
            NavRow("Apps that bypass the VPN", "${s.bypassApps.size} selected – bKash, Nagad & banks keep your real IP")
        }

        SectionTitle("My servers (import)")
        AppCard {
            Text(
                "Import an OpenVPN (.ovpn) file from a provider you have an account with – for example the free plan of " +
                    "Proton VPN – or from your own server. Files and passwords are encrypted with a key kept in the Android Keystore.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            val entries = remember(importsVersion, s) { vm.importedEntries() }
            entries.forEach { e ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${com.moni.freeglobalvpn.data.Countries.flag(e.countryCode)}  ${e.name}", Modifier.weight(1f))
                    IconButton(onClick = { vm.removeImported(e.id); importsVersion++ }) { Icon(Icons.Filled.Delete, "Remove") }
                }
            }
            TextButton(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Import .ovpn file") }
        }

        SectionTitle("About")
        AppCard {
            Column(Modifier.clickable(onClick = openPrivacy)) { NavRow("Privacy & server logging", "What is (and isn't) logged, by whom") }
            Divider()
            Column(Modifier.clickable(onClick = openAbout)) { NavRow("About & developer", "Md. Marufur Rahman Moni") }
        }
        Spacer(Modifier.height(28.dp))
    }

    pendingUri?.let { uri ->
        ImportDialog(
            onDismiss = { pendingUri = null },
            onImport = { name, cc, user, pass ->
                vm.importConfig(uri, name, cc, user, pass) { msg -> toast(msg); importsVersion++ }
                pendingUri = null
            },
        )
    }
}

@Composable
private fun Divider() = HorizontalDivider(Modifier.padding(vertical = 8.dp), color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))

@Composable
private fun ToggleRow(title: String, sub: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }, verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(8.dp))
        Switch(checked, onChange)
    }
}

@Composable
private fun NavRow(title: String, sub: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
    }
}

@Composable
private fun ImportDialog(onDismiss: () -> Unit, onImport: (String, String, String?, String?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var cc by remember { mutableStateOf("") }
    var user by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Import OpenVPN config") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name") }, singleLine = true)
                OutlinedTextField(cc, { if (it.length <= 2) cc = it.uppercase() }, label = { Text("Country code (e.g. NL) – optional") }, singleLine = true)
                OutlinedTextField(user, { user = it }, label = { Text("Username – if required") }, singleLine = true)
                OutlinedTextField(pass, { pass = it }, label = { Text("Password – if required") }, singleLine = true,
                    visualTransformation = androidx.compose.ui.text.input.PasswordVisualTransformation())
            }
        },
        confirmButton = { TextButton(onClick = { onImport(name, cc, user, pass) }) { Text("Import") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
