package com.om.int3r.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.rotary.onRotaryScrollEvent
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.wear.compose.material3.FilledIconButton
import androidx.wear.compose.material3.FilledTonalIconButton
import androidx.wear.compose.material3.IconButtonDefaults
import androidx.wear.compose.material3.MaterialTheme
import androidx.wear.compose.material3.Text
import com.om.int3r.music.MusicControl

enum class MediaGlyph { PLAY, PAUSE, NEXT, PREVIOUS, MINUS, PLUS }

/** Media symbols drawn as shapes, so they look crisp at any size without an icon library. */
@Composable
fun MediaIcon(glyph: MediaGlyph, color: Color, size: Dp = 22.dp) {
    Canvas(Modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        fun triangle(left: Float, right: Float, pointRight: Boolean) = Path().apply {
            if (pointRight) {
                moveTo(left, h * 0.15f); lineTo(right, h / 2); lineTo(left, h * 0.85f)
            } else {
                moveTo(right, h * 0.15f); lineTo(left, h / 2); lineTo(right, h * 0.85f)
            }
            close()
        }
        val r = CornerRadius(w * 0.06f)
        when (glyph) {
            MediaGlyph.PLAY -> drawPath(triangle(w * 0.22f, w * 0.88f, true), color)
            MediaGlyph.PAUSE -> {
                drawRoundRect(color, Offset(w * 0.2f, h * 0.15f), Size(w * 0.2f, h * 0.7f), r)
                drawRoundRect(color, Offset(w * 0.6f, h * 0.15f), Size(w * 0.2f, h * 0.7f), r)
            }
            MediaGlyph.NEXT -> {
                drawPath(triangle(w * 0.08f, w * 0.5f, true), color)
                drawPath(triangle(w * 0.42f, w * 0.84f, true), color)
                drawRoundRect(color, Offset(w * 0.82f, h * 0.15f), Size(w * 0.1f, h * 0.7f), r)
            }
            MediaGlyph.PREVIOUS -> {
                drawRoundRect(color, Offset(w * 0.08f, h * 0.15f), Size(w * 0.1f, h * 0.7f), r)
                drawPath(triangle(w * 0.16f, w * 0.58f, false), color)
                drawPath(triangle(w * 0.5f, w * 0.92f, false), color)
            }
            MediaGlyph.MINUS -> drawRoundRect(color, Offset(w * 0.2f, h * 0.44f), Size(w * 0.6f, h * 0.12f), r)
            MediaGlyph.PLUS -> {
                drawRoundRect(color, Offset(w * 0.2f, h * 0.44f), Size(w * 0.6f, h * 0.12f), r)
                drawRoundRect(color, Offset(w * 0.44f, h * 0.2f), Size(w * 0.12f, h * 0.6f), r)
            }
        }
    }
}

/**
 * Music during a workout: what's playing, previous / play-pause / next, and volume.
 * Turning the bezel changes the volume, like the Digital Crown on Now Playing.
 */
@Composable
fun MusicPage(isCurrent: Boolean) {
    val context = LocalContext.current
    val music = remember { MusicControl(context) }
    val now by music.state.collectAsStateWithLifecycle()
    val focus = remember { FocusRequester() }
    var scrolled by remember { mutableFloatStateOf(0f) }

    DisposableEffect(Unit) {
        music.start()
        onDispose { music.stop() }
    }
    LaunchedEffect(isCurrent) { if (isCurrent) runCatching { focus.requestFocus() } }
    val small = screenSize() == ScreenSize.SMALL
    val playSize = if (small) 48.dp else 58.dp
    val sideSize = if (small) 38.dp else 44.dp
    val volSize = if (small) 28.dp else 32.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .onRotaryScrollEvent { e ->
                scrolled += e.verticalScrollPixels
                if (kotlin.math.abs(scrolled) > 48f) {
                    music.volume(up = scrolled > 0)
                    scrolled = 0f
                }
                true
            }
            .focusRequester(focus)
            .focusable()
            .padding(horizontal = 24.dp, vertical = if (small) 20.dp else 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(if (small) 3.dp else 6.dp, Alignment.CenterVertically),
    ) {
        Text(
            (now.app ?: "Music").uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Secondary,
        )
        Text(
            now.title ?: if (now.canSeeTrack) "Nothing playing" else "Now playing",
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth().basicMarquee(),
            textAlign = TextAlign.Center,
        )
        Text(
            now.artist ?: "Start music on your phone",
            style = MaterialTheme.typography.labelSmall,
            color = Palette.Secondary,
            maxLines = 1,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().basicMarquee(),
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalIconButton(onClick = music::previous, modifier = Modifier.size(sideSize)) {
                MediaIcon(MediaGlyph.PREVIOUS, Color.White, 18.dp)
            }
            FilledIconButton(
                onClick = music::playPause,
                modifier = Modifier.size(playSize),
                colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.Accent, contentColor = Color.Black),
            ) {
                MediaIcon(if (now.playing) MediaGlyph.PAUSE else MediaGlyph.PLAY, Color.Black, 24.dp)
            }
            FilledTonalIconButton(onClick = music::next, modifier = Modifier.size(sideSize)) {
                MediaIcon(MediaGlyph.NEXT, Color.White, 18.dp)
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FilledTonalIconButton(onClick = { music.volume(false) }, modifier = Modifier.size(volSize)) {
                MediaIcon(MediaGlyph.MINUS, Color.White, 14.dp)
            }
            val level = now.volume ?: 0.5f
            Canvas(Modifier.width(64.dp).height(6.dp)) {
                drawRoundRect(Palette.Track, cornerRadius = CornerRadius(size.height / 2))
                drawRoundRect(Color.White, size = Size(size.width * level, size.height), cornerRadius = CornerRadius(size.height / 2))
            }
            FilledTonalIconButton(onClick = { music.volume(true) }, modifier = Modifier.size(volSize)) {
                MediaIcon(MediaGlyph.PLUS, Color.White, 14.dp)
            }
        }
    }
}