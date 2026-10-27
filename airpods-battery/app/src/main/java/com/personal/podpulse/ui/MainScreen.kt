package com.personal.podpulse.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import android.text.format.DateUtils
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.min
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.personal.podpulse.R
import com.personal.podpulse.ble.ScanDiagnostics
import com.personal.podpulse.data.AppSettings
import com.personal.podpulse.data.ChargeEstimator
import com.personal.podpulse.data.LastSeen
import com.personal.podpulse.data.PodRepository
import com.personal.podpulse.features.PairedDevice
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
    val notificationsAllowed: Boolean = true,
    val canDrawOverlays: Boolean = true,
    val pairedDevices: List<PairedDevice> = emptyList(),
    val deviceName: String? = null,
    val flags: Map<String, Boolean> = emptyMap(),
    val crashReport: String? = null,
)

class ScreenActions(
    val requestPermissions: () -> Unit,
    val requestBackgroundLocation: () -> Unit,
    val enableBluetooth: () -> Unit,
    val openLocationSettings: () -> Unit,
    val setBackgroundEnabled: (Boolean) -> Unit,
    val requestBatteryExemption: () -> Unit,
    val pinWidget: () -> Unit,
    val setFlag: (String, Boolean) -> Unit,
    val selectDevice: (PairedDevice) -> Unit,
    val requestNotifications: () -> Unit,
    val requestOverlay: () -> Unit,
    val openFind: () -> Unit,
    val openLastSeen: () -> Unit,
    val dismissCrash: () -> Unit,
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

            env.crashReport?.let { CrashCard(it, actions.dismissCrash) }

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
                        "switched on. Your location never leaves your phone.",
                    button = "Open settings",
                    onClick = actions.openLocationSettings,
                )
            }

            val current = status
            if (current == null) SearchingCard() else PodsCard(current, now)

            FindCard(actions)
            FeaturesCard(env, actions)
            WidgetCard(actions.pinWidget)
            BackgroundCard(env, actions)
            DiagnosticsCard(env, expandedByDefault = current == null)

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
    val context = LocalContext.current
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
                    icon = R.drawable.ic_headphones,
                    caption = chargeCaption(context, ChargeEstimator.Part.LEFT, status.left, status.leftCharging),
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
                            icon = R.drawable.ic_bud_left,
                            inCase = status.leftInCase,
                            caption = chargeCaption(context, ChargeEstimator.Part.LEFT, status.left, status.leftCharging),
                        )
                        BatteryRing(
                            status.right, "Right", status.rightCharging, status.rightInEar, big, stale,
                            icon = R.drawable.ic_bud_right,
                            inCase = status.rightInCase,
                            caption = chargeCaption(context, ChargeEstimator.Part.RIGHT, status.right, status.rightCharging),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    BatteryRing(
                        status.case, "Case", status.caseCharging, false, big * 0.8f, stale,
                        icon = R.drawable.ic_case,
                        caption = chargeCaption(context, ChargeEstimator.Part.CASE, status.case, status.caseCharging),
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
private fun FindCard(actions: ScreenActions) {
    val disconnectedAt by LastSeen.disconnectedAt.collectAsStateWithLifecycle()
    GlassCard {
        Text("Find my AirPods", color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Tile(
                title = "Find nearby",
                subtitle = "Hotter / colder",
                modifier = Modifier.weight(1f),
                onClick = actions.openFind,
            )
            Tile(
                title = "Last seen",
                subtitle = disconnectedAt?.let {
                    DateUtils.getRelativeTimeSpanString(
                        it,
                        System.currentTimeMillis(),
                        DateUtils.MINUTE_IN_MILLIS,
                    ).toString()
                } ?: "Where you left them",
                modifier = Modifier.weight(1f),
                onClick = actions.openLastSeen,
            )
        }
    }
}

@Composable
private fun Tile(title: String, subtitle: String, modifier: Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .clip(shape)
            .background(Glass, shape)
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        Text(title, color = Mint, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
        Text(subtitle, color = Muted, fontSize = 12.sp, maxLines = 1)
    }
}

@Composable
private fun FeaturesCard(env: EnvState, actions: ScreenActions) {
    var pickerOpen by remember { mutableStateOf(false) }
    GlassCard {
        Text("Features", color = TextHi, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))

        Box {
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { pickerOpen = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("My AirPods", color = TextHi, fontSize = 15.sp)
                    Text(
                        env.deviceName ?: "Tap to choose from paired devices",
                        color = if (env.deviceName == null) Amber else Muted,
                        fontSize = 13.sp,
                    )
                }
                Text("Change", color = Sky, fontSize = 13.sp)
            }
            DropdownMenu(expanded = pickerOpen, onDismissRequest = { pickerOpen = false }) {
                if (env.pairedDevices.isEmpty()) {
                    DropdownMenuItem(text = { Text("No paired devices") }, onClick = { pickerOpen = false })
                }
                env.pairedDevices.forEach { device ->
                    DropdownMenuItem(
                        text = { Text(device.name) },
                        onClick = {
                            pickerOpen = false
                            actions.selectDevice(device)
                        },
                    )
                }
            }
        }

        FeatureSwitch(
            env, actions, AppSettings.AUTO_PAUSE,
            "Auto-pause",
            "Pauses music when you take a bud out, resumes when you put it back.",
        )
        FeatureSwitch(
            env, actions, AppSettings.CASE_POPUP,
            "Connection pop-up",
            "Shows battery when you open the case, like on an iPhone.",
        )
        if (env.flags[AppSettings.CASE_POPUP] == true && !env.canDrawOverlays) {
            Hint(
                "Allow \"Display over other apps\" so the pop-up can appear on top. " +
                    "Until then it shows as a notification.",
                "Allow",
                actions.requestOverlay,
            )
        }
        FeatureSwitch(
            env, actions, AppSettings.LOW_BATTERY,
            "Low battery alert",
            "Notifies you when a bud drops to 20%.",
        )
        FeatureSwitch(
            env, actions, AppSettings.REMEMBER_LOCATION,
            "Remember where I left them",
            "Saves your phone's location when your AirPods disconnect.",
        )
        if (env.flags[AppSettings.REMEMBER_LOCATION] == true && env.hasPermissions && !env.hasBackgroundLocation) {
            Hint(
                "Needs Location set to \"Allow all the time\".",
                "Allow",
                actions.requestBackgroundLocation,
            )
        }
        if (!env.notificationsAllowed) {
            Hint(
                "Notifications are off, so alerts and the battery notification can't show.",
                "Turn on",
                actions.requestNotifications,
            )
        }
    }
}

@Composable
private fun FeatureSwitch(
    env: EnvState,
    actions: ScreenActions,
    key: String,
    title: String,
    subtitle: String,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, color = TextHi, fontSize = 15.sp)
            Text(subtitle, color = Muted, fontSize = 13.sp)
        }
        Switch(
            checked = env.flags[key] ?: true,
            onCheckedChange = { actions.setFlag(key, it) },
            colors = SwitchDefaults.colors(checkedThumbColor = Bg0, checkedTrackColor = Mint),
        )
    }
}

@Composable
private fun Hint(text: String, button: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text, color = Amber, fontSize = 13.sp, modifier = Modifier.weight(1f).padding(end = 12.dp))
        OutlinedButton(onClick = onClick) { Text(button, color = TextHi) }
    }
}

@Composable
private fun DiagnosticsCard(env: EnvState, expandedByDefault: Boolean) {
    val d by ScanDiagnostics.state.collectAsStateWithLifecycle()
    var expanded by rememberSaveable { mutableStateOf(expandedByDefault) }
    GlassCard(Modifier.clickable { expanded = !expanded }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "Diagnostics",
                color = TextHi,
                fontSize = 17.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(if (expanded) "Hide" else "Show", color = Sky, fontSize = 13.sp)
        }
        if (expanded) {
            Spacer(Modifier.height(10.dp))
            val hint = when {
                !env.hasPermissions -> "Permissions not granted yet."
                !env.bluetoothOn -> "Bluetooth is off."
                !env.locationOn -> "Location is off: Android hides scan results."
                d.anyPackets == 0 -> "No Bluetooth packets at all. Check Location is on and " +
                    "PodPulse has Precise location + Nearby devices in App info → Permissions."
                d.applePackets == 0 -> "Bluetooth works, but no Apple devices are being heard."
                d.podPackets == 0 -> "Apple devices heard, but no AirPods battery broadcast yet. " +
                    "Open the case lid next to the phone."
                d.lastPodParsed == false -> "AirPods packets heard but not understood. " +
                    "Send a screenshot of this card."
                else -> "AirPods packets are being received."
            }
            Text(hint, color = Amber, fontSize = 13.sp)
            Spacer(Modifier.height(10.dp))

            DiagLine("Live scan", d.liveScan)
            DiagLine("Location", if (env.locationOn) "On" else "Off")
            DiagLine("Background location", if (env.hasBackgroundLocation) "Allowed" else "Not allowed")
            DiagLine("Bluetooth packets", d.anyPackets.toString())
            DiagLine("Apple packets", d.applePackets.toString())
            DiagLine(
                "Apple types",
                d.appleTypes.sorted().joinToString(" ") { "%02X".format(it) }.ifEmpty { "-" },
            )
            DiagLine("AirPods packets", d.podPackets.toString())
            if (d.recent.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text("Latest AirPods packets (raw → decoded)", color = Muted, fontSize = 12.sp)
                d.recent.forEach { p ->
                    Text(
                        "${p.hex}  ${p.rssi}dBm\n  → ${p.decoded}",
                        color = TextHi,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun CrashCard(report: String, onDismiss: () -> Unit) {
    val context = LocalContext.current
    GlassCard {
        Text(
            "PodPulse crashed last time",
            color = Coral,
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Tap Copy and paste the report to Claude so it can be fixed.",
            color = Muted,
            fontSize = 13.sp,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            report.lines().take(12).joinToString("\n"),
            color = TextHi,
            fontSize = 10.sp,
            fontFamily = FontFamily.Monospace,
        )
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = {
                    val clipboard = context.getSystemService(ClipboardManager::class.java)
                    clipboard?.setPrimaryClip(ClipData.newPlainText("PodPulse crash", report))
                    Toast.makeText(context, "Crash report copied", Toast.LENGTH_SHORT).show()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Bg0),
            ) {
                Text("Copy", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(onClick = onDismiss) { Text("Dismiss", color = TextHi) }
        }
    }
}

@Composable
private fun DiagLine(label: String, value: String) {
    Row(Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
        Text(label, color = Muted, fontSize = 13.sp, modifier = Modifier.weight(1f))
        Text(value, color = TextHi, fontSize = 13.sp)
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
        s.leftInCase && s.rightInCase -> "Both buds in case"
        s.leftInCase || s.rightInCase -> "One bud in case"
        s.caseCharging -> "Case charging"
        else -> "Ready"
    }
}

private fun chargeCaption(
    context: android.content.Context,
    part: ChargeEstimator.Part,
    level: Int?,
    charging: Boolean,
): String? {
    if (!charging || level == null) return null
    return ChargeEstimator.format(ChargeEstimator.minutesToFull(context, part, level))
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
