package com.sloth.carwidget

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.delay

/** Chirps and buzzes. */
object Feedback {
    enum class Kind { TAP, LOCKED, UNLOCKED, FAILED }

    fun vibrate(context: Context, kind: Kind) {
        if (!Prefs(context).vibrate) return
        val vib = if (Build.VERSION.SDK_INT >= 31)
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        else @Suppress("DEPRECATION") context.getSystemService(Vibrator::class.java)
        val effect = when (kind) {
            Kind.TAP -> VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE)
            Kind.LOCKED -> VibrationEffect.createWaveform(longArrayOf(0, 60), -1)
            Kind.UNLOCKED -> VibrationEffect.createWaveform(longArrayOf(0, 40, 90, 40), -1)
            Kind.FAILED -> VibrationEffect.createWaveform(longArrayOf(0, 200, 120, 350), -1)
        }
        vib?.vibrate(effect)
    }

    /** Plays the sound and suspends until it is done, so the worker doesn't die mid-chirp. */
    suspend fun play(context: Context, kind: Kind) {
        vibrate(context, kind)
        if (!Prefs(context).sound) return
        val res = when (kind) {
            Kind.LOCKED -> R.raw.chirp_lock
            Kind.UNLOCKED -> R.raw.chirp_unlock
            Kind.FAILED -> R.raw.chirp_fail
            Kind.TAP -> return
        }
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val mp = MediaPlayer.create(context, res, attrs, 0) ?: return
        try {
            mp.start()
            delay(mp.duration.toLong() + 100)
        } finally {
            mp.release()
        }
    }
}
