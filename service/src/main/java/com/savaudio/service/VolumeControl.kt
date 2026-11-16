package com.savaudio.service

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import com.savaudio.core.SeparationSettings
import com.savaudio.core.VolumeMath

/**
 * Keeps routed copies on the alarm volume so the media volume can be turned down
 * (or muted) without silencing them. Volumes are saved first and put back when routing stops.
 */
object VolumeControl {
    private const val SAVED_MUSIC = "saved_music"
    private const val SAVED_ALARM = "saved_alarm"

    fun usage(settings: SeparationSettings): Int =
        if (settings.separateVolume) AudioAttributes.USAGE_ALARM else AudioAttributes.USAGE_MEDIA

    fun applyOnStart(context: Context) {
        val store = RouterSettings.get(context)
        val settings = store.state.value
        if (!settings.separateVolume) return
        val audio = context.getSystemService(AudioManager::class.java)
        if (store.saved(SAVED_ALARM) == null) store.setSaved(SAVED_ALARM, audio.getStreamVolume(AudioManager.STREAM_ALARM))
        setStream(audio, AudioManager.STREAM_ALARM, routedIndex(audio, settings.routedLevel))
        if (settings.shouldMuteOriginals) {
            if (store.saved(SAVED_MUSIC) == null) store.setSaved(SAVED_MUSIC, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
            setStream(audio, AudioManager.STREAM_MUSIC, 0)
        }
    }

    /** Puts the alarm and media volumes back. Safe to call when nothing was saved. */
    fun restore(context: Context) {
        val store = RouterSettings.get(context)
        val audio = context.getSystemService(AudioManager::class.java)
        store.saved(SAVED_ALARM)?.let { setStream(audio, AudioManager.STREAM_ALARM, it) }
        store.saved(SAVED_MUSIC)?.let { setStream(audio, AudioManager.STREAM_MUSIC, it) }
        store.setSaved(SAVED_ALARM, null)
        store.setSaved(SAVED_MUSIC, null)
    }

    fun setRoutedLevel(context: Context, level: Float) {
        val store = RouterSettings.get(context)
        val settings = store.state.value.copy(routedLevel = level.coerceIn(0f, 1f))
        store.update(settings)
        if (settings.separateVolume && RouterState.status.value.running) {
            val audio = context.getSystemService(AudioManager::class.java)
            setStream(audio, AudioManager.STREAM_ALARM, routedIndex(audio, settings.routedLevel))
        }
    }

    private fun routedIndex(audio: AudioManager, level: Float): Int =
        VolumeMath.toIndex(level, audio.getStreamMaxVolume(AudioManager.STREAM_ALARM))

    private fun setStream(audio: AudioManager, stream: Int, index: Int) {
        try { audio.setStreamVolume(stream, index, 0) } catch (_: SecurityException) {}
    }
}
