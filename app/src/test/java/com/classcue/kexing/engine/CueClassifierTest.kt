package com.classcue.kexing.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CueClassifierTest {
    private fun config(
        name: String = "李明",
        keywords: List<String> = listOf("点名", "请回答"),
        aliases: List<String> = listOf("小李"),
        sensitivity: Int = 3
    ) = ClassifierConfig(
        name,
        "20260001",
        aliases,
        keywords,
        sensitivity,
        true,
        true,
        true
    )

    @Test
    fun directQuestionWithNameAlerts() {
        val result = CueClassifier.classify("李明，请回答为什么需求曲线向下倾斜", config())
        assertEquals(CueType.DIRECT_QUESTION, result.type)
        assertTrue(result.shouldAlert())
    }

    @Test
    fun customStrongKeywordAloneAlerts() {
        val result = CueClassifier.classify("现在开始点名", config())
        assertTrue(result.shouldAlert())
        assertEquals(CueType.ROLL_CALL, result.type)
    }

    @Test
    fun customStrongKeywordInsideLongerSentenceBypassesRollCallThreshold() {
        val result = CueClassifier.classify(
            "老师说现在开始点名，听到特殊口令的同学请准备",
            config(keywords = listOf("特殊口令"), sensitivity = 1)
        )
        assertTrue(result.shouldAlert())
        assertEquals(CueType.ROLL_CALL, result.type)
    }

    @Test
    fun partialLongSentenceContainsConfiguredStrongKeyword() {
        assertTrue(
            CueClassifier.containsCustomKeyword(
                "这是还没有结束的一整句话，其中已经出现特殊口令但仍在继续识别",
                listOf("特殊口令")
            )
        )
    }

    @Test
    fun unrelatedLectureSentenceDoesNotAlert() {
        val result = CueClassifier.classify("今天我们学习宏观经济学第三章", config())
        assertFalse(result.shouldAlert())
    }

    @Test
    fun generalQuestionAlertsInAllMode() {
        val result = CueClassifier.classify("这个公式为什么要除以二", config(keywords = emptyList()))
        assertTrue(result.shouldAlert())
        assertEquals(CueType.GENERAL_QUESTION, result.type)
    }

    @Test
    fun configuredClassroomDescriptionCanIdentifyUser() {
        val result = CueClassifier.classify(
            "最后一排穿黑衣服的同学，你来概括一下这段材料的核心观点",
            config(keywords = emptyList(), aliases = listOf("最后一排穿黑衣服的同学"))
        )
        assertTrue(result.shouldAlert())
        assertEquals(CueType.DIRECT_QUESTION, result.type)
    }

    @Test
    fun uncommonStudentTaskPhraseIsRecognized() {
        val result = CueClassifier.classify(
            "哪位同学愿意到黑板上推导一下这个公式",
            config(keywords = emptyList())
        )
        assertTrue(result.shouldAlert())
        assertEquals(CueType.GENERAL_QUESTION, result.type)
    }

    @Test
    fun expandedLexiconIsLargeAndSharedWithFollowupDetection() {
        assertTrue(CueClassifier.builtInCueCount() >= 180)
        assertTrue(CueClassifier.looksLikeQuestion("还有其他答案吗"))
        assertTrue(CueClassifier.looksLikeQuestion("请比较一下两种算法的优缺点"))
        assertFalse(CueClassifier.looksLikeQuestion("今天继续学习宏观经济学第三章"))
    }
}
