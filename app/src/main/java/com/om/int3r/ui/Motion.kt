package com.om.int3r.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Row
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Soft entrance: fades and rises into place, staggered by [index]. */
@Composable
fun Modifier.appear(index: Int = 0): Modifier {
    val alpha = remember { Animatable(0f) }
    val offset = remember { Animatable(18f) }
    LaunchedEffect(Unit) {
        delay(index * 55L)
        launch { alpha.animateTo(1f, tween(420, easing = FastOutSlowInEasing)) }
        offset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessLow))
    }
    return graphicsLayer {
        this.alpha = alpha.value
        translationY = offset.value * density
    }
}

/**
 * Text whose characters roll like an odometer when they change. Characters are keyed from the right,
 * so "10:00" -> "9:59" rolls the digits that changed and leaves the colon still.
 */
@Composable
fun RollingText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    rollDown: Boolean = true,
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        text.forEachIndexed { i, ch ->
            androidx.compose.runtime.key(text.length - i) {
                AnimatedContent(
                    targetState = ch,
                    // Clip to the digit's own window, like an odometer, so rolling digits never overlap other text.
                    modifier = Modifier.clipToBounds(),
                    transitionSpec = {
                        val dir = if (rollDown) -1 else 1
                        (slideInVertically(tween(260)) { h -> dir * h } + fadeIn(tween(220)))
                            .togetherWith(slideOutVertically(tween(260)) { h -> -dir * h } + fadeOut(tween(180)))
                    },
                    label = "digit",
                ) { c -> Text(c.toString(), style = style, color = color) }
            }
        }
    }
}

/** A number that counts up from zero the first time it is shown. */
@Composable
fun rememberCountUp(target: Float, durationMs: Int = 900): Float {
    val anim = remember { Animatable(0f) }
    LaunchedEffect(target) { anim.animateTo(target, tween(durationMs, easing = FastOutSlowInEasing)) }
    return anim.value
}
/**
 * One line of text that shrinks (down to [minSize]) rather than truncating, for the narrow
 * top and bottom lines of a round screen.
 */
@Composable
fun FitText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minSize: androidx.compose.ui.unit.TextUnit = 10.sp,
) {
    androidx.compose.foundation.text.BasicText(
        text = text,
        modifier = modifier,
        style = style.copy(color = color, textAlign = androidx.compose.ui.text.style.TextAlign.Center),
        maxLines = 1,
        overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
        autoSize = androidx.compose.foundation.text.TextAutoSize.StepBased(minFontSize = minSize, maxFontSize = style.fontSize, stepSize = 0.5.sp),
    )
}