package com.classcue.kexing.engine

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

enum class SessionPhase { STOPPED, STARTING, LISTENING, ERROR }

data class TranscriptLine(val text: String, val timestamp: Long, val isFinal: Boolean)

data class SessionState(
    val phase: SessionPhase = SessionPhase.STOPPED,
    val startedAt: Long? = null,
    val statusText: String = "尚未开始",
    val partialText: String = "",
    val transcript: List<TranscriptLine> = emptyList(),
    val lastCue: CueResult? = null,
    val recordingName: String? = null,
    val recordingBytes: Long = 0L,
    val inputLevel: Int = 0,
    val recognizerMode: String = "离线中文识别 · 荣耀兼容"
)

object ClassCueRuntime {
    private val _session = MutableStateFlow(SessionState())
    val session: StateFlow<SessionState> = _session

    fun update(transform: (SessionState) -> SessionState) {
        _session.value = transform(_session.value)
    }

    fun reset() { _session.value = SessionState() }
}
