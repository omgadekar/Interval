package com.om.interval.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.ScalingLazyListScope
import androidx.wear.compose.foundation.lazy.ScalingLazyListState
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.ColorScheme
import androidx.wear.compose.material3.ListHeader
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.ScreenScaffold
import androidx.wear.compose.material3.Text
import com.om.interval.exercise.PaceStatus
import com.om.interval.workout.StepType

/** Palette: near-black surfaces with saturated accents, close to Apple's dark system colours. */
object Palette {
    val Accent = Color(0xFFFF7A45)
    val Blue = Color(0xFF0A84FF)
    val Cyan = Color(0xFF5AC8FA)
    val Teal = Color(0xFF64D2FF)
    val Green = Color(0xFF30D158)
    val Yellow = Color(0xFFFFD60A)
    val Amber = Color(0xFFFF9F0A)
    val Red = Color(0xFFFF375F)
    val Purple = Color(0xFFBF5AF2)
    val Surface = Color(0xFF1C1C1E)
    val SurfaceHigh = Color(0xFF2C2C2E)
    val Track = Color(0xFF2C2C2E)
    val Secondary = Color(0xFF98989F)
}

/** Effort colours run cool to hot, like heart-rate zones. */
val StepType.color: Color
    get() = when (this) {
        StepType.WALK -> Palette.Cyan
        StepType.RECOVER -> Palette.Teal
        StepType.JOG -> Palette.Green
        StepType.EASY -> Color(0xFF34C759)
        StepType.RUN -> Palette.Yellow
        StepType.TEMPO -> Palette.Amber
        StepType.FAST -> Palette.Accent
        StepType.STRIDE -> Palette.Red
    }

/** Background tint while running: on pace, too fast, too slow. */
val PaceStatus.tint: Color
    get() = when (this) {
        PaceStatus.ON -> Palette.Green
        PaceStatus.FAST -> Palette.Blue
        PaceStatus.SLOW -> Palette.Amber
        PaceStatus.NONE -> Color.Transparent
    }

private val colors = ColorScheme(
    primary = Palette.Accent,
    onPrimary = Color.Black,
    primaryContainer = Color(0xFF4A2215),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondary = Palette.Cyan,
    onSecondary = Color.Black,
    tertiary = Palette.Green,
    onTertiary = Color.Black,
    surfaceContainerLow = Color(0xFF121214),
    surfaceContainer = Palette.Surface,
    surfaceContainerHigh = Palette.SurfaceHigh,
    onSurface = Color.White,
    onSurfaceVariant = Palette.Secondary,
    background = Color.Black,
    onBackground = Color.White,
)

@Composable
fun IntervalTheme(content: @Composable () -> Unit) = MaterialTheme(colorScheme = colors, content = content)

/** Standard scrolling screen: scaffolded list with bezel scrolling and a scroll indicator. */
@Composable
fun ListScreen(
    state: ScalingLazyListState = rememberScalingLazyListState(),
    content: ScalingLazyListScope.() -> Unit,
) {
    ScreenScaffold(scrollState = state) { padding ->
        ScalingLazyColumn(
            state = state,
            contentPadding = padding,
            modifier = Modifier.fillMaxSize(),
            content = content,
        )
    }
}

fun ScalingLazyListScope.header(text: String) = item {
    ListHeader { Text(text, textAlign = TextAlign.Center) }
}

fun ScalingLazyListScope.paragraph(text: String, color: Color? = null) = item {
    Text(
        text,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        style = MaterialTheme.typography.bodySmall,
        color = color ?: MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
    )
}