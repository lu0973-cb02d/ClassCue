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
    fun studentDirectedQuestionAlertsInAllMode() {
        val result = CueClassifier.classify("大家想一想这个公式为什么要除以二", config(keywords = emptyList()))
        assertTrue(result.shouldAlert())
        assertEquals(CueType.GENERAL_QUESTION, result.type)
        assertTrue(CueClassifier.classify("谁能说一下这个公式为什么要除以二", config(keywords = emptyList())).shouldAlert())
    }

    @Test
    fun rhetoricalLectureQuestionNeedsTheMostSensitiveSetting() {
        val lectureLines = listOf(
            "这个公式为什么要除以二",
            "我们来看一下这个公式是怎么推导出来的",
            "那么什么是边际效用呢",
            "为什么这么说呢因为成本是沉没的",
            "有没有发现这两个曲线的形状很像"
        )
        lectureLines.forEach { line ->
            assertFalse(line, CueClassifier.classify(line, config(keywords = emptyList())).shouldAlert())
            assertFalse(line, CueClassifier.classify(line, config(keywords = emptyList(), sensitivity = 4)).shouldAlert())
        }
        val mostSensitive = CueClassifier.classify(lectureLines.first(), config(keywords = emptyList(), sensitivity = 5))
        assertEquals(CueType.GENERAL_QUESTION, mostSensitive.type)
    }

    @Test
    fun singleCharacterAliasNeedsDirectAddress() {
        val single = config(keywords = emptyList(), aliases = listOf("明"))
        // "说明" contains the alias character but nobody is being called on.
        assertFalse(CueClassifier.classify("说明一下为什么需求曲线向下倾斜", single.copyWithName("")).shouldAlert())
        assertFalse(CueClassifier.mentionsIdentity("这说明了什么问题", single.copyWithName(""), null))
        assertTrue(CueClassifier.mentionsIdentity("明同学你来回答", single.copyWithName(""), null))
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
    fun broadQuestionWordAloneDoesNotTurnLectureIntoAnAlert() {
        val result = CueClassifier.classify(
            "本节会说明什么是边际收益以及它和总收益的关系",
            config(keywords = emptyList())
        )
        assertFalse(result.shouldAlert())
    }

    @Test
    fun expandedLexiconIsLargeAndSharedWithFollowupDetection() {
        assertTrue(CueClassifier.builtInCueCount() >= 180)
        assertTrue(CueClassifier.looksLikeQuestion("还有其他答案吗"))
        assertTrue(CueClassifier.looksLikeQuestion("请比较一下两种算法的优缺点"))
        assertFalse(CueClassifier.looksLikeQuestion("今天继续学习宏观经济学第三章"))
    }

    private fun ClassifierConfig.copyWithName(name: String) = ClassifierConfig(
        name, "", aliases, customKeywords, sensitivity, detectRollCall, detectQuestions, detectGeneralQuestions
    )
}
