package com.narcic.ng.ui.compose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.min
import kotlin.math.sin

/**
 * A regular N-sided polygon with smoothly rounded corners, used as the
 * "core" shape of the connect hero button. A hexagon reads as a distinct,
 * more deliberate/technical silhouette than a plain circle while still
 * nesting cleanly inside the circular orbit/pulse rings around it.
 *
 * @param sides number of polygon sides (6 = hexagon).
 * @param cornerRadius how much to round each vertex; automatically clamped
 * so corners never overlap on small sizes.
 * @param rotationDegrees rotates the whole polygon; -90 puts a flat edge
 * on top for hexagons, which reads as more "shield-like".
 */
class RoundedPolygonShape(
    private val sides: Int = 6,
    private val cornerRadius: Dp,
    private val rotationDegrees: Float = -90f,
) : Shape {

    override fun createOutline(
        size: Size,
        layoutDirection: LayoutDirection,
        density: Density,
    ): Outline {
        val radius = min(size.width, size.height) / 2f
        val center = Offset(size.width / 2f, size.height / 2f)
        val cornerRadiusPx = with(density) { cornerRadius.toPx() }.coerceAtMost(radius * 0.6f)

        val angleStep = (2.0 * Math.PI / sides)
        val startAngle = Math.toRadians(rotationDegrees.toDouble())

        val vertices = (0 until sides).map { i ->
            val angle = startAngle + i * angleStep
            Offset(
                x = center.x + radius * cos(angle).toFloat(),
                y = center.y + radius * sin(angle).toFloat(),
            )
        }

        val path = Path()
        for (i in vertices.indices) {
            val current = vertices[i]
            val prev = vertices[(i - 1 + sides) % sides]
            val next = vertices[(i + 1) % sides]

            val toPrev = Offset(prev.x - current.x, prev.y - current.y)
            val toPrevLen = hypot(toPrev.x, toPrev.y)
            val toNext = Offset(next.x - current.x, next.y - current.y)
            val toNextLen = hypot(toNext.x, toNext.y)

            val cutLen = min(cornerRadiusPx, min(toPrevLen, toNextLen) / 2f)

            val startPoint = Offset(
                current.x + (toPrev.x / toPrevLen) * cutLen,
                current.y + (toPrev.y / toPrevLen) * cutLen,
            )
            val endPoint = Offset(
                current.x + (toNext.x / toNextLen) * cutLen,
                current.y + (toNext.y / toNextLen) * cutLen,
            )

            if (i == 0) {
                path.moveTo(startPoint.x, startPoint.y)
            } else {
                path.lineTo(startPoint.x, startPoint.y)
            }
            path.quadraticBezierTo(current.x, current.y, endPoint.x, endPoint.y)
        }
        path.close()

        return Outline.Generic(path)
    }
}
