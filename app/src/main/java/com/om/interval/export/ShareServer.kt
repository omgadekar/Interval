package com.om.interval.export

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.net.ServerSocket
import java.net.Socket
import java.security.SecureRandom

/**
 * A tiny web server that hands a run's files to a phone on the same Wi-Fi.
 * Everything lives under a random token, so only someone who scanned the QR code can fetch it.
 */
class ShareServer(
    private val title: String,
    private val files: List<File>,
    val token: String = newToken(),
) {
    private var socket: ServerSocket? = null
    private var job: Job? = null

    val port: Int? get() = socket?.localPort

    /** Prefers [preferredPort] for a short URL; falls back to any free port. */
    fun start(scope: CoroutineScope, preferredPort: Int = 8765): Int {
        val server = runCatching { ServerSocket(preferredPort) }.getOrElse { ServerSocket(0) }
        socket = server
        job = scope.launch(Dispatchers.IO) {
            while (isActive) {
                val client = runCatching { server.accept() }.getOrNull() ?: break
                launch { client.use { handle(it) } }
            }
        }
        return server.localPort
    }

    fun stop() {
        job?.cancel()
        runCatching { socket?.close() }
        socket = null
    }

    private fun handle(client: Socket) {
        client.soTimeout = 10_000
        val reader = client.getInputStream().bufferedReader()
        val requestLine = reader.readLine() ?: return
        // Drain headers; nothing in them matters here.
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isEmpty()) break
        }
        val parts = requestLine.split(" ")
        val response = if (parts.size >= 2 && (parts[0] == "GET" || parts[0] == "HEAD")) route(parts[1]) else Response.notFound()
        val out = client.getOutputStream()
        out.write(response.header().toByteArray())
        if (parts.getOrNull(0) != "HEAD") out.write(response.body)
        out.flush()
    }

    class Response(val status: String, val contentType: String, val body: ByteArray, val filename: String? = null) {
        fun header(): String = buildString {
            append("HTTP/1.1 $status\r\n")
            append("Content-Type: $contentType\r\n")
            append("Content-Length: ${body.size}\r\n")
            filename?.let { append("Content-Disposition: attachment; filename=\"$it\"\r\n") }
            append("Cache-Control: no-store\r\nConnection: close\r\n\r\n")
        }

        companion object {
            fun notFound() = Response("404 Not Found", "text/plain; charset=utf-8", "Not found".toByteArray())
        }
    }

    /** Pure routing, so it can be tested without sockets. */
    fun route(path: String): Response {
        val clean = path.substringBefore('?').trimEnd('/')
        if (clean == "/$token") return Response("200 OK", "text/html; charset=utf-8", page().toByteArray())
        val name = clean.removePrefix("/$token/").takeIf { clean.startsWith("/$token/") } ?: return Response.notFound()
        val file = files.firstOrNull { it.name == name && it.exists() } ?: return Response.notFound()
        val type = when (file.extension) {
            "gpx" -> "application/gpx+xml"
            "tcx" -> "application/vnd.garmin.tcx+xml"
            else -> "application/octet-stream"
        }
        return Response("200 OK", type, file.readBytes(), filename = file.name)
    }

    private fun page(): String {
        val links = files.filter { it.exists() }.joinToString("\n") { f ->
            val hint = when (f.extension) {
                "tcx" -> "Laps, heart rate, GPS · best for Strava &amp; Garmin"
                "gpx" -> "GPS track with heart rate"
                else -> ""
            }
            """<a class="file" href="/$token/${f.name}" download><span class="ext">${f.extension.uppercase()}</span><span class="txt"><b>Download ${f.extension.uppercase()}</b><small>$hint</small></span></a>"""
        }
        return """<!doctype html><html><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>${escape(title)}</title><style>
body{margin:0;font-family:-apple-system,Inter,Roboto,sans-serif;background:#000;color:#fff;display:flex;justify-content:center}
main{max-width:420px;width:100%;padding:32px 20px}
h1{font-size:28px;margin:0 0 4px;letter-spacing:-.02em}p{color:#98989f;margin:0 0 24px}
.file{display:flex;gap:14px;align-items:center;background:#1c1c1e;border-radius:18px;padding:16px;margin:0 0 12px;color:#fff;text-decoration:none}
.ext{background:#ff7a45;color:#000;font-weight:700;border-radius:12px;padding:12px 10px;font-size:13px}
.txt{display:flex;flex-direction:column}small{color:#98989f;margin-top:2px}
</style></head><body><main><h1>${escape(title)}</h1><p>From Interval on your watch. Tap a file to save it, then share it to Strava or any app.</p>
$links
</main></body></html>"""
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    companion object {
        private const val ALPHABET = "abcdefghijkmnpqrstuvwxyz23456789"

        fun newToken(length: Int = 8): String {
            val random = SecureRandom()
            return (1..length).map { ALPHABET[random.nextInt(ALPHABET.length)] }.joinToString("")
        }
    }
}