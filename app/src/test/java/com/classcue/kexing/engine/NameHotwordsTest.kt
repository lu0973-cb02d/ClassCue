package com.classcue.kexing.engine

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class NameHotwordsTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun keepsOnlyEntriesTheModelCanSpell() {
        val vocabulary = setOf("张", "伟", "小", "李", "班", "长", "陆")
        val hotwords = NameHotwords.build(listOf("张伟", " 小李 ", "陆舜", "李", "班长", "张伟", "a/b"), vocabulary)
        // "陆舜": 舜 is not a model token, and one unknown character makes sherpa-onnx drop the whole list.
        assertEquals(listOf("张伟", "小李", "班长"), hotwords)
    }

    @Test
    fun readsTokenColumnFromTokensFile() {
        val tokens = temp.newFile("tokens.txt").apply { writeText("<blk> 0\n▁的 260\n张 900\n伟 901\n") }
        assertEquals(setOf("<blk>", "▁的", "张", "伟"), NameHotwords.readVocabulary(tokens))
    }
}
