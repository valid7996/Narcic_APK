package com.narcic.ng.ui.main

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.outlined.NetworkCheck
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.narcic.ng.ui.compose.GooLoader
import com.narcic.ng.ui.compose.LocalDarkTheme
import com.narcic.ng.ui.compose.Nc
import kotlin.math.cos
import kotlin.math.sin

/**
 * Big connect button per the two-engine redesign: idle=red power icon,
 * connecting=amber + [GooLoader] + fast spinning ring, connected=engine
 * accent + double outward ripple. `accent`/`accent2` come from
 * `accentFor(isAwg)` (Theme's DesignTokens) so the whole hero (ring, glow,
 * button gradient, "test connection" chip) recolors with the active engine.
 *
 * `isConnecting` is UI-local (MainScreen flips it true on tap and clears it
 * once `isRunning` actually turns true, or after a timeout) since the real
 * ViewModel doesn't expose a dedicated "handshaking" state.
 */
@Composable
fun ConnectHero(
    isRunning: Boolean,
    isConnecting: Boolean,
    isTesting: Boolean,
    statusText: String,
    timeText: String,
    accent: Color,
    accent2: Color,
    onToggle: () -> Unit,
    onCheckConnection: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isDark = LocalDarkTheme.current
    val txtMain = if (isDark) Nc.Txt else Color(0xFF1B2230)
    val txtSub = if (isDark) Nc.Sub else Color(0xFF6B7688)
    val statusColor = when {
        isRunning -> accent
        isConnecting -> Nc.StateConnecting
        else -> txtSub
    }

    Column(modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {

        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(230.dp)) {

            // ── outward double ripple, only while actually connected ──
            if (isRunning) repeat(2) { idx ->
                val p by rememberInfiniteTransition(label = "rip$idx").animateFloat(
                    initialValue = 0f, targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        tween(2600, easing = LinearEasing),
                        initialStartOffset = StartOffset(idx * 1300),
                    ), label = "ripP$idx"
                )
                Box(
                    Modifier
                        .size(202.dp)
                        .graphicsLayer {
                            val s = 0.62f + p * 0.83f
                            scaleX = s; scaleY = s
                            alpha = (0.55f * (1f - p)).coerceIn(0f, 1f)
                        }
                        .border(1.5.dp, accent, CircleShape)
                )
            }

            // ── pulsing glow halo ──
            if (isRunning || isConnecting) {
                val glowColor = if (isConnecting) Nc.StateConnecting else accent
                val glow by rememberInfiniteTransition(label = "glow").animateFloat(
                    1f, 1.12f,
                    infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "glowS"
                )
                val glowAlpha by rememberInfiniteTransition(label = "glowA").animateFloat(
                    .85f, .5f,
                    infiniteRepeatable(tween(1100, easing = FastOutSlowInEasing), RepeatMode.Reverse),
                    label = "glowAl"
                )
                Box(
                    Modifier
                        .size(210.dp)
                        .graphicsLayer { scaleX = glow; scaleY = glow; alpha = glowAlpha }
                        .drawBehind {
                            drawCircle(
                                Brush.radialGradient(
                                    listOf(glowColor.copy(alpha = .30f), Color.Transparent)
                                )
                            )
                        }
                )
            }

            // ── spinning gradient ring — fast while connecting, slow while connected, static idle ──
            val ringAngle by rememberInfiniteTransition(label = "ring").animateFloat(
                0f, 360f,
                infiniteRepeatable(tween(if (isConnecting) 900 else 7000, easing = LinearEasing)),
                label = "ringA"
            )
            Box(
                Modifier
                    .size(198.dp)
                    .graphicsLayer { rotationZ = ringAngle }
                    .drawBehind {
                        drawCircle(
                            brush = Brush.sweepGradient(
                                listOf(Color.Transparent, accent2.copy(alpha = .9f), accent, Color.Transparent)
                            ),
                            style = Stroke(3.5.dp.toPx()),
                            alpha = if (isConnecting || isRunning) 1f else 0.3f,
                        )
                    }
            )

            // ── main button ──
            val interaction = remember { MutableInteractionSource() }
            val pressed by interaction.collectIsPressedAsState()
            val btnScale by animateFloatAsState(
                if (pressed) 0.93f else 1f,
                spring(dampingRatio = Spring.DampingRatioMediumBouncy), label = "btnS"
            )

            val btnColors = when {
                isRunning -> listOf(accent.copy(alpha = .8f), accent, accent2.copy(alpha = .7f))
                isConnecting -> listOf(Color(0xFFFCD34D), Color(0xFFF59E0B), Color(0xFFB45309))
                else -> listOf(Color(0xFFFB7185), Color(0xFFE11D48), Color(0xFFBE123C))
            }

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(158.dp)
                    .graphicsLayer { scaleX = btnScale; scaleY = btnScale }
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(btnColors))
                    .drawBehind {
                        // top-left glassy highlight
                        drawCircle(
                            Brush.radialGradient(
                                colors = listOf(Color.White.copy(alpha = .28f), Color.Transparent),
                                center = Offset(size.width * .32f, size.height * .22f),
                                radius = size.width * .55f
                            )
                        )
                    }
                    .clickable(interactionSource = interaction, indication = null, onClick = onToggle)
            ) {
                if (isConnecting) {
                    GooLoader(size = 96.dp, color = Color.White)
                } else {
                    // Spider-on-web emblem from the connection-screen prototype,
                    // tinted with the active engine accent instead of the old
                    // power icon. Purely visual: same tap target, same states.
                    Box(Modifier.size(96.dp).drawBehind { drawSpiderEmblem(accent) })
                }
            }
        }

        Spacer(Modifier.height(14.dp))
        Text(
            timeText,
            color = if (isRunning) txtMain else txtSub,
            fontSize = if (isRunning) 16.sp else 15.sp,
            fontWeight = FontWeight.Bold,
        )
        Text(
            statusText,
            color = statusColor,
            fontSize = 12.5.sp,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(top = 4.dp)
        )

        if (isRunning) {
            Spacer(Modifier.height(14.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = if (isDark) .06f else .9f))
                    .border(1.dp, Color.White.copy(alpha = .14f), RoundedCornerShape(50))
                    .clickable(onClick = onCheckConnection)
                    .padding(horizontal = 18.dp, vertical = 9.dp)
            ) {
                Icon(Icons.Outlined.NetworkCheck, null, tint = accent, modifier = Modifier.size(14.dp))
                Text(
                    if (isTesting) "در حال تست..." else "تست اتصال",
                    color = txtMain, fontSize = 12.sp, fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/**
 * Spider-on-web emblem decoded from the connection-screen prototype, redrawn
 * as vectors so it scales cleanly. Prototype geometry (1:1, 850x910 frame,
 * emblem center ~(313,313) in its half-scale wireframe):
 *  - thick full ring, r 175→212 (29 units thick) with a rounded outer rim;
 *  - three interior arcs hugging the ring's inner edge (r≈158), spanning
 *    40°-115°, 140°-215° and 240°-315°;
 *  - dense radial threads in the four ring-gap sectors (centered ~75°, 165°,
 *    275°, 352°) fanning from the body outward;
 *  - three thin concentric web arcs at r 55/85/120 on the thread-free side;
 *  - a filled spider: tilted teardrop abdomen from the hub toward ~135°,
 *    a small round head at the hub, four thick tapering legs (three wrapping
 *    clockwise along the ring's inner edge, one free across the lower web).
 * Angles below are measured clockwise from 3 o'clock to match atan2 with y
 * increasing downward.
 */
private fun DrawScope.drawSpiderEmblem(accent: Color) {
    val S = size.width / 420f // prototype half-scale units -> canvas
    val cx = size.width / 2f
    val cy = size.height / 2f
    fun pt(r: Float, adeg: Float): Offset =
        Offset(cx + r * S * cos(Math.toRadians(adeg.toDouble())).toFloat(),
               cy + r * S * sin(Math.toRadians(adeg.toDouble())).toFloat())

    val bodyColor = Color.White
    val webColor = accent.copy(alpha = 0.55f)
    val roundCap = StrokeCap.Round

    // ── thick ring (r 175→212) + thin outer rim ──
    drawCircle(color = bodyColor, radius = 193.5f * S, style = Stroke(37 * S))
    drawCircle(color = bodyColor, radius = 230.5f * S, style = Stroke(3 * S))

    // ── three interior arcs at r≈158, hugging the ring's inner edge ──
    val arcStroke = Stroke(width = 13 * S, cap = roundCap)
    listOf(40f to 115f, 140f to 215f, 240f to 315f).forEach { (start, end) ->
        drawArc(
            color = bodyColor,
            startAngle = start - 6.5f,
            sweepAngle = (end - start) + 13f,
            useCenter = false,
            topLeft = Offset(cx - 158 * S, cy - 158 * S),
            size = Size(316 * S, 316 * S),
            style = arcStroke,
        )
    }

    // ── dense radial threads in the four ring-gap sectors ──
    val threadSectors = listOf(45f to 105f, 135f to 195f, 245f to 305f, 325f to 383f)
    threadSectors.forEach { (from, to) ->
        var a = from
        while (a <= to + 0.1f) {
            drawLine(
                color = webColor,
                start = pt(70f, a),
                end = pt(172f, a),
                strokeWidth = 2.5f * S,
            )
            a += 6f
        }
    }

    // ── three thin concentric web arcs on the thread-free side ──
    listOf(55f, 85f, 120f).forEach { r ->
        drawArc(
            color = webColor,
            startAngle = -130f,
            sweepAngle = 78f,
            useCenter = false,
            topLeft = Offset(cx - r * S, cy - r * S),
            size = Size(2 * r * S, 2 * r * S),
            style = Stroke(2.5f * S, cap = roundCap),
        )
    }

    // ── spider body: tilted teardrop abdomen (hub → ~135°, i.e. up-left) + head ──
    val body = androidx.compose.ui.graphics.Path().apply {
        val tip = pt(158f, 135f)
        val mid = pt(85f, 135f)
        val nx = cos(Math.toRadians(45.0)).toFloat()
        val ny = sin(Math.toRadians(45.0)).toFloat()
        val half = 34f * S
        moveTo(pt(12f, 135f).x, pt(12f, 135f).y)
        quadraticBezierTo(mid.x + nx * half, mid.y + ny * half, tip.x, tip.y)
        quadraticBezierTo(mid.x - nx * half, mid.y - ny * half, pt(12f, 135f).x, pt(12f, 135f).y)
        close()
    }
    drawPath(body, bodyColor)
    drawCircle(color = bodyColor, radius = 21 * S, center = pt(0f, 0f))

    // ── four thick tapering legs ──
    val legStroke = Stroke(width = 13 * S, cap = roundCap)
    // three legs wrapping clockwise along the ring's inner edge (r≈137)
    listOf(165f to 243f, 205f to 285f, 245f to 322f).forEach { (from, to) ->
        drawArc(
            color = bodyColor,
            startAngle = from,
            sweepAngle = to - from,
            useCenter = false,
            topLeft = Offset(cx - 137 * S, cy - 137 * S),
            size = Size(274 * S, 274 * S),
            style = legStroke,
        )
    }
    // one free leg sweeping across the lower web toward the lower-left gap
    drawArc(
        color = bodyColor,
        startAngle = 122f,
        sweepAngle = 50f,
        useCenter = false,
        topLeft = Offset(cx - 108 * S, cy - 108 * S),
        size = Size(216 * S, 216 * S),
        style = legStroke,
    )
}
