package com.example.audioplayer

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.audioplayer.databinding.ActivityPlaylistBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import java.io.File
import kotlin.concurrent.thread

class PlaylistActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlaylistBinding
    private val mainHandler = Handler(Looper.getMainLooper())

    private var playlist: Playlist? = null
    private var songs: List<Song> = emptyList()
    private var songUriMap: Map<String, Song> = emptyMap()
    private var pendingCoverPlaylistId: String? = null

    private lateinit var adapter: SongAdapter
    private lateinit var touchHelper: ItemTouchHelper

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = refreshCurrent()
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            adapter.setPlaying(isPlaying)
            updateMini()
        }

        override fun onPlaybackStateChanged(playbackState: Int) = refreshCurrent()
    }

    private val ticker = object : Runnable {
        override fun run() {
            updateMiniProgress()
            mainHandler.postDelayed(this, 500)
        }
    }

    private val pickCoverLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) applyCoverFromUri(uri)
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlaylistBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val id = intent.getStringExtra(EXTRA_ID)
        if (id == null) {
            finish()
            return
        }
        playlist = MusicStore.playlist(id)
        if (playlist == null) {
            finish()
            return
        }

        adapter = SongAdapter(
            this,
            onOpenPlayer = { song, index -> openPlayer(song, index) },
            onTogglePlay = { song, index -> togglePlay(song, index) },
            onFavorite = { song -> toggleFavorite(song) },
            onLongClick = { song -> showSongMenu(song) },
            onAddToPlaylist = { song -> PlaylistDialogs.showAddToPlaylist(this, song.uri) },
            onStartDrag = { vh -> touchHelper.startDrag(vh) },
        )
        adapter.dragEnabled = true
        binding.recyclerSongs.layoutManager = LinearLayoutManager(this)
        binding.recyclerSongs.adapter = adapter

        touchHelper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0,
        ) {
            override fun onMove(
                rv: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder,
            ): Boolean {
                val from = viewHolder.bindingAdapterPosition
                val to = target.bindingAdapterPosition
                if (from < 0 || to < 0 || from == to) return false
                adapter.move(from, to)
                playlist?.let { MusicStore.reorderPlaylistSongs(it.id, from, to) }
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

            override fun isLongPressDragEnabled(): Boolean = false
        })
        touchHelper.attachToRecyclerView(binding.recyclerSongs)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnEdit.setOnClickListener { playlist?.let { showMenu(it) } }
        binding.btnPlayAll.setOnClickListener { playFrom(0) }

        binding.miniPlay.setOnClickListener { PlayerManager.togglePlayPause() }
        binding.miniNext.setOnClickListener { PlayerManager.next() }
        binding.miniPrev.setOnClickListener { PlayerManager.previous() }
        binding.miniPlayer.setOnClickListener {
            startActivity(Intent(this, PlayerActivity::class.java))
            overridePendingTransition(R.anim.slide_up_in, 0)
        }

        render()
    }

    override fun onStart() {
        super.onStart()
        PlayerManager.addListener(playerListener)
        refreshCurrent()
        mainHandler.postDelayed(ticker, 500)
    }

    override fun onStop() {
        super.onStop()
        PlayerManager.removeListener(playerListener)
        mainHandler.removeCallbacks(ticker)
    }

    override fun onResume() {
        super.onResume()
        render()
    }

    private fun render() {
        val p = playlist ?: return
        binding.tvTitle.text = p.name
        binding.tvName.text = p.name
        MusicStore.coverFile(p.id)?.let { Artwork.loadCover(this, binding.ivCover, it) }

        thread {
            if (!LocalMusic.hasAudioPermission(this)) {
                mainHandler.post { finishLoading(emptyList()) }
                return@thread
            }
            val all = LocalMusic.scanAll(this)
            val map = all.associateBy { it.uri }
            val resolved = p.songUris.mapNotNull { map[it] }
            mainHandler.post {
                songUriMap = map
                finishLoading(resolved)
            }
        }
    }

    private fun finishLoading(resolved: List<Song>) {
        val p = playlist ?: return
        songs = resolved
        adapter.submit(songs)
        adapter.setCurrentUri(PlayerManager.currentSongUri())
        val n = songs.size
        binding.tvCount.text =
            if (n == 1) getString(R.string.one_song) else getString(R.string.songs_count, n)
        binding.tvEmpty.visibility = if (songs.isEmpty()) View.VISIBLE else View.GONE
        binding.btnPlayAll.isEnabled = songs.isNotEmpty()
        binding.tvName.text = p.name
        MusicStore.coverFile(p.id)?.let { Artwork.loadCover(this, binding.ivCover, it) }
        updateMini()
    }

    private fun playFrom(index: Int) {
        if (songs.isEmpty()) return
        PlayerManager.playQueue(songs, index)
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun openPlayer(song: Song, index: Int) {
        if (PlayerManager.currentSongUri() != song.uri) {
            PlayerManager.playQueue(songs, index)
        }
        startActivity(Intent(this, PlayerActivity::class.java))
    }

    private fun togglePlay(song: Song, index: Int) {
        if (PlayerManager.currentSongUri() == song.uri) {
            PlayerManager.togglePlayPause()
        } else {
            PlayerManager.playQueue(songs, index)
        }
    }

    private fun refreshCurrent() {
        adapter.setCurrentUri(PlayerManager.currentSongUri())
        updateMini()
    }

    private fun updateMini() {
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
        val playing = PlayerManager.isPlaying()
        binding.miniPlay.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        updateMiniProgress()
    }

    private fun updateMiniProgress() {
        val c = PlayerManager.controller() ?: return
        val dur = c.duration
        val pos = c.currentPosition
        binding.pbMini.progress = if (dur > 0) (pos * 1000 / dur).toInt() else 0
    }

    private fun toggleFavorite(song: Song) {
        MusicStore.toggleFavorite(song.uri)
        adapter.notifyDataSetChanged()
    }

    private fun showSongMenu(song: Song) {
        val items = arrayOf(
            if (MusicStore.isFavorite(song.uri)) getString(R.string.remove_from_favorites)
            else getString(R.string.add_to_favorites),
            getString(R.string.remove_from_playlist),
        )
        MaterialAlertDialogBuilder(this, R.style.App_Dialog)
            .setTitle(song.title)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> toggleFavorite(song)
                    1 -> removeSong(song)
                }
            }
            .show()
    }

    private fun removeSong(song: Song) {
        val p = playlist ?: return
        MusicStore.removeFromPlaylist(p.id, song.uri)
        render()
    }

    private fun showMenu(p: Playlist) {
        val items = arrayOf(
            getString(R.string.playlist_rename),
            getString(R.string.playlist_cover),
            getString(R.string.playlist_delete),
        )
        MaterialAlertDialogBuilder(this, R.style.App_Dialog)
            .setTitle(p.name)
            .setItems(items) { _, which ->
                when (which) {
                    0 -> renameDialog(p)
                    1 -> pickCover(p)
                    2 -> deleteDialog(p)
                }
            }
            .show()
    }

    private fun renameDialog(p: Playlist) {
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
                binding.tvTitle.text = name
                binding.tvName.text = name
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
                    MusicStore.coverFile(pid)?.let { Artwork.loadCover(this, binding.ivCover, it) }
                    toast(getString(R.string.cover_set))
                }
            } catch (_: Exception) {
            }
        }
    }

    private fun deleteDialog(p: Playlist) {
        MaterialAlertDialogBuilder(this, R.style.App_Dialog)
            .setTitle(R.string.playlist_delete)
            .setMessage(R.string.playlist_delete_confirm)
            .setPositiveButton(R.string.delete) { _, _ ->
                MusicStore.deletePlaylist(p.id)
                finish()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    companion object {
        const val EXTRA_ID = "extra_playlist_id"
    }
}
