package com.example.audioplayer

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationMs: Long,
    val uri: String,
    val albumId: Long = -1L,
    val dateAddedSec: Long = 0L,
) {
    fun toUri(): Uri = Uri.parse(uri)

    fun toMediaItem(): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .apply { if (artist.isNotBlank()) setArtist(artist) }
            .apply { if (album.isNotBlank()) setAlbumTitle(album) }
            .apply { if (albumId > 0) setArtworkUri(LocalMusic.albumArtUri(albumId)) }
            .build()
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(toUri())
            .setMediaMetadata(metadata)
            .build()
    }
}

data class Playlist(
    val id: String,
    var name: String,
    var coverPath: String?,
    val songUris: MutableList<String>,
)

data class SourceFolder(
    val uri: String,
    val name: String,
)

data class AudioInfo(
    val title: String,
    val uploader: String,
    val thumbnail: String,
    val durationSeconds: Int,
    val audioFormats: List<String>,
)
