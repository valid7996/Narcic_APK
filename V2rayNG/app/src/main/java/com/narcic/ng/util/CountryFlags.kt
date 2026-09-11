package com.narcic.ng.util

/**
 * Server remarks pulled from subscriptions conventionally start with a flag
 * emoji, e.g. "🇩🇪 | @WhiteDNS | DE14|15ms". This helper extracts that flag,
 * maps it to a Persian display name for the location picker / home card, and
 * lets the rest of the app group/filter servers by country without needing
 * any extra metadata on ProfileItem itself.
 */
object CountryFlags {

    // Two "regional indicator symbol" code points back to back, e.g. 🇩🇪 = 🇩 + 🇪.
    private val FLAG_REGEX = Regex("[\uD83C][\uDDE6-\uDDFF][\uD83C][\uDDE6-\uDDFF]")

    /** Returns the first flag emoji found in [remarks], or null if there isn't one. */
    fun extractFlag(remarks: String?): String? {
        if (remarks.isNullOrEmpty()) return null
        return FLAG_REGEX.find(remarks)?.value
    }

    /** Converts a flag emoji (e.g. "🇩🇪") back to its 2-letter ISO code (e.g. "DE"). */
    fun flagToIsoCode(flag: String): String? {
        if (flag.length < 4) return null
        val first = flag.codePointAt(0)
        val second = flag.codePointAt(flag.offsetByCodePoints(0, 1))
        if (first !in 0x1F1E6..0x1F1FF || second !in 0x1F1E6..0x1F1FF) return null
        val a = ('A' + (first - 0x1F1E6))
        val b = ('A' + (second - 0x1F1E6))
        return "$a$b"
    }

    /** Persian display name for a flag emoji; falls back to its ISO code if unmapped. */
    fun displayNameFa(flag: String): String {
        val iso = flagToIsoCode(flag) ?: return flag
        return isoNamesFa[iso] ?: iso
    }

    private val isoNamesFa: Map<String, String> = mapOf(
        "US" to "آمریکا",
        "GB" to "بریتانیا",
        "DE" to "آلمان",
        "FR" to "فرانسه",
        "NL" to "هلند",
        "TR" to "ترکیه",
        "RU" to "روسیه",
        "CA" to "کانادا",
        "SG" to "سنگاپور",
        "JP" to "ژاپن",
        "HK" to "هنگ‌کنگ",
        "KR" to "کره جنوبی",
        "IN" to "هند",
        "AE" to "امارات متحدهٔ عربی",
        "IR" to "ایران",
        "AL" to "آلبانی",
        "IT" to "ایتالیا",
        "ES" to "اسپانیا",
        "CH" to "سوئیس",
        "AT" to "اتریش",
        "PL" to "لهستان",
        "RO" to "رومانی",
        "GR" to "یونان",
        "BE" to "بلژیک",
        "DK" to "دانمارک",
        "NO" to "نروژ",
        "SE" to "سوئد",
        "FI" to "فنلاند",
        "IE" to "ایرلند",
        "PT" to "پرتغال",
        "CZ" to "چک",
        "UA" to "اوکراین",
        "IL" to "اسرائیل",
        "AU" to "استرالیا",
        "BR" to "برزیل",
        "ZA" to "افریقای جنوبی",
        "ID" to "اندونزی",
        "AM" to "ارمنستان",
        "UZ" to "ازبکستان",
        "EE" to "استونی",
        "LT" to "لیتوانی",
        "LV" to "لتونی",
        "BG" to "بلغارستان",
        "HU" to "مجارستان",
        "HR" to "کرواسی",
        "RS" to "صربستان",
        "MD" to "مولداوی",
        "GE" to "گرجستان",
        "AZ" to "آذربایجان",
        "KZ" to "قزاقستان",
        "TH" to "تایلند",
        "VN" to "ویتنام",
        "MY" to "مالزی",
        "PH" to "فیلیپین",
        "TW" to "تایوان",
        "CN" to "چین",
        "MX" to "مکزیک",
        "AR" to "آرژانتین",
        "EG" to "مصر",
        "SA" to "عربستان سعودی",
        "QA" to "قطر",
        "KW" to "کویت",
        "OM" to "عمان",
        "IQ" to "عراق",
        "PK" to "پاکستان",
        "AF" to "افغانستان",
        "IS" to "ایسلند",
        "SK" to "اسلواکی",
        "LU" to "لوکزامبورگ",
        "CY" to "قبرس",
        "MT" to "مالت",
        "NZ" to "نیوزیلند",
    )
}
