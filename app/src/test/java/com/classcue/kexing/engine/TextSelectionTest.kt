package com.classcue.kexing.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TextSelectionTest {
    @Test
    fun chineseCharactersAndLatinModelNamesBecomeSelectableBlocks() {
        val tokens = TextSelection.tokenize("厂家能力，MagicOS 10 可以吗？")

        assertEquals(listOf("厂", "家", "能", "力", "，", "MagicOS", "10", "可", "以", "吗", "？"), tokens.map { it.text })
    }

    @Test
    fun selectedRangePreservesOriginalSpacesAndPunctuation() {
        val source = "前文无关。 为什么 MagicOS 不显示通知？ 后文无关。"
        val tokens = TextSelection.tokenize(source)
        val start = tokens.indexOfFirst { it.text == "为" }
        val end = tokens.indexOfFirst { it.text == "？" }

        assertTrue(start >= 0 && end > start)
        assertEquals(
            "为什么 MagicOS 不显示通知？",
            TextSelection.selectedText(source, tokens, (start..end).toSet())
        )
    }

    @Test
    fun emptySelectionReturnsEmptyText() {
        assertEquals("", TextSelection.selectedText("老师提问", TextSelection.tokenize("老师提问"), emptySet()))
    }

    @Test
    fun nonContiguousCharactersRemainIndependentlySelectable() {
        val source = "老师说请张同学回答供给与需求的区别"
        val tokens = TextSelection.tokenize(source)
        val selected = setOf(
            tokens.indexOfFirst { it.text == "供" },
            tokens.indexOfFirst { it.text == "需" },
            tokens.indexOfFirst { it.text == "区" },
            tokens.indexOfFirst { it.text == "别" }
        )

        assertEquals("供 需 区别", TextSelection.selectedText(source, tokens, selected))
    }
}
