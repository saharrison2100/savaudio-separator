package com.savaudio.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SeparationTest {
    @Test fun defaultsSeparateButDoNotMute() {
        val s = SeparationSettings()
        assertTrue(s.separateVolume)
        assertFalse(s.shouldMuteOriginals)
    }

    @Test fun muteNeedsSeparateVolume() {
        assertTrue(SeparationSettings(separateVolume = true, muteOriginals = true).shouldMuteOriginals)
        assertFalse(SeparationSettings(separateVolume = false, muteOriginals = true).shouldMuteOriginals)
    }

    @Test fun indexIsClampedAndRounded() {
        assertEquals(0, VolumeMath.toIndex(-1f, 7))
        assertEquals(7, VolumeMath.toIndex(2f, 7))
        assertEquals(5, VolumeMath.toIndex(0.7f, 7))
        assertEquals(0, VolumeMath.toIndex(0.5f, 0))
        assertEquals(0, VolumeMath.toIndex(Float.NaN, 7))
    }

    @Test fun fractionRoundTrips() {
        for (i in 0..15) assertEquals(i, VolumeMath.toIndex(VolumeMath.toFraction(i, 15), 15))
        assertEquals(0f, VolumeMath.toFraction(3, 0), 0f)
    }
}
