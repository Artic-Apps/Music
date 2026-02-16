package com.artic.music

import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
