package com.podgir.app

import androidx.media3.exoplayer.ExoPlayer
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService

class PlaybackService : MediaSessionService() {
    private var session: MediaSession? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressTask: Runnable? = null
    private var sleepTask: Runnable? = null
    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build().apply {
            setAudioAttributes(AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_SPEECH).build(), true)
            setHandleAudioBecomingNoisy(true)
        }
        val repo = Repository(applicationContext)
        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId ?: return
                val episode = (repo.queue() + repo.history()).firstOrNull { it.audio == id } ?: return
                repo.saveLastEpisode(episode)
                repo.record(episode)
                repo.saveQueue(repo.queue().filterNot { it.audio == id })
            }
        })
        progressTask = object : Runnable {
            override fun run() {
                val id = player.currentMediaItem?.mediaId
                if (id != null && player.currentPosition > 0) { repo.savePosition(id, player.currentPosition); repo.saveDuration(id, player.duration) }
                handler.postDelayed(this, 5000)
            }
        }.also { handler.post(it) }
        session = MediaSession.Builder(this, player).build()
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "com.podgir.app.SLEEP_TIMER") {
            sleepTask?.let { handler.removeCallbacks(it) }
            val deadline = intent.getLongExtra("deadline", 0L)
            Repository(applicationContext).setLong("sleepDeadline", deadline)
            if (deadline > 0) {
                sleepTask = Runnable { session?.player?.pause(); Repository(applicationContext).setLong("sleepDeadline", 0) }
                handler.postDelayed(sleepTask!!, (deadline - System.currentTimeMillis()).coerceAtLeast(0))
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }
    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session
    override fun onDestroy() {
        progressTask?.let { handler.removeCallbacks(it) }
        sleepTask?.let { handler.removeCallbacks(it) }
        Repository(applicationContext).setLong("sleepDeadline", 0)
        session?.run { player.release(); release() }
        session = null
        super.onDestroy()
    }
}
