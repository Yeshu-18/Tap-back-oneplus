package com.tapback.app.data.datastore

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.tapback.app.domain.models.ActionType
import com.tapback.app.domain.models.SensitivityLevel
import com.tapback.app.domain.models.TapBackSettings
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "tapback_settings")

class PreferencesManager(private val context: Context) {

    private object Keys {
        val IS_ENABLED = booleanPreferencesKey("is_enabled")
        val SINGLE_TAP_ACTION = stringPreferencesKey("single_tap_action")
        val SINGLE_TAP_PKG = stringPreferencesKey("single_tap_package")
        val DOUBLE_TAP_ACTION = stringPreferencesKey("double_tap_action")
        val DOUBLE_TAP_PKG = stringPreferencesKey("double_tap_package")
        val TRIPLE_TAP_ACTION = stringPreferencesKey("triple_tap_action")
        val TRIPLE_TAP_PKG = stringPreferencesKey("triple_tap_package")
        val SCREEN_OFF_DOUBLE_TAP = stringPreferencesKey("screen_off_double_tap")
        val SCREEN_OFF_TRIPLE_TAP = stringPreferencesKey("screen_off_triple_tap")
        val SENSITIVITY = stringPreferencesKey("sensitivity")
        val CUSTOM_THRESHOLD = floatPreferencesKey("custom_threshold")
        val MIN_INTERVAL_MS = longPreferencesKey("min_interval_ms")
        val MAX_INTERVAL_MS = longPreferencesKey("max_interval_ms")
        val COOLDOWN_MS = longPreferencesKey("cooldown_ms")
        val HAPTIC_FEEDBACK = booleanPreferencesKey("haptic_feedback")
        val SOUND_FEEDBACK = booleanPreferencesKey("sound_feedback")
        val START_ON_BOOT = booleanPreferencesKey("start_on_boot")
        val SCREEN_OFF_DETECTION = booleanPreferencesKey("screen_off_detection")
        val DEBUG_MODE = booleanPreferencesKey("debug_mode")
    }

    val settingsFlow: Flow<TapBackSettings> = context.dataStore.data.map { prefs ->
        TapBackSettings(
            isEnabled = prefs[Keys.IS_ENABLED] ?: true,
            singleTapAction = safeAction(prefs[Keys.SINGLE_TAP_ACTION], ActionType.NONE),
            singleTapPackage = prefs[Keys.SINGLE_TAP_PKG],
            doubleTapAction = safeAction(prefs[Keys.DOUBLE_TAP_ACTION], ActionType.SCREENSHOT),
            doubleTapPackage = prefs[Keys.DOUBLE_TAP_PKG],
            tripleTapAction = safeAction(prefs[Keys.TRIPLE_TAP_ACTION], ActionType.OPEN_ASSISTANT),
            tripleTapPackage = prefs[Keys.TRIPLE_TAP_PKG],
            screenOffDoubleTapAction = safeAction(prefs[Keys.SCREEN_OFF_DOUBLE_TAP], ActionType.FLASHLIGHT),
            screenOffTripleTapAction = safeAction(prefs[Keys.SCREEN_OFF_TRIPLE_TAP], ActionType.NONE),
            sensitivity = safeSensitivity(prefs[Keys.SENSITIVITY], SensitivityLevel.MEDIUM),
            customThreshold = prefs[Keys.CUSTOM_THRESHOLD] ?: 2.80f,
            minTapIntervalMs = prefs[Keys.MIN_INTERVAL_MS] ?: 135L,
            maxTapIntervalMs = prefs[Keys.MAX_INTERVAL_MS] ?: 480L,
            cooldownPeriodMs = prefs[Keys.COOLDOWN_MS] ?: 350L,
            hapticFeedback = prefs[Keys.HAPTIC_FEEDBACK] ?: true,
            soundFeedback = prefs[Keys.SOUND_FEEDBACK] ?: false,
            startOnBoot = prefs[Keys.START_ON_BOOT] ?: true,
            screenOffDetection = prefs[Keys.SCREEN_OFF_DETECTION] ?: false,
            debugMode = prefs[Keys.DEBUG_MODE] ?: true
        )
    }

    suspend fun updateSettings(settings: TapBackSettings) {
        context.dataStore.edit { prefs ->
            prefs[Keys.IS_ENABLED] = settings.isEnabled
            prefs[Keys.SINGLE_TAP_ACTION] = settings.singleTapAction.name
            if (settings.singleTapPackage != null) {
                prefs[Keys.SINGLE_TAP_PKG] = settings.singleTapPackage
            } else {
                prefs.remove(Keys.SINGLE_TAP_PKG)
            }
            prefs[Keys.DOUBLE_TAP_ACTION] = settings.doubleTapAction.name
            if (settings.doubleTapPackage != null) {
                prefs[Keys.DOUBLE_TAP_PKG] = settings.doubleTapPackage
            } else {
                prefs.remove(Keys.DOUBLE_TAP_PKG)
            }
            prefs[Keys.TRIPLE_TAP_ACTION] = settings.tripleTapAction.name
            if (settings.tripleTapPackage != null) {
                prefs[Keys.TRIPLE_TAP_PKG] = settings.tripleTapPackage
            } else {
                prefs.remove(Keys.TRIPLE_TAP_PKG)
            }
            prefs[Keys.SCREEN_OFF_DOUBLE_TAP] = settings.screenOffDoubleTapAction.name
            prefs[Keys.SCREEN_OFF_TRIPLE_TAP] = settings.screenOffTripleTapAction.name
            prefs[Keys.SENSITIVITY] = settings.sensitivity.name
            prefs[Keys.CUSTOM_THRESHOLD] = settings.customThreshold
            prefs[Keys.MIN_INTERVAL_MS] = settings.minTapIntervalMs
            prefs[Keys.MAX_INTERVAL_MS] = settings.maxTapIntervalMs
            prefs[Keys.COOLDOWN_MS] = settings.cooldownPeriodMs
            prefs[Keys.HAPTIC_FEEDBACK] = settings.hapticFeedback
            prefs[Keys.SOUND_FEEDBACK] = settings.soundFeedback
            prefs[Keys.START_ON_BOOT] = settings.startOnBoot
            prefs[Keys.SCREEN_OFF_DETECTION] = settings.screenOffDetection
            prefs[Keys.DEBUG_MODE] = settings.debugMode
        }
    }

    suspend fun setEnabled(enabled: Boolean) {
        context.dataStore.edit { it[Keys.IS_ENABLED] = enabled }
    }

    suspend fun setSingleTapAction(action: ActionType, packageName: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SINGLE_TAP_ACTION] = action.name
            if (packageName != null) prefs[Keys.SINGLE_TAP_PKG] = packageName
            else prefs.remove(Keys.SINGLE_TAP_PKG)
        }
    }

    suspend fun setDoubleTapAction(action: ActionType, packageName: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[Keys.DOUBLE_TAP_ACTION] = action.name
            if (packageName != null) prefs[Keys.DOUBLE_TAP_PKG] = packageName
            else prefs.remove(Keys.DOUBLE_TAP_PKG)
        }
    }

    suspend fun setTripleTapAction(action: ActionType, packageName: String? = null) {
        context.dataStore.edit { prefs ->
            prefs[Keys.TRIPLE_TAP_ACTION] = action.name
            if (packageName != null) prefs[Keys.TRIPLE_TAP_PKG] = packageName
            else prefs.remove(Keys.TRIPLE_TAP_PKG)
        }
    }

    suspend fun setSensitivity(sensitivity: SensitivityLevel, customThreshold: Float = 2.80f) {
        context.dataStore.edit { prefs ->
            prefs[Keys.SENSITIVITY] = sensitivity.name
            prefs[Keys.CUSTOM_THRESHOLD] = customThreshold
        }
    }

    suspend fun setMaxTapInterval(intervalMs: Long) {
        context.dataStore.edit { prefs ->
            prefs[Keys.MAX_INTERVAL_MS] = intervalMs
        }
    }

    private fun safeAction(name: String?, default: ActionType): ActionType {
        if (name == null) return default
        return try { ActionType.valueOf(name) } catch (e: Exception) { default }
    }

    private fun safeSensitivity(name: String?, default: SensitivityLevel): SensitivityLevel {
        if (name == null) return default
        return try { SensitivityLevel.valueOf(name) } catch (e: Exception) { default }
    }
}
