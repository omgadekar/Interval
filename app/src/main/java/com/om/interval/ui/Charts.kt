package com.om.interval.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.om.interval.workout.Timeline
import com.om.interval.workout.Workout

/** Builds a smooth path through the points, breaking the line where values are missing. */
private fun smoothPath(points: List<Offset?>): Path {
    val path = Path()
    var prev: Offset? = null
    for (p in points) {
        if (p == null) {
            prev = null
            continue
        }
        val last = prev
        if (last == null) {
            path.moveTo(p.x, p.y)
        } else {
            val midX = (last.x + p.x) / 2
            path.cubicTo(midX, last.y, midX, p.y, p.x, p.y)
        }
        prev = p
    }
    return path
}

/**
 * Line graph with a soft gradient fill that draws itself in from the left.
 * [invert] puts small values at the top, which is how pace should read (faster is higher).
 */
@Composable
fun LineChart(
    values: List<Float?>,
    color: Color,
    modifier: Modifier = Modifier,
    invert: Boolean = false,
    animate: Boolean = true,
    strokeWidth: Dp = 2.5.dp,
) {
    val reveal = remember(values) { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(values) { if (animate) reveal.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    val present = values.filterNotNull()
    if (present.size < 2) return
    val min = present.min()
    val max = present.max()
    // A flat series (steady pace) sits in the middle instead of hugging the top.
    val flat = max - min < 0.5f
    val range = if (flat) 1f else max - min
    Canvas(modifier) {
        val pad = strokeWidth.toPx()
        val h = size.height - pad * 2
        val stepX = size.width / (values.size - 1).coerceAtLeast(1)
        val points = values.mapIndexed { i, v ->
            v?.let {
                val norm = if (flat) 0.5f else (it - min) / range
                Offset(i * stepX, pad + h * (if (invert) norm else 1f - norm))
            }
        }
        val line = smoothPath(points)
        clipRect(right = size.width * reveal.value) {
            // Fill each continuous run of points down to the baseline.
            var run = mutableListOf<Offset>()
            fun flush() {
                if (run.size >= 2) {
                    val fill = smoothPath(run).apply {
                        lineTo(run.last().x, size.height)
                        lineTo(run.first().x, size.height)
                        close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.38f), color.copy(alpha = 0f))))
                }
                run = mutableListOf()
            }
            points.forEach { p -> if (p == null) flush() else run.add(p) }
            flush()
            drawPath(line, color, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/** A titled graph card row: label on the left, current or average value on the right. */
@Composable
fun ChartBlock(title: String, value: String, color: Color, values: List<Float?>, invert: Boolean = false, low: String = "", high: String = "") {
    Column(Modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            Text(value, style = MaterialTheme.typography.labelMedium, color = color)
        }
        LineChart(values, color, Modifier.fillMaxWidth().height(54.dp).padding(top = 4.dp), invert = invert)
        if (low.isNotEmpty() || high.isNotEmpty()) {
            Row(Modifier.fillMaxWidth()) {
                Text(low, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(high, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End)
            }
        }
    }
}

/** Tiny live line, no fill animation; used for the heart-rate trail during a run. */
@Composable
fun Sparkline(values: List<Int>, color: Color, modifier: Modifier = Modifier) {
    LineChart(values.map { it.toFloat() }, color, modifier, animate = false, strokeWidth = 2.dp)
}

/**
 * The shape of a workout: one rounded bar per segment, width by duration and height by effort,
 * coloured cool (walk) to hot (stride). Bars grow in with a spring.
 */
@Composable
fun WorkoutShape(workout: Workout, goalSecPerKm: Int?, modifier: Modifier = Modifier, activeIndex: Int? = null) {
    val bars = remember(workout, goalSecPerKm) { Timeline.bars(workout, goalSecPerKm) }
    val grow = remember(workout) { Animatable(0f) }
    LaunchedEffect(workout) { grow.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessVeryLow)) }
    if (bars.isEmpty()) return
    val total = bars.sumOf { it.seconds }.coerceAtLeast(1.0)
    Canvas(modifier) {
        val gap = 2.dp.toPx()
        val usable = size.width - gap * (bars.size - 1)
        var x = 0f
        bars.forEachIndexed { i, bar ->
            val w = (usable * (bar.seconds / total)).toFloat().coerceAtLeast(1.5f)
            val h = size.height * (0.18f + 0.82f * bar.type.intensity) * grow.value
            val dim = activeIndex != null && i != activeIndex
            drawRoundRect(
                color = bar.type.color.copy(alpha = if (dim) 0.35f else 1f),
                topLeft = Offset(x, size.height - h),
                size = Size(w, h),
                cornerRadius = CornerRadius(3.dp.toPx()),
            )
            x += w + gap
        }
    }
}

/** Progress ring hugging the round screen edge. Restarts from zero each segment (keyed by [segment]). */
@Composable
fun EdgeRing(progress: Float, segment: Int, color: Color, modifier: Modifier = Modifier, width: Dp = 7.dp) {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(segment) { anim.snapTo(0f) }
    LaunchedEffect(segment, progress) { anim.animateTo(progress, tween(950, easing = LinearEasing)) }
    Canvas(modifier) {
        val stroke = width.toPx()
        val inset = stroke / 2 + 1.dp.toPx()
        val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
        drawArc(Palette.Track, -90f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        drawArc(color, -90f, 360f * anim.value, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
    }
}
/**
 * A live graph that glides left as each new second arrives, like a heart monitor. The newest
 * point slides in from the right edge with a soft glow; the scale eases rather than jumping.
 * [window] is how many seconds are visible.
 */
@Composable
fun LiveChart(
    values: List<Float?>,
    color: Color,
    modifier: Modifier = Modifier,
    invert: Boolean = false,
    window: Int = 90,
    minSpan: Float = 10f,
) {
    val phase = remember { Animatable(1f) }
    LaunchedEffect(values) {
        phase.snapTo(0f)
        phase.animateTo(1f, tween(980, easing = LinearEasing))
    }
    val visible = values.takeLast(window + 1)
    val present = visible.filterNotNull()
    val rawMin = present.minOrNull() ?: 0f
    val rawMax = present.maxOrNull() ?: 0f
    val mid = (rawMin + rawMax) / 2
    val span = maxOf(rawMax - rawMin, minSpan)
    val low by animateFloatAsState(mid - span / 2, tween(900), label = "low")
    val high by animateFloatAsState(mid + span / 2, tween(900), label = "high")
    Canvas(modifier) {
        val stroke = 2.5.dp.toPx()
        val baseY = size.height - stroke
        if (present.size < 2) {
            drawLine(color.copy(alpha = 0.25f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = stroke, cap = StrokeCap.Round)
            return@Canvas
        }
        val step = size.width / window
        val n = visible.size
        val range = (high - low).coerceAtLeast(0.001f)
        val pad = size.height * 0.12f
        val points = visible.mapIndexed { i, v ->
            v?.let {
                val norm = ((it - low) / range).coerceIn(0f, 1f)
                val y = pad + (size.height - pad * 2) * (if (invert) norm else 1f - norm)
                Offset(size.width - (n - 1 - i) * step + (1f - phase.value) * step, y)
            }
        }
        clipRect {
            var run = mutableListOf<Offset>()
            fun flush() {
                if (run.size >= 2) {
                    val fill = smoothPath(run).apply {
                        lineTo(run.last().x, baseY)
                        lineTo(run.first().x, baseY)
                        close()
                    }
                    drawPath(fill, Brush.verticalGradient(listOf(color.copy(alpha = 0.32f), color.copy(alpha = 0f))))
                }
                run = mutableListOf()
            }
            points.forEach { p -> if (p == null) flush() else run.add(p) }
            flush()
            drawPath(smoothPath(points), color, style = Stroke(stroke, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
        points.lastOrNull()?.let { p ->
            val x = p.x.coerceAtMost(size.width - 4.dp.toPx())
            drawCircle(color.copy(alpha = 0.25f), 6.dp.toPx(), Offset(x, p.y))
            drawCircle(color, 3.dp.toPx(), Offset(x, p.y))
        }
    }
}