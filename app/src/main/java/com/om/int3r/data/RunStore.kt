package com.om.int3r.data

import android.content.Context
import com.om.int3r.record.RunSummary
import kotlinx.serialization.json.Json
import java.io.BufferedWriter
import java.io.File

/**
 * Finished runs live in the app's external files dir so they can be pulled with
 * `adb pull /sdcard/Android/data/com.om.int3r/files/runs`:
 * `<id>.json` summary, `<id>.tcx`, `<id>.gpx`, and `<id>.csv` raw samples written during the run.
 */
class RunStore(context: Context) {
    val dir: File = (context.getExternalFilesDir("runs") ?: File(context.filesDir, "runs")).apply { mkdirs() }

    private val json = Json { ignoreUnknownKeys = true }

    fun file(id: String, ext: String) = File(dir, "$id.$ext")

    fun openRawLog(id: String): BufferedWriter = file(id, "csv").bufferedWriter().apply {
        write("stream,time_ms,v1,v2,v3\n")
    }

    fun save(summary: RunSummary, tcx: String, gpx: String?) {
        file(summary.id, "tcx").writeText(tcx)
        gpx?.let { file(summary.id, "gpx").writeText(it) }
        file(summary.id, "json").writeText(json.encodeToString(RunSummary.serializer(), summary))
    }

    fun list(): List<RunSummary> = dir.listFiles { f -> f.extension == "json" }
        .orEmpty()
        .mapNotNull { read(it) }
        .sortedByDescending { it.startMs }

    fun load(id: String): RunSummary? = file(id, "json").takeIf { it.exists() }?.let(::read)

    fun delete(id: String) = listOf("json", "tcx", "gpx", "csv").forEach { file(id, it).delete() }

    private fun read(f: File): RunSummary? =
        runCatching { json.decodeFromString(RunSummary.serializer(), f.readText()) }.getOrNull()
}