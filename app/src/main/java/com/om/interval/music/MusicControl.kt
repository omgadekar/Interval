package com.om.interval.music

import android.content.ComponentName
import android.content.Context
import android.media.AudioManager
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class NowPlaying(
    val title: String? = null,
    val artist: String? = null,
    val app: String? = null,
    val playing: Boolean = false,
    /** 0..1, or null when unknown. */
    val volume: Float? = null,
    /** False until notification access is granted; controls still work, but without track names. */
    val canSeeTrack: Boolean = false,
)

/**
 * Controls whatever is playing: your phone's player as bridged to the watch, or a player on the watch.
 * With notification access it talks to the media session directly and shows the track; without it,
 * it falls back to media buttons, like pressing play on a headset.
 */
class MusicControl(private val context: Context) {
    private val audio = context.getSystemService(AudioManager::class.java)
    private val sessions = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListener::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var controller: MediaController? = null

    private val _state = MutableStateFlow(NowPlaying())
    val state: StateFlow<NowPlaying> = _state.asStateFlow()

    private val sessionsChanged = MediaSessionManager.OnActiveSessionsChangedListener { list -> pick(list.orEmpty()) }

    private val callback = object : MediaController.Callback() {
        override fun onMetadataChanged(metadata: MediaMetadata?) = publish()
        override fun onPlaybackStateChanged(state: PlaybackState?) = publish()
        override fun onAudioInfoChanged(info: MediaController.PlaybackInfo) = publish()
        override fun onSessionDestroyed() = pick(emptyList())
    }

    fun start() {
        try {
            sessions.addOnActiveSessionsChangedListener(sessionsChanged, listener, handler)
            pick(sessions.getActiveSessions(listener))
        } catch (_: SecurityException) {
            _state.value = NowPlaying(canSeeTrack = false, volume = streamVolume())
        }
    }

    fun stop() {
        runCatching { sessions.removeOnActiveSessionsChangedListener(sessionsChanged) }
        controller?.unregisterCallback(callback)
        controller = null
    }

    private fun pick(list: List<MediaController>) {
        controller?.unregisterCallback(callback)
        controller = list.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING } ?: list.firstOrNull()
        controller?.registerCallback(callback, handler)
        publish()
    }

    private fun publish() {
        val c = controller
        val meta = c?.metadata
        val info = c?.playbackInfo
        _state.value = NowPlaying(
            title = meta?.getString(MediaMetadata.METADATA_KEY_TITLE),
            artist = meta?.getString(MediaMetadata.METADATA_KEY_ARTIST) ?: meta?.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST),
            app = c?.packageName?.let(::appLabel),
            playing = c?.playbackState?.state == PlaybackState.STATE_PLAYING,
            volume = info?.takeIf { it.maxVolume > 0 }?.let { it.currentVolume.toFloat() / it.maxVolume } ?: streamVolume(),
            canSeeTrack = true,
        )
    }

    private fun appLabel(pkg: String): String = runCatching {
        val pm = context.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    }.getOrDefault(pkg)

    private fun streamVolume(): Float? {
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        return if (max > 0) audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max else null
    }

    private fun key(code: Int) {
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    fun playPause() {
        val c = controller
        when {
            c == null -> {
                key(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
                _state.value = _state.value.copy(playing = !_state.value.playing)
            }
            _state.value.playing -> c.transportControls.pause()
            else -> c.transportControls.play()
        }
    }

    fun next() = controller?.transportControls?.skipToNext() ?: key(KeyEvent.KEYCODE_MEDIA_NEXT)

    fun previous() = controller?.transportControls?.skipToPrevious() ?: key(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    fun volume(up: Boolean) {
        val direction = if (up) AudioManager.ADJUST_RAISE else AudioManager.ADJUST_LOWER
        val c = controller
        if (c != null) c.adjustVolume(direction, 0)
        else audio.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, 0)
        if (c == null) _state.value = _state.value.copy(volume = streamVolume())
    }
}