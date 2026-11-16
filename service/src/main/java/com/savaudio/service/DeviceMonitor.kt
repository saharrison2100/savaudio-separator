package com.savaudio.service

import android.content.Context
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import com.savaudio.core.DeviceKind
import com.savaudio.core.OutputDevice
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** Tracks the outputs that are connected right now. */
class DeviceMonitor(context: Context) {
    private val audioManager = context.applicationContext.getSystemService(AudioManager::class.java)
    private val state = MutableStateFlow<List<OutputDevice>>(emptyList())
    val devices: StateFlow<List<OutputDevice>> = state

    private val callback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) = refresh()
        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) = refresh()
    }

    fun start() {
        audioManager.registerAudioDeviceCallback(callback, Handler(Looper.getMainLooper()))
        refresh()
    }

    fun stop() {
        audioManager.unregisterAudioDeviceCallback(callback)
    }

    fun refresh() {
        state.value = outputs().mapNotNull { info ->
            val kind = DeviceKind.fromType(info.type) ?: return@mapNotNull null
            val name = info.productName?.toString().orEmpty().ifBlank { kind.label }
            OutputDevice(info.id, kind, name)
        }.distinctBy { it.kind to it.name }
    }

    fun find(id: Int): AudioDeviceInfo? = outputs().firstOrNull { it.id == id }

    private fun outputs(): Array<AudioDeviceInfo> = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
}
