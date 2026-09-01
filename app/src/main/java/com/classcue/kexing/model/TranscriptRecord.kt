package com.classcue.kexing.model

data class TranscriptRecord(
    val id: Long = 0,
    val createdAt: Long,
    val text: String,
    val recordingName: String?,
    val courseName: String = ""
)
