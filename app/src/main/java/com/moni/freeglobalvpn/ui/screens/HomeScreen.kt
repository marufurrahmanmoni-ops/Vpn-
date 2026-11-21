package com.moni.freeglobalvpn.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moni.freeglobalvpn.data.Countries
import com.moni.freeglobalvpn.ui.MainViewModel
import com.moni.freeglobalvpn.ui.components.AppCard
import com.moni.freeglobalvpn.ui.components.ButtonVisual
import com.moni.freeglobalvpn.ui.components.ConnectButton
import com.moni.freeglobalvpn.ui.components.Flag
import com.moni.freeglobalvpn.ui.components.Fmt
import com.moni.freeglobalvpn.ui.components.StatTile
import com.moni.freeglobalvpn.ui.components.WorldMap
import com.moni.freeglobalvpn.ui.theme.Brand
import com.moni.freeglobalvpn.vpn.ConnectionState
import kotlinx.coroutines.delay
import java.util.Locale

@Composable
fun HomeScreen(
    vm: MainViewModel,
    onConnectRequest: (() -> Unit) -> Unit,
    openServers: () -> Unit,
    openBypass: () -> Unit,
) {
    val conn by vm.connection.collectAsStateWithLifecycle()
    val traffic by vm.traffic.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val net by vm.net.collectAsStateWithLifecycle()
    val countries by vm.countries.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    val serverState by vm.servers.collectAsStateWithLifecycle()
    val speed by vm.speed.collectAsStateWithLifecycle()
    val engineMsg by vm.engineMessage.collectAsStateWithLifecycle()

    val visual = when (conn) {
        is ConnectionState.Connected -> ButtonVisual.ON
        is ConnectionState.Failed -> ButtonVisual.ERROR
        is ConnectionState.Disconnected -> ButtonVisual.OFF
        else -> ButtonVisual.BUSY
    }
    val statusColor = when (visual) {
        ButtonVisual.ON -> Brand.Green
        ButtonVisual.BUSY -> Brand.Amber
        ButtonVisual.ERROR -> Brand.Red
        ButtonVisual.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    val targetCountry = when (val c = conn) {
        is ConnectionState.Connected -> c.server.countryCode
        is ConnectionState.Connecting -> c.server.countryCode
        is ConnectionState.Reconnecting -> c.server.countryCode
        is ConnectionState.Searching -> c.countryCode
        else -> selected?.countryCode
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(10.dp))
        // Header
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(42.dp).background(Brand.gradient, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                androidx.compose.foundation.Image(
                    androidx.compose.ui.res.painterResource(com.moni.freeglobalvpn.R.drawable.ic_launcher_foreground),
                    contentDescription = null, modifier = Modifier.size(42.dp),
                )
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Free Global VPN", style = MaterialTheme.typography.titleLarge)
                Text(
                    "${countries.count { it.isAvailable }} countries · ${serverState.servers.size} " +
                        if (serverState.verifiedAt > 0) "tested-good servers" else "live servers",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            StatusPill(visual, statusColor)
        }

        Spacer(Modifier.height(14.dp))
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
            Box(Modifier.padding(12.dp)) {
                WorldMap(
                    available = countries.filter { it.isAvailable }.map { it.code },
                    target = targetCountry,
                    home = net.homeCountry,
                    connected = conn is ConnectionState.Connected,
                    landColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                    accent = if (conn is ConnectionState.Connected) Brand.Green else MaterialTheme.colorScheme.primary,
                )
            }
        }

        Spacer(Modifier.height(16.dp))
        StatusText(conn, engineMsg, statusColor)

        ConnectButton(
            visual = visual,
            idleRing = MaterialTheme.colorScheme.outline,
            onClick = {
                when (conn) {
                    is ConnectionState.Disconnected, is ConnectionState.Failed -> onConnectRequest { vm.connectSelectedOrQuick() }
                    else -> vm.disconnect()
                }
            },
        )

        // Timer
        if (conn is ConnectionState.Connected) {
            val since = (conn as ConnectionState.Connected).since
            var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
            LaunchedEffect(since) { while (true) { now = System.currentTimeMillis(); delay(1000) } }
            Text(
                Fmt.duration(now - since), fontFamily = FontFamily.Monospace, fontSize = 26.sp,
                fontWeight = FontWeight.Bold, letterSpacing = 1.sp,
            )
            Text("Connection time", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.height(14.dp))

        // Location selector
        LocationCard(
            selected = selected, onClick = openServers,
            latency = selected?.let { s -> serverState.health[s.id]?.latencyMs ?: s.reportedPingMs },
        )
        if (selected != null && (conn is ConnectionState.Disconnected || conn is ConnectionState.Failed)) {
            Spacer(Modifier.height(8.dp))
            FilledTonalButton(onClick = { vm.useSmartConnect(); onConnectRequest { vm.quickConnect() } }, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Text("⚡  Smart Connect – find the fastest server for me")
            }
        }

        Spacer(Modifier.height(14.dp))
        // Live stats
        val (down, downU) = Fmt.speed(traffic.downBps)
        val (up, upU) = Fmt.speed(traffic.upBps)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StatTile("Download", if (conn is ConnectionState.Connected) down else "–", downU, Brand.Cyan, Modifier.weight(1f))
            StatTile("Upload", if (conn is ConnectionState.Connected) up else "–", upU, Brand.Violet, Modifier.weight(1f))
            StatTile("Ping", net.tunnelLatencyMs?.toString() ?: "–", "ms", Brand.Green, Modifier.weight(1f))
        }
        if (conn is ConnectionState.Connected) {
            Text(
                "Session: ↓ ${Fmt.bytes(traffic.totalDown)}   ↑ ${Fmt.bytes(traffic.totalUp)}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }

        if (!settings.backgroundTipDismissed) {
            Spacer(Modifier.height(14.dp))
            com.moni.freeglobalvpn.ui.components.BackgroundCard(compact = true, onDismiss = { vm.dismissBackgroundTip() })
        }

        Spacer(Modifier.height(14.dp))
        IpCard(vm, conn, net)

        Spacer(Modifier.height(12.dp))
        AppCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Speed test", style = MaterialTheme.typography.titleMedium)
                    Text(
                        when (val s = speed) {
                            is MainViewModel.SpeedState.Idle -> "Measures real throughput${if (conn is ConnectionState.Connected) " through the VPN" else ""}"
                            is MainViewModel.SpeedState.Running -> s.step
                            is MainViewModel.SpeedState.Done -> String.format(Locale.US, "↓ %.1f Mbps   ↑ %s", s.result.downMbps,
                                s.result.upMbps?.let { String.format(Locale.US, "%.1f Mbps", it) } ?: "n/a")
                            is MainViewModel.SpeedState.Error -> s.message
                        },
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                OutlinedButton(onClick = vm::runSpeedTest, enabled = speed !is MainViewModel.SpeedState.Running) { Text("Run") }
            }
        }

        Spacer(Modifier.height(12.dp))
        AppCard(Modifier.clickable(onClick = openBypass)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Banking apps bypass VPN", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (settings.bypassApps.isEmpty()) "No apps excluded – tap to choose bKash, Nagad, banks…"
                        else "${settings.bypassApps.size} app(s) use your normal connection (bKash, Nagad, banks work normally)",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun StatusPill(visual: ButtonVisual, color: Color) {
    val text = when (visual) {
        ButtonVisual.ON -> "Protected"
        ButtonVisual.BUSY -> "Working"
        ButtonVisual.ERROR -> "Error"
        ButtonVisual.OFF -> "Unprotected"
    }
    Surface(shape = CircleShape, color = color.copy(alpha = 0.14f)) {
        Row(Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).background(color, CircleShape))
            Spacer(Modifier.width(6.dp))
            Text(text, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}

@Composable
private fun StatusText(conn: ConnectionState, engineMsg: String, color: Color) {
    AnimatedContent(conn, transitionSpec = { fadeIn() togetherWith fadeOut() }, contentKey = { it::class }, label = "status") { state ->
        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
            val (title, sub) = when (state) {
                is ConnectionState.Disconnected -> "Not connected" to "One tap: the app finds the fastest tested server and connects"
                is ConnectionState.Searching -> "Finding the best server…" to
                    "Measuring ${state.candidates} tested servers${state.countryCode?.let { " in " + Countries.name(it) } ?: ""} from your phone"
                is ConnectionState.Connecting -> "Connecting…" to buildString {
                    append(state.detail.ifBlank { engineMsg })
                    if (state.maxAttempts > 1 && state.attempt > 1) append("  (server ${state.attempt}/${state.maxAttempts})")
                }
                is ConnectionState.Connected -> "Connected" to "${state.server.countryName} · ${state.server.protocol.uppercase()} ${state.server.port}"
                is ConnectionState.Reconnecting -> "Reconnecting…" to state.detail
                is ConnectionState.WaitingForNetwork -> "Waiting for network" to "Will reconnect automatically when you're back online"
                is ConnectionState.Paused -> "Paused" to "VPN paused by the system (e.g. screen off setting)"
                is ConnectionState.Failed -> "Connection failed" to state.reason
                is ConnectionState.Disconnecting -> "Disconnecting…" to ""
            }
            Text(title, style = MaterialTheme.typography.headlineSmall, color = color, fontWeight = FontWeight.Bold)
            if (sub.isNotBlank()) Text(
                sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center, maxLines = 3, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
private fun LocationCard(selected: com.moni.freeglobalvpn.data.VpnServer?, latency: Int?, onClick: () -> Unit) {
    AppCard(Modifier.clickable(onClick = onClick)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (selected != null) Flag(selected.countryCode, 26) else Flag("", 26)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    selected?.countryName ?: "⚡ Smart Connect (automatic)",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    selected?.let { "${it.hostName} · ${latency?.let { l -> "$l ms" } ?: "latency unknown"}" }
                        ?: "Fastest tested & secure server, measured from your phone",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Text("Change", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun IpCard(vm: MainViewModel, conn: ConnectionState, net: MainViewModel.NetInfo) {
    val connected = conn is ConnectionState.Connected
    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                if (connected) Icons.Filled.CheckCircle else Icons.Filled.Warning, null,
                tint = if (connected) Brand.Green else Brand.Amber,
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(if (connected) "Your IP is hidden" else "Your real IP is visible", style = MaterialTheme.typography.titleMedium)
                val ip = net.ip
                Text(
                    when {
                        net.checking -> "Checking public IP…"
                        ip == null -> "Public IP unknown (offline?)"
                        else -> buildString {
                            append(ip.ip)
                            ip.countryCode?.let { append("  ").append(Countries.flag(it)) }
                            listOfNotNull(ip.city, ip.country).takeIf { it.isNotEmpty() }?.let { append("  ").append(it.joinToString(", ")) }
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace,
                )
                if (connected) {
                    Text(
                        if (net.dns.isNotEmpty()) "DNS via tunnel: ${net.dns.joinToString(", ")}" else "DNS: checking…",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                net.ip?.isp?.let {
                    Text("Network: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = vm::refreshNetInfo) { Icon(Icons.Filled.Refresh, "Re-check IP") }
        }
        AnimatedVisibility(connected && net.ip?.countryCode != null && net.ip.countryCode != (conn as? ConnectionState.Connected)?.server?.countryCode) {
            Text(
                "Note: IP databases sometimes place a server in a different country than its operator reports.",
                style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

