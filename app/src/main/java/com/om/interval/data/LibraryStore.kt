package com.om.interval.data

import android.content.Context
import com.om.interval.workout.Library
import com.om.interval.workout.LibraryJson
import com.om.interval.workout.SavedWorkout
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.time.LocalDate

/** Your workouts and calendar, saved to `library.json` after every change. */
class LibraryStore private constructor(context: Context) {
    private val file = File(context.filesDir, "library.json")

    private val _library = MutableStateFlow(
        if (file.exists()) LibraryJson.decode(file.readText()) else Library(),
    )
    val library: StateFlow<Library> = _library.asStateFlow()

    @Synchronized
    fun update(transform: (Library) -> Library) {
        val next = transform(_library.value)
        if (next == _library.value) return
        _library.value = next
        // Write to a temp file first so a crash mid-write never leaves a broken library.
        val tmp = File(file.parentFile, "library.json.tmp")
        tmp.writeText(LibraryJson.encode(next))
        tmp.renameTo(file)
    }

    fun workout(id: String?): SavedWorkout? = _library.value.workout(id)

    fun scheduledOn(date: LocalDate): SavedWorkout? = _library.value.scheduledOn(date)

    companion object {
        @Volatile
        private var instance: LibraryStore? = null

        fun get(context: Context): LibraryStore =
            instance ?: synchronized(this) { instance ?: LibraryStore(context.applicationContext).also { instance = it } }
    }
}