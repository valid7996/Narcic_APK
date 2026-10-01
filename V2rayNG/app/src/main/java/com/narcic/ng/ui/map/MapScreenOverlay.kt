package com.narcic.ng.ui.map

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.Nc

/**
 * Full-screen connection map (opened via the نقشه button on the main screen):
 *  - pinch to zoom, drag to pan (clamped)
 *  - exit country filled with the engine accent
 *  - flag markers: the device's REAL location (home) and the tunnel EXIT
 *  - dotted route line between them + a floating pill with the city names
 *  - details card: موقعیت واقعی تو / خروجی / سرور
 * The real location is looked up OUTSIDE the tunnel (RealIpLookup).
 */
@Composable
fun MapScreenOverlay(
    remoteCountryCode: String,
    remoteCity: String,
    remoteIsp: String,
    remoteIp: String,
    remoteCountryName: String,
    serverAddress: String,
    accent: Color,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkTheme.current
    val txtMain = if (isDark) Nc.Txt else Color(0xFF1B2230)
    val txtSub = if (isDark) Nc.Sub else Color(0xFF6B7688)
    val cardBg = if (isDark) Color(0xFF0D1321) else Color.White
    val stroke = if (isDark) Color.White.copy(alpha = .12f) else Color(0x1A1B2230)
    val landColor = if (isDark) Color(0xFF1B2436) else Color(0xFFDDE4F0)
    val context = LocalContext.current

    val countries = remember { mutableStateOf<List<WorldMapData.Country>>(emptyList()) }
    LaunchedEffect(Unit) { countries.value = WorldMapData.countries(context) }

    var real by remember { mutableStateOf<RealIpLookup.Result?>(null) }
    var loadingReal by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        real = RealIpLookup.fetch(context)
        loadingReal = false
    }

    val homeFlag = real?.countryCode?.let { flagOf(it) }.orEmpty()
    val exitFlag = flagOf(remoteCountryCode)
    val exitLabel = listOf(remoteCity, remoteCountryName).filter { it.isNotBlank() }
        .joinToString(" · ").ifBlank { remoteCountryCode }

    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    Box(modifier.fillMaxSize().background(Color(0xFF070B14))) {
        Column(Modifier.fillMaxSize()) {

            // ── title bar ──
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 14.dp),
            ) {
                Text("نقشه", color = txtMain, fontSize = 22.sp, fontWeight = FontWeight.ExtraBold)
                Spacer(Modifier.weight(1f))
                Text(
                    "بستن ‹",
                    color = accent, fontSize = 12.sp, fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(10.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                )
            }

            // ── the map (pinch zoom + drag) ──
            Box(
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(Color(0xFF0B0F1A))
                    .border(1.dp, stroke, RoundedCornerShape(20.dp))
                    .clipToBounds()
            ) {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            translationX = offset.x
                            translationY = offset.y
                        }
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                val newScale = (scale * zoom).coerceIn(1f, 12f)
                                if (newScale != scale) {
                                    // keep the pinch midpoint roughly stable
                                    offset = Offset(
                                        (offset.x - size.width / 2f) * (newScale / scale) + size.width / 2f,
                                        (offset.y - size.height / 2f) * (newScale / scale) + size.height / 2f,
                                    )
                                    scale = newScale
                                }
                                val maxX = size.width * (scale - 1f) / 2f
                                val maxY = size.height * (scale - 1f) / 2f
                                offset = Offset(
                                    offset.x.plus(pan.x).coerceIn(-maxX, maxX),
                                    offset.y.plus(pan.y).coerceIn(-maxY, maxY),
                                )
                            }
                        }
                ) {
                    val list = countries.value
                    if (list.isEmpty()) return@Canvas
                    val sx = size.width / WorldMapData.WIDTH
                    val sy = size.height / WorldMapData.HEIGHT
                    val exitCode = remoteCountryCode.trim().uppercase()

                    // faint grid (like the reference shot)
                    val gridStep = size.width / 12f
                    var gx = 0f
                    while (gx <= size.width) {
                        drawLine(Color.White.copy(alpha = .03f), Offset(gx, 0f), Offset(gx, size.height), 1.dp.toPx())
                        gx += gridStep
                    }
                    var gy = 0f
                    while (gy <= size.height) {
                        drawLine(Color.White.copy(alpha = .03f), Offset(0f, gy), Offset(size.width, gy), 1.dp.toPx())
                        gy += gridStep
                    }

                    // land
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

                    // exit country filled
                    val exitCountry = list.firstOrNull { it.code == exitCode.trim().uppercase() }
                    if (exitCountry != null) {
                        val exitPath = Path()
                        exitCountry.rings.forEach { ring ->
                            if (ring.size >= 4) {
                                exitPath.moveTo(ring[0] * sx, ring[1] * sy)
                                for (i in 2 until ring.size step 2) exitPath.lineTo(ring[i] * sx, ring[i + 1] * sy)
                                exitPath.close()
                            }
                        }
                        drawPath(exitPath, color = accent.copy(alpha = .40f))
                        drawPath(exitPath, color = accent, style = Stroke(1.4.dp.toPx()))
                    }

                    // markers + dotted route
                    val exitAnchor = WorldMapData.anchorOf(list, remoteCountryCode)
                    val homeAnchor = real?.countryCode?.let { WorldMapData.anchorOf(list, it) }
                    if (homeAnchor != null && exitAnchor != null) {
                        val a = Offset(homeAnchor.first * sx, homeAnchor.second * sy)
                        val b = Offset(exitAnchor.first * sx, exitAnchor.second * sy)
                        val steps = 14
                        for (i in 0..steps) {
                            val t = i / steps.toFloat()
                            val p = Offset(a.x + (b.x - a.x) * t, a.y + (b.y - a.y) * t)
                            drawCircle(Nc.Green.copy(alpha = 1f - t * .6f), radius = (if (i % 2 == 0) 3.2f else 2.1f).dp.toPx(), center = p)
                        }
                        drawCircle(Nc.Green, radius = 9.dp.toPx(), center = a)
                        drawCircle(Color.White, radius = 3.dp.toPx(), center = a)
                        drawCircle(accent, radius = 10.dp.toPx(), center = b)
                        drawCircle(Color.White, radius = 3.2.dp.toPx(), center = b)
                    }
                }

                // ── floating flag pill (city → city) ──
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(9.dp),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xCC0D1321))
                        .border(1.dp, stroke, RoundedCornerShape(999.dp))
                        .padding(horizontal = 14.dp, vertical = 9.dp)
                ) {
                    FlagBadge(homeFlag.ifBlank { "🌐" })
                    Text(
                        real?.city?.ifBlank { null } ?: homeFlag.ifBlank { "…" },
                        color = txtMain, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text("⋯", color = Nc.Sub, fontSize = 11.sp)
                    Text(
                        exitLabel, color = txtMain, fontSize = 11.sp, fontWeight = FontWeight.Bold,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    FlagBadge(exitFlag.ifBlank { "🌐" })
                }

                if (loadingReal) {
                    Text(
                        "در حال یافتن موقعیت واقعی…",
                        color = txtSub, fontSize = 9.sp,
                        modifier = Modifier.align(Alignment.BottomStart).padding(10.dp),
                    )
                }
            }

            // ── details card ──
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(cardBg)
                    .border(1.dp, stroke, RoundedCornerShape(20.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                DetailRow("موقعیت واقعی تو", listOf(real?.city, real?.countryCode?.let { countryNameOf(it) }).filter { !it.isNullOrBlank() }.joinToString(", ").ifBlank { "…" }, Nc.Green, txtMain, txtSub)
                DetailRow("خروجی", listOf(remoteCity, remoteCountryName).filter { it.isNotBlank() }.joinToString(", ").ifBlank { remoteCountryCode }, accent, txtMain, txtSub)
                DetailRow("سرور", serverAddress.ifBlank { "—" }, Nc.Sub, txtMain, txtSub)
            }
            Spacer(Modifier.height(10.dp))
        }
    }
}

@Composable
private fun FlagBadge(flag: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0xFF10182A))
            .border(2.dp, Color.White.copy(alpha = .85f), CircleShape)
    ) {
        Text(flag, fontSize = 21.sp)
    }
}

@Composable
private fun DetailRow(label: String, value: String, dotColor: Color, txtMain: Color, txtSub: Color) {
    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(9.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(7.dp))
            Text(label, color = txtSub, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(2.dp))
        Text(
            value, color = txtMain, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth(),
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

fun flagOf(code: String): String {
    val c = code.trim().uppercase()
    if (c.length != 2 || c.any { it !in 'A'..'Z' }) return ""
    return c.map { Character.toChars(0x1F1E6 + (it - 'A')) }.joinToString("") { String(it) }
}

private fun countryNameOf(code: String): String =
    runCatching { java.util.Locale("", code).displayCountry }.getOrNull().orEmpty()
