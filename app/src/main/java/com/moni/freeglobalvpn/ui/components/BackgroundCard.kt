package com.moni.freeglobalvpn.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.moni.freeglobalvpn.ui.theme.Brand
import com.moni.freeglobalvpn.vpn.DeviceTuning

/** "Keep VPN running" helper tuned for Realme/OPPO, Xiaomi/Redmi and Samsung. */
@Composable
fun BackgroundCard(compact: Boolean = false, onDismiss: (() -> Unit)? = null) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    var unrestricted by remember { mutableStateOf(DeviceTuning.isUnrestricted(context)) }
    // Re-check whenever the user comes back from the settings screen.
    LaunchedEffect(owner) {
        owner.repeatOnLifecycle(Lifecycle.State.RESUMED) { unrestricted = DeviceTuning.isUnrestricted(context) }
    }
    if (compact && unrestricted) return
    AppCard {
        Text(
            if (unrestricted) "✓ VPN can run in the background" else "Keep the VPN running on your ${DeviceTuning.brand.label}",
            style = MaterialTheme.typography.titleMedium,
            color = if (unrestricted) Brand.Green else MaterialTheme.colorScheme.onSurface,
        )
        if (!compact || !unrestricted) {
            Text(
                "${DeviceTuning.brand.label} phones close background apps to save battery, which disconnects the VPN. Do this once:",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 6.dp),
            )
            DeviceTuning.steps().forEachIndexed { i, s ->
                Text("${i + 1}. $s", style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(vertical = 2.dp))
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!unrestricted) Button(onClick = { DeviceTuning.openBatteryExemption(context) }) { Text("Allow background running") }
                OutlinedButton(onClick = { DeviceTuning.openOemBackgroundSettings(context) }) { Text("Auto-start / battery") }
            }
            if (!compact) TextButton(onClick = { DeviceTuning.openVpnSettings(context) }) { Text("Always-on VPN settings") }
            if (onDismiss != null) TextButton(onClick = onDismiss) { Text("Not now") }
        }
    }
}
