package com.om.int3r.ui

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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
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
import com.om.int3r.record.Trail
import com.om.int3r.workout.Timeline
import com.om.int3r.workout.Workout

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
 * A live graph that glides left at a steady speed, like a heart monitor. Position comes from each
 * reading's own time and the screen's frame clock, so it never jumps when data arrives, and readings
 * delivered late (screen was off) appear where they belong. The scale moves in whole [quantum] steps
 * and eases, so it doesn't wobble.
 */
@Composable
fun LiveChart(
    trail: Trail,
    color: Color,
    modifier: Modifier = Modifier,
    invert: Boolean = false,
    windowSec: Int = 60,
    minSpan: Float = 10f,
    quantum: Float = 5f,
) {
    val now by produceState(System.currentTimeMillis()) {
        while (true) withFrameMillis { value = System.currentTimeMillis() }
    }
    val windowMs = windowSec * 1000L
    // Newest second is still filling up; show it once it is complete, sliding in from the right edge.
    val lagMs = 1500L
    val inView = trail.values.indices.filter { i -> trail.values[i] != null && now - lagMs - trail.timeOf(i) <= windowMs + 2_000 }
    val present = inView.map { trail.values[it]!! }
    val rawMin = present.minOrNull() ?: 0f
    val rawMax = present.maxOrNull() ?: 0f
    val mid = (rawMin + rawMax) / 2
    val half = maxOf(rawMax - rawMin, minSpan) / 2
    val low by animateFloatAsState(kotlin.math.floor((mid - half) / quantum) * quantum, tween(1200), label = "low")
    val high by animateFloatAsState(kotlin.math.ceil((mid + half) / quantum) * quantum, tween(1200), label = "high")
    Canvas(modifier) {
        val stroke = 2.5.dp.toPx()
        val baseY = size.height - stroke
        if (present.size < 2) {
            drawLine(color.copy(alpha = 0.25f), Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = stroke, cap = StrokeCap.Round)
            return@Canvas
        }
        val range = (high - low).coerceAtLeast(0.001f)
        val pad = size.height * 0.12f
        fun x(t: Long) = size.width * (1f - (now - lagMs - t).toFloat() / windowMs)
        fun y(v: Float): Float {
            val norm = ((v - low) / range).coerceIn(0f, 1f)
            return pad + (size.height - pad * 2) * (if (invert) norm else 1f - norm)
        }
        val first = inView.first()
        val points = (first..inView.last()).map { i -> trail.values[i]?.let { Offset(x(trail.timeOf(i)), y(it)) } }
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
        points.lastOrNull { it != null }?.let { p ->
            val px = p.x.coerceAtMost(size.width - 4.dp.toPx())
            drawCircle(color.copy(alpha = 0.25f), 6.dp.toPx(), Offset(px, p.y))
            drawCircle(color, 3.dp.toPx(), Offset(px, p.y))
        }
    }
}
