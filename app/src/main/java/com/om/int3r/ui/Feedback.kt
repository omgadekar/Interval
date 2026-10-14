package com.om.int3r.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.wear.compose.material3.ConfirmationDialogDefaults
import androidx.wear.compose.material3.SuccessConfirmationDialog
import androidx.wear.compose.material3.confirmationDialogCurvedText
import kotlinx.coroutines.delay

/**
 * Lives as long as an editor screen's back-stack entry. A new step or repeat is added to the
 * workout as soon as its editor opens; if you leave without tapping Add, it is removed again,
 * so swiping back works as Cancel.
 */
class DraftGuard : ViewModel() {
    var discard: (() -> Unit)? = null
    var confirmed = false

    override fun onCleared() {
        if (!confirmed) discard?.invoke()
    }
}

/** Route key for "this step or block just changed", passed back so the outline can point at it. */
fun flashKey(block: Int, inner: Int = -1) = "$block,$inner"

/**
 * A just-added or just-saved row announces itself: a green outline that fades out and a small
 * bounce, so you can see where it landed.
 */
@Composable
fun Modifier.flash(active: Boolean): Modifier {
    val glow = remember { Animatable(0f) }
    LaunchedEffect(active) {
        if (active) {
            delay(250)
            glow.snapTo(1f)
            // Hold long enough to be noticed, then fade.
            delay(700)
            glow.animateTo(0f, tween(1200, easing = FastOutSlowInEasing))
        }
    }
    val g = glow.value
    return this
        .graphicsLayer {
            val s = 1f + 0.035f * g
            scaleX = s
            scaleY = s
        }
        .border(2.dp, Palette.Green.copy(alpha = g), RoundedCornerShape(percent = 50))
}

/** The standard Wear check-mark confirmation, finishing after about a second. */
@Composable
fun DoneConfirmation(visible: Boolean, text: String, onDone: () -> Unit) {
    val style = ConfirmationDialogDefaults.curvedTextStyle
    SuccessConfirmationDialog(
        visible = visible,
        onDismissRequest = onDone,
        curvedText = { confirmationDialogCurvedText(text, style) },
        durationMillis = 1200,
    )
}