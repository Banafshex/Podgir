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
            setPauseAtEndOfMediaItems(!Repository(applicationContext).boolean("autoplay", true))
            setPlaybackSpeed((Repository(applicationContext).long("defaultSpeed", 100) / 100f).coerceIn(.5f, 3f))
        }
        val repo = Repository(applicationContext)
        player.addListener(object : Player.Listener {
            override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
                val oldId = oldPosition.mediaItem?.mediaId
                if (oldId != null && oldId != newPosition.mediaItem?.mediaId && oldPosition.positionMs > 0) repo.savePosition(oldId, oldPosition.positionMs)
            }
            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_ENDED) player.currentMediaItem?.mediaId?.let { id ->
                    if (player.duration > 0) { repo.saveDuration(id, player.duration); repo.savePosition(id, player.duration) }
                }
            }
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val id = mediaItem?.mediaId ?: return
                val episode = (repo.queue() + repo.history()).firstOrNull { it.audio == id } ?: return
                repo.saveLastEpisode(episode)
                repo.record(episode)
                repo.saveQueue(repo.queue().filterNot { it.audio == id })
                if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
                    val resume = resumePosition(repo.position(id), repo.duration(id))
                    if (resume > 0) player.seekTo(resume)
                }
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
        if (intent?.action == "com.podgir.app.AUTOPLAY") {
            (session?.player as? ExoPlayer)?.setPauseAtEndOfMediaItems(!Repository(applicationContext).boolean("autoplay", true))
        }
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
    private fun repoForShutdown(id: String, position: Long, duration: Long) { val repo = Repository(applicationContext); repo.savePosition(id, position); repo.saveDuration(id, duration) }
    override fun onDestroy() {
        progressTask?.let { handler.removeCallbacks(it) }
        sleepTask?.let { handler.removeCallbacks(it) }
        Repository(applicationContext).setLong("sleepDeadline", 0)
        session?.run { player.currentMediaItem?.mediaId?.let { id -> if (player.currentPosition > 0) repoForShutdown(id, player.currentPosition, player.duration) }; player.release(); release() }
        session = null
        super.onDestroy()
    }
}
