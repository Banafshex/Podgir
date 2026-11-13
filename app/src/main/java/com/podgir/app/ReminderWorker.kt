package com.podgir.app

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit

class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = Repository(applicationContext)
        if (!repo.boolean("listeningReminder", false)) return Result.success()
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return Result.success()
        if (!NotificationManagerCompat.from(applicationContext).areNotificationsEnabled()) return Result.success()
        val fa = repo.boolean("fa", true)
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(NotificationChannel("listening_reminder", if (fa) "یادآور شنیدن" else "Listening reminders", NotificationManager.IMPORTANCE_DEFAULT))
        }
        val intent = Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(applicationContext, 1, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(applicationContext, "listening_reminder")
            .setSmallIcon(R.drawable.ic_podgir).setContentTitle(if (fa) "وقت یک پادکست؟" else "Time for a podcast?")
            .setContentText(if (fa) "برای ادامهٔ شنیدن به پادگیر سر بزنید." else "Open Podgir to continue listening.")
            .setContentIntent(pending).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(applicationContext).notify(0x504f44, notification) } catch (_: SecurityException) { }
        return Result.success()
    }
    companion object {
        fun configure(context: Context, enabled: Boolean) {
            val work = WorkManager.getInstance(context)
            if (!enabled) { work.cancelUniqueWork("listening-reminder"); return }
            val request = PeriodicWorkRequestBuilder<ReminderWorker>(24, TimeUnit.HOURS).setInitialDelay(24, TimeUnit.HOURS).build()
            work.enqueueUniquePeriodicWork("listening-reminder", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
