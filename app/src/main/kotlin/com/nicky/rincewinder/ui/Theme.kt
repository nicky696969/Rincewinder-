package com.nicky.rincewinder.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

/** UX_WIREFRAMES §11: tmavé výchozí, Material 3 dynamické barvy, fallback oranžová. */
object Palette {
    val Orange = Color(0xFFFF8A3D)
    val Bg = Color(0xFF0F1114)
    val Card = Color(0xFF1A1D22)
    val Divider = Color(0xFF2F343C)
    val Ok = Color(0xFF4CD27A)
    val Warn = Color(0xFFF5C542)
    val Err = Color(0xFFFF6B6B)
}

@Composable
fun RincewinderTheme(content: @Composable () -> Unit) {
    val ctx = LocalContext.current
    // tmavé téma vždy (zamčeno 31. 10.), dynamické barvy Pixelu s oranžovým fallbackem
    val scheme = runCatching { dynamicDarkColorScheme(ctx) }.getOrNull()?.copy(background = Palette.Bg, surface = Palette.Card)
        ?: darkColorScheme(primary = Palette.Orange, background = Palette.Bg, surface = Palette.Card)
    MaterialTheme(colorScheme = scheme, content = content)
}
