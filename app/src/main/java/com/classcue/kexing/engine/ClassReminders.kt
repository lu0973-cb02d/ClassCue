package com.classcue.kexing.engine

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import com.classcue.kexing.ClassCueApplication
import com.classcue.kexing.MainActivity
import com.classcue.kexing.R
import com.classcue.kexing.model.Timetable
import com.classcue.kexing.model.TimetableResolver
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Optional "class starts soon" notifications from the timetable. Tapping one opens the course
 * confirmation; recording never starts by itself (Android does not allow a background app to
 * open the microphone, and recording should stay a deliberate choice).
 *
 * Uses an inexact alarm window, which needs no special permission. Android may stretch windows
 * shorter than 10 minutes, so the window is centred on the requested lead time.
 */
object ClassReminders {
    const val ACTION_REMIND = "com.classcue.kexing.CLASS_REMINDER"
    const val EXTRA_CONFIRM_COURSE = "confirm_course"
    const val EXTRA_COURSE_NAME = "course_name"
    private const val EXTRA_START_EPOCH_MS = "start_epoch_ms"
    private const val EXTRA_LOCATION = "location"
    private const val REQUEST_ALARM = 4410
    private const val REQUEST_OPEN = 4411
    private const val NOTIFICATION_ID = 4412
    private const val WINDOW_MS = 10L * 60L * 1_000L

    fun reschedule(context: Context, timetable: Timetable, now: LocalDateTime = LocalDateTime.now()) {
        val alarms = context.getSystemService(AlarmManager::class.java) ?: return
        val pending = alarmIntent(context, null)
        alarms.cancel(pending)
        if (!timetable.reminderEnabled) return
        val lead = timetable.reminderLeadMinutes.toLong()
        val next = TimetableResolver.next(timetable, now.plusMinutes(lead)) ?: return
        val zone = ZoneId.systemDefault()
        val remindAt = next.start.minusMinutes(lead).atZone(zone).toInstant().toEpochMilli()
        val nowMs = now.atZone(zone).toInstant().toEpochMilli()
        val windowStart = (remindAt - WINDOW_MS / 2).coerceAtLeast(nowMs + 1_000L)
        alarms.setWindow(
            AlarmManager.RTC_WAKEUP,
            windowStart,
            WINDOW_MS,
            alarmIntent(
                context,
                Intent()
                    .putExtra(EXTRA_COURSE_NAME, next.slot.courseName)
                    .putExtra(EXTRA_LOCATION, next.slot.location)
                    .putExtra(EXTRA_START_EPOCH_MS, next.start.atZone(zone).toInstant().toEpochMilli())
            )
        )
    }

    private fun alarmIntent(context: Context, extras: Intent?): PendingIntent {
        val intent = Intent(context, ClassReminderReceiver::class.java).setAction(ACTION_REMIND)
        extras?.extras?.let(intent::putExtras)
        return PendingIntent.getBroadcast(
            context,
            REQUEST_ALARM,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    internal fun showReminder(context: Context, intent: Intent) {
        val course = intent.getStringExtra(EXTRA_COURSE_NAME)?.takeIf { it.isNotBlank() } ?: return
        val startMs = intent.getLongExtra(EXTRA_START_EPOCH_MS, 0L)
        // A reminder delivered after the class ended (phone was off, deep doze) is pointless.
        if (startMs > 0L && System.currentTimeMillis() > startMs + 60L * 60L * 1_000L) return
        if (context.checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val time = if (startMs > 0L) {
            DateTimeFormatter.ofPattern("HH:mm").format(
                java.time.Instant.ofEpochMilli(startMs).atZone(ZoneId.systemDefault())
            )
        } else ""
        val location = intent.getStringExtra(EXTRA_LOCATION).orEmpty()
        val open = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(context, MainActivity::class.java)
                .putExtra(EXTRA_CONFIRM_COURSE, true)
                .putExtra(EXTRA_COURSE_NAME, course)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(context, ClassCueApplication.CHANNEL_CLASS_REMINDER)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle("《$course》$time 开始")
            .setContentText(listOf(location, "点此确认课程并开始听课").filter { it.isNotBlank() }.joinToString(" · "))
            .setContentIntent(open)
            .setAutoCancel(true)
            .setCategory(NotificationCompat.CATEGORY_EVENT)
            .setTimeoutAfter(90L * 60L * 1_000L)
            .build()
        context.getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification)
    }
}

/** Receives the reminder alarm, shows it, and schedules the following class. */
class ClassReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ClassReminders.ACTION_REMIND) ClassReminders.showReminder(context, intent)
        ClassReminders.reschedule(context, ClassCueApplication.scheduleStore.timetable.value)
    }
}

/** Alarms are cleared on reboot and invalid after clock changes; schedule the next one again. */
class ClassReminderRestoreReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED ->
                ClassReminders.reschedule(context, ClassCueApplication.scheduleStore.timetable.value)
        }
    }
}
