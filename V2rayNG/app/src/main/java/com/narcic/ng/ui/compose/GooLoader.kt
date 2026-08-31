package com.narcic.ng.ui.compose

import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

private data class GooArm(val periodSec: Float, val dir: Int, val dist: Float, val r: Float)

// Timings/positions per the design spec (viewBox 64): 4 arms with distinct
// orbit periods + a breathing core, blurred+thresholded into one gooey blob.
private val GOO_ARMS = listOf(
    GooArm(2.8f, 1, 11.0f, 7.0f),
    GooArm(3.6f, -1, 14.5f, 5.5f),
    GooArm(2.2f, 1, 9.0f, 4.6f),
    GooArm(4.4f, -1, 16.0f, 4.0f),
)

/**
 * Metaball ("gooey") loader used inside the connect button while connecting
 * and inside [GooOverlay] for full-screen busy states (testing servers,
 * refreshing subscriptions).
 *
 * On API 31+ this uses a real blur + alpha-threshold RenderEffect so the
 * core and orbiting arms visually merge/split like a liquid blob. On older
 * devices it falls back to plain (non-merging) circles — still readable,
 * just without the metaball fusion effect.
 */
@Composable
fun GooLoader(
    modifier: Modifier = Modifier,
    size: Dp = 96.dp,
    color: Color = Color.White,
) {
    var elapsed by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(Unit) {
        var start = 0L
        while (true) {
            withFrameNanos { now ->
                if (start == 0L) start = now
                elapsed = (now - start) / 1_000_000_000f
            }
        }
    }
    val gooEffect = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val alphaThreshold = ColorMatrix(
                floatArrayOf(
                    1f, 0f, 0f, 0f, 0f,
                    0f, 1f, 0f, 0f, 0f,
                    0f, 0f, 1f, 0f, 0f,
                    0f, 0f, 0f, 19f, -8f,
                )
            )
            val blur: RenderEffect = RenderEffect.createBlurEffect(6f, 6f, Shader.TileMode.CLAMP)
            val threshold: RenderEffect =
                RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(alphaThreshold))
            RenderEffect.createChainEffect(threshold, blur).asComposeRenderEffect()
        } else null
    }

    Canvas(modifier.size(size).graphicsLayer { renderEffect = gooEffect }) {
        val s: Float = this.size.minDimension / 64f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)

        fun armDot(angleDeg: Float, dist: Float, r: Float) {
            val a = Math.toRadians(angleDeg.toDouble())
            drawCircle(
                color = color,
                radius = r * s,
                center = Offset(
                    c.x + (dist * sin(a)).toFloat() * s,
                    c.y - (dist * cos(a)).toFloat() * s
                )
            )
        }
        // Breathing core: scale .82<->1.05 over 3.2s.
        val breathe: Float = 0.935f - 0.115f * cos((elapsed / 3.2f) * (2.0 * Math.PI).toFloat())
        drawCircle(color = color, radius = 9.5f * s * breathe, center = c)
        GOO_ARMS.forEach { arm ->
            armDot(arm.dir * 360f * (elapsed / arm.periodSec), arm.dist, arm.r)
        }
        // Faint outer ring.
        drawCircle(
            color = color.copy(alpha = 0.14f),
            radius = 30f * s,
            center = c,
            style = Stroke(width = 1.dp.toPx())
        )
    }
}
