package com.tapback.app.feature.settings

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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatterySaver
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Vibration
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapback.app.TapBackApplication
import com.tapback.app.core.permissions.PermissionHelper
import com.tapback.app.domain.models.TapBackSettings
import androidx.compose.ui.text.font.FontFamily
import com.tapback.app.ui.theme.ElectricBlue
import com.tapback.app.ui.theme.NordBackground
import com.tapback.app.ui.theme.NordCard
import com.tapback.app.ui.theme.NordCardBorder
import com.tapback.app.ui.theme.OnePlusRed
import com.tapback.app.ui.theme.RetroNeonCyan
import com.tapback.app.ui.theme.RetroNeonPink
import com.tapback.app.ui.theme.RetroPhosphorAmber
import com.tapback.app.ui.theme.RetroPhosphorGreen
import com.tapback.app.ui.theme.TextPrimary
import com.tapback.app.ui.theme.TextSecondary
import com.tapback.app.ui.theme.TextTertiary
import com.tapback.app.ui.theme.WarningOrange
import kotlinx.coroutines.launch

@Composable
fun AppSettingsScreen(app: TapBackApplication) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val settings by app.container.preferencesManager.settingsFlow.collectAsState(initial = TapBackSettings())

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
                        text = "// SYSTEM CONFIGURATION",
                        fontSize = 20.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = TextPrimary
                    )
                    Text(
                        text = "DAEMON LIFECYCLE & OXYGENOS COMPLIANCE",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 0.5.sp,
                        color = RetroNeonCyan
                    )
                }
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = null,
                    tint = RetroNeonCyan,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        // OxygenOS Battery Optimization Guide Card
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0D0F18))
                    .border(1.dp, RetroPhosphorAmber.copy(alpha = 0.45f), RoundedCornerShape(16.dp))
                    .padding(16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.BatterySaver,
                        contentDescription = null,
                        tint = RetroPhosphorAmber,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "// OXYGENOS DAEMON RETENTION",
                        fontSize = 13.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.5.sp,
                        color = TextPrimary
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "OnePlus Nord 5 aggressively terminates dormant background sensors. Configure these 3 system parameters:",
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
                Spacer(modifier = Modifier.height(10.dp))

                GuideStepItem(number = "01", title = "UNRESTRICTED BATTERY", desc = "Set Battery usage to 'Don't optimize' or 'Unrestricted'.")
                GuideStepItem(number = "02", title = "AUTO-LAUNCH ACTIVE", desc = "Enable 'Allow background activity' and 'Allow auto-launch'.")
                GuideStepItem(number = "03", title = "LOCK IN RECENTS", desc = "In OxygenOS App Switcher, tap 3 dots on TapBack and select 'Lock'.")

                Spacer(modifier = Modifier.height(14.dp))
                Button(
                    onClick = { PermissionHelper.openOnePlusBatterySettings(context) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1428)),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .border(1.dp, RetroNeonPink.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "[ OPEN ONEPLUS BATTERY PREFERENCES ]",
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                        color = RetroNeonPink
                    )
                }
            }
        }

        // Detection & System Controls
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0A0D15))
                    .border(1.dp, Color(0xFF1B2438), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "// DAEMON LIFECYCLE",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = RetroNeonCyan
                )

                SettingToggleRow(
                    icon = Icons.Default.PowerSettingsNew,
                    title = "START ON SYSTEM BOOT",
                    subtitle = "Resume IMU sensor daemon on reboot",
                    checked = settings.startOnBoot,
                    onCheckedChange = { checked ->
                        scope.launch { app.container.preferencesManager.updateSettings(settings.copy(startOnBoot = checked)) }
                    }
                )

                SettingToggleRow(
                    icon = Icons.Default.Info,
                    title = "SCREEN-OFF DETECTION",
                    subtitle = "Dormant wake-lock listening while locked",
                    checked = settings.screenOffDetection,
                    onCheckedChange = { checked ->
                        scope.launch { app.container.preferencesManager.updateSettings(settings.copy(screenOffDetection = checked)) }
                    }
                )
            }
        }

        // Feedback & Haptics
        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0A0D15))
                    .border(1.dp, Color(0xFF1B2438), RoundedCornerShape(16.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "// HAPTIC & AUDIO ANNUNCIATORS",
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp,
                    color = RetroPhosphorGreen
                )

                SettingToggleRow(
                    icon = Icons.Default.Vibration,
                    title = "HAPTIC TICK PATTERNS",
                    subtitle = "Crisp hardware clicks (1× single, 2× double, 3× triple)",
                    checked = settings.hapticFeedback,
                    onCheckedChange = { checked ->
                        scope.launch { app.container.preferencesManager.updateSettings(settings.copy(hapticFeedback = checked)) }
                    }
                )

                SettingToggleRow(
                    icon = Icons.AutoMirrored.Filled.VolumeUp,
                    title = "AUDIO CONFIRMATION",
                    subtitle = "Subtle auditory feedback on confirmed gesture",
                    checked = settings.soundFeedback,
                    onCheckedChange = { checked ->
                        scope.launch { app.container.preferencesManager.updateSettings(settings.copy(soundFeedback = checked)) }
                    }
                )
            }
        }

        // Reset Defaults Card
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF0E121E))
                    .border(1.dp, Color(0xFF1E283C), RoundedCornerShape(12.dp))
                    .clickable {
                        scope.launch {
                            app.container.preferencesManager.updateSettings(TapBackSettings())
                        }
                    }
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Restore,
                    contentDescription = null,
                    tint = TextTertiary,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "RESET TO FACTORY DEFAULTS",
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.Bold,
                        color = TextPrimary
                    )
                    Text(
                        text = "Restores recommended 2.8 m/s² threshold and actions",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        color = TextSecondary
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = RetroNeonCyan,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RetroNeonCyan,
                uncheckedThumbColor = TextSecondary,
                uncheckedTrackColor = Color(0xFF1B2334)
            )
        )
    }
}

@Composable
private fun GuideStepItem(number: String, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF161F30))
                .border(1.dp, RetroNeonCyan.copy(alpha = 0.5f), RoundedCornerShape(6.dp)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = number,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = RetroNeonCyan
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(
                text = title,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Bold,
                color = TextPrimary
            )
            Text(
                text = desc,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = TextSecondary
            )
        }
    }
}
