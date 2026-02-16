package com.artic.music

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore

class ArticMusicService : Service() {
    private lateinit var mediaSession: MediaSession
    private val CHANNEL_ID = "ArticMusicChannel"
    private val NOTIFICATION_ID = 101

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        mediaSession = MediaSession(this, "ArticMusicSession").apply {
            setCallback(object : MediaSession.Callback() {
                override fun onPlay() { AudioEngine.togglePlay(this@ArticMusicService) }
                override fun onPause() { AudioEngine.togglePlay(this@ArticMusicService) }
                override fun onSkipToNext() { AudioEngine.playNext(this@ArticMusicService) }
                override fun onSkipToPrevious() { AudioEngine.playPrev(this@ArticMusicService) }
                override fun onSeekTo(pos: Long) { 
                    AudioEngine.seekTo(pos)
                    updateNotification(AudioEngine.isPlaying) 
                }
            })
            isActive = true
        }
        AudioEngine.onStateChanged = { isPlaying -> updateNotification(isPlaying) }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "PLAY", "RESUME" -> updateNotification(true)
            "PAUSE" -> updateNotification(false)
            "NEXT" -> AudioEngine.playNext(this)
            "PREV" -> AudioEngine.playPrev(this)
        }
        return START_NOT_STICKY
    }

    private fun updateNotification(isPlaying: Boolean) {
        val song = AudioEngine.currentSong ?: return
        
        val stateBuilder = PlaybackState.Builder()
            .setActions(
                PlaybackState.ACTION_PLAY or 
                PlaybackState.ACTION_PAUSE or 
                PlaybackState.ACTION_PLAY_PAUSE or
                PlaybackState.ACTION_SKIP_TO_NEXT or 
                PlaybackState.ACTION_SKIP_TO_PREVIOUS or
                PlaybackState.ACTION_SEEK_TO
            )
            .setState(
                if (isPlaying) PlaybackState.STATE_PLAYING else PlaybackState.STATE_PAUSED, 
                AudioEngine.currentPosition, 
                if (isPlaying) AudioEngine.playbackSpeed else 0f,
                android.os.SystemClock.elapsedRealtime()
            )
        mediaSession.setPlaybackState(stateBuilder.build())

        val metadataBuilder = MediaMetadata.Builder()
            .putString(MediaMetadata.METADATA_KEY_TITLE, song.title)
            .putString(MediaMetadata.METADATA_KEY_ARTIST, song.artist)
            .putString(MediaMetadata.METADATA_KEY_ALBUM, song.albumName)
            .putLong(MediaMetadata.METADATA_KEY_DURATION, song.duration)
        try {
            val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, song.albumArtUri)
            metadataBuilder.putBitmap(MediaMetadata.METADATA_KEY_ALBUM_ART, bitmap)
        } catch (e: Exception) { }
        mediaSession.setMetadata(metadataBuilder.build())

        val openAppIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_IMMUTABLE
        )

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0, 1, 2))
            .setContentIntent(openAppIntent)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(song.title)
            .setContentText(song.artist)
            .setLargeIcon(try { MediaStore.Images.Media.getBitmap(contentResolver, song.albumArtUri) } catch(e:Exception){ null })
            .addAction(Notification.Action(android.R.drawable.ic_media_previous, "Previous", PendingIntent.getService(this, 0, Intent(this, ArticMusicService::class.java).apply { action = "PREV" }, PendingIntent.FLAG_IMMUTABLE)))
            .addAction(Notification.Action(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, "Play/Pause", PendingIntent.getService(this, 1, Intent(this, ArticMusicService::class.java).apply { action = if(isPlaying) "PAUSE" else "RESUME" }, PendingIntent.FLAG_IMMUTABLE)))
            .addAction(Notification.Action(android.R.drawable.ic_media_next, "Next", PendingIntent.getService(this, 2, Intent(this, ArticMusicService::class.java).apply { action = "NEXT" }, PendingIntent.FLAG_IMMUTABLE)))
            .setVisibility(Notification.VISIBILITY_PUBLIC)
            .setOngoing(isPlaying)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Music Playback", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Media Controls"
                setShowBadge(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }
    override fun onBind(intent: Intent?): IBinder? = null
    override fun onDestroy() { mediaSession.release(); super.onDestroy() }
}
