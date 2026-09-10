package com.example.audioplayer

import android.content.res.ColorStateList
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SongAdapter(
    private val context: android.content.Context,
    private val onOpenPlayer: (Song, Int) -> Unit,
    private val onTogglePlay: (Song, Int) -> Unit,
    private val onFavorite: (Song) -> Unit,
    private val onLongClick: (Song) -> Unit,
    private val onAddToPlaylist: (Song) -> Unit,
    private val onStartDrag: ((RecyclerView.ViewHolder) -> Unit)? = null,
) : RecyclerView.Adapter<SongAdapter.VH>() {

    private var songs: List<Song> = emptyList()
    private var currentUri: String? = null
    private var isPlaying: Boolean = false

    var dragEnabled: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun submit(list: List<Song>) {
        songs = list
        notifyDataSetChanged()
    }

    fun setCurrentUri(uri: String?) {
        currentUri = uri
        notifyDataSetChanged()
    }

    fun setPlaying(playing: Boolean) {
        isPlaying = playing
        notifyDataSetChanged()
    }

    fun move(from: Int, to: Int) {
        if (from < 0 || from >= songs.size || to < 0 || to >= songs.size) return
        val list = songs.toMutableList()
        val item = list.removeAt(from)
        list.add(to, item)
        songs = list
        notifyItemMoved(from, to)
    }

    fun songs(): List<Song> = songs

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val accentBar: View = view.findViewById(R.id.accentBar)
        val artFrame: FrameLayout = view.findViewById(R.id.artFrame)
        val art: ImageView = view.findViewById(R.id.ivArt)
        val playState: ImageView = view.findViewById(R.id.ivPlayState)
        val title: TextView = view.findViewById(R.id.tvTitle)
        val subtitle: TextView = view.findViewById(R.id.tvSubtitle)
        val duration: TextView = view.findViewById(R.id.tvDuration)
        val fav: ImageView = view.findViewById(R.id.ivFav)
        val addPlaylist: ImageView = view.findViewById(R.id.ivAddPlaylist)
        val drag: ImageView = view.findViewById(R.id.ivDrag)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = songs.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val song = songs[position]
        val isCurrent = song.uri == currentUri

        holder.title.text = song.title
        holder.subtitle.text = song.artist.ifBlank { context.getString(R.string.unknown_artist) }
        holder.duration.text = if (song.durationMs > 0) formatDuration(song.durationMs) else ""

        holder.title.setTextColor(
            if (isCurrent) SettingsStore.accent(context) else context.getColor(R.color.text_primary),
        )
        holder.subtitle.setTextColor(
            if (isCurrent) SettingsStore.accentLight(context) else context.getColor(R.color.text_secondary),
        )
        holder.accentBar.setBackgroundColor(
            if (isCurrent) SettingsStore.accent(context) else Color.TRANSPARENT,
        )

        val showingPause = isCurrent && isPlaying
        holder.playState.setImageResource(
            if (showingPause) R.drawable.ic_pause else R.drawable.ic_play,
        )
        holder.playState.imageTintList = ColorStateList.valueOf(
            if (isCurrent) SettingsStore.accent(context) else context.getColor(R.color.white),
        )

        holder.drag.visibility = if (dragEnabled) View.VISIBLE else View.GONE
        if (dragEnabled && onStartDrag != null) {
            holder.drag.setOnTouchListener { _, e ->
                if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN) onStartDrag(holder)
                false
            }
        } else {
            holder.drag.setOnTouchListener(null)
        }

        Artwork.loadAlbumArt(context, holder.art, song.albumId)

        val isFav = MusicStore.isFavorite(song.uri)
        holder.fav.setImageResource(if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart)
        holder.fav.imageTintList = ColorStateList.valueOf(
            if (isFav) SettingsStore.accent(context) else context.getColor(R.color.chrome_dark),
        )

        holder.artFrame.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onTogglePlay(song, pos)
        }
        holder.fav.setOnClickListener { onFavorite(song) }
        holder.addPlaylist.setOnClickListener { onAddToPlaylist(song) }
        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onOpenPlayer(song, pos)
        }
        holder.itemView.setOnLongClickListener {
            onLongClick(song)
            true
        }
    }
}
