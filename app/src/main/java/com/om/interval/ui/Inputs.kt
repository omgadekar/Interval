package com.om.interval.ui

import android.app.RemoteInput
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.foundation.lazy.ScalingLazyColumn
import androidx.wear.compose.foundation.lazy.items
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.Dialog
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.PickerGroup
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.rememberPickerState
import androidx.wear.input.RemoteInputIntentHelper
import com.om.interval.workout.StepType

/** A dot in a step type's colour, used as the icon on step rows. */
@Composable
fun TypeDot(type: StepType, size: Int = 10) {
    Canvas(Modifier.size(size.dp)) { drawCircle(type.color) }
}

/** One scrolling wheel in a [WheelDialog]. */
data class Wheel(val count: Int, val initial: Int, val description: String, val label: (Int) -> String)

/**
 * Full-screen picker: one or more wheels side by side, turned with a finger or the bezel.
 * Tap a wheel to give it the bezel; Done returns the selected index of each wheel.
 */
@Composable
fun WheelDialog(
    visible: Boolean,
    title: String,
    wheels: List<Wheel>,
    separator: String = "",
    onDismiss: () -> Unit,
    onConfirm: (List<Int>) -> Unit,
) {
    Dialog(visible = visible, onDismissRequest = onDismiss) {
        // Fresh wheel state each time the dialog opens.
        key(visible) {
            val states = wheels.map { w -> rememberPickerState(w.count, w.initial.coerceIn(0, w.count - 1), true) }
            var selected by remember { mutableIntStateOf(0) }
            Column(
                Modifier.fillMaxSize().padding(top = 26.dp, bottom = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                PickerGroup(
                    selectedPickerState = states[selected],
                    modifier = Modifier.height(110.dp),
                    autoCenter = false,
                ) {
                    wheels.forEachIndexed { i, w ->
                        if (i > 0 && separator.isNotEmpty()) {
                            Text(separator, style = MaterialTheme.typography.numeralMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PickerGroupItem(
                            pickerState = states[i],
                            selected = selected == i,
                            onSelected = { selected = i },
                            modifier = Modifier.width(if (w.count > 99) 74.dp else 58.dp).height(110.dp),
                            contentDescription = { w.description },
                            option = { index, _ ->
                                Text(w.label(index), style = MaterialTheme.typography.numeralMedium, textAlign = TextAlign.Center)
                            },
                        )
                    }
                }
                Button(
                    onClick = { onConfirm(states.map { it.selectedOptionIndex }) },
                    label = { Text("Done", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    modifier = Modifier.width(110.dp),
                )
            }
        }
    }
}

/** Minutes and seconds (5 s steps), up to 3 hours. */
fun durationWheels(seconds: Int) = listOf(
    Wheel(181, seconds / 60, "Minutes") { "%d".format(it) },
    Wheel(12, (seconds % 60) / 5, "Seconds") { "%02d".format(it * 5) },
)

fun durationFrom(indexes: List<Int>) = (indexes[0] * 60 + indexes[1] * 5).coerceAtLeast(5)

/** Kilometres and 50 m steps, up to 42.95 km. */
fun distanceWheels(meters: Double) = listOf(
    Wheel(43, (meters / 1000).toInt(), "Kilometres") { "$it" },
    Wheel(20, ((meters % 1000) / 50).toInt(), "Metres") { "%02d".format(it * 5) },
)

fun distanceFrom(indexes: List<Int>) = (indexes[0] * 1000.0 + indexes[1] * 50.0).coerceAtLeast(50.0)

/** Pace from 2:00 to 15:55 per km. */
fun paceWheels(secPerKm: Int) = listOf(
    Wheel(14, (secPerKm / 60 - 2).coerceIn(0, 13), "Minutes per km") { "${it + 2}" },
    Wheel(12, (secPerKm % 60) / 5, "Seconds") { "%02d".format(it * 5) },
)

fun paceFrom(indexes: List<Int>) = (indexes[0] + 2) * 60 + indexes[1] * 5

/** A list of choices in a dialog, each with an optional coloured dot. */
@Composable
fun <T> ChoiceDialog(
    visible: Boolean,
    title: String,
    choices: List<T>,
    label: (T) -> String,
    dot: ((T) -> Color)? = null,
    onDismiss: () -> Unit,
    onChoose: (T) -> Unit,
) {
    Dialog(visible = visible, onDismissRequest = onDismiss) {
        val state = rememberScalingLazyListState()
        ScalingLazyColumn(state = state, modifier = Modifier.fillMaxSize()) {
            item { Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(choices) { c ->
                Button(
                    onClick = { onChoose(c) },
                    colors = ButtonDefaults.filledTonalButtonColors(),
                    icon = dot?.let { d -> { Canvas(Modifier.size(12.dp)) { drawCircle(d(c)) } } },
                    label = { Text(label(c)) },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }
}

private const val TEXT_KEY = "text"

/** Opens the watch keyboard / voice input and hands back what you typed. */
@Composable
fun rememberTextInput(onText: (String) -> Unit): (String) -> Unit {
    val latest by rememberUpdatedState(onText)
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val text = result.data?.let { RemoteInput.getResultsFromIntent(it) }?.getCharSequence(TEXT_KEY)?.toString()?.trim()
        if (!text.isNullOrEmpty()) latest(text)
    }
    return { label ->
        val intent = RemoteInputIntentHelper.createActionRemoteInputIntent()
        RemoteInputIntentHelper.putRemoteInputsExtra(intent, listOf(RemoteInput.Builder(TEXT_KEY).setLabel(label).build()))
        launcher.launch(intent)
    }
}

/** Two or three small buttons side by side, one of which is highlighted. */
@Composable
fun SegmentedChoice(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        options.forEachIndexed { i, label ->
            Button(
                onClick = { onSelect(i) },
                colors = if (i == selected) ButtonDefaults.buttonColors() else ButtonDefaults.filledTonalButtonColors(),
                label = { Text(label, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, maxLines = 1) },
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 4.dp),
            )
        }
    }
}