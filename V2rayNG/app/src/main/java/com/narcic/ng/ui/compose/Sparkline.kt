package com.narcic.ng.ui.compose

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * Live download-speed line chart, fed a rolling window of values (see
 * MainScreen's `speedHistory` buffer — 28 points, ~1 per notification tick).
 * Values are relative to each other (auto-scaled to the tallest point in
 * the window), not an absolute-units chart.
 */
@Composable
fun Sparkline(values: List<Float>, accent: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val max = (values.maxOrNull() ?: 1f).coerceAtLeast(1f)
        val step = size.width / (values.size - 1)
        val pts = values.mapIndexed { i, v ->
            Offset(i * step, size.height - (v / max) * size.height * .92f)
        }
        val line = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            pts.drop(1).forEach { lineTo(it.x, it.y) }
        }
        val fill = Path().apply {
            addPath(line)
            lineTo(size.width, size.height)
            lineTo(0f, size.height)
            close()
        }
        drawPath(fill, Brush.verticalGradient(listOf(accent.copy(alpha = .35f), Color.Transparent)))
        drawPath(line, accent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
    }
}
