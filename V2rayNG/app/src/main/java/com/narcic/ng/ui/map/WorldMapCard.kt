package com.narcic.ng.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.Nc

/**
 * Connection map for the main dashboard: the world drawn from the compact
 * world.bin asset, the EXIT country highlighted in the engine accent, the
 * device's REAL (home) IP location marked separately, and a route line
 * between the two. Ported concept from ZedSecure, restyled with Nc tokens.
 *
 * The real-IP point comes from [RealIpLookup] (bound to the physical network,
 * bypassing the tunnel); the exit point from the connection's country code.
 */
@Composable
fun WorldMapCard(
    remoteCountryCode: String,
    remoteCity: String,
    remoteIsp: String,
    remoteIp: String,
    remoteCountryName: String,
    accent: Color,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkTheme.current
    val txtMain = if (isDark) Nc.Txt else Color(0xFF1B2230)
    val txtSub = if (isDark) Nc.Sub else Color(0xFF6B7688)
    val cardBg = if (isDark) Color.White.copy(alpha = .05f) else Color.White.copy(alpha = .92f)
    val stroke = if (isDark) Color.White.copy(alpha = .12f) else Color(0x1A1B2230)
    val landColor = if (isDark) Color(0xFF1B2436) else Color(0xFFDDE4F0)
    val context = LocalContext.current

    val countries = remember { mutableStateOf<List<WorldMapData.Country>>(emptyList()) }
    LaunchedEffect(Unit) { countries.value = WorldMapData.countries(context) }

    var real by remember { mutableStateOf<RealIpLookup.Result?>(null) }
    LaunchedEffect(Unit) { real = RealIpLookup.fetch(context) }

    val exitAnchor = WorldMapData.anchorOf(countries.value, remoteCountryCode)
    val realAnchor = real?.countryCode?.let { WorldMapData.anchorOf(countries.value, it) }
    val homeCode = real?.countryCode

    Column(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(cardBg)
            .border(1.dp, stroke, RoundedCornerShape(20.dp))
            .padding(13.dp)
    ) {
        // ── header badges: شما (real IP) → خروجی (tunnel) ──
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            BadgeDot(color = Nc.Green, label = "شما")
            Text(
                text = listOf(
                    real?.city,
                    homeCode?.let { homeFlag(it) },
                ).filter { it != null && it.isNotBlank() }.joinToString(" ").ifBlank { "…" },
                color = txtMain, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Text("→", color = accent, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
            BadgeDot(color = accent, label = "خروجی")
            Text(
                text = listOf(remoteCity, remoteCountryName.ifBlank { remoteCountryCode })
                    .filter { it.isNotBlank() }.joinToString(" · ").ifBlank { "…" },
                color = txtMain, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.weight(1f))
            Text(remoteIp.ifBlank { "" }, color = txtSub, fontSize = 8.5.sp, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }

        // ── the map ──
        Canvas(
            Modifier
                .fillMaxWidth()
                .padding(top = 10.dp)
                .height(190.dp)
        ) {
            val list = countries.value
            if (list.isEmpty()) return@Canvas
            val sx = size.width / WorldMapData.WIDTH
            val sy = size.height / WorldMapData.HEIGHT
            val exitCode = remoteCountryCode.trim().uppercase()

            // Land: all countries as thin outlines.
            val landPath = Path()
            list.forEach { c ->
                c.rings.forEach { ring ->
                    if (ring.size >= 4) {
                        landPath.moveTo(ring[0] * sx, ring[1] * sy)
                        for (i in 2 until ring.size step 2) landPath.lineTo(ring[i] * sx, ring[i + 1] * sy)
                        landPath.close()
                    }
                }
            }
            drawPath(landPath, color = landColor, style = Stroke(1.dp.toPx()))

            // Exit country: filled with the engine accent.
            val exitCountry = list.firstOrNull { it.code == exitCode }
            if (exitCountry != null) {
                val exitPath = Path()
                exitCountry.rings.forEach { ring ->
                    if (ring.size >= 4) {
                        exitPath.moveTo(ring[0] * sx, ring[1] * sy)
                        for (i in 2 until ring.size step 2) exitPath.lineTo(ring[i] * sx, ring[i + 1] * sy)
                        exitPath.close()
                    }
                }
                drawPath(exitPath, color = accent.copy(alpha = .45f))
                drawPath(exitPath, color = accent, style = Stroke(1.2.dp.toPx()))
            }

            // Route line: real location → exit location.
            val from = realAnchor ?: exitAnchor
            val to = exitAnchor
            if (from != null && to != null) {
                val a = Offset(from.first * sx, from.second * sy)
                val b = Offset(to.first * sx, to.second * sy)
                drawLine(Nc.Green, a, b, strokeWidth = 2.dp.toPx())
                drawCircle(Nc.Green, radius = 5.dp.toPx(), center = a)
                drawCircle(Color.White, radius = 2.dp.toPx(), center = a)
                drawCircle(accent, radius = 6.dp.toPx(), center = b)
                drawCircle(Color.White, radius = 2.2.dp.toPx(), center = b)
            }
        }

        // ── footer: details ──
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 9.dp),
        ) {
            Column(Modifier.weight(1f)) {
                Text("IP واقعی شما", color = txtSub, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                Text(
                    listOf(real?.ip, real?.city).filter { !it.isNullOrBlank() }.joinToString(" · ").ifBlank { "—" },
                    color = txtMain, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            Column(Modifier.weight(1f)) {
                Text("ISP واقعی", color = txtSub, fontSize = 8.5.sp, fontWeight = FontWeight.Bold)
                Text(
                    real?.isp?.ifBlank { null } ?: "—",
                    color = txtMain, fontSize = 10.5.sp, fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun BadgeDot(color: Color, label: String) {
    Box(Modifier.size(8.dp).clip(CircleShape).background(color))
    Spacer(Modifier.width(1.dp))
    Text(label, color = color, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold)
}

private fun homeFlag(code: String): String {
    if (code.length != 2 || code.any { it !in 'A'..'Z' }) return ""
    return code.map { Character.toChars(0x1F1E6 + (it - 'A')) }.joinToString("") { String(it) }
}
