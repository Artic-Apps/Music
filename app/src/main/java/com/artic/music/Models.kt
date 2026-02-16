package com.artic.music

import android.content.ContentUris
import android.net.Uri

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
