package com.example.audioplayer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.EditText
import android.widget.ImageView
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.documentfile.provider.DocumentFile
import com.example.audioplayer.databinding.ActivityMainBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    private enum class Section { SONGS, FAVORITES, PLAYLISTS, RECENTS }

    private enum class SortField { NAME, DATE, SIZE, DURATION }

    private lateinit var binding: ActivityMainBinding
    private val mainHandler = Handler(Looper.getMainLooper())

    private val songAdapter: SongAdapter
    private val playlistAdapter: PlaylistAdapter

    private var allSongs: List<Song> = emptyList()
    private var songUriMap: Map<String, Song> = emptyMap()
    private var currentList: List<Song> = emptyList()
    private var section = Section.SONGS
    private var query = ""
    private var pendingCoverPlaylistId: String? = null
    private var sortField = SortField.DATE
    private var sortAscending = false

    @Volatile
    private var ready = false

    private val permLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            scan()
        }

    private val pickCoverLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) applyCoverFromUri(uri)
        }

    private val pickSourceFolderLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                persistTree(uri)
                val name = treeName(uri)
                MusicStore.addSourceFolder(uri.toString(), name)
                scan()
                toast(getString(R.string.folder_added, name))
            }
        }

    private val pickDownloadDirLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null) {
                persistTree(uri)
                val name = treeName(uri)
                MusicStore.setDownloadLocation(uri.toString(), name)
                downloadDialog?.refreshLocation()
                toast(getString(R.string.folder_added, name))
            }
        }

    private var downloadDialog: DownloadDialog? = null

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) = updateMiniPlayIcon()
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateMiniPlayer()
        override fun onPlaybackStateChanged(playbackState: Int) = updateMiniPlayer()
    }

    init {
        songAdapter = SongAdapter(
            this,
            onPlay = { song, index -> playFrom(currentList, index) },
            onFavorite = { song -> toggleFavorite(song) },
            onLongClick = { song -> showSongMenu(song) },
            onAddToPlaylist = { song -> PlaylistDialogs.showAddToPlaylist(this, song.uri) },
        )
        playlistAdapter = PlaylistAdapter(
            this,
            onClick = { p -> openPlaylist(p.id) },
            onEdit = { p -> showPlaylistMenu(p) },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        MusicStore.init(this)
        PlayerManager.init(this)

        binding.recyclerSongs.layoutManager = LinearLayoutManager(this)
        binding.recyclerSongs.adapter = songAdapter
        binding.recyclerPlaylists.layoutManager = LinearLayoutManager(this)
        binding.recyclerPlaylists.adapter = playlistAdapter

        binding.btnDownload.setOnClickListener {
            downloadDialog = DownloadDialog(
                this,
                onDownloaded = { scan() },
                onPickLocation = { pickDownloadDirLauncher.launch(null) },
            )
            downloadDialog?.show()
        }

        binding.navFavorites.setOnClickListener { setSection(Section.FAVORITES) }
        binding.navPlaylists.setOnClickListener { setSection(Section.PLAYLISTS) }
        binding.navRecents.setOnClickListener { setSection(Section.RECENTS) }
        binding.btnBack.setOnClickListener { setSection(Section.SONGS) }
        binding.btnAddFolder.setOnClickListener { pickSourceFolderLauncher.launch(null) }
        binding.btnSort.setOnClickListener { showSortMenu(it) }

        binding.etSearch.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                query = s?.toString()?.trim() ?: ""
                applyFilter()
            }

            override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
            override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) {}
        })

        binding.tvEmpty.setOnClickListener { requestAudioPermission() }

        binding.miniPlay.setOnClickListener { PlayerManager.togglePlayPause() }
        binding.miniNext.setOnClickListener { PlayerManager.next() }
        binding.miniPrev.setOnClickListener { PlayerManager.previous() }
        binding.miniPlayer.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java))
            overridePendingTransition(R.anim.slide_up_in, 0)
        }

        requestNotificationPermission()
        requestAudioPermission()

        thread {
            try {
                Downloader.init(applicationContext)
                ready = true
            } catch (e: Exception) {
                mainHandler.post { toast("Error al inicializar: ${e.message ?: ""}") }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        PlayerManager.addListener(playerListener)
        updateMiniPlayer()
    }

    override fun onStop() {
        super.onStop()
        PlayerManager.removeListener(playerListener)
    }

    override fun onResume() {
        super.onResume()
        scan()
    }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 100)
        }
    }

    private fun requestAudioPermission() {
        if (LocalMusic.hasAudioPermission(this)) {
            scan()
            return
        }
        val perms = if (Build.VERSION.SDK_INT >= 33) {
            arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
        } else {
            arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        permLauncher.launch(perms)
    }

    private fun scan() {
        if (!LocalMusic.hasAudioPermission(this)) {
            showPermissionState()
            return
        }
        thread {
            val media = LocalMusic.scan(this)
            val sources = LocalMusic.scanSources(this, MusicStore.sourceFolders())
            val songs = (media + sources).distinctBy { it.uri }
            mainHandler.post {
                allSongs = songs
                songUriMap = songs.associateBy { it.uri }
                applyFilter()
            }
        }
    }

    private fun showPermissionState() {
        binding.recyclerSongs.visibility = View.GONE
        binding.recyclerPlaylists.visibility = View.GONE
        binding.tvEmpty.visibility = View.VISIBLE
        binding.tvEmpty.text = getString(R.string.perm_audio_desc)
        binding.tvCount.text = ""
    }

    private fun setSection(newSection: Section) {
        section = newSection
        updateNavState()
        binding.btnBack.visibility = if (newSection == Section.SONGS) View.GONE else View.VISIBLE
        binding.btnAddFolder.visibility =
            if (newSection == Section.SONGS) View.VISIBLE else View.GONE
        applyFilter()
    }

    private fun persistTree(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: Exception) {
        }
    }

    private fun treeName(uri: Uri): String =
        try {
            DocumentFile.fromTreeUri(this, uri)?.name
                ?: getString(R.string.dl_location_default)
        } catch (_: Exception) {
            getString(R.string.dl_location_default)
        }

    private fun updateNavState() {
        val fav = section == Section.FAVORITES
        val play = section == Section.PLAYLISTS
        val rec = section == Section.RECENTS
        setNavItem(binding.ivNavFav, binding.tvNavFav, fav)
        setNavItem(binding.ivNavPlay, binding.tvNavPlay, play)
        setNavItem(binding.ivNavRec, binding.tvNavRec, rec)
    }

    private fun setNavItem(icon: ImageView, label: TextView, selected: Boolean) {
        val color = ContextCompat.getColor(this, if (selected) R.color.orange else R.color.chrome_dark)
        icon.imageTintList = android.content.res.ColorStateList.valueOf(color)
        label.setTextColor(color)
    }

    private fun applyFilter() {
        binding.tvSectionTitle.text = when (section) {
            Section.SONGS -> getString(R.string.section_songs)
            Section.FAVORITES -> getString(R.string.section_favorites)
            Section.PLAYLISTS -> getString(R.string.section_playlists)
            Section.RECENTS -> getString(R.string.section_recents)
        }

        when (section) {
            Section.PLAYLISTS -> {
                binding.recyclerSongs.visibility = View.GONE
                binding.recyclerPlaylists.visibility = View.VISIBLE
                val pls = MusicStore.playlists().filter {
                    query.isBlank() || it.name.contains(query, ignoreCase = true)
                }
                playlistAdapter.submit(pls)
                val empty = pls.isEmpty()
                binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                if (empty) binding.tvEmpty.text = emptyText()
                binding.tvCount.text = if (pls.isNotEmpty()) countText(pls.size) else ""
            }
            else -> {
                binding.recyclerSongs.visibility = View.VISIBLE
                binding.recyclerPlaylists.visibility = View.GONE
                currentList = when (section) {
                    Section.FAVORITES -> sortSongs(filterSongs(resolve(MusicStore.favoriteUris())))
                    Section.RECENTS -> filterSongs(resolve(MusicStore.recentUris()))
                    else -> sortSongs(filterSongs(allSongs))
                }
                songAdapter.submit(currentList)
                val empty = currentList.isEmpty()
                binding.tvEmpty.visibility = if (empty) View.VISIBLE else View.GONE
                if (empty) binding.tvEmpty.text = emptyText()
                binding.tvCount.text = if (currentList.isNotEmpty()) countText(currentList.size) else ""
            }
        }
    }

    private fun emptyText(): String {
        if (query.isNotBlank()) return getString(R.string.empty_search)
        return when (section) {
            Section.SONGS -> getString(R.string.empty_songs)
            Section.FAVORITES -> getString(R.string.empty_favorites)
            Section.PLAYLISTS -> getString(R.string.empty_playlists)
            Section.RECENTS -> getString(R.string.empty_recents)
        }
    }

    private fun countText(n: Int): String =
        if (n == 1) getString(R.string.one_song) else getString(R.string.songs_count, n)

    private fun filterSongs(songs: List<Song>): List<Song> =
        if (query.isBlank()) songs
        else songs.filter {
            it.title.contains(query, ignoreCase = true) ||
                it.artist.contains(query, ignoreCase = true) ||
                it.album.contains(query, ignoreCase = true)
        }

    private fun sortSongs(songs: List<Song>): List<Song> {
        val sorted = when (sortField) {
            SortField.NAME -> songs.sortedBy { it.title.lowercase() }
            SortField.DATE -> songs.sortedBy { it.dateAddedSec }
            SortField.SIZE -> songs.sortedBy { it.sizeBytes }
            SortField.DURATION -> songs.sortedBy { it.durationMs }
        }
        return if (sortAscending) sorted else sorted.reversed()
    }

    private fun showSortMenu(anchor: View) {
        val menu = PopupMenu(this, anchor)
        menu.menu.add(0, 0, 0, getString(R.string.sort_name_asc))
        menu.menu.add(0, 1, 1, getString(R.string.sort_name_desc))
        menu.menu.add(0, 2, 2, getString(R.string.sort_date_desc))
        menu.menu.add(0, 3, 3, getString(R.string.sort_date_asc))
        menu.menu.add(0, 4, 4, getString(R.string.sort_size_desc))
        menu.menu.add(0, 5, 5, getString(R.string.sort_size_asc))
        menu.menu.add(0, 6, 6, getString(R.string.sort_duration_desc))
        menu.menu.add(0, 7, 7, getString(R.string.sort_duration_asc))
        menu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                0 -> { sortField = SortField.NAME; sortAscending = true }
                1 -> { sortField = SortField.NAME; sortAscending = false }
                2 -> { sortField = SortField.DATE; sortAscending = false }
                3 -> { sortField = SortField.DATE; sortAscending = true }
                4 -> { sortField = SortField.SIZE; sortAscending = false }
                5 -> { sortField = SortField.SIZE; sortAscending = true }
                6 -> { sortField = SortField.DURATION; sortAscending = false }
                7 -> { sortField = SortField.DURATION; sortAscending = true }
            }
            applyFilter()
            true
        }
        menu.show()
    }

    private fun resolve(uris: List<String>): List<Song> = uris.mapNotNull { songUriMap[it] }

    private fun playFrom(songs: List<Song>, index: Int) {
        if (songs.isEmpty()) return
        PlayerManager.playQueue(songs, index)
    }

    private fun toggleFavorite(song: Song) {
        MusicStore.toggleFavorite(song.uri)
        songAdapter.notifyDataSetChanged()
        if (section == Section.FAVORITES) applyFilter()
    }

    private fun showSongMenu(song: Song) {
        val items = arrayOf(
            if (MusicStore.isFavorite(song.uri)) getString(R.string.remove_from_favorites)
            else getString(R.string.add_to_favorites),
            getString(R.string.add_to_playlist),
        )
        MaterialAlertDialogBuilder(this, R.style.App_Dialog)
            .setTitle(song.title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> toggleFavorite(song)
                    1 -> PlaylistDialogs.showAddToPlaylist(this, song.uri)
                }
            }
            .show()
    }

    private fun openPlaylist(id: String) {
        startActivity(Intent(this, PlaylistActivity::class.java).putExtra(PlaylistActivity.EXTRA_ID, id))
    }

    private fun showPlaylistMenu(p: Playlist) {
        val items = arrayOf(
            getString(R.string.playlist_rename),
            getString(R.string.playlist_cover),
            getString(R.string.playlist_delete),
        )
        MaterialAlertDialogBuilder(this, R.style.App_Dialog)
            .setTitle(p.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> renamePlaylistDialog(p)
                    1 -> pickCover(p)
                    2 -> confirmDeletePlaylist(p)
                }
            }
            .show()
    }

    private fun renamePlaylistDialog(p: Playlist) {
        val input = EditText(this).apply {
            setText(p.name)
            setSelection(text.length)
            setTextColor(getColor(R.color.text_primary))
            setHintTextColor(getColor(R.color.text_secondary))
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        MaterialAlertDialogBuilder(this, R.style.App_Dialog)
            .setTitle(R.string.playlist_rename)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = input.text.toString().trim().ifEmpty { p.name }
                MusicStore.renamePlaylist(p.id, name)
                if (section == Section.PLAYLISTS) applyFilter()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun pickCover(p: Playlist) {
        pendingCoverPlaylistId = p.id
        pickCoverLauncher.launch("image/*")
    }

    private fun applyCoverFromUri(uri: Uri) {
        val pid = pendingCoverPlaylistId ?: return
        pendingCoverPlaylistId = null
        thread {
            try {
                val temp = File(cacheDir, "cover_${System.currentTimeMillis()}.jpg")
                contentResolver.openInputStream(uri)?.use { input ->
                    temp.outputStream().use { input.copyTo(it) }
                }
                MusicStore.setPlaylistCover(pid, temp)
                temp.delete()
                mainHandler.post {
                    if (section == Section.PLAYLISTS) applyFilter()
                    toast(getString(R.string.cover_set))
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun confirmDeletePlaylist(p: Playlist) {
        MaterialAlertDialogBuilder(this, R.style.App_Dialog)
            .setTitle(R.string.playlist_delete)
            .setMessage(R.string.playlist_delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                MusicStore.deletePlaylist(p.id)
                if (section == Section.PLAYLISTS) applyFilter()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun updateMiniPlayer() {
        val has = PlayerManager.hasMedia()
        binding.miniPlayer.visibility = if (has) View.VISIBLE else View.GONE
        if (!has) return
        binding.miniTitle.text = PlayerManager.currentTitle() ?: getString(R.string.unknown_artist)
        binding.miniArtist.text = PlayerManager.currentArtist() ?: ""
        val song = PlayerManager.currentSongUri()?.let { songUriMap[it] }
        if (song != null) {
            Artwork.loadAlbumArt(this, binding.miniCover, song.albumId)
        } else {
            binding.miniCover.setImageDrawable(null)
        }
        updateMiniPlayIcon()
    }

    private fun updateMiniPlayIcon() {
        val playing = PlayerManager.isPlaying()
        binding.miniPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
