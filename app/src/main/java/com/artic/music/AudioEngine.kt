package com.artic.music

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.os.Build
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AudioEngine {
    var mediaPlayer: MediaPlayer? = null
    var currentSong by mutableStateOf<Song?>(null)
    var isPlaying by mutableStateOf(false)
    var currentPosition by mutableStateOf(0L)
    var duration by mutableStateOf(0L)
    var songQueue = mutableStateListOf<Song>()
    var allSongsLibrary = mutableListOf<Song>() // For random suggestions
    var playbackSpeed by mutableStateOf(1.0f)
    var onStateChanged: ((Boolean) -> Unit)? = null

    // Sleep Timer
    var sleepTimerEndTime by mutableStateOf<Long?>(null)
    var sleepTimerMinutesLeft by mutableStateOf(0)

    fun setSleepTimer(minutes: Int) {
        if (minutes > 0) {
            sleepTimerEndTime = System.currentTimeMillis() + (minutes * 60 * 1000L)
            sleepTimerMinutesLeft = minutes
        } else {
            sleepTimerEndTime = null
            sleepTimerMinutesLeft = 0
        }
    }

    fun cancelSleepTimer() {
        sleepTimerEndTime = null
        sleepTimerMinutesLeft = 0
    }

    fun checkSleepTimer(context: Context) {
        sleepTimerEndTime?.let { endTime ->
            val remaining = endTime - System.currentTimeMillis()
            if (remaining <= 0) {
                // Timer expired - pause playback
                mediaPlayer?.pause()
                isPlaying = false
                startService(context, "PAUSE")
                cancelSleepTimer()
            } else {
                sleepTimerMinutesLeft = (remaining / 60000).toInt() + 1
            }
        }
    }

    fun play(context: Context, song: Song, newQueue: List<Song>? = null) {
        try {
            if (newQueue != null) {
                songQueue.clear()
                songQueue.addAll(newQueue)
            } else if (!songQueue.contains(song)) {
                if (songQueue.isEmpty()) songQueue.add(song)
            }

            if (currentSong?.id == song.id && mediaPlayer != null) {
                togglePlay(context)
                return
            }
            mediaPlayer?.release()
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                        .setUsage(AudioAttributes.USAGE_MEDIA)
                        .build()
                )
                val songUri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, song.id)
                setDataSource(context, songUri)
                setOnPreparedListener {
                    start()
                    // Apply current playback speed
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        playbackParams = playbackParams.setSpeed(playbackSpeed)
                    }
                    this@AudioEngine.isPlaying = true
                    this@AudioEngine.duration = it.duration.toLong()
                    startService(context, "PLAY")
                }
                setOnCompletionListener { playNext(context) }
                setOnSeekCompleteListener { mp ->
                    this@AudioEngine.currentPosition = mp.currentPosition.toLong()
                }
                prepareAsync()
            }
            currentSong = song
            DataManager(context).recordPlay(song)
        } catch (e: Exception) { e.printStackTrace() }
    }

    fun togglePlay(context: Context) {
        mediaPlayer?.let {
            if (it.isPlaying) {
                it.pause()
                isPlaying = false
                startService(context, "PAUSE")
            } else {
                it.start()
                isPlaying = true
                startService(context, "RESUME")
            }
        }
    }

    fun playNext(context: Context) {
        val idx = songQueue.indexOf(currentSong)
        if (idx != -1 && idx < songQueue.size - 1) {
            play(context, songQueue[idx + 1])
        } else {
            // Queue ended - check if auto-suggest is enabled
            val dataManager = DataManager(context)
            if (dataManager.isAutoSuggestEnabled() && allSongsLibrary.isNotEmpty()) {
                // Pick a truly random song from entire library
                val randomSong = allSongsLibrary.random()
                songQueue.add(randomSong)
                play(context, randomSong)
                return
            }
            isPlaying = false
            startService(context, "PAUSE")
        }
    }

    fun playPrev(context: Context) {
        val idx = songQueue.indexOf(currentSong)
        if (idx > 0) play(context, songQueue[idx - 1])
    }

    fun seekTo(pos: Long) {
        currentPosition = pos
        mediaPlayer?.let { mp ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                mp.seekTo(pos, MediaPlayer.SEEK_CLOSEST)
            } else {
                mp.seekTo(pos.toInt())
            }
        }
    }
    
    fun updatePlaybackSpeed(speed: Float) {
        playbackSpeed = speed
        mediaPlayer?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                it.playbackParams = it.playbackParams.setSpeed(speed)
            }
        }
    }
    
    fun removeFromQueue(song: Song) {
        if (song.id != currentSong?.id) {
            songQueue.remove(song)
        }
    }
    
    fun moveInQueue(from: Int, to: Int) {
        if (from in songQueue.indices && to in songQueue.indices) {
            val item = songQueue.removeAt(from)
            songQueue.add(to, item)
        }
    }
    
    fun getUpcomingQueue(): List<Song> {
        val idx = songQueue.indexOf(currentSong)
        return if (idx != -1 && idx < songQueue.size - 1) {
            songQueue.subList(idx + 1, songQueue.size).toList()
        } else {
            emptyList()
        }
    }
    
    fun shuffleUpcoming() {
        val idx = songQueue.indexOf(currentSong)
        if (idx != -1 && idx < songQueue.size - 1) {
            // Get songs before current (including current)
            val beforeCurrent = songQueue.take(idx + 1)
            // Get and shuffle upcoming songs
            val upcoming = songQueue.drop(idx + 1).shuffled()
            // Rebuild the queue
            songQueue.clear()
            songQueue.addAll(beforeCurrent)
            songQueue.addAll(upcoming)
        }
    }

    private fun startService(context: Context, action: String) {
        val intent = Intent(context, ArticMusicService::class.java).apply { this.action = action }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) context.startForegroundService(intent) else context.startService(intent)
        onStateChanged?.invoke(isPlaying)
    }
}
