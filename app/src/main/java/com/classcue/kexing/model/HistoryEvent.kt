package com.classcue.kexing.model

import com.classcue.kexing.engine.CueType
import com.classcue.kexing.engine.RiskLevel

data class HistoryEvent(
    val id: Long = 0,
    val createdAt: Long,
    val type: CueType,
    val risk: RiskLevel,
    val transcript: String,
    val confidence: Double,
    val reason: String,
    val answer: String? = null,
    val recordingName: String? = null,
    val recordingOffsetMs: Long? = null,
    val courseName: String = ""
)
