package com.tapback.app.feature.gestures

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.database.Cursor
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Build
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Assistant
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.FlashlightOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Screenshot
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tapback.app.domain.models.ActionType
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class InstalledAppInfo(
    val label: String,
    val packageName: String,
    val iconBitmap: ImageBitmap? = null
)

private fun drawableToBitmap(drawable: Drawable): Bitmap? {
    return try {
        if (drawable is BitmapDrawable && drawable.bitmap != null) {
            return drawable.bitmap
        }
        val width = drawable.intrinsicWidth.takeIf { it > 0 } ?: 96
        val height = drawable.intrinsicHeight.takeIf { it > 0 } ?: 96
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = android.graphics.Canvas(bitmap)
        drawable.setBounds(0, 0, canvas.width, canvas.height)
        drawable.draw(canvas)
        bitmap
    } catch (e: Exception) {
        null
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ActionPickerSheet(
    selectedAction: ActionType,
    selectedPackage: String?,
    onActionSelected: (ActionType, String?) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    var installedApps by remember { mutableStateOf<List<InstalledAppInfo>>(emptyList()) }
    var showAppDrawer by remember { mutableStateOf(false) }
    var showContactPicker by remember { mutableStateOf(false) }
    var appSearchQuery by remember { mutableStateOf("") }

    // Contact state prefilled from existing selection if any
    var contactNameInput by remember {
        mutableStateOf(
            if (selectedAction == ActionType.CALL_CONTACT && selectedPackage != null && selectedPackage.contains("|")) {
                selectedPackage.substringAfter("|")
            } else ""
        )
    }
    var contactPhoneInput by remember {
        mutableStateOf(
            if (selectedAction == ActionType.CALL_CONTACT && selectedPackage != null) {
                if (selectedPackage.contains("|")) selectedPackage.substringBefore("|") else selectedPackage
            } else ""
        )
    }

    // Native contact picker launcher
    val contactPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickContact()
    ) { contactUri: Uri? ->
        if (contactUri != null) {
            try {
                val cr = context.contentResolver
                val cursor: Cursor? = cr.query(
                    contactUri,
                    arrayOf(ContactsContract.Contacts._ID, ContactsContract.Contacts.DISPLAY_NAME, ContactsContract.Contacts.HAS_PHONE_NUMBER),
                    null,
                    null,
                    null
                )
                cursor?.use { c ->
                    if (c.moveToFirst()) {
                        val idIndex = c.getColumnIndex(ContactsContract.Contacts._ID)
                        val nameIndex = c.getColumnIndex(ContactsContract.Contacts.DISPLAY_NAME)
                        val hasPhoneIndex = c.getColumnIndex(ContactsContract.Contacts.HAS_PHONE_NUMBER)

                        val id = if (idIndex != -1) c.getString(idIndex) else null
                        val name = if (nameIndex != -1) c.getString(nameIndex) else "Contact"
                        contactNameInput = name

                        val hasPhone = if (hasPhoneIndex != -1) c.getInt(hasPhoneIndex) > 0 else false
                        if (hasPhone && id != null) {
                            val phoneCursor = cr.query(
                                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                                arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER),
                                "${ContactsContract.CommonDataKinds.Phone.CONTACT_ID} = ?",
                                arrayOf(id),
                                null
                            )
                            phoneCursor?.use { pc ->
                                if (pc.moveToFirst()) {
                                    val numIndex = pc.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                                    if (numIndex != -1) {
                                        contactPhoneInput = pc.getString(numIndex).replace(" ", "").replace("-", "")
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Handled gracefully; user can enter phone number directly
            }
        }
    }

    // Load full list of installed launcher applications in background IO
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val resolveInfos = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                pm.queryIntentActivities(mainIntent, PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_ALL.toLong()))
            } else {
                @Suppress("DEPRECATION")
                pm.queryIntentActivities(mainIntent, PackageManager.MATCH_ALL)
            }

            val appsList = mutableListOf<InstalledAppInfo>()
            val seenPackages = mutableSetOf<String>()

            for (ri in resolveInfos) {
                val pkg = ri.activityInfo.packageName
                if (pkg != context.packageName && seenPackages.add(pkg)) {
                    val label = ri.loadLabel(pm).toString()
                    val drawable = ri.loadIcon(pm)
                    val bitmap = drawableToBitmap(drawable)
                    appsList.add(InstalledAppInfo(label, pkg, bitmap?.asImageBitmap()))
                }
            }
            installedApps = appsList.sortedBy { it.label.lowercase() }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = Color(0xFF0A0D15),
        dragHandle = {
            Box(
                modifier = Modifier
                    .padding(vertical = 12.dp)
                    .width(44.dp)
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF28344C))
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 6.dp)
        ) {
            when {
                // =========================================================================
                // VIEW 1: MINI APP DRAWER (All Installed Apps with Icons & Search)
                // =========================================================================
                showAppDrawer -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { showAppDrawer = false }) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = RetroNeonCyan
                                )
                            }
                            Spacer(modifier = Modifier.width(6.dp))
                            Column {
                                Text(
                                    text = "// MINI APP DRAWER",
                                    fontSize = 16.sp,
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "SELECT APPLICATION SHORTCUT",
                                    fontSize = 10.sp,
                                    fontFamily = FontFamily.Monospace,
                                    color = RetroNeonCyan
                                )
                            }
                        }

                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0xFF141C2E))
                                .border(1.dp, RetroNeonCyan.copy(alpha = 0.4f), RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 3.dp)
                        ) {
                            Text(
                                text = "${installedApps.size} APPS",
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                color = RetroNeonCyan
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // App Search Bar
                    OutlinedTextField(
                        value = appSearchQuery,
                        onValueChange = { appSearchQuery = it },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp)),
                        placeholder = {
                            Text(
                                text = "Search installed applications...",
                                fontSize = 12.sp,
                                fontFamily = FontFamily.Monospace,
                                color = TextTertiary
                            )
                        },
                        leadingIcon = {
                            Icon(Icons.Default.Search, contentDescription = null, tint = RetroNeonCyan, modifier = Modifier.size(18.dp))
                        },
                        trailingIcon = {
                            if (appSearchQuery.isNotEmpty()) {
                                IconButton(onClick = { appSearchQuery = "" }) {
                                    Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(18.dp))
                                }
                            }
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RetroNeonCyan,
                            unfocusedBorderColor = Color(0xFF1E283C),
                            focusedContainerColor = Color(0xFF0F1422),
                            unfocusedContainerColor = Color(0xFF0F1422),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    val filteredApps = remember(installedApps, appSearchQuery) {
                        if (appSearchQuery.isBlank()) installedApps
                        else installedApps.filter {
                            it.label.contains(appSearchQuery, ignoreCase = true) ||
                                    it.packageName.contains(appSearchQuery, ignoreCase = true)
                        }
                    }

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(400.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredApps, key = { it.packageName }) { app ->
                            val isSelected = selectedPackage == app.packageName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isSelected) Color(0xFF1A2338) else Color(0xFF0E131F))
                                    .border(
                                        1.dp,
                                        if (isSelected) RetroNeonCyan else Color(0xFF1C263A),
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable {
                                        onActionSelected(ActionType.LAUNCH_APP, app.packageName)
                                        onDismiss()
                                    }
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (app.iconBitmap != null) {
                                    Image(
                                        bitmap = app.iconBitmap,
                                        contentDescription = app.label,
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(Color(0xFF1A2338)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PhoneAndroid,
                                            contentDescription = null,
                                            tint = RetroNeonCyan,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = app.label,
                                        fontSize = 13.sp,
                                        fontFamily = FontFamily.Monospace,
                                        fontWeight = FontWeight.Bold,
                                        color = TextPrimary
                                    )
                                    Text(
                                        text = app.packageName,
                                        fontSize = 10.sp,
                                        fontFamily = FontFamily.Monospace,
                                        color = TextSecondary
                                    )
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = RetroPhosphorGreen,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                // =========================================================================
                // VIEW 2: FAVORITE CONTACT SPEED DIAL PICKER
                // =========================================================================
                showContactPicker -> {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { showContactPicker = false }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back",
                                tint = RetroNeonCyan
                            )
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "// SPEED DIAL SHORTCUT",
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                color = TextPrimary
                            )
                            Text(
                                text = "TRIGGER PHONE CALL ON TAP",
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                color = RetroNeonPink
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Contact Name Field
                    OutlinedTextField(
                        value = contactNameInput,
                        onValueChange = { contactNameInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                text = "CONTACT NAME / ALIAS",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        },
                        placeholder = {
                            Text(
                                text = "e.g. Mom, Home, Alex",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TextTertiary
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RetroNeonCyan,
                            unfocusedBorderColor = Color(0xFF1E283C),
                            focusedContainerColor = Color(0xFF0F1422),
                            unfocusedContainerColor = Color(0xFF0F1422),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(10.dp))

                    // Phone Number Field
                    OutlinedTextField(
                        value = contactPhoneInput,
                        onValueChange = { contactPhoneInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = {
                            Text(
                                text = "PHONE NUMBER",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 11.sp
                            )
                        },
                        placeholder = {
                            Text(
                                text = "e.g. +91 9876543210",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = TextTertiary
                            )
                        },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = RetroNeonCyan,
                            unfocusedBorderColor = Color(0xFF1E283C),
                            focusedContainerColor = Color(0xFF0F1422),
                            unfocusedContainerColor = Color(0xFF0F1422),
                            focusedTextColor = TextPrimary,
                            unfocusedTextColor = TextPrimary
                        )
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    // Pick From Contacts Button
                    Button(
                        onClick = { contactPickerLauncher.launch(null) },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF141D2E)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .border(1.dp, RetroNeonCyan.copy(alpha = 0.5f), RoundedCornerShape(8.dp))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Contacts,
                            contentDescription = null,
                            tint = RetroNeonCyan,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "[ PICK FROM ADDRESS BOOK ]",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = RetroNeonCyan
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    // Save Contact Action Button
                    Button(
                        onClick = {
                            val trimmedNumber = contactPhoneInput.trim()
                            val trimmedName = contactNameInput.trim().ifEmpty { "Contact" }
                            if (trimmedNumber.isNotEmpty()) {
                                onActionSelected(ActionType.CALL_CONTACT, "$trimmedNumber|$trimmedName")
                                onDismiss()
                            }
                        },
                        enabled = contactPhoneInput.trim().isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = RetroNeonCyan),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            imageVector = Icons.Default.Call,
                            contentDescription = null,
                            tint = Color(0xFF080A10),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "SET SPEED DIAL ACTION",
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color(0xFF080A10)
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))
                }

                // =========================================================================
                // VIEW 3: MAIN ACTION SELECTION LIST
                // =========================================================================
                else -> {
                    Text(
                        text = "// SELECT GESTURE ACTION",
                        fontSize = 18.sp,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = 1.sp,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "DISPATCH ACTION ON REAR CHASSIS STRIKE",
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        letterSpacing = 0.5.sp,
                        color = RetroNeonCyan
                    )

                    Spacer(modifier = Modifier.height(14.dp))

                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(420.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(ActionType.values()) { action ->
                            val isSelected = selectedAction == action
                            ActionRowItem(
                                action = action,
                                isSelected = isSelected,
                                extraPackage = if (isSelected) selectedPackage else null,
                                onClick = {
                                    when (action) {
                                        ActionType.LAUNCH_APP -> showAppDrawer = true
                                        ActionType.CALL_CONTACT -> showContactPicker = true
                                        else -> {
                                            onActionSelected(action, null)
                                            onDismiss()
                                        }
                                    }
                                }
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun ActionRowItem(
    action: ActionType,
    isSelected: Boolean,
    extraPackage: String? = null,
    onClick: () -> Unit
) {
    val icon = getActionIcon(action)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(if (isSelected) Color(0xFF161F30) else Color(0xFF0C101A))
            .border(
                1.dp,
                if (isSelected) RetroNeonCyan else Color(0xFF1D2638),
                RoundedCornerShape(12.dp)
            )
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) RetroNeonCyan.copy(alpha = 0.2f) else Color(0xFF141A28)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (isSelected) RetroNeonCyan else TextSecondary,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = action.title.uppercase(),
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = if (isSelected) RetroNeonCyan else TextPrimary
                )
                if (action.requiresAccessibility) {
                    Spacer(modifier = Modifier.width(6.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(RetroPhosphorAmber.copy(alpha = 0.2f))
                            .padding(horizontal = 4.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = "A11Y",
                            fontSize = 8.sp,
                            fontFamily = FontFamily.Monospace,
                            fontWeight = FontWeight.Bold,
                            color = RetroPhosphorAmber
                        )
                    }
                }
            }

            if (action == ActionType.CALL_CONTACT && extraPackage != null) {
                val name = if (extraPackage.contains("|")) extraPackage.substringAfter("|") else extraPackage
                val num = if (extraPackage.contains("|")) extraPackage.substringBefore("|") else ""
                Text(
                    text = "TARGET: $name ($num)",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = RetroPhosphorGreen
                )
            } else if (action == ActionType.LAUNCH_APP && extraPackage != null) {
                Text(
                    text = "TARGET: $extraPackage",
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = RetroPhosphorGreen
                )
            } else {
                Text(
                    text = action.description,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    color = TextSecondary
                )
            }
        }

        if (isSelected) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = RetroPhosphorGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

fun getActionIcon(action: ActionType): ImageVector {
    return when (action) {
        ActionType.NONE -> Icons.Default.Settings
        ActionType.SCREENSHOT -> Icons.Default.Screenshot
        ActionType.FLASHLIGHT -> Icons.Default.FlashlightOn
        ActionType.OPEN_CAMERA -> Icons.Default.CameraAlt
        ActionType.OPEN_ASSISTANT -> Icons.Default.Assistant
        ActionType.LAUNCH_APP -> Icons.Default.PhoneAndroid
        ActionType.CALL_CONTACT -> Icons.Default.Call
        ActionType.MEDIA_PLAY_PAUSE -> Icons.Default.PlayArrow
        ActionType.MEDIA_NEXT -> Icons.Default.SkipNext
        ActionType.MEDIA_PREV -> Icons.Default.SkipPrevious
        ActionType.VOLUME_UP -> Icons.AutoMirrored.Filled.VolumeUp
        ActionType.VOLUME_DOWN -> Icons.AutoMirrored.Filled.VolumeDown
        ActionType.OPEN_NOTIFICATIONS -> Icons.Default.Notifications
        ActionType.OPEN_QUICK_SETTINGS -> Icons.Default.Settings
        ActionType.LOCK_SCREEN -> Icons.Default.Lock
    }
}
