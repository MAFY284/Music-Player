package com.example.audioplayer

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

object LocalMusic {

    private const val CACHE_FILE = "music_cache.json"

    @Volatile
    private var cached: List<Song>? = null

    fun invalidateScanCache() {
        cached = null
    }

    fun albumArtUri(albumId: Long): Uri =
        ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"),
            albumId,
        )

    fun hasAudioPermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= 33) {
            context.checkSelfPermission(Manifest.permission.READ_MEDIA_AUDIO) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun scan(context: Context): List<Song> {
        val songs = mutableListOf<Song>()
        val collection = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.SIZE,
        )
        val selection =
            "(${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.IS_MUSIC} IS NULL)"
        val sort = "${MediaStore.Audio.Media.TITLE} COLLATE NOCASE ASC"

        val cursor = context.contentResolver.query(
            collection, projection, selection, null, sort,
        )
        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(0)
                val title = it.getString(1)?.takeIf { t -> t.isNotBlank() } ?: "Desconocido"
                val artist = it.getString(2) ?: ""
                val album = it.getString(3) ?: ""
                val duration = it.getLong(4)
                val albumId = it.getLong(5)
                val dateAdded = it.getLong(6)
                val size = it.getLong(7)
                val uri = ContentUris.withAppendedId(collection, id)
                songs.add(
                    Song(
                        id = "local_$id",
                        title = title,
                        artist = artist,
                        album = album,
                        durationMs = duration,
                        uri = uri.toString(),
                        albumId = albumId,
                        dateAddedSec = dateAdded,
                        sizeBytes = size,
                    ),
                )
            }
        }
        return songs
    }

    private val audioExtensions = setOf(
        "mp3", "wav", "flac", "m4a", "m4b", "opus", "ogg", "aac", "wma", "amr",
    )

    fun scanAll(context: Context): List<Song> {
        cached?.let { return it }
        loadCache(context)?.let { cached = it; return it }
        return rescan(context)
    }

    fun rescan(context: Context): List<Song> {
        val media = scan(context)
        val sources = scanSources(context, MusicStore.sourceFolders())
        val result = (media + sources).distinctBy { it.uri }
        cached = result
        saveCache(context, result)
        return result
    }

    fun peekCache(context: Context): List<Song>? =
        cached ?: loadCache(context)?.also { cached = it }

    private fun cacheFile(context: Context): File = File(context.filesDir, CACHE_FILE)

    private fun loadCache(context: Context): List<Song>? {
        val file = cacheFile(context)
        if (!file.exists()) return null
        return try {
            val arr = JSONArray(file.readText())
            val songs = mutableListOf<Song>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                songs.add(
                    Song(
                        id = o.getString("id"),
                        title = o.getString("title"),
                        artist = o.optString("artist"),
                        album = o.optString("album"),
                        durationMs = o.optLong("duration"),
                        uri = o.getString("uri"),
                        albumId = o.optLong("albumId"),
                        dateAddedSec = o.optLong("dateAdded"),
                        sizeBytes = o.optLong("size"),
                    ),
                )
            }
            songs.ifEmpty { null }
        } catch (_: Exception) {
            null
        }
    }

    private fun saveCache(context: Context, songs: List<Song>) {
        try {
            val arr = JSONArray()
            songs.forEach { s ->
                arr.put(
                    JSONObject()
                        .put("id", s.id)
                        .put("title", s.title)
                        .put("artist", s.artist)
                        .put("album", s.album)
                        .put("duration", s.durationMs)
                        .put("uri", s.uri)
                        .put("albumId", s.albumId)
                        .put("dateAdded", s.dateAddedSec)
                        .put("size", s.sizeBytes),
                )
            }
            cacheFile(context).writeText(arr.toString())
        } catch (_: Exception) {
        }
    }

    fun scanSources(context: Context, folders: List<SourceFolder>): List<Song> {
        val songs = mutableListOf<Song>()
        folders.forEach { folder ->
            val tree = DocumentFile.fromTreeUri(context, Uri.parse(folder.uri)) ?: return@forEach
            walk(context, tree, folder.name, songs)
        }
        return songs
    }

    private fun walk(context: Context, dir: DocumentFile, sourceName: String, out: MutableList<Song>) {
        val files = try {
            dir.listFiles()
        } catch (_: Exception) {
            return
        }
        files.forEach { f ->
            when {
                f.isDirectory -> walk(context, f, sourceName, out)
                isAudioFile(f) -> {
                    val name = f.name ?: return@forEach
                    val title = name.substringBeforeLast('.')
                    out.add(
                        Song(
                            id = f.uri.toString(),
                            title = title,
                            artist = sourceName,
                            album = "",
                            durationMs = 0L,
                            uri = f.uri.toString(),
                        ),
                    )
                }
            }
        }
    }

    private fun isAudioFile(f: DocumentFile): Boolean {
        val mime = f.type ?: ""
        if (mime.startsWith("audio/")) return true
        val name = f.name ?: return false
        val ext = name.substringAfterLast('.', "").lowercase()
        return ext in audioExtensions
    }
}
