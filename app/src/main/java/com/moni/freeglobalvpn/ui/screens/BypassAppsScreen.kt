package com.moni.freeglobalvpn.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moni.freeglobalvpn.ui.MainViewModel
import com.moni.freeglobalvpn.ui.components.SectionTitle
import com.moni.freeglobalvpn.vpn.BankingApps
import com.moni.freeglobalvpn.vpn.ConnectionState

@Composable
fun BypassAppsScreen(vm: MainViewModel, onBack: () -> Unit) {
    val settings by vm.settings.collectAsStateWithLifecycle()
    val conn by vm.connection.collectAsStateWithLifecycle()
    val apps by produceState<List<BankingApps.AppInfo>?>(null) { value = vm.installedApps() }
    var query by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(top = 8.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Apps that bypass the VPN", style = MaterialTheme.typography.titleLarge)
        }
        Surface(Modifier.padding(horizontal = 16.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                "Checked apps (bKash, Nagad, Rocket, banks…) are excluded from the tunnel and always use your normal " +
                    "Bangladeshi connection, so they work without VPN warnings or blocks — while every other app stays protected. " +
                    "No need to switch the VPN off.",
                Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
            )
        }
        if (conn.isActive && conn !is ConnectionState.Disconnected) {
            Text("Changes apply the next time you connect.", Modifier.padding(horizontal = 20.dp, vertical = 6.dp),
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
        }
        OutlinedTextField(
            query, { query = it }, leadingIcon = { Icon(Icons.Filled.Search, null) }, placeholder = { Text("Search apps") },
            singleLine = true, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        val list = apps
        if (list == null) {
            CircularProgressIndicator(Modifier.padding(32.dp).align(Alignment.CenterHorizontally))
            return@Column
        }
        val q = query.trim().lowercase()
        val shown = list.filter { q.isEmpty() || it.label.lowercase().contains(q) || it.packageName.contains(q) }
        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp)) {
            val suggested = shown.filter { it.suggested }
            if (suggested.isNotEmpty()) item { SectionTitle("Suggested: banking & payment apps") }
            items(suggested, key = { "s" + it.packageName }) { AppRow(it, it.packageName in settings.bypassApps) { b -> vm.setBypass(it.packageName, b) } }
            item { SectionTitle("All apps") }
            items(shown.filterNot { it.suggested }, key = { it.packageName }) { AppRow(it, it.packageName in settings.bypassApps) { b -> vm.setBypass(it.packageName, b) } }
        }
    }
}

@Composable
private fun AppRow(app: BankingApps.AppInfo, checked: Boolean, onChange: (Boolean) -> Unit) {
    val icon = remember(app.packageName) { runCatching { app.icon?.toBitmap(96, 96)?.asImageBitmap() }.getOrNull() }
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) Image(icon, null, Modifier.size(36.dp)) else Spacer(Modifier.size(36.dp))
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(app.label, style = MaterialTheme.typography.bodyLarge)
            Text(app.packageName, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Checkbox(checked, onChange)
    }
}
