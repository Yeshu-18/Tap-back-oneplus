package com.tapback.app.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.tapback.app.TapBackApplication
import com.tapback.app.service.BackTapService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restores TapBack foreground service upon device reboot if enabled in user preferences.
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.intent.action.QUICKBOOT_POWERON" -> {
                Log.d("BootReceiver", "Boot broadcast received: ${intent.action}")
                val app = context.applicationContext as? TapBackApplication ?: return

                CoroutineScope(Dispatchers.IO).launch {
                    val settings = app.container.preferencesManager.settingsFlow.first()
                    if (settings.isEnabled && settings.startOnBoot) {
                        Log.d("BootReceiver", "Auto-starting BackTapService on boot")
                        BackTapService.startService(context)
                    }
                }
            }
        }
    }
}
