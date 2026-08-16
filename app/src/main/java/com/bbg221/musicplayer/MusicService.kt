package com.bbg221.musicplayer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.bbg221.musicplayer.model.Song
import java.util.concurrent.CopyOnWriteArrayList

class MusicService : Service(), MediaPlayer.OnCompletionListener, AudioManager.OnAudioFocusChangeListener {

    interface PlayerListener {
        fun onSongChanged(song: Song?, index: Int) {}
        fun onPlayStateChanged(playing: Boolean) {}
        fun onModeChanged(mode: Int) {}
        fun onPositionChanged(position: Long, duration: Long) {}
        fun onQueueChanged() {}
    }

    companion object {
        const val ACTION_PLAY_QUEUE = "com.bbg221.musicplayer.action.PLAY_QUEUE"
        const val ACTION_PLAY_PAUSE = "com.bbg221.musicplayer.action.PLAY_PAUSE"
        const val ACTION_NEXT = "com.bbg221.musicplayer.action.NEXT"
        const val ACTION_PREV = "com.bbg221.musicplayer.action.PREV"
        const val ACTION_TOGGLE_MODE = "com.bbg221.musicplayer.action.TOGGLE_MODE"
        const val ACTION_SEEK = "com.bbg221.musicplayer.action.SEEK"
        const val EXTRA_QUEUE = "extra_queue"
        const val EXTRA_INDEX = "extra_index"
        const val EXTRA_POSITION = "extra_position"

        const val MODE_SEQUENCE = 0
        const val MODE_LOOP_ONE = 1
        const val MODE_COUNT = 2

        private const val CHANNEL_ID = "playback"
        private const val NOTIFICATION_ID = 1

        val listeners = CopyOnWriteArrayList<PlayerListener>()

        @Volatile
        private var instance: MusicService? = null

        fun currentSong(): Song? = instance?.currentSong
        fun currentIndex(): Int = instance?.index ?: -1
        fun isPlaying(): Boolean = instance?.mp?.isPlaying == true
        fun queue(): List<Song> = instance?.queue ?: emptyList()
        fun playMode(): Int = instance?.mode ?: MODE_SEQUENCE
        fun position(): Long = instance?.mp?.currentPosition?.toLong() ?: 0L
        fun duration(): Long = instance?.currentSong?.duration ?: 0L

        fun toggleMode(context: Context) {
            context.startService(Intent(context, MusicService::class.java).setAction(ACTION_TOGGLE_MODE))
        }

        fun playPause(context: Context) {
            context.startService(Intent(context, MusicService::class.java).setAction(ACTION_PLAY_PAUSE))
        }

        fun next(context: Context) {
            context.startService(Intent(context, MusicService::class.java).setAction(ACTION_NEXT))
        }

        fun prev(context: Context) {
            context.startService(Intent(context, MusicService::class.java).setAction(ACTION_PREV))
        }

        fun seekTo(context: Context, positionMs: Long) {
            context.startService(
                Intent(context, MusicService::class.java)
                    .setAction(ACTION_SEEK)
                    .putExtra(EXTRA_POSITION, positionMs)
            )
        }

        fun playQueue(context: Context, queue: List<Song>, index: Int) {
            val intent = Intent(context, MusicService::class.java)
                .setAction(ACTION_PLAY_QUEUE)
                .putParcelableArrayListExtra(EXTRA_QUEUE, ArrayList(queue))
                .putExtra(EXTRA_INDEX, index)
            context.startService(intent)
        }
    }

    private val queue = mutableListOf<Song>()
    private var index = -1
    var mode: Int = MODE_SEQUENCE
        private set
    private var mp: MediaPlayer? = null
    var currentSong: Song? = null
        private set

    private val handler = Handler(Looper.getMainLooper())
    private val positionTicker = object : Runnable {
        override fun run() {
            val player = mp
            if (player != null && player.isPlaying) {
                notifyPosition()
                handler.postDelayed(this, 500)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        createNotificationChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(positionTicker)
        listeners.forEach { it.onSongChanged(null, -1) }
        mp?.release()
        mp = null
        instance = null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_QUEUE -> {
                @Suppress("UNCHECKED_CAST")
                val newQueue = intent.getParcelableArrayListExtra<Song>(EXTRA_QUEUE)
                val newIndex = intent.getIntExtra(EXTRA_INDEX, 0)
                if (newQueue != null) {
                    queue.clear()
                    queue.addAll(newQueue)
                    playIndex(newIndex.coerceIn(0, (queue.size - 1).coerceAtLeast(0)))
                }
            }
            ACTION_PLAY_PAUSE -> togglePlayPause()
            ACTION_NEXT -> playNext(manual = true)
            ACTION_PREV -> playPrev()
            ACTION_TOGGLE_MODE -> toggleMode()
            ACTION_SEEK -> {
                val pos = intent.getLongExtra(EXTRA_POSITION, 0L)
                mp?.seekTo(pos.toInt())
                notifyPosition()
            }
        }
        return START_NOT_STICKY
    }

    private fun playIndex(newIndex: Int) {
        if (queue.isEmpty()) {
            stopSelfAndForeground()
            return
        }
        index = newIndex.coerceIn(0, queue.size - 1)
        val song = queue[index]
        currentSong = song
        releasePlayer()

        val player = MediaPlayer()
        try {
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            player.setDataSource(this, song.uri)
            player.setOnPreparedListener { p ->
                p.isLooping = mode == MODE_LOOP_ONE
                requestAudioFocus()
                p.start()
                notifySongChanged()
                notifyPlayState()
                startForegroundWithNotification()
                handler.removeCallbacks(positionTicker)
                handler.post(positionTicker)
            }
            player.setOnCompletionListener(this)
            player.setOnErrorListener { _, _, _ ->
                playNext(manual = true)
                true
            }
            player.prepareAsync()
            mp = player
        } catch (e: Exception) {
            player.release()
            mp = null
        }
    }

    private fun releasePlayer() {
        mp?.let {
            it.setOnPreparedListener(null)
            it.setOnCompletionListener(null)
            it.setOnErrorListener(null)
            it.release()
        }
        mp = null
    }

    override fun onCompletion(mp: MediaPlayer) {
        if (mode == MODE_LOOP_ONE) return
        playNext(manual = false)
    }

    private fun playNext(manual: Boolean) {
        if (queue.isEmpty()) return
        if (index >= queue.size - 1) {
            if (manual) {
                playIndex(0)
            } else {
                releasePlayer()
                currentSong = null
                notifySongChanged()
                notifyPlayState()
                stopSelfAndForeground()
            }
            return
        }
        playIndex(index + 1)
    }

    private fun playPrev() {
        if (queue.isEmpty()) return
        val player = mp
        if (player != null && player.isPlaying && player.currentPosition > 3000) {
            player.seekTo(0)
            return
        }
        playIndex(if (index > 0) index - 1 else 0)
    }

    private fun togglePlayPause() {
        val player = mp ?: return
        if (player.isPlaying) {
            player.pause()
            handler.removeCallbacks(positionTicker)
        } else {
            requestAudioFocus()
            player.start()
            handler.postDelayed(positionTicker, 0)
        }
        notifyPlayState()
    }

    private fun toggleMode() {
        mode = (mode + 1) % MODE_COUNT
        mp?.isLooping = mode == MODE_LOOP_ONE
        notifyMode()
    }

    private fun requestAudioFocus() {
        val am = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        am.requestAudioFocus(
            this,
            AudioManager.STREAM_MUSIC,
            AudioManager.AUDIOFOCUS_GAIN
        )
    }

    override fun onAudioFocusChange(focusChange: Int) {
        when (focusChange) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                mp?.pause()
                handler.removeCallbacks(positionTicker)
                notifyPlayState()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                mp?.pause()
                handler.removeCallbacks(positionTicker)
                notifyPlayState()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                mp?.setVolume(0.3f, 0.3f)
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                mp?.setVolume(1.0f, 1.0f)
                if (currentSong != null) {
                    mp?.start()
                    handler.postDelayed(positionTicker, 0)
                    notifyPlayState()
                }
            }
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.now_playing),
            NotificationManager.IMPORTANCE_LOW
        )
        getSystemService(NotificationManager::class.java)
            .createNotificationChannel(channel)
    }

    private fun startForegroundWithNotification() {
        val song = currentSong ?: return
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, PlayerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MusicService::class.java).setAction(ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            this, 2,
            Intent(this, MusicService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prevIntent = PendingIntent.getService(
            this, 3,
            Intent(this, MusicService::class.java).setAction(ACTION_PREV),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification: Notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_skip_previous, getString(R.string.tab_songs), prevIntent)
            .addAction(
                if (mp?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                getString(R.string.now_playing),
                playPauseIntent
            )
            .addAction(R.drawable.ic_skip_next, getString(R.string.tab_songs), nextIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle())
            .build()

        ServiceCompat.startForeground(
            this, NOTIFICATION_ID, notification,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0
        )
    }

    private fun stopSelfAndForeground() {
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun notifySongChanged() {
        listeners.forEach { it.onSongChanged(currentSong, index) }
    }

    private fun notifyPlayState() {
        val playing = mp?.isPlaying == true
        listeners.forEach { it.onPlayStateChanged(playing) }
        if (playing) {
            (getSystemService(NotificationManager::class.java))
                .notify(NOTIFICATION_ID, buildNotification())
        }
    }

    private fun notifyMode() {
        listeners.forEach { it.onModeChanged(mode) }
    }

    private fun notifyPosition() {
        val player = mp ?: return
        listeners.forEach {
            it.onPositionChanged(player.currentPosition.toLong(), player.duration.toLong())
        }
    }

    private fun buildNotification(): Notification {
        val song = currentSong ?: return NotificationCompat.Builder(this, CHANNEL_ID).build()
        val contentIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, PlayerActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val playPauseIntent = PendingIntent.getService(
            this, 1,
            Intent(this, MusicService::class.java).setAction(ACTION_PLAY_PAUSE),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val nextIntent = PendingIntent.getService(
            this, 2,
            Intent(this, MusicService::class.java).setAction(ACTION_NEXT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val prevIntent = PendingIntent.getService(
            this, 3,
            Intent(this, MusicService::class.java).setAction(ACTION_PREV),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setOngoing(true)
            .setContentIntent(contentIntent)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .addAction(R.drawable.ic_skip_previous, getString(R.string.tab_songs), prevIntent)
            .addAction(
                if (mp?.isPlaying == true) R.drawable.ic_pause else R.drawable.ic_play_arrow,
                getString(R.string.now_playing),
                playPauseIntent
            )
            .addAction(R.drawable.ic_skip_next, getString(R.string.tab_songs), nextIntent)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle())
            .build()
    }
}
