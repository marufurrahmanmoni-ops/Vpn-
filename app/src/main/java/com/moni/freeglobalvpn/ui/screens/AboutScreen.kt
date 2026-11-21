package com.moni.freeglobalvpn.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.moni.freeglobalvpn.BuildConfig
import com.moni.freeglobalvpn.R
import com.moni.freeglobalvpn.ui.MainViewModel
import com.moni.freeglobalvpn.ui.components.AppCard
import com.moni.freeglobalvpn.ui.components.SectionTitle
import com.moni.freeglobalvpn.ui.theme.Brand

const val DEVELOPER_NAME = "Md. Marufur Rahman Moni"

@Composable
private fun BackBar(title: String, onBack: () -> Unit) {
    Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
        Text(title, style = MaterialTheme.typography.titleLarge)
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackBar("About", onBack)
        Column(Modifier.fillMaxWidth().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Image(
                painter = painterResource(R.drawable.developer_photo),
                contentDescription = "Photo of $DEVELOPER_NAME",
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(132.dp).clip(CircleShape).border(3.dp, Brand.gradient, CircleShape),
            )
            Spacer(Modifier.height(14.dp))
            Text(DEVELOPER_NAME, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
            Text("Developer", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(6.dp))
            Text("Free Global VPN  v${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionTitle("Built with")
            AppCard {
                Credit("OpenVPN for Android (ics-openvpn)", "Arne Schwabe · GNU GPL v2 – the VPN engine")
                Credit("OpenVPN 2.x", "OpenVPN Inc. & community · GNU GPL v2")
                Credit("OpenSSL, LZ4, LZO, {fmt}", "Their respective open-source licenses")
                Credit("VPN Gate Academic Experiment", "University of Tsukuba – free volunteer server list")
                Credit("Natural Earth", "Public-domain map data for the world map")
                Credit("Jetpack Compose, Kotlin", "Apache License 2.0")
            }
            SectionTitle("License")
            AppCard {
                Text(
                    "Because it includes ics-openvpn, this app is distributed under the GNU General Public License v2. " +
                        "If you publish it, you must also publish its complete source code.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Credit(title: String, sub: String) {
    Column(Modifier.padding(vertical = 5.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun PrivacyScreen(vm: MainViewModel, onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        BackBar("Privacy & logging", onBack)
        Column(Modifier.padding(horizontal = 16.dp)) {
            SectionTitle("This app")
            AppCard {
                Bullet("No account, no sign-up, no ads, no analytics or tracking SDKs.")
                Bullet("Your browsing history is never recorded – the app has no code that inspects your traffic.")
                Bullet("Stored only on your phone: settings, favourites, recent server names, the cached server list, and any configs you import (encrypted with an Android Keystore key).")
                Bullet("App data is excluded from Android cloud backup.")
            }
            SectionTitle("Third-party services the app contacts")
            AppCard {
                Bullet("www.vpngate.net – to download the public server list.")
                Bullet("ipwho.is / api.ipify.org – to show your public IP (proves the IP changed). They see that request.")
                Bullet("speed.cloudflare.com – only when you tap “Run” speed test.")
                Bullet("1.1.1.1 / 8.8.8.8 port 443 – a TCP handshake to measure tunnel latency (no data sent).")
            }
            SectionTitle("VPN server operators")
            vm.repository.providers().forEach { p ->
                AppCard(Modifier.padding(bottom = 8.dp)) {
                    Text(p.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(p.loggingDisclosure, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            AppCard {
                Text(
                    "This app does NOT claim to be “no-log”. Free volunteer servers can't guarantee that. Avoid using them for " +
                        "banking or sensitive logins – that's why banking apps bypass the VPN by default.",
                    style = MaterialTheme.typography.bodyMedium, color = Brand.Amber,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun Bullet(text: String) {
    Row(Modifier.padding(vertical = 3.dp)) {
        Text("•  ", style = MaterialTheme.typography.bodyMedium)
        Text(text, style = MaterialTheme.typography.bodyMedium)
    }
}
