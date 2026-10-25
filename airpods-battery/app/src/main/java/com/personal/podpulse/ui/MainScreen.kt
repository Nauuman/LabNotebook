package com.personal.podpulse.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.podpulse.data.PodRepository
import com.personal.podpulse.data.PodStatus
import kotlinx.coroutines.delay

/** Snapshot of everything outside our control that affects scanning. */
data class EnvState(
    val hasPermissions: Boolean = false,
    val hasBackgroundLocation: Boolean = false,
    val bluetoothOn: Boolean = false,
    val locationOn: Boolean = false,
    val backgroundEnabled: Boolean = true,
    val ignoringBatteryOptimizations: Boolean = true,
)

class ScreenActions(
    val requestPermissions: () -> Unit,
    val requestBackgroundLocation: () -> Unit,
    val enableBluetooth: () -> Unit,
    val openLocationSettings: () -> Unit,
    val setBackgroundEnabled: (Boolean) -> Unit,
    val requestBatteryExemption: () -> Unit,
    val pinWidget: () -> Unit,
)

private const val LIVE_WINDOW_MS = 30_000L

@Composable
fun MainScreen(env: EnvState, actions: ScreenActions) {
    val status by PodRepository.status.collectAsStateWithLifecycle()
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            delay(1_000)
            value = System.currentTimeMillis()
        }
    }
    val accent = levelColor(status?.lowest)

    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Bg0, Bg1))),
    ) {
        // Ambient glow tinted by the battery level.
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(accent.copy(alpha = 0.18f), Color.Transparent),
                    center = Offset(size.width * 0.5f, size.height * 0.22f),
                    radius = size.width * 0.9f,
                ),
                radius = size.width * 0.9f,
                center = Offset(size.width * 0.5f, size.height * 0.22f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Header(status, now)

            when {
                !env.hasPermissions -> SetupCard(
                    title = "Allow Bluetooth access",
                    body = "PodPulse reads the battery broadcast your AirPods send over " +
                        "Bluetooth LE. Android requires Nearby devices and precise Location " +
                        "permission to receive those broadcasts. Nothing leaves your phone.",
                    button = "Grant access",
                    onClick = actions.requestPermissions,
                )
                !env.bluetoothOn -> SetupCard(
                    title = "Bluetooth is off",
                    body = "Turn on Bluetooth to see your AirPods.",
                    button = "Turn on",
                    onClick = actions.enableBluetooth,
                )
                !env.locationOn -> SetupCard(
                    title = "Location is off",
                    body = "Android only delivers Bluetooth scan results while Location is " +
                        "switched on. PodPulse never reads your location.",
                    button = "Open settings",
                    onClick = actions.openLocationSettings,
                )
            }

            val current = status
            if (current == null) SearchingCard() else PodsCard(current, now)

            WidgetCard(actions.pinWidget)
            BackgroundCard(env, actions)

            Text(
                text = "Tip: open the case lid next to your phone for an instant reading.",
                color = Muted,
                fontSize = 12.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun Header(status: PodStatus?, now: Long) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text("PodPulse", color = TextHi, fontSize = 28.sp, fontWeight = FontWeight.Bold)
            Text("AirPods battery", color = Muted, fontSize = 14.sp)
        }
        val age = status?.let { now - it.timestamp }
        val live = age != null && age < LIVE_WINDOW_MS
        val label = when {
            age == null -> "Searching"
            live -> "Live"
            else -> "Seen ${formatAge(age)}"
        }
        Row(
            modifier = Modifier
                .background(Glass, RoundedCornerShape(50))
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PulsingDot(color = if (live) Mint else Muted, animate = live || age == null)
            Spacer(Modifier.width(8.dp))
            Text(label, color = TextHi, fontSize = 13.sp)
        }
    }
}

@Composable
private fun SearchingCard() {
    GlassCard {
        Box(
            Modifier
                .fillMaxWidth()
                .height(220.dp),
            contentAlignment = Alignment.Center,
        ) {
            Radar(Modifier.size(200.dp))
        }
        Text(
            "Looking for AirPods…",
            color = TextHi,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Open the case lid or put a bud in your ear.",
            color = Muted,
            fontSize = 14.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
    }
}

@Composable
private fun PodsCard(status: PodStatus, now: Long) {
    val stale = now - status.timestamp > LIVE_WINDOW_MS
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    status.model.displayName,
                    color = TextHi,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(summary(status), color = Muted, fontSize = 13.sp)
            }
            if (!stale) SignalBars(status.rssi)
        }
        Spacer(Modifier.height(20.dp))

        BoxWithConstraints(Modifier.fillMaxWidth()) {
            val big = min(160.dp, (maxWidth - 16.dp) / 2)
            if (status.model.single) {
                BatteryRing(
                    level = status.left,
                    label = "Battery",
                    charging = status.leftCharging,
                    inEar = false,
                    size = min(200.dp, maxWidth),
                    dimmed = stale,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                Column(
                    Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                    ) {
                        BatteryRing(
                            status.left, "Left", status.leftCharging, status.leftInEar, big, stale,
                        )
                        BatteryRing(
                            status.right, "Right", status.rightCharging, status.rightInEar, big, stale,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    BatteryRing(
                        status.case, "Case", status.caseCharging, false, big * 0.8f, stale,
                    )
                }
            }
        }
    }
}

@Composable
private fun WidgetCard(onPin: () -> Unit) {
    GlassCard {
        Text("Home screen widget", color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(
            "Glance at left, right and case battery without opening the app. " +
                "Resizable — shrink it to a single row.",
            color = Muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onPin,
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Bg0),
        ) {
            Text("Add widget", fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun BackgroundCard(env: EnvState, actions: ScreenActions) {
    GlassCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    "Background updates",
                    color = TextHi,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    "Keep the widget fresh while the app is closed.",
                    color = Muted,
                    fontSize = 13.sp,
                )
            }
            Switch(
                checked = env.backgroundEnabled,
                onCheckedChange = actions.setBackgroundEnabled,
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Bg0,
                    checkedTrackColor = Mint,
                ),
            )
        }
        if (env.backgroundEnabled && env.hasPermissions) {
            if (!env.hasBackgroundLocation) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Set Location to \"Allow all the time\" so Android keeps delivering " +
                        "AirPods broadcasts when PodPulse isn't open.",
                    color = Amber,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = actions.requestBackgroundLocation) {
                    Text("Allow all the time", color = TextHi)
                }
            }
            if (!env.ignoringBatteryOptimizations) {
                Spacer(Modifier.height(12.dp))
                Text(
                    "Samsung may put PodPulse to sleep. Exempt it from battery " +
                        "optimisation for reliable widget updates.",
                    color = Amber,
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = actions.requestBatteryExemption) {
                    Text("Don't optimise", color = TextHi)
                }
            }
        }
    }
}

@Composable
private fun SetupCard(title: String, body: String, button: String, onClick: () -> Unit) {
    GlassCard {
        Text(title, color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(4.dp))
        Text(body, color = Muted, fontSize = 13.sp)
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Bg0),
        ) {
            Text(button, fontWeight = FontWeight.SemiBold)
        }
    }
}

private fun summary(s: PodStatus): String {
    if (s.model.single) return if (s.leftCharging) "Charging" else "Connected"
    return when {
        s.leftInEar && s.rightInEar -> "Both buds in ear"
        s.leftInEar -> "Left bud in ear"
        s.rightInEar -> "Right bud in ear"
        s.leftCharging || s.rightCharging -> "Charging in case"
        s.caseCharging -> "Case charging"
        else -> "Ready"
    }
}

private fun formatAge(ms: Long): String {
    val minutes = ms / 60_000
    return when {
        minutes < 1 -> "just now"
        minutes < 60 -> "${minutes}m ago"
        minutes < 24 * 60 -> "${minutes / 60}h ago"
        else -> "${minutes / (24 * 60)}d ago"
    }
}
