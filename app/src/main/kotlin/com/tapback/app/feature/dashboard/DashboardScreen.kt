package com.tapback.app.feature.dashboard

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
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.BatteryAlert
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapback.app.TapBackApplication
import com.tapback.app.core.permissions.PermissionHelper
import com.tapback.app.domain.models.TapBackSettings
import com.tapback.app.feature.gestures.GestureActionCard
import com.tapback.app.service.BackTapService
import com.tapback.app.service.TapBackAccessibilityService
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
import com.tapback.app.ui.theme.WarningOrange
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DashboardScreen(
    app: TapBackApplication,
    onNavigateToGestures: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.container.preferencesManager.settingsFlow.collectAsState(initial = TapBackSettings())
    val isServiceRunning by BackTapService.isRunning.collectAsState()
    val isSensorListening by app.container.sensorController.isListening.collectAsState()
    val recentEvents by BackTapService.recentEvents.collectAsState()

    var isAccessibilityActive by remember { mutableStateOf(PermissionHelper.isAccessibilityEnabled(context)) }
    var isIgnoringBatteryOpt by remember { mutableStateOf(PermissionHelper.isIgnoringBatteryOptimizations(context)) }
    var visualizerTriggerCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        app.container.detectionEngine.gestureEvents.collect {
            visualizerTriggerCount++
        }
    }

    // Refresh permission statuses when returning
    LaunchedEffect(isServiceRunning) {
        isAccessibilityActive = PermissionHelper.isAccessibilityEnabled(context)
        isIgnoringBatteryOpt = PermissionHelper.isIgnoringBatteryOptimizations(context)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(NordBackground)
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "TAPBACK",
                        fontSize = 24.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 2.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "SYS.ID: CPH2707 • BOSCH BMI26x @ 101 HZ",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        color = RetroNeonCyan
                    )
                }

                // Master Toggle Switch
                Switch(
                    checked = settings.isEnabled,
                    onCheckedChange = { enabled ->
                        scope.launch {
                            app.container.preferencesManager.setEnabled(enabled)
                            if (enabled) {
                                BackTapService.startService(context)
                            } else {
                                BackTapService.stopService(context)
                            }
                        }
                    },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = RetroNeonCyan,
                        uncheckedThumbColor = TextSecondary,
                        uncheckedTrackColor = Color(0xFF1E2638)
                    )
                )
            }
        }

        // Animated Retro Wireframe Phone Visualizer
        item {
            PhoneIllustration(
                isListening = settings.isEnabled && isSensorListening,
                tapTriggerCount = visualizerTriggerCount
            )
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0D121F))
                    .border(
                        1.dp,
                        if (settings.isEnabled && isServiceRunning) RetroPhosphorGreen.copy(alpha = 0.4f) else Color(0xFF202A3C),
                        RoundedCornerShape(8.dp)
                    )
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (settings.isEnabled && isServiceRunning) RetroPhosphorGreen else RetroPhosphorAmber)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (settings.isEnabled && isServiceRunning) "SYSTEM // ARMED [DAEMON RUNNING]" else "SYSTEM // STANDBY [OFFLINE]",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = if (settings.isEnabled && isServiceRunning) RetroPhosphorGreen else RetroPhosphorAmber
                )
            }
        }

        // System Health & Privileges Badges (Retro HUD style)
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Accessibility Service Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(74.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F1320))
                        .border(
                            1.dp,
                            if (!isAccessibilityActive) RetroPhosphorAmber.copy(alpha = 0.6f) else Color(0xFF1E2638),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { PermissionHelper.openAccessibilitySettings(context) }
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isAccessibilityActive) Icons.Default.CheckCircle else Icons.Default.Accessibility,
                                contentDescription = null,
                                tint = if (isAccessibilityActive) RetroPhosphorGreen else RetroPhosphorAmber,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isAccessibilityActive) "[ A11Y // BOUND ]" else "[ A11Y // ACTION ]",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                color = if (isAccessibilityActive) RetroPhosphorGreen else RetroPhosphorAmber
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isAccessibilityActive) "Root Actions Online" else "Tap to Grant Binding",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            color = TextSecondary
                        )
                    }
                }

                // Battery Optimization Card
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(74.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(Color(0xFF0F1320))
                        .border(
                            1.dp,
                            if (!isIgnoringBatteryOpt) RetroPhosphorAmber.copy(alpha = 0.6f) else Color(0xFF1E2638),
                            RoundedCornerShape(14.dp)
                        )
                        .clickable { PermissionHelper.openOnePlusBatterySettings(context) }
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.Center) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = if (isIgnoringBatteryOpt) Icons.Default.CheckCircle else Icons.Default.BatteryAlert,
                                contentDescription = null,
                                tint = if (isIgnoringBatteryOpt) RetroPhosphorGreen else RetroPhosphorAmber,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = if (isIgnoringBatteryOpt) "[ PWR // NO_OPT ]" else "[ PWR // LIMIT ]",
                                fontSize = 11.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                color = if (isIgnoringBatteryOpt) RetroPhosphorGreen else RetroPhosphorAmber
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = if (isIgnoringBatteryOpt) "Unrestricted OS" else "Tap to Exclude",
                            fontSize = 10.sp,
                            fontFamily = FontFamily.Monospace,
                            maxLines = 1,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Quick Gestures Setup Overview
        item {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "// GESTURE MAPPINGS",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = RetroNeonCyan
                    )
                    Text(
                        text = "[ CONFIGURE ]",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = RetroNeonPink,
                        modifier = Modifier.clickable(onClick = onNavigateToGestures)
                    )
                }

                GestureActionCard(
                    title = "SINGLE TAP",
                    subtitle = "Strike rear chassis once (optional)",
                    action = settings.singleTapAction,
                    targetPackage = settings.singleTapPackage,
                    onClick = onNavigateToGestures
                )

                GestureActionCard(
                    title = "DOUBLE TAP",
                    subtitle = "Strike rear chassis twice (160–420 ms cadence)",
                    action = settings.doubleTapAction,
                    targetPackage = settings.doubleTapPackage,
                    onClick = onNavigateToGestures
                )

                GestureActionCard(
                    title = "TRIPLE TAP",
                    subtitle = "Strike rear chassis 3× (adaptive in-sequence force)",
                    action = settings.tripleTapAction,
                    targetPackage = settings.tripleTapPackage,
                    onClick = onNavigateToGestures
                )
            }
        }

        // Live Event Ticker (Retro Telemetry HUD)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0B0E16))
                    .border(1.dp, Color(0xFF1C2436), RoundedCornerShape(16.dp))
                    .padding(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "// TELEMETRY LOG",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.sp,
                        color = RetroPhosphorGreen
                    )
                    Text(
                        text = "LIVE FEED",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = TextTertiary
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))

                if (recentEvents.isEmpty()) {
                    Text(
                        text = "Awaiting physical chassis strikes. Events will stream here in real time.",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextTertiary
                    )
                } else {
                    val sdf = remember { SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()) }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        recentEvents.take(4).forEach { event ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF101522))
                                    .border(1.dp, Color(0xFF1E283C), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(6.dp)
                                            .clip(CircleShape)
                                            .background(RetroPhosphorGreen)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column {
                                        Text(
                                            text = "${event.gestureType.name} → ${event.actionExecuted.title.uppercase()}",
                                            fontSize = 11.sp,
                                            fontFamily = FontFamily.Monospace,
                                            fontWeight = FontWeight.Bold,
                                            color = RetroNeonCyan
                                        )
                                        Text(
                                            text = "CONF: ${"%.0f".format(event.confidence * 100)}% • PEAKS: ${event.peakCount}",
                                            fontSize = 10.sp,
                                            fontFamily = FontFamily.Monospace,
                                            color = TextSecondary
                                        )
                                    }
                                }
                                Text(
                                    text = sdf.format(Date(event.timestampMs)),
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = TextTertiary
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
