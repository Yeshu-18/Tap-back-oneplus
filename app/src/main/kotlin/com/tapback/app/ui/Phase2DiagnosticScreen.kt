package com.tapback.app.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapback.app.TapBackApplication
import com.tapback.app.core.detection.CsvReplayEngine
import com.tapback.app.core.detection.DetectionDiagnostics
import com.tapback.app.core.detection.TapDetectorEngine
import com.tapback.app.domain.models.GestureClassification
import com.tapback.app.domain.models.GestureType
import com.tapback.app.domain.models.ImpactEvent
import com.tapback.app.domain.models.ProcessedSensorSample
import com.tapback.app.domain.models.ReplayBenchmarkResult
import com.tapback.app.ui.theme.CyanAccent
import com.tapback.app.ui.theme.ElectricBlue
import com.tapback.app.ui.theme.NordBackground
import com.tapback.app.ui.theme.NordCard
import com.tapback.app.ui.theme.NordCardBorder
import com.tapback.app.ui.theme.OnePlusRed
import com.tapback.app.ui.theme.OnePlusRedGlow
import com.tapback.app.ui.theme.RetroNeonCyan
import com.tapback.app.ui.theme.RetroNeonPink
import com.tapback.app.ui.theme.RetroPhosphorAmber
import com.tapback.app.ui.theme.RetroPhosphorGreen
import com.tapback.app.ui.theme.SuccessGreen
import com.tapback.app.ui.theme.TextPrimary
import com.tapback.app.ui.theme.TextSecondary
import com.tapback.app.ui.theme.TextTertiary
import com.tapback.app.ui.theme.WarningOrange
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max

enum class DiagnosticTab(val title: String) {
    LIVE_STREAM("// LIVE TELEMETRY"),
    CSV_REPLAY("// CSV REPLAY LAB")
}

@Composable
fun Phase2DiagnosticScreen(
    app: TapBackApplication
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var selectedTab by remember { mutableStateOf(DiagnosticTab.LIVE_STREAM) }

    val detectionEngine = app.container.detectionEngine
    val sensorController = app.container.sensorController
    val csvReplayEngine = app.container.csvReplayEngine

    val isListening by sensorController.isListening.collectAsState()
    val diagnostics = detectionEngine.diagnostics

    val latestSample by diagnostics.latestSample.collectAsState()
    val dynamicWaveform by diagnostics.dynamicZWaveform.collectAsState()
    val recentImpacts by diagnostics.recentImpacts.collectAsState()
    val sequenceState by diagnostics.sequenceState.collectAsState()
    val totalSamples by diagnostics.totalSamples.collectAsState()
    val singleCount by diagnostics.singleTapCount.collectAsState()
    val doubleCount by diagnostics.doubleTapCount.collectAsState()
    val tripleCount by diagnostics.tripleTapCount.collectAsState()
    val diagnosticLogs by diagnostics.diagnosticLogs.collectAsState()

    // Offline Replay State
    var isReplaying by remember { mutableStateOf(false) }
    var selectedDataset by remember { mutableStateOf(CsvReplayEngine.Dataset.DOUBLE_TAPS) }
    var benchmarkResults by remember { mutableStateOf<Map<CsvReplayEngine.Dataset, ReplayBenchmarkResult>>(emptyMap()) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(NordBackground)
    ) {
        // Top App Bar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "// TELEMETRY LAB",
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "CPH2707 • BOSCH BMI26x IMU @ 101 HZ",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = RetroNeonCyan
                    )
                }

                // Streaming / Hardware Indicator Badge
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isListening) RetroPhosphorGreen.copy(alpha = 0.15f) else Color(0xFF141926))
                        .border(1.dp, if (isListening) RetroPhosphorGreen.copy(alpha = 0.5f) else Color(0xFF222B3E), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (isListening) RetroPhosphorGreen else TextTertiary)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (isListening) "BMI26x LIVE" else "DAEMON IDLE",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = if (isListening) RetroPhosphorGreen else TextSecondary
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Tab Selector
            TabRow(
                selectedTabIndex = selectedTab.ordinal,
                containerColor = Color(0xFF0C0F18),
                contentColor = RetroNeonCyan,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[selectedTab.ordinal]),
                        color = RetroNeonCyan
                    )
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0xFF1D2638), RoundedCornerShape(10.dp))
            ) {
                DiagnosticTab.values().forEach { tab ->
                    val isSelected = selectedTab == tab
                    Tab(
                        selected = isSelected,
                        onClick = { selectedTab = tab },
                        text = {
                            Text(
                                text = tab.title,
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) RetroNeonCyan else TextSecondary
                            )
                        }
                    )
                }
            }
        }

        // Tab Content
        when (selectedTab) {
            DiagnosticTab.LIVE_STREAM -> {
                var currentCadenceMs by remember { mutableStateOf(detectionEngine.sequenceClassifier.maxTapIntervalMs) }
                var currentThreshold by remember { mutableStateOf(detectionEngine.impactDetector.baseThreshold) }

                LiveDiagnosticsView(
                    isListening = isListening,
                    onToggleListening = {
                        if (isListening) {
                            sensorController.stopListening()
                        } else {
                            sensorController.startListening()
                        }
                    },
                    sample = latestSample,
                    dynamicWaveform = dynamicWaveform,
                    recentImpacts = recentImpacts,
                    sequenceState = sequenceState,
                    totalSamples = totalSamples,
                    singleCount = singleCount,
                    doubleCount = doubleCount,
                    tripleCount = tripleCount,
                    currentCadenceMs = currentCadenceMs,
                    onUpdateCadence = { newCadence ->
                        currentCadenceMs = newCadence
                        detectionEngine.sequenceClassifier.maxTapIntervalMs = newCadence
                        Toast.makeText(context, "Sequence window set to ${newCadence}ms", Toast.LENGTH_SHORT).show()
                    },
                    currentThreshold = currentThreshold,
                    onUpdateThreshold = { newThreshold ->
                        currentThreshold = newThreshold
                        detectionEngine.impactDetector.updateThreshold(newThreshold)
                        Toast.makeText(context, "Base threshold set to ${newThreshold} m/s²", Toast.LENGTH_SHORT).show()
                    },
                    logs = diagnosticLogs,
                    onExportLogs = {
                        val text = diagnostics.exportLogsAsText()
                        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        clipboard.setPrimaryClip(ClipData.newPlainText("TapBack Logs", text))
                        Toast.makeText(context, "Logs copied to clipboard!", Toast.LENGTH_SHORT).show()
                    },
                    onResetStats = {
                        diagnostics.reset()
                    }
                )
            }

            DiagnosticTab.CSV_REPLAY -> {
                CsvReplayView(
                    context = context,
                    csvReplayEngine = csvReplayEngine,
                    selectedDataset = selectedDataset,
                    onSelectDataset = { selectedDataset = it },
                    isReplaying = isReplaying,
                    benchmarkResults = benchmarkResults,
                    onRunSingleReplay = { dataset ->
                        scope.launch {
                            isReplaying = true
                            try {
                                val result = withContext(Dispatchers.IO) {
                                    context.assets.open("sensor_data/${dataset.fileName}").use { stream ->
                                        csvReplayEngine.replay(stream, dataset)
                                    }
                                }
                                benchmarkResults = benchmarkResults + (dataset to result)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Replay failed: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isReplaying = false
                            }
                        }
                    },
                    onRunAllBenchmarks = {
                        scope.launch {
                            isReplaying = true
                            try {
                                val results = withContext(Dispatchers.IO) {
                                    val map = mutableMapOf<CsvReplayEngine.Dataset, ReplayBenchmarkResult>()
                                    for (ds in CsvReplayEngine.Dataset.values()) {
                                        context.assets.open("sensor_data/${ds.fileName}").use { stream ->
                                            map[ds] = csvReplayEngine.replay(stream, ds)
                                        }
                                    }
                                    map
                                }
                                benchmarkResults = results
                                Toast.makeText(context, "All 5 dataset benchmarks complete!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Replay error: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isReplaying = false
                            }
                        }
                    }
                )
            }
        }
    }
}

// =========================================================================
// SECTION 1: LIVE DIAGNOSTICS VIEW
// =========================================================================

@Composable
private fun LiveDiagnosticsView(
    isListening: Boolean,
    onToggleListening: () -> Unit,
    sample: ProcessedSensorSample?,
    dynamicWaveform: List<Float>,
    recentImpacts: List<ImpactEvent>,
    sequenceState: DetectionDiagnostics.SequenceDisplayState,
    totalSamples: Long,
    singleCount: Int,
    doubleCount: Int,
    tripleCount: Int,
    currentCadenceMs: Long,
    onUpdateCadence: (Long) -> Unit,
    currentThreshold: Float,
    onUpdateThreshold: (Float) -> Unit,
    logs: List<DetectionDiagnostics.DiagnosticLogEntry>,
    onExportLogs: () -> Unit,
    onResetStats: () -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Controls & Sequence State Card
        item {
            GestureStatusCard(
                sequenceState = sequenceState,
                isListening = isListening,
                onToggleListening = onToggleListening,
                singleCount = singleCount,
                doubleCount = doubleCount,
                tripleCount = tripleCount,
                currentCadenceMs = currentCadenceMs,
                onUpdateCadence = onUpdateCadence,
                currentThreshold = currentThreshold,
                onUpdateThreshold = onUpdateThreshold
            )
        }

        // Real-Time Dynamic Acceleration Graph
        item {
            DynamicWaveformGraphCard(
                waveform = dynamicWaveform,
                impactCount = recentImpacts.size,
                currentDynamicZ = sample?.dynamicAz ?: 0f
            )
        }

        // Live Telemetry Cards (Accelerometer & Gyroscope)
        item {
            LiveTelemetryCards(sample = sample, totalSamples = totalSamples)
        }

        // Recent Physical Impacts
        item {
            RecentImpactsCard(impacts = recentImpacts)
        }

        // Diagnostic Logs Section
        item {
            DiagnosticLogsCard(
                logs = logs,
                onExportLogs = onExportLogs,
                onResetStats = onResetStats
            )
        }

        item {
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun GestureStatusCard(
    sequenceState: DetectionDiagnostics.SequenceDisplayState,
    isListening: Boolean,
    onToggleListening: () -> Unit,
    singleCount: Int,
    doubleCount: Int,
    tripleCount: Int,
    currentCadenceMs: Long,
    onUpdateCadence: (Long) -> Unit,
    currentThreshold: Float,
    onUpdateThreshold: (Float) -> Unit
) {
    val statusColor by animateColorAsState(
        targetValue = when (sequenceState.activeGesture) {
            GestureType.SINGLE_TAP -> ElectricBlue
            GestureType.DOUBLE_TAP -> SuccessGreen
            GestureType.TRIPLE_TAP -> OnePlusRed
            null -> if (isListening) (if (sequenceState.inProgress) WarningOrange else CyanAccent) else TextTertiary
        },
        label = "statusColor"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NordCard)
            .border(1.dp, NordCardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "SEQUENCE CLASSIFICATION",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Text(
                    text = sequenceState.statusText,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = statusColor
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onToggleListening,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isListening) Color(0xFF331118) else OnePlusRed,
                    contentColor = if (isListening) OnePlusRed else Color.White
                ),
                shape = RoundedCornerShape(10.dp)
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = if (isListening) "STOP" else "START",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // Active Tap Timeout Countdown Bar
        if (sequenceState.inProgress && sequenceState.windowRemainingMs > 0L) {
            Spacer(modifier = Modifier.height(8.dp))
            val progress = (sequenceState.windowRemainingMs.toFloat() / currentCadenceMs.toFloat()).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = WarningOrange,
                trackColor = Color(0xFF282D3A)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Gesture Counters Pill Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CounterPill(modifier = Modifier.weight(1f), label = "Singles", count = singleCount, color = ElectricBlue)
            CounterPill(modifier = Modifier.weight(1f), label = "Doubles", count = doubleCount, color = SuccessGreen)
            CounterPill(modifier = Modifier.weight(1f), label = "Triples", count = tripleCount, color = OnePlusRed)
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Cadence Timing Window Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("CADENCE TIMING", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CadencePill(label = "Fast (350ms)", cadenceMs = 350L, selectedCadenceMs = currentCadenceMs, onSelect = onUpdateCadence)
                CadencePill(label = "Standard (480ms)", cadenceMs = 480L, selectedCadenceMs = currentCadenceMs, onSelect = onUpdateCadence)
                CadencePill(label = "Relaxed (580ms)", cadenceMs = 580L, selectedCadenceMs = currentCadenceMs, onSelect = onUpdateCadence)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Sensitivity Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("TAP SENSITIVITY", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                SensitivityPill(label = "Gentle (2.4)", threshold = 2.40f, selectedThreshold = currentThreshold, onSelect = onUpdateThreshold)
                SensitivityPill(label = "Medium (2.8)", threshold = 2.80f, selectedThreshold = currentThreshold, onSelect = onUpdateThreshold)
                SensitivityPill(label = "Firm (3.6)", threshold = 3.60f, selectedThreshold = currentThreshold, onSelect = onUpdateThreshold)
            }
        }
    }
}

@Composable
private fun SensitivityPill(
    label: String,
    threshold: Float,
    selectedThreshold: Float,
    onSelect: (Float) -> Unit
) {
    val isSelected = abs(selectedThreshold - threshold) < 0.05f
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) CyanAccent.copy(alpha = 0.2f) else NordBackground)
            .border(1.dp, if (isSelected) CyanAccent else NordCardBorder, RoundedCornerShape(6.dp))
            .clickable { onSelect(threshold) }
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) CyanAccent else TextSecondary
        )
    }
}

@Composable
private fun CadencePill(
    label: String,
    cadenceMs: Long,
    selectedCadenceMs: Long,
    onSelect: (Long) -> Unit
) {
    val isSelected = selectedCadenceMs == cadenceMs
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(if (isSelected) OnePlusRed.copy(alpha = 0.2f) else NordBackground)
            .border(1.dp, if (isSelected) OnePlusRed else NordCardBorder, RoundedCornerShape(6.dp))
            .clickable { onSelect(cadenceMs) }
            .padding(horizontal = 6.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 9.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
            color = if (isSelected) OnePlusRed else TextSecondary
        )
    }
}

@Composable
private fun CounterPill(
    modifier: Modifier = Modifier,
    label: String,
    count: Int,
    color: Color
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(NordBackground)
            .border(1.dp, NordCardBorder, RoundedCornerShape(10.dp))
            .padding(vertical = 8.dp, horizontal = 10.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = count.toString(),
                fontSize = 18.sp,
                fontWeight = FontWeight.ExtraBold,
                color = color
            )
            Text(
                text = label,
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = TextSecondary
            )
        }
    }
}

@Composable
private fun DynamicWaveformGraphCard(
    waveform: List<Float>,
    impactCount: Int,
    currentDynamicZ: Float
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NordCard)
            .border(1.dp, NordCardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "DYNAMIC Z ACCELERATION (Δaz)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = TextSecondary
                )
                Text(
                    text = "${"%.2f".format(currentDynamicZ)} m/s²",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.ExtraBold,
                    fontFamily = FontFamily.Monospace,
                    color = if (abs(currentDynamicZ) >= 3.0f) OnePlusRed else CyanAccent
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF1E222D))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "Threshold: ±3.0 m/s²",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                    color = TextSecondary
                )
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Dynamic Waveform Canvas
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(NordBackground)
                .border(1.dp, NordCardBorder, RoundedCornerShape(10.dp))
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val midY = h / 2f
                val maxRange = 10.0f // ±10 m/s^2 scale

                // Zero line
                drawLine(
                    color = Color(0xFF282D3A),
                    start = Offset(0f, midY),
                    end = Offset(w, midY),
                    strokeWidth = 1.dp.toPx()
                )

                // Upper threshold line (+3.0 m/s^2)
                val threshUpperY = midY - (3.0f / maxRange) * (h / 2f)
                drawLine(
                    color = OnePlusRed.copy(alpha = 0.35f),
                    start = Offset(0f, threshUpperY),
                    end = Offset(w, threshUpperY),
                    strokeWidth = 1.dp.toPx()
                )

                // Lower threshold line (-3.0 m/s^2)
                val threshLowerY = midY + (3.0f / maxRange) * (h / 2f)
                drawLine(
                    color = OnePlusRed.copy(alpha = 0.35f),
                    start = Offset(0f, threshLowerY),
                    end = Offset(w, threshLowerY),
                    strokeWidth = 1.dp.toPx()
                )

                if (waveform.size >= 2) {
                    val stepX = w / (waveform.size - 1)
                    val path = Path()

                    waveform.forEachIndexed { i, valZ ->
                        val clampedVal = valZ.coerceIn(-maxRange, maxRange)
                        val x = i * stepX
                        val y = midY - (clampedVal / maxRange) * (h / 2f)

                        if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)

                        // Highlight spikes crossing threshold with impact markers
                        if (valZ >= 3.0f) {
                            drawCircle(
                                color = OnePlusRed,
                                radius = 3.dp.toPx(),
                                center = Offset(x, y)
                            )
                        }
                    }

                    drawPath(
                        path = path,
                        color = CyanAccent,
                        style = Stroke(width = 1.8.dp.toPx())
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTelemetryCards(
    sample: ProcessedSensorSample?,
    totalSamples: Long
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Accelerometer
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(NordCard)
                .border(1.dp, NordCardBorder, RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("ACCELEROMETER", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Text("m/s²", fontSize = 10.sp, color = TextTertiary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            TelemetryRow("ax", sample?.ax ?: 0f, TextPrimary)
            TelemetryRow("ay", sample?.ay ?: 0f, TextPrimary)
            TelemetryRow("az", sample?.az ?: 9.8f, CyanAccent)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("||a||", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Text(
                    "${"%.2f".format(sample?.totalMagnitude ?: 9.8f)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = TextPrimary
                )
            }
        }

        // Gyroscope
        Column(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(14.dp))
                .background(NordCard)
                .border(1.dp, NordCardBorder, RoundedCornerShape(14.dp))
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("GYROSCOPE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Text("rad/s", fontSize = 10.sp, color = TextTertiary)
            }
            Spacer(modifier = Modifier.height(6.dp))
            TelemetryRow("gx", sample?.gx ?: 0f, TextPrimary)
            TelemetryRow("gy", sample?.gy ?: 0f, TextPrimary)
            TelemetryRow("gz", sample?.gz ?: 0f, ElectricBlue)
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("||ω||", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                Text(
                    "${"%.2f".format(sample?.gyroMagnitude ?: 0f)}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    color = if ((sample?.gyroMagnitude ?: 0f) > 1.25f) WarningOrange else TextPrimary
                )
            }
        }
    }
}

@Composable
private fun TelemetryRow(label: String, value: Float, color: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, fontSize = 11.sp, color = TextSecondary)
        Text(
            text = "${if (value >= 0) "+" else ""}${"%.2f".format(value)}",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = color
        )
    }
}

@Composable
private fun RecentImpactsCard(impacts: List<ImpactEvent>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NordCard)
            .border(1.dp, NordCardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("RECENT CONFIRMED IMPACTS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Text("${impacts.size} events", fontSize = 11.sp, color = CyanAccent)
        }

        Spacer(modifier = Modifier.height(8.dp))

        if (impacts.isEmpty()) {
            Text(
                text = "No impact peaks detected yet. Tap firmly on the back of the phone.",
                fontSize = 12.sp,
                color = TextTertiary,
                modifier = Modifier.padding(vertical = 8.dp)
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                impacts.take(4).forEach { impact ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(NordBackground)
                            .border(1.dp, NordCardBorder, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Strength: ${"%.2f".format(impact.dynamicZStrength)} m/s²",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = TextPrimary
                            )
                            Text(
                                text = "Gyro: ${"%.2f".format(impact.gyroActivity)} rad/s • Z-ratio: ${"%.1f".format(impact.zDominanceRatio)}x",
                                fontSize = 10.sp,
                                color = TextSecondary
                            )
                        }

                        Text(
                            text = "${"%.0f".format(impact.confidence * 100)}% conf",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuccessGreen
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DiagnosticLogsCard(
    logs: List<DetectionDiagnostics.DiagnosticLogEntry>,
    onExportLogs: () -> Unit,
    onResetStats: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NordCard)
            .border(1.dp, NordCardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("DIAGNOSTIC LOGS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = onExportLogs,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy", fontSize = 11.sp)
                }

                OutlinedButton(
                    onClick = onResetStats,
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 2.dp)
                ) {
                    Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(14.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", fontSize = 11.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(110.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(NordBackground)
                .border(1.dp, NordCardBorder, RoundedCornerShape(8.dp))
                .padding(8.dp)
        ) {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                items(logs) { entry ->
                    Text(
                        text = "[${entry.formattedTime}] [${entry.tag}] ${entry.message}",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = when (entry.tag) {
                            "GESTURE" -> SuccessGreen
                            "IMPACT" -> CyanAccent
                            else -> TextSecondary
                        }
                    )
                }
            }
        }
    }
}

// =========================================================================
// SECTION 2: OFFLINE CSV REPLAY & BENCHMARKS
// =========================================================================

@Composable
private fun CsvReplayView(
    context: Context,
    csvReplayEngine: CsvReplayEngine,
    selectedDataset: CsvReplayEngine.Dataset,
    onSelectDataset: (CsvReplayEngine.Dataset) -> Unit,
    isReplaying: Boolean,
    benchmarkResults: Map<CsvReplayEngine.Dataset, ReplayBenchmarkResult>,
    onRunSingleReplay: (CsvReplayEngine.Dataset) -> Unit,
    onRunAllBenchmarks: () -> Unit
) {
    val activeResult = benchmarkResults[selectedDataset]

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Benchmark Suite Header & Global Run Action
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NordCard)
                    .border(1.dp, NordCardBorder, RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "OFFLINE CSV REPLAY ENGINE",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = TextPrimary
                        )
                        Text(
                            text = "5 Real Recorded Datasets from OnePlus Nord 5 (BMI26x)",
                            fontSize = 11.sp,
                            color = TextSecondary
                        )
                    }

                    Button(
                        onClick = onRunAllBenchmarks,
                        enabled = !isReplaying,
                        colors = ButtonDefaults.buttonColors(containerColor = OnePlusRed),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        if (isReplaying) {
                            CircularProgressIndicator(color = Color.White, modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(imageVector = Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("RUN ALL", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // Dataset Carousel / Selector
        item {
            Text(
                text = "SELECT RECORDED TEST FIXTURE",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = TextSecondary,
                modifier = Modifier.padding(start = 2.dp, bottom = 4.dp)
            )

            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                CsvReplayEngine.Dataset.values().forEach { dataset ->
                    val isSelected = selectedDataset == dataset
                    val result = benchmarkResults[dataset]

                    DatasetCard(
                        dataset = dataset,
                        isSelected = isSelected,
                        result = result,
                        onSelect = { onSelectDataset(dataset) },
                        onRun = { onRunSingleReplay(dataset) },
                        isReplaying = isReplaying
                    )
                }
            }
        }

        // Active Replay Detailed Benchmark Results
        item {
            if (activeResult != null) {
                DetailedBenchmarkCard(result = activeResult)
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(NordCard)
                        .border(1.dp, NordCardBorder, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(imageVector = Icons.Default.FolderOpen, contentDescription = null, tint = TextTertiary, modifier = Modifier.size(36.dp))
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("Select a dataset and tap 'REPLAY' to execute offline detection.", fontSize = 12.sp, color = TextSecondary)
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun DatasetCard(
    dataset: CsvReplayEngine.Dataset,
    isSelected: Boolean,
    result: ReplayBenchmarkResult?,
    onSelect: () -> Unit,
    onRun: () -> Unit,
    isReplaying: Boolean
) {
    val borderColor = if (isSelected) OnePlusRed else NordCardBorder

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(if (isSelected) Color(0xFF1E2330) else NordCard)
            .border(1.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onSelect() }
            .padding(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = dataset.displayName,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.width(8.dp))

                    // Validation Type Tag (Distinctly tag Tuning vs Held-out Rejection)
                    val tagBg = if (dataset.isTuningDataset) Color(0xFF2E1B4E) else Color(0xFF0F3642)
                    val tagText = if (dataset.isTuningDataset) Color(0xFFC084FC) else CyanAccent
                    val tagLabel = if (dataset.isTuningDataset) "TUNING FIXTURE" else "HELD-OUT VALIDATION"

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(tagBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = tagLabel,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = tagText
                        )
                    }
                }

                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = dataset.description,
                    fontSize = 10.sp,
                    color = TextSecondary
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            OutlinedButton(
                onClick = onRun,
                enabled = !isReplaying,
                shape = RoundedCornerShape(8.dp),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 10.dp, vertical = 2.dp)
            ) {
                Text("REPLAY", fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }

        // Result Pill Badge if benchmark already executed
        if (result != null) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(NordBackground)
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (result.isAccuracyPerfect) Icons.Default.CheckCircle else Icons.Default.Error,
                        contentDescription = null,
                        tint = if (result.isAccuracyPerfect) SuccessGreen else WarningOrange,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (result.isAccuracyPerfect) "100% Match Ground Truth" else "FP: ${result.falsePositives}, Missed: ${result.missedEvents}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (result.isAccuracyPerfect) SuccessGreen else WarningOrange
                    )
                }

                Text(
                    text = "${result.totalSamples} samples • ${"%.1f".format(result.samplingFrequencyHz)} Hz",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
            }
        }
    }
}

@Composable
private fun DetailedBenchmarkCard(result: ReplayBenchmarkResult) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(NordCard)
            .border(1.dp, NordCardBorder, RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "BENCHMARK REPORT: ${result.datasetName.uppercase()}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextPrimary
                )
                Text(
                    text = if (result.isTuningDataset) "Dataset Role: Gesture Tuning / Calibration" else "Dataset Role: Held-Out Independent Rejection Validation",
                    fontSize = 10.sp,
                    color = if (result.isTuningDataset) Color(0xFFC084FC) else CyanAccent
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(if (result.isAccuracyPerfect) SuccessGreen.copy(alpha = 0.2f) else WarningOrange.copy(alpha = 0.2f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = if (result.isAccuracyPerfect) "PERFECT ACCURACY" else "EVALUATED",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.ExtraBold,
                    color = if (result.isAccuracyPerfect) SuccessGreen else WarningOrange
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Metrics Grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricBox(modifier = Modifier.weight(1f), label = "Duration", value = "${"%.2f".format(result.durationSeconds)}s")
            MetricBox(modifier = Modifier.weight(1f), label = "Samples", value = "${result.totalSamples}")
            MetricBox(modifier = Modifier.weight(1f), label = "Frequency", value = "${"%.1f".format(result.samplingFrequencyHz)} Hz")
        }

        Spacer(modifier = Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            MetricBox(modifier = Modifier.weight(1f), label = "Impacts", value = "${result.detectedImpactCount}")
            MetricBox(modifier = Modifier.weight(1f), label = "False Positives", value = "${result.falsePositives}", color = if (result.falsePositives == 0) SuccessGreen else OnePlusRed)
            MetricBox(modifier = Modifier.weight(1f), label = "Missed", value = "${result.missedEvents}", color = if (result.missedEvents == 0) SuccessGreen else OnePlusRed)
            MetricBox(modifier = Modifier.weight(1f), label = "Confidence", value = "${"%.0f".format(result.averageConfidence * 100)}%", color = SuccessGreen)
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Sequence Counts vs Ground Truth
        Text("CLASSIFIED SEQUENCES VS GROUND TRUTH", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
        Spacer(modifier = Modifier.height(6.dp))

        SequenceComparisonRow("Single Taps", actual = result.classifiedSingles, expected = result.expectedSingles)
        SequenceComparisonRow("Double Taps", actual = result.classifiedDoubles, expected = result.expectedDoubles)
        SequenceComparisonRow("Triple Taps", actual = result.classifiedTriples, expected = result.expectedTriples)

        // Event Timeline
        if (result.gestures.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))
            Text("CLASSIFIED GESTURE TIMELINE", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
            Spacer(modifier = Modifier.height(6.dp))

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                result.gestures.forEachIndexed { idx, g ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(NordBackground)
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "#${idx + 1} ${g.gestureType.displayName}",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = when (g.gestureType) {
                                GestureType.SINGLE_TAP -> ElectricBlue
                                GestureType.DOUBLE_TAP -> SuccessGreen
                                GestureType.TRIPLE_TAP -> OnePlusRed
                            }
                        )

                        val intervals = if (g.interTapIntervalsMs.isNotEmpty()) {
                            "dt: [${g.interTapIntervalsMs.joinToString(", ")}ms]"
                        } else ""

                        Text(
                            text = "$intervals • ${"%.0f".format(g.confidence * 100)}% conf",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            color = TextSecondary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    color: Color = TextPrimary
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(NordBackground)
            .border(1.dp, NordCardBorder, RoundedCornerShape(8.dp))
            .padding(vertical = 6.dp, horizontal = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = value, fontSize = 13.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = color)
            Text(text = label, fontSize = 9.sp, color = TextSecondary)
        }
    }
}

@Composable
private fun SequenceComparisonRow(label: String, actual: Int, expected: Int) {
    val matches = actual == expected
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 11.sp, color = TextSecondary)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Actual: $actual",
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = if (matches) SuccessGreen else OnePlusRed
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "(Expected: $expected)",
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextTertiary
            )
        }
    }
}

