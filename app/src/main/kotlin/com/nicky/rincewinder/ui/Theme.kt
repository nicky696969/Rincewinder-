package com.nicky.rincewinder.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * „Dvě světla · beton“ (zvoleno Nickym 8. 11. 2026, náhled mimo repo): nahoře modrá hodina a LED,
 * dole sodíková lampa; beton nemá vlastní barvu, nese světlo. Barvy odvozené z měření všech 435
 * snímků na kartě (výrazně teplé 190, výrazně chladné 68). Dynamické barvy Pixelu se nepoužívají —
 * na Nickyho tapetě vycházely šedé a mazaly identitu.
 */
object Palette {
    // studený pól: LED pásky kolonády, modrý smrk
    val LedWhite = Color(0xFFE9F7FF)
    val LedBlue = Color(0xFF7FD6FF)
    val ColdText = Color(0xFFCFEEFC)
    val ColdLabel = Color(0xFF7FB3C2)
    val Teal = Color(0xFF5FD3C4)
    // teplý pól: sodíková lampa
    val SodiumLight = Color(0xFFFFCF7A)
    val Sodium = Color(0xFFFFB34A)
    val SodiumDeep = Color(0xFF6A4410)
    val WarmLabel = Color(0xFFC99A4E)
    val OnSodium = Color(0xFF2A1604)
    // chyby a mazání: LED hodiny v metru, vrata hasičárny
    val Red = Color(0xFFFF6B5A)
    // beton a text
    val Concrete = Color(0xFF2B2E31)
    val ConcreteDark = Color(0xFF07090B)
    val Flat = Color(0xFF151B21)
    val FlatBorder = Color(0xFF232B33)
    val Text = Color(0xFFECE6DC)
    val Mute = Color(0xFF8E9AA2)
    val MuteWarm = Color(0xFF8E7D63)

    // původní jména, ať zbytek kódu sedí
    val Orange = Sodium
    val Bg = Color(0xFF0D151C)
    val Card = Flat
    val Divider = FlatBorder
    val Ok = Teal
    val Warn = Sodium
    val Err = Red

    /** Pozadí obrazovky: modrá hodina nahoře → sodík dole. */
    val Sky = Brush.verticalGradient(
        0f to Color(0xFF13222D), 0.38f to Color(0xFF0D151C), 0.70f to Color(0xFF120E0A), 1f to Color(0xFF24170A),
    )
}

@Composable
fun RincewinderTheme(content: @Composable () -> Unit) {
    val scheme = darkColorScheme(
        primary = Palette.Sodium, onPrimary = Palette.OnSodium,
        secondary = Palette.Teal, onSecondary = Color(0xFF04201C),
        tertiary = Palette.LedBlue,
        background = Palette.Bg, onBackground = Palette.Text,
        surface = Palette.Flat, onSurface = Palette.Text,
        surfaceVariant = Color(0xFF1A2229), onSurfaceVariant = Palette.Mute,
        surfaceContainer = Palette.Flat, surfaceContainerHigh = Color(0xFF1A2229), surfaceContainerHighest = Color(0xFF1F2830),
        outline = Color(0xFF3A4652), outlineVariant = Palette.FlatBorder,
        error = Palette.Red,
    )
    MaterialTheme(colorScheme = scheme, content = content)
}
