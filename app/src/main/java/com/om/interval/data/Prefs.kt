package com.om.interval.data

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate

data class Settings(
    /** Pace used by "goal pace" targets, seconds per km. */
    val goalSecPerKm: Int? = null,
    val paceAlerts: Boolean = true,
    val sound: Boolean = false,
)

/** Settings and the calendar's done ticks, kept in SharedPreferences and mirrored as flows for the UI. */
class Prefs private constructor(context: Context) {
    private val settingsPrefs: SharedPreferences = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val donePrefs: SharedPreferences = context.getSharedPreferences("done", Context.MODE_PRIVATE)

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    private val _done = MutableStateFlow(donePrefs.all.filterValues { it == true }.keys.toSet())
    val done: StateFlow<Set<String>> = _done.asStateFlow()

    private fun readSettings() = Settings(
        goalSecPerKm = settingsPrefs.getInt("goalPace", -1).takeIf { it > 0 },
        paceAlerts = settingsPrefs.getBoolean("paceAlerts", true),
        sound = settingsPrefs.getBoolean("sound", false),
    )

    fun update(transform: (Settings) -> Settings) {
        val s = transform(_settings.value)
        settingsPrefs.edit {
            putInt("goalPace", s.goalSecPerKm ?: -1)
            putBoolean("paceAlerts", s.paceAlerts)
            putBoolean("sound", s.sound)
        }
        _settings.value = s
    }

    fun isDone(date: LocalDate) = date.toString() in _done.value

    fun setDone(date: LocalDate, done: Boolean) {
        donePrefs.edit { putBoolean(date.toString(), done) }
        _done.value = if (done) _done.value + date.toString() else _done.value - date.toString()
    }

    companion object {
        @Volatile
        private var instance: Prefs? = null

        fun get(context: Context): Prefs =
            instance ?: synchronized(this) { instance ?: Prefs(context.applicationContext).also { instance = it } }
    }
}