package com.classcue.kexing.ui

import android.app.Application
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.activity.ComponentActivity
import com.classcue.kexing.engine.CueType
import com.classcue.kexing.engine.InterruptedSession
import com.classcue.kexing.engine.RiskLevel
import com.classcue.kexing.engine.SessionPhase
import com.classcue.kexing.engine.SessionState
import com.classcue.kexing.model.AppSettings
import com.classcue.kexing.model.HistoryEvent
import com.classcue.kexing.model.LessonArchive
import com.classcue.kexing.model.LessonSummary
import com.classcue.kexing.model.RecordingInfo
import com.classcue.kexing.model.SearchHit
import com.classcue.kexing.model.TranscriptRecord
import java.io.File
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowMediaPlayer
import org.robolectric.shadows.util.DataSource

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h860dp-xhdpi")
class HistoryScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val start = 1_760_500_800_000L
    private val recording = "class_$start.wav"
    private val summary = LessonSummary("微观经济学", recording, start + 60_000L, start + 5_400_000L, 412, 3)
    private val lines = listOf(
        TranscriptRecord(1, start + 62_000L, "好，我们今天接着讲机会成本", recording, "微观经济学"),
        TranscriptRecord(2, start + 75_000L, "机会成本是指为了得到某种东西而放弃的最高价值", recording, "微观经济学"),
        TranscriptRecord(3, start + 98_000L, "大家想一想，上大学的机会成本包括哪些", recording, "微观经济学")
    )
    private val alert = HistoryEvent(
        id = 9, createdAt = start + 99_000L, type = CueType.DIRECT_QUESTION, risk = RiskLevel.HIGH,
        transcript = "大家想一想，上大学的机会成本包括哪些\n张伟你来回答一下", confidence = 0.96,
        reason = "检测到称呼和提问语气", answer = "【极速 · deepseek-chat】\n主要是上学期间放弃的工作收入，学费本身属于会计成本。",
        recordingName = recording, recordingOffsetMs = 99_000L, courseName = "微观经济学"
    )

    @Test
    fun lessonDetailWithPlayer() {
        val file = File("/fake/$recording")
        ShadowMediaPlayer.addMediaInfo(DataSource.toDataSource(file.absolutePath), ShadowMediaPlayer.MediaInfo(5_400_000, 0))
        compose.setContent {
            ClassCueTheme {
                LessonDetailScreen(
                    summary = summary,
                    focusAt = null,
                    recording = RecordingInfo(file, start, 172_800_044L, 5_400_000L),
                    loadLesson = { LessonArchive("微观经济学", recording, summary.startedAt, summary.endedAt, lines, listOf(alert)) },
                    aiEnabled = true,
                    onBack = {}, onExport = {}, onDelete = {}, onRetryEvent = {}
                )
            }
        }
        compose.saveScreenshot("history_lesson_detail")
    }

    @Test
    fun searchResults() {
        compose.setContent {
            ClassCueTheme {
                HistoryScreen(
                    modifier = Modifier,
                    events = listOf(alert),
                    transcripts = lines,
                    lessonSummaries = listOf(summary),
                    recordings = emptyList(),
                    currentCourseName = "微观经济学",
                    onDeleteHistory = {}, onRetryEvent = {}, onPlayRecording = {}, onShareRecording = {},
                    onDeleteRecording = {}, onExportAllMarkdown = {}, onExportCourseMarkdown = {},
                    onExportLessonMarkdown = {}, onDeleteLesson = {},
                    aiEnabled = true,
                    loadLesson = { null },
                    searchHistory = {
                        listOf(
                            SearchHit(SearchHit.Kind.TRANSCRIPT, 2, lines[1].createdAt, lines[1].text, null, "微观经济学", recording),
                            SearchHit(SearchHit.Kind.EVENT, 9, alert.createdAt, alert.transcript, alert.answer, "微观经济学", recording)
                        )
                    },
                    initialQuery = "机会成本"
                )
            }
        }
        compose.mainClock.advanceTimeBy(1_000)
        compose.saveScreenshot("history_search")
    }

    @Test
    fun listenScreenAfterInterruptedLesson() {
        compose.setContent {
            ClassCueTheme {
                ListenScreen(
                    modifier = Modifier,
                    session = SessionState(phase = SessionPhase.STOPPED, statusText = "尚未开始"),
                    settings = AppSettings(courseName = "微观经济学", displayName = "张伟"),
                    hasAudioPermission = true,
                    hasNotificationPermission = true,
                    onStart = {}, onStop = {}, onTestAlert = {}, onOpenSettings = {}, onUpdateSettings = {},
                    latestEvent = null, onRetryEvent = {},
                    interruptedSession = InterruptedSession("微观经济学", start, recording, recording, 2_460_000L),
                    onDismissInterrupted = {}
                )
            }
        }
        compose.saveScreenshot("listen_interrupted")
    }

    @Test
    fun listenScreenWhileStopping() {
        compose.setContent {
            ClassCueTheme {
                ListenScreen(
                    modifier = Modifier,
                    session = SessionState(
                        phase = SessionPhase.STOPPING, startedAt = start,
                        statusText = "正在收尾：保存录音并识别最后一句…", recordingBytes = 86_000_000L
                    ),
                    settings = AppSettings(courseName = "微观经济学", displayName = "张伟"),
                    hasAudioPermission = true,
                    hasNotificationPermission = true,
                    onStart = {}, onStop = {}, onTestAlert = {}, onOpenSettings = {}, onUpdateSettings = {},
                    latestEvent = null, onRetryEvent = {},
                    interruptedSession = null,
                    onDismissInterrupted = {}
                )
            }
        }
        compose.saveScreenshot("listen_stopping")
    }
}
