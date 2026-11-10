package com.tapback.app.feature.calibration

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapback.app.TapBackApplication
import com.tapback.app.domain.models.DetectedPeak
import com.tapback.app.domain.models.SensitivityLevel
import com.tapback.app.domain.models.TapBackSettings
import com.tapback.app.ui.theme.CyanAccent
import com.tapback.app.ui.theme.ElectricBlue
import com.tapback.app.ui.theme.NordBackground
import com.tapback.app.ui.theme.NordCard
import com.tapback.app.ui.theme.NordCardBorder
import com.tapback.app.ui.theme.OnePlusRed
import com.tapback.app.ui.theme.RetroNeonCyan
import com.tapback.app.ui.theme.RetroNeonPink
import com.tapback.app.ui.theme.RetroPhosphorAmber
import com.tapback.app.ui.theme.RetroPhosphorGreen
import com.tapback.app.ui.theme.SuccessGreen
import com.tapback.app.ui.theme.TextPrimary
import com.tapback.app.ui.theme.TextSecondary
import com.tapback.app.ui.theme.TextTertiary
import kotlinx.coroutines.launch
import kotlin.math.sqrt

@Composable
fun CalibrationScreen(app: TapBackApplication) {
    val scope = rememberCoroutineScope()
    val settings by app.container.preferencesManager.settingsFlow.collectAsState(initial = TapBackSettings())
    val rawSample by app.container.detectionEngine.liveRawSample.collectAsState()
    val filteredZ by app.container.detectionEngine.liveFilteredZ.collectAsState()

    // Oscilloscope wave buffer (stores last 80 samples of filtered Z)
    val waveBuffer = remember { mutableStateListOf<Float>() }
    val detectedPeaks = remember { mutableStateListOf<DetectedPeak>() }

    LaunchedEffect(filteredZ) {
        waveBuffer.add(filteredZ)
        if (waveBuffer.size > 80) {
            waveBuffer.removeAt(0)
        }
    }

    LaunchedEffect(Unit) {
        app.container.detectionEngine.peakEvents.collect { peak ->
            detectedPeaks.add(0, peak)
            if (detectedPeaks.size > 15) {
                detectedPeaks.removeAt(detectedPeaks.size - 1)
            }
        }
    }

    // Calibration Wizard state
    var isWizardActive by remember { mutableStateOf(false) }
    var wizardStep by remember { mutableStateOf(0) } // 0: not started, 1: tap 1, 2: tap 2, 3: completed
    val collectedWizardPeaks = remember { mutableStateListOf<Float>() }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NordBackground)
            .padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "// SENSOR CALIBRATION",
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "LIVE IMU TELEMETRY // BOSCH BMI26x @ 101 HZ",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        color = RetroNeonCyan
                    )
                }
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = RetroNeonCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Live Oscilloscope Waveform Card (Retro Phosphor CRT Scope)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0A0D15))
                    .border(1.dp, Color(0xFF1B2438), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "OSCILLOSCOPE // Z-AXIS TRANSIENT",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = RetroPhosphorGreen
                    )
                    Text(
                        text = "TRIGGER: ${"%.1f".format(settings.activeThreshold)} m/s²",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = RetroNeonPink
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                // CRT Oscilloscope Screen
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF05080E))
                        .border(1.dp, Color(0xFF142436), RoundedCornerShape(10.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val midY = canvasHeight / 2f

                        // CRT Grid lines (Subtle cyan grid)
                        val numGridLines = 6
                        for (g in 1 until numGridLines) {
                            val gy = canvasHeight * (g.toFloat() / numGridLines)
                            drawLine(
                                color = Color(0x1500F0FF),
                                start = Offset(0f, gy),
                                end = Offset(canvasWidth, gy),
                                strokeWidth = 0.8.dp.toPx()
                            )
                        }
                        for (g in 1..8) {
                            val gx = canvasWidth * (g.toFloat() / 8)
                            drawLine(
                                color = Color(0x1500F0FF),
                                start = Offset(gx, 0f),
                                end = Offset(gx, canvasHeight),
                                strokeWidth = 0.8.dp.toPx()
                            )
                        }

                        // Centerline (Zero-G reference)
                        drawLine(
                            color = Color(0x4000F0FF),
                            start = Offset(0f, midY),
                            end = Offset(canvasWidth, midY),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Threshold Boundary Lines (Neon Pink Trigger Limits)
                        val scopeScale = 15.0f
                        val thresholdYPos = midY - (settings.activeThreshold / scopeScale) * midY
                        val thresholdYNeg = midY + (settings.activeThreshold / scopeScale) * midY
                        drawLine(
                            color = RetroNeonPink.copy(alpha = 0.65f),
                            start = Offset(0f, thresholdYPos),
                            end = Offset(canvasWidth, thresholdYPos),
                            strokeWidth = 1.dp.toPx()
                        )
                        drawLine(
                            color = RetroNeonPink.copy(alpha = 0.65f),
                            start = Offset(0f, thresholdYNeg),
                            end = Offset(canvasWidth, thresholdYNeg),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Phosphor Waveform Trace
                        if (waveBuffer.size > 1) {
                            val step = canvasWidth / 80f
                            val path = Path()
                            for (i in waveBuffer.indices) {
                                val x = i * step
                                val clampedVal = waveBuffer[i].coerceIn(-15f, 15f)
                                val y = midY - (clampedVal / scopeScale) * midY
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(
                                path = path,
                                color = RetroNeonCyan,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Numerical Telemetry readout (4 cleanly aligned columns)
                val totalAcc = sqrt(rawSample.ax * rawSample.ax + rawSample.ay * rawSample.ay + rawSample.az * rawSample.az)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF0F1524))
                        .padding(horizontal = 6.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "AX:${if (rawSample.ax >= 0) "+" else ""}${"%.2f".format(rawSample.ax)}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                        color = TextSecondary
                    )
                    Text(
                        text = "AY:${if (rawSample.ay >= 0) "+" else ""}${"%.2f".format(rawSample.ay)}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                        color = TextSecondary
                    )
                    Text(
                        text = "AZ:${if (rawSample.az >= 0) "+" else ""}${"%.2f".format(rawSample.az)}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                        color = RetroNeonCyan
                    )
                    Text(
                        text = "|A|:${"%.2f".format(totalAcc)}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.weight(1f),
                        color = RetroPhosphorGreen
                    )
                }
            }
        }

        // Sensitivity & Firmness Preset Selector
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0C0F18))
                    .border(1.dp, Color(0xFF1E283C), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "// SENSITIVITY & FIRMNESS",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "THRESHOLD: ${"%.1f".format(settings.activeThreshold)} m/s²",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = RetroNeonCyan
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Controls physical tap force required to trigger gestures. Increase to require firmer, more deliberate strikes.",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    SensitivityLevel.values().forEach { level ->
                        val isSelected = settings.sensitivity == level
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) RetroNeonCyan.copy(alpha = 0.15f) else Color(0xFF121726))
                                .border(
                                    1.dp,
                                    if (isSelected) RetroNeonCyan else Color(0xFF202A3E),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    scope.launch {
                                        app.container.preferencesManager.setSensitivity(level, settings.customThreshold)
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = level.displayName,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                color = if (isSelected) RetroNeonCyan else TextSecondary
                            )
                        }
                    }
                }

                if (settings.sensitivity == SensitivityLevel.CUSTOM) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "CUSTOM FIRMNESS FORCE",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = RetroNeonPink
                        )
                        Text(
                            text = "${"%.1f".format(settings.customThreshold)} m/s²",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = RetroNeonPink
                        )
                    }
                    Slider(
                        value = settings.customThreshold.coerceIn(2.0f, 14.0f),
                        onValueChange = { newVal ->
                            scope.launch {
                                app.container.preferencesManager.setSensitivity(SensitivityLevel.CUSTOM, newVal)
                            }
                        },
                        valueRange = 2.0f..14.0f,
                        colors = SliderDefaults.colors(
                            thumbColor = RetroNeonPink,
                            activeTrackColor = RetroNeonPink,
                            inactiveTrackColor = Color(0xFF1E283C)
                        )
                    )
                }
            }
        }

        // Inter-Tap Cadence Delay Window (Configurable delay between consecutive taps)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0C0F18))
                    .border(1.dp, Color(0xFF1E283C), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "// TAP CADENCE DELAY",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "${settings.maxTapIntervalMs} ms",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = RetroPhosphorGreen
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Maximum delay allowed between consecutive taps in double & triple tap sequences. Increase to tap more slowly and deliberately.",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(12.dp))

                // Presets: FAST (380ms), NORMAL (480ms), RELAXED (620ms), SLOW (750ms)
                val cadencePresets = listOf(
                    380L to "FAST",
                    480L to "NORMAL",
                    620L to "RELAXED",
                    750L to "SLOW"
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    cadencePresets.forEach { (ms, name) ->
                        val isSelected = settings.maxTapIntervalMs == ms
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) RetroPhosphorGreen.copy(alpha = 0.15f) else Color(0xFF121726))
                                .border(
                                    1.dp,
                                    if (isSelected) RetroPhosphorGreen else Color(0xFF202A3E),
                                    RoundedCornerShape(8.dp)
                                )
                                .clickable {
                                    scope.launch {
                                        app.container.preferencesManager.setMaxTapInterval(ms)
                                    }
                                }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = name,
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isSelected) RetroPhosphorGreen else TextSecondary
                                )
                                Text(
                                    text = "${ms}ms",
                                    fontSize = 8.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = if (isSelected) RetroPhosphorGreen else TextTertiary
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))
                Slider(
                    value = settings.maxTapIntervalMs.toFloat().coerceIn(350f, 850f),
                    onValueChange = { newVal ->
                        scope.launch {
                            app.container.preferencesManager.setMaxTapInterval(newVal.toLong())
                        }
                    },
                    valueRange = 350f..850f,
                    colors = SliderDefaults.colors(
                        thumbColor = RetroPhosphorGreen,
                        activeTrackColor = RetroPhosphorGreen,
                        inactiveTrackColor = Color(0xFF1E283C)
                    )
                )
            }
        }

        // Tap Strength Calibration Wizard Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0C0F18))
                    .border(1.dp, Color(0xFF1E283C), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "// CALIBRATION WIZARD",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 0.5.sp,
                            color = TextPrimary
                        )
                        Text(
                            text = "Automated mechanical strike calibration routine",
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = RetroNeonCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                if (!isWizardActive) {
                    Button(
                        onClick = {
                            isWizardActive = true
                            wizardStep = 1
                            collectedWizardPeaks.clear()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF151D2E)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, RetroNeonCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    ) {
                        Text(
                            text = "[ INITIALIZE CALIBRATION ]",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = RetroNeonCyan
                        )
                    }
                } else {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF0F1524))
                            .border(1.dp, Color(0xFF1E283E), RoundedCornerShape(10.dp))
                            .padding(12.dp)
                    ) {
                        when (wizardStep) {
                            1 -> {
                                Text(
                                    text = "STEP 1: PERFORM NATURAL DOUBLE TAP",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = RetroPhosphorGreen
                                )
                                Text(
                                    text = "Strike rear chassis with your typical finger force.",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextSecondary
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                if (detectedPeaks.isNotEmpty()) {
                                    val lastPeak = detectedPeaks.first()
                                    collectedWizardPeaks.add(lastPeak.amplitude)
                                    wizardStep = 2
                                }
                            }
                            2 -> {
                                Text(
                                    text = "STEP 2: CONFIRM FORCE PROFILE",
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = RetroPhosphorAmber
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = {
                                        val avgPeak = if (collectedWizardPeaks.isNotEmpty()) {
                                            collectedWizardPeaks.average().toFloat()
                                        } else 4.5f
                                        val personalizedThreshold = (avgPeak * 0.65f).coerceIn(2.2f, 5.0f)
                                        scope.launch {
                                            app.container.preferencesManager.setSensitivity(
                                                SensitivityLevel.CUSTOM,
                                                personalizedThreshold
                                            )
                                        }
                                        wizardStep = 3
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = RetroNeonCyan),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "COMMIT PERSONALIZED THRESHOLD",
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFF090B10)
                                    )
                                }
                            }
                            3 -> {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.CheckCircle,
                                        contentDescription = null,
                                        tint = RetroPhosphorGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "COMMITTED // THRESHOLD: ${"%.1f".format(settings.customThreshold)} m/s²",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = RetroPhosphorGreen
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Button(
                                    onClick = { isWizardActive = false; wizardStep = 0 },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF161C2C)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text("DONE", fontFamily = FontFamily.Monospace, color = TextPrimary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Recent Detection Log
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0A0D15))
                    .border(1.dp, Color(0xFF1B2438), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Text(
                    text = "// TRANSIENT IMPACT LOG",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(10.dp))

                if (detectedPeaks.isEmpty()) {
                    Text(
                        text = "Awaiting physical mechanical peaks. Strike chassis to log.",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextTertiary
                    )
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        detectedPeaks.take(6).forEach { peak ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0F1422))
                                    .border(1.dp, Color(0xFF1C263A), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(if (peak.confidence > 0.7f) RetroPhosphorGreen else RetroNeonPink)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "PEAK: ${"%.1f".format(peak.amplitude)} m/s²",
                                        fontSize = 11.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                }
                                Text(
                                    text = "CONF: ${"%.0f".format(peak.confidence * 100)}%",
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = RetroNeonCyan
                                )
                            }
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}
