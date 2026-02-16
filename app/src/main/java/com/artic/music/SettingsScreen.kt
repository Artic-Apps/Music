package com.artic.music

import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onNavigateRecap: () -> Unit, onNavigateHistory: () -> Unit, scrollState: ScrollState) {
    val context = LocalContext.current
    var showAbout by remember { mutableStateOf(false) }

    if (showAbout) {
        AlertDialog(
            onDismissRequest = { showAbout = false },
            title = { Text(stringResource(R.string.settings_about)) },
            text = { Text(stringResource(R.string.settings_about_dialog)) },
            confirmButton = { TextButton(onClick = { showAbout = false }) { Text(stringResource(R.string.dialog_close)) } }
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
                Row(modifier = Modifier.clickable { onNavigateHistory() }.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.History, null)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text("Listening History", style = MaterialTheme.typography.bodyLarge)
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
