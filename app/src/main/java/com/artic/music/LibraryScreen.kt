package com.artic.music

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
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
        
        items(sortedSongs, key = { it.id }) { song ->
            UniqueSongRow(song) { AudioEngine.play(context, song) }
            Spacer(modifier = Modifier.height(12.dp))
        }
    }
}
