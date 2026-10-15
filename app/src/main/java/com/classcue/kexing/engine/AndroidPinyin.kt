package com.classcue.kexing.engine

import android.icu.text.Transliterator

/**
 * Pinyin from the ICU transliteration data that ships with Android. If a device build lacks the
 * Han-Latin rules, sound-alike name matching is simply turned off and exact matching still works.
 */
object AndroidPinyin {
    @Volatile private var cached: PinyinNameMatcher? = null
    @Volatile private var unavailable = false

    fun matcher(): PinyinNameMatcher? {
        cached?.let { return it }
        if (unavailable) return null
        return synchronized(this) {
            cached ?: runCatching {
                val plain = Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower")
                val names = runCatching {
                    Transliterator.getInstance("Han-Latin/Names; Latin-ASCII; Lower")
                }.getOrNull()
                PinyinNameMatcher(
                    charPinyin = { c -> synchronized(plain) { plain.transliterate(c.toString()) } },
                    surnamePinyin = { name -> names?.let { synchronized(it) { it.transliterate(name) } } }
                )
            }.onFailure { unavailable = true }.getOrNull()?.also { cached = it }
        }
    }
}
