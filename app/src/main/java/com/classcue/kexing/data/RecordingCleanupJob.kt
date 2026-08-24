package com.classcue.kexing.data

import android.app.job.JobInfo
import android.app.job.JobParameters
import android.app.job.JobScheduler
import android.app.job.JobService
import android.content.ComponentName
import android.content.Context
import java.util.concurrent.TimeUnit

class RecordingCleanupJob : JobService() {
    override fun onStartJob(params: JobParameters?): Boolean {
        Thread {
            runCatching {
                RecordingRepository(applicationContext).apply {
                    recoverInterruptedRecordings()
                    cleanupExpired()
                }
            }
            jobFinished(params, false)
        }.start()
        return true
    }

    override fun onStopJob(params: JobParameters?): Boolean = true

    companion object {
        private const val JOB_ID = 0x4b58

        fun schedule(context: Context) {
            val scheduler = context.getSystemService(JobScheduler::class.java)
            val job = JobInfo.Builder(JOB_ID, ComponentName(context, RecordingCleanupJob::class.java))
                .setPeriodic(TimeUnit.HOURS.toMillis(12))
                .setPersisted(false)
                .build()
            scheduler.schedule(job)
        }
    }
}
