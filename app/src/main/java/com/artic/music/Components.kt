package com.artic.music

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import kotlinx.coroutines.delay

// UNIQUE LIST ITEM

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UniqueSongRow(
    song: Song,
    playlists: List<Playlist>? = null,
    onAddToPlaylist: ((String, Long) -> Unit)? = null,
    onCreatePlaylist: ((String) -> Unit)? = null,
    onClick: () -> Unit
) {
    var showDropdown by remember { mutableStateOf(false) }
    var showAddToPlaylist by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = { if (playlists != null) showDropdown = true }
            )
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AsyncImage(
                model = androidx.compose.ui.platform.LocalContext.current.let { 
                    coil.request.ImageRequest.Builder(it)
                        .data(song.albumArtUri)
                        .size(144)
                        .crossfade(true)
                        .build()
                },
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

            // Long-press dropdown
            DropdownMenu(expanded = showDropdown, onDismissRequest = { showDropdown = false }) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.PlaylistAdd, null, modifier = Modifier.size(20.dp))
                            Spacer(modifier = Modifier.width(10.dp))
                            Text("Add to Playlist")
                        }
                    },
                    onClick = {
                        showDropdown = false
                        showAddToPlaylist = true
                    }
                )
            }
        }
    }

    // Add to Playlist dialog
    if (showAddToPlaylist && playlists != null && onAddToPlaylist != null) {
        AddToPlaylistDialog(
            song = song,
            playlists = playlists,
            onDismiss = { showAddToPlaylist = false },
            onAddToPlaylist = onAddToPlaylist,
            onCreatePlaylist = onCreatePlaylist ?: {}
        )
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

@Composable
fun AddToPlaylistDialog(
    song: Song,
    playlists: List<Playlist>,
    onDismiss: () -> Unit,
    onAddToPlaylist: (String, Long) -> Unit,
    onCreatePlaylist: (String) -> Unit
) {
    var showCreateField by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Playlist") },
        text = {
            Column {
                if (playlists.isEmpty() && !showCreateField) {
                    Text(
                        "No playlists yet. Create one!",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.secondary
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                playlists.forEach { playlist ->
                    val alreadyIn = playlist.songIds.contains(song.id)
                    Surface(
                        onClick = {
                            if (!alreadyIn) {
                                onAddToPlaylist(playlist.id, song.id)
                                onDismiss()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (alreadyIn) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f) else Color.Transparent,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Rounded.QueueMusic,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                playlist.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            if (alreadyIn) {
                                Icon(
                                    Icons.Rounded.Check,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                if (showCreateField) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("Playlist name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = {
                            if (newName.isNotBlank()) {
                                onCreatePlaylist(newName.trim())
                                newName = ""
                                showCreateField = false
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(10.dp)
                    ) { Text("Create & Add Later") }
                } else {
                    TextButton(
                        onClick = { showCreateField = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Rounded.Add, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Create New Playlist")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
fun SongSelectorDialog(
    allSongs: List<Song>,
    currentSongIds: List<Long>,
    onDismiss: () -> Unit,
    onAddSongs: (List<Long>) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var selectedIds by remember { mutableStateOf(setOf<Long>()) }

    // Filter songs: not in playlist AND matches search
    val availableSongs = remember(allSongs, currentSongIds, searchQuery) {
        allSongs.filter { !currentSongIds.contains(it.id) &&
                (it.title.contains(searchQuery, true) || it.artist.contains(searchQuery, true))
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Songs") },
        text = {
            Column(modifier = Modifier.height(400.dp)) {
                // Search Field
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Search songs...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    shape = RoundedCornerShape(12.dp)
                )

                if (availableSongs.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No songs found", color = MaterialTheme.colorScheme.secondary)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(availableSongs, key = { it.id }) { song ->
                            val isSelected = selectedIds.contains(song.id)
                            Surface(
                                onClick = {
                                    selectedIds = if (isSelected) selectedIds - song.id else selectedIds + song.id
                                },
                                color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha=0.3f) else Color.Transparent,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    AsyncImage(
                                        model = song.albumArtUri,
                                        contentDescription = null,
                                        modifier = Modifier.size(40.dp).clip(RoundedCornerShape(8.dp)),
                                        contentScale = ContentScale.Crop
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(song.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1)
                                        Text(song.artist, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary, maxLines = 1)
                                    }
                                    Checkbox(
                                        checked = isSelected,
                                        onCheckedChange = { 
                                            selectedIds = if (it) selectedIds + song.id else selectedIds - song.id
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { 
                    if (selectedIds.isNotEmpty()) {
                        onAddSongs(selectedIds.toList())
                    }
                },
                enabled = selectedIds.isNotEmpty()
            ) {
                Text("Add (${selectedIds.size})")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

