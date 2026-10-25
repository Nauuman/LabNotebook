package com.personal.podpulse.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personal.podpulse.R

@Composable
fun GlassCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(28.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Glass, shape)
            .border(1.dp, GlassBorder, shape)
            .padding(20.dp),
        content = content,
    )
}

/** A 270° gauge with a glowing arc, animated fill, and a pulse while charging. */
@Composable
fun BatteryRing(
    level: Int?,
    label: String,
    charging: Boolean,
    inEar: Boolean,
    size: Dp,
    dimmed: Boolean,
    modifier: Modifier = Modifier,
) {
    val color = levelColor(level)
    val progress by animateFloatAsState(
        targetValue = (level ?: 0) / 100f,
        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        label = "ring",
    )
    val pulse = if (charging) {
        val t = rememberInfiniteTransition(label = "charge")
        t.animateFloat(
            initialValue = 0.25f,
            targetValue = 0.9f,
            animationSpec = infiniteRepeatable(tween(1200), RepeatMode.Reverse),
            label = "pulse",
        ).value
    } else {
        0.45f
    }

    Box(
        modifier = modifier
            .size(size)
            .alpha(if (dimmed) 0.55f else 1f),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = this.size.minDimension * 0.075f
            val inset = stroke * 1.4f
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val sweep = 270f * progress

            drawArc(
                color = RingTrack,
                startAngle = 135f,
                sweepAngle = 270f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(stroke, cap = StrokeCap.Round),
            )
            if (level != null && sweep > 0f) {
                // soft glow underneath
                drawArc(
                    color = color.copy(alpha = 0.22f * pulse),
                    startAngle = 135f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke * 2.6f, cap = StrokeCap.Round),
                )
                drawArc(
                    brush = Brush.linearGradient(
                        listOf(color.copy(alpha = 0.65f), color),
                        start = Offset(0f, this.size.height),
                        end = Offset(this.size.width, 0f),
                    ),
                    startAngle = 135f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (charging) {
                    Icon(
                        painter = painterResource(R.drawable.ic_bolt),
                        contentDescription = "Charging",
                        tint = color,
                        modifier = Modifier.size(size * 0.13f),
                    )
                }
                Text(
                    text = level?.toString() ?: "--",
                    color = TextHi,
                    fontSize = (size.value * 0.25f).sp,
                    fontWeight = FontWeight.SemiBold,
                )
                if (level != null) {
                    Text(
                        text = "%",
                        color = Muted,
                        fontSize = (size.value * 0.11f).sp,
                        modifier = Modifier.offset(y = size * 0.03f),
                    )
                }
            }
            Text(label, color = Muted, fontSize = (size.value * 0.085f).sp)
        }

        if (inEar) {
            Text(
                text = "IN EAR",
                color = Bg0,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .background(Sky, RoundedCornerShape(50))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@Composable
fun SignalBars(rssi: Int, modifier: Modifier = Modifier) {
    val bars = when {
        rssi >= -60 -> 4
        rssi >= -70 -> 3
        rssi >= -80 -> 2
        else -> 1
    }
    Row(
        modifier = modifier.height(16.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (i in 1..4) {
            Box(
                Modifier
                    .width(4.dp)
                    .height((4 + i * 3).dp)
                    .background(if (i <= bars) Sky else RingTrack, RoundedCornerShape(2.dp)),
            )
        }
    }
}

@Composable
fun PulsingDot(color: Color, animate: Boolean) {
    val alpha = if (animate) {
        rememberInfiniteTransition(label = "dot").animateFloat(
            initialValue = 1f,
            targetValue = 0.3f,
            animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
            label = "dotAlpha",
        ).value
    } else {
        1f
    }
    Box(
        Modifier
            .size(8.dp)
            .alpha(alpha)
            .background(color, CircleShape),
    )
}

/** Expanding rings shown while searching for AirPods. */
@Composable
fun Radar(modifier: Modifier = Modifier, color: Color = Mint) {
    val t = rememberInfiniteTransition(label = "radar")
    val phase by t.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2400, easing = LinearEasing)),
        label = "phase",
    )
    Canvas(modifier) {
        val maxR = size.minDimension / 2
        for (i in 0 until 3) {
            val p = (phase + i / 3f) % 1f
            drawCircle(
                color = color.copy(alpha = (1f - p) * 0.5f),
                radius = maxR * p,
                style = Stroke(width = 2.dp.toPx()),
            )
        }
        drawCircle(color = color, radius = 6.dp.toPx())
    }
}
