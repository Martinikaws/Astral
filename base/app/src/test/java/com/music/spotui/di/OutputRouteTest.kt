package com.music.spotui.di

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Test

class OutputRouteTest {
    @Test fun keysSayWhatKindOfPlaceTheyAre() {
        assertEquals(OutputRoute.Kind.SPEAKER, OutputRoute.kindOf("speaker"))
        assertEquals(OutputRoute.Kind.WIRED, OutputRoute.kindOf("wired"))
        assertEquals(OutputRoute.Kind.BLUETOOTH, OutputRoute.kindOf("bt"))
        assertEquals(OutputRoute.Kind.BLUETOOTH, OutputRoute.kindOf("bt:Buds Pro"))
        assertEquals(OutputRoute.Kind.USB, OutputRoute.kindOf("usb:DAC"))
        assertEquals(OutputRoute.Kind.OTHER, OutputRoute.kindOf("type:9"))
        // A name that merely starts like a kind is not that kind.
        assertEquals(OutputRoute.Kind.OTHER, OutputRoute.kindOf("btx"))
    }

    @Test fun twoPairsOfTheSameNameShareACurveAndNamesAreTold() {
        val a = OutputRoute.describe(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, " Buds ")
        val b = OutputRoute.describe(AudioDeviceInfo.TYPE_BLE_HEADSET, "Buds")
        assertEquals("bt:Buds", a.key)
        assertEquals(a.key, b.key)
        assertEquals("Buds", a.name)
        assertEquals("bt", OutputRoute.describe(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, "").key)
    }

    @Test fun aCableAndTheSpeakerAreOnePlaceEach() {
        assertEquals("wired", OutputRoute.describe(AudioDeviceInfo.TYPE_WIRED_HEADPHONES, "Some cable").key)
        assertEquals("wired", OutputRoute.describe(AudioDeviceInfo.TYPE_WIRED_HEADSET, "").key)
        assertEquals(OutputRoute.SPEAKER, OutputRoute.describe(AudioDeviceInfo.TYPE_BUILTIN_SPEAKER, "Pixel"))
        assertEquals("usb:DAC", OutputRoute.describe(AudioDeviceInfo.TYPE_USB_DEVICE, "DAC").key)
        assertEquals("type:${AudioDeviceInfo.TYPE_HDMI}", OutputRoute.describe(AudioDeviceInfo.TYPE_HDMI, "TV").key)
    }
}
