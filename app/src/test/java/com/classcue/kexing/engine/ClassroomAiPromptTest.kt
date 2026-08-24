package com.classcue.kexing.engine

import com.classcue.kexing.model.AiTier
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassroomAiPromptTest {
    @Test
    fun fastPromptAccountsForAsrLimitsAndShortAnswers() {
        val prompt = ClassroomAiPrompt.system(AiTier.FAST, "先给定义")
        assertTrue(prompt.contains("同音字"))
        assertTrue(prompt.contains("课堂指令"))
        assertTrue(prompt.contains("1至3句话"))
        assertTrue(prompt.contains("先给定义"))
    }

    @Test
    fun strongPromptRequestsEvidenceAndKeepsTranscriptDelimited() {
        val prompt = ClassroomAiPrompt.system(AiTier.STRONG, "")
        val user = ClassroomAiPrompt.user("请解释边际效用", "今天讲消费者理论")
        assertTrue(prompt.contains("关键概念、依据、步骤"))
        assertTrue(user.contains("<课堂上下文>"))
        assertTrue(user.contains("<当前识别片段>"))
    }
}
