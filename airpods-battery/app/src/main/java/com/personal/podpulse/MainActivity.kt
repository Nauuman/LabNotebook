package com.personal.podpulse

import android.Manifest
import android.annotation.SuppressLint
import android.appwidget.AppWidgetManager
import android.bluetooth.BluetoothAdapter
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.personal.podpulse.ble.Permissions
import com.personal.podpulse.ble.PodScanner
import com.personal.podpulse.ble.ScanDiagnostics
import com.personal.podpulse.data.AppSettings
import com.personal.podpulse.data.Place
import com.personal.podpulse.features.Alerts
import com.personal.podpulse.features.BtDevices
import com.personal.podpulse.features.PodService
import com.personal.podpulse.ui.FindScreen
import com.personal.podpulse.ui.LastSeenScreen
import com.personal.podpulse.ui.EnvState
import com.personal.podpulse.ui.MainScreen
import com.personal.podpulse.ui.PodPulseTheme
import com.personal.podpulse.ui.ScreenActions
import com.personal.podpulse.widget.PodWidgetReceiver
import com.personal.podpulse.widget.WidgetUpdater
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private var env by mutableStateOf(EnvState())
    private var liveScanning = false
    private var backgroundArmed = false
    private var serviceChecked = false

    private val liveCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult) {
            onResult(result)
        }

        override fun onBatchScanResults(results: MutableList<ScanResult>) {
            results.forEach(::onResult)
        }

        override fun onScanFailed(errorCode: Int) {
            liveScanning = false
            ScanDiagnostics.setLiveScan("Failed (error $errorCode)")
        }
    }

    private val bluetoothStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            refresh()
        }
    }

    private val permissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }

    private val backgroundLocationLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    private val enableBluetoothLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)

        val actions = ScreenActions(
            requestPermissions = { permissionLauncher.launch(Permissions.foreground + notificationPermission()) },
            requestBackgroundLocation = ::requestBackgroundLocation,
            enableBluetooth = ::enableBluetooth,
            openLocationSettings = {
                startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS))
            },
            setBackgroundEnabled = { enabled ->
                AppSettings.setBackgroundEnabled(this, enabled)
                backgroundArmed = false
                refresh()
            },
            requestBatteryExemption = ::requestBatteryExemption,
            pinWidget = ::pinWidget,
            setFlag = { key, enabled ->
                AppSettings.setFlag(this, key, enabled)
                serviceChecked = false
                refresh()
            },
            selectDevice = { device ->
                AppSettings.setDevice(this, device.address, device.name)
                serviceChecked = false
                refresh()
            },
            requestNotifications = ::requestNotifications,
            requestOverlay = {
                runCatching {
                    startActivity(
                        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                    )
                }
            },
            openFind = { screen = Screen.Find },
            openLastSeen = { screen = Screen.LastSeen },
        )

        setContent {
            PodPulseTheme {
                BackHandler(enabled = screen != Screen.Home) { screen = Screen.Home }
                when (screen) {
                    Screen.Home -> MainScreen(env, actions)
                    Screen.Find -> FindScreen(onBack = { screen = Screen.Home })
                    Screen.LastSeen -> LastSeenScreen(
                        onBack = { screen = Screen.Home },
                        openMap = ::openMap,
                        directions = ::openDirections,
                        hasBackgroundLocation = env.hasBackgroundLocation,
                        requestBackgroundLocation = ::requestBackgroundLocation,
                    )
                }
            }
        }
    }

    private enum class Screen { Home, Find, LastSeen }

    private var screen by mutableStateOf(Screen.Home)

    override fun onStart() {
        super.onStart()
        isVisible = true
        ContextCompat.registerReceiver(
            this,
            bluetoothStateReceiver,
            IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        refresh()
    }

    override fun onResume() {
        super.onResume()
        // Permissions or settings may have changed while we were in system settings.
        refresh()
    }

    override fun onStop() {
        unregisterReceiver(bluetoothStateReceiver)
        stopLive()
        backgroundArmed = false
        serviceChecked = false
        isVisible = false
        super.onStop()
    }

    private fun onResult(result: ScanResult) {
        if (PodScanner.handle(this, result)) {
            val app = applicationContext
            lifecycleScope.launch { WidgetUpdater.push(app) }
        }
    }

    /** Re-reads the environment and (re)starts scanning as appropriate. */
    private fun refresh() {
        env = EnvState(
            hasPermissions = Permissions.hasForeground(this),
            hasBackgroundLocation = Permissions.hasBackground(this),
            bluetoothOn = PodScanner.isBluetoothOn(this),
            locationOn = Permissions.isLocationOn(this),
            backgroundEnabled = AppSettings.backgroundEnabled(this),
            ignoringBatteryOptimizations = Permissions.isIgnoringBatteryOptimizations(this),
            notificationsAllowed = Alerts.canNotify(this),
            canDrawOverlays = Settings.canDrawOverlays(this),
            pairedDevices = BtDevices.paired(this),
            deviceName = AppSettings.deviceName(this),
            flags = listOf(
                AppSettings.AUTO_PAUSE,
                AppSettings.CASE_POPUP,
                AppSettings.LOW_BATTERY,
                AppSettings.REMEMBER_LOCATION,
            ).associateWith { AppSettings.flag(this, it) },
        )

        val canScan = env.hasPermissions && env.bluetoothOn
        if (!canScan) {
            // Scans die with Bluetooth; start fresh once it's back.
            liveScanning = false
            ScanDiagnostics.setLiveScan(if (env.hasPermissions) "Bluetooth off" else "Missing permission")
            backgroundArmed = false
            return
        }
        if (!liveScanning) {
            liveScanning = PodScanner.startLive(this, liveCallback)
        }
        if (!backgroundArmed) {
            if (env.backgroundEnabled) PodScanner.armBackground(this) else PodScanner.disarmBackground(this)
            backgroundArmed = true
        }
        if (!serviceChecked) {
            serviceChecked = true
            BtDevices.autoSelect(this)
            env = env.copy(deviceName = AppSettings.deviceName(this))
            // Start the connected-mode service now if the AirPods are already connected.
            if (PodService.needed(this)) {
                BtDevices.queryConnected(this) { connected ->
                    if (connected) PodService.start(this)
                }
            } else {
                PodService.stop(this)
            }
        }
    }

    private fun notificationPermission(): Array<String> =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            arrayOf(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            emptyArray()
        }

    private fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED &&
            !askedNotifications
        ) {
            // Ask once in-app; after that Android only allows changing it in settings.
            askedNotifications = true
            permissionLauncher.launch(notificationPermission())
            return
        }
        runCatching {
            startActivity(
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, packageName),
            )
        }
    }

    private var askedNotifications = false

    private fun openMap(place: Place) {
        val uri = Uri.parse(
            "geo:${place.latitude},${place.longitude}?q=${place.latitude},${place.longitude}(My AirPods)",
        )
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }.onFailure {
            Toast.makeText(this, "No maps app found", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openDirections(place: Place) {
        val uri = Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=${place.latitude},${place.longitude}&travelmode=walking",
        )
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, uri)) }
    }

    private fun stopLive() {
        if (liveScanning) {
            PodScanner.stopLive(this, liveCallback)
            liveScanning = false
            ScanDiagnostics.setLiveScan("Stopped")
        }
    }

    private fun requestBackgroundLocation() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // On Android 11+ this opens the app's location settings page.
            backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }
    }

    @SuppressLint("MissingPermission") // only reachable once permissions are granted
    private fun enableBluetooth() {
        runCatching {
            enableBluetoothLauncher.launch(Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE))
        }.onFailure {
            startActivity(Intent(Settings.ACTION_BLUETOOTH_SETTINGS))
        }
    }

    @SuppressLint("BatteryLife") // personal sideloaded app, not distributed on Play
    private fun requestBatteryExemption() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName")),
            )
        }
    }

    companion object {
        /** Lets the service skip the pop-up while the app itself is on screen. */
        @Volatile
        var isVisible = false
            private set
    }

    private fun pinWidget() {
        val manager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, PodWidgetReceiver::class.java)
        if (manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(provider, null, null)
        } else {
            Toast.makeText(
                this,
                "Long-press your home screen → Widgets → PodPulse",
                Toast.LENGTH_LONG,
            ).show()
        }
    }
}
