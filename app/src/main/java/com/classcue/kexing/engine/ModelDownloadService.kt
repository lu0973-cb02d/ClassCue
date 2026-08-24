package com.classcue.kexing.engine

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.classcue.kexing.ClassCueApplication
import com.classcue.kexing.MainActivity
import com.classcue.kexing.R
import java.util.ArrayDeque
import java.util.concurrent.atomic.AtomicBoolean

class ModelDownloadService : Service() {
    private val continueWork = AtomicBoolean(false)
    private val pending = ArrayDeque<OfflineModelPack>()
    private val queueLock = Any()
    @Volatile private var currentPack: OfflineModelPack? = null
    private var worker: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pauseDownloads()
            ACTION_DOWNLOAD -> {
                val pack = runCatching {
                    OfflineModelPack.valueOf(intent.getStringExtra(EXTRA_PACK).orEmpty())
                }.getOrNull() ?: return START_NOT_STICKY
                enqueue(pack)
            }
        }
        return START_NOT_STICKY
    }

    private fun enqueue(requested: OfflineModelPack) {
        val repository = ClassCueApplication.modelRepository
        val plan = if (requested == OfflineModelPack.LARGE) {
            listOf(OfflineModelPack.MEDIUM, OfflineModelPack.LARGE)
        } else {
            listOf(OfflineModelPack.MEDIUM)
        }
        var shouldStart = false
        synchronized(queueLock) {
            plan.filterNot(repository::isReady).forEach { pack ->
                if (pack != currentPack && pack !in pending) {
                    pending.addLast(pack)
                    repository.queue(pack)
                }
            }
            if (worker?.isAlive != true && pending.isNotEmpty()) {
                continueWork.set(true)
                shouldStart = true
            }
        }
        if (!shouldStart) return
        val first = synchronized(queueLock) { pending.firstOrNull() } ?: return
        startForeground(NOTIFICATION_ID, notification(repository.states.value.getValue(first)))
        worker = Thread({ runQueue() }, "classcue-model-download").apply { start() }
    }

    private fun runQueue() {
        val repository = ClassCueApplication.modelRepository
        try {
            while (continueWork.get()) {
                val pack = synchronized(queueLock) {
                    if (pending.isEmpty()) null else pending.removeFirst()
                } ?: break
                currentPack = pack
                try {
                    repository.downloadAndInstall(
                        pack = pack,
                        shouldContinue = { continueWork.get() },
                        onProgress = { state -> updateNotification(state) }
                    )
                } catch (_: InterruptedException) {
                    repository.pause(pack)
                    break
                } catch (error: Throwable) {
                    val failed = repository.fail(pack, error.message ?: "下载失败")
                    updateNotification(failed)
                    continueWork.set(false)
                    break
                } finally {
                    currentPack = null
                }
            }
        } finally {
            synchronized(queueLock) {
                if (!continueWork.get()) {
                    pending.forEach { repository.pause(it, "等待继续下载") }
                    pending.clear()
                }
            }
            continueWork.set(false)
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun pauseDownloads() {
        continueWork.set(false)
        worker?.interrupt()
        currentPack?.let { ClassCueApplication.modelRepository.pause(it) }
        if (worker?.isAlive != true) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
        }
    }

    private fun updateNotification(state: ModelDownloadState) {
        getSystemService(NotificationManager::class.java).notify(NOTIFICATION_ID, notification(state))
    }

    private fun notification(state: ModelDownloadState): Notification {
        val pauseIntent = PendingIntent.getService(
            this,
            910,
            Intent(this, ModelDownloadService::class.java).setAction(ACTION_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val title = when (state.pack) {
            OfflineModelPack.MEDIUM -> "正在准备中档转写模型"
            OfflineModelPack.LARGE -> "正在准备大档精修模型"
        }
        val builder = NotificationCompat.Builder(this, ClassCueApplication.CHANNEL_MODEL_DOWNLOAD)
            .setSmallIcon(R.drawable.ic_stat_alert)
            .setContentTitle(title)
            .setContentText(notificationText(state))
            .setContentIntent(openAppIntent())
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setOngoing(state.phase in ACTIVE_PHASES)
            .setOnlyAlertOnce(true)
            .addAction(0, "暂停", pauseIntent)
        when (state.phase) {
            ModelDownloadPhase.DOWNLOADING -> builder.setProgress(100, (state.progress * 100).toInt(), false)
            ModelDownloadPhase.QUEUED, ModelDownloadPhase.INSTALLING -> builder.setProgress(0, 0, true)
            else -> builder.setProgress(0, 0, false)
        }
        return builder.build()
    }

    private fun notificationText(state: ModelDownloadState): String = when (state.phase) {
        ModelDownloadPhase.DOWNLOADING -> {
            val percent = (state.progress * 100).toInt()
            "$percent% · ${formatSize(state.downloadedBytes)} / ${formatSize(state.totalBytes)} · ${formatSpeed(state.bytesPerSecond)}"
        }
        else -> state.message
    }

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        911,
        Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        },
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    override fun onDestroy() {
        continueWork.set(false)
        worker?.interrupt()
        super.onDestroy()
    }

    companion object {
        const val ACTION_DOWNLOAD = "com.classcue.kexing.DOWNLOAD_MODEL"
        const val ACTION_PAUSE = "com.classcue.kexing.PAUSE_MODEL_DOWNLOAD"
        const val EXTRA_PACK = "model_pack"
        private const val NOTIFICATION_ID = 2201
        private val ACTIVE_PHASES = setOf(
            ModelDownloadPhase.QUEUED,
            ModelDownloadPhase.DOWNLOADING,
            ModelDownloadPhase.INSTALLING
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes <= 0L -> "0 MB"
    bytes >= 1024L * 1024L * 1024L -> "%.1f GB".format(bytes / 1024.0 / 1024.0 / 1024.0)
    else -> "%.0f MB".format(bytes / 1024.0 / 1024.0)
}

private fun formatSpeed(bytesPerSecond: Long): String = when {
    bytesPerSecond <= 0L -> "计算速度中"
    bytesPerSecond >= 1024L * 1024L -> "%.1f MB/s".format(bytesPerSecond / 1024.0 / 1024.0)
    else -> "%.0f KB/s".format(bytesPerSecond / 1024.0)
}
