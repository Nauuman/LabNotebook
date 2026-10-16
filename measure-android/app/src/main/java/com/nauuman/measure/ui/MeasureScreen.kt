package com.nauuman.measure.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nauuman.measure.ar.ArController
import com.nauuman.measure.ar.MeasureEngine
import com.nauuman.measure.ar.MeasureMode
import com.nauuman.measure.data.AppSettings
import com.nauuman.measure.data.Units
import kotlinx.coroutines.delay

private val Scrim = Color.Black.copy(alpha = 0.55f)

/** Controls drawn over the live camera view. */
@Composable
fun MeasureHud(
    ar: ArController,
    settings: AppSettings,
    mode: MeasureMode,
    onModeChange: (MeasureMode) -> Unit,
    selectedId: Long?,
    onSelect: (Long?) -> Unit,
    onCapture: () -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    flash: Animatable<Float, AnimationVector1D>,
) {
    val hud by ar.hud.collectAsState()
    val imperial = settings.imperial
    var toast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        ar.events.collect { e ->
            if (e == MeasureEngine.Event.NO_SURFACE) toast = "No surface found here — move closer or add light"
        }
    }
    LaunchedEffect(toast) {
        if (toast != null) {
            delay(1800)
            toast = null
        }
    }

    Box(Modifier.fillMaxSize()) {
        // Shutter flash after taking a photo.
        Box(Modifier.fillMaxSize().alpha(flash.value).background(Color.White))

        Column(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                RoundIcon(AppIcons.Undo, "Undo", enabled = hud.measurementCount > 0) {
                    onSelect(null)
                    ar.post(MeasureEngine.Action.Undo)
                }
                Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                    Readout(
                        live = hud.liveLength?.let { Units.length(it, imperial) },
                        last = when {
                            hud.lastArea != null -> Units.area(hud.lastArea!!, imperial)
                            hud.lastLength != null -> Units.length(hud.lastLength!!, imperial)
                            else -> null
                        },
                    )
                }
                AnimatedVisibility(hud.measurementCount > 0, enter = fadeIn(), exit = fadeOut()) {
                    TextButton(onClick = {
                        onSelect(null)
                        ar.post(MeasureEngine.Action.Clear)
                    }) { Text("Clear", color = Color.White, fontWeight = FontWeight.SemiBold) }
                }
                RoundIcon(Icons.Filled.Settings, "Settings", onClick = onOpenSettings)
            }

            val message = toast ?: hud.message ?: if (mode == MeasureMode.PATH && hud.activePoints >= 3) {
                "Tap ✓ to finish, or aim at the first point to measure the area"
            } else null
            AnimatedVisibility(message != null, enter = fadeIn(), exit = fadeOut()) {
                Text(
                    message.orEmpty(),
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Scrim)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }

        Column(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(bottom = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val selected = selectedId?.let { id -> ar.engine.lastSnapshot?.measurements?.firstOrNull { it.id == id } }
            if (selectedId != null && selected == null) {
                LaunchedEffect(selectedId) { onSelect(null) }
            }
            if (selected != null) {
                // Recomposed on every HUD update, so the numbers follow anchor refinements.
                val clipboard = LocalClipboardManager.current
                Column(
                    Modifier
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xEE1C1C1E))
                        .padding(16.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            Describe.title(selected), color = Color.White, fontSize = 18.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f),
                        )
                        IconButton(onClick = { onSelect(null) }) {
                            Icon(Icons.Filled.Close, "Close", tint = Color.White)
                        }
                    }
                    Describe.details(selected, imperial).forEach {
                        Text(it, color = Color.White.copy(alpha = 0.85f), fontSize = 15.sp)
                    }
                    Row(Modifier.padding(top = 8.dp)) {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(Describe.details(selected, imperial).joinToString("\n")))
                            toast = "Copied"
                        }) {
                            Icon(AppIcons.Copy, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Copy")
                        }
                        TextButton(onClick = {
                            ar.post(MeasureEngine.Action.Delete(selected.id))
                            onSelect(null)
                        }) {
                            Icon(Icons.Filled.Delete, null, tint = Color(0xFFFF453A), modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Delete", color = Color(0xFFFF453A))
                        }
                    }
                }
            }

            ModeSwitch(mode, onModeChange)
            Spacer(Modifier.height(14.dp))

            Row(
                Modifier.fillMaxWidth().padding(horizontal = 28.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RoundIcon(AppIcons.History, "Saved measurements", size = 52, onClick = onOpenHistory)

                Row(verticalAlignment = Alignment.CenterVertically) {
                    AddButton(enabled = hud.canAdd) {
                        onSelect(null)
                        ar.post(MeasureEngine.Action.AddPoint)
                    }
                    AnimatedVisibility(mode == MeasureMode.PATH && hud.activePoints >= 2) {
                        Row {
                            Spacer(Modifier.width(12.dp))
                            RoundIcon(Icons.Filled.Check, "Finish", size = 52) {
                                ar.post(MeasureEngine.Action.Finish)
                            }
                        }
                    }
                }

                RoundIcon(AppIcons.Camera, "Save photo", size = 52, enabled = hud.measurementCount > 0, onClick = onCapture)
            }
        }
    }
}

@Composable
private fun Readout(live: String?, last: String?) {
    val text = live ?: last ?: return
    Text(
        text,
        color = Color.White,
        fontSize = if (live != null) 34.sp else 26.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Scrim)
            .padding(horizontal = 18.dp, vertical = 4.dp),
    )
}

@Composable
private fun ModeSwitch(mode: MeasureMode, onChange: (MeasureMode) -> Unit) {
    Row(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(Scrim)
            .padding(3.dp),
    ) {
        listOf(MeasureMode.LINE to "Line", MeasureMode.PATH to "Path / Area").forEach { (m, label) ->
            val selected = m == mode
            Text(
                label,
                color = if (selected) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Color.White else Color.Transparent)
                    .clickable { onChange(m) }
                    .padding(horizontal = 16.dp, vertical = 7.dp),
            )
        }
    }
}

@Composable
private fun AddButton(enabled: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .size(78.dp)
            .clip(CircleShape)
            .border(4.dp, Color.White.copy(alpha = 0.9f), CircleShape)
            .padding(7.dp)
            .clip(CircleShape)
            .background(if (enabled) Color.White else Color.White.copy(alpha = 0.45f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.Add, contentDescription = "Add point", tint = Color.Black, modifier = Modifier.size(36.dp))
    }
}

@Composable
fun RoundIcon(
    icon: ImageVector,
    description: String,
    size: Int = 44,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .size(size.dp)
            .clip(CircleShape)
            .background(Scrim)
            .alpha(if (enabled) 1f else 0.4f)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = description, tint = Color.White, modifier = Modifier.size((size * 0.5f).dp))
    }
}

/** Shown in place of the HUD when measuring can't run. */
@Composable
fun MeasureBlocked(title: String, body: String, action: String?, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize().background(Color.Black).padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(12.dp))
            Text(body, color = Color.White.copy(alpha = 0.7f), fontSize = 15.sp)
            if (action != null) {
                Spacer(Modifier.height(20.dp))
                Button(onClick = onAction) { Text(action) }
            }
        }
    }
}
