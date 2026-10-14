package com.om.interval.ui

import androidx.activity.compose.LocalActivity
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavBackStackEntry
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.navigation.SwipeDismissableNavHost
import androidx.wear.compose.navigation.composable
import androidx.wear.compose.navigation.rememberSwipeDismissableNavController
import com.om.interval.exercise.Phase
import com.om.interval.exercise.RunSession
import com.om.interval.workout.StepPath
import java.time.LocalDate

object Routes {
    const val HOME = "home"
    const val CALENDAR = "calendar"
    const val LIBRARY = "library"
    const val HISTORY = "history"
    const val SETTINGS = "settings"
    const val DAY = "day/{date}"
    const val PICK = "pick/{date}"
    const val NEW = "new/{date}"
    const val WORKOUT = "workout/{id}/{date}"
    const val EDIT = "edit/{id}"
    const val STEP = "step/{id}/{block}/{inner}"
    const val BLOCK = "block/{id}/{block}"
    const val SCHEDULE = "schedule/{id}"
    const val PRERUN = "prerun/{id}/{date}"
    const val SUMMARY = "summary/{id}"
    const val EXPORT = "export/{id}"

    private fun d(date: LocalDate?) = date?.toString() ?: "-"

    fun day(date: LocalDate) = "day/$date"
    fun pick(date: LocalDate) = "pick/$date"
    fun newWorkout(date: LocalDate?) = "new/${d(date)}"
    fun workout(id: String, date: LocalDate? = null) = "workout/$id/${d(date)}"
    fun edit(id: String) = "edit/$id"
    fun step(id: String, block: Int, inner: Int) = "step/$id/$block/$inner"
    fun block(id: String, block: Int) = "block/$id/$block"
    fun schedule(id: String) = "schedule/$id"
    fun prerun(id: String, date: LocalDate?) = "prerun/$id/${d(date)}"
    fun summary(id: String) = "summary/$id"
    fun export(id: String) = "export/$id"
}

private fun NavBackStackEntry.arg(name: String): String = arguments?.getString(name).orEmpty()

private fun NavBackStackEntry.date(name: String = "date"): LocalDate? =
    arg(name).takeIf { it != "-" }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }

@Composable
fun IntervalApp(ambient: Boolean, permissionsGranted: Boolean, onRequestPermissions: () -> Unit) {
    val run by RunSession.state.collectAsStateWithLifecycle()
    val nav = rememberSwipeDismissableNavController()
    val open: (String) -> Unit = { nav.navigate(it) }
    val back: () -> Unit = { nav.popBackStack() }

    // A finished run lands on its summary, then the session goes back to idle.
    LaunchedEffect(run.phase, run.summaryId) {
        if (run.phase == Phase.ENDED) {
            val id = run.summaryId
            if (id != null) nav.navigate(Routes.summary(id)) { popUpTo(Routes.HOME) }
            else nav.popBackStack(Routes.HOME, inclusive = false)
            RunSession.reset()
        }
    }

    // Back during a run leaves the workout recording and returns to the watch face, as on Apple Watch;
    // it must not move the screens hidden underneath.
    val activity = LocalActivity.current
    BackHandler(enabled = run.isRecording) { activity?.moveTaskToBack(true) }

    IntervalTheme {
        AppScaffold {
            Box(Modifier.fillMaxSize()) {
                // The screens stay composed underneath the run, so navigation always exists,
                // even when the app is reopened mid-run from the watch-face chip.
                SwipeDismissableNavHost(navController = nav, startDestination = Routes.HOME) {
                    composable(Routes.HOME) { HomeScreen(permissionsGranted, onRequestPermissions, open) }
                    composable(Routes.CALENDAR) { CalendarScreen(open) }
                    composable(Routes.LIBRARY) { LibraryScreen(open) }
                    composable(Routes.HISTORY) { HistoryScreen(open) }
                    composable(Routes.SETTINGS) { SettingsScreen() }
                    composable(Routes.DAY) { e -> e.date()?.let { DayScreen(it, open) } }
                    composable(Routes.PICK) { e -> e.date()?.let { PickWorkoutScreen(it, open, back) } }
                    composable(Routes.NEW) { e ->
                        NewWorkoutScreen(e.date()) { id ->
                            nav.navigate(Routes.edit(id)) { popUpTo(Routes.NEW) { inclusive = true } }
                        }
                    }
                    composable(Routes.WORKOUT) { e -> WorkoutScreen(e.arg("id"), e.date(), open, back) }
                    composable(Routes.EDIT) { e -> EditorScreen(e.arg("id"), open, back) }
                    composable(Routes.STEP) { e ->
                        StepScreen(
                            e.arg("id"),
                            StepPath(e.arg("block").toIntOrNull() ?: 0, e.arg("inner").toIntOrNull() ?: -1),
                            back,
                        )
                    }
                    composable(Routes.BLOCK) { e -> BlockScreen(e.arg("id"), e.arg("block").toIntOrNull() ?: 0, open, back) }
                    composable(Routes.SCHEDULE) { e -> ScheduleScreen(e.arg("id"), back) }
                    composable(Routes.PRERUN) { e -> PreRunScreen(e.arg("id"), e.date(), run, permissionsGranted, onRequestPermissions) }
                    composable(Routes.SUMMARY) { e -> SummaryScreen(e.arg("id"), open, back) }
                    composable(Routes.EXPORT) { e -> ExportScreen(e.arg("id")) }
                }
                AnimatedVisibility(
                    visible = run.isRecording || run.phase == Phase.ENDED,
                    enter = fadeIn(tween(450)) + scaleIn(tween(450), initialScale = 0.92f),
                    exit = fadeOut(tween(350)) + scaleOut(tween(350), targetScale = 1.04f),
                ) {
                    // Swallow touches so nothing reaches the screens underneath.
                    Box(Modifier.fillMaxSize().pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }) {
                        RunScreen(run, ambient)
                    }
                }
            }
        }
    }
}
