package com.artic.music

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import org.burnoutcrew.reorderable.*

// FLOATING MINI PLAYER 

@Composable
fun FloatingMiniPlayer(song: Song, isPlaying: Boolean, onPlayPause: () -> Unit, onClick: () -> Unit) {
    val progress by animateFloatAsState(
        targetValue = if (AudioEngine.duration > 0) AudioEngine.currentPosition.toFloat() / AudioEngine.duration.toFloat() else 0f,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "progress"
    )
    val progressColor = MaterialTheme.colorScheme.primary
    val trackColor = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.15f)

    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
        shadowElevation = 16.dp,
        modifier = Modifier.fillMaxWidth().height(72.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(12.dp)) {
            // Album art with circular progress ring
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.size(52.dp)
            ) {
                // Circular progress ring
                Canvas(modifier = Modifier.size(52.dp)) {
                    val strokeWidth = 3.dp.toPx()
                    val arcSize = size.width - strokeWidth
                    val topLeft = Offset(strokeWidth / 2f, strokeWidth / 2f)

                    // Track (background ring)
                    drawArc(
                        color = trackColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(arcSize, arcSize),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )

                    // Progress arc
                    drawArc(
                        color = progressColor,
                        startAngle = -90f,
                        sweepAngle = progress * 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = Size(arcSize, arcSize),
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                // Album art (circular)
                AsyncImage(
                    model = androidx.compose.ui.platform.LocalContext.current.let {
                        coil.request.ImageRequest.Builder(it)
                            .data(song.albumArtUri)
                            .size(144)
                            .crossfade(true)
                            .build()
                    },
                    contentDescription = null,
                    modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surface),
                    contentScale = ContentScale.Crop
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
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
                                            model = androidx.compose.ui.platform.LocalContext.current.let { 
                                                coil.request.ImageRequest.Builder(it)
                                                    .data(queueSong.albumArtUri)
                                                    .size(144)
                                                    .crossfade(true)
                                                    .build()
                                            },
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
