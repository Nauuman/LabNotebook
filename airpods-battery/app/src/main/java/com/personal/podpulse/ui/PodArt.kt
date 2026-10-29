package com.personal.podpulse.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale

enum class PodArtKind { LeftBud, RightBud, Case, Headphones }

// Glossy white plastic and brushed metal.
private val Highlight = Color(0xFFFFFFFF)
private val Plastic = Color(0xFFF1F1F4)
private val PlasticShade = Color(0xFFCFD0D7)
private val PlasticEdge = Color(0xFFA6A7B0)
private val MetalLight = Color(0xFFE9EAEE)
private val MetalDark = Color(0xFF8F919A)
private val Grille = Color(0xFF2C2C2E)

/** Shaded, lit drawings of the AirPods parts, so they read as 3D objects. */
@Composable
fun PodArt(kind: PodArtKind, modifier: Modifier = Modifier, ledOn: Boolean = false) {
    Canvas(modifier) {
        when (kind) {
            PodArtKind.LeftBud -> bud()
            // The right bud is the left one seen in a mirror.
            PodArtKind.RightBud -> scale(scaleX = -1f, scaleY = 1f, pivot = center) { bud() }
            PodArtKind.Case -> case(ledOn)
            PodArtKind.Headphones -> headphones()
        }
    }
}

private fun DrawScope.softShadow(y: Float, width: Float) {
    val s = size.minDimension
    drawOval(
        Brush.radialGradient(
            listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent),
            center = Offset(center.x, y),
            radius = width / 2,
        ),
        topLeft = Offset(center.x - width / 2, y - s * 0.04f),
        size = Size(width, s * 0.08f),
    )
}

private fun DrawScope.bud() {
    val s = size.minDimension
    val head = Offset(center.x - s * 0.06f, s * 0.3f)
    val r = s * 0.2f

    softShadow(s * 0.93f, s * 0.4f)

    // Stem: a cylinder, lit from the left.
    val stemW = s * 0.15f
    val stemLeft = head.x + r * 0.35f
    val stemTop = head.y
    val stemBottom = s * 0.86f
    drawRoundRect(
        Brush.horizontalGradient(
            0f to PlasticShade,
            0.35f to Highlight,
            0.7f to Plastic,
            1f to PlasticEdge,
            startX = stemLeft,
            endX = stemLeft + stemW,
        ),
        topLeft = Offset(stemLeft, stemTop),
        size = Size(stemW, stemBottom - stemTop),
        cornerRadius = CornerRadius(stemW / 2),
    )
    // Metal tip at the bottom of the stem.
    val tipH = s * 0.07f
    drawRoundRect(
        Brush.horizontalGradient(
            listOf(MetalDark, MetalLight, MetalDark),
            startX = stemLeft,
            endX = stemLeft + stemW,
        ),
        topLeft = Offset(stemLeft, stemBottom - tipH),
        size = Size(stemW, tipH),
        cornerRadius = CornerRadius(stemW / 2),
    )

    // Head: a sphere with the light coming from the top left.
    drawCircle(
        Brush.radialGradient(
            0f to Highlight,
            0.45f to Plastic,
            0.8f to PlasticShade,
            1f to PlasticEdge,
            center = head + Offset(-r * 0.35f, -r * 0.35f),
            radius = r * 1.45f,
        ),
        radius = r,
        center = head,
    )
    // Speaker grille facing the ear.
    drawOval(
        Brush.verticalGradient(listOf(Grille, Color(0xFF55555A))),
        topLeft = Offset(head.x - r * 0.75f, head.y - r * 0.05f),
        size = Size(r * 0.55f, r * 0.38f),
    )
    // Specular highlight.
    drawOval(
        Color.White.copy(alpha = 0.85f),
        topLeft = Offset(head.x - r * 0.55f, head.y - r * 0.75f),
        size = Size(r * 0.45f, r * 0.25f),
    )
}

private fun DrawScope.case(ledOn: Boolean) {
    val s = size.minDimension
    val left = s * 0.1f
    val width = s * 0.8f
    val lidTop = s * 0.22f
    val seam = s * 0.42f
    val bottom = s * 0.86f
    val corner = CornerRadius(s * 0.17f)

    softShadow(s * 0.92f, width * 0.95f)

    // Body.
    drawRoundRect(
        Brush.verticalGradient(
            listOf(Highlight, Plastic, PlasticShade),
            startY = seam - s * 0.06f,
            endY = bottom,
        ),
        topLeft = Offset(left, seam - s * 0.06f),
        size = Size(width, bottom - seam + s * 0.06f),
        cornerRadius = corner,
    )
    // Lid, slightly brighter as it faces the light.
    drawRoundRect(
        Brush.verticalGradient(listOf(Highlight, Plastic), startY = lidTop, endY = seam),
        topLeft = Offset(left, lidTop),
        size = Size(width, seam - lidTop),
        cornerRadius = corner,
    )
    // Rounded sides: darker towards the edges.
    drawRoundRect(
        Brush.horizontalGradient(
            0f to Color.Black.copy(alpha = 0.16f),
            0.22f to Color.Transparent,
            0.78f to Color.Transparent,
            1f to Color.Black.copy(alpha = 0.2f),
            startX = left,
            endX = left + width,
        ),
        topLeft = Offset(left, lidTop),
        size = Size(width, bottom - lidTop),
        cornerRadius = corner,
    )
    // Seam between lid and body.
    drawLine(
        PlasticEdge,
        Offset(left + s * 0.02f, seam),
        Offset(left + width - s * 0.02f, seam),
        strokeWidth = s * 0.014f,
    )
    // Gloss along the top of the lid.
    drawRoundRect(
        Color.White.copy(alpha = 0.9f),
        topLeft = Offset(left + width * 0.2f, lidTop + s * 0.025f),
        size = Size(width * 0.6f, s * 0.03f),
        cornerRadius = CornerRadius(s * 0.015f),
    )
    // Status light.
    val led = Offset(center.x, seam + s * 0.15f)
    if (ledOn) {
        drawCircle(
            Brush.radialGradient(listOf(Color(0xAA34C759), Color.Transparent), center = led, radius = s * 0.08f),
            radius = s * 0.08f,
            center = led,
        )
    }
    drawCircle(if (ledOn) Color(0xFF34C759) else Color(0xFFD5D6DC), radius = s * 0.025f, center = led)
}

private fun DrawScope.headphones() {
    val s = size.minDimension
    softShadow(s * 0.92f, s * 0.7f)
    // Headband.
    drawArc(
        Brush.verticalGradient(listOf(MetalLight, MetalDark)),
        startAngle = 180f,
        sweepAngle = 180f,
        useCenter = false,
        topLeft = Offset(s * 0.2f, s * 0.12f),
        size = Size(s * 0.6f, s * 0.6f),
        style = Stroke(s * 0.07f, cap = StrokeCap.Round),
    )
    // Ear cups.
    for (x in listOf(s * 0.1f, s * 0.68f)) {
        drawRoundRect(
            Brush.horizontalGradient(
                listOf(PlasticEdge, Highlight, PlasticShade),
                startX = x,
                endX = x + s * 0.22f,
            ),
            topLeft = Offset(x, s * 0.42f),
            size = Size(s * 0.22f, s * 0.4f),
            cornerRadius = CornerRadius(s * 0.09f),
        )
    }
}
