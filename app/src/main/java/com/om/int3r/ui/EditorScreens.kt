package com.om.int3r.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.wear.compose.foundation.lazy.rememberScalingLazyListState
import androidx.wear.compose.material3.EdgeButton
import androidx.wear.compose.material3.EdgeButtonSize
import kotlinx.coroutines.delay
import androidx.wear.compose.material3.Button
import androidx.wear.compose.material3.ButtonDefaults
import androidx.wear.compose.material3.CompactButton
import androidx.wear.compose.material3.FilledTonalButton
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.OutlinedButton
import androidx.wear.compose.material3.SwitchButton
import androidx.wear.compose.material3.Text
import com.om.int3r.data.LibraryStore
import com.om.int3r.data.Prefs
import com.om.int3r.workout.Block
import com.om.int3r.workout.Describe
import com.om.int3r.workout.Edit
import com.om.int3r.workout.End
import com.om.int3r.workout.PaceTarget
import com.om.int3r.workout.Repeat
import com.om.int3r.workout.RepeatUntilDistance
import com.om.int3r.workout.SavedWorkout
import com.om.int3r.workout.Step
import com.om.int3r.workout.StepPath
import com.om.int3r.workout.StepType
import com.om.int3r.workout.Timeline
import com.om.int3r.workout.Workout
import com.om.int3r.workout.formatDistance
import com.om.int3r.workout.formatPace

/** Reads one saved workout and gives back an editor that saves every change immediately. */
@Composable
private fun rememberWorkout(id: String): Pair<SavedWorkout?, ((Workout) -> Workout) -> Unit> {
    val store = LibraryStore.get(LocalContext.current)
    val library by store.library.collectAsStateWithLifecycle()
    val saved = library.workout(id)
    val edit: ((Workout) -> Workout) -> Unit = { change ->
        store.update { lib -> lib.workout(id)?.let { lib.upsert(it.copy(workout = change(it.workout))) } ?: lib }
    }
    return saved to edit
}

@Composable
private fun goalPace(): Int? {
    val settings by Prefs.get(LocalContext.current).settings.collectAsStateWithLifecycle()
    return settings.goalSecPerKm
}

private fun stepDetail(step: Step, goal: Int?): String =
    listOfNotNull(Describe.end(step.end), Describe.target(step.target, goal)).joinToString(" ")

@Composable
private fun StepRow(step: Step, goal: Int?, indent: Boolean = false, flash: Boolean = false, onClick: () -> Unit) {
    val modifier = Modifier.fillMaxWidth().then(if (indent) Modifier.padding(start = 14.dp) else Modifier).flash(flash)
    val label: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        Text(step.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    val secondary: @Composable androidx.compose.foundation.layout.RowScope.() -> Unit = {
        Text(stepDetail(step, goal), maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    if (indent) {
        OutlinedButton(onClick = onClick, icon = { TypeDot(step.type) }, label = label, secondaryLabel = secondary, modifier = modifier)
    } else {
        FilledTonalButton(onClick = onClick, icon = { TypeDot(step.type) }, label = label, secondaryLabel = secondary, modifier = modifier)
    }
}

private fun blockTitle(block: Block): String = when (block) {
    is Repeat -> "Repeat ${block.times}×"
    is RepeatUntilDistance -> "Repeat to ${formatDistance(block.meters)}"
    is Step -> block.label
}

/** The outline of a workout: every block in order, plus buttons to add more. */
@Composable
fun EditorScreen(id: String, open: (String) -> Unit, back: () -> Unit, flash: String = "", onFlashShown: () -> Unit = {}) {
    val (saved, edit) = rememberWorkout(id)
    val store = LibraryStore.get(LocalContext.current)
    val goal = goalPace()
    if (saved == null) {
        ListScreen { paragraph("This workout was deleted.") }
        return
    }
    val rename = rememberTextInput { name -> store.update { it.upsert(saved.copy(name = name)) } }
    val w = saved.workout
    var saving by remember { mutableStateOf(false) }

    // Where each step sits in the list, so a just-added one can be scrolled into view.
    val rowIndex = remember(w) {
        buildMap {
            var i = 3
            w.blocks.forEachIndexed { b, block ->
                put(flashKey(b), i++)
                Edit.innerSteps(block)?.indices?.forEach { j -> put(flashKey(b, j), i++) }
            }
        }
    }
    val state = rememberScalingLazyListState()
    LaunchedEffect(flash) {
        val index = rowIndex[flash] ?: return@LaunchedEffect
        state.animateScrollToItem(index)
        delay(2_000)
        onFlashShown()
    }

    ListScreen(
        state = state,
        edgeButton = {
            EdgeButton(onClick = { saving = true }, buttonSize = EdgeButtonSize.Medium, colors = Buttons.primary()) {
                Text("Done")
            }
        },
    ) {
        item {
            Button(
                onClick = { rename("Workout name") },
                colors = ButtonDefaults.filledTonalButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                label = { Text(saved.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                secondaryLabel = { Text("Tap to rename") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            WorkoutShape(w, goal, Modifier.fillMaxWidth().height(34.dp).padding(horizontal = 12.dp, vertical = 4.dp))
        }
        if (w.blocks.isNotEmpty()) {
            paragraph("≈ ${Math.round(Timeline.totalSeconds(w, goal) / 60.0)} min")
        } else {
            paragraph("Empty so far. Add a run or walk below.")
        }
        w.blocks.forEachIndexed { i, block ->
            when (block) {
                is Step -> item { StepRow(block, goal, flash = flash == flashKey(i)) { open(Routes.step(id, i, -1)) } }
                is Repeat, is RepeatUntilDistance -> {
                    item {
                        Button(
                            onClick = { open(Routes.block(id, i)) },
                            label = { Text(blockTitle(block)) },
                            secondaryLabel = {
                                Text(
                                    if (block is Repeat && block.skipLastOnFinal > 0) "Last recovery skipped" else "Tap to edit",
                                    maxLines = 1,
                                )
                            },
                            modifier = Modifier.fillMaxWidth().flash(flash == flashKey(i)),
                        )
                    }
                    Edit.innerSteps(block).orEmpty().forEachIndexed { j, step ->
                        item { StepRow(step, goal, indent = true, flash = flash == flashKey(i, j)) { open(Routes.step(id, i, j)) } }
                    }
                }
            }
        }
        item {
            FilledTonalButton(
                onClick = {
                    edit { Edit.add(it, Edit.newStep()) }
                    open(Routes.step(id, w.blocks.size, -1, isNew = true))
                },
                label = { Text("Add run or walk") },
                secondaryLabel = { Text("Choose the effort and how long") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            FilledTonalButton(
                onClick = {
                    edit { Edit.add(it, Edit.newRepeat()) }
                    open(Routes.block(id, w.blocks.size, isNew = true))
                },
                label = { Text("Add a repeat set") },
                secondaryLabel = { Text("e.g. 6 × run 3 min, walk 1 min") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            FilledTonalButton(
                onClick = {
                    edit { Edit.add(it, Edit.newRepeatUntil()) }
                    open(Routes.block(id, w.blocks.size, isNew = true))
                },
                label = { Text("Add repeat until distance") },
                secondaryLabel = { Text("e.g. run 10 / walk 1 until 4 km") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
    var left by remember { mutableStateOf(false) }
    DoneConfirmation(saving, "Workout saved") {
        if (!left) {
            left = true
            back()
        }
    }
}

private enum class StepDialog { NONE, TYPE, TIME, DISTANCE, FAST, SLOW, OFFSET }

/** Edit one step: effort, how it ends, pace target, name, order. */
@Composable
fun StepScreen(id: String, path: StepPath, isNew: Boolean, finish: (String) -> Unit, back: () -> Unit) {
    val (saved, edit) = rememberWorkout(id)
    val store = LibraryStore.get(LocalContext.current)
    val guard: DraftGuard = viewModel()
    if (isNew) {
        guard.discard = { store.update { lib -> lib.workout(id)?.let { lib.upsert(it.copy(workout = Edit.removeStep(it.workout, path))) } ?: lib } }
    }
    var confirming by remember { mutableStateOf(false) }
    var left by remember { mutableStateOf(false) }
    var armDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(armDelete) {
        if (armDelete) {
            delay(3_000)
            armDelete = false
        }
    }
    val goal = goalPace()
    val step = saved?.let { Edit.stepAt(it.workout, path) }
    if (step == null) {
        ListScreen { paragraph("Step not found.") }
        return
    }
    var dialog by remember { mutableStateOf(StepDialog.NONE) }
    fun set(newStep: Step) = edit { Edit.replaceStep(it, path, newStep) }
    val rename = rememberTextInput { name -> set(step.copy(name = name)) }
    val range = step.target as? PaceTarget.Range
    val goalTarget = step.target as? PaceTarget.Goal

    ListScreen(
        edgeButton = {
            EdgeButton(
                onClick = {
                    guard.confirmed = true
                    confirming = true
                },
                buttonSize = EdgeButtonSize.Medium,
                colors = Buttons.primary(),
            ) { Text(if (isNew) "Add to workout" else "Save") }
        },
    ) {
        item {
            AnimatedContent(
                targetState = step.type,
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                label = "stepType",
            ) { type ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    TypeDot(type, 14)
                    Text(step.label, style = MaterialTheme.typography.titleLarge, color = type.color)
                }
            }
        }
        if (isNew) paragraph("Choose what to do and for how long, then tap Add to workout at the bottom.")
        item {
            FilledTonalButton(
                onClick = { dialog = StepDialog.TYPE },
                icon = { TypeDot(step.type) },
                label = { Text("Effort") },
                secondaryLabel = { Text(step.type.label) },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        header("How long")
        item {
            SegmentedChoice(listOf("Time", "Distance"), if (step.end is End.Time) 0 else 1) { i ->
                if (i == 0 && step.end !is End.Time) set(step.copy(end = End.Time(5 * 60)))
                if (i == 1 && step.end !is End.Distance) set(step.copy(end = End.Distance(1000.0)))
            }
        }
        item {
            FilledTonalButton(
                onClick = { dialog = if (step.end is End.Time) StepDialog.TIME else StepDialog.DISTANCE },
                label = { Text(Describe.end(step.end), style = MaterialTheme.typography.numeralSmall) },
                secondaryLabel = { Text(if (step.end is End.Time) "min : sec" else "kilometres") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        header("Pace goal (optional)")
        item {
            SegmentedChoice(
                listOf("None", "Range", "My goal"),
                when (step.target) { null -> 0; is PaceTarget.Range -> 1; is PaceTarget.Goal -> 2 },
            ) { i ->
                when (i) {
                    0 -> set(step.copy(target = null))
                    1 -> if (range == null) set(step.copy(target = PaceTarget.Range(6 * 60, 6 * 60 + 30)))
                    2 -> if (goalTarget == null) set(step.copy(target = PaceTarget.Goal()))
                }
            }
        }
        if (range != null) {
            item {
                FilledTonalButton(
                    onClick = { dialog = StepDialog.FAST },
                    label = { Text("Fastest ${formatPace(range.fastSecPerKm)} /km") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item {
                FilledTonalButton(
                    onClick = { dialog = StepDialog.SLOW },
                    label = { Text("Slowest ${formatPace(range.slowSecPerKm)} /km") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (goalTarget != null) {
            item {
                FilledTonalButton(
                    onClick = { dialog = StepDialog.OFFSET },
                    label = { Text(if (goalTarget.offsetSec == 0) "Goal pace" else "Goal %+d s/km".format(goalTarget.offsetSec)) },
                    secondaryLabel = {
                        Text(goal?.let { "= ${formatPace(it + goalTarget.offsetSec)} /km" } ?: "Set goal pace in Settings")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        header("Name (optional)")
        item {
            FilledTonalButton(
                onClick = { rename("Step name") },
                label = { Text("Rename") },
                secondaryLabel = { Text(step.name ?: "Uses \"${step.type.label}\"") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (step.name != null) {
            item {
                FilledTonalButton(onClick = { set(step.copy(name = null)) }, label = { Text("Reset name") }, modifier = Modifier.fillMaxWidth())
            }
        }
        if (isNew) {
            item {
                FilledTonalButton(onClick = back, label = { Text("Cancel") }, secondaryLabel = { Text("Don't add it") }, modifier = Modifier.fillMaxWidth())
            }
        } else {
            header("Arrange")
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, androidx.compose.ui.Alignment.CenterHorizontally)) {
                    CompactButton(onClick = { edit { Edit.moveStep(it, path, -1) }; back() }, colors = Buttons.secondary(), label = { Text("Up") })
                    CompactButton(onClick = { edit { Edit.moveStep(it, path, 1) }; back() }, colors = Buttons.secondary(), label = { Text("Down") })
                    CompactButton(onClick = { edit { Edit.duplicateStep(it, path) }; back() }, colors = Buttons.secondary(), label = { Text("Copy") })
                }
            }
            item {
                Button(
                    onClick = { if (armDelete) deleting = true else armDelete = true },
                    colors = Buttons.destructive(armDelete),
                    label = { Text(if (armDelete) "Tap again to delete" else "Delete") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    DoneConfirmation(confirming, if (isNew) "Added to workout" else "Saved") {
        if (!left) {
            left = true
            finish(flashKey(path.block, path.inner))
        }
    }
    DeletedConfirmation(deleting) {
        if (!left) {
            left = true
            guard.confirmed = true
            edit { Edit.removeStep(it, path) }
            back()
        }
    }

    ChoiceDialog(
        visible = dialog == StepDialog.TYPE,
        title = "Effort",
        choices = StepType.entries,
        label = { it.label },
        dot = { it.color },
        onDismiss = { dialog = StepDialog.NONE },
    ) { type ->
        set(step.copy(type = type))
        dialog = StepDialog.NONE
    }
    WheelDialog(
        visible = dialog == StepDialog.TIME,
        title = "Duration",
        wheels = durationWheels((step.end as? End.Time)?.seconds ?: 300),
        separator = ":",
        onDismiss = { dialog = StepDialog.NONE },
    ) {
        set(step.copy(end = End.Time(durationFrom(it))))
        dialog = StepDialog.NONE
    }
    WheelDialog(
        visible = dialog == StepDialog.DISTANCE,
        title = "Distance (km)",
        wheels = distanceWheels((step.end as? End.Distance)?.meters ?: 1000.0),
        separator = ".",
        onDismiss = { dialog = StepDialog.NONE },
    ) {
        set(step.copy(end = End.Distance(distanceFrom(it))))
        dialog = StepDialog.NONE
    }
    WheelDialog(
        visible = dialog == StepDialog.FAST || dialog == StepDialog.SLOW,
        title = if (dialog == StepDialog.FAST) "Fastest pace" else "Slowest pace",
        wheels = paceWheels(if (dialog == StepDialog.FAST) range?.fastSecPerKm ?: 360 else range?.slowSecPerKm ?: 390),
        separator = ":",
        onDismiss = { dialog = StepDialog.NONE },
    ) {
        val pace = paceFrom(it)
        val r = range ?: PaceTarget.Range(pace, pace)
        set(step.copy(target = if (dialog == StepDialog.FAST) r.copy(fastSecPerKm = pace) else r.copy(slowSecPerKm = pace)))
        dialog = StepDialog.NONE
    }
    WheelDialog(
        visible = dialog == StepDialog.OFFSET,
        title = "Slower (+) or faster (-)",
        wheels = listOf(Wheel(25, ((goalTarget?.offsetSec ?: 0) + 60) / 5, "Seconds per km") { "%+d".format(it * 5 - 60) }),
        onDismiss = { dialog = StepDialog.NONE },
    ) {
        set(step.copy(target = PaceTarget.Goal(it[0] * 5 - 60)))
        dialog = StepDialog.NONE
    }
}

private enum class BlockDialog { NONE, ROUNDS, DISTANCE }

/** Edit a repeat block: rounds or target distance, its steps, order. */
@Composable
fun BlockScreen(
    id: String,
    index: Int,
    isNew: Boolean,
    open: (String) -> Unit,
    finish: (String) -> Unit,
    back: () -> Unit,
    flash: String = "",
    onFlashShown: () -> Unit = {},
) {
    val (saved, edit) = rememberWorkout(id)
    val store = LibraryStore.get(LocalContext.current)
    val guard: DraftGuard = viewModel()
    if (isNew) {
        guard.discard = { store.update { lib -> lib.workout(id)?.let { lib.upsert(it.copy(workout = Edit.remove(it.workout, index))) } ?: lib } }
    }
    var confirming by remember { mutableStateOf(false) }
    var left by remember { mutableStateOf(false) }
    var armDelete by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    LaunchedEffect(armDelete) {
        if (armDelete) {
            delay(3_000)
            armDelete = false
        }
    }
    LaunchedEffect(flash) {
        if (flash.isNotEmpty()) {
            delay(2_000)
            onFlashShown()
        }
    }
    val goal = goalPace()
    val block = saved?.workout?.blocks?.getOrNull(index)
    if (block == null || block is Step) {
        ListScreen { paragraph("Block not found.") }
        return
    }
    var dialog by remember { mutableStateOf(BlockDialog.NONE) }
    fun set(b: Block) = edit { Edit.replace(it, index, b) }

    ListScreen(
        edgeButton = {
            EdgeButton(
                onClick = {
                    guard.confirmed = true
                    confirming = true
                },
                buttonSize = EdgeButtonSize.Medium,
                colors = Buttons.primary(),
            ) { Text(if (isNew) "Add to workout" else "Save") }
        },
    ) {
        item { Text(blockTitle(block), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.primary) }
        if (isNew) paragraph("Set how many rounds and what happens in each one, then tap Add to workout at the bottom.")
        when (block) {
            is Repeat -> {
                item {
                    FilledTonalButton(
                        onClick = { dialog = BlockDialog.ROUNDS },
                        label = { Text("${block.times}", style = MaterialTheme.typography.numeralSmall) },
                        secondaryLabel = { Text("rounds") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (block.steps.size > 1) {
                    item {
                        SwitchButton(
                            checked = block.skipLastOnFinal > 0,
                            onCheckedChange = { set(block.copy(skipLastOnFinal = if (it) 1 else 0)) },
                            label = { Text("Recover between") },
                            secondaryLabel = { Text("Skip last step at the end") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                }
            }
            is RepeatUntilDistance -> item {
                FilledTonalButton(
                    onClick = { dialog = BlockDialog.DISTANCE },
                    label = { Text(formatDistance(block.meters), style = MaterialTheme.typography.numeralSmall) },
                    secondaryLabel = { Text("stop once covered") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            is Step -> Unit
        }
        header("Each round")
        Edit.innerSteps(block).orEmpty().forEachIndexed { j, step ->
            item {
                FilledTonalButton(
                    onClick = { open(Routes.step(id, index, j)) },
                    icon = { TypeDot(step.type) },
                    label = { Text(step.label) },
                    secondaryLabel = { Text(stepDetail(step, goal), maxLines = 1) },
                    modifier = Modifier.fillMaxWidth().flash(flash == flashKey(index, j)),
                )
            }
        }
        item {
            FilledTonalButton(
                onClick = {
                    val count = Edit.innerSteps(block).orEmpty().size
                    edit { Edit.addInner(it, index, Edit.newStep(StepType.WALK)) }
                    open(Routes.step(id, index, count, isNew = true))
                },
                label = { Text("Add run or walk") },
                secondaryLabel = { Text("To each round") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (isNew) {
            item {
                FilledTonalButton(onClick = back, label = { Text("Cancel") }, secondaryLabel = { Text("Don't add it") }, modifier = Modifier.fillMaxWidth())
            }
        } else {
            header("Arrange")
            item {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(4.dp, androidx.compose.ui.Alignment.CenterHorizontally)) {
                    CompactButton(onClick = { edit { Edit.move(it, index, -1) }; back() }, colors = Buttons.secondary(), label = { Text("Up") })
                    CompactButton(onClick = { edit { Edit.move(it, index, 1) }; back() }, colors = Buttons.secondary(), label = { Text("Down") })
                    CompactButton(onClick = { edit { Edit.duplicate(it, index) }; back() }, colors = Buttons.secondary(), label = { Text("Copy") })
                }
            }
            item {
                Button(
                    onClick = { if (armDelete) deleting = true else armDelete = true },
                    colors = Buttons.destructive(armDelete),
                    label = { Text(if (armDelete) "Tap again to delete" else "Delete") },
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 14.dp),
                )
            }
        }
    }

    DoneConfirmation(confirming, if (isNew) "Added to workout" else "Saved") {
        if (!left) {
            left = true
            finish(flashKey(index))
        }
    }
    DeletedConfirmation(deleting) {
        if (!left) {
            left = true
            guard.confirmed = true
            edit { Edit.remove(it, index) }
            back()
        }
    }

    WheelDialog(
        visible = dialog == BlockDialog.ROUNDS,
        title = "Rounds",
        wheels = listOf(Wheel(50, ((block as? Repeat)?.times ?: 4) - 1, "Rounds") { "${it + 1}" }),
        onDismiss = { dialog = BlockDialog.NONE },
    ) {
        (block as? Repeat)?.let { r -> set(r.copy(times = it[0] + 1)) }
        dialog = BlockDialog.NONE
    }
    WheelDialog(
        visible = dialog == BlockDialog.DISTANCE,
        title = "Repeat until (km)",
        wheels = distanceWheels((block as? RepeatUntilDistance)?.meters ?: 3000.0),
        separator = ".",
        onDismiss = { dialog = BlockDialog.NONE },
    ) {
        (block as? RepeatUntilDistance)?.let { r -> set(r.copy(meters = distanceFrom(it))) }
        dialog = BlockDialog.NONE
    }
}

