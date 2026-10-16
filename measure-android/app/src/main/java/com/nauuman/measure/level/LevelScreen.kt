package com.nauuman.measure.level

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sqrt

private val LevelGreen = Color(0xFF34C759)
private val CalibratedRed = Color(0xFFFF453A)

/** One smoothed accelerometer reading converted into level angles (degrees). */
private data class Reading(
    val flat: Boolean,
    /** Edge mode: rotation of the phone in the screen plane; 0 = upright portrait. */
    val roll: Float,
    /** Flat mode: tilt left/right and forward/back. */
    val tiltX: Float,
    val tiltY: Float,
)

@Composable
fun LevelScreen(haptics: Boolean, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    var reading by remember { mutableStateOf<Reading?>(null) }
    var sensorMissing by remember { mutableStateOf(false) }

    // Calibration references (tap to set, tap again to clear) — like Apple's Level.
    var refRoll by remember { mutableStateOf<Float?>(null) }
    var refTilt by remember { mutableStateOf<Pair<Float, Float>?>(null) }

    DisposableEffect(Unit) {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val sensor = sm.getDefaultSensor(Sensor.TYPE_GRAVITY) ?: sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        if (sensor == null) {
            sensorMissing = true
            return@DisposableEffect onDispose { }
        }
        val g = FloatArray(3)
        var initialized = false
        var flat = false
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) {
                if (!initialized) {
                    e.values.copyInto(g, 0, 0, 3)
                    initialized = true
                } else {
                    for (i in 0..2) g[i] += SMOOTHING * (e.values[i] - g[i])
                }
                val n = sqrt(g[0] * g[0] + g[1] * g[1] + g[2] * g[2])
                if (n < 1e-3f) return
                val x = g[0] / n
                val y = g[1] / n
                val z = g[2] / n
                val tiltFromFlat = Math.toDegrees(acos(abs(z).toDouble().coerceAtMost(1.0))).toFloat()
                flat = if (flat) tiltFromFlat < 45f else tiltFromFlat < 35f
                reading = Reading(
                    flat = flat,
                    roll = Math.toDegrees(atan2(x.toDouble(), y.toDouble())).toFloat(),
                    tiltX = Math.toDegrees(asin(x.toDouble())).toFloat(),
                    tiltY = Math.toDegrees(asin(y.toDouble())).toFloat(),
                )
            }

            override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
        }
        sm.registerListener(listener, sensor, SensorManager.SENSOR_DELAY_GAME)
        onDispose { sm.unregisterListener(listener) }
    }

    val r = reading
    if (sensorMissing || r == null) {
        Box(modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
            Text(
                if (sensorMissing) "This device has no motion sensor." else "Starting level…",
                color = Color.White,
            )
        }
        return
    }

    val calibrated = if (r.flat) refTilt != null else refRoll != null
    val deviation: Float
    val orientation: Float
    val relRoll: Float
    val offX: Float
    val offY: Float
    if (r.flat) {
        val (rx, ry) = refTilt ?: (0f to 0f)
        offX = r.tiltX - rx
        offY = r.tiltY - ry
        deviation = hypot(offX, offY)
        orientation = 0f
        relRoll = 0f
    } else {
        relRoll = r.roll - (refRoll ?: 0f)
        val snap = (relRoll / 90f).roundToInt() * 90f
        deviation = abs(relRoll - snap)
        orientation = (r.roll / 90f).roundToInt() * 90f
        offX = 0f
        offY = 0f
    }
    val degrees = deviation.roundToInt()
    val isLevel = degrees == 0

    LaunchedEffect(isLevel) {
        if (isLevel && haptics) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
    }

    val tapSource = remember { MutableInteractionSource() }
    Box(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .clickable(interactionSource = tapSource, indication = null) {
                if (r.flat) {
                    refTilt = if (refTilt == null) r.tiltX to r.tiltY else null
                } else {
                    refRoll = if (refRoll == null) {
                        // Store the offset from the nearest right angle.
                        r.roll - (r.roll / 90f).roundToInt() * 90f
                    } else null
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val c = center
            if (r.flat) {
                if (isLevel) drawRect(LevelGreen)
                val radius = 72.dp.toPx()
                val scale = 6.dp.toPx()
                val mx = (-offX * scale).coerceIn(-size.width / 2f, size.width / 2f)
                val my = (offY * scale).coerceIn(-size.height / 2f, size.height / 2f)
                val ringColor = if (isLevel) Color.White else Color.White.copy(alpha = 0.85f)
                drawCircle(ringColor, radius, c, style = Stroke(3.dp.toPx()))
                drawCircle(
                    if (isLevel) Color.White.copy(alpha = 0.35f) else Color.White.copy(alpha = 0.18f),
                    radius,
                    Offset(c.x + mx, c.y + my),
                )
                drawLine(ringColor.copy(alpha = 0.4f), Offset(c.x - radius * 1.4f, c.y), Offset(c.x + radius * 1.4f, c.y), 1.dp.toPx())
                drawLine(ringColor.copy(alpha = 0.4f), Offset(c.x, c.y - radius * 1.4f), Offset(c.x, c.y + radius * 1.4f), 1.dp.toPx())
            } else {
                val diag = hypot(size.width, size.height)
                rotate(relRoll, pivot = c) {
                    drawRect(
                        color = if (isLevel) LevelGreen else Color(0xFF2C2C2E),
                        topLeft = Offset(c.x - diag, c.y),
                        size = Size(diag * 2f, diag),
                    )
                    drawLine(Color.White, Offset(c.x - diag, c.y), Offset(c.x + diag, c.y), 2.dp.toPx())
                }
            }
        }

        Text(
            text = "$degrees°",
            color = if (calibrated && !isLevel) CalibratedRed else Color.White,
            fontSize = 72.sp,
            fontWeight = FontWeight.Light,
            modifier = Modifier.align(Alignment.Center).rotate(orientation),
        )
        Text(
            text = if (calibrated) "Calibrated · tap to reset" else "Tap to calibrate",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 24.dp),
        )
        Text(
            text = if (r.flat) "Lay the phone on a surface" else "Hold an edge against a surface",
            color = Color.White.copy(alpha = 0.6f),
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.TopCenter).statusBarsPadding().padding(top = 24.dp),
        )
    }
}

private const val SMOOTHING = 0.15f
