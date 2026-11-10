package com.tapback.app.feature.gestures

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
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapback.app.TapBackApplication
import com.tapback.app.domain.models.ActionType
import com.tapback.app.domain.models.GestureType
import com.tapback.app.domain.models.TapBackSettings
import com.tapback.app.ui.theme.ElectricBlue
import com.tapback.app.ui.theme.NordBackground
import com.tapback.app.ui.theme.NordCard
import com.tapback.app.ui.theme.NordCardBorder
import com.tapback.app.ui.theme.OnePlusRed
import com.tapback.app.ui.theme.SuccessGreen
import com.tapback.app.ui.theme.TextPrimary
import com.tapback.app.ui.theme.TextSecondary
import com.tapback.app.ui.theme.TextTertiary
import androidx.compose.ui.text.font.FontFamily
import com.tapback.app.ui.theme.RetroNeonCyan
import com.tapback.app.ui.theme.RetroNeonPink
import com.tapback.app.ui.theme.RetroPhosphorAmber
import com.tapback.app.ui.theme.RetroPhosphorGreen
import kotlinx.coroutines.launch

@Composable
fun GestureConfigScreen(app: TapBackApplication) {
    val scope = rememberCoroutineScope()
    val settings by app.container.preferencesManager.settingsFlow.collectAsState(initial = TapBackSettings())

    var activePickerGesture by remember { mutableStateOf<GestureType?>(null) }
    var isScreenOffPicker by remember { mutableStateOf(false) }

    // Practice test mode tracking
    var testTapCount by remember { mutableIntStateOf(0) }
    var lastTestGesture by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        app.container.detectionEngine.gestureEvents.collect { gesture ->
            testTapCount++
            lastTestGesture = "${gesture.gestureType.displayName} detected (${gesture.peakCount} peaks)"
        }
    }

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
                        text = "// GESTURE MAPPINGS",
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "DISPATCH ACTIONS ON CHASSIS STRIKES",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        color = RetroNeonCyan
                    )
                }
                Icon(
                    imageVector = Icons.Default.Gesture,
                    contentDescription = null,
                    tint = RetroNeonCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // Single Tap Card
        item {
            GestureActionCard(
                title = "// SINGLE TAP",
                subtitle = "Single strike on rear chassis (disabled by default to prevent false triggers)",
                action = settings.singleTapAction,
                targetPackage = settings.singleTapPackage,
                onClick = {
                    isScreenOffPicker = false
                    activePickerGesture = GestureType.SINGLE_TAP
                }
            )
        }

        // Double Tap Card
        item {
            GestureActionCard(
                title = "// DOUBLE TAP",
                subtitle = "Two rhythmic strikes (160–420 ms cadence window)",
                action = settings.doubleTapAction,
                targetPackage = settings.doubleTapPackage,
                onClick = {
                    isScreenOffPicker = false
                    activePickerGesture = GestureType.DOUBLE_TAP
                }
            )
        }

        // Triple Tap Card
        item {
            GestureActionCard(
                title = "// TRIPLE TAP",
                subtitle = "Three rhythmic strikes (adaptive 85% in-sequence force threshold)",
                action = settings.tripleTapAction,
                targetPackage = settings.tripleTapPackage,
                onClick = {
                    isScreenOffPicker = false
                    activePickerGesture = GestureType.TRIPLE_TAP
                }
            )
        }

        // Screen-Off Gestures Section
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0C0F18))
                    .border(1.dp, Color(0xFF1E2638), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PowerSettingsNew,
                        contentDescription = null,
                        tint = RetroNeonCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "// LOW-POWER DORMANT SENSING",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Requires Screen-Off Detection enabled under CONFIG tab",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF111624))
                        .border(1.dp, Color(0xFF1F2B42), RoundedCornerShape(10.dp))
                        .clickable {
                            isScreenOffPicker = true
                            activePickerGesture = GestureType.DOUBLE_TAP
                        }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "DOUBLE TAP [DISPLAY OFF]",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = settings.screenOffDoubleTapAction.title.uppercase(),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = RetroNeonCyan
                        )
                    }
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextTertiary)
                }

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF111624))
                        .border(1.dp, Color(0xFF1F2B42), RoundedCornerShape(10.dp))
                        .clickable {
                            isScreenOffPicker = true
                            activePickerGesture = GestureType.TRIPLE_TAP
                        }
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "TRIPLE TAP [DISPLAY OFF]",
                            fontSize = 12.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.SemiBold,
                            color = TextPrimary
                        )
                        Text(
                            text = settings.screenOffTripleTapAction.title.uppercase(),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = RetroNeonCyan
                        )
                    }
                    Icon(imageVector = Icons.Default.ChevronRight, contentDescription = null, tint = TextTertiary)
                }
            }
        }

        // Live Practice / Test Mode Card (Retro Cyber HUD)
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0C0F18))
                    .border(1.dp, Color(0xFF1E283C), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(RetroPhosphorGreen)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "// TACTILE BENCH PRACTICE",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Tap phone back now to verify sensory feedback & threshold.",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF101524))
                        .border(1.dp, Color(0xFF1E2A40), RoundedCornerShape(10.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = lastTestGesture ?: "AWAITING CHASSIS STRIKE...",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        color = if (lastTestGesture != null) RetroPhosphorGreen else TextTertiary
                    )
                    Text(
                        text = "TOTAL: $testTapCount",
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = RetroNeonPink
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    // Modal action selector
    activePickerGesture?.let { gesture ->
        val currentAction = when {
            isScreenOffPicker && gesture == GestureType.DOUBLE_TAP -> settings.screenOffDoubleTapAction
            isScreenOffPicker && gesture == GestureType.TRIPLE_TAP -> settings.screenOffTripleTapAction
            gesture == GestureType.SINGLE_TAP -> settings.singleTapAction
            gesture == GestureType.DOUBLE_TAP -> settings.doubleTapAction
            else -> settings.tripleTapAction
        }
        val currentPkg = when (gesture) {
            GestureType.SINGLE_TAP -> settings.singleTapPackage
            GestureType.DOUBLE_TAP -> settings.doubleTapPackage
            GestureType.TRIPLE_TAP -> settings.tripleTapPackage
        }

        ActionPickerSheet(
            selectedAction = currentAction,
            selectedPackage = currentPkg,
            onActionSelected = { newAction, pkg ->
                scope.launch {
                    if (isScreenOffPicker) {
                        val updated = if (gesture == GestureType.DOUBLE_TAP) {
                            settings.copy(screenOffDoubleTapAction = newAction)
                        } else {
                            settings.copy(screenOffTripleTapAction = newAction)
                        }
                        app.container.preferencesManager.updateSettings(updated)
                    } else {
                        when (gesture) {
                            GestureType.SINGLE_TAP -> app.container.preferencesManager.setSingleTapAction(newAction, pkg)
                            GestureType.DOUBLE_TAP -> app.container.preferencesManager.setDoubleTapAction(newAction, pkg)
                            GestureType.TRIPLE_TAP -> app.container.preferencesManager.setTripleTapAction(newAction, pkg)
                        }
                    }
                }
            },
            onDismiss = { activePickerGesture = null }
        )
    }
}

@Composable
fun GestureActionCard(
    title: String,
    subtitle: String,
    action: ActionType,
    targetPackage: String?,
    onClick: () -> Unit
) {
    val icon = getActionIcon(action)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Color(0xFF0D111C))
            .border(1.dp, Color(0xFF1D273C), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF151D30))
                .border(1.dp, RetroNeonCyan.copy(alpha = 0.3f), RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RetroNeonCyan,
                modifier = Modifier.size(22.dp)
            )
        }

        Spacer(modifier = Modifier.width(14.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                letterSpacing = 0.5.sp,
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            val displayActionText = when (action) {
                ActionType.LAUNCH_APP -> if (targetPackage != null) "APP // $targetPackage" else action.title.uppercase()
                ActionType.CALL_CONTACT -> if (targetPackage != null) {
                    val name = if (targetPackage.contains("|")) targetPackage.substringAfter("|") else "CONTACT"
                    val num = if (targetPackage.contains("|")) targetPackage.substringBefore("|") else targetPackage
                    "SPEED DIAL // $name ($num)"
                } else "SPEED DIAL // [NOT CONFIGURED]"
                else -> action.title.uppercase()
            }
            Text(
                text = displayActionText,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = RetroPhosphorGreen
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextTertiary,
            modifier = Modifier.size(20.dp)
        )
    }
}
