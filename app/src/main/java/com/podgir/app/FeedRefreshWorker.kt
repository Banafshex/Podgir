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
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import kotlinx.coroutines.CancellationException
import java.util.concurrent.TimeUnit

class FeedRefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val repo = Repository(applicationContext)
        var failed = false
        for (podcast in repo.subscriptions()) {
            try {
                val episodes = repo.episodes(podcast)
                if (episodes.isEmpty() || repo.subscriptions().none { it.feed == podcast.feed }) continue
                val newest = episodes.first().audio
                val previous = repo.seenEpisode(podcast)
                if (previous == null) { repo.setSeenEpisode(podcast, newest); continue }
                if (previous == newest) continue
                // If the old item disappeared, establish a new baseline instead of downloading a whole archive.
                val fresh = newSince(episodes, previous)
                if (fresh == null) { repo.setSeenEpisode(podcast, newest); continue }
                if (repo.autoDownload(podcast)) {
                    // Keep automatic data usage bounded; enqueue oldest first so the newest is not missed.
                    fresh.take(3).asReversed().forEach { repo.download(it) }
                }
                if (fresh.isNotEmpty() && repo.boolean("newEpisodeNotifications", true)) notifyNew(podcast, fresh.size, repo.boolean("fa", true))
                repo.setSeenEpisode(podcast, newest)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { failed = true }
        }
        return if (failed) Result.retry() else Result.success()
    }

    private fun notifyNew(p: Podcast, count: Int, fa: Boolean) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel("new_episodes", if (fa) "قسمت‌های جدید" else "New episodes", NotificationManager.IMPORTANCE_DEFAULT))
        val intent = Intent(applicationContext, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val pending = PendingIntent.getActivity(applicationContext, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val body = if (fa) "$count قسمت جدید منتشر شد" else "$count new episode(s) available"
        val notification = NotificationCompat.Builder(applicationContext, "new_episodes").setSmallIcon(R.drawable.ic_podgir).setContentTitle(p.title).setContentText(body).setContentIntent(pending).setAutoCancel(true).build()
        try { NotificationManagerCompat.from(applicationContext).notify(p.feed.hashCode(), notification) } catch (_: SecurityException) { }
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<FeedRefreshWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build()).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork("feed-refresh", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

/** The feed is expected to be newest first. Unknown baseline means no safe delta. */
internal fun newSince(episodes: List<Episode>, previous: String): List<Episode>? {
    val index = episodes.indexOfFirst { it.audio == previous }
    return if (index < 0) null else episodes.take(index)
}
