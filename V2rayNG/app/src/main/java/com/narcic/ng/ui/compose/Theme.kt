package com.narcic.ng.ui.compose

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.narcic.ng.AppConfig
import com.narcic.ng.handler.MmkvManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

// ─────────────────────────────────────────────────────────────────────────
// "Narcic Aurora" palette — matches the app's own launcher icon (deep navy
// #0A0E1A with a cyan/azure "N" mark) instead of the old brown/gold theme,
// for a premium NordVPN / Windscribe-style look.
// ─────────────────────────────────────────────────────────────────────────
val AuroraCyan = Color(0xFF38BDF8)     // signature brand blue (from app icon)
val AuroraIndigo = Color(0xFF6366F1)   // gradient partner
val AuroraViolet = Color(0xFF8B7CF6)   // used in glows / sweep gradients
val AuroraDeep = Color(0xFF0A0E1A)     // exact launcher-icon background

fun auroraGradient(alpha: Float = 1f) = Brush.linearGradient(
    colors = listOf(AuroraCyan.copy(alpha = alpha), AuroraIndigo.copy(alpha = alpha))
)

private val LightColor = lightColorScheme(
    primary = Color(0xFF0B79C4), // Aurora Blue (deepened for contrast on white)
    onPrimary = Color(0xFFFFFFFF), // White
    primaryContainer = Color(0xFFD3EBFC), // Pale Sky Blue
    onPrimaryContainer = Color(0xFF00344E), // Deep Navy
    secondary = Color(0xFF6552D6), // Aurora Violet
    onSecondary = Color(0xFFFFFFFF), // White
    secondaryContainer = Color(0xFFE6E0FF), // Pale Violet
    onSecondaryContainer = Color(0xFF1D1149), // Deep Indigo
    tertiary = Color(0xFFB4790A), // Refined Gold (used sparingly, e.g. Pro badges)
    onTertiary = Color(0xFFFFFFFF), // White
    tertiaryContainer = Color(0xFFFFE4AD), // Pale Gold
    onTertiaryContainer = Color(0xFF3A2600), // Deep Brown
    error = Color(0xFFD5273F), // Rose Red
    errorContainer = Color(0xFFFFDADD), // Pale Rose
    onError = Color(0xFFFFFFFF), // White
    onErrorContainer = Color(0xFF41000C), // Deep Red
    background = Color(0xFFF6F8FC), // Cool Off-White
    onBackground = Color(0xFF10151F), // Near-Black Navy
    surface = Color(0xFFFFFFFF), // White
    onSurface = Color(0xFF10151F), // Near-Black Navy
    surfaceVariant = Color(0xFFE3E8F2), // Pale Blue-Gray
    onSurfaceVariant = Color(0xFF444E60), // Slate
    outline = Color(0xFF75808F), // Medium Slate
    outlineVariant = Color(0xFFC7CFDC), // Light Slate
    inverseSurface = Color(0xFF1B2130), // Deep Navy
    inverseOnSurface = Color(0xFFEFF2F8), // Very Light Blue-Gray
    inversePrimary = Color(0xFF7FD4FF), // Bright Sky Blue
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFF0B79C4), // Aurora Blue
    surfaceContainerLowest = Color(0xFFFFFFFF), // White
    surfaceContainerLow = Color(0xFFF0F3F9), // Very Pale Blue
    surfaceContainer = Color(0xFFEAEEF6), // Pale Blue-Gray
    surfaceContainerHigh = Color(0xFFE4E9F2), // Blue-Gray
    surfaceContainerHighest = Color(0xFFDEE4EF), // Blue-Gray
)

private val DarkColor = darkColorScheme(
    primary = Color(0xFF38BDF8), // Aurora Cyan (matches the app's own launcher icon)
    onPrimary = Color(0xFF04121F), // Near-Black Navy
    primaryContainer = Color(0xFF13314A), // Deep Ocean Blue
    onPrimaryContainer = Color(0xFFBFE4FB), // Pale Sky Blue
    secondary = Color(0xFF8B7CF6), // Aurora Violet
    onSecondary = Color(0xFF1A1330), // Deep Indigo
    secondaryContainer = Color(0xFF2A2150), // Indigo
    onSecondaryContainer = Color(0xFFE3DBFF), // Pale Violet
    tertiary = Color(0xFFF4B740), // Refined Gold (kept as a small nod to "Narcis" — used only for Pro/rating accents)
    onTertiary = Color(0xFF2B1C00), // Deep Brown
    tertiaryContainer = Color(0xFF4A3300), // Brown
    onTertiaryContainer = Color(0xFFFFE1A6), // Pale Gold
    error = Color(0xFFFB7185), // Rose
    errorContainer = Color(0xFF4A1123), // Deep Rose
    onError = Color(0xFF2B000A), // Near-Black Red
    onErrorContainer = Color(0xFFFFD9E1), // Pale Rose
    background = Color(0xFF0A0E1A), // Deep Navy — exact match to the launcher icon
    onBackground = Color(0xFFEAF0FA), // Cool Near-White
    surface = Color(0xFF0A0E1A), // Deep Navy
    onSurface = Color(0xFFEAF0FA), // Cool Near-White
    surfaceVariant = Color(0xFF1B2436), // Elevated Blue-Gray
    onSurfaceVariant = Color(0xFF9AA8C2), // Muted Slate Blue
    outline = Color(0xFF2E3A52), // Slate Border
    outlineVariant = Color(0xFF1E2739), // Subtle Slate Border
    inverseSurface = Color(0xFFEAF0FA), // Cool Near-White
    inverseOnSurface = Color(0xFF11182A), // Deep Navy
    inversePrimary = Color(0xFF0B5E86), // Deep Azure
    scrim = Color(0xFF000000), // Black
    surfaceTint = Color(0xFF38BDF8), // Aurora Cyan
    surfaceContainerLowest = Color(0xFF060911), // Almost Black Navy
    surfaceContainerLow = Color(0xFF0D1220), // Deep Navy
    surfaceContainer = Color(0xFF111827), // Card Navy
    surfaceContainerHigh = Color(0xFF161F32), // Elevated Card Navy
    surfaceContainerHighest = Color(0xFF1C273C), // Most Elevated Card Navy
)

// Semantic Colors
val colorPing = Color(0xFF34D399) // Emerald (fast/good)
val colorPingRed = Color(0xFFFB7185) // Rose (slow/bad)
val colorConfigType = Color(0xFFFB923C) // Orange
val colorFabActive = Color(0xFF38BDF8) // Aurora Cyan
val colorFabInactiveLight = Color(0xFFAEB8C6) // Cool Gray
val colorFabInactiveDark = Color(0xFF4B5568) // Dark Slate
val dividerColorLight = Color(0xFFE1E6EF) // Pale Slate
val dividerColorDark = Color(0xFF232D40) // Deep Slate

// Toast Colors 70%
val toastNormalBgLight = Color(0xB3232A3D) // Deep Slate
val toastNormalBgDark = Color(0xB32E3A52) // Slate Navy
val toastSuccessBg = Color(0xB30F9D67) // Emerald
val toastErrorBg = Color(0xB3E11D48) // Rose Red
val toastInfoBg = Color(0xB32F6FEB) // Aurora Blue
val toastIconCircleBg = Color(0x33FFFFFF) // Semi-transparent White
val toastTextColor = Color.White // White

object ThemeManager {
    private val _themeMode = MutableStateFlow(
        MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    )
    val themeMode: StateFlow<String> = _themeMode.asStateFlow()

    fun setThemeMode(mode: String) {
        MmkvManager.encodeSettings(AppConfig.PREF_UI_MODE_NIGHT, mode)
        _themeMode.value = mode
    }

    fun refresh() {
        _themeMode.value =
            MmkvManager.decodeSettingsString(AppConfig.PREF_UI_MODE_NIGHT, "0") ?: "0"
    }
}

@Composable
fun resolveDarkTheme(): Boolean {
    val mode by ThemeManager.themeMode.collectAsState()
    return when (mode) {
        "1" -> false
        "2" -> true
        else -> isSystemInDarkTheme()
    }
}

val LocalDarkTheme = compositionLocalOf { false }

@Composable
fun AppTheme(
    darkTheme: Boolean = resolveDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColor else LightColor
    val snackbarController = rememberAppSnackbarController()

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val activity = view.context as? Activity ?: return@SideEffect
            val window = activity.window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    CompositionLocalProvider(
        LocalDarkTheme provides darkTheme,
        LocalAppSnackbar provides snackbarController
    ) {
        MaterialTheme(
            colorScheme = colorScheme
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                AppSnackbarBridge(controller = snackbarController)
                content()
                AppSnackbarHost(hostState = snackbarController.hostState)
            }
        }
    }
}
