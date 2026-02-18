package com.artic.music

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.*

class DataManager(context: Context) {
    private val prefs = context.getSharedPreferences("artic_db", Context.MODE_PRIVATE)

    fun recordPlay(song: Song) {
        // Track artist plays
        val artistCurrent = prefs.getInt("artist_${song.artist}", 0)
        prefs.edit().putInt("artist_${song.artist}", artistCurrent + 1).apply()
        
        // Track song plays
        val songCurrent = prefs.getInt("song_${song.id}", 0)
        prefs.edit().putInt("song_${song.id}", songCurrent + 1).apply()
        
        // Track recently played (store last 1000 song IDs)
        val recentJson = prefs.getString("recent_plays", "[]") ?: "[]"
        val recentArray = JSONArray(recentJson)
        
        val newArray = JSONArray()
        newArray.put(song.id)
        for (i in 0 until minOf(recentArray.length(), 999)) {
            newArray.put(recentArray.getLong(i))
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

    fun getHistory(allSongs: List<Song>): List<Song> {
        val recentJson = prefs.getString("recent_plays", "[]") ?: "[]"
        val recentArray = JSONArray(recentJson)
        val history = mutableListOf<Song>()
        val songMap = allSongs.associateBy { it.id }
        
        for (i in 0 until recentArray.length()) {
            val id = recentArray.getLong(i)
            songMap[id]?.let { history.add(it) }
        }
        return history
    }

    fun getPlaylists(): List<Playlist> {
        val list = mutableListOf<Playlist>()
        val jsonArray = JSONArray(prefs.getString("playlists", "[]"))
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            val songsArray = obj.optJSONArray("songs") ?: JSONArray()
            val songIds = mutableListOf<Long>()
            for (j in 0 until songsArray.length()) {
                songIds.add(songsArray.getLong(j))
            }
            list.add(Playlist(obj.getString("id"), obj.getString("name"), songIds))
        }
        return list
    }

    fun addSongToPlaylist(playlistId: String, songId: Long) {
        val jsonArray = JSONArray(prefs.getString("playlists", "[]"))
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.getString("id") == playlistId) {
                val songs = obj.optJSONArray("songs") ?: JSONArray()
                // Skip duplicates
                var exists = false
                for (j in 0 until songs.length()) {
                    if (songs.getLong(j) == songId) { exists = true; break }
                }
                if (!exists) {
                    songs.put(songId)
                    obj.put("songs", songs)
                }
                break
            }
        }
        prefs.edit().putString("playlists", jsonArray.toString()).apply()
    }

    fun removeSongFromPlaylist(playlistId: String, songId: Long) {
        val jsonArray = JSONArray(prefs.getString("playlists", "[]"))
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.getString("id") == playlistId) {
                val songs = obj.optJSONArray("songs") ?: JSONArray()
                val newSongs = JSONArray()
                for (j in 0 until songs.length()) {
                    if (songs.getLong(j) != songId) newSongs.put(songs.getLong(j))
                }
                obj.put("songs", newSongs)
                break
            }
        }
        prefs.edit().putString("playlists", jsonArray.toString()).apply()
    }

    fun deletePlaylist(playlistId: String) {
        val jsonArray = JSONArray(prefs.getString("playlists", "[]"))
        val newArray = JSONArray()
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.getString("id") != playlistId) newArray.put(obj)
        }
        prefs.edit().putString("playlists", newArray.toString()).apply()
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        val jsonArray = JSONArray(prefs.getString("playlists", "[]"))
        for (i in 0 until jsonArray.length()) {
            val obj = jsonArray.getJSONObject(i)
            if (obj.getString("id") == playlistId) {
                obj.put("name", newName)
                break
            }
        }
        prefs.edit().putString("playlists", jsonArray.toString()).apply()
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
    
    // Get total plays across all songs
    fun getTotalPlays(): Int {
        val all = prefs.all
        return all.keys
            .filter { it.startsWith("song_") }
            .sumOf { key -> all[key] as? Int ?: 0 }
    }
    
    // Track and get best streak ever
    fun updateBestStreak() {
        val currentStreak = getCurrentStreak()
        val bestStreak = prefs.getInt("best_streak", 0)
        if (currentStreak > bestStreak) {
            prefs.edit().putInt("best_streak", currentStreak).apply()
        }
    }
    
    fun getBestStreak(): Int = prefs.getInt("best_streak", 0)
}
