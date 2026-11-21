package com.moni.freeglobalvpn.ui.components

import android.content.Context
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import com.moni.freeglobalvpn.data.Countries
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.hypot

/** Dotted world map (Natural Earth land, 2° grid, equirectangular), generated into assets/world_dots.txt. */
class DotMap(val cols: Int, val rows: Int, val stepDeg: Float, val latMax: Float, val dots: List<Pair<Int, Int>>) {
    fun project(lat: Float, lon: Float, w: Float, h: Float): Offset {
        val x = (lon + 180f) / 360f * w
        val y = (latMax - lat) / (rows * stepDeg) * h
        return Offset(x, y.coerceIn(0f, h))
    }

    companion object {
        @Volatile private var cached: DotMap? = null

        fun load(context: Context): DotMap = cached ?: synchronized(this) {
            cached ?: context.assets.open("world_dots.txt").bufferedReader().use { r ->
                val head = r.readLine().split(' ')
                val cols = head[0].toInt(); val rows = head[1].toInt()
                val dots = ArrayList<Pair<Int, Int>>(4000)
                var row = 0
                r.forEachLine { line ->
                    for (c in line.indices) if (line[c] == '1') dots += c to row
                    row++
                }
                DotMap(cols, rows, head[2].toFloat(), head[3].toFloat(), dots)
            }.also { cached = it }
        }
    }
}

/**
 * @param available countries that currently have live servers (small dots)
 * @param target    selected / connected server country (pulsing marker)
 * @param home      the user's real country (from the public-IP check)
 */
@Composable
fun WorldMap(
    available: Collection<String>,
    target: String?,
    home: String?,
    connected: Boolean,
    landColor: Color,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val map by produceState<DotMap?>(null) { value = withContext(Dispatchers.IO) { DotMap.load(context) } }
    val pulse by rememberInfiniteTransition(label = "pulse").animateFloat(
        0f, 1f, infiniteRepeatable(tween(1800, easing = LinearEasing), RepeatMode.Restart), label = "p"
    )
    val m = map
    Box(
        modifier
            .fillMaxWidth()
            .aspectRatio(if (m != null) m.cols.toFloat() / m.rows else 2.5f)
            .drawWithCache {
                val w = size.width; val h = size.height
                val land = Path()
                if (m != null) {
                    val cell = w / m.cols
                    val r = cell * 0.36f
                    for ((c, row) in m.dots) {
                        val cx = (c + 0.5f) * cell; val cy = (row + 0.5f) * (h / m.rows)
                        land.addOval(Rect(cx - r, cy - r, cx + r, cy + r))
                    }
                }
                val avail = if (m != null) available.mapNotNull { Countries.get(it) }.map { m.project(it.lat, it.lon, w, h) } else emptyList()
                val t = target?.let { Countries.get(it) }?.let { m?.project(it.lat, it.lon, w, h) }
                val hm = home?.let { Countries.get(it) }?.let { m?.project(it.lat, it.lon, w, h) }
                val arc = if (t != null && hm != null && connected && hypot(t.x - hm.x, t.y - hm.y) > 8f) {
                    Path().apply {
                        moveTo(hm.x, hm.y)
                        val mid = Offset((hm.x + t.x) / 2f, minOf(hm.y, t.y) - h * 0.18f)
                        quadraticTo(mid.x, mid.y, t.x, t.y)
                    }
                } else null
                val unit = w / 180f
                onDrawBehind {
                    drawPath(land, landColor)
                    avail.forEach { drawCircle(accent.copy(alpha = 0.55f), radius = unit * 0.9f, center = it) }
                    arc?.let { drawPath(it, accent.copy(alpha = 0.8f), style = Stroke(width = unit * 0.6f, cap = StrokeCap.Round)) }
                    hm?.let {
                        drawCircle(Color.White, radius = unit * 1.4f, center = it)
                        drawCircle(Color(0xFF94A3B8), radius = unit * 0.9f, center = it)
                    }
                    t?.let {
                        val ring = unit * (2f + 7f * pulse)
                        drawCircle(accent.copy(alpha = (1f - pulse) * 0.6f), radius = ring, center = it)
                        drawCircle(accent, radius = unit * 2.0f, center = it)
                        drawCircle(Color.White, radius = unit * 0.9f, center = it)
                    }
                }
            }
    )
}
