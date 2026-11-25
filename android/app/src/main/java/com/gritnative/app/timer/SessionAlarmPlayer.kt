package com.gritnative.app.timer

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.Looper
import android.util.Log

/**
 * Audible fallback for devices where the completion notification cannot sound
 * (notifications denied, channel muted, or a channel created without sound).
 *
 * The player is held by this singleton for the whole ring, so the alarm does not
 * depend on a local Ringtone staying reachable in a short-lived receiver.
 */
object SessionAlarmPlayer {
    private const val TAG = "GritAlarm"
    /** A completion rings long enough to be found, without being endless. */
    private const val RING_MS = 20_000L

    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private val stopTask = Runnable { stopInternal() }

    fun play(context: Context, durationMs: Long = RING_MS) {
        handler.post {
            stopInternal()
            val uri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)
                ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION)
                ?: return@post
            val next = MediaPlayer()
            try {
                next.setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                next.setDataSource(context.applicationContext, uri)
                next.isLooping = true
                next.setOnPreparedListener { prepared ->
                    if (prepared === player) {
                        runCatching { prepared.start() }
                    }
                }
                next.setOnErrorListener { failed, what, extra ->
                    Log.w(TAG, "Alarm playback failed ($what/$extra)")
                    if (failed === player) stopInternal()
                    true
                }
                player = next
                next.prepareAsync()
                handler.removeCallbacks(stopTask)
                handler.postDelayed(stopTask, durationMs)
            } catch (error: Exception) {
                Log.w(TAG, "Could not prepare the alarm", error)
                runCatching { next.release() }
                player = null
            }
        }
    }

    fun stop() {
        handler.post { stopInternal() }
    }

    private fun stopInternal() {
        handler.removeCallbacks(stopTask)
        player?.let { current ->
            runCatching { if (current.isPlaying) current.stop() }
            runCatching { current.release() }
        }
        player = null
    }
}
