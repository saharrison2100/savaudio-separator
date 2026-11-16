package com.savaudio.core

/** A connected output the user can pick. [id] is the platform device id and changes across reconnects. */
data class OutputDevice(val id: Int, val kind: DeviceKind, val name: String) {
    val displayName: String get() = if (kind == DeviceKind.SPEAKER) kind.label else "$name (${kind.label})"
}

/** "Play [packageName] on the output of this [kind] called [deviceName]". */
data class RouteRule(
    val packageName: String,
    val kind: DeviceKind,
    val deviceName: String,
    val enabled: Boolean = true,
)
