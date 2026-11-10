package com.tapback.app.feature.dashboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.tapback.app.ui.theme.OnePlusRed
import com.tapback.app.ui.theme.RetroNeonCyan
import com.tapback.app.ui.theme.RetroNeonPink
import com.tapback.app.ui.theme.RetroPhosphorAmber

/**
 * Animated Retro-Cyber OnePlus Nord 5 chassis visualizer.
 * Displays synthwave neon wireframe and propagating radar/sonar tap shockwaves.
 */
@Composable
fun PhoneIllustration(
    isListening: Boolean,
    tapTriggerCount: Int,
    modifier: Modifier = Modifier
) {
    val rippleRadius = remember { Animatable(0f) }
    val rippleAlpha = remember { Animatable(0f) }

    LaunchedEffect(tapTriggerCount) {
        if (tapTriggerCount > 0) {
            rippleRadius.snapTo(8f)
            rippleAlpha.snapTo(0.95f)
            rippleRadius.animateTo(
                targetValue = 145f,
                animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing)
            )
            rippleAlpha.animateTo(
                targetValue = 0f,
                animationSpec = tween(durationMillis = 180)
            )
        }
    }

    Box(
        modifier = modifier
            .width(190.dp)
            .height(270.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val phoneWidth = size.width * 0.76f
            val phoneHeight = size.height * 0.94f
            val left = (size.width - phoneWidth) / 2f
            val top = (size.height - phoneHeight) / 2f

            // Phone Body (Retro Cyber Chassis)
            val phonePath = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = left,
                        top = top,
                        right = left + phoneWidth,
                        bottom = top + phoneHeight,
                        cornerRadius = CornerRadius(24.dp.toPx(), 24.dp.toPx())
                    )
                )
            }

            // Chassis Dark Cathode Gradient
            drawPath(
                path = phonePath,
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF131726),
                        Color(0xFF0D101C),
                        Color(0xFF080A12)
                    )
                )
            )

            // Retro Grid Lines across Phone Back
            val gridStep = 18.dp.toPx()
            var gy = top + gridStep
            while (gy < top + phoneHeight - 8.dp.toPx()) {
                drawLine(
                    color = Color(0x1800F0FF),
                    start = Offset(left + 6.dp.toPx(), gy),
                    end = Offset(left + phoneWidth - 6.dp.toPx(), gy),
                    strokeWidth = 1.dp.toPx()
                )
                gy += gridStep
            }

            var gx = left + gridStep
            while (gx < left + phoneWidth - 6.dp.toPx()) {
                drawLine(
                    color = Color(0x1400F0FF),
                    start = Offset(gx, top + 6.dp.toPx()),
                    end = Offset(gx, top + phoneHeight - 6.dp.toPx()),
                    strokeWidth = 1.dp.toPx()
                )
                gx += gridStep
            }

            // Outer Neon Cyan Border Glow
            drawPath(
                path = phonePath,
                color = if (isListening) RetroNeonCyan else Color(0xFF26334D),
                style = Stroke(width = if (isListening) 2.dp.toPx() else 1.2.dp.toPx())
            )

            // Vertical Camera Module
            val camLeft = left + 14.dp.toPx()
            val camTop = top + 16.dp.toPx()
            val camWidth = 26.dp.toPx()
            val camHeight = 58.dp.toPx()

            drawRoundRect(
                color = Color(0xFF090C15),
                topLeft = Offset(camLeft, camTop),
                size = Size(camWidth, camHeight),
                cornerRadius = CornerRadius(13.dp.toPx(), 13.dp.toPx())
            )
            drawRoundRect(
                color = if (isListening) RetroNeonCyan.copy(alpha = 0.5f) else Color(0xFF1E283D),
                topLeft = Offset(camLeft, camTop),
                size = Size(camWidth, camHeight),
                cornerRadius = CornerRadius(13.dp.toPx(), 13.dp.toPx()),
                style = Stroke(width = 1.dp.toPx())
            )

            // Top camera lens with Neon Cyan aperture
            val lens1Center = Offset(camLeft + camWidth / 2f, camTop + 15.dp.toPx())
            drawCircle(color = Color(0xFF172033), radius = 8.dp.toPx(), center = lens1Center)
            drawCircle(color = RetroNeonCyan, radius = 4.dp.toPx(), center = lens1Center)

            // Bottom camera lens with Neon Pink aperture
            val lens2Center = Offset(camLeft + camWidth / 2f, camTop + 43.dp.toPx())
            drawCircle(color = Color(0xFF172033), radius = 8.dp.toPx(), center = lens2Center)
            drawCircle(color = RetroNeonPink, radius = 3.5.dp.toPx(), center = lens2Center)

            // Flash LED
            drawCircle(
                color = RetroPhosphorAmber,
                radius = 2.5.dp.toPx(),
                center = Offset(camLeft + camWidth + 8.dp.toPx(), camTop + 29.dp.toPx())
            )

            // Center Tap Target Indicator
            val tapCenter = Offset(size.width / 2f, size.height * 0.49f)

            // Animated Tap Ripples (Retro Tri-Color Shockwaves)
            if (rippleAlpha.value > 0.01f) {
                // Outer Cyan Radar Ring
                drawCircle(
                    color = RetroNeonCyan.copy(alpha = rippleAlpha.value * 0.5f),
                    radius = rippleRadius.value * 1.0f,
                    center = tapCenter,
                    style = Stroke(width = 2.dp.toPx())
                )
                // Mid Amber Sonar Ring
                drawCircle(
                    color = RetroPhosphorAmber.copy(alpha = rippleAlpha.value * 0.75f),
                    radius = rippleRadius.value * 0.65f,
                    center = tapCenter,
                    style = Stroke(width = 2.5.dp.toPx())
                )
                // Core Laser Magenta Impact Ring
                drawCircle(
                    color = RetroNeonPink.copy(alpha = rippleAlpha.value),
                    radius = rippleRadius.value * 0.35f,
                    center = tapCenter,
                    style = Stroke(width = 3.5.dp.toPx())
                )
            }

            // Target Crosshair Reticle (+)
            val crosshairLen = 10.dp.toPx()
            drawLine(
                color = if (isListening) RetroNeonCyan else Color(0xFF3B4861),
                start = Offset(tapCenter.x - crosshairLen, tapCenter.y),
                end = Offset(tapCenter.x + crosshairLen, tapCenter.y),
                strokeWidth = 1.5.dp.toPx()
            )
            drawLine(
                color = if (isListening) RetroNeonCyan else Color(0xFF3B4861),
                start = Offset(tapCenter.x, tapCenter.y - crosshairLen),
                end = Offset(tapCenter.x, tapCenter.y + crosshairLen),
                strokeWidth = 1.5.dp.toPx()
            )

            // Outer Target Reticle Ring
            drawCircle(
                color = if (isListening) RetroNeonPink.copy(alpha = 0.85f) else Color(0xFF2C364C),
                radius = 16.dp.toPx(),
                center = tapCenter,
                style = Stroke(width = 1.8.dp.toPx())
            )

            // Center Tactile Bullseye Dot
            drawCircle(
                color = if (isListening) RetroNeonPink else Color(0xFF5A667F),
                radius = 4.dp.toPx(),
                center = tapCenter
            )

            // Cyber HUD Corner Brackets
            val bSize = 8.dp.toPx()
            val bOffset = 5.dp.toPx()
            // Top-Left bracket
            drawLine(Color(0xFF00F0FF), Offset(left + bOffset, top + bOffset), Offset(left + bOffset + bSize, top + bOffset), 1.5.dp.toPx())
            drawLine(Color(0xFF00F0FF), Offset(left + bOffset, top + bOffset), Offset(left + bOffset, top + bOffset + bSize), 1.5.dp.toPx())
            // Top-Right bracket
            drawLine(Color(0xFF00F0FF), Offset(left + phoneWidth - bOffset, top + bOffset), Offset(left + phoneWidth - bOffset - bSize, top + bOffset), 1.5.dp.toPx())
            drawLine(Color(0xFF00F0FF), Offset(left + phoneWidth - bOffset, top + bOffset), Offset(left + phoneWidth - bOffset, top + bOffset + bSize), 1.5.dp.toPx())
            // Bottom-Left bracket
            drawLine(Color(0xFF00F0FF), Offset(left + bOffset, top + phoneHeight - bOffset), Offset(left + bOffset + bSize, top + phoneHeight - bOffset), 1.5.dp.toPx())
            drawLine(Color(0xFF00F0FF), Offset(left + bOffset, top + phoneHeight - bOffset), Offset(left + bOffset, top + phoneHeight - bOffset - bSize), 1.5.dp.toPx())
            // Bottom-Right bracket
            drawLine(Color(0xFF00F0FF), Offset(left + phoneWidth - bOffset, top + phoneHeight - bOffset), Offset(left + phoneWidth - bOffset - bSize, top + phoneHeight - bOffset), 1.5.dp.toPx())
            drawLine(Color(0xFF00F0FF), Offset(left + phoneWidth - bOffset, top + phoneHeight - bOffset), Offset(left + phoneWidth - bOffset, top + phoneHeight - bOffset - bSize), 1.5.dp.toPx())
        }
    }
}
