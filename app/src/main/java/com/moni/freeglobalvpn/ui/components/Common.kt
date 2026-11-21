package com.moni.freeglobalvpn.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moni.freeglobalvpn.data.Availability
import com.moni.freeglobalvpn.ui.theme.Brand
import java.util.Locale

@Composable
fun AppCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
        shadowElevation = 0.dp,
    ) { Column(Modifier.padding(16.dp), content = content) }
}

@Composable
fun Flag(code: String, size: Int = 28) {
    Box(
        Modifier.size(size.dp + 8.dp).background(MaterialTheme.colorScheme.surfaceVariant, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(com.moni.freeglobalvpn.data.Countries.flag(code), fontSize = (size * 0.72f).sp, textAlign = TextAlign.Center)
    }
}

@Composable
fun StatTile(label: String, value: String, unit: String, accent: Color, modifier: Modifier = Modifier) {
    Surface(modifier, shape = RoundedCornerShape(18.dp), color = MaterialTheme.colorScheme.surface, tonalElevation = 1.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(8.dp).background(accent, CircleShape))
                Spacer(Modifier.width(6.dp))
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.size(4.dp))
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.Center) {
                Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(3.dp))
                Text(unit, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp))
            }
        }
    }
}

@Composable
fun AvailabilityDot(a: Availability) {
    val c = when (a) {
        Availability.GOOD -> Brand.Green
        Availability.FAIR -> Brand.Amber
        Availability.POOR -> Color(0xFFF97316)
        Availability.UNAVAILABLE -> Brand.Slate
    }
    Box(Modifier.size(10.dp).background(c, CircleShape))
}

@Composable
fun SectionTitle(text: String) {
    Text(
        text.uppercase(Locale.ROOT), style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary, letterSpacing = 1.sp,
        modifier = Modifier.padding(start = 4.dp, top = 18.dp, bottom = 8.dp),
    )
}

object Fmt {
    /** Bytes/second -> (value, unit) in bits, as ISPs and speed tests report. */
    fun speed(bytesPerSec: Long): Pair<String, String> {
        val bits = bytesPerSec * 8.0
        return when {
            bits >= 1_000_000 -> String.format(Locale.US, "%.1f", bits / 1_000_000) to "Mbps"
            bits >= 1_000 -> String.format(Locale.US, "%.0f", bits / 1_000) to "Kbps"
            else -> String.format(Locale.US, "%.0f", bits) to "bps"
        }
    }

    fun bytes(b: Long): String = when {
        b >= 1L shl 30 -> String.format(Locale.US, "%.2f GB", b / (1L shl 30).toDouble())
        b >= 1L shl 20 -> String.format(Locale.US, "%.1f MB", b / (1L shl 20).toDouble())
        b >= 1L shl 10 -> String.format(Locale.US, "%.0f KB", b / 1024.0)
        else -> "$b B"
    }

    fun duration(ms: Long): String {
        val s = (ms / 1000).coerceAtLeast(0)
        return String.format(Locale.US, "%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60)
    }

    fun uptime(ms: Long?): String {
        if (ms == null || ms <= 0) return "–"
        val h = ms / 3_600_000
        return if (h >= 48) "${h / 24} days" else "$h h"
    }

    fun ago(ts: Long): String {
        if (ts <= 0) return "never"
        val m = (System.currentTimeMillis() - ts) / 60_000
        return when {
            m < 1 -> "just now"
            m < 60 -> "$m min ago"
            m < 48 * 60 -> "${m / 60} h ago"
            else -> "${m / 1440} days ago"
        }
    }
}
