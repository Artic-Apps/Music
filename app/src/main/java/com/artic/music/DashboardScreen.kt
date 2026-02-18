package com.artic.music

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(
    songs: List<Song>,
    albums: List<Album>,
    artists: List<Artist>,
    state: LazyListState,
    onAlbumClick: (Album) -> Unit,
    onArtistClick: (Artist) -> Unit,
    onAlbumRenamed: (Album, String) -> Unit,
    playlists: List<Playlist> = emptyList(),
    onAddToPlaylist: ((String, Long) -> Unit)? = null,
    onCreatePlaylist: ((String) -> Unit)? = null
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
            Text(
                text = buildAnnotatedString {
                    append("Jump ")
                    withStyle(SpanStyle(color = MaterialTheme.colorScheme.primary)) {
                        append("back in")
                    }
                },
                style = MaterialTheme.typography.headlineLarge, 
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(modifier = Modifier.height(24.dp))
        }

        // Albums Section
        item {
            Text(stringResource(R.string.dashboard_albums), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            Spacer(modifier = Modifier.height(16.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                items(albums, key = { it.id }) { album ->
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
                            AsyncImage(
                                model = androidx.compose.ui.platform.LocalContext.current.let { 
                                    coil.request.ImageRequest.Builder(it)
                                        .data(album.artUri)
                                        .size(400)
                                        .crossfade(true)
                                        .build()
                                },
                                contentDescription = null, 
                                contentScale = ContentScale.Crop, 
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
                            )
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
                items(artists, key = { it.name }) { artist ->
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
                                model = androidx.compose.ui.platform.LocalContext.current.let { 
                                    coil.request.ImageRequest.Builder(it)
                                        .data(artist.artUri)
                                        .size(300)
                                        .crossfade(true)
                                        .build()
                                },
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
                items(quickAccessSongs, key = { it.id }) { song ->
                    Column(modifier = Modifier.width(140.dp).clickable { 
                        // Create a random queue with the clicked song first
                        val randomQueue = songs.shuffled().toMutableList()
                        randomQueue.remove(song)
                        randomQueue.add(0, song)
                        AudioEngine.play(context, song, randomQueue)
                    }) {
                        Surface(shape = RoundedCornerShape(20.dp), modifier = Modifier.size(140.dp), shadowElevation = 4.dp) {
                            AsyncImage(
                                model = androidx.compose.ui.platform.LocalContext.current.let { 
                                    coil.request.ImageRequest.Builder(it)
                                        .data(song.albumArtUri)
                                        .size(400)
                                        .crossfade(true)
                                        .build()
                                },
                                contentDescription = null, 
                                contentScale = ContentScale.Crop, 
                                modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant)
                            )
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
            UniqueSongRow(song, playlists = playlists, onAddToPlaylist = onAddToPlaylist, onCreatePlaylist = onCreatePlaylist) { 
                val randomQueue = songs.shuffled().toMutableList()
                randomQueue.remove(song)
                randomQueue.add(0, song)
                AudioEngine.play(context, song, randomQueue)
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AlbumDetailScreen(album: Album, songs: List<Song>, onBack: () -> Unit) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header with album art
        Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
            AsyncImage(
                model = album.artUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                )
            ))
            
            // Back button
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(20.dp)
                    .padding(top = 24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            
            // Album info at bottom
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                Text(
                    album.title,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${album.artist} · ${songs.size} songs",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        
        // Play All / Shuffle buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { 
                    if (songs.isNotEmpty()) AudioEngine.play(context, songs.first(), songs) 
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.detail_play_all))
            }
            OutlinedButton(
                onClick = {
                    if (songs.isNotEmpty()) {
                        val shuffled = songs.shuffled()
                        AudioEngine.play(context, shuffled.first(), shuffled)
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.detail_shuffle))
            }
        }
        
        // Song list
        LazyColumn(contentPadding = PaddingValues(bottom = 200.dp)) {
            itemsIndexed(songs) { index, song ->
                Surface(
                    onClick = { AudioEngine.play(context, song, songs) },
                    modifier = Modifier.fillMaxWidth(),
                    color = Color.Transparent
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "${index + 1}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.width(32.dp)
                        )
                        AsyncImage(
                            model = song.albumArtUri,
                            contentDescription = null,
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                song.title,
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                song.artist,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                maxLines = 1
                            )
                        }
                        val durationStr = "${(song.duration / 60000).toInt()}:${String.format("%02d", (song.duration % 60000 / 1000).toInt())}"
                        Text(durationStr, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArtistDetailScreen(artist: Artist, songs: List<Song>, onBack: () -> Unit) {
    val context = LocalContext.current
    
    // Group songs by album
    val albumGroups = songs.groupBy { it.albumName }
    
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Box(modifier = Modifier.fillMaxWidth().height(240.dp)) {
            AsyncImage(
                model = artist.artUri,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                )
            ))
            
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(20.dp)
                    .padding(top = 24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = stringResource(R.string.back))
            }
            
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                Text(
                    artist.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${songs.size} songs · ${albumGroups.size} albums",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }
        
        // Play All / Shuffle
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = { 
                    if (songs.isNotEmpty()) AudioEngine.play(context, songs.first(), songs) 
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.detail_play_all))
            }
            OutlinedButton(
                onClick = {
                    if (songs.isNotEmpty()) {
                        val shuffled = songs.shuffled()
                        AudioEngine.play(context, shuffled.first(), shuffled)
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.detail_shuffle))
            }
        }
        
        // Songs grouped by album
        LazyColumn(contentPadding = PaddingValues(bottom = 200.dp)) {
            albumGroups.forEach { (albumName, albumSongs) ->
                item {
                    Text(
                        albumName,
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp)
                    )
                }
                items(albumSongs) { song ->
                    Surface(
                        onClick = { AudioEngine.play(context, song, songs) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            AsyncImage(
                                model = song.albumArtUri,
                                contentDescription = null,
                                modifier = Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    song.albumName,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1
                                )
                            }
                            val dur = "${(song.duration / 60000).toInt()}:${String.format("%02d", (song.duration % 60000 / 1000).toInt())}"
                            Text(dur, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaylistDetailScreen(
    playlist: Playlist,
    allSongs: List<Song>,
    onBack: () -> Unit,
    onRemoveSong: (String, Long) -> Unit,
    onAddToPlaylist: (String, Long) -> Unit
) {
    val context = LocalContext.current
    val playlistSongs = remember(playlist, allSongs) {
        playlist.songIds.mapNotNull { id -> allSongs.find { it.id == id } }
    }
    
    // Add Songs Dialog State
    var showAddSongsDialog by remember { mutableStateOf(false) }
    val coverArts = remember(playlistSongs) {
        playlistSongs.map { it.albumArtUri }.distinct().take(4)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // Header
        Box(modifier = Modifier.fillMaxWidth().height(280.dp)) {
            // Mosaic or single cover background
            if (coverArts.size >= 4) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(modifier = Modifier.weight(1f)) {
                        coverArts.take(2).forEach { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                    Row(modifier = Modifier.weight(1f)) {
                        coverArts.drop(2).take(2).forEach { uri ->
                            AsyncImage(
                                model = uri,
                                contentDescription = null,
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                }
            } else if (coverArts.isNotEmpty()) {
                AsyncImage(
                    model = coverArts.first(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Rounded.QueueMusic,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MaterialTheme.colorScheme.secondary
                    )
                }
            }

            // Gradient overlay
            Box(modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, MaterialTheme.colorScheme.background)
                )
            ))

            // Back button
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .padding(20.dp)
                    .padding(top = 24.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            ) {
                Icon(Icons.Rounded.ArrowBack, contentDescription = "Back")
            }

            // Playlist info
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(20.dp)
            ) {
                Text(
                    playlist.name,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${playlistSongs.size} songs",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.secondary
                )
            }
        }

        // Play All / Shuffle buttons
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Button(
                onClick = {
                    if (playlistSongs.isNotEmpty()) AudioEngine.play(context, playlistSongs.first(), playlistSongs)
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                enabled = playlistSongs.isNotEmpty()
            ) {
                Icon(Icons.Rounded.PlayArrow, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Play All")
            }
            OutlinedButton(
                onClick = {
                    if (playlistSongs.isNotEmpty()) {
                        val shuffled = playlistSongs.shuffled()
                        AudioEngine.play(context, shuffled.first(), shuffled)
                    }
                },
                modifier = Modifier.weight(1f),
                shape = RoundedCornerShape(16.dp),
                enabled = playlistSongs.isNotEmpty()
            ) {
                Icon(Icons.Rounded.Shuffle, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Shuffle")
            }
        }
        
        // Add Songs Button
        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp).padding(bottom = 12.dp)) {
            OutlinedButton(
                onClick = { showAddSongsDialog = true },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Rounded.Add, null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Add Songs")
            }
        }

        // Song list
        if (playlistSongs.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxWidth().weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Rounded.MusicNote,
                        contentDescription = null,
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "No songs yet",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Text(
                        "Add songs from your library",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f)
                    )
                }
            }
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 200.dp)) {
                itemsIndexed(playlistSongs) { index, song ->
                    Surface(
                        onClick = { AudioEngine.play(context, song, playlistSongs) },
                        modifier = Modifier.fillMaxWidth(),
                        color = Color.Transparent
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "${index + 1}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.width(32.dp)
                            )
                            AsyncImage(
                                model = song.albumArtUri,
                                contentDescription = null,
                                modifier = Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)),
                                contentScale = ContentScale.Crop
                            )
                            Spacer(modifier = Modifier.width(14.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    song.title,
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    song.artist,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.secondary,
                                    maxLines = 1
                                )
                            }
                            // Remove from playlist
                            IconButton(onClick = { onRemoveSong(playlist.id, song.id) }) {
                                Icon(
                                    Icons.Rounded.RemoveCircleOutline,
                                    contentDescription = "Remove",
                                    tint = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
            
    // Song Selector Dialog
    if (showAddSongsDialog) {
        SongSelectorDialog(
            allSongs = allSongs,
            currentSongIds = playlist.songIds,
            onDismiss = { showAddSongsDialog = false },
            onAddSongs = { selectedIds ->
                selectedIds.forEach { songId ->
                    onAddToPlaylist(playlist.id, songId)
                }
            }
        )
    }
}
    }
}
