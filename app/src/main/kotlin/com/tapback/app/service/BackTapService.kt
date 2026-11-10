package com.tapback.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.tapback.app.MainActivity
import com.tapback.app.R
import com.tapback.app.TapBackApplication
import com.tapback.app.domain.models.ActionType
import com.tapback.app.domain.models.GestureTriggerEvent
import com.tapback.app.domain.models.GestureType
import com.tapback.app.domain.models.TapBackSettings
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Foreground Service hosting the BackTap detection loop, ensuring persistent execution
 * and surviving OxygenOS background resource trimming.
 */
class BackTapService : Service() {

    companion object {
        const val CHANNEL_ID = "tapback_service_channel"
        const val NOTIFICATION_ID = 1001

        const val ACTION_START = "com.tapback.app.action.START"
        const val ACTION_STOP = "com.tapback.app.action.STOP"

        private val _isRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

        private val _recentEvents = MutableStateFlow<List<GestureTriggerEvent>>(emptyList())
        val recentEvents: StateFlow<List<GestureTriggerEvent>> = _recentEvents.asStateFlow()

        fun startService(context: Context) {
            val intent = Intent(context, BackTapService::class.java).apply {
                action = ACTION_START
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stopService(context: Context) {
            val intent = Intent(context, BackTapService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var currentSettings = TapBackSettings()
    private var isScreenOn = true

    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                Intent.ACTION_SCREEN_OFF -> {
                    isScreenOn = false
                    handleScreenStateChanged()
                }
                Intent.ACTION_SCREEN_ON -> {
                    isScreenOn = true
                    handleScreenStateChanged()
                }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        registerScreenReceiver()
        observeSettings()
        observeGestures()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopForegroundService()
                return START_NOT_STICKY
            }
            ACTION_START, null -> {
                startForegroundWithNotification()
                _isRunning.value = true
                applySensorState()
            }
        }
        return START_STICKY
    }

    private fun startForegroundWithNotification() {
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                } else 0
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopForegroundService() {
        _isRunning.value = false
        val app = application as? TapBackApplication
        app?.container?.sensorController?.stopListening()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun handleScreenStateChanged() {
        val app = application as? TapBackApplication ?: return
        if (!currentSettings.isEnabled) return

        if (!isScreenOn && !currentSettings.screenOffDetection) {
            // Power saving optimization for OxygenOS: stop sensors while screen is asleep
            app.container.sensorController.stopListening()
            Log.d("BackTapService", "Screen OFF: stopped sensors for battery saving")
        } else {
            // Screen turned ON or screen-off detection is allowed
            app.container.sensorController.startListening()
            Log.d("BackTapService", "Screen ON: listening resumed")
        }
    }

    private fun applySensorState() {
        val app = application as? TapBackApplication ?: return
        if (currentSettings.isEnabled && (isScreenOn || currentSettings.screenOffDetection)) {
            app.container.sensorController.startListening()
        } else {
            app.container.sensorController.stopListening()
        }
    }

    private fun observeSettings() {
        val app = application as? TapBackApplication ?: return
        serviceScope.launch {
            app.container.preferencesManager.settingsFlow.collect { settings ->
                currentSettings = settings
                app.container.detectionEngine.updateSettings(settings)
                applySensorState()
            }
        }
    }

    private fun observeGestures() {
        val app = application as? TapBackApplication ?: return
        serviceScope.launch {
            app.container.detectionEngine.gestureEvents.collect { gesture ->
                handleGestureDetected(gesture, app)
            }
        }
    }

    private fun handleGestureDetected(
        gesture: com.tapback.app.core.detection.TapDetectorEngine.GestureDetected,
        app: TapBackApplication
    ) {
        val (action, targetPackage) = if (!isScreenOn && currentSettings.screenOffDetection) {
            when (gesture.gestureType) {
                GestureType.SINGLE_TAP -> Pair(ActionType.NONE, null)
                GestureType.DOUBLE_TAP -> Pair(currentSettings.screenOffDoubleTapAction, currentSettings.doubleTapPackage)
                GestureType.TRIPLE_TAP -> Pair(currentSettings.screenOffTripleTapAction, currentSettings.tripleTapPackage)
            }
        } else {
            when (gesture.gestureType) {
                GestureType.SINGLE_TAP -> Pair(currentSettings.singleTapAction, currentSettings.singleTapPackage)
                GestureType.DOUBLE_TAP -> Pair(currentSettings.doubleTapAction, currentSettings.doubleTapPackage)
                GestureType.TRIPLE_TAP -> Pair(currentSettings.tripleTapAction, currentSettings.tripleTapPackage)
            }
        }

        if (currentSettings.hapticFeedback) {
            app.container.actionExecutor.triggerHapticFeedback(gesture.gestureType)
        }
        if (currentSettings.soundFeedback) {
            app.container.actionExecutor.triggerSoundFeedback()
        }

        serviceScope.launch {
            app.container.actionExecutor.executeAction(action, targetPackage)
        }

        val event = GestureTriggerEvent(
            gestureType = gesture.gestureType,
            peakCount = gesture.peakCount,
            peakAmplitudes = gesture.amplitudes,
            confidence = gesture.confidence,
            actionExecuted = action
        )
        val updated = (_recentEvents.value.take(20) + event).sortedByDescending { it.timestampMs }
        _recentEvents.value = updated
    }

    private fun registerScreenReceiver() {
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_ON)
            addAction(Intent.ACTION_SCREEN_OFF)
        }
        registerReceiver(screenReceiver, filter)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.notification_channel_desc)
                setShowBadge(false)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingOpen = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, BackTapService::class.java).apply {
            action = ACTION_STOP
        }
        val pendingStop = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_running_title))
            .setContentText(getString(R.string.notification_running_text))
            .setSmallIcon(android.R.drawable.ic_lock_idle_charging)
            .setOngoing(true)
            .setContentIntent(pendingOpen)
            .addAction(android.R.drawable.ic_media_pause, "Pause", pendingStop)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(screenReceiver)
        } catch (e: Exception) {
            // Already unregistered
        }
        serviceScope.cancel()
        _isRunning.value = false
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
