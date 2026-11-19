/* SPDX-License-Identifier: GPL-3.0-only */
package com.music.spotui.automix

import app.myspoti.automix.CustomTransition
import app.myspoti.automix.EnvelopePoint
import app.myspoti.automix.MixPlan
import app.myspoti.automix.TrackAnalysis
import app.myspoti.automix.TransitionPreset
import com.music.spotui.di.Graph
import com.music.spotui.di.LocalPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persistent store for user-authored transitions between consecutive tracks `(fromTrackId -> toTrackId)`,
 * optionally scoped to a playlist/album context (`contextUri`).
 */
object CustomTransitionStore {
    private const val FILE_NAME = "custom_transitions.json"

    private val lock = Any()
    private val entries = LinkedHashMap<String, CustomTransition>()
    private var loaded = false
    private var overrideFile: File? = null

    private val _version = MutableStateFlow(0)
    val version: StateFlow<Int> = _version.asStateFlow()

    internal fun keyOf(fromTrackId: String, toTrackId: String, contextUri: String? = null): String {
        val ctx = contextUri?.trim().orEmpty()
        return if (ctx.isNotEmpty()) "$ctx|$fromTrackId->$toTrackId" else "$fromTrackId->$toTrackId"
    }

    fun get(fromTrackId: String, toTrackId: String, contextUri: String? = null): CustomTransition? {
        if (fromTrackId.isBlank() || toTrackId.isBlank()) return null
        synchronized(lock) {
            ensureLoadedLocked()
            if (!contextUri.isNullOrBlank()) {
                entries[keyOf(fromTrackId, toTrackId, contextUri)]?.let { return it }
            }
            return entries[keyOf(fromTrackId, toTrackId, null)]
        }
    }

    fun hasCustom(fromTrackId: String, toTrackId: String, contextUri: String? = null): Boolean =
        get(fromTrackId, toTrackId, contextUri) != null

    fun save(transition: CustomTransition, contextUri: String? = null) {
        if (transition.fromTrackId.isBlank() || transition.toTrackId.isBlank()) return
        synchronized(lock) {
            ensureLoadedLocked()
            val key = keyOf(transition.fromTrackId, transition.toTrackId, contextUri)
            entries[key] = transition
            persistLocked()
            _version.value = _version.value + 1
        }
        notifyPlayers()
    }

    fun remove(fromTrackId: String, toTrackId: String, contextUri: String? = null) {
        if (fromTrackId.isBlank() || toTrackId.isBlank()) return
        val changed = synchronized(lock) {
            ensureLoadedLocked()
            var removed = false
            if (!contextUri.isNullOrBlank()) {
                if (entries.remove(keyOf(fromTrackId, toTrackId, contextUri)) != null) removed = true
            }
            if (entries.remove(keyOf(fromTrackId, toTrackId, null)) != null) removed = true
            if (removed) {
                persistLocked()
                _version.value = _version.value + 1
            }
            removed
        }
        if (changed) notifyPlayers()
    }

    fun toMixPlan(
        custom: CustomTransition,
        analysisA: TrackAnalysis?,
        analysisB: TrackAnalysis?,
        basePlan: MixPlan? = null,
    ): MixPlan = CustomTransition.compileToMixPlan(custom, analysisA, analysisB, basePlan)

    private fun notifyPlayers() {
        runCatching { LocalPlayer.onCustomTransitionsChanged() }
        runCatching { TransitionEditorBridge.refresh() }
    }

    private fun storageFileLocked(): File? {
        overrideFile?.let { return it }
        return runCatching { File(Graph.app.filesDir, FILE_NAME) }.getOrNull()
    }

    private fun ensureLoadedLocked() {
        if (loaded) return
        loaded = true
        val file = storageFileLocked() ?: return
        if (!file.exists()) return
        runCatching {
            val text = file.readText(Charsets.UTF_8)
            if (text.isBlank()) return
            val root = JSONObject(text)
            val items = root.optJSONArray("transitions") ?: return
            for (i in 0 until items.length()) {
                val obj = items.optJSONObject(i) ?: continue
                val key = obj.optString("key", "")
                val parsed = parseTransition(obj) ?: continue
                val effectiveKey = key.ifBlank { keyOf(parsed.fromTrackId, parsed.toTrackId, null) }
                entries[effectiveKey] = parsed
            }
        }
    }

    private fun persistLocked() {
        val file = storageFileLocked() ?: return
        runCatching {
            file.parentFile?.mkdirs()
            val arr = JSONArray()
            for ((k, tr) in entries) {
                arr.put(serializeTransition(k, tr))
            }
            val root = JSONObject().apply {
                put("version", 1)
                put("transitions", arr)
            }
            val tmp = File(file.parentFile, "${file.name}.tmp")
            tmp.writeText(root.toString(), Charsets.UTF_8)
            if (!tmp.renameTo(file)) {
                file.writeText(root.toString(), Charsets.UTF_8)
                tmp.delete()
            }
        }
    }

    private fun serializeTransition(key: String, tr: CustomTransition): JSONObject = JSONObject().apply {
        put("key", key)
        put("fromTrackId", tr.fromTrackId)
        put("toTrackId", tr.toTrackId)
        put("exitA", tr.exitA)
        put("entryB", tr.entryB)
        put("overlapBars", tr.overlapBars)
        put("overlapSec", tr.overlapSec)
        put("preset", tr.preset.name)
        put("eqSwap", tr.eqSwap)
        put("updatedAtMs", tr.updatedAtMs)
        put("outgoingCurve", serializeCurve(tr.outgoingCurve))
        put("incomingCurve", serializeCurve(tr.incomingCurve))
    }

    private fun serializeCurve(points: List<EnvelopePoint>): JSONArray = JSONArray().apply {
        for (pt in points) {
            put(JSONObject().apply {
                put("t", pt.t.toDouble())
                put("gain", pt.gain.toDouble())
            })
        }
    }

    private fun parseTransition(obj: JSONObject): CustomTransition? {
        val fromId = obj.optString("fromTrackId", "")
        val toId = obj.optString("toTrackId", "")
        if (fromId.isBlank() || toId.isBlank()) return null
        val exitA = obj.optDouble("exitA", 0.0).let { if (it.isNaN()) 0.0 else it }
        val entryB = obj.optDouble("entryB", 0.0).let { if (it.isNaN()) 0.0 else it }
        val overlapBars = obj.optInt("overlapBars", 8).coerceIn(1, 64)
        val overlapSec = obj.optDouble("overlapSec", 8.0).let { if (it.isNaN() || it <= 0.0) 8.0 else it }
        val presetName = obj.optString("preset", TransitionPreset.AUTO.name)
        val preset = runCatching { TransitionPreset.valueOf(presetName) }.getOrDefault(TransitionPreset.AUTO)
        val eqSwap = obj.optBoolean("eqSwap", true)
        val updatedAtMs = obj.optLong("updatedAtMs", 0L)
        val outgoing = parseCurve(obj.optJSONArray("outgoingCurve"))
        val incoming = parseCurve(obj.optJSONArray("incomingCurve"))
        return CustomTransition(
            fromTrackId = fromId,
            toTrackId = toId,
            exitA = exitA,
            entryB = entryB,
            overlapBars = overlapBars,
            overlapSec = overlapSec,
            preset = preset,
            outgoingCurve = outgoing,
            incomingCurve = incoming,
            eqSwap = eqSwap,
            updatedAtMs = updatedAtMs,
        )
    }

    private fun parseCurve(arr: JSONArray?): List<EnvelopePoint> {
        if (arr == null || arr.length() == 0) return emptyList()
        val out = ArrayList<EnvelopePoint>(arr.length())
        for (i in 0 until arr.length()) {
            val pt = arr.optJSONObject(i) ?: continue
            val t = pt.optDouble("t", Double.NaN)
            val gain = pt.optDouble("gain", Double.NaN)
            if (!t.isNaN() && !gain.isNaN()) {
                out.add(EnvelopePoint(t.toFloat().coerceIn(0f, 1f), gain.toFloat().coerceIn(0f, 1f)))
            }
        }
        return out
    }

    /** Test hook: redirect disk persistence to [file] and force reload on next access. */
    internal fun replaceStorageFileForTest(file: File?) {
        synchronized(lock) {
            overrideFile = file
            entries.clear()
            loaded = false
        }
    }

    /** Test hook: clear in-memory state and reset test file override. */
    internal fun clearForTest() {
        synchronized(lock) {
            entries.clear()
            overrideFile = null
            loaded = false
            _version.value = 0
        }
    }
}
