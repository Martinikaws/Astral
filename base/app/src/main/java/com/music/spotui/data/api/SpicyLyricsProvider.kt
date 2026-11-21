package com.music.spotui.data.api

import android.content.Context
import android.util.Log
import app.myspoti.engine.BuildConfig
import com.music.spotui.data.DataSaver
import com.music.spotui.data.entity.LyricLine
import com.music.spotui.data.entity.LyricWord
import com.music.spotui.data.entity.Lyrics
import com.music.spotui.data.offline.OfflineStore
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * Provider for Spicy Lyrics (`https://api.spicylyrics.org/v1/lyrics/{trackId}`),
 * supporting syllable/word/line-synced lyrics, opposite-aligned duet vocals, and
 * timed backing vocals, with graceful fallback on 401/403/404 or offline state.
 */
object SpicyLyricsProvider {
    private const val TAG = "SpicyLyricsProvider"
    private const val ENDPOINT = "https://api.spicylyrics.org/v1/lyrics/"
    private const val PREF_KEY = "spicy_lyrics_api_key"
    private const val TIMEOUT_MS = 5_000
    private const val UA = "Astral (https://github.com/Martinikaws/Astral)"
    private val clientKeyPattern = Regex("sl_pk_[A-Za-z0-9_-]+")
    private val trackIdPattern = Regex("[A-Za-z0-9]{22}")

    fun isConfigured(context: Context): Boolean = readApiKey(context) != null

    internal fun publishableKey(value: String?): String? =
        value?.trim()?.takeIf(clientKeyPattern::matches)

    /** Fetches lyrics for [trackId] from Spicy Lyrics, or returns null on miss/auth/network failure. */
    fun fetch(context: Context?, trackId: String, durationMs: Long = 0L): Lyrics? {
        if (context == null) return null
        val id = trackId.trim().substringAfterLast(':')
        if (!id.matches(trackIdPattern)) return null
        val key = readApiKey(context) ?: return null
        if (!runCatching { OfflineStore.isOnline(context) }.getOrDefault(true)) return null

        return runCatching {
            DataSaver.check()
            val encodedId = URLEncoder.encode(id, "UTF-8")
            val url = URL("$ENDPOINT$encodedId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                setRequestProperty("User-Agent", UA)
                setRequestProperty("Accept", "application/json")
                instanceFollowRedirects = false
                setRequestProperty("Authorization", "Bearer $key")
            }
            try {
                val code = conn.responseCode
                if (code == 401 || code == 403 || code == 404) {
                    Log.d(TAG, "HTTP $code for $id; falling back")
                    return null
                }
                if (code != 200) {
                    Log.d(TAG, "HTTP $code for $id")
                    return null
                }
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                parseResponse(body, durationMs)
            } finally {
                conn.disconnect()
            }
        }.getOrElse { e ->
            if (e !is DataSaver.Blocked) {
                Log.d(TAG, "fetch failed for $id: ${e.javaClass.simpleName}: ${e.message}")
            }
            null
        }
    }

    private fun readApiKey(context: Context): String? {
        val prefNames = listOf("spicy_lyrics", "lyrics", "${context.packageName}_preferences")
        for (name in prefNames) {
            val key = runCatching {
                context.getSharedPreferences(name, Context.MODE_PRIVATE).getString(PREF_KEY, null)
            }.getOrNull()
            publishableKey(key)?.let { return it }
        }
        return publishableKey(BuildConfig.SPICY_LYRICS_CLIENT_KEY)
    }

    /** Parses a Spicy Lyrics v1 JSON payload into [Lyrics], or null if empty/invalid. */
    internal fun parseResponse(json: String, durationMs: Long = 0L): Lyrics? {
        if (json.isBlank()) return null
        val root = runCatching { JSONObject(json) }.getOrNull() ?: return null
        val payload = root.optJSONObject("data") ?: root

        val type = (payload.optString("Type").ifBlank { payload.optString("type") }).lowercase()
        val content = payload.optJSONArray("Content")
            ?: payload.optJSONArray("content")
            ?: payload.optJSONArray("Lines")
            ?: payload.optJSONArray("lines")
            ?: return null

        if (content.length() == 0) return null

        val inSeconds = detectSecondsScale(content, durationMs)
        val lines = ArrayList<LyricLine>(content.length())

        for (i in 0 until content.length()) {
            val item = content.optJSONObject(i) ?: continue
            val itemType = (item.optString("Type").ifBlank { item.optString("type") }).lowercase()
            if (itemType == "gap" || itemType == "instrumental") continue

            val voice = when {
                item.has("OppositeAligned") -> if (item.optBoolean("OppositeAligned")) 2 else 1
                item.has("oppositeAligned") -> if (item.optBoolean("oppositeAligned")) 2 else 1
                item.has("voice") -> item.optInt("voice")
                else -> null
            }

            val lead = item.optJSONObject("Lead") ?: item.optJSONObject("lead") ?: item
            val syllables = lead.optJSONArray("Syllables")
                ?: lead.optJSONArray("syllables")
                ?: lead.optJSONArray("Words")
                ?: lead.optJSONArray("words")

            val parsedLead = if (syllables != null && syllables.length() > 0) {
                parseSyllables(syllables, inSeconds)
            } else {
                val rawText = (lead.optString("Text").ifBlank { lead.optString("text") }).trim()
                if (rawText.isEmpty()) continue
                val startMs = readTimeMs(lead, "StartTime", "startTime", "startMs", "time", inSeconds)
                ParsedText(rawText, startMs, null)
            }

            if (parsedLead.text.isBlank()) continue

            val bgArray = item.optJSONArray("Background") ?: item.optJSONArray("background")
            val parsedBg = if (bgArray != null && bgArray.length() > 0) {
                parseBackground(bgArray, inSeconds)
            } else null

            val translation = (item.optString("Translation").ifBlank { item.optString("translation") })
                .trim().ifEmpty { null }
            val romanization = (item.optString("Romanization").ifBlank { item.optString("romanization") })
                .trim().ifEmpty { null }

            lines += LyricLine(
                timeMs = parsedLead.startMs,
                text = parsedLead.text,
                words = parsedLead.words,
                translation = translation,
                romanization = romanization,
                voice = voice,
                background = parsedBg?.text,
                backgroundWords = parsedBg?.words,
            )
        }

        if (lines.isEmpty()) return null
        val synced = type != "static" && lines.any { it.timeMs > 0L || it.words != null }
        val language = (payload.optString("Language").ifBlank { payload.optString("language") })
            .trim().ifEmpty { null }
        val trLang = (payload.optString("TranslationLanguage").ifBlank { payload.optString("translationLanguage") })
            .trim().ifEmpty { null }

        return Lyrics(
            lines = lines,
            synced = synced,
            language = language,
            provider = "Spicy Lyrics",
            translationLanguage = trLang,
        )
    }

    private data class ParsedText(
        val text: String,
        val startMs: Long,
        val words: List<LyricWord>?,
    )

    private fun parseSyllables(arr: JSONArray, inSeconds: Boolean): ParsedText {
        val sb = StringBuilder()
        val words = ArrayList<LyricWord>(arr.length())
        var lineStart = Long.MAX_VALUE

        for (i in 0 until arr.length()) {
            val s = arr.optJSONObject(i) ?: continue
            val raw = s.optString("Text").ifEmpty { s.optString("text") }
            if (raw.isEmpty()) continue

            val startMs = readTimeMs(s, "StartTime", "startTime", "startMs", "time", inSeconds)
            val endMs = readTimeMs(s, "EndTime", "endTime", "endMs", "end", inSeconds).let { end ->
                if (end > startMs) end else {
                    val dur = readTimeMs(s, "Duration", "duration", "durationMs", "dur", inSeconds)
                    if (dur > 0L) startMs + dur else startMs
                }
            }
            if (startMs < lineStart) lineStart = startMs

            val partOfWord = when {
                s.has("PartOfWord") -> s.optBoolean("PartOfWord")
                s.has("partOfWord") -> s.optBoolean("partOfWord")
                s.has("IsPartOfWord") -> s.optBoolean("IsPartOfWord")
                s.has("isPartOfWord") -> s.optBoolean("isPartOfWord")
                else -> false
            }

            val needsSpace = i < arr.length() - 1 && !partOfWord && !raw.endsWith(" ")
            val token = if (needsSpace) "$raw " else raw
            sb.append(token)
            words += LyricWord(startMs = startMs, endMs = endMs, text = token)
        }

        val text = sb.toString().trimEnd()
        // Trim trailing space on the last word so wordSpans matches `text`.
        if (words.isNotEmpty() && words.last().text.endsWith(" ")) {
            val last = words.removeAt(words.lastIndex)
            words += last.copy(text = last.text.trimEnd())
        }
        return ParsedText(
            text = text,
            startMs = if (lineStart == Long.MAX_VALUE) 0L else lineStart,
            words = words.takeIf { it.isNotEmpty() },
        )
    }

    private fun parseBackground(arr: JSONArray, inSeconds: Boolean): ParsedText? {
        val allWords = ArrayList<LyricWord>()
        val parts = ArrayList<String>(arr.length())
        var earliest = Long.MAX_VALUE

        for (i in 0 until arr.length()) {
            val bg = arr.optJSONObject(i) ?: continue
            val syls = bg.optJSONArray("Syllables")
                ?: bg.optJSONArray("syllables")
                ?: bg.optJSONArray("Words")
                ?: bg.optJSONArray("words")
            if (syls != null && syls.length() > 0) {
                val parsed = parseSyllables(syls, inSeconds)
                val clean = parsed.text.removeSurrounding("(", ")").trim()
                if (clean.isNotEmpty()) {
                    parts += clean
                    if (parsed.startMs < earliest) earliest = parsed.startMs
                    parsed.words?.forEach { w ->
                        val cleaned = w.text.replace("(", "").replace(")", "")
                        if (cleaned.isNotBlank()) allWords += w.copy(text = cleaned)
                    }
                }
            } else {
                val raw = (bg.optString("Text").ifBlank { bg.optString("text") })
                    .trim().removeSurrounding("(", ")").trim()
                if (raw.isNotEmpty()) {
                    parts += raw
                    val t = readTimeMs(bg, "StartTime", "startTime", "startMs", "time", inSeconds)
                    if (t < earliest) earliest = t
                }
            }
        }

        if (parts.isEmpty()) return null
        return ParsedText(
            text = parts.joinToString(" "),
            startMs = if (earliest == Long.MAX_VALUE) 0L else earliest,
            words = allWords.takeIf { it.isNotEmpty() },
        )
    }

    private fun detectSecondsScale(content: JSONArray, durationMs: Long): Boolean {
        var maxVal = 0.0
        var hasFraction = false
        for (i in 0 until minOf(content.length(), 12)) {
            val item = content.optJSONObject(i) ?: continue
            val lead = item.optJSONObject("Lead") ?: item.optJSONObject("lead") ?: item
            val syls = lead.optJSONArray("Syllables") ?: lead.optJSONArray("syllables")
            val target = syls?.optJSONObject(0) ?: lead
            for (k in arrayOf("StartTime", "startTime", "EndTime", "endTime", "startMs", "time")) {
                if (!target.has(k)) continue
                val v = target.optDouble(k, -1.0)
                if (v < 0.0) continue
                if (v > maxVal) maxVal = v
                if (abs(v - v.roundToLong().toDouble()) > 1e-4) hasFraction = true
            }
        }
        if (hasFraction) return true
        if (durationMs > 10_000L && maxVal in 1.0..2_000.0) return true
        return false
    }

    private fun readTimeMs(
        obj: JSONObject,
        k1: String,
        k2: String,
        k3: String,
        k4: String,
        inSeconds: Boolean,
    ): Long {
        val raw = when {
            obj.has(k1) -> obj.optDouble(k1, 0.0)
            obj.has(k2) -> obj.optDouble(k2, 0.0)
            obj.has(k3) -> obj.optDouble(k3, 0.0)
            obj.has(k4) -> obj.optDouble(k4, 0.0)
            else -> return 0L
        }
        if (raw <= 0.0 || !raw.isFinite()) return 0L
        return if (inSeconds) (raw * 1000.0).roundToLong() else raw.roundToLong()
    }
}
