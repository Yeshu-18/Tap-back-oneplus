package com.tapback.app.core.actions

import android.content.Context
import android.content.Intent
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.CombinedVibration
import android.os.PowerManager
import android.os.SystemClock
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.MediaStore
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import com.tapback.app.domain.models.ActionType
import com.tapback.app.service.TapBackAccessibilityService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Executes user-configured actions when physical tap gestures are validated.
 */
class ActionExecutor(private val context: Context) {

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as? CameraManager
    private var isTorchOn = false

    private val cameraTorchCallback = object : CameraManager.TorchCallback() {
        override fun onTorchModeChanged(cameraId: String, enabled: Boolean) {
            super.onTorchModeChanged(cameraId, enabled)
            isTorchOn = enabled
        }
    }

    init {
        try {
            cameraManager?.registerTorchCallback(cameraTorchCallback, null)
        } catch (e: Exception) {
            Log.w("ActionExecutor", "Failed to register torch callback: ${e.message}")
        }
    }

    suspend fun executeAction(actionType: ActionType, extraPackage: String? = null): Boolean {
        return withContext(Dispatchers.Main) {
            when (actionType) {
                ActionType.NONE -> true

                ActionType.SCREENSHOT -> {
                    val service = TapBackAccessibilityService.instance
                    if (service != null) {
                        service.takeScreenshot()
                    } else {
                        showToast("Accessibility Service required for Screenshot")
                        false
                    }
                }

                ActionType.FLASHLIGHT -> toggleFlashlight()

                ActionType.OPEN_CAMERA -> openCamera()

                ActionType.OPEN_ASSISTANT -> openAssistant()

                ActionType.LAUNCH_APP -> launchApp(extraPackage)

                ActionType.CALL_CONTACT -> callContact(extraPackage)

                ActionType.MEDIA_PLAY_PAUSE -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)

                ActionType.MEDIA_NEXT -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)

                ActionType.MEDIA_PREV -> sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

                ActionType.VOLUME_UP -> {
                    audioManager?.adjustVolume(AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
                    true
                }

                ActionType.VOLUME_DOWN -> {
                    audioManager?.adjustVolume(AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
                    true
                }

                ActionType.OPEN_NOTIFICATIONS -> {
                    val service = TapBackAccessibilityService.instance
                    if (service != null) {
                        service.openNotifications()
                    } else {
                        showToast("Accessibility Service required for Notifications")
                        false
                    }
                }

                ActionType.OPEN_QUICK_SETTINGS -> {
                    val service = TapBackAccessibilityService.instance
                    if (service != null) {
                        service.openQuickSettings()
                    } else {
                        showToast("Accessibility Service required for Quick Settings")
                        false
                    }
                }

                ActionType.LOCK_SCREEN -> {
                    val service = TapBackAccessibilityService.instance
                    if (service != null) {
                        service.lockScreen()
                    } else {
                        showToast("Accessibility Service required to Lock Screen")
                        false
                    }
                }
            }
        }
    }

    private fun toggleFlashlight(): Boolean {
        val cm = cameraManager ?: return false
        return try {
            val cameraId = cm.cameraIdList.firstOrNull { id ->
                val chars = cm.getCameraCharacteristics(id)
                chars.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            } ?: "0"

            val targetState = !isTorchOn
            cm.setTorchMode(cameraId, targetState)
            isTorchOn = targetState
            true
        } catch (e: Exception) {
            Log.e("ActionExecutor", "Error toggling flashlight: ${e.message}")
            false
        }
    }

    private fun openCamera(): Boolean {
        return try {
            val intent = Intent(MediaStore.INTENT_ACTION_STILL_IMAGE_CAMERA).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val fallbackIntent = Intent(MediaStore.ACTION_IMAGE_CAPTURE).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(fallbackIntent)
                true
            } catch (ex: Exception) {
                false
            }
        }
    }

    private fun wakeScreenIfNeeded() {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
            if (pm?.isInteractive == false) {
                @Suppress("DEPRECATION")
                val wakeLock = pm.newWakeLock(
                    PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP or PowerManager.ON_AFTER_RELEASE,
                    "TapBack:ActionWakeLock"
                )
                wakeLock.acquire(3000)
            }
        } catch (e: Exception) {
            Log.w("ActionExecutor", "WakeLock acquisition failed: ${e.message}")
        }
    }

    private fun openAssistant(): Boolean {
        wakeScreenIfNeeded()

        // 1. Prioritize official Gemini standalone application (package: com.google.android.apps.bard)
        try {
            val geminiIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.apps.bard")
            if (geminiIntent != null) {
                geminiIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(geminiIntent)
                return true
            }
        } catch (e: Exception) {
            Log.w("ActionExecutor", "Failed to launch com.google.android.apps.bard: ${e.message}")
        }

        // 2. Launch voice assistant overlay via ACTION_VOICE_ASSIST (opens Gemini Robin overlay)
        try {
            val voiceAssistIntent = Intent("android.intent.action.VOICE_ASSIST").apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            context.startActivity(voiceAssistIntent)
            return true
        } catch (e: Exception) {
            Log.w("ActionExecutor", "Failed ACTION_VOICE_ASSIST: ${e.message}")
        }

        // 3. Fallback to standard ACTION_ASSIST
        try {
            val assistIntent = Intent(Intent.ACTION_ASSIST).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
            }
            context.startActivity(assistIntent)
            return true
        } catch (ex: Exception) {
            Log.w("ActionExecutor", "Failed ACTION_ASSIST: ${ex.message}")
        }

        // 4. Fallback to Google Search launcher
        try {
            val googleIntent = context.packageManager.getLaunchIntentForPackage("com.google.android.googlequicksearchbox")
            if (googleIntent != null) {
                googleIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(googleIntent)
                return true
            }
        } catch (e: Exception) {
            Log.e("ActionExecutor", "Failed to launch Google search fallback: ${e.message}")
        }

        showToast("Could not launch Gemini / Assistant")
        return false
    }

    private fun launchApp(packageName: String?): Boolean {
        if (packageName.isNullOrBlank()) {
            showToast("No application selected")
            return false
        }
        wakeScreenIfNeeded()
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
                context.startActivity(intent)
                true
            } else {
                showToast("Application not found")
                false
            }
        } catch (e: Exception) {
            Log.e("ActionExecutor", "Failed to launch package $packageName: ${e.message}")
            false
        }
    }

    private fun callContact(contactData: String?): Boolean {
        if (contactData.isNullOrBlank()) {
            showToast("No contact configured for speed dial")
            return false
        }
        wakeScreenIfNeeded()
        val phoneNumber = if (contactData.contains("|")) {
            contactData.substringBefore("|").trim()
        } else {
            contactData.trim()
        }

        return try {
            val hasCallPermission = context.checkSelfPermission(android.Manifest.permission.CALL_PHONE) == android.content.pm.PackageManager.PERMISSION_GRANTED
            val intent = if (hasCallPermission) {
                Intent(Intent.ACTION_CALL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            } else {
                Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            }
            context.startActivity(intent)
            true
        } catch (e: Exception) {
            try {
                val dialIntent = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$phoneNumber")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(dialIntent)
                true
            } catch (ex: Exception) {
                Log.e("ActionExecutor", "Failed to call contact $phoneNumber: ${ex.message}")
                showToast("Could not place call")
                false
            }
        }
    }

    private fun sendMediaKey(keyCode: Int): Boolean {
        val am = audioManager ?: return false
        val downEvent = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_DOWN, keyCode, 0)
        val upEvent = KeyEvent(SystemClock.uptimeMillis(), SystemClock.uptimeMillis(), KeyEvent.ACTION_UP, keyCode, 0)
        am.dispatchMediaKeyEvent(downEvent)
        am.dispatchMediaKeyEvent(upEvent)
        return true
    }

    fun triggerHapticFeedback(gestureType: com.tapback.app.domain.models.GestureType = com.tapback.app.domain.models.GestureType.DOUBLE_TAP) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                val effect = when (gestureType) {
                    com.tapback.app.domain.models.GestureType.SINGLE_TAP -> {
                        VibrationEffect.createPredefined(VibrationEffect.EFFECT_CLICK)
                    }
                    com.tapback.app.domain.models.GestureType.DOUBLE_TAP -> {
                        VibrationEffect.createWaveform(longArrayOf(0, 25, 60, 25), intArrayOf(0, 180, 0, 220), -1)
                    }
                    com.tapback.app.domain.models.GestureType.TRIPLE_TAP -> {
                        VibrationEffect.createWaveform(longArrayOf(0, 25, 50, 25, 50, 25), intArrayOf(0, 160, 0, 190, 0, 240), -1)
                    }
                }
                vibratorManager?.vibrate(CombinedVibration.createParallel(effect))
            } else {
                @Suppress("DEPRECATION")
                val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    val effect = when (gestureType) {
                        com.tapback.app.domain.models.GestureType.SINGLE_TAP -> VibrationEffect.createOneShot(30, VibrationEffect.DEFAULT_AMPLITUDE)
                        com.tapback.app.domain.models.GestureType.DOUBLE_TAP -> VibrationEffect.createWaveform(longArrayOf(0, 25, 60, 25), -1)
                        com.tapback.app.domain.models.GestureType.TRIPLE_TAP -> VibrationEffect.createWaveform(longArrayOf(0, 25, 50, 25, 50, 25), -1)
                    }
                    vibrator?.vibrate(effect)
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(35)
                }
            }
        } catch (e: Exception) {
            Log.w("ActionExecutor", "Haptics not supported or failed: ${e.message}")
        }
    }

    fun triggerSoundFeedback() {
        try {
            val tone = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP, 50)
        } catch (e: Exception) {
            Log.w("ActionExecutor", "Sound tone failed: ${e.message}")
        }
    }

    private fun showToast(msg: String) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
    }
}
