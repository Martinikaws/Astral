/*
 * Adapted from Square (https://github.com/Lelonio/Square), GPL-3.0, Copyright (C) Lelonio,
 * app/src/main/java/dev/lelonio/square/playback/AudioRoute.kt. Modified for Astral, 2026-11.
 *
 * Changes: renamed OutputRoute (Astral has other AudioRoutes), moved to Astral's package, logging under Astral's tag, and the key and kind of an
 * output are made by a function of its type and name ([describe]) so they can be tested without a
 * phone.
 *
 * This file is part of Astral. It is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3 of the License. It is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU General Public License (base/licenses/GPL-3.0.txt, or
 * <https://www.gnu.org/licenses/>) for more details.
 */
package com.music.spotui.di

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Where the music is coming out: the phone, a cable, or which pair of Bluetooth headphones.
 *
 * What the equaliser keys its curves by. A Bluetooth or USB device is told apart by its own name
 * (two pairs of the same model share a curve, which is what they would want anyway); the phone's
 * speaker and a cable are one place each, since a cable says nothing about what is on its end.
 */
object OutputRoute {

    enum class Kind { SPEAKER, WIRED, BLUETOOTH, USB, OTHER }

    data class Output(
        /** Stable across connections: what a curve is saved under. */
        val key: String,
        val kind: Kind,
        /** The device's own name for Bluetooth and USB, empty for the rest. */
        val name: String,
    )

    val SPEAKER = Output("speaker", Kind.SPEAKER, "")

    private val _current = MutableStateFlow(SPEAKER)

    /** The output in use now (the speaker until the system has been asked). */
    val current: StateFlow<Output> = _current.asStateFlow()

    private var manager: AudioManager? = null
    private val listeners = mutableListOf<(Output) -> Unit>()
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** Called on the main thread whenever the output changes. */
    fun onChange(listener: (Output) -> Unit) {
        listeners += listener
    }

    /** Starts watching the outputs; once. */
    fun start(context: Context) {
        if (manager != null) return
        val audio = context.applicationContext.getSystemService(AudioManager::class.java) ?: return
        manager = audio
        audio.registerAudioDeviceCallback(
            object : AudioDeviceCallback() {
                override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) = settle()
                override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) = settle()
            },
            main,
        )
        evaluate()
    }

    /**
     * Looks again now, and twice more as the system catches up: a device announces itself before
     * the audio is moved to it, and asked at once the answer is still the speaker it replaces.
     */
    private fun settle() {
        evaluate()
        main.postDelayed(::evaluate, 600)
        main.postDelayed(::evaluate, 2000)
    }

    private fun evaluate() {
        val audio = manager ?: return
        val outputs = runCatching { audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList() }
            .getOrDefault(emptyList())
        val chosen = routed(audio, outputs) ?: likeliest(outputs)
        val output = chosen?.let { describe(it.type, it.productName?.toString().orEmpty()) } ?: SPEAKER
        if (output == _current.value) return
        _current.value = output
        android.util.Log.i("AstralEq", "output now ${output.kind}")
        listeners.forEach { it(output) }
    }

    /** The device media is actually routed to, where the system says so (Android 13 and later). */
    private fun routed(audio: AudioManager, outputs: List<AudioDeviceInfo>): AudioDeviceInfo? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val media = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()
        val target = runCatching { audio.getAudioDevicesForAttributes(media).firstOrNull() }
            .getOrNull() ?: return null
        return outputs.firstOrNull { it.type == target.type && it.address == target.address }
            ?: outputs.firstOrNull { it.type == target.type }
    }

    /** Before Android 13 the routing is not public: whatever was plugged in wins, Bluetooth over a cable. */
    private fun likeliest(outputs: List<AudioDeviceInfo>): AudioDeviceInfo? =
        outputs.filter { kindOf(it.type) != Kind.OTHER || it.type == AudioDeviceInfo.TYPE_HDMI }
            .maxByOrNull {
                when (kindOf(it.type)) {
                    Kind.BLUETOOTH -> if (it.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) 1 else 5
                    Kind.USB -> 4
                    Kind.WIRED -> 3
                    Kind.OTHER -> 2
                    Kind.SPEAKER -> if (it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) 1 else 0
                }
            }

    /** The output a device of [type] called [productName] is. */
    fun describe(type: Int, productName: String): Output {
        val kind = kindOf(type)
        val name = productName.trim()
        return when (kind) {
            Kind.SPEAKER -> SPEAKER
            Kind.WIRED -> Output("wired", Kind.WIRED, "")
            Kind.BLUETOOTH -> Output(if (name.isEmpty()) "bt" else "bt:$name", kind, name)
            Kind.USB -> Output(if (name.isEmpty()) "usb" else "usb:$name", kind, name)
            Kind.OTHER -> Output("type:$type", kind, name)
        }
    }

    /** What kind of place [key] stands for, from the key alone. */
    fun kindOf(key: String): Kind = when {
        key == "speaker" -> Kind.SPEAKER
        key == "wired" -> Kind.WIRED
        key == "bt" || key.startsWith("bt:") -> Kind.BLUETOOTH
        key == "usb" || key.startsWith("usb:") -> Kind.USB
        else -> Kind.OTHER
    }

    /** The kind of a device type (the numbers of [AudioDeviceInfo]). */
    fun kindOf(type: Int): Kind = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE,
        -> Kind.SPEAKER

        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_AUX_LINE,
        -> Kind.WIRED

        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST,
        AudioDeviceInfo.TYPE_HEARING_AID,
        -> Kind.BLUETOOTH

        AudioDeviceInfo.TYPE_USB_HEADSET,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_DOCK,
        -> Kind.USB

        else -> Kind.OTHER
    }
}
