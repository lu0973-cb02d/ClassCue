package com.classcue.kexing.engine

import java.util.concurrent.ConcurrentHashMap

/**
 * Finds a name in a transcript by pronunciation instead of by characters, so "张薇" or "章伟" in
 * the speech-recognition output still matches the configured name "张伟".
 *
 * Syllables are compared without tones and with common Mandarin accent merges: zh/z, ch/c, sh/s,
 * n/l and the front/back nasal finals in/ing, en/eng, an/ang. The first character of a name also
 * accepts its surname reading (单 → shan, 曾 → zeng).
 *
 * @param charPinyin pinyin of one character in lower-case ASCII, or null if it is not a Han character.
 * @param surnamePinyin pinyin of a whole name using surname readings, syllables separated by spaces.
 */
class PinyinNameMatcher(
    private val charPinyin: (Char) -> String?,
    private val surnamePinyin: (String) -> String? = { null }
) : CueClassifier.NameSoundMatcher {
    private val charCache = ConcurrentHashMap<Char, String>()
    private val nameCache = ConcurrentHashMap<String, List<Set<String>>>()

    override fun soundsLike(text: String, name: String): Boolean {
        val target = nameSyllables(name) ?: return false
        if (target.isEmpty() || text.length < target.size) return false
        val spoken = text.map { syllable(it)?.let(::fuzzy) }
        for (start in 0..spoken.size - target.size) {
            var matched = true
            for (offset in target.indices) {
                val syllable = spoken[start + offset]
                if (syllable == null || syllable !in target[offset]) {
                    matched = false
                    break
                }
            }
            if (matched) return true
        }
        return false
    }

    /** Per-character accepted readings of [name], or null when it contains non-Han characters. */
    private fun nameSyllables(name: String): List<Set<String>>? {
        nameCache[name]?.let { return it.ifEmpty { null } }
        val plain = name.map { syllable(it) }
        val result = if (plain.any { it == null }) {
            emptyList()
        } else {
            val surname = runCatching { surnamePinyin(name) }.getOrNull()
                ?.trim()
                ?.split(Regex("\\s+"))
                ?.takeIf { it.size == name.length }
            plain.mapIndexed { index, reading ->
                buildSet {
                    add(fuzzy(requireNotNull(reading)))
                    surname?.getOrNull(index)?.let { add(fuzzy(it)) }
                }
            }
        }
        nameCache[name] = result
        return result.ifEmpty { null }
    }

    private fun syllable(c: Char): String? {
        if (!isHan(c)) return null
        charCache[c]?.let { return it.ifEmpty { null } }
        val value = runCatching { charPinyin(c) }.getOrNull()
            ?.trim()
            ?.lowercase()
            ?.takeIf { SYLLABLE.matches(it) }
            .orEmpty()
        charCache[c] = value
        return value.ifEmpty { null }
    }

    companion object {
        private val SYLLABLE = Regex("[a-z]{1,6}")

        fun isHan(c: Char): Boolean = Character.UnicodeScript.of(c.code) == Character.UnicodeScript.HAN

        /** Collapses accent-dependent distinctions so near-homophones compare equal. */
        fun fuzzy(syllable: String): String {
            var s = syllable
            s = when {
                s.startsWith("zh") -> "z" + s.drop(2)
                s.startsWith("ch") -> "c" + s.drop(2)
                s.startsWith("sh") -> "s" + s.drop(2)
                s.startsWith("n") && s != "n" && s != "ng" -> "l" + s.drop(1)
                else -> s
            }
            if (s.endsWith("ing") || s.endsWith("eng") || s.endsWith("ang")) s = s.dropLast(1)
            return s
        }
    }
}
