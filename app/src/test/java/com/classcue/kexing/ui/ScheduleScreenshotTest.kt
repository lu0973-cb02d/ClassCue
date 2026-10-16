package com.classcue.kexing.ui

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import com.classcue.kexing.engine.SessionState
import com.classcue.kexing.model.AppSettings
import com.classcue.kexing.model.CourseSlot
import com.classcue.kexing.model.Timetable
import com.classcue.kexing.model.TimetableResolver
import com.classcue.kexing.model.WeekParity
import java.time.LocalDate
import java.time.LocalDateTime
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class, qualifiers = "w400dp-h860dp-xhdpi")
class ScheduleScreenshotTest {
    @get:Rule
    val compose = createAndroidComposeRule<ComponentActivity>()

    private val table = Timetable(
        semesterStart = LocalDate.of(2026, 9, 7),
        slots = listOf(
            CourseSlot("a", "微观经济学", 1, 8 * 60, 9 * 60 + 40, 1, 16, WeekParity.ALL, "教1-101"),
            CourseSlot("b", "线性代数", 1, 10 * 60, 11 * 60 + 40, 3, 16, WeekParity.ODD, "理科楼 305"),
            CourseSlot("c", "统计学", 3, 14 * 60, 15 * 60 + 40, 1, 18, WeekParity.EVEN, ""),
            CourseSlot("d", "数据库原理", 4, 8 * 60, 9 * 60 + 40, 1, 16, WeekParity.ALL, "实验楼 2-204")
        ),
        reminderEnabled = true,
        reminderLeadMinutes = 10
    )
    // Monday of week 3, 07:52 — micro-economics is about to start.
    private val now = LocalDateTime.of(2026, 9, 21, 7, 52)

    @Test
    fun timetableList() {
        compose.setContent { ClassCueTheme { ScheduleScreen(Modifier, table, now) {} } }
        compose.saveScreenshot("schedule_list")
    }

    @Test
    fun editSlotDialog() {
        compose.setContent { ClassCueTheme { ScheduleScreen(Modifier, table, now) {} } }
        compose.onNodeWithText("统计学").performClick()
        compose.mainClock.autoAdvance = false
        compose.saveDialogScreenshot("schedule_edit_dialog")
    }

    @Test
    fun confirmCourseBeforeRecording() {
        var show by mutableStateOf(false)
        compose.setContent {
            ClassCueTheme {
                ScheduleScreen(Modifier, table, now) {}
                if (show) {
                    CourseConfirmDialog(
                        suggestion = TimetableResolver.suggestion(table, now),
                        typedCourse = "数据库原理",
                        knownCourses = table.courseNames,
                        preselect = null,
                        onDismiss = {},
                        onConfirm = {}
                    )
                }
            }
        }
        compose.runOnIdle { show = true }
        compose.saveDialogScreenshot("listen_confirm_course")
    }

    @Test
    fun listenScreenShowsTimetableSuggestion() {
        compose.setContent {
            ClassCueTheme {
                var settings by remember { mutableStateOf(AppSettings(courseName = "数据库原理", displayName = "张伟")) }
                ListenScreen(
                    modifier = Modifier,
                    session = SessionState(),
                    settings = settings,
                    hasAudioPermission = true,
                    hasNotificationPermission = true,
                    onStart = {}, onStop = {}, onTestAlert = {}, onOpenSettings = {},
                    onUpdateSettings = { settings = it(settings) },
                    latestEvent = null, onRetryEvent = {},
                    interruptedSession = null, onDismissInterrupted = {},
                    timetable = table.copy(semesterStart = null).withMondayClassNow()
                )
            }
        }
        compose.saveScreenshot("listen_timetable_hint")
    }

    /** Puts a class on today's weekday around the real clock so the live suggestion shows. */
    private fun Timetable.withMondayClassNow(): Timetable {
        val real = LocalDateTime.now()
        val minute = (real.hour * 60 + real.minute).coerceIn(30, 22 * 60)
        return copy(slots = slots + CourseSlot("now", "微观经济学", real.dayOfWeek.value, minute - 10, minute + 90, location = "教1-101"))
    }
}
