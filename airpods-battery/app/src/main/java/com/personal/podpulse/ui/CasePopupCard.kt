package com.personal.podpulse.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.podpulse.R
import com.personal.podpulse.data.PodRepository
import com.personal.podpulse.data.PodStatus

// iOS system colours
private val IosGreen = Color(0xFF34C759)
private val IosRed = Color(0xFFFF3B30)
private val IosGrey = Color(0xFF8E8E93)

private enum class Glyph { Left, Right, Buds, Case, Headphones }

private data class Gauge(val glyph: Glyph, val level: Int, val charging: Boolean)

/** iPhone-style battery sheet shown over other apps when the case opens. */
@Composable
fun CasePopupCard(onDismiss: () -> Unit) {
    val status by PodRepository.status.collectAsStateWithLifecycle()
    val dark = isSystemInDarkTheme()
    val sheet = if (dark) Color(0xFF1C1C1E) else Color.White
    val primary = if (dark) Color.White else Color.Black
    val track = if (dark) Color(0xFF3A3A3C) else Color(0xFFE5E5EA)

    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }
    val lidOpen by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 900, delayMillis = 300, easing = FastOutSlowInEasing),
        label = "lid",
    )

    AnimatedVisibility(
        visible = visible,
        enter = slideInVertically(
            spring(dampingRatio = 0.82f, stiffness = Spring.StiffnessMediumLow),
        ) { it } + fadeIn(),
    ) {
        val s = status
        Column(
            modifier = Modifier
                .padding(start = 10.dp, end = 10.dp, bottom = 24.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(40.dp))
                .background(sheet)
                .padding(start = 20.dp, end = 20.dp, top = 18.dp, bottom = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.fillMaxWidth()) {
                Text(
                    s?.model?.displayName ?: "AirPods",
                    color = primary,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.Center),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(track)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("✕", color = IosGrey, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(Modifier.height(10.dp))
            if (s?.model?.single != true) {
                CaseArt(
                    open = lidOpen,
                    ledOn = s?.caseCharging == true,
                    dark = dark,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(170.dp),
                )
                Spacer(Modifier.height(18.dp))
            }

            if (s != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(30.dp)) {
                    gauges(s).forEach { BatteryGauge(it, track, primary, sheet) }
                }
            }
        }
    }
}

/** Like iOS: one "AirPods" gauge when both buds agree, otherwise left and right. */
private fun gauges(s: PodStatus): List<Gauge> {
    if (s.model.single) {
        return listOfNotNull(s.left?.let { Gauge(Glyph.Headphones, it, s.leftCharging) })
    }
    val buds = if (s.left != null && s.left == s.right && s.leftCharging == s.rightCharging) {
        listOf(Gauge(Glyph.Buds, s.left, s.leftCharging))
    } else {
        listOfNotNull(
            s.left?.let { Gauge(Glyph.Left, it, s.leftCharging) },
            s.right?.let { Gauge(Glyph.Right, it, s.rightCharging) },
        )
    }
    return buds + listOfNotNull(s.case?.let { Gauge(Glyph.Case, it, s.caseCharging) })
}

@Composable
private fun BatteryGauge(g: Gauge, track: Color, primary: Color, sheet: Color) {
    val progress by animateFloatAsState(
        targetValue = g.level / 100f,
        animationSpec = tween(1000, delayMillis = 500, easing = FastOutSlowInEasing),
        label = "gauge",
    )
    val color = if (g.level <= 20 && !g.charging) IosRed else IosGreen
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(58.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val stroke = 3.5.dp.toPx()
                val inset = stroke / 2
                val arcSize = Size(size.width - stroke, size.height - stroke)
                drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
                drawArc(
                    color, -90f, 360f * progress, false, Offset(inset, inset), arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
                glyph(g.glyph, IosGrey, sheet)
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (g.charging) {
                Icon(
                    painterResource(R.drawable.ic_bolt),
                    contentDescription = "Charging",
                    tint = IosGreen,
                    modifier = Modifier.size(13.dp),
                )
            }
            Text("${g.level}%", color = primary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

/** Small icons inside the gauges. */
private fun DrawScope.glyph(g: Glyph, color: Color, cutout: Color) {
    val s = size.minDimension
    when (g) {
        Glyph.Left -> bud(center, s * 0.5f, mirrored = false, color)
        Glyph.Right -> bud(center, s * 0.5f, mirrored = true, color)
        Glyph.Buds -> {
            bud(center + Offset(-s * 0.1f, 0f), s * 0.44f, mirrored = false, color)
            bud(center + Offset(s * 0.1f, 0f), s * 0.44f, mirrored = true, color)
        }
        Glyph.Case -> {
            val w = s * 0.46f
            val h = s * 0.36f
            val top = Offset(center.x - w / 2, center.y - h / 2)
            drawRoundRect(color, top, Size(w, h), CornerRadius(h * 0.35f))
            drawLine(cutout, Offset(top.x, top.y + h * 0.32f), Offset(top.x + w, top.y + h * 0.32f), s * 0.025f)
        }
        Glyph.Headphones -> {
            val r = s * 0.2f
            drawArc(
                color, 180f, 180f, false,
                Offset(center.x - r, center.y - r * 1.1f), Size(r * 2, r * 2),
                style = Stroke(s * 0.05f, cap = StrokeCap.Round),
            )
            val cup = Size(r * 0.55f, r * 0.9f)
            drawRoundRect(color, Offset(center.x - r - cup.width / 2, center.y - r * 0.2f), cup, CornerRadius(cup.width / 2))
            drawRoundRect(color, Offset(center.x + r - cup.width / 2, center.y - r * 0.2f), cup, CornerRadius(cup.width / 2))
        }
    }
}

/** An earbud seen from the front: round head with the stem hanging down. */
private fun DrawScope.bud(c: Offset, s: Float, mirrored: Boolean, color: Color) {
    val dir = if (mirrored) -1f else 1f
    val r = s * 0.24f
    val head = Offset(c.x - dir * s * 0.06f, c.y - s * 0.2f)
    val stemW = s * 0.17f
    drawRoundRect(
        color,
        Offset(head.x + dir * r * 0.45f - stemW / 2, head.y),
        Size(stemW, s * 0.6f),
        CornerRadius(stemW / 2),
    )
    drawCircle(color, r, head)
}

/** The charging case with its lid swinging open and the buds rising out. */
@Composable
private fun CaseArt(open: Float, ledOn: Boolean, dark: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val h = size.height
        val cx = size.width / 2
        val caseW = h * 1.0f
        val caseH = h * 0.5f
        val left = cx - caseW / 2
        val bodyTop = h - caseH - 6.dp.toPx()
        val corner = CornerRadius(caseH * 0.36f)
        val white = Color(0xFFFDFDFD)
        val shade = Color(0xFFE2E2E7)
        val edge = Color(0xFFC7C7CC)
        val outline = Stroke(1.dp.toPx())

        // soft floor shadow
        drawOval(
            Color.Black.copy(alpha = if (dark) 0.55f else 0.12f),
            topLeft = Offset(left + caseW * 0.06f, h - 9.dp.toPx()),
            size = Size(caseW * 0.88f, 9.dp.toPx()),
        )

        // Lid: rises and foreshortens as it swings back.
        val lidH = caseH * 0.36f
        val lidNow = lidH * (1f - 0.5f * open)
        val lidBottom = bodyTop - open * lidH * 1.45f
        val lidTopLeft = Offset(left, lidBottom - lidNow)
        val lidSize = Size(caseW, lidNow)
        drawRoundRect(Brush.verticalGradient(listOf(shade, white), lidTopLeft.y, lidBottom), lidTopLeft, lidSize, corner)
        drawRoundRect(edge, lidTopLeft, lidSize, corner, style = outline)

        // Buds rise out of the case.
        val rise = open * caseH * 0.4f
        val r = caseW * 0.1f
        for (dir in listOf(-1f, 1f)) {
            val head = Offset(cx + dir * caseW * 0.17f, bodyTop + caseH * 0.14f - rise)
            val stemW = r * 0.8f
            val stemTopLeft = Offset(head.x + dir * r * 0.35f - stemW / 2, head.y)
            val stemSize = Size(stemW, caseH * 0.8f)
            drawRoundRect(white, stemTopLeft, stemSize, CornerRadius(stemW / 2))
            drawRoundRect(edge, stemTopLeft, stemSize, CornerRadius(stemW / 2), style = outline)
            drawCircle(white, r, head)
            drawCircle(edge, r, head, style = outline)
            // speaker mesh, facing inward
            drawOval(
                Color(0xFF3A3A3C),
                topLeft = Offset(head.x - dir * r * 0.35f - r * 0.22f, head.y - r * 0.15f),
                size = Size(r * 0.44f, r * 0.3f),
            )
        }

        // Case opening seen from the front.
        if (open > 0f) {
            drawRoundRect(
                Color(0xFF2C2C2E).copy(alpha = 0.35f * open),
                Offset(left + caseW * 0.07f, bodyTop - 2.dp.toPx()),
                Size(caseW * 0.86f, 6.dp.toPx()),
                CornerRadius(3.dp.toPx()),
            )
        }

        // Body, drawn last so it hides the lower part of the stems.
        val bodySize = Size(caseW, caseH)
        val bodyTopLeft = Offset(left, bodyTop)
        drawRoundRect(Brush.verticalGradient(listOf(white, shade), bodyTop, bodyTop + caseH), bodyTopLeft, bodySize, corner)
        drawRoundRect(edge, bodyTopLeft, bodySize, corner, style = outline)

        // Status light: green while the case is charging, like the real one.
        drawCircle(
            if (ledOn) IosGreen else Color(0xFFD1D1D6),
            2.5.dp.toPx(),
            Offset(cx, bodyTop + caseH * 0.42f),
        )
    }
}
