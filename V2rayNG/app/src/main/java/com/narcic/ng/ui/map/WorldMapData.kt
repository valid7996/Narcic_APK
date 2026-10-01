package com.narcic.ng.ui.map

import android.content.Context

/**
 * World map for the connection dashboard — a compact binary asset
 * (assets/map/world.bin, magic "ZWM1") holding, per country: its ISO-2 code,
 * a label anchor point and polygon rings, all in an equirectangular grid of
 * 65535 × 32767.5 units. Ported from ZedSecure's WorldMap (AGPL-3.0).
 */
object WorldMapData {
    const val WIDTH: Float = 65535f
    const val HEIGHT: Float = WIDTH / 2f

    class Country(
        val code: String,
        val anchorX: Float,
        val anchorY: Float,
        val rings: List<FloatArray>,
    )

    private var cached: List<Country>? = null

    fun countries(context: Context): List<Country> {
        cached?.let { return it }
        val parsed = runCatching {
            context.assets.open("map/world.bin").use { parse(it.readBytes()) }
        }.getOrDefault(emptyList())
        cached = parsed
        return parsed
    }

    fun anchorOf(countries: List<Country>, code: String?): Pair<Float, Float>? {
        val c = code?.trim()?.uppercase()?.takeIf { it.length == 2 } ?: return null
        val hit = countries.firstOrNull { it.code == c } ?: return null
        return hit.anchorX to hit.anchorY
    }

    private const val Y_SCALE = 0.5f

    private fun parse(bytes: ByteArray): List<Country> {
        var o = 0
        fun u8(): Int = bytes[o++].toInt() and 0xFF
        fun u16(): Int {
            val v = (bytes[o].toInt() and 0xFF) or ((bytes[o + 1].toInt() and 0xFF) shl 8)
            o += 2
            return v
        }

        if (bytes.size < 8) return emptyList()
        if (bytes[0] != 'Z'.code.toByte() || bytes[1] != 'W'.code.toByte() ||
            bytes[2] != 'M'.code.toByte() || bytes[3] != '1'.code.toByte()
        ) return emptyList()
        o = 4

        val count = u16()
        val out = ArrayList<Country>(count)
        repeat(count) {
            if (o + 5 > bytes.size) return out
            val code = "${bytes[o].toInt().toChar()}${bytes[o + 1].toInt().toChar()}"
            o += 2
            val ax = u16().toFloat()
            val ay = u16().toFloat() * Y_SCALE
            val ringCount = u8()
            val rings = ArrayList<FloatArray>(ringCount)
            repeat(ringCount) {
                if (o + 2 > bytes.size) return out
                val pointCount = u16()
                val ring = FloatArray(pointCount * 2)
                for (i in 0 until pointCount) {
                    ring[i * 2] = u16().toFloat()
                    ring[i * 2 + 1] = u16().toFloat() * Y_SCALE
                }
                rings += ring
            }
            out += Country(code, ax, ay, rings)
        }
        return out
    }
}
