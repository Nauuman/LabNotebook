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
import androidx.activity.ComponentActivity
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
            requestPermissions = { permissionLauncher.launch(Permissions.foreground) },
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
        )

        setContent {
            PodPulseTheme {
                MainScreen(env, actions)
            }
        }
    }

    override fun onStart() {
        super.onStart()
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
