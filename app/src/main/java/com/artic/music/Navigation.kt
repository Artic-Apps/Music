package com.artic.music

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.materials.ExperimentalHazeMaterialsApi
import dev.chrisbanes.haze.materials.HazeMaterials
import kotlinx.coroutines.delay

enum class Screen { Splash, Permission, Home, Recap, History }
enum class Tab { Dashboard, Library, Search, Settings }

var currentTab by mutableStateOf(Tab.Dashboard)

@Composable
fun ArticApp() {
    val context = LocalContext.current
    val colorScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) dynamicDarkColorScheme(context) else darkColorScheme()
    MaterialTheme(colorScheme = colorScheme) { MainContent() }
}

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
                // Content area
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .haze(state = hazeState)
                ) {
                    if (currentScreen == Screen.Recap) {
                        RecapScreen(dataManager, allSongs) { currentScreen = Screen.Home }
                    } else if (currentScreen == Screen.History) {
                        HistoryScreen(dataManager, allSongs) { currentScreen = Screen.Home }
                    } else {
                        HomeContent(allSongs, allAlbums, allArtists, { currentScreen = Screen.Recap }, { currentScreen = Screen.History }, onAlbumRenamed)
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
    onNavigateHistory: () -> Unit,
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
            Tab.Settings -> SettingsScreen(onNavigateRecap, onNavigateHistory, settingsState)
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
            .graphicsLayer {
                shape = barShape
                clip = true
                compositingStrategy = CompositingStrategy.Offscreen
            }
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .hazeChild(
                    state = hazeState,
                    shape = barShape,
                    style = HazeMaterials.ultraThin(surfaceColor.copy(alpha = 0.3f))
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
