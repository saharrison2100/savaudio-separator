package com.savaudio.service

import android.content.Context
import com.savaudio.core.SeparationSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Persistent separation settings, plus the volumes to put back when routing stops. */
class RouterSettings private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("settings", Context.MODE_PRIVATE)
    private val flow = MutableStateFlow(
        SeparationSettings(
            separateVolume = prefs.getBoolean("separate", true),
            muteOriginals = prefs.getBoolean("mute", false),
            routedLevel = prefs.getFloat("level", 0.7f),
        )
    )
    val state: StateFlow<SeparationSettings> = flow

    fun update(settings: SeparationSettings) {
        prefs.edit()
            .putBoolean("separate", settings.separateVolume)
            .putBoolean("mute", settings.muteOriginals)
            .putFloat("level", settings.routedLevel)
            .apply()
        flow.value = settings
    }

    internal fun saved(key: String): Int? = if (prefs.contains(key)) prefs.getInt(key, 0) else null

    internal fun setSaved(key: String, value: Int?) {
        val edit = prefs.edit()
        if (value == null) edit.remove(key) else edit.putInt(key, value)
        edit.apply()
    }

    companion object {
        @Volatile private var instance: RouterSettings? = null

        fun get(context: Context): RouterSettings =
            instance ?: synchronized(this) {
                instance ?: RouterSettings(context.applicationContext).also { instance = it }
            }
    }
}
