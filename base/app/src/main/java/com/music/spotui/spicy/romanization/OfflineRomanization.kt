package com.music.spotui.spicy.romanization

/** Uses the script of the whole lyric to distinguish Japanese kanji from Chinese Han. */
internal object OfflineRomanization {
    fun fill(lines: Map<Int, String>, allText: String): Map<Int, String> {
        if (lines.isEmpty()) return emptyMap()
        val scripts = ScriptDetector.detect(allText)
        val readers = scripts.mapNotNull { script ->
            when (script) {
                Script.JAPANESE -> KanaOnlyJapaneseRomanizer
                Script.CHINESE -> PinyinRomanizer
                Script.KOREAN -> KoreanRomanizer
                Script.CYRILLIC -> CyrillicRomanizer
                Script.GREEK -> GreekRomanizer
                Script.LATIN -> null
            }
        }
        if (readers.isEmpty()) return emptyMap()
        return lines.mapNotNull { (index, line) ->
            val converted = readers.fold(line) { text, reader ->
                if (reader.isAvailable() && ScriptDetector.contains(reader.script, text)) reader.romanize(text) else text
            }
            converted.takeIf { it != line && it.isNotBlank() }?.let { index to it }
        }.toMap()
    }
}
