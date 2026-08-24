package com.classcue.kexing.model

import java.io.File

data class RecordingInfo(
    val file: File,
    val startedAt: Long,
    val bytes: Long,
    val estimatedDurationMs: Long,
    val expiresAt: Long
)
