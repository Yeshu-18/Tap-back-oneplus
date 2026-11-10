package com.tapback.app.ui

import android.widget.Toast
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
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Sensors
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import com.tapback.app.core.sensor.AccSample
import com.tapback.app.core.sensor.DiagnosticSensorManager
import com.tapback.app.ui.theme.CyanAccent
import com.tapback.app.ui.theme.ElectricBlue
import com.tapback.app.ui.theme.NordBackground
import com.tapback.app.ui.theme.NordCard
import com.tapback.app.ui.theme.NordCardBorder
import com.tapback.app.ui.theme.OnePlusRed
import com.tapback.app.ui.theme.SuccessGreen
import com.tapback.app.ui.theme.TextPrimary
import com.tapback.app.ui.theme.TextSecondary
import com.tapback.app.ui.theme.TextTertiary
import com.tapback.app.ui.theme.WarningOrange

@Composable
fun Phase1DiagnosticScreen(sensorManager: DiagnosticSensorManager) {
    val context = LocalContext.current
    val isMonitoring by sensorManager.isMonitoring.collectAsState()
    val isRecording by sensorManager.isRecording.collectAsState()
    val acc by sensorManager.latestAcc.collectAsState()
    val gyro by sensorManager.latestGyro.collectAsState()
    val recentEvents by sensorManager.recentEvents.collectAsState()
    val waveform by sensorManager.magnitudeWaveform.collectAsState()
    val stats by sensorManager.sessionStats.collectAsState()

    var exportedPath by remember { mutableStateOf<String?>(null) }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NordBackground)
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // App Header
        item {
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TapBack Diagnostics",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = TextPrimary
                    )
                    Text(
                        text = "OnePlus Nord 5 • Physical Sensor Telemetry",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = OnePlusRed
                    )
                }
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(if (isMonitoring) OnePlusRed.copy(alpha = 0.2f) else Color(0xFF222734)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = null,
                        tint = if (isMonitoring) OnePlusRed else TextSecondary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        // Hardware Registration & Sensor Status Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NordCard)
                    .border(1.dp, NordCardBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Hardware Sensors",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(if (isMonitoring) SuccessGreen.copy(alpha = 0.15f) else Color(0xFF252A36))
                            .padding(horizontal = 8.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isMonitoring) "ACTIVE • 10ms (~100 Hz)" else "IDLE",
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isMonitoring) SuccessGreen else TextSecondary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                SensorInfoRow(
                    type = "Accelerometer",
                    name = sensorManager.accelerometerInfo?.name ?: "Bosch BMI26x Accelerometer",
                    vendor = sensorManager.accelerometerInfo?.vendor ?: "BOSCH",
                    maxRate = "400 Hz max (BMI26x)"
                )

                Spacer(modifier = Modifier.height(4.dp))

                SensorInfoRow(
                    type = "Gyroscope",
                    name = sensorManager.gyroscopeInfo?.name ?: "Bosch BMI26x Gyroscope",
                    vendor = sensorManager.gyroscopeInfo?.vendor ?: "BOSCH",
                    maxRate = "400 Hz max (BMI26x)"
                )
            }
        }

        // Start / Stop / Reset / Record Controls
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (!isMonitoring) {
                    Button(
                        onClick = { sensorManager.start() },
                        colors = ButtonDefaults.buttonColors(containerColor = OnePlusRed),
                        shape = RoundedCornerShape(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Start", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                    }
                } else {
                    Button(
                        onClick = { sensorManager.stop() },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE53935)),
                        shape = RoundedCornerShape(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Stop", fontWeight = FontWeight.Bold, fontSize = 11.sp, maxLines = 1)
                    }
                }

                OutlinedButton(
                    onClick = { sensorManager.reset() },
                    shape = RoundedCornerShape(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = TextPrimary),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Reset", fontSize = 11.sp, maxLines = 1)
                }

                Button(
                    onClick = {
                        if (!isRecording) {
                            sensorManager.startRecording()
                            Toast.makeText(context, "Recording started...", Toast.LENGTH_SHORT).show()
                        } else {
                            val file = sensorManager.stopRecording()
                            if (file != null) {
                                exportedPath = file.absolutePath
                                Toast.makeText(context, "Log saved to: ${file.name}", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isRecording) Color(0xFFFF9800) else Color(0xFF262E3D)
                    ),
                    shape = RoundedCornerShape(12.dp), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 4.dp, vertical = 8.dp),
                    modifier = Modifier.weight(1.3f)
                ) {
                    Icon(
                        Icons.Default.FiberManualRecord,
                        contentDescription = null,
                        tint = if (isRecording) Color.Red else CyanAccent,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (isRecording) "Stop Rec" else "Record CSV",
                        color = if (isRecording) Color.White else CyanAccent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                }
            }

            exportedPath?.let { path ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Saved: $path",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = CyanAccent
                )
            }
        }

        // Live Telemetry Stats Bar
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF131722))
                    .padding(vertical = 10.dp, horizontal = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(text = "Sampling Rate", fontSize = 10.sp, color = TextSecondary)
                    Text(
                        text = "${"%.1f".format(stats.currentHz)} Hz",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = if (stats.currentHz in 80f..120f) SuccessGreen else WarningOrange
                    )
                }
                Column {
                    Text(text = "Total Samples", fontSize = 10.sp, color = TextSecondary)
                    Text(
                        text = "${stats.sampleCount}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = TextPrimary
                    )
                }
                Column {
                    Text(text = "Min / Max |A|", fontSize = 10.sp, color = TextSecondary)
                    Text(
                        text = "${"%.1f".format(stats.minMagnitude)} / ${"%.1f".format(stats.maxMagnitude)}",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        color = ElectricBlue
                    )
                }
                Column {
                    Text(text = "Peak Δ", fontSize = 10.sp, color = TextSecondary)
                    Text(
                        text = "${"%.1f".format(stats.peakToPeak)}",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                        color = OnePlusRed
                    )
                }
            }
        }

        // Live Readout Cards: Accelerometer & Gyroscope
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Accelerometer Card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NordCard)
                        .border(1.dp, NordCardBorder, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Accelerometer (m/s²)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AxisReadout(axis = "X", value = acc.ax, color = ElectricBlue)
                    AxisReadout(axis = "Y", value = acc.ay, color = SuccessGreen)
                    AxisReadout(axis = "Z", value = acc.az, color = OnePlusRed)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Norm |A|:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Text(
                            text = "${"%.2f".format(acc.magnitude)} m/s²",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                    }
                }

                // Gyroscope Card
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(16.dp))
                        .background(NordCard)
                        .border(1.dp, NordCardBorder, RoundedCornerShape(16.dp))
                        .padding(12.dp)
                ) {
                    Text(
                        text = "Gyroscope (rad/s)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    AxisReadout(axis = "ωX", value = gyro.gx, color = ElectricBlue)
                    AxisReadout(axis = "ωY", value = gyro.gy, color = SuccessGreen)
                    AxisReadout(axis = "ωZ", value = gyro.gz, color = OnePlusRed)
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(text = "Norm |ω|:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextSecondary)
                        Text(
                            text = "${"%.2f".format(gyro.magnitude)} rad/s",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.Monospace,
                            color = TextPrimary
                        )
                    }
                }
            }
        }

        // Live Acceleration Magnitude Graph
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(NordCard)
                    .border(1.dp, NordCardBorder, RoundedCornerShape(16.dp))
                    .padding(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Live Acceleration Magnitude Waveform",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "1g Baseline (9.8 m/s²)",
                        fontSize = 10.sp,
                        color = SuccessGreen
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF0A0C10))
                        .border(1.dp, Color(0xFF1B202C), RoundedCornerShape(10.dp))
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val canvasWidth = size.width
                        val canvasHeight = size.height
                        val maxDisplay = 35f

                        // 9.8 m/s² Baseline Guide Line
                        val baselineY = canvasHeight - (9.8f / maxDisplay) * canvasHeight
                        drawLine(
                            color = SuccessGreen.copy(alpha = 0.45f),
                            start = Offset(0f, baselineY),
                            end = Offset(canvasWidth, baselineY),
                            strokeWidth = 1.dp.toPx()
                        )

                        // 20 m/s² Tap Impact Level Line
                        val tapLevelY = canvasHeight - (20.0f / maxDisplay) * canvasHeight
                        drawLine(
                            color = OnePlusRed.copy(alpha = 0.35f),
                            start = Offset(0f, tapLevelY),
                            end = Offset(canvasWidth, tapLevelY),
                            strokeWidth = 1.dp.toPx()
                        )

                        // Draw live waveform
                        if (waveform.size > 1) {
                            val step = canvasWidth / 80f
                            val path = Path()
                            for (i in waveform.indices) {
                                val x = i * step
                                val mag = waveform[i].coerceIn(0f, maxDisplay)
                                val y = canvasHeight - (mag / maxDisplay) * canvasHeight
                                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                            }
                            drawPath(
                                path = path,
                                color = CyanAccent,
                                style = Stroke(width = 2.dp.toPx())
                            )
                        }
                    }
                }
            }
        }

        // Scrolling Sensor Event History Table Header
        item {
            Text(
                text = "Real-Time Sensor Event History (Latest 50 Samples)",
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
        }

        // Table Header
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF161A24))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "Index", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.weight(0.8f))
                Text(text = "Δt (ms)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextSecondary, modifier = Modifier.weight(0.8f))
                Text(text = "Ax", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = ElectricBlue, modifier = Modifier.weight(0.8f))
                Text(text = "Ay", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = SuccessGreen, modifier = Modifier.weight(0.8f))
                Text(text = "Az", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = OnePlusRed, modifier = Modifier.weight(0.8f))
                Text(text = "|A| (m/s²)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = TextPrimary, modifier = Modifier.weight(1.0f))
            }
        }

        // Table Rows
        items(recentEvents.take(30)) { event ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(Color(0xFF0F1218))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(text = "#${event.index}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextTertiary, modifier = Modifier.weight(0.8f))
                Text(text = "${"%.1f".format(event.deltaMs)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = TextSecondary, modifier = Modifier.weight(0.8f))
                Text(text = "${"%.2f".format(event.ax)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = ElectricBlue, modifier = Modifier.weight(0.8f))
                Text(text = "${"%.2f".format(event.ay)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = SuccessGreen, modifier = Modifier.weight(0.8f))
                Text(text = "${"%.2f".format(event.az)}", fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = OnePlusRed, modifier = Modifier.weight(0.8f))
                Text(text = "${"%.2f".format(event.magnitude)}", fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Monospace, color = TextPrimary, modifier = Modifier.weight(1.0f))
            }
        }

        item { Spacer(modifier = Modifier.height(20.dp)) }
    }
}

@Composable
private fun SensorInfoRow(type: String, name: String, vendor: String, maxRate: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF131720))
            .padding(horizontal = 10.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = type, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = TextPrimary)
            Text(text = "$name ($vendor)", fontSize = 10.sp, color = TextSecondary)
        }
        Text(text = maxRate, fontSize = 11.sp, fontFamily = FontFamily.Monospace, color = CyanAccent)
    }
}

@Composable
private fun AxisReadout(axis: String, value: Float, color: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = "$axis:", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = color)
        Text(
            text = "${if (value >= 0) " " else ""}${"%.2f".format(value)}",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = TextPrimary
        )
    }
}
