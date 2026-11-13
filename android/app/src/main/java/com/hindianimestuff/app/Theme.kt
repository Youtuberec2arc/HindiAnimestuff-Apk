package com.hindianimestuff.app

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Same palette as the website's :root variables
object C {
    val Bg = Color(0xFF0B0D12)
    val Surface = Color(0xFF151922)
    val Surface2 = Color(0xFF1A1F2B)
    val Surface3 = Color(0xFF222838)
    val Border = Color(0x14FFFFFF)
    val Text = Color(0xFFF2F4F8)
    val TextSecondary = Color(0xFFC2C8D6)
    val Muted = Color(0xFF8B92A5)
    val Accent = Color(0xFFE85D4C)
    val AccentSoft = Color(0x26E85D4C)
    val Gold = Color(0xFFE8B84A)
    val Success = Color(0xFF22C55E)
    val Telegram = Color(0xFF229ED9)
}

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = C.Accent,
            onPrimary = Color.White,
            background = C.Bg,
            onBackground = C.Text,
            surface = C.Surface,
            onSurface = C.Text,
            surfaceVariant = C.Surface2,
            onSurfaceVariant = C.TextSecondary,
            outline = C.Border
        ),
        content = content
    )
}
