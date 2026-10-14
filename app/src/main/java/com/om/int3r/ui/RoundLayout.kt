package com.om.int3r.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * A vertically centred column for round screens. Each child gets exactly the width the circle allows
 * at its height, inside [edgeInset] (the progress ring plus a gap), so lines near the top and bottom
 * are narrower and nothing runs into the ring.
 */
@Composable
fun RoundColumn(
    modifier: Modifier = Modifier,
    spacing: Dp = 4.dp,
    edgeInset: Dp = 14.dp,
    content: @Composable () -> Unit,
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val w = constraints.maxWidth
        val h = constraints.maxHeight
        val gap = spacing.roundToPx()
        val radius = minOf(w, h) / 2f - edgeInset.toPx()
        val cy = h / 2f
        val heights = measurables.map { it.minIntrinsicHeight(w) }
        val total = heights.sum() + gap * (measurables.size - 1).coerceAtLeast(0)
        var y = (h - total) / 2
        val placeables = measurables.mapIndexed { i, m ->
            val top = y.toFloat()
            val bottom = top + heights[i]
            // The narrower of the child's top and bottom edge decides its width.
            val d = max(abs(top - cy), abs(bottom - cy))
            val width = (2 * sqrt(max(radius * radius - d * d, 0f))).toInt().coerceIn(0, w)
            y += heights[i] + gap
            m.measure(Constraints.fixed(width, heights[i]))
        }
        layout(w, h) {
            var py = (h - total) / 2
            placeables.forEach { p ->
                p.place((w - p.width) / 2, py)
                py += p.height + gap
            }
        }
    }
}