package com.nauuman.measure

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.nauuman.measure.ar.ArController
import com.nauuman.measure.ar.MeasureMode
import com.nauuman.measure.data.AppSettings
import com.nauuman.measure.data.HistoryStore
import com.nauuman.measure.level.LevelScreen
import com.nauuman.measure.ui.Describe
import com.nauuman.measure.ui.HistoryScreen
import com.nauuman.measure.ui.MeasureBlocked
import com.nauuman.measure.ui.MeasureHud
import com.nauuman.measure.ui.share
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private lateinit var ar: ArController
    private lateinit var settings: AppSettings
    private lateinit var history: HistoryStore

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)

        ar = ArController(this)
        settings = AppSettings(this)
        history = HistoryStore(this)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xFFFFD60A),
                    onPrimary = Color.Black,
                    surface = Color(0xFF1C1C1E),
                    background = Color.Black,
                ),
            ) {
                MeasureApp(ar, settings, history)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        ar.onActivityResumed(true)
    }

    override fun onPause() {
        ar.onActivityResumed(false)
        super.onPause()
    }

    override fun onDestroy() {
        ar.destroy()
        super.onDestroy()
    }
}

private enum class Tab { MEASURE, LEVEL }

@Composable
private fun MeasureApp(ar: ArController, settings: AppSettings, history: HistoryStore) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val flash = remember { Animatable(0f) }

    var tab by rememberSaveable { mutableStateOf(Tab.MEASURE) }
    var showHistory by rememberSaveable { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var mode by rememberSaveable { mutableStateOf(MeasureMode.LINE) }
    var selectedId by remember { mutableStateOf<Long?>(null) }

    fun cameraGranted() =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED

    var hasCamera by remember { mutableStateOf(cameraGranted()) }
    var askedOnce by rememberSaveable { mutableStateOf(false) }
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasCamera = granted
        askedOnce = true
        ar.refresh()
    }
    LaunchedEffect(Unit) {
        if (!hasCamera && !askedOnce) permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    // Push settings into the AR layer.
    SideEffect {
        ar.engine.mode = mode
        ar.engine.snapEnabled = settings.snapping
        ar.engine.showPlanes = settings.showPlanes
        ar.hapticsEnabled = settings.haptics
        ar.overlay.imperial = settings.imperial
        ar.overlay.selectedId = selectedId
    }
    DisposableEffect(Unit) {
        ar.overlay.onLabelTap = { id -> selectedId = id }
        onDispose { ar.overlay.onLabelTap = null }
    }

    val measureVisible = tab == Tab.MEASURE && !showHistory
    LaunchedEffect(measureVisible, hasCamera) { ar.setVisible(measureVisible) }

    fun capture() {
        ar.capture { bitmap, snapshot ->
            scope.launch { flash.snapTo(0.8f); flash.animateTo(0f, tween(350)) }
            val summary = Describe.summary(snapshot.measurements, settings.imperial)
            scope.launch {
                val item = withContext(Dispatchers.IO) {
                    try {
                        history.writeCapture(bitmap, summary, settings.saveToGallery)
                    } catch (e: Exception) {
                        null
                    } finally {
                        bitmap.recycle()
                    }
                }
                if (item == null) {
                    Toast.makeText(context, "Couldn't save the photo", Toast.LENGTH_SHORT).show()
                    return@launch
                }
                history.add(item)
                val where = if (settings.saveToGallery && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    "Saved to Measure history and Photos"
                } else {
                    "Saved to Measure history"
                }
                val result = snackbar.showSnackbar(where, actionLabel = "Share", duration = SnackbarDuration.Short)
                if (result == SnackbarResult.ActionPerformed) share(context, history.fileFor(item), item.summary)
            }
        }
    }

    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().background(Color.Black)) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                // The camera + overlay views stay composed; other screens cover them.
                AndroidView(
                    factory = { ctx ->
                        FrameLayout(ctx).apply {
                            val mp = ViewGroup.LayoutParams.MATCH_PARENT
                            addView(ar.surfaceView, FrameLayout.LayoutParams(mp, mp))
                            addView(ar.overlay, FrameLayout.LayoutParams(mp, mp))
                        }
                    },
                    modifier = Modifier.fillMaxSize(),
                )

                when (tab) {
                    Tab.MEASURE -> {
                        val status = ar.status
                        when {
                            !hasCamera -> MeasureBlocked(
                                title = "Camera access needed",
                                body = "Measure uses the camera and Google Play Services for AR to measure objects around you. Nothing leaves your phone.",
                                action = if (askedOnce) "Open settings" else "Allow camera",
                            ) {
                                if (askedOnce) {
                                    context.startActivity(
                                        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}")),
                                    )
                                } else {
                                    permissionLauncher.launch(Manifest.permission.CAMERA)
                                }
                            }
                            status is ArController.Status.Error -> MeasureBlocked(
                                title = "Can't measure right now",
                                body = status.message,
                                action = "Retry",
                            ) { ar.retry() }
                            else -> MeasureHud(
                                ar = ar,
                                settings = settings,
                                mode = mode,
                                onModeChange = { mode = it },
                                selectedId = selectedId,
                                onSelect = { selectedId = it },
                                onCapture = ::capture,
                                onOpenHistory = { showHistory = true },
                                onOpenSettings = { showSettings = true },
                                flash = flash,
                            )
                        }
                    }
                    Tab.LEVEL -> LevelScreen(haptics = settings.haptics)
                }

                SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 150.dp))
            }

            TabBar(tab) {
                tab = it
                selectedId = null
            }
        }

        // When returning from system Settings (e.g. after granting the permission there).
        LifecycleResumeEffect(Unit) {
            hasCamera = cameraGranted()
            ar.refresh()
            onPauseOrDispose { }
        }

        if (showHistory) HistoryScreen(history) { showHistory = false }
        if (showSettings) SettingsDialog(settings) { showSettings = false }
    }
}

@Composable
private fun TabBar(tab: Tab, onSelect: (Tab) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Color.Black)
            .navigationBarsPadding()
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        listOf(Tab.MEASURE to "Measure", Tab.LEVEL to "Level").forEach { (t, label) ->
            Text(
                label,
                color = if (t == tab) Color(0xFFFFD60A) else Color.White.copy(alpha = 0.6f),
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clickable { onSelect(t) }
                    .padding(horizontal = 24.dp, vertical = 6.dp),
            )
        }
    }
}

@Composable
private fun SettingsDialog(settings: AppSettings, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Settings") },
        text = {
            Column {
                SettingRow("Imperial units (ft, in)", settings.imperial, settings::updateImperial)
                SettingRow("Show detected surfaces", settings.showPlanes, settings::updateShowPlanes)
                SettingRow("Snap to existing points", settings.snapping, settings::updateSnapping)
                SettingRow("Haptic feedback", settings.haptics, settings::updateHaptics)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    SettingRow("Also save photos to gallery", settings.saveToGallery, settings::updateSaveToGallery)
                }
                Text(
                    "Tip: Measurements are most accurate within about 3 m, on textured, well-lit surfaces. " +
                        "Move the phone slowly before adding points.",
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.6f),
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
    )
}

@Composable
private fun SettingRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
