package com.savaudio.core

/** How routed sound is kept apart from the original sound of the same app. */
data class SeparationSettings(
    /** Play routed copies on the alarm volume so the media volume only affects originals. */
    val separateVolume: Boolean = true,
    /** Set the media volume to zero while routing, so only the routed copies are heard. */
    val muteOriginals: Boolean = false,
    /** Loudness of the routed copies, 0 to 1. */
    val routedLevel: Float = 0.7f,
) {
    /** Muting originals is only safe when routed copies do not share the media volume. */
    val shouldMuteOriginals: Boolean get() = separateVolume && muteOriginals
}

object VolumeMath {
    fun toIndex(fraction: Float, max: Int): Int {
        if (max <= 0 || fraction.isNaN()) return 0
        return Math.round(fraction.coerceIn(0f, 1f) * max).coerceIn(0, max)
    }

    fun toFraction(index: Int, max: Int): Float =
        if (max <= 0) 0f else (index.toFloat() / max).coerceIn(0f, 1f)
}
