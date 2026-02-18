package com.artic.music

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import coil.compose.AsyncImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun LibraryScreen(
    songs: List<Song>,
    state: LazyListState,
    playlists: List<Playlist>,
    allSongs: List<Song>,
    onCreatePlaylist: (String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onRenamePlaylist: (String, String) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onAddSongToPlaylist: (String, Long) -> Unit
) {
    val context = LocalContext.current
    val tabs = listOf(stringResource(R.string.library_tab_songs), stringResource(R.string.library_tab_playlists))
    val pagerState = rememberPagerState(pageCount = { tabs.size })
    val coroutineScope = rememberCoroutineScope()

    // Create playlist dialog
    var showCreateDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    // Rename dialog
    var showRenameDialog by remember { mutableStateOf(false) }
    var renamingPlaylist by remember { mutableStateOf<Playlist?>(null) }
    var renameText by remember { mutableStateOf("") }

    // Delete confirm dialog
    var showDeleteDialog by remember { mutableStateOf(false) }
    var deletingPlaylist by remember { mutableStateOf<Playlist?>(null) }

    Column(modifier = Modifier.fillMaxSize()) {
        // Header
        Column(modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 40.dp)) {
            Text("Library", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.ExtraBold)
            Text(
                stringResource(R.string.library_stats_header, songs.size, playlists.size),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.secondary
            )
            Spacer(modifier = Modifier.height(16.dp))

            // Tabs
            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = {}
            ) {
                tabs.forEachIndexed { index, title ->
                    Tab(
                        selected = pagerState.currentPage == index,
                        onClick = { coroutineScope.launch { pagerState.animateScrollToPage(index) } },
                        text = {
                            Text(
                                title,
                                fontWeight = if (pagerState.currentPage == index) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    )
                }
            }
        }

        // Swipeable Tab Content
        HorizontalPager(state = pagerState, modifier = Modifier.fillMaxSize()) { page ->
            when (page) {
                0 -> SongsTab(songs, state, playlists, onAddSongToPlaylist, onCreatePlaylist)
                1 -> PlaylistsTab(
                    playlists = playlists,
                    allSongs = allSongs,
                    onCreateClick = { showCreateDialog = true },
                    onPlaylistClick = onPlaylistClick,
                    onRename = { pl ->
                        renamingPlaylist = pl
                        renameText = pl.name
                        showRenameDialog = true
                    },
                    onDelete = { pl ->
                        deletingPlaylist = pl
                        showDeleteDialog = true
                    }
                )
            }
        }
    }

    // Create Playlist Dialog
    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false; newPlaylistName = "" },
            title = { Text(stringResource(R.string.library_create_new_playlist)) },
            text = {
                OutlinedTextField(
                    value = newPlaylistName,
                    onValueChange = { newPlaylistName = it },
                    label = { Text(stringResource(R.string.library_create_playlist_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPlaylistName.isNotBlank()) {
                        onCreatePlaylist(newPlaylistName.trim())
                        newPlaylistName = ""
                    }
                    showCreateDialog = false
                }) { Text(stringResource(R.string.library_create_playlist_confirm)) }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false; newPlaylistName = "" }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Rename Playlist Dialog
    // Rename Playlist Dialog
    if (showRenameDialog && renamingPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            title = { Text(stringResource(R.string.library_rename_playlist)) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text(stringResource(R.string.library_playlist_name_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (renameText.isNotBlank()) {
                        onRenamePlaylist(renamingPlaylist!!.id, renameText.trim())
                    }
                    showRenameDialog = false
                }) { Text(stringResource(R.string.library_rename)) }
            },
            dismissButton = {
                TextButton(onClick = { showRenameDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }

    // Delete Playlist Dialog
    if (showDeleteDialog && deletingPlaylist != null) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text(stringResource(R.string.library_delete_playlist)) },
            text = { Text(stringResource(R.string.library_delete_playlist_confirm, deletingPlaylist!!.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDeletePlaylist(deletingPlaylist!!.id)
                    showDeleteDialog = false
                }) { Text(stringResource(R.string.library_delete)) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text(stringResource(R.string.cancel)) }
            }
        )
    }
}

@Composable
private fun SongsTab(songs: List<Song>, state: LazyListState, playlists: List<Playlist>, onAddSongToPlaylist: (String, Long) -> Unit, onCreatePlaylist: (String) -> Unit) {
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

    LazyColumn(state = state, contentPadding = PaddingValues(top = 16.dp, bottom = 200.dp, start = 20.dp, end = 20.dp)) {
        item {
            // Sorting Controls
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.library_sort_by), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.secondary)
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
                            listOf(
                                stringResource(R.string.library_sort_name),
                                stringResource(R.string.library_sort_artist),
                                stringResource(R.string.library_sort_date)
                            ).forEach { option ->
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

        items(sortedSongs, key = { it.id }) { song ->
            UniqueSongRow(song, playlists = playlists, onAddToPlaylist = onAddSongToPlaylist, onCreatePlaylist = onCreatePlaylist) { AudioEngine.play(context, song) }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PlaylistsTab(
    playlists: List<Playlist>,
    allSongs: List<Song>,
    onCreateClick: () -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onRename: (Playlist) -> Unit,
    onDelete: (Playlist) -> Unit
) {
    val songMap = remember(allSongs) { allSongs.associateBy { it.id } }

    LazyColumn(
        state = rememberLazyListState(),
        contentPadding = PaddingValues(top = 16.dp, bottom = 200.dp, start = 20.dp, end = 20.dp)
    ) {
        // Create Playlist Card
        item {
            Surface(
                onClick = onCreateClick,
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                border = BorderStroke(
                    width = 1.5.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.5f),
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                        )
                    )
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.15f),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                Icons.Rounded.Add,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column {
                        Text(
                            stringResource(R.string.library_create_new_playlist),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            stringResource(R.string.library_create_playlist_hint), // Or "Start your collection" if different
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }

        // Playlist Cards
        items(playlists, key = { it.id }) { playlist ->
            val playlistSongs = remember(playlist, allSongs) {
                playlist.songIds.mapNotNull { songMap[it] }
            }
            val coverArts = remember(playlistSongs) {
                playlistSongs.map { it.albumArtUri }.distinct().take(4)
            }

            Surface(
                onClick = { onPlaylistClick(playlist) },
                shape = RoundedCornerShape(20.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                modifier = Modifier
                    .fillMaxWidth()
                    .combinedClickable(
                        onClick = { onPlaylistClick(playlist) },
                        onLongClick = { /* handled by context menu below via dropdown */ }
                    )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Album art mosaic
                    Surface(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.size(60.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        if (coverArts.isEmpty()) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.QueueMusic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                        } else if (coverArts.size < 4) {
                            AsyncImage(
                                model = coverArts.first(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            // 2x2 mosaic grid
                            Column {
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
                        }
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            playlist.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            stringResource(R.string.dashboard_songs_count, playlistSongs.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }

                    // Options menu
                    var showMenu by remember { mutableStateOf(false) }
                    Box {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(
                                Icons.Rounded.MoreVert,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Edit, null, modifier = Modifier.size(18.dp))
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(stringResource(R.string.library_rename))
                                    }
                                },
                                onClick = { showMenu = false; onRename(playlist) }
                            )
                            DropdownMenuItem(
                                text = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(Icons.Rounded.Delete, null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.error)
                                        Spacer(modifier = Modifier.width(10.dp))
                                        Text(stringResource(R.string.library_delete), color = MaterialTheme.colorScheme.error)
                                    }
                                },
                                onClick = { showMenu = false; onDelete(playlist) }
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
