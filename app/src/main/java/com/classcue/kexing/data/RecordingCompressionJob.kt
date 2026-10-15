package com.classcue.kexing.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import com.classcue.kexing.ClassCueApplication
import com.classcue.kexing.engine.ClassCueRuntime
import com.classcue.kexing.engine.SessionPhase
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Compresses finished lesson WAVs in the background when the battery is not low. Each file is
 * handled independently; a stopped job keeps the WAV untouched and resumes later.
 */
class RecordingCompressionJob : JobService() {
    private val keepGoing = AtomicBoolean(true)

    override fun onStartJob(params: JobParameters?): Boolean {
        keepGoing.set(true)
        Thread({
            var needsRetry = false
            runCatching {
                val repository = ClassCueApplication.recordingRepository
                val settings = ClassCueApplication.settingsStore.settings.value
                if (!settings.compressRecordings) return@runCatching
                // Never compete with live recognition for the CPU.
                if (ClassCueRuntime.session.value.phase != SessionPhase.STOPPED) {
                    needsRetry = true
                    return@runCatching
                }
                for (wav in repository.pendingCompression()) {
                    if (!keepGoing.get()) {
                        needsRetry = true
                        break
                    }
                    try {
                        repository.compress(wav, settings.deleteWavAfterCompression) { keepGoing.get() }
                    } catch (_: RecordingCompressor.Cancelled) {
                        needsRetry = true
                        break
                    } catch (_: Throwable) {
                        // Leave this WAV as it is; it stays playable and is retried on the next run.
                    }
                }
            }
            jobFinished(params, needsRetry)
        }, "classcue-compress").start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean {
        keepGoing.set(false)
        return true
    }

    companion object {
        private const val JOB_ID = 0x4b59

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java) ?: return
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, RecordingCompressionJob::class.java))
                .setRequiresBatteryNotLow(true)
                .setRequiresStorageNotLow(false)
                .setBackoffCriteria(5L * 60L * 1_000L, JobInfo.BACKOFF_POLICY_EXPONENTIAL)
                .build()
            scheduler.schedule(job)
        }
    }
}
