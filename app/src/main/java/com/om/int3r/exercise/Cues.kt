package com.om.int3r.exercise

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.om.int3r.engine.PaceCoach

/**
 * Wrist cues. Each event has its own rhythm so you can tell them apart without looking:
 *  - run/jog starts: three long buzzes      - walk/recover starts: two long buzzes
 *  - 10 s / 100 m left: two short taps      - too fast: four quick taps
 *  - too slow: two medium buzzes, low-high  - workout complete: one very long buzz
 */
class Cues(context: Context) {
    private val vibrator: Vibrator =
        if (Build.VERSION.SDK_INT >= 31) context.getSystemService(VibratorManager::class.java).defaultVibrator
        else context.getSystemService(Vibrator::class.java)

    private var tone: ToneGenerator? = null

    var sound = false

    fun work() = play(longArrayOf(0, 450, 150, 450, 150, 450), beeps = 3)
    fun ease() = play(longArrayOf(0, 700, 200, 700), beeps = 2)
    fun warning() = play(longArrayOf(0, 120, 120, 120), beeps = 1)
    fun pace(verdict: PaceCoach.Verdict) = when (verdict) {
        PaceCoach.Verdict.TOO_FAST -> play(longArrayOf(0, 90, 90, 90, 90, 90, 90, 90), beeps = 0)
        PaceCoach.Verdict.TOO_SLOW -> play(longArrayOf(0, 300, 200, 300), beeps = 0)
    }
    fun finished() = play(longArrayOf(0, 1500), beeps = 1)

    @Suppress("DEPRECATION")
    private fun play(timings: LongArray, beeps: Int) {
        val effect = VibrationEffect.createWaveform(timings, -1)
        // Alarm usage so cues still come through with the screen off or in Do Not Disturb.
        if (Build.VERSION.SDK_INT >= 33) {
            vibrator.vibrate(effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM))
        } else {
            vibrator.vibrate(effect, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build())
        }
        if (sound && beeps > 0) {
            val gen = tone ?: runCatching { ToneGenerator(AudioManager.STREAM_ALARM, 90) }.getOrNull()?.also { tone = it }
            gen?.startTone(if (beeps >= 3) ToneGenerator.TONE_PROP_BEEP2 else ToneGenerator.TONE_PROP_BEEP, 250)
        }
    }

    fun release() {
        tone?.release()
        tone = null
    }
}