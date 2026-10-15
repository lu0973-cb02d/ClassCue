package com.classcue.kexing.engine

import com.ibm.icu.text.Transliterator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PinyinNameMatcherTest {
    private val plain = Transliterator.getInstance("Han-Latin; Latin-ASCII; Lower")
    private val names = Transliterator.getInstance("Han-Latin/Names; Latin-ASCII; Lower")
    private val matcher = PinyinNameMatcher(
        charPinyin = { plain.transliterate(it.toString()) },
        surnamePinyin = { names.transliterate(it) }
    )

    private fun config(name: String, aliases: List<String> = emptyList()) =
        ClassifierConfig(name, "", aliases, emptyList(), 3, true, true, true)

    @Test
    fun homophoneCharactersMatch() {
        assertTrue(matcher.soundsLike("张薇你来回答一下这个问题", "张伟"))
        assertTrue(matcher.soundsLike("章伟回答一下", "张伟"))
    }

    /** Real outputs of the medium streaming model on synthesized name calls. */
    @Test
    fun matchesNamesMisrecognizedByTheStreamingModel() {
        assertTrue(matcher.soundsLike("张维也来回答一下这个问题", "张伟"))
        assertTrue(matcher.soundsLike("李斯与你玩鬼仔以下这个问题", "李思雨"))
        assertTrue(matcher.soundsLike("黄子轩一来回答一下这个问题", "黄梓萱"))
        // Different initials are not merged: these must stay unmatched.
        assertFalse(matcher.soundsLike("钟子萱也来回答以下这个问题", "周子轩"))
        assertFalse(matcher.soundsLike("迪斯云逆来回答一下这个问题", "李思雨"))
    }

    @Test
    fun commonAccentMergesAreTolerated() {
        // n/l and ing/in: "牛林" (niu lin) for "刘宁" (liu ning).
        assertTrue(matcher.soundsLike("牛林你来说一下", "刘宁"))
        // zh/z and sh/s.
        assertTrue(matcher.soundsLike("赞三同学", "张山"))
    }

    @Test
    fun surnameReadingIsAccepted() {
        // 单 reads "dan" in words but "shan" as a surname.
        assertTrue(matcher.soundsLike("善丽同学请回答", "单丽"))
        assertTrue(matcher.soundsLike("单丽同学请回答", "单丽"))
    }

    @Test
    fun unrelatedWordsDoNotMatch() {
        assertFalse(matcher.soundsLike("说明一下这个结论", "李明"))
        assertFalse(matcher.soundsLike("今天讲需求弹性", "张伟"))
        assertFalse(matcher.soundsLike("abc", "张伟"))
        assertFalse(matcher.soundsLike("张伟", "Tom"))
    }

    @Test
    fun fuzzyRulesAreNarrow() {
        assertEquals("zan", PinyinNameMatcher.fuzzy("zhang"))
        assertEquals("lin", PinyinNameMatcher.fuzzy("ning"))
        assertEquals("zong", PinyinNameMatcher.fuzzy("zhong"))
        assertEquals("ng", PinyinNameMatcher.fuzzy("ng"))
    }

    @Test
    fun classifierUsesSoundAlikeNamesOnlyWithClassroomContext() {
        val direct = CueClassifier.classify("张薇你来回答一下这个问题", config("张伟"), matcher)
        assertEquals(CueType.DIRECT_QUESTION, direct.type)
        assertTrue(direct.reason.contains("读音"))

        // Without a call or question, a sound-alike name alone stays below the alert threshold.
        assertFalse(CueClassifier.classify("张薇上次作业写得很好", config("张伟"), matcher).shouldAlert())
        // Exact name alone still alerts as before.
        assertTrue(CueClassifier.classify("张伟上次作业写得很好", config("张伟"), matcher).shouldAlert())
    }

    @Test
    fun soundAlikeNameArmsFollowUpOnlyWhenTeacherIsCalling() {
        assertFalse(CueClassifier.mentionsIdentity("黎明前的黑暗最难熬", config("李明"), matcher))
        assertTrue(CueClassifier.mentionsIdentity("黎明同学在不在", config("李明"), matcher))
        assertTrue(CueClassifier.mentionsIdentity("李明前面那道题", config("李明"), matcher))
    }

    @Test
    fun soundAlikeAliasNeedsTwoCharacters() {
        val result = CueClassifier.classify("小莉你来回答一下", config("", aliases = listOf("小李")), matcher)
        assertEquals(CueType.DIRECT_QUESTION, result.type)
        assertFalse(CueClassifier.mentionsIdentity("你来回答", config("", aliases = listOf("李")), matcher))
    }
}
