package com.classcue.kexing.ui

import android.media.MediaPlayer
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.delay
import java.io.File

/** Minimal in-app player for one lesson recording, so a tapped transcript line can be heard. */
@Stable
internal class LessonPlayer(private val file: File) {
    private var player: MediaPlayer? = null

    var isPlaying by mutableStateOf(false)
        private set
    var positionMs by mutableLongStateOf(0L)
        private set
    var durationMs by mutableLongStateOf(0L)
        private set
    var error by mutableStateOf<String?>(null)
        private set

    private fun ensurePrepared(): MediaPlayer? {
        player?.let { return it }
        return runCatching {
            MediaPlayer().apply {
                setDataSource(file.absolutePath)
                prepare()
                setOnCompletionListener { this@LessonPlayer.isPlaying = false }
            }
        }.onSuccess {
            player = it
            durationMs = it.duration.toLong().coerceAtLeast(0L)
            error = null
        }.onFailure {
            error = "无法播放这段录音：${it.message ?: it.javaClass.simpleName}"
        }.getOrNull()
    }

    fun toggle() {
        val p = ensurePrepared() ?: return
        if (p.isPlaying) {
            p.pause()
            isPlaying = false
        } else {
            p.start()
            isPlaying = true
        }
    }

    fun seekTo(ms: Long, play: Boolean = true) {
        val p = ensurePrepared() ?: return
        val target = ms.coerceIn(0L, (durationMs - 1L).coerceAtLeast(0L))
        p.seekTo(target, MediaPlayer.SEEK_CLOSEST)
        positionMs = target
        if (play && !p.isPlaying) {
            p.start()
            isPlaying = true
        }
    }

    fun skip(deltaMs: Long) = seekTo(positionMs + deltaMs, play = isPlaying)

    internal fun poll() {
        player?.let { p -> positionMs = p.currentPosition.toLong() }
    }

    fun release() {
        runCatching { player?.release() }
        player = null
        isPlaying = false
    }

    /** Loads the duration up front so the slider and seek clamps are right before playback. */
    internal fun prepareMetadata() {
        ensurePrepared()
    }
}

@Composable
internal fun rememberLessonPlayer(file: File?): LessonPlayer? {
    val player = remember(file?.absolutePath) { file?.let(::LessonPlayer) }
    DisposableEffect(player) {
        player?.prepareMetadata()
        onDispose { player?.release() }
    }
    LaunchedEffect(player, player?.isPlaying) {
        while (player != null && player.isPlaying) {
            player.poll()
            delay(250)
        }
    }
    return player
}
