package com.example.audioplayer

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.UUID

object MusicStore {

    private const val INDEX_FILE = "music_store.json"
    private const val MAX_RECENTS = 100

    private lateinit var appContext: Context
    private val playlists = mutableListOf<Playlist>()
    private val favorites = mutableListOf<String>()
    private val recents = mutableListOf<String>()
    private val sourceFolders = mutableListOf<SourceFolder>()
    private var downloadUri: String? = null
    private var downloadName: String = ""
    private val lyrics = mutableMapOf<String, String>()

    @Volatile
    private var initialized = false

    fun init(context: Context) {
        if (initialized) return
        appContext = context.applicationContext
        load()
        initialized = true
    }

    private fun coversDir(): File = File(appContext.filesDir, "covers").apply { mkdirs() }

    fun sourceFolders(): List<SourceFolder> = sourceFolders.toList()

    fun addSourceFolder(uri: String, name: String): Boolean {
        if (sourceFolders.any { it.uri == uri }) return false
        sourceFolders.add(SourceFolder(uri, name))
        save()
        return true
    }

    fun removeSourceFolder(uri: String) {
        sourceFolders.removeAll { it.uri == uri }
        save()
    }

    fun downloadUri(): String? = downloadUri

    fun downloadName(): String = downloadName

    fun downloadDisplayName(): String =
        if (downloadUri == null) appContext.getString(R.string.dl_location_default)
        else downloadName.ifBlank { appContext.getString(R.string.dl_location_default) }

    fun getLyrics(songUri: String): String = lyrics[songUri] ?: ""

    fun setLyrics(songUri: String, text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) lyrics.remove(songUri) else lyrics[songUri] = trimmed
        save()
    }

    fun setDownloadLocation(uri: String?, name: String) {
        downloadUri = uri
        downloadName = name
        if (uri != null) addSourceFolder(uri, name)
        save()
    }

    fun playlists(): List<Playlist> = playlists.toList()

    fun playlist(id: String): Playlist? = playlists.firstOrNull { it.id == id }

    fun coverFile(playlistId: String): File? =
        playlists.firstOrNull { it.id == playlistId }
            ?.coverPath?.let { File(it) }?.takeIf { it.exists() }

    fun createPlaylist(name: String): Playlist {
        val p = Playlist(UUID.randomUUID().toString(), name, null, mutableListOf())
        playlists.add(p)
        save()
        return p
    }

    fun renamePlaylist(id: String, name: String) {
        playlists.firstOrNull { it.id == id }?.name = name
        save()
    }

    fun setPlaylistCover(id: String, source: File): String? {
        val p = playlists.firstOrNull { it.id == id } ?: return null
        return try {
            val dest = File(coversDir(), p.id + ".jpg")
            source.copyTo(dest, overwrite = true)
            p.coverPath = dest.absolutePath
            save()
            dest.absolutePath
        } catch (e: Exception) {
            null
        }
    }

    fun deletePlaylist(id: String) {
        File(coversDir(), id + ".jpg").delete()
        playlists.removeAll { it.id == id }
        save()
    }

    fun addToPlaylist(playlistId: String, songUri: String): Boolean {
        val p = playlists.firstOrNull { it.id == playlistId } ?: return false
        if (p.songUris.contains(songUri)) return false
        p.songUris.add(songUri)
        save()
        return true
    }

    fun removeFromPlaylist(playlistId: String, songUri: String) {
        playlists.firstOrNull { it.id == playlistId }?.songUris?.remove(songUri)
        save()
    }

    fun isFavorite(songUri: String): Boolean = favorites.contains(songUri)

    fun toggleFavorite(songUri: String): Boolean {
        val nowFav = if (favorites.contains(songUri)) {
            favorites.remove(songUri)
            false
        } else {
            favorites.add(songUri)
            true
        }
        save()
        return nowFav
    }

    fun favoriteUris(): List<String> = favorites.toList()

    fun recordRecent(songUri: String) {
        if (!initialized) return
        recents.remove(songUri)
        recents.add(0, songUri)
        while (recents.size > MAX_RECENTS) recents.removeAt(recents.size - 1)
        save()
    }

    fun recentUris(): List<String> = recents.toList()

    private fun load() {
        playlists.clear()
        favorites.clear()
        recents.clear()
        val file = File(appContext.filesDir, INDEX_FILE)
        if (!file.exists()) return
        try {
            val obj = JSONObject(file.readText())

            val plArr = obj.optJSONArray("playlists") ?: JSONArray()
            for (i in 0 until plArr.length()) {
                val o = plArr.getJSONObject(i)
                val uris = mutableListOf<String>()
                val uArr = o.optJSONArray("songs") ?: JSONArray()
                for (j in 0 until uArr.length()) uris.add(uArr.getString(j))
                playlists.add(
                    Playlist(
                        id = o.getString("id"),
                        name = o.getString("name"),
                        coverPath = o.optString("coverPath").ifBlank { null },
                        songUris = uris,
                    ),
                )
            }

            val favArr = obj.optJSONArray("favorites") ?: JSONArray()
            for (i in 0 until favArr.length()) favorites.add(favArr.getString(i))

            val recArr = obj.optJSONArray("recents") ?: JSONArray()
            for (i in 0 until recArr.length()) recents.add(recArr.getString(i))

            val srcArr = obj.optJSONArray("sources") ?: JSONArray()
            for (i in 0 until srcArr.length()) {
                val o = srcArr.getJSONObject(i)
                sourceFolders.add(SourceFolder(o.getString("uri"), o.optString("name")))
            }
            downloadUri = obj.optString("downloadUri").ifBlank { null }
            downloadName = obj.optString("downloadName")

            val lyrObj = obj.optJSONObject("lyrics")
            if (lyrObj != null) {
                val keys = lyrObj.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    lyrics[k] = lyrObj.optString(k)
                }
            }
        } catch (_: Exception) {
        }
    }

    private fun save() {
        if (!initialized) return
        try {
            val root = JSONObject()
            val plArr = JSONArray()
            playlists.forEach {
                val uArr = JSONArray()
                it.songUris.forEach { uri -> uArr.put(uri) }
                plArr.put(
                    JSONObject()
                        .put("id", it.id)
                        .put("name", it.name)
                        .put("coverPath", it.coverPath ?: "")
                        .put("songs", uArr),
                )
            }
            val favArr = JSONArray()
            favorites.forEach { favArr.put(it) }
            val recArr = JSONArray()
            recents.forEach { recArr.put(it) }
            val srcArr = JSONArray()
            sourceFolders.forEach {
                srcArr.put(JSONObject().put("uri", it.uri).put("name", it.name))
            }
            val lyrObj = JSONObject()
            lyrics.forEach { (k, v) -> lyrObj.put(k, v) }

            root.put("playlists", plArr)
            root.put("favorites", favArr)
            root.put("recents", recArr)
            root.put("sources", srcArr)
            root.put("downloadUri", downloadUri ?: "")
            root.put("downloadName", downloadName)
            root.put("lyrics", lyrObj)
            File(appContext.filesDir, INDEX_FILE).writeText(root.toString())
        } catch (_: Exception) {
        }
    }
}
