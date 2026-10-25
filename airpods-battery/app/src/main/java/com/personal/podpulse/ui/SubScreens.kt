package com.personal.podpulse.ui

import android.content.Context
import android.location.Geocoder
import android.text.format.DateFormat
import android.text.format.DateUtils
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.podpulse.R
import com.personal.podpulse.data.LastSeen
import com.personal.podpulse.data.Place
import com.personal.podpulse.features.FindSignal
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.Date

@Composable
fun SubScreen(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg0, Bg1))),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(painterResource(R.drawable.ic_back), contentDescription = "Back", tint = TextHi)
                }
                Spacer(Modifier.width(4.dp))
                Text(title, color = TextHi, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
fun FindScreen(onBack: () -> Unit) {
    val reading by FindSignal.reading.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(500)
            value = System.currentTimeMillis()
        }
    }
    val r = reading
    val fresh = r != null && now - r.time < 4_000
    val target = if (fresh) FindSignal.proximity(r!!.rssi) else 0f
    val proximity by animateFloatAsState(target, tween(600), label = "proximity")
    val color = lerp(Sky, Mint, proximity)
    val label = when {
        !fresh -> "No signal"
        proximity > 0.8f -> "Right here!"
        proximity > 0.6f -> "Very close"
        proximity > 0.4f -> "Getting warmer"
        proximity > 0.2f -> "Nearby"
        else -> "Far away"
    }

    val pulse by rememberInfiniteTransition(label = "find").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1600, easing = LinearEasing)),
        label = "pulse",
    )

    SubScreen("Find nearby", onBack) {
        GlassCard {
            Box(
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.fillMaxSize()) {
                    val maxR = size.minDimension / 2
                    val core = maxR * (0.18f + 0.62f * proximity)
                    if (fresh) {
                        for (i in 0 until 3) {
                            val p = (pulse + i / 3f) % 1f
                            drawCircle(
                                color = color.copy(alpha = (1f - p) * 0.45f),
                                radius = core + (maxR - core) * p,
                                style = Stroke(2.dp.toPx()),
                            )
                        }
                    }
                    drawCircle(
                        brush = Brush.radialGradient(
                            listOf(color.copy(alpha = if (fresh) 0.55f else 0.15f), Color.Transparent),
                            center = center,
                            radius = core * 1.6f,
                        ),
                        radius = core * 1.6f,
                    )
                    drawCircle(color = if (fresh) color else Muted, radius = core, style = Stroke(4.dp.toPx()))
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        if (fresh) "${(proximity * 100).toInt()}%" else "--",
                        color = TextHi,
                        fontSize = 44.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(label, color = if (fresh) color else Muted, fontSize = 18.sp)
                }
            }
            if (fresh) {
                Text(
                    "Signal ${r!!.rssi.toInt()} dBm",
                    color = Muted,
                    fontSize = 13.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            }
        }
        GlassCard {
            Text("How to use", color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Walk around slowly. The circle grows and turns green as you get closer. " +
                    "AirPods only broadcast when they're out of the case or the lid is open, " +
                    "so a closed case can't be found this way. Check Last seen instead.",
                color = Muted,
                fontSize = 14.sp,
            )
        }
    }
}

@Composable
fun LastSeenScreen(
    onBack: () -> Unit,
    openMap: (Place) -> Unit,
    directions: (Place) -> Unit,
    hasBackgroundLocation: Boolean,
    requestBackgroundLocation: () -> Unit,
) {
    val context = LocalContext.current
    val place by LastSeen.place.collectAsStateWithLifecycle()
    val disconnectedAt by LastSeen.disconnectedAt.collectAsStateWithLifecycle()
    val p = place
    val address by produceState<String?>(null, p) {
        value = p?.let { withContext(Dispatchers.IO) { reverseGeocode(context, it) } }
    }

    SubScreen("Last seen", onBack) {
        GlassCard {
            Text("Last disconnected", color = Muted, fontSize = 13.sp)
            Text(
                disconnectedAt?.let { describeTime(context, it) } ?: "Not yet recorded",
                color = TextHi,
                fontSize = 20.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(16.dp))
            if (p == null) {
                Text(
                    "No location saved yet. When your AirPods disconnect, PodPulse saves " +
                        "where your phone was so you know where you left them.",
                    color = Muted,
                    fontSize = 14.sp,
                )
            } else {
                Text("Your phone was here", color = Muted, fontSize = 13.sp)
                Text(
                    address ?: "%.5f, %.5f".format(p.latitude, p.longitude),
                    color = TextHi,
                    fontSize = 17.sp,
                )
                Text(
                    "Accurate to about ${p.accuracyMeters.toInt()} m · saved ${describeTime(context, p.time)}",
                    color = Muted,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = { openMap(p) },
                        colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Bg0),
                    ) {
                        Text("Open in Maps", fontWeight = FontWeight.SemiBold)
                    }
                    OutlinedButton(onClick = { directions(p) }) {
                        Text("Directions", color = TextHi)
                    }
                }
            }
        }
        if (!hasBackgroundLocation) {
            GlassCard {
                Text(
                    "Location access needed",
                    color = TextHi,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "Your AirPods usually disconnect while PodPulse is closed. Set Location to " +
                        "\"Allow all the time\" so the spot can be saved.",
                    color = Amber,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(onClick = requestBackgroundLocation) {
                    Text("Allow all the time", color = TextHi)
                }
            }
        }
        GlassCard {
            Text(
                "Apple's Find My network only works on Apple devices. To see your AirPods' " +
                    "live location or play a sound, sign in at icloud.com/find in a browser.",
                color = Muted,
                fontSize = 13.sp,
            )
        }
    }
}

private fun describeTime(context: Context, time: Long): String {
    val relative = DateUtils.getRelativeTimeSpanString(
        time,
        System.currentTimeMillis(),
        DateUtils.MINUTE_IN_MILLIS,
    )
    val clock = DateFormat.getTimeFormat(context).format(Date(time))
    return if (DateUtils.isToday(time)) "$relative · $clock" else "$relative"
}

@Suppress("DEPRECATION") // the async variant needs API 33
private fun reverseGeocode(context: Context, place: Place): String? = runCatching {
    if (!Geocoder.isPresent()) return null
    Geocoder(context).getFromLocation(place.latitude, place.longitude, 1)
        ?.firstOrNull()
        ?.getAddressLine(0)
}.getOrNull()
