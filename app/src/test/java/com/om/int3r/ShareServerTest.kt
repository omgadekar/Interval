package com.om.int3r

import com.om.int3r.export.ShareServer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files

class ShareServerTest {
    private fun files(): List<File> {
        val dir = Files.createTempDirectory("runs").toFile()
        return listOf(
            File(dir, "2026-10-15_quick_0800.tcx").apply { writeText("<tcx/>") },
            File(dir, "2026-10-15_quick_0800.gpx").apply { writeText("<gpx/>") },
        )
    }

    @Test
    fun servesPageAndFilesOnlyUnderTheToken() {
        val f = files()
        val server = ShareServer("Quick <test>", f, token = "abc123")
        val page = server.route("/abc123/")
        assertEquals("200 OK", page.status)
        val html = String(page.body)
        assertTrue(html.contains("Quick &lt;test&gt;"))
        assertTrue(html.contains("/abc123/2026-10-15_quick_0800.tcx"))

        val tcx = server.route("/abc123/2026-10-15_quick_0800.tcx")
        assertEquals("<tcx/>", String(tcx.body))
        assertTrue(tcx.header().contains("attachment; filename=\"2026-10-15_quick_0800.tcx\""))

        assertEquals("404 Not Found", server.route("/").status)
        assertEquals("404 Not Found", server.route("/wrong/2026-10-15_quick_0800.tcx").status)
        assertEquals("404 Not Found", server.route("/abc123/../secret.txt").status)
    }

    @Test
    fun answersRealHttpRequests() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val server = ShareServer("Run", files(), token = "tok")
        val port = server.start(scope, preferredPort = 0)
        try {
            val conn = URL("http://127.0.0.1:$port/tok/2026-10-15_quick_0800.gpx").openConnection() as HttpURLConnection
            assertEquals(200, conn.responseCode)
            assertEquals("<gpx/>", conn.inputStream.bufferedReader().readText())
            val missing = URL("http://127.0.0.1:$port/nope").openConnection() as HttpURLConnection
            assertEquals(404, missing.responseCode)
        } finally {
            server.stop()
            scope.cancel()
        }
    }

    @Test
    fun tokensAreRandomAndUnambiguous() {
        val tokens = (1..50).map { ShareServer.newToken() }.toSet()
        assertEquals(50, tokens.size)
        tokens.forEach { t -> assertFalse(t.any { it in "lo01" }) }
    }
}