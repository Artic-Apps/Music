package com.artic.music

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.RenderEffect
import android.graphics.Shader
import android.media.AudioAttributes
import android.media.MediaMetadata
import android.media.MediaPlayer
import android.media.session.MediaSession
import android.media.session.PlaybackState
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.provider.MediaStore
import android.text.format.DateUtils
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.util.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.res.colorResource
import androidx.compose.ui.res.dimensionResource
import org.burnoutcrew.reorderable.*

// DATA MODELS

data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val albumId: Long,
    val albumName: String,
    val duration: Long,
    val path: String,
    val dateAdded: Long = 0L
) {
    val albumArtUri: Uri
        get() = ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId
        )
}

data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val artUri: Uri
)

data class Artist(
    val name: String,
    val songCount: Int,
    val artUri: Uri // Use art from first song
)

data class Playlist(val id: String, val name: String, val songIds: List<Long>)

// AUDIO ENGINE
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

        val notification = Notification.Builder(this, CHANNEL_ID)
            .setStyle(Notification.MediaStyle().setMediaSession(mediaSession.sessionToken).setShowActionsInCompactView(0, 1, 2))
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

// MAIN ACTIVITY
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ArticApp() }
    }
}

@Composable
fun ArticApp() {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context) else darkColorScheme()
    MaterialTheme(colorScheme = colorScheme) { MainContent() }
}

// UI COMPONENTS

enum class Screen { Splash, Permission, Home, Recap }
enum class Tab { Dashboard, Library, Search, Settings }

var currentTab by mutableStateOf(Tab.Dashboard)

@Composable
fun MainContent() {
    val context = LocalContext.current
    val dataManager = remember { DataManager(context) }
    var currentScreen by remember { mutableStateOf(Screen.Splash) }
    val hasPerm = remember { ContextCompat.checkSelfPermission(context, if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED }
    var allSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var allAlbums by remember { mutableStateOf<List<Album>>(emptyList()) }
    var allArtists by remember { mutableStateOf<List<Artist>>(emptyList()) }

    LaunchedEffect(currentScreen) {
        if (currentScreen == Screen.Home && allSongs.isEmpty()) {
            allSongs = SongRepository.getAudioFiles(context)
            // Load any saved album renames
            val savedRenames = dataManager.getAllAlbumRenames()
            allAlbums = allSongs.groupBy { it.albumId }.map { (id, songs) ->
                val originalName = songs.first().albumName
                val displayName = savedRenames[id] ?: originalName
                Album(id, displayName, songs.first().artist, songs.first().albumArtUri)
            }
            allArtists = allSongs.groupBy { it.artist }.map { (artistName, songs) ->
                Artist(artistName, songs.size, songs.first().albumArtUri)
            }.sortedByDescending { it.songCount }
            AudioEngine.songQueue.clear()
            AudioEngine.songQueue.addAll(allSongs)
            // Store all songs for random suggestions
            AudioEngine.allSongsLibrary.clear()
            AudioEngine.allSongsLibrary.addAll(allSongs)
        }
    }
    
    // Callback to handle album rename
    val onAlbumRenamed: (Album, String) -> Unit = { album, newName ->
        dataManager.saveAlbumRename(album.id, newName)
        // Update the allAlbums list with the new name
        allAlbums = allAlbums.map { 
            if (it.id == album.id) album.copy(title = newName) else it 
        }
    }
    LaunchedEffect(AudioEngine.isPlaying) {
        while(AudioEngine.isPlaying) {
            AudioEngine.currentPosition = AudioEngine.mediaPlayer?.currentPosition?.toLong() ?: 0L
            AudioEngine.checkSleepTimer(context) // Check if sleep timer expired
            delay(1000)
            DataManager(context).addListeningTime(1000)
        }
    }

    if (currentScreen == Screen.Splash) {
        SplashScreen { currentScreen = if (hasPerm) Screen.Home else Screen.Permission }
    } else if (currentScreen == Screen.Permission) {
        PermissionScreen { currentScreen = Screen.Home }
    } else {
        val showPlayer = remember { mutableStateOf(false) }

        Scaffold(
            containerColor = MaterialTheme.colorScheme.background,
        ) { padding ->
            // Haze state for backdrop blur
            val hazeState = remember { HazeState() }

            Box(modifier = Modifier.padding(padding).fillMaxSize()) {
                // Content with haze source - this content will be blurred behind the nav bar
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .haze(state = hazeState)
                ) {
                    if (currentScreen == Screen.Recap) {
                        RecapScreen(dataManager, allSongs) { currentScreen = Screen.Home }
                    } else {
                        HomeContent(allSongs, allAlbums, allArtists, { currentScreen = Screen.Recap }, onAlbumRenamed)
                    }

                    // Mini Player (also part of content that gets blurred through nav bar)
                    if (AudioEngine.currentSong != null) {
                        Column(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(horizontal = 16.dp)
                                .padding(bottom = if (currentScreen == Screen.Home) 120.dp else 24.dp)
                                .navigationBarsPadding()
                        ) {
                            FloatingMiniPlayer(
                                song = AudioEngine.currentSong!!,
                                isPlaying = AudioEngine.isPlaying,
                                onPlayPause = { AudioEngine.togglePlay(context) },
                                onClick = { showPlayer.value = true }
                            )
                        }
                    }
                }

                if (currentScreen == Screen.Home) {
                    ModernNavBar(
                        hazeState = hazeState,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(horizontal = 16.dp, vertical = 24.dp)
                            .navigationBarsPadding()
                    )
                }
            }
        }

        if (showPlayer.value && AudioEngine.currentSong != null) {
            Box(modifier = Modifier.fillMaxSize().zIndex(100f).background(MaterialTheme.colorScheme.background)) {
                ImmersivePlayerScreen(
                    song = AudioEngine.currentSong!!,
                    onClose = { showPlayer.value = false },
                    onPlayPause = { AudioEngine.togglePlay(context) },
                    onNext = { AudioEngine.playNext(context) },
                    onPrev = { AudioEngine.playPrev(context) }
                )
            }
        }
    }
}

@Composable
fun HomeContent(
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    onNavigateRecap: () -> Unit,
    onAlbumRenamed: (Album, String) -> Unit
) {
    val context = LocalContext.current

    val dashboardState = rememberLazyListState()
    val libraryState = rememberLazyListState()
    val searchState = rememberLazyListState()
    val settingsState = rememberScrollState()

    var searchQuery by remember { mutableStateOf("") }
    
    // Detail view states
    var selectedAlbum by remember { mutableStateOf<Album?>(null) }
    var selectedArtist by remember { mutableStateOf<Artist?>(null) }

    // Handle back press for detail views
    BackHandler(enabled = selectedAlbum != null || selectedArtist != null) {
        selectedAlbum = null
        selectedArtist = null
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (currentTab) {
            Tab.Dashboard -> DashboardScreen(
                songs = songs,
                albums = albums,
                artists = artists,
                state = dashboardState,
                onAlbumClick = { selectedAlbum = it },
                onArtistClick = { selectedArtist = it },
                onAlbumRenamed = onAlbumRenamed
            )
            Tab.Library -> LibraryScreen(songs, libraryState)
            Tab.Search -> SearchScreen(songs, searchState, searchQuery) { searchQuery = it }
            Tab.Settings -> SettingsScreen(onNavigateRecap, settingsState)
        }
        
        // Album Detail Overlay
        selectedAlbum?.let { album ->
            AlbumDetailScreen(
                album = album,
                songs = songs.filter { it.albumId == album.id },
                onBack = { selectedAlbum = null }
            )
        }
        
        // Artist Detail Overlay
        selectedArtist?.let { artist ->
            ArtistDetailScreen(
                artist = artist,
                songs = songs.filter { it.artist == artist.name },
                onBack = { selectedArtist = null }
            )
        }
    }
}

@OptIn(ExperimentalHazeMaterialsApi::class)
@Composable
fun ModernNavBar(
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    // iOS-style Liquid Glass navigation bar
    val barHeight = 72.dp
    val barShape = RoundedCornerShape(36.dp)
    
    // Dynamic colors that adapt to theme
    val surfaceColor = MaterialTheme.colorScheme.surface
    val backgroundColor = MaterialTheme.colorScheme.background

    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(barHeight)
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .shadow(
                    elevation = 16.dp,
                    shape = barShape,
                    spotColor = Color.Black.copy(alpha = 0.15f),
                    ambientColor = Color.Black.copy(alpha = 0.1f)
                )
        )
        
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(barShape)
                .hazeChild(
                    state = hazeState,
                    style = HazeMaterials.ultraThin(surfaceColor.copy(alpha = 0.25f))
                )
        )
        
        Box(
            modifier = Modifier
                .matchParentSize()
                .clip(barShape)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.08f),
                            Color.White.copy(alpha = 0.01f),
                            Color.Black.copy(alpha = 0.02f)
                        )
                    )
                )
        )
        
        Box(
            modifier = Modifier
                .matchParentSize()
                .border(
                    width = 1.5.dp,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.White.copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0.1f),
                            Color.Transparent
                        )
                    ),
                    shape = barShape
                )
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Tab.values().forEach { tab ->
                val selected = currentTab == tab
                val icon = when(tab) {
                    Tab.Dashboard -> Icons.Rounded.Dashboard
                    Tab.Library -> Icons.Rounded.LibraryMusic
                    Tab.Search -> Icons.Rounded.Search
                    Tab.Settings -> Icons.Rounded.Settings
                }

                val scale by animateFloatAsState(
                    targetValue = if (selected) 1.1f else 1f,
                    animationSpec = spring(dampingRatio = 0.6f, stiffness = 400f),
                    label = "scale"
                )
                
                val iconAlpha by animateFloatAsState(
                    targetValue = if (selected) 1f else 0.6f,
                    label = "alpha"
                )

                // Use subtle, iOS-like colors
                val activeColor = MaterialTheme.colorScheme.primary
                val inactiveColor = MaterialTheme.colorScheme.onSurface

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .height(barHeight)
                        .weight(1f)
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { currentTab = tab }
                ) {
                    // Subtle pill indicator for selected tab (iOS style)
                    androidx.compose.animation.AnimatedVisibility(
                        visible = selected,
                        enter = fadeIn(tween(200)) + scaleIn(tween(200), initialScale = 0.8f),
                        exit = fadeOut(tween(150)) + scaleOut(tween(150), targetScale = 0.8f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .background(
                                    Brush.radialGradient(
                                        colors = listOf(
                                            activeColor.copy(alpha = 0.25f),
                                            activeColor.copy(alpha = 0.1f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (selected) activeColor else inactiveColor.copy(alpha = iconAlpha),
                        modifier = Modifier
                            .size(24.dp)
                            .scale(scale)
                    )
                }
            }
        }
    }
}

// --- SCREENS ---

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    state: LazyListState,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onAlbumRenamed: (Album, String) -> Unit
) {
    val context = LocalContext.current

    // Album edit dialog state
    var showEditDialog by remember { mutableStateOf(false) }
    var editingAlbum by remember { mutableStateOf<Album?>(null) }
    var editedAlbumName by remember { mutableStateOf("") }

    // Edit Album Dialog
    if (showEditDialog && editingAlbum != null) {
        AlertDialog(
            onDismissRequest = { showEditDialog = false },
            title = { Text(stringResource(R.string.edit_album_title)) },
            text = {
                Column {
                    Text("Current: ${editingAlbum!!.title}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = editedAlbumName,
                        onValueChange = { editedAlbumName = it },
                        label = { Text(stringResource(R.string.edit_album_new_name)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editedAlbumName.isNotBlank()) {
                        onAlbumRenamed(editingAlbum!!, editedAlbumName.trim())
                        Toast.makeText(context, "Album renamed to: ${editedAlbumName.trim()}", Toast.LENGTH_SHORT).show()
                    }
                    showEditDialog = false
                }) { Text(stringResource(R.string.edit_album_save)) }
            },
            dismissButton = {
                TextButton(onClick = { showEditDialog = false }) { Text(stringResource(R.string.edit_album_cancel)) }
            }
        )
    }

    LazyColumn(state = state, contentPadding = PaddingValues(top = 40.dp, bottom = 200.dp, start = 20.dp, end = 20.dp)) {
        item {
            Text("Jump back in", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Albums Section
        item {
            Text(stringResource(R.string.dashboard_albums), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(albums) { album ->
                    Column(
                        modifier = Modifier
                            .width(140.dp)
                            .combinedClickable(
                                onClick = { onAlbumClick(album) },
                                onLongClick = {
                                    editingAlbum = album
                                    editedAlbumName = album.title
                                    showEditDialog = true
                                }
                            )
                    ) {
                        Surface(shape = RoundedCornerShape(20.dp), modifier = Modifier.size(140.dp), shadowElevation = 4.dp) {
                            AsyncImage(model = album.artUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(album.title, maxLines = 1, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                        Text(album.artist, maxLines = 1, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Artists Section
        item {
            Text(stringResource(R.string.dashboard_artists), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(artists) { artist ->
                    Column(
                        modifier = Modifier
                            .width(120.dp)
                            .clickable { onArtistClick(artist) },
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = CircleShape,
                            modifier = Modifier.size(100.dp),
                            shadowElevation = 4.dp
                        ) {
                            AsyncImage(
                                model = artist.artUri,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(artist.name, maxLines = 1, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
                        Text("${artist.songCount} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Quick Access Section
        item {
            Text("Quick Access", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            
            // Store shuffled songs to show consistent previews
            val quickAccessSongs = remember(songs) { songs.shuffled().take(5) }
            
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(quickAccessSongs) { song ->
                    Column(modifier = Modifier.width(140.dp).clickable { 
                        // Create a random queue with the clicked song first
                        val randomQueue = songs.shuffled().toMutableList()
                        randomQueue.remove(song)
                        randomQueue.add(0, song)
                        AudioEngine.play(context, song, randomQueue)
                    }) {
                        Surface(shape = RoundedCornerShape(20.dp), modifier = Modifier.size(140.dp), shadowElevation = 4.dp) {
                            AsyncImage(model = song.albumArtUri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant))
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(song.title, maxLines = 1, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        // From Your Library Section
        item {
            Text("From Your Library", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
        }
        items(songs.take(10)) { song ->
            UniqueSongRow(song) { 
                val randomQueue = songs.shuffled().toMutableList()
                randomQueue.remove(song)
                randomQueue.add(0, song)
                AudioEngine.play(context, song, randomQueue)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

// Album Detail Screen
@Composable
fun AlbumDetailScreen(album: Album, songs: List<Song>, onBack: () -> Unit) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
            AsyncImage(
                model = album.artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.3f)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                ) {
                    Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                }
                Spacer(modifier = Modifier.weight(1f))
                Row(verticalAlignment = Alignment.Bottom) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.size(120.dp),
                        shadowElevation = 8.dp
                    ) {
                        AsyncImage(
                            model = album.artUri,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(album.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, maxLines = 2)
                        Text(album.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                        Text("${songs.size} songs", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
        
        // Play All Button
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { if (songs.isNotEmpty()) AudioEngine.play(context, songs.first(), songs) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play All")
            }
            OutlinedButton(
                onClick = { if (songs.isNotEmpty()) AudioEngine.play(context, songs.random(), songs.shuffled()) }
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
            }
        }
        
        // Song List
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(songs) { song ->
                UniqueSongRow(song) { AudioEngine.play(context, song, songs) }
                Spacer(modifier = Modifier.height(8.dp))
            }
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

// Artist Detail Screen
@Composable
fun ArtistDetailScreen(artist: Artist, songs: List<Song>, onBack: () -> Unit) {
    val context = LocalContext.current
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Box(modifier = Modifier.fillMaxWidth().height(260.dp)) {
            AsyncImage(
                model = artist.artUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().alpha(0.3f)
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                    ) {
                        Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Surface(
                    shape = CircleShape,
                    modifier = Modifier.size(100.dp),
                    shadowElevation = 8.dp
                ) {
                    AsyncImage(
                        model = artist.artUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(artist.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                Text("${songs.size} songs", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
            }
        }
        
        // Play All Button
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { if (songs.isNotEmpty()) AudioEngine.play(context, songs.first(), songs) },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Rounded.PlayArrow, contentDescription = null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play All")
            }
            OutlinedButton(
                onClick = { if (songs.isNotEmpty()) AudioEngine.play(context, songs.random(), songs.shuffled()) }
            ) {
                Icon(Icons.Rounded.Shuffle, contentDescription = null)
            }
        }
        
        // Song List
        LazyColumn(
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 8.dp),
            modifier = Modifier.weight(1f)
        ) {
            items(songs) { song ->
                UniqueSongRow(song) { AudioEngine.play(context, song, songs) }
                Spacer(modifier = Modifier.height(8.dp))
            }
            item { Spacer(modifier = Modifier.height(100.dp)) }
        }
    }
}

@Composable
fun LibraryScreen(songs: List<Song>, state: LazyListState) {
    val context = LocalContext.current
    var sortOption by remember { mutableStateOf("Name") }
    var isAscending by remember { mutableStateOf(true) }
    
    val sortedSongs = remember(songs, sortOption, isAscending) {
        val list = when (sortOption) {
            "Name" -> songs.sortedBy { it.title.lowercase() }
            "Artist" -> songs.sortedBy { it.artist.lowercase() }
            "Date Added" -> songs.sortedBy { it.dateAdded }
            else -> songs.sortedBy { it.title.lowercase() }
        }
        if (isAscending) list else list.reversed()
    }
    
    LazyColumn(state = state, contentPadding = PaddingValues(top = 40.dp, bottom = 200.dp, start = 20.dp, end = 20.dp)) {
        item {
            Column {
                Text("Library", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                Text("${songs.size} tracks", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
                Spacer(modifier = Modifier.height(16.dp))
                
                // Sorting Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sort Option
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Sort by:", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
                        Spacer(modifier = Modifier.width(8.dp))
                        
                        var showSortMenu by remember { mutableStateOf(false) }
                        Box {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                onClick = { showSortMenu = true }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(sortOption, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                    Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(16.dp))
                                }
                            }
                            
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                listOf("Name", "Artist", "Date Added").forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option) },
                                        onClick = { 
                                            sortOption = option
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    }
                    
                    // Sort Direction
                    IconButton(onClick = { isAscending = !isAscending }) {
                        Icon(
                            if (isAscending) Icons.Rounded.ArrowUpward else Icons.Rounded.ArrowDownward,
                            contentDescription = "Sort Direction",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
            }
        }
        
        items(sortedSongs) { song ->
            UniqueSongRow(song) { AudioEngine.play(context, song) }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@Composable
fun SearchScreen(songs: List<Song>, state: LazyListState, query: String, onQueryChange: (String) -> Unit) {
    val context = LocalContext.current
    val filtered = songs.filter { it.title.contains(query, true) || it.artist.contains(query, true) }

    Column(modifier = Modifier.fillMaxSize().padding(horizontal = 20.dp).padding(top = 20.dp)) {
        Text("Search", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(20.dp))
        Box(
            modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=0.5f))
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            if (query.isEmpty()) Text("Search tracks...", color = MaterialTheme.colorScheme.onSurfaceVariant)
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                textStyle = TextStyle(color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
        LazyColumn(state = state, contentPadding = PaddingValues(bottom = 200.dp)) {
            items(filtered) { song ->
                UniqueSongRow(song) { AudioEngine.play(context, song) }
                Spacer(modifier = Modifier.height(12.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateRecap: () -> Unit, scrollState: ScrollState) {
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text("About Artic") },
            text = { Text("Artic Music Player\nVersion 1.0\n\nA modern, material you music experience.") },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text("Close") } }
        )
    }

    Column(modifier = Modifier
        .fillMaxSize()
        .verticalScroll(scrollState)
        .padding(horizontal = 20.dp)
        .padding(top = 20.dp, bottom = 200.dp)
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
        Spacer(modifier = Modifier.height(32.dp))

        Card(
            onClick = onNavigateRecap,
            shape = RoundedCornerShape(32.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            modifier = Modifier.fillMaxWidth().height(140.dp)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                Icon(Icons.Rounded.AutoGraph, null, modifier = Modifier.align(Alignment.BottomEnd).offset(x=20.dp, y=20.dp).size(120.dp).alpha(0.2f), tint = MaterialTheme.colorScheme.onPrimaryContainer)
                Column(modifier = Modifier.padding(24.dp)) {
                    Text(stringResource(R.string.settings_your_stats), style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, fontWeight = FontWeight.Bold)
                    Text(stringResource(R.string.settings_your_stats_desc), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Spacer(modifier = Modifier.weight(1f))
                    Icon(Icons.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("Library", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(modifier = Modifier.clickable { Toast.makeText(context, "Rescanning...", Toast.LENGTH_SHORT).show() }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Refresh, null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Rescan Storage", style = MaterialTheme.typography.bodyLarge)
                }
                Divider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha=0.5f))
                Row(modifier = Modifier.clickable { showAbout = true }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Info, null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("About Artic", style = MaterialTheme.typography.bodyLarge)
                }
            }
        }
        
        // Playback Settings Section
        Spacer(modifier = Modifier.height(32.dp))
        Text("Playback", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        
        val dataManager = remember { DataManager(context) }
        var autoSuggestEnabled by remember { mutableStateOf(dataManager.isAutoSuggestEnabled()) }
        
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.AutoAwesome, null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Auto-Suggest Songs", style = MaterialTheme.typography.bodyLarge)
                        Text("Play random songs when queue ends", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                    Switch(
                        checked = autoSuggestEnabled,
                        onCheckedChange = { 
                            autoSuggestEnabled = it
                            dataManager.setAutoSuggestEnabled(it)
                        }
                    )
                }
            }
        }
        
        // Sleep Timer Section
        Spacer(modifier = Modifier.height(32.dp))
        Text("Sleep Timer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(16.dp))
        
        var showTimerDialog by remember { mutableStateOf(false) }
        val timerOptions = listOf(15, 30, 45, 60, 90, 120)
        
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    modifier = Modifier
                        .clickable { showTimerDialog = true }
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Rounded.Timer, null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Sleep Timer", style = MaterialTheme.typography.bodyLarge)
                        if (AudioEngine.sleepTimerEndTime != null) {
                            Text(
                                "${AudioEngine.sleepTimerMinutesLeft} min remaining",
                                style = MaterialTheme.typography.bodySmall, 
                                color = MaterialTheme.colorScheme.primary
                            )
                        } else {
                            Text("Stop playback after set time", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                    if (AudioEngine.sleepTimerEndTime != null) {
                        TextButton(onClick = { AudioEngine.cancelSleepTimer() }) {
                            Text("Cancel", color = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
        
        // Sleep Timer Dialog
        if (showTimerDialog) {
            var showCustomInput by remember { mutableStateOf(false) }
            var customInput by remember { mutableStateOf("") }

            AlertDialog(
                onDismissRequest = { showTimerDialog = false },
                title = { Text(if (showCustomInput) "Set Custom Timer" else "Set Sleep Timer") },
                text = {
                    Column {
                        if (showCustomInput) {
                            OutlinedTextField(
                                value = customInput,
                                onValueChange = { if (it.all { char -> char.isDigit() }) customInput = it },
                                label = { Text("Minutes") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                modifier = Modifier.fillMaxWidth()
                            )
                        } else {
                            timerOptions.forEach { minutes ->
                                Surface(
                                    onClick = {
                                        AudioEngine.setSleepTimer(minutes)
                                        showTimerDialog = false
                                        Toast.makeText(context, "Sleep timer set for $minutes minutes", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(16.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Rounded.Timer, null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(16.dp))
                                        Text(
                                            if (minutes < 60) "$minutes minutes" 
                                            else if (minutes == 60) "1 hour"
                                            else "${minutes / 60}h ${minutes % 60}m",
                                            style = MaterialTheme.typography.bodyLarge
                                        )
                                    }
                                }
                            }
                            
                            // allow user selected input
                            Surface(
                                onClick = { showCustomInput = true },
                                modifier = Modifier.fillMaxWidth(),
                                color = Color.Transparent,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(16.dp))
                                    Text("Custom...", style = MaterialTheme.typography.bodyLarge)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    Row {
                        if (showCustomInput) {
                            TextButton(onClick = { showCustomInput = false }) {
                                Text("Back")
                            }
                            TextButton(
                                onClick = {
                                    val minutes = customInput.toIntOrNull()
                                    if (minutes != null && minutes > 0) {
                                        AudioEngine.setSleepTimer(minutes)
                                        showTimerDialog = false
                                        Toast.makeText(context, "Sleep timer set for $minutes minutes", Toast.LENGTH_SHORT).show()
                                    } else {
                                        Toast.makeText(context, "Please enter a valid number", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            ) {
                                Text("Set")
                            }
                        } else {
                            TextButton(onClick = { showTimerDialog = false }) {
                                Text("Cancel")
                            }
                        }
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecapScreen(dataManager: DataManager, allSongs: List<Song>, onBack: () -> Unit) {
    val context = LocalContext.current
    
    // Gather all stats
    val totalMs = dataManager.getTotalListeningTime()
    val hours = totalMs / 3600000
    val mins = (totalMs % 3600000) / 60000
    val streak = dataManager.getCurrentStreak()
    val topArtist = dataManager.getTopArtist()
    val allArtistStats = dataManager.getAllArtistStats().take(10)
    val topSongIds = dataManager.getTopSongIds().take(5)
    val recentIds = dataManager.getRecentlyPlayedIds().take(10)
    val uniqueSongs = dataManager.getUniqueSongsPlayed()
    val uniqueArtists = dataManager.getUniqueArtistsCount()
    
    // Map song IDs to actual songs
    val topSongs = topSongIds.mapNotNull { (id, count) ->
        allSongs.find { it.id == id }?.let { it to count }
    }
    val recentSongs = recentIds.mapNotNull { id -> allSongs.find { it.id == id } }
    
    // Get artist art from songs
    fun getArtistArt(artistName: String) = allSongs.find { it.artist == artistName }?.albumArtUri

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Header
        item {
            Column(modifier = Modifier.padding(20.dp).padding(top = 20.dp)) {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(Icons.Rounded.ArrowBack, null)
                }
                Spacer(modifier = Modifier.height(24.dp))
                Text("Your Stats", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
                Text("Your listening journey", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.secondary)
            }
        }
        
        // Stats Cards Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Listening Time Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(140.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(Icons.Rounded.Timer, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Column {
                            if (hours > 0) {
                                Text("${hours}h ${mins}m", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            } else {
                                Text("$mins min", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimaryContainer)
                            }
                            Text("Listened", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f))
                        }
                    }
                }
                
                // Streak Card
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .height(140.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp).fillMaxSize(),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Icon(Icons.Rounded.LocalFireDepartment, null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
                        Column {
                            Text("$streak", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onTertiaryContainer)
                            Text("Day streak", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onTertiaryContainer.copy(alpha = 0.7f))
                        }
                    }
                }
            }
        }
        
        // Quick Stats Row
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.MusicNote, null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("$uniqueSongs", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Songs played", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                Surface(
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(Icons.Rounded.Person, null, tint = MaterialTheme.colorScheme.primary)
                        Column {
                            Text("$uniqueArtists", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text("Artists", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
        
        // Top Songs Section
        if (topSongs.isNotEmpty()) {
            item {
                Text(
                    "Most Played Songs",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
                )
            }
            items(topSongs) { (song, playCount) ->
                Surface(
                    onClick = { AudioEngine.play(context, song, allSongs) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                        .padding(bottom = 8.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surface
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Rank badge
                        val rank = topSongs.indexOfFirst { it.first.id == song.id } + 1
                        Box(
                            modifier = Modifier
                                .size(28.dp)
                                .clip(CircleShape)
                                .background(
                                    when (rank) {
                                        1 -> Color(0xFFFFD700) // Gold
                                        2 -> Color(0xFFC0C0C0) // Silver
                                        3 -> Color(0xFFCD7F32) // Bronze
                                        else -> MaterialTheme.colorScheme.surfaceVariant
                                    }
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "$rank",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (rank <= 3) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        AsyncImage(
                            model = song.albumArtUri,
                            contentDescription = null,
                            modifier = Modifier
                                .size(50.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("$playCount", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                            Text("plays", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
        
        // Top Artists Section
        if (allArtistStats.isNotEmpty()) {
            item {
                Text(
                    "Top Artists",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(allArtistStats) { (artistName, playCount) ->
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .width(90.dp)
                                .clickable {
                                    val artistSongs = allSongs.filter { it.artist == artistName }
                                    if (artistSongs.isNotEmpty()) AudioEngine.play(context, artistSongs.first(), artistSongs)
                                }
                        ) {
                            Surface(
                                shape = CircleShape,
                                modifier = Modifier.size(70.dp),
                                shadowElevation = 4.dp
                            ) {
                                AsyncImage(
                                    model = getArtistArt(artistName),
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(artistName, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
                            Text("$playCount plays", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
            }
        }
        
        // Recently Played Section
        if (recentSongs.isNotEmpty()) {
            item {
                Text(
                    "Recently Played",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 20.dp).padding(bottom = 12.dp)
                )
            }
            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(recentSongs) { song ->
                        Column(
                            modifier = Modifier
                                .width(120.dp)
                                .clickable { AudioEngine.play(context, song, allSongs) }
                        ) {
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier.size(120.dp),
                                shadowElevation = 4.dp
                            ) {
                                AsyncImage(
                                    model = song.albumArtUri,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(song.title, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}

// FLOATING MINI PLAYER 

@Composable
fun FloatingMiniPlayer(song: Song, isPlaying: Boolean, onPlayPause: () -> Unit, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth().height(72.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            // Static Rounded Art
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSecondaryContainer, maxLines = 1)
                Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha=0.8f), maxLines = 1)
            }
            IconButton(onClick = onPlayPause) {
                Icon(if (isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
        }
    }
}

// IMMERSIVE PLAYER SCREEN

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImmersivePlayerScreen(song: Song, onClose: () -> Unit, onPlayPause: () -> Unit, onNext: () -> Unit, onPrev: () -> Unit) {
    val context = LocalContext.current
    
    // Format duration helper
    fun formatTime(ms: Long): String {
        val s = ms / 1000
        val m = s / 60
        val sec = s % 60
        return String.format("%d:%02d", m, sec)
    }

    val baseColor = Color(0xFF121212)
    
    // Menu and queue state
    var showMenu by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showSpeedPicker by remember { mutableStateOf(false) }
    
    val speedOptions = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    // Handle back press for queue view
    BackHandler(enabled = showQueue) {
        showQueue = false
    }

    Box(modifier = Modifier.fillMaxSize().background(baseColor)) {
        // Blurred Background
        AsyncImage(
            model = song.albumArtUri,
            contentDescription = null,
            modifier = Modifier.fillMaxSize().alpha(0.5f).blur(radius = 80.dp),
            contentScale = ContentScale.Crop
        )

        // Gradient overlay
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.Transparent,
                        baseColor.copy(alpha = 0.5f),
                        baseColor.copy(alpha = 0.95f)
                    )
                )
            )
        )

        // Main Content
        if (showQueue) {
            // Queue View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .statusBarsPadding()
            ) {
                // Queue Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { showQueue = false }) {
                        Icon(Icons.Rounded.ArrowBack, null, tint = Color.White)
                    }
                    Text(stringResource(R.string.player_up_next), style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { AudioEngine.shuffleUpcoming() }) {
                        Icon(Icons.Rounded.Shuffle, null, tint = Color.White)
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.player_shuffle), color = Color.White)
                    }
                }
                
                // Current Song
                Surface(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                    shape = RoundedCornerShape(16.dp),
                    color = Color.White.copy(alpha = 0.15f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AsyncImage(
                            model = song.albumArtUri,
                            contentDescription = null,
                            modifier = Modifier.size(50.dp).clip(RoundedCornerShape(8.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Now Playing", style = MaterialTheme.typography.labelSmall, color = Color.White.copy(alpha = 0.6f))
                            Text(song.title, style = MaterialTheme.typography.bodyLarge, color = Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                            Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.7f), maxLines = 1)
                        }
                        Icon(Icons.Rounded.GraphicEq, null, tint = MaterialTheme.colorScheme.primary)
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                // Upcoming Songs
                val upcomingQueue = AudioEngine.getUpcomingQueue()
                if (upcomingQueue.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("No more songs in queue", color = Color.White.copy(alpha = 0.5f))
                    }
                } else {
                    // Get the current song index to calculate actual queue positions
                    val currentSongIdx = AudioEngine.songQueue.indexOf(AudioEngine.currentSong)
                    
                    val reorderState = rememberReorderableLazyListState(
                        onMove = { from, to ->
                            // Convert from upcoming list index to actual queue index
                            val fromActual = currentSongIdx + 1 + from.index
                            val toActual = currentSongIdx + 1 + to.index
                            AudioEngine.moveInQueue(fromActual, toActual)
                        }
                    )
                    
                    LazyColumn(
                        state = reorderState.listState,
                        modifier = Modifier
                            .weight(1f)
                            .reorderable(reorderState),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        items(upcomingQueue.size, key = { upcomingQueue[it].id }) { index ->
                            val queueSong = upcomingQueue[index]
                            
                            ReorderableItem(reorderState, key = queueSong.id) { isDragging ->
                                val elevation = animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "elevation")
                                
                                Surface(
                                    onClick = { AudioEngine.play(context, queueSong) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp)
                                        .shadow(elevation.value, RoundedCornerShape(12.dp)),
                                    shape = RoundedCornerShape(12.dp),
                                    color = if (isDragging) Color.White.copy(alpha = 0.15f) else Color.White.copy(alpha = 0.08f)
                                ) {
                                    Row(
                                        modifier = Modifier.padding(start = 4.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // Drag handle
                                        Icon(
                                            Icons.Rounded.DragHandle,
                                            contentDescription = "Drag to reorder",
                                            tint = Color.White.copy(alpha = 0.5f),
                                            modifier = Modifier
                                                .detectReorderAfterLongPress(reorderState)
                                                .padding(8.dp)
                                                .size(20.dp)
                                        )
                                        
                                        Text("${index + 1}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.5f), modifier = Modifier.width(24.dp))
                                        AsyncImage(
                                            model = queueSong.albumArtUri,
                                            contentDescription = null,
                                            modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                                            contentScale = ContentScale.Crop
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(queueSong.title, style = MaterialTheme.typography.bodyMedium, color = Color.White, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(queueSong.artist, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.6f), maxLines = 1)
                                        }
                                        IconButton(onClick = { AudioEngine.removeFromQueue(queueSong) }) {
                                            Icon(Icons.Rounded.Close, null, tint = Color.White.copy(alpha = 0.5f), modifier = Modifier.size(20.dp))
                                        }
                                    }
                                }
                            }
                        }
                        item { Spacer(modifier = Modifier.height(100.dp)) }
                    }
                }
            }
        } else {
            // Player View
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .padding(horizontal = 24.dp)
                    .padding(top = 24.dp, bottom = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header with Menu
                Row(modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(top = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                    IconButton(onClick = onClose) { Icon(Icons.Rounded.KeyboardArrowDown, null, tint = Color.White, modifier = Modifier.size(32.dp)) }
                    Text(stringResource(R.string.player_now_playing), style = MaterialTheme.typography.titleMedium, color = Color.White, modifier = Modifier.align(Alignment.CenterVertically))
                    
                    Box {
                        IconButton(onClick = { showMenu = true }) { 
                            Icon(Icons.Rounded.MoreVert, null, tint = Color.White) 
                        }
                        
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false },
                            modifier = Modifier.background(MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Speed, null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Speed: ${AudioEngine.playbackSpeed}x")
                                    }
                                },
                                onClick = { showMenu = false; showSpeedPicker = true }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.QueueMusic, null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Up Next (${AudioEngine.getUpcomingQueue().size})")
                                    }
                                },
                                onClick = { showMenu = false; showQueue = true }
                            )
                            DropdownMenuItem(
                                text = { 
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(20.dp))
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Text("Shuffle Queue")
                                    }
                                },
                                onClick = { 
                                    showMenu = false
                                    AudioEngine.shuffleUpcoming()
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.weight(0.1f))

                // ARTWORK
                Card(
                    shape = RoundedCornerShape(24.dp),
                    elevation = CardDefaults.cardElevation(20.dp),
                    modifier = Modifier
                        .weight(0.8f, fill = false)
                        .aspectRatio(1f)
                        .fillMaxWidth()
                ) {
                    AsyncImage(
                        model = song.albumArtUri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize().background(Color.DarkGray),
                        contentScale = ContentScale.Crop
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Text Info
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(song.title, style = MaterialTheme.typography.headlineMedium, color = Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.titleLarge, color = Color.White.copy(alpha=0.7f), textAlign = TextAlign.Center, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                Spacer(modifier = Modifier.height(32.dp))

                // Seekbar
                Column {
                    Slider(
                        value = if (AudioEngine.duration > 0) AudioEngine.currentPosition.toFloat() / AudioEngine.duration else 0f,
                        onValueChange = { AudioEngine.seekTo((it * AudioEngine.duration).toLong()) },
                        colors = SliderDefaults.colors(
                            thumbColor = Color.White,
                            activeTrackColor = Color.White,
                            inactiveTrackColor = Color.White.copy(alpha = 0.3f)
                        ),
                        modifier = Modifier.height(20.dp)
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(formatTime(AudioEngine.currentPosition), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                        // Show speed if not 1x
                        if (AudioEngine.playbackSpeed != 1.0f) {
                            Text("${AudioEngine.playbackSpeed}x", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Text(formatTime(AudioEngine.duration), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.7f))
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // CONTROLS
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrev, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }

                    Surface(
                        onClick = onPlayPause,
                        shape = CircleShape,
                        color = Color.White,
                        modifier = Modifier.size(72.dp),
                        shadowElevation = 8.dp
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                if (AudioEngine.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                null,
                                modifier = Modifier.size(40.dp),
                                tint = Color.Black
                            )
                        }
                    }

                    IconButton(onClick = onNext, modifier = Modifier.size(56.dp)) {
                        Icon(Icons.Rounded.SkipNext, null, tint = Color.White, modifier = Modifier.size(40.dp))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }
    
    // Speed Picker Dialog
    if (showSpeedPicker) {
        AlertDialog(
            onDismissRequest = { showSpeedPicker = false },
            title = { Text("Playback Speed") },
            text = {
                Column {
                    speedOptions.forEach { speed ->
                        Surface(
                            onClick = {
                                AudioEngine.updatePlaybackSpeed(speed)
                                showSpeedPicker = false
                            },
                            modifier = Modifier.fillMaxWidth(),
                            color = if (AudioEngine.playbackSpeed == speed) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "${speed}x",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = if (AudioEngine.playbackSpeed == speed) FontWeight.Bold else FontWeight.Normal
                                )
                                Spacer(modifier = Modifier.weight(1f))
                                if (speed == 1.0f) {
                                    Text("Normal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                                }
                                if (AudioEngine.playbackSpeed == speed) {
                                    Icon(Icons.Rounded.Check, null, tint = MaterialTheme.colorScheme.primary)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showSpeedPicker = false }) { Text("Close") }
            }
        )
    }
}

// UNIQUE LIST ITEM

@Composable
fun UniqueSongRow(song: Song, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = song.albumArtUri,
                contentDescription = null,
                modifier = Modifier.size(56.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                contentScale = ContentScale.Crop
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(song.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(song.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
            }
            Icon(Icons.Rounded.PlayCircle, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}

// UTILS
@Composable
fun SplashScreen(onDone: () -> Unit) {
    LaunchedEffect(Unit) { delay(1000); onDone() }
    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
        Icon(Icons.Rounded.MusicNote, null, modifier = Modifier.size(80.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

@Composable
fun PermissionScreen(onDone: () -> Unit) {
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { if (it) onDone() }
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Button(onClick = {
            if (Build.VERSION.SDK_INT >= 33) launcher.launch(Manifest.permission.READ_MEDIA_AUDIO)
            else launcher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
        }) { Text("Grant Permissions") }
    }
}

class DataManager(context: Context) {
    private val prefs = context.getSharedPreferences("artic_db", Context.MODE_PRIVATE)

    fun recordPlay(song: Song) {
        // Track artist plays
        val artistCurrent = prefs.getInt("artist_${song.artist}", 0)
        prefs.edit().putInt("artist_${song.artist}", artistCurrent + 1).apply()
        
        // Track song plays
        val songCurrent = prefs.getInt("song_${song.id}", 0)
        prefs.edit().putInt("song_${song.id}", songCurrent + 1).apply()
        
        // Track recently played (store last 20 song IDs)
        val recentJson = prefs.getString("recent_plays", "[]") ?: "[]"
        val recentArray = JSONArray(recentJson)
        // Remove if already exists to move to front
        val newArray = JSONArray()
        newArray.put(song.id)
        for (i in 0 until minOf(recentArray.length(), 19)) {
            if (recentArray.getLong(i) != song.id) {
                newArray.put(recentArray.getLong(i))
            }
        }
        prefs.edit().putString("recent_plays", newArray.toString()).apply()
        
        // Track daily streak
        val today = System.currentTimeMillis() / (1000 * 60 * 60 * 24)
        val lastPlayDay = prefs.getLong("last_play_day", 0L)
        val currentStreak = prefs.getInt("current_streak", 0)
        
        when {
            today == lastPlayDay -> {} // Same day, no change
            today == lastPlayDay + 1 -> {
                // Consecutive day, increase streak
                prefs.edit().putInt("current_streak", currentStreak + 1).apply()
            }
            else -> {
                // Streak broken, reset to 1
                prefs.edit().putInt("current_streak", 1).apply()
            }
        }
        prefs.edit().putLong("last_play_day", today).apply()
    }

    fun addListeningTime(ms: Long) {
        val current = prefs.getLong("total_time", 0L)
        prefs.edit().putLong("total_time", current + ms).apply()
    }

    fun getTotalListeningTime() = prefs.getLong("total_time", 0L)
    
    fun getCurrentStreak() = prefs.getInt("current_streak", 0)

    fun getTopArtist(): Pair<String, Int> {
        val all = prefs.all
        var topName = "None"
        var topCount = 0
        all.keys.filter { it.startsWith("artist_") }.forEach { key ->
            val count = all[key] as? Int ?: 0
            if (count > topCount) {
                topCount = count
                topName = key.removePrefix("artist_")
            }
        }
        return topName to topCount
    }
    
    // Get all artists with play counts, sorted by plays descending
    fun getAllArtistStats(): List<Pair<String, Int>> {
        val all = prefs.all
        return all.keys
            .filter { it.startsWith("artist_") }
            .map { key ->
                val name = key.removePrefix("artist_")
                val count = all[key] as? Int ?: 0
                name to count
            }
            .sortedByDescending { it.second }
    }
    
    // Get top songs by play count
    fun getTopSongIds(): List<Pair<Long, Int>> {
        val all = prefs.all
        return all.keys
            .filter { it.startsWith("song_") }
            .mapNotNull { key ->
                val id = key.removePrefix("song_").toLongOrNull()
                val count = all[key] as? Int ?: 0
                if (id != null) id to count else null
            }
            .sortedByDescending { it.second }
    }
    
    // Get recently played song IDs
    fun getRecentlyPlayedIds(): List<Long> {
        val recentJson = prefs.getString("recent_plays", "[]") ?: "[]"
        val recentArray = JSONArray(recentJson)
        val ids = mutableListOf<Long>()
        for (i in 0 until recentArray.length()) {
            ids.add(recentArray.getLong(i))
        }
        return ids
    }
    
    // Get total unique songs played
    fun getUniqueSongsPlayed(): Int {
        return prefs.all.keys.filter { it.startsWith("song_") }.size
    }
    
    // Get total unique artists listened to
    fun getUniqueArtistsCount(): Int {
        return prefs.all.keys.filter { it.startsWith("artist_") }.size
    }

    fun createPlaylist(name: String) {
        val currentJson = prefs.getString("playlists", "[]")
        val jsonArray = JSONArray(currentJson)
        val newObj = JSONObject().apply {
            put("id", UUID.randomUUID().toString())
            put("name", name)
            put("songs", JSONArray())
        }
        jsonArray.put(newObj)
        prefs.edit().putString("playlists", jsonArray.toString()).apply()
    }

    fun getPlaylists(): List<Playlist> {
        val list = mutableListOf<Playlist>()
        val jsonArray = JSONArray(prefs.getString("playlists", "[]"))
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            list.add(Playlist(obj.getString("id"), obj.getString("name"), emptyList()))
        }
        return list
    }
    
    // Album rename - stores custom album names by album ID
    fun saveAlbumRename(albumId: Long, newName: String) {
        prefs.edit().putString("album_rename_$albumId", newName).apply()
    }
    
    fun getAlbumRename(albumId: Long): String? {
        return prefs.getString("album_rename_$albumId", null)
    }
    
    fun getAllAlbumRenames(): Map<Long, String> {
        val renames = mutableMapOf<Long, String>()
        prefs.all.keys.filter { it.startsWith("album_rename_") }.forEach { key ->
            val albumId = key.removePrefix("album_rename_").toLongOrNull()
            val name = prefs.getString(key, null)
            if (albumId != null && name != null) {
                renames[albumId] = name
            }
        }
        return renames
    }
    
    // Auto-suggest setting - whether to suggest songs from other albums after queue ends
    fun setAutoSuggestEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("auto_suggest_enabled", enabled).apply()
    }
    
    fun isAutoSuggestEnabled(): Boolean {
        return prefs.getBoolean("auto_suggest_enabled", true) // Default: enabled
    }
}

object SongRepository {
    suspend fun getAudioFiles(context: Context): List<Song> = withContext(Dispatchers.IO) {
        val songs = mutableListOf<Song>()
        val collection = if (Build.VERSION.SDK_INT >= 33) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL) else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID, 
            MediaStore.Audio.Media.TITLE, 
            MediaStore.Audio.Media.ARTIST, 
            MediaStore.Audio.Media.ALBUM_ID, 
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION, 
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.DATE_ADDED
        )
        context.contentResolver.query(collection, projection, "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, null)?.use { cursor ->
            val idCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
            val titleCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
            val artistCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
            val albIdCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
            val albNameCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
            val durCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
            val pathCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
            val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
            
            while(cursor.moveToNext()) {
                val filePath = cursor.getString(pathCol)
                var albumName = cursor.getString(albNameCol) ?: ""
                
                if (albumName.isBlank() || 
                    albumName.equals("Unknown Album", ignoreCase = true) ||
                    albumName.equals("<unknown>", ignoreCase = true) ||
                    albumName.equals("Music", ignoreCase = true)) {
                    // Extract parent folder name from file path
                    val parentFolder = java.io.File(filePath).parentFile?.name
                    if (!parentFolder.isNullOrBlank() && 
                        !parentFolder.equals("Music", ignoreCase = true) &&
                        !parentFolder.equals("Download", ignoreCase = true) &&
                        !parentFolder.equals("Downloads", ignoreCase = true)) {
                        albumName = parentFolder
                    } else {
                        albumName = "Unknown Album"
                    }
                }
                
                songs.add(Song(
                    cursor.getLong(idCol), 
                    cursor.getString(titleCol), 
                    cursor.getString(artistCol) ?: "Unknown Artist",
                    cursor.getLong(albIdCol), 
                    albumName, 
                    cursor.getLong(durCol), 
                    filePath,
                    cursor.getLong(dateAddedCol)
                ))
            }
        }
        songs
    }
}