package com.om.interval.workout

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.LocalDate
import java.util.UUID

/** Everything you build on the watch: saved workouts and which one is planned on which day. */
@Serializable
data class Library(
    val workouts: List<SavedWorkout> = emptyList(),
    /** ISO date -> workout id. */
    val schedule: Map<String, String> = emptyMap(),
) {
    fun workout(id: String?): SavedWorkout? = id?.let { wid -> workouts.firstOrNull { it.id == wid } }

    fun scheduledOn(date: LocalDate): SavedWorkout? = workout(schedule[date.toString()])

    fun upsert(w: SavedWorkout): Library =
        if (workouts.any { it.id == w.id }) copy(workouts = workouts.map { if (it.id == w.id) w else it })
        else copy(workouts = workouts + w)

    /** Removes the workout and every day it was planned on. */
    fun delete(id: String): Library =
        copy(workouts = workouts.filterNot { it.id == id }, schedule = schedule.filterValues { it != id })

    fun duplicate(id: String, newId: String = newId(), nowMs: Long = System.currentTimeMillis()): Library {
        val src = workout(id) ?: return this
        val copy = src.copy(id = newId, name = "${src.name} copy", createdMs = nowMs)
        val index = workouts.indexOf(src)
        return copy(workouts = workouts.toMutableList().apply { add(index + 1, copy) })
    }

    fun plan(date: LocalDate, workoutId: String?): Library =
        copy(schedule = if (workoutId == null) schedule - date.toString() else schedule + (date.toString() to workoutId))

    companion object {
        fun newId(): String = UUID.randomUUID().toString().take(8)
    }
}

object LibraryJson {
    // "kind" as the type tag: Step already has a property called "type".
    val json = Json {
        ignoreUnknownKeys = true
        classDiscriminator = "kind"
        encodeDefaults = false
    }

    fun encode(library: Library): String = json.encodeToString(Library.serializer(), library)

    fun decode(text: String): Library = runCatching { json.decodeFromString(Library.serializer(), text) }.getOrDefault(Library())
}