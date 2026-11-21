package com.moni.freeglobalvpn.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moni.freeglobalvpn.BuildConfig
import com.moni.freeglobalvpn.R
import com.moni.freeglobalvpn.ui.MainViewModel
import com.moni.freeglobalvpn.ui.components.AppCard
import com.moni.freeglobalvpn.ui.components.Fmt
import com.moni.freeglobalvpn.ui.components.SectionTitle

private const val REPO_URL = "https://github.com/marufurrahmanmoni-ops/Vpn-"

/** Developer options: developer profile, app & server-list diagnostics, connection log. */
@Composable
fun DeveloperScreen(vm: MainViewModel, onBack: () -> Unit, openAbout: () -> Unit) {
    val context = LocalContext.current
    val st by vm.servers.collectAsStateWithLifecycle()
    val countries by vm.countries.collectAsStateWithLifecycle()
    var log by remember { mutableStateOf<List<String>?>(null) }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Developer options", style = MaterialTheme.typography.titleLarge)
        }

        // ---- Developer profile
        Box(
            Modifier.padding(16.dp).fillMaxWidth().clip(RoundedCornerShape(26.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF3B2FD9), Color(0xFF0EA5E9))))
                .padding(vertical = 26.dp, horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Image(
                    painter = painterResource(R.drawable.developer_photo),
                    contentDescription = "Photo of $DEVELOPER_NAME",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(120.dp).clip(CircleShape).border(4.dp, Color.White, CircleShape),
                )
                Spacer(Modifier.height(14.dp))
                Text(DEVELOPER_NAME, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Text("Developer · Free Global VPN", color = Color.White.copy(alpha = 0.85f), style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(10.dp))
                Surface(shape = CircleShape, color = Color.White.copy(alpha = 0.18f)) {
                    Text("v${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", color = Color.White,
                        style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp))
                }
            }
        }

        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionTitle("Server list")
            AppCard {
                Info("Tested-good servers", "${st.servers.size} in ${countries.count { it.isAvailable }} countries")
                Info("List tested", if (st.verifiedAt > 0) Fmt.ago(st.verifiedAt) else "–")
                Info("Last update check", Fmt.ago(st.fetchedAt))
                Info("Reachable from this phone", "${st.health.values.count { it.reachable }} / ${st.health.size} probed")
                st.providerErrors.forEach { (k, v) -> Info("Note ($k)", v) }
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = vm::updateServers, enabled = !st.refreshing) { Text(if (st.refreshing) "Updating…" else "Update servers now") }
                    OutlinedButton(onClick = vm::retestServers, enabled = !st.checkingHealth) { Text(if (st.checkingHealth) "Testing…" else "Re-test") }
                }
                TextButton(onClick = { vm.resetServerHealth(); toast(context, "Server measurements cleared") }) {
                    Text("Reset server measurements")
                }
            }

            SectionTitle("App & device")
            AppCard {
                Info("Package", BuildConfig.APPLICATION_ID)
                Info("Build", "${BuildConfig.BUILD_TYPE} · v${BuildConfig.VERSION_NAME}")
                Info("Android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
                Info("Device ABI", Build.SUPPORTED_ABIS.firstOrNull() ?: "?")
                Info("VPN engine", "OpenVPN 2.x (ics-openvpn)")
                Info("List signature key", if (BuildConfig.LIST_PUBLIC_KEY.isBlank()) "not set (HTTPS only)" else "set")
            }

            SectionTitle("Connection log")
            AppCard {
                Text("Engine messages from this session (stays on your phone).", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(onClick = { log = vm.connectionLog() }) { Text(if (log == null) "Show log" else "Refresh") }
                    if (!log.isNullOrEmpty()) TextButton(onClick = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("VPN log", log!!.joinToString("\n")))
                        toast(context, "Log copied")
                    }) { Text("Copy") }
                }
                log?.let { lines ->
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                        Text(
                            if (lines.isEmpty()) "No messages yet." else lines.joinToString("\n"),
                            fontFamily = FontFamily.Monospace, fontSize = 11.sp,
                            modifier = Modifier.fillMaxWidth().heightIn(max = 320.dp).verticalScroll(rememberScrollState())
                                .horizontalScroll(rememberScrollState()).padding(10.dp),
                        )
                    }
                }
            }

            SectionTitle("Project")
            AppCard {
                TextButton(onClick = { runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(REPO_URL))) } }) {
                    Text("Source code & server tests on GitHub")
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
                TextButton(onClick = openAbout) { Text("About, licenses & credits") }
            }
            Spacer(Modifier.height(28.dp))
        }
    }
}

@Composable
private fun Info(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}

private fun toast(c: Context, s: String) = Toast.makeText(c, s, Toast.LENGTH_SHORT).show()
