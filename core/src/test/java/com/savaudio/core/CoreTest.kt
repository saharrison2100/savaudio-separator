package com.savaudio.core

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CoreTest {
    private val speaker = OutputDevice(1, DeviceKind.SPEAKER, "Phone")
    private val buds = OutputDevice(7, DeviceKind.BLUETOOTH, "Buds Pro")
    private val car = OutputDevice(9, DeviceKind.BLUETOOTH, "Car Kit")

    @Test fun mapsRoutableTypes() {
        assertEquals(DeviceKind.SPEAKER, DeviceKind.fromType(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER))
        assertEquals(DeviceKind.BLUETOOTH, DeviceKind.fromType(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP))
        assertEquals(DeviceKind.BLE_AUDIO, DeviceKind.fromType(AudioDeviceInfo.TYPE_BLE_HEADSET))
        assertEquals(DeviceKind.USB, DeviceKind.fromType(AudioDeviceInfo.TYPE_USB_HEADSET))
        assertEquals(DeviceKind.WIRED, DeviceKind.fromType(AudioDeviceInfo.TYPE_WIRED_HEADPHONES))
    }

    @Test fun rejectsCallAndInternalOutputs() {
        assertNull(DeviceKind.fromType(AudioDeviceInfo.TYPE_BUILTIN_EARPIECE))
        assertNull(DeviceKind.fromType(AudioDeviceInfo.TYPE_BLUETOOTH_SCO))
        assertNull(DeviceKind.fromType(AudioDeviceInfo.TYPE_TELEPHONY))
        assertNull(DeviceKind.fromType(AudioDeviceInfo.TYPE_REMOTE_SUBMIX))
    }

    @Test fun exactNameWins() {
        val rule = RouteRule("com.music", DeviceKind.BLUETOOTH, "Car Kit")
        assertEquals(car, RoutePlanner.resolve(rule, listOf(speaker, buds, car)))
    }

    @Test fun fallsBackToSameKindAfterRename() {
        val rule = RouteRule("com.music", DeviceKind.BLUETOOTH, "Old Name")
        assertEquals(buds, RoutePlanner.resolve(rule, listOf(speaker, buds)))
    }

    @Test fun unpinsWhenDeviceIsLost() {
        val rule = RouteRule("com.music", DeviceKind.BLUETOOTH, "Buds Pro")
        assertNull(RoutePlanner.resolve(rule, listOf(speaker)))
        assertTrue(RoutePlanner.plan(listOf(rule), listOf(speaker)).isEmpty())
    }

    @Test fun pinSurvivesReconnectWithNewId() {
        val rule = RouteRule("com.music", DeviceKind.BLUETOOTH, "Buds Pro")
        val reconnected = buds.copy(id = 42)
        assertEquals(42, RoutePlanner.plan(listOf(rule), listOf(speaker, reconnected))["com.music"]?.id)
    }

    @Test fun disabledRuleIsIgnored() {
        val rule = RouteRule("com.music", DeviceKind.SPEAKER, "Phone", enabled = false)
        assertNull(RoutePlanner.resolve(rule, listOf(speaker)))
    }

    @Test fun planKeepsOtherAppsOnTheirOwnOutputs() {
        val rules = listOf(
            RouteRule("com.music", DeviceKind.BLUETOOTH, "Buds Pro"),
            RouteRule("com.video", DeviceKind.SPEAKER, "Phone"),
        )
        val plan = RoutePlanner.plan(rules, listOf(speaker, buds))
        assertEquals(buds, plan["com.music"])
        assertEquals(speaker, plan["com.video"])
    }

    @Test fun codecRoundTripsAwkwardNames() {
        val rules = listOf(
            RouteRule("com.music", DeviceKind.BLUETOOTH, "Sav's\tBuds\\Pro\nv2"),
            RouteRule("com.video", DeviceKind.USB, "DAC", enabled = false),
        )
        assertEquals(rules, RuleCodec.decode(RuleCodec.encode(rules)))
    }

    @Test fun codecSkipsCorruptLines() {
        assertTrue(RuleCodec.decode(null).isEmpty())
        assertTrue(RuleCodec.decode("garbage").isEmpty())
        assertEquals(1, RuleCodec.decode("com.a\tSPEAKER\ttrue\tPhone\ncom.b\tNOPE\ttrue\tx").size)
    }
}
