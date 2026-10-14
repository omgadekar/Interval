package com.om.interval.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.CircularProgressIndicator
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import com.om.interval.data.RunStore
import com.om.interval.export.ShareServer
import com.om.interval.export.WifiLink
import kotlinx.coroutines.delay

private sealed interface ExportState {
    data object Connecting : ExportState
    data class Ready(val url: String) : ExportState
    data class Failed(val message: String) : ExportState
}

private fun qr(text: String): BitMatrix = QRCodeWriter().encode(
    text, BarcodeFormat.QR_CODE, 0, 0,
    mapOf(EncodeHintType.MARGIN to 0, EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M),
)

/** Dark modules on a white rounded card, which every phone camera reads easily. */
@Composable
private fun QrCode(text: String, modifier: Modifier = Modifier) {
    val matrix = remember(text) { qr(text) }
    Canvas(modifier) {
        drawRoundRect(Color.White, cornerRadius = CornerRadius(14.dp.toPx()))
        val pad = 8.dp.toPx()
        val cell = (size.minDimension - pad * 2) / matrix.width
        for (y in 0 until matrix.height) for (x in 0 until matrix.width) {
            if (matrix[x, y]) drawRect(Color.Black, Offset(pad + x * cell, pad + y * cell), Size(cell + 0.5f, cell + 0.5f))
        }
    }
}

/**
 * Export without a phone app: the watch joins Wi-Fi and shows a QR code; your phone, on the same
 * network, opens it and downloads the TCX/GPX. Sharing stops when you leave or after 10 minutes.
 */
@Composable
fun ExportScreen(id: String) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val store = remember { RunStore(context) }
    val summary = remember(id) { store.load(id) }
    val link = remember { WifiLink(context) }
    val server = remember(id) {
        ShareServer(summary?.title ?: "Run", listOf(store.file(id, "tcx"), store.file(id, "gpx")))
    }
    var state by remember { mutableStateOf<ExportState>(ExportState.Connecting) }
    var attempt by remember { mutableIntStateOf(0) }

    DisposableEffect(Unit) {
        view.keepScreenOn = true
        onDispose {
            view.keepScreenOn = false
            server.stop()
            link.release()
        }
    }
    LaunchedEffect(attempt) {
        state = ExportState.Connecting
        val ip = link.connect()
        if (ip == null) {
            state = ExportState.Failed("Wi-Fi didn't connect. Check Wi-Fi is on and has a saved network, then tap to retry.")
            return@LaunchedEffect
        }
        val port = server.port ?: server.start(scope)
        state = ExportState.Ready("http://$ip:$port/${server.token}")
        delay(10 * 60 * 1000L)
        server.stop()
        link.release()
        state = ExportState.Failed("Sharing stopped after 10 minutes. Tap to share again.")
    }

    AnimatedContent(
        targetState = state,
        transitionSpec = { (fadeIn(tween(400)) + scaleIn(tween(400), initialScale = 0.94f)) togetherWith fadeOut(tween(200)) },
        label = "export",
        modifier = Modifier.fillMaxSize(),
    ) { s ->
        Column(
            Modifier.fillMaxSize().padding(horizontal = 26.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
        ) {
            when (s) {
                ExportState.Connecting -> {
                    CircularProgressIndicator(modifier = Modifier.size(40.dp))
                    Text("Joining Wi-Fi…", style = MaterialTheme.typography.titleSmall)
                    Text("Keep your phone on the same network", style = MaterialTheme.typography.labelSmall, color = Palette.Secondary, textAlign = TextAlign.Center)
                }
                is ExportState.Ready -> {
                    QrCode(s.url, Modifier.size(if (screenSize() == ScreenSize.SMALL) 112.dp else 124.dp))
                    Text("Scan with your phone", style = MaterialTheme.typography.titleSmall)
                    Text(s.url.removePrefix("http://"), style = MaterialTheme.typography.labelSmall, color = Palette.Secondary, textAlign = TextAlign.Center)
                }
                is ExportState.Failed -> {
                    Text("Export", style = MaterialTheme.typography.titleMedium)
                    androidx.wear.compose.material3.Button(
                        onClick = { attempt++ },
                        colors = Buttons.primary(),
                        label = { Text("Try again", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    )
                    Text(s.message, style = MaterialTheme.typography.labelSmall, color = Palette.Secondary, textAlign = TextAlign.Center)
                    androidx.wear.compose.material3.FilledTonalButton(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
                            }
                        },
                        label = { Text("Wi-Fi settings", Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
                    )
                }
            }
        }
    }
}