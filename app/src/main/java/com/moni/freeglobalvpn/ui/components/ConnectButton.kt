package com.moni.freeglobalvpn.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.moni.freeglobalvpn.ui.theme.Brand

enum class ButtonVisual { OFF, BUSY, ON, ERROR }

@Composable
fun ConnectButton(
    visual: ButtonVisual,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 184.dp,
    idleRing: Color,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.94f else 1f, spring(dampingRatio = 0.5f), label = "s")
    val inf = rememberInfiniteTransition(label = "btn")
    val spin by inf.animateFloat(0f, 360f, infiniteRepeatable(tween(1200, easing = LinearEasing)), label = "spin")
    val breathe by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2200), RepeatMode.Reverse), label = "br")

    val fill: Brush = when (visual) {
        ButtonVisual.ON -> Brand.connectedGradient
        ButtonVisual.BUSY -> Brand.connectingGradient
        ButtonVisual.ERROR -> Brush.linearGradient(listOf(Color(0xFFEF4444), Color(0xFFF97316)))
        ButtonVisual.OFF -> Brand.gradient
    }
    val glow = when (visual) {
        ButtonVisual.ON -> Brand.Green
        ButtonVisual.BUSY -> Brand.Amber
        ButtonVisual.ERROR -> Brand.Red
        ButtonVisual.OFF -> Brand.Violet
    }
    val label = when (visual) {
        ButtonVisual.ON -> "Disconnect VPN"
        ButtonVisual.BUSY -> "Cancel connection"
        else -> "Connect VPN"
    }

    Box(modifier.size(size + 56.dp), contentAlignment = Alignment.Center) {
        // Outer animated rings
        Canvas(Modifier.fillMaxSize()) {
            val r = this.size.minDimension / 2f
            when (visual) {
                ButtonVisual.BUSY -> drawArc(
                    glow, startAngle = spin, sweepAngle = 100f, useCenter = false,
                    style = Stroke(width = 5.dp.toPx(), cap = StrokeCap.Round),
                    topLeft = Offset(6.dp.toPx(), 6.dp.toPx()),
                    size = androidx.compose.ui.geometry.Size(this.size.width - 12.dp.toPx(), this.size.height - 12.dp.toPx()),
                )
                ButtonVisual.ON -> {
                    drawCircle(glow.copy(alpha = 0.10f + 0.12f * breathe), radius = r)
                    drawCircle(glow.copy(alpha = 0.18f), radius = r * (0.86f + 0.04f * breathe))
                }
                else -> drawCircle(idleRing, radius = r * 0.93f, style = Stroke(width = 2.dp.toPx()))
            }
        }
        Box(
            Modifier
                .scale(scale)
                .size(size)
                .shadow(24.dp, CircleShape, ambientColor = glow, spotColor = glow)
                .clip(CircleShape)
                .background(fill)
                .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
                .semantics { contentDescription = label },
            contentAlignment = Alignment.Center,
        ) {
            PowerGlyph(Modifier.size(size * 0.36f).padding(2.dp))
        }
    }
}

@Composable
private fun PowerGlyph(modifier: Modifier) {
    Canvas(modifier) {
        val w = size.minDimension
        val stroke = w * 0.11f
        drawArc(
            Color.White, startAngle = -50f, sweepAngle = 280f, useCenter = false,
            topLeft = Offset(stroke, stroke + w * 0.06f),
            size = androidx.compose.ui.geometry.Size(w - 2 * stroke, w - 2 * stroke),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawLine(Color.White, Offset(w / 2f, stroke / 2f), Offset(w / 2f, w * 0.48f), strokeWidth = stroke, cap = StrokeCap.Round)
    }
}
