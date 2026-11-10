package com.tapback.app.di

import android.content.Context
import com.tapback.app.core.actions.ActionExecutor
import com.tapback.app.core.detection.CsvReplayEngine
import com.tapback.app.core.detection.TapDetectorEngine
import com.tapback.app.core.repository.NordSensorRepository
import com.tapback.app.core.sensor.DiagnosticSensorManager
import com.tapback.app.core.sensors.NordSensorController
import com.tapback.app.data.datastore.PreferencesManager

class TapBackContainer(private val context: Context) {
    val diagnosticSensorManager: DiagnosticSensorManager by lazy {
        DiagnosticSensorManager(context)
    }

    val preferencesManager: PreferencesManager by lazy {
        PreferencesManager(context)
    }

    val detectionEngine: TapDetectorEngine by lazy {
        TapDetectorEngine()
    }

    val actionExecutor: ActionExecutor by lazy {
        ActionExecutor(context)
    }

    val sensorController: NordSensorController by lazy {
        NordSensorController(context, detectionEngine)
    }

    val sensorRepository: NordSensorRepository by lazy {
        NordSensorRepository(context)
    }

    val csvReplayEngine: CsvReplayEngine by lazy {
        CsvReplayEngine()
    }
}
