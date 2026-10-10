package com.odh.healthhub.work

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.odh.healthhub.HealthHubApp
import com.odh.healthhub.MainActivity
import com.odh.healthhub.R
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/** Once a day (about 7:30am) analyses yesterday's full data with Claude. */
class AnalysisWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as HealthHubApp
        if (!app.store.autoAnalyse || app.store.apiKey.isBlank()) return Result.success()
        // Reading health data from a background job needs the background permission
        if (!app.repo.available || !app.repo.hasBackgroundPermission()) return Result.success()

        val date = LocalDate.now().minusDays(1)
        if (app.store.analysis(date) != null) return Result.success()

        val result = app.analyzer.analyse(date)
        return when {
            result.isSuccess -> { notifyReady(applicationContext, date); Result.success() }
            runAttemptCount < 3 -> Result.retry()
            else -> Result.failure()
        }
    }

    companion object {
        private const val NAME = "daily-analysis"
        const val CHANNEL = "insights"

        fun schedule(context: Context) {
            val now = LocalDateTime.now()
            var next = now.toLocalDate().atTime(LocalTime.of(7, 30))
            if (!next.isAfter(now)) next = next.plusDays(1)
            val request = PeriodicWorkRequestBuilder<AnalysisWorker>(24, TimeUnit.HOURS)
                .setInitialDelay(Duration.between(now, next).toMinutes(), TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, request)
        }

        fun createChannel(context: Context) {
            val nm = context.getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL, "Daily health insights", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }

        fun notifyReady(context: Context, date: LocalDate) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED
            ) return
            val open = PendingIntent.getActivity(
                context, 0,
                Intent(context, MainActivity::class.java).putExtra("open_insights", true),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            val n = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat)
                .setContentTitle("Your health insight is ready")
                .setContentText("Claude has analysed your data for ${date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }}.")
                .setContentIntent(open)
                .setAutoCancel(true)
                .build()
            NotificationManagerCompat.from(context).notify(1, n)
        }
    }
}
