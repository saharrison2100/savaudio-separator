package com.savaudio.core

import android.media.AudioDeviceInfo

/** The families of output a route can be pinned to. */
enum class DeviceKind(val label: String) {
    SPEAKER("Phone speaker"),
    WIRED("Wired headphones"),
    BLUETOOTH("Bluetooth"),
    BLE_AUDIO("Bluetooth LE Audio"),
    USB("USB audio"),
    HDMI("HDMI");

    companion object {
        /** Maps an [AudioDeviceInfo] type to a routable kind, or null when the output cannot carry a route. */
        fun fromType(type: Int): DeviceKind? = when (type) {
            AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> SPEAKER
            AudioDeviceInfo.TYPE_WIRED_HEADSET,
            AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
            AudioDeviceInfo.TYPE_LINE_ANALOG,
            AudioDeviceInfo.TYPE_AUX_LINE -> WIRED
            AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> BLUETOOTH
            AudioDeviceInfo.TYPE_BLE_HEADSET,
            AudioDeviceInfo.TYPE_BLE_SPEAKER -> BLE_AUDIO
            AudioDeviceInfo.TYPE_USB_DEVICE,
            AudioDeviceInfo.TYPE_USB_ACCESSORY,
            AudioDeviceInfo.TYPE_USB_HEADSET -> USB
            AudioDeviceInfo.TYPE_HDMI,
            AudioDeviceInfo.TYPE_HDMI_ARC,
            AudioDeviceInfo.TYPE_HDMI_EARC -> HDMI
            else -> null
        }
    }
}
