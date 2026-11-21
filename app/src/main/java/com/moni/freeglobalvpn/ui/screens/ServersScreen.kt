package com.moni.freeglobalvpn.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.moni.freeglobalvpn.data.Availability
import com.moni.freeglobalvpn.data.Countries
import com.moni.freeglobalvpn.data.CountryGroup
import com.moni.freeglobalvpn.data.ServerHealth
import com.moni.freeglobalvpn.data.VpnServer
import com.moni.freeglobalvpn.ui.MainViewModel
import com.moni.freeglobalvpn.ui.components.AvailabilityDot
import com.moni.freeglobalvpn.ui.components.Flag
import com.moni.freeglobalvpn.ui.components.Fmt
import com.moni.freeglobalvpn.ui.theme.Brand

private enum class Tab { ALL, FAVORITES, RECENT }

@Composable
fun ServersScreen(vm: MainViewModel, onConnectRequest: (() -> Unit) -> Unit, onConnected: () -> Unit) {
    val countries by vm.countries.collectAsStateWithLifecycle()
    val st by vm.servers.collectAsStateWithLifecycle()
    val settings by vm.settings.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    var tab by rememberSaveable { mutableStateOf(Tab.ALL) }
    var expanded by rememberSaveable { mutableStateOf<String?>(null) }
    var showInfo by remember { mutableStateOf(false) }

    val filtered = remember(countries, query, tab, settings.favorites) {
        val q = query.trim().lowercase()
        countries.filter { g ->
            (q.isEmpty() || g.name.lowercase().contains(q) || g.code.lowercase() == q) &&
                when (tab) {
                    Tab.FAVORITES -> "c:${g.code}" in settings.favorites || g.servers.any { "s:${it.id}" in settings.favorites }
                    else -> true
                }
        }
    }

    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(start = 18.dp, end = 8.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Locations", style = MaterialTheme.typography.headlineSmall)
                Text(
                    (if (st.verifiedAt > 0) "✓ ${st.servers.size} tested-good servers in ${countries.count { it.isAvailable }} countries · tested ${Fmt.ago(st.verifiedAt)}"
                    else "${countries.count { it.isAvailable }} countries with live servers · updated ${Fmt.ago(st.fetchedAt)}") +
                        if (st.checkingHealth) " · checking…" else "",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = { showInfo = !showInfo }) { Icon(Icons.Filled.Info, "About these servers") }
            val rot by rememberInfiniteTransition(label = "r").animateFloat(0f, 360f, infiniteRepeatable(tween(900, easing = LinearEasing)), label = "a")
            IconButton(onClick = vm::refreshServers, enabled = !st.refreshing) {
                Icon(Icons.Filled.Refresh, "Refresh list", Modifier.rotate(if (st.refreshing) rot else 0f))
            }
        }

        AnimatedVisibility(showInfo, enter = expandVertically(), exit = shrinkVertically()) {
            Surface(Modifier.padding(horizontal = 16.dp, vertical = 6.dp), shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceVariant) {
                Column(Modifier.padding(14.dp)) {
                    vm.repository.providers().forEach { p ->
                        Text(p.displayName, style = MaterialTheme.typography.titleSmall)
                        Text(p.loggingDisclosure, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(1.dp).padding(4.dp))
                    }
                }
            }
        }
        if (st.unverifiedFallback && st.servers.isNotEmpty()) {
            Surface(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(14.dp), color = Brand.Amber.copy(alpha = 0.14f)) {
                Text(
                    "The tested-good list couldn't be downloaded, so untested live servers are shown. Some may not work – the app will skip them automatically.",
                    Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        st.error?.let {
            Surface(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), shape = RoundedCornerShape(14.dp), color = Brand.Red.copy(alpha = 0.12f)) {
                Text(it, Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall, color = Brand.Red)
            }
        }

        OutlinedTextField(
            value = query, onValueChange = { query = it },
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Search country") },
            singleLine = true, shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(tab == Tab.ALL, { tab = Tab.ALL }, { Text("All") })
            FilterChip(tab == Tab.FAVORITES, { tab = Tab.FAVORITES }, { Text("Favourites") })
            FilterChip(tab == Tab.RECENT, { tab = Tab.RECENT }, { Text("Recent") })
        }

        LazyColumn(contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 8.dp, bottom = 24.dp), modifier = Modifier.fillMaxSize()) {
            if (tab == Tab.RECENT) {
                if (settings.recents.isEmpty()) item { Empty("No recent connections yet.") }
                items(settings.recents, key = { "r" + it.serverId + it.at }) { r ->
                    val server = st.servers.firstOrNull { it.id == r.serverId }
                    ServerRow(
                        server = server, fallbackCountry = r.countryCode, fallbackName = r.hostName,
                        health = server?.let { st.health[it.id] }, subtitle = Fmt.ago(r.at),
                        favorite = server != null && "s:${server.id}" in settings.favorites,
                        onFavorite = { server?.let { vm.toggleFavorite("s:${it.id}") } },
                        onClick = { server?.let { s -> onConnectRequest { vm.connectServer(s); onConnected() } } },
                    )
                }
                if (settings.recents.isNotEmpty()) item {
                    TextButton(onClick = vm::clearRecents, modifier = Modifier.padding(top = 8.dp)) { Text("Clear recent connections") }
                }
                return@LazyColumn
            }

            if (tab == Tab.ALL && query.isBlank()) item {
                Surface(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { onConnectRequest { vm.quickConnect(); onConnected() } },
                    shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.primary.copy(alpha = 0.10f),
                ) {
                    Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("⚡", style = MaterialTheme.typography.headlineSmall)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Smart connect", style = MaterialTheme.typography.titleMedium)
                            Text("Fastest reachable server, ranked by latency, uptime and load", style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (filtered.isEmpty()) item {
                Empty(if (st.loadingFromCache || st.refreshing) "Loading live servers…" else "No matching locations.")
            }
            items(filtered, key = { it.code }) { g ->
                CountryRow(
                    g = g, expanded = expanded == g.code, favorite = "c:${g.code}" in settings.favorites,
                    onToggle = { expanded = if (expanded == g.code) null else g.code },
                    onFavorite = { vm.toggleFavorite("c:${g.code}") },
                    onConnectCountry = { onConnectRequest { vm.connectCountry(g.code); onConnected() } },
                )
                AnimatedVisibility(expanded == g.code && g.servers.isNotEmpty(), enter = expandVertically(), exit = shrinkVertically()) {
                    Column(Modifier.padding(start = 14.dp)) {
                        g.servers.forEach { s ->
                            ServerRow(
                                server = s, fallbackCountry = g.code, fallbackName = s.hostName,
                                health = st.health[s.id], subtitle = null,
                                favorite = "s:${s.id}" in settings.favorites,
                                onFavorite = { vm.toggleFavorite("s:${s.id}") },
                                onClick = { onConnectRequest { vm.connectServer(s); onConnected() } },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun CountryRow(
    g: CountryGroup, expanded: Boolean, favorite: Boolean,
    onToggle: () -> Unit, onFavorite: () -> Unit, onConnectCountry: () -> Unit,
) {
    val enabled = g.isAvailable
    Surface(
        Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable(enabled = enabled, onClick = onToggle),
        shape = RoundedCornerShape(18.dp),
        color = if (expanded) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Flag(g.code, 24)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.name, style = MaterialTheme.typography.titleMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    AvailabilityDot(g.availability)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        if (!enabled) "Currently unavailable – no free server online"
                        else "${g.servers.size} server${if (g.servers.size > 1) "s" else ""}" +
                            (g.bestLatencyMs?.let { " · $it ms" } ?: ""),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (enabled) {
                TextButton(onClick = onConnectCountry) { Text("Connect") }
            }
            IconButton(onClick = onFavorite) {
                Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.Star, "Favourite",
                    tint = if (favorite) Brand.Amber else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ServerRow(
    server: VpnServer?, fallbackCountry: String, fallbackName: String, health: ServerHealth?, subtitle: String?,
    favorite: Boolean, onFavorite: () -> Unit, onClick: () -> Unit,
) {
    val reachable = health?.reachable != false
    Row(
        Modifier.fillMaxWidth().clickable(enabled = server != null, onClick = onClick).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(Countries.flag(server?.countryCode ?: fallbackCountry))
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(server?.hostName ?: fallbackName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val parts = buildList {
                if (server == null) add("no longer listed")
                else {
                    add("${server.protocol.uppercase()} ${server.port}")
                    val lat = health?.latencyMs ?: server.reportedPingMs
                    add(if (!reachable) "unreachable" else lat?.let { "$it ms" } ?: "– ms")
                    if (server.verifiedMbps != null) add(String.format(java.util.Locale.US, "✓ tested %.0f Mbps", server.verifiedMbps))
                    else server.reportedSpeedBps?.takeIf { it > 0 }?.let { add(String.format(java.util.Locale.US, "%.0f Mbps", it / 1e6)) }
                    server.uptimeMs?.let { add("up ${Fmt.uptime(it)}") }
                    server.logPolicy?.let { add("logs: $it") }
                    if ((health?.failures ?: 0) > 0) add("failed recently")
                }
                subtitle?.let { add(it) }
            }
            Text(parts.joinToString(" · "), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        AvailabilityDot(
            when {
                server == null || !reachable -> Availability.UNAVAILABLE
                (health?.latencyMs ?: 999) < 150 -> Availability.GOOD
                (health?.latencyMs ?: 999) < 350 -> Availability.FAIR
                else -> Availability.POOR
            }
        )
        IconButton(onClick = onFavorite, enabled = server != null) {
            Icon(if (favorite) Icons.Filled.Star else Icons.Outlined.Star, "Favourite server",
                tint = if (favorite) Brand.Amber else MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
}
