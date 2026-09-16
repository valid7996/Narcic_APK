package com.narcic.ng.ui.compose

import androidx.compose.ui.graphics.Color

/**
 * "Narcic Two-Engine" design tokens — the flat palette from the redesign
 * spec (dark glass UI, cyan/sky accent for AmneziaWG, violet/indigo accent
 * for V2Ray). Kept as a separate, additive object so existing screens that
 * still read [MaterialTheme.colorScheme] / Aurora* colors (Theme.kt) are
 * untouched; new VPN-screen components (ConnectHero, EngineSwitch,
 * ServerCard, GooLoader, ...) pull their colors from here instead.
 */
object Nc {
    val Bg = Color(0xFF070B14)
    val Txt = Color(0xFFF1F5F9)
    val Sub = Color(0xFF8B95A9)
    val Stroke = Color(0x2494A3B8)   // rgba(148,163,184,.14)
    val Stroke2 = Color(0x1494A3B8)  // rgba(148,163,184,.08)

    val Cyan = Color(0xFF22D3EE)
    val Violet = Color(0xFF8B5CF6)
    val Green = Color(0xFF34D399)
    val Amber = Color(0xFFFBBF24)
    val Red = Color(0xFFFB7185)

    // Engine accent pairs.
    val AwgAccent = Color(0xFF22D3EE)
    val AwgAccent2 = Color(0xFF0EA5E9)
    val V2Accent = Color(0xFF8B5CF6)
    val V2Accent2 = Color(0xFF6366F1)

    // State colors for the connect button / status pill.
    val StateConnecting = Color(0xFFF59E0B)
    val StateOff = Color(0xFFFB7185)

    // Signal-bar thresholds (ms -> color), null/unknown = all bars dim.
    val SignalGreen = Green            // <=100ms
    val SignalLime = Color(0xFFA3E635) // <=180ms
    val SignalAmber = Amber            // <=300ms
    val SignalRed = Red                // else
    val SignalOff = Color(0x3394A3B8)

    // Protocol badges.
    val BadgeAwg = Cyan
    val BadgeVless = Color(0xFFA78BFA)
    val BadgeVmess = Color(0xFF818CF8)
    val BadgeHy2 = Amber
    val BadgeAether = Color(0xFFF97316)

    // Week usage chart gradients.
    val ChartDownloadTop = Color(0xFF67E8F9)
    val ChartDownloadBottom = Color(0xFF0891B2)
    val ChartUploadTop = Color(0xFFA78BFA)
    val ChartUploadBottom = Color(0xFF6D28D9)
}

/** One accent + its darker/secondary partner, used for gradients/rings. */
data class AccentPair(val main: Color, val second: Color)

/** Active-engine accent: AmneziaWG (cyan/sky) vs V2Ray (violet/indigo). */
fun accentFor(isAwg: Boolean): AccentPair =
    if (isAwg) AccentPair(Nc.AwgAccent, Nc.AwgAccent2) else AccentPair(Nc.V2Accent, Nc.V2Accent2)

/** فارسی‌سازی ارقام برای نمایش (فقط نمایش، نه داده). */
fun faDigits(s: String): String = s.map { c ->
    if (c in '0'..'9') '۰' + (c - '0') else c
}.joinToString("")
