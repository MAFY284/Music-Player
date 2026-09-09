package com.example.audioplayer

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.FileInputStream

object MediaSaver {

    private const val SUBDIR = "Reproductor"

    fun mimeFor(format: String): String = when (format.lowercase()) {
        "mp3" -> "audio/mpeg"
        "wav" -> "audio/wav"
        "flac" -> "audio/flac"
        "m4a", "m4b" -> "audio/mp4"
        "opus", "ogg" -> "audio/ogg"
        "aac" -> "audio/aac"
        else -> "application/octet-stream"
    }

    fun sanitize(name: String): String =
        name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
            .replace(Regex("\\s+"), " ")
            .trim()
            .trimEnd('.')
            .ifEmpty { "audio" }

    fun save(
        context: Context,
        source: File,
        title: String,
        artist: String,
        format: String,
        durationMs: Long,
    ): Song? {
        val downloadUri = MusicStore.downloadUri()
        return if (downloadUri != null) {
            saveToTree(context, downloadUri, source, title, artist, format, durationMs)
        } else {
            saveToMediaStore(context, source, title, artist, format, durationMs)
        }
    }

    private fun saveToMediaStore(
        context: Context,
        source: File,
        title: String,
        artist: String,
        format: String,
        durationMs: Long,
    ): Song? {
        val displayName = sanitize(title) + "." + format.lowercase()
        val relativePath = Environment.DIRECTORY_MUSIC + "/" + SUBDIR
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Audio.Media.MIME_TYPE, mimeFor(format))
            put(MediaStore.Audio.Media.RELATIVE_PATH, relativePath)
            put(MediaStore.Audio.Media.TITLE, title)
            put(MediaStore.Audio.Media.ARTIST, artist)
            put(MediaStore.Audio.Media.DURATION, durationMs)
        }
        val collection =
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = context.contentResolver.insert(collection, values) ?: return null
        return try {
            context.contentResolver.openOutputStream(uri)?.use { os ->
                FileInputStream(source).use { it.copyTo(os) }
            }
            Song(
                id = uri.toString(),
                title = title,
                artist = artist,
                album = "",
                durationMs = durationMs,
                uri = uri.toString(),
            )
        } catch (e: Exception) {
            context.contentResolver.delete(uri, null, null)
            null
        }
    }

    private fun saveToTree(
        context: Context,
        treeUri: String,
        source: File,
        title: String,
        artist: String,
        format: String,
        durationMs: Long,
    ): Song? {
        val tree = DocumentFile.fromTreeUri(context, Uri.parse(treeUri)) ?: return null
        val displayName = sanitize(title) + "." + format.lowercase()
        var dest = tree.findFile(displayName)
        if (dest != null && dest.isFile) dest.delete()
        dest = tree.createFile(mimeFor(format), displayName) ?: return null
        return try {
            context.contentResolver.openOutputStream(dest.uri)?.use { os ->
                FileInputStream(source).use { it.copyTo(os) }
            }
            Song(
                id = dest.uri.toString(),
                title = title,
                artist = artist,
                album = "",
                durationMs = durationMs,
                uri = dest.uri.toString(),
            )
        } catch (e: Exception) {
            null
        }
    }
}
