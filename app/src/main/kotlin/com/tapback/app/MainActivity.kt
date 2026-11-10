package com.tapback.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.tapback.app.service.BackTapService
import com.tapback.app.ui.MainAppView
import com.tapback.app.ui.theme.NordBackground
import com.tapback.app.ui.theme.TapBackTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var app: TapBackApplication

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            checkAndStartService()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        app = application as TapBackApplication

        setContent {
            TapBackTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = NordBackground
                ) {
                    MainAppView(app = app)
                }
            }
        }

        // Request notification permission for Foreground Service on Android 13+
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                checkAndStartService()
            }
        } else {
            checkAndStartService()
        }
    }

    private fun checkAndStartService() {
        lifecycleScope.launch {
            val settings = app.container.preferencesManager.settingsFlow.first()
            if (settings.isEnabled) {
                BackTapService.startService(this@MainActivity)
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // If the persistent background service is not running, stop the sensor listeners
        if (!BackTapService.isRunning.value) {
            app.container.sensorController.stopListening()
        }
    }
}
