package com.narcic.ng.psiphon

import android.content.Context
import com.narcic.ng.util.CountryFlags

/**
 * The egress countries for MSN-Guard Psiphon mode, with flags and names.
 */
object PsiphonRegions {

    const val AVAILABLE_PREF = "psiphon_available_regions"

    private val BUNDLED = mapOf(
        "US" to "ایالات متحده آمریکا",
        "CA" to "کانادا",
        "DE" to "آلمان",
        "NL" to "هلند",
        "GB" to "بریتانیا",
        "FR" to "فرانسه",
        "PL" to "لهستان",
        "SE" to "سوئد",
        "JP" to "ژاپن",
        "SG" to "سنگاپور",
        "IN" to "هند",
        "ES" to "اسپانیا",
        "IT" to "ایتالیا",
        "AU" to "استرالیا",
        "DK" to "دانمارک",
        "FI" to "فنلاند",
        "RS" to "صربستان",
        "NO" to "نروژ",
        "CH" to "سوئیس",
        "AT" to "اتریش",
        "CZ" to "چک",
        "BE" to "بلژیک",
        "IE" to "ایرلند",
        "ID" to "اندونزی",
        "RO" to "رومانی",
    )

    private val BUNDLED_COUNT = mapOf(
        "US" to 65, "CA" to 65, "DE" to 60, "NL" to 38, "GB" to 31, "FR" to 26,
        "PL" to 18, "SE" to 17, "JP" to 13, "SG" to 12, "IN" to 11, "ES" to 10,
        "IT" to 9, "AU" to 8, "DK" to 7, "FI" to 7, "RS" to 5, "NO" to 5,
        "CH" to 5, "AT" to 4, "CZ" to 4, "BE" to 3, "IE" to 3, "ID" to 3,
        "RO" to 1,
    )

    fun name(code: String): String {
        val key = code.trim().uppercase()
        return BUNDLED[key] ?: CountryFlags.displayNameFa(flagFor(key))
    }

    fun count(code: String): Int = BUNDLED_COUNT[code.trim().uppercase()] ?: 0

    fun flagFor(code: String): String {
        if (code.length != 2) return "🌐"
        val first = Character.codePointAt(code.uppercase(), 0) - 'A'.code + 0x1F1E6
        val second = Character.codePointAt(code.uppercase(), 1) - 'A'.code + 0x1F1E6
        return String(Character.toChars(first)) + String(Character.toChars(second))
    }

    fun options(): List<Pair<String, String>> {
        return BUNDLED.keys.sortedByDescending { BUNDLED_COUNT[it] ?: 0 }.map { code ->
            code to name(code)
        }
    }
}
