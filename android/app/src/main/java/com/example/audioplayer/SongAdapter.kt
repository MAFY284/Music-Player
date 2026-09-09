package com.example.audioplayer

import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class SongAdapter(
    private val context: android.content.Context,
    private val onPlay: (Song, Int) -> Unit,
    private val onFavorite: (Song) -> Unit,
    private val onLongClick: (Song) -> Unit,
    private val onAddToPlaylist: (Song) -> Unit,
) : RecyclerView.Adapter<SongAdapter.VH>() {

    private var songs: List<Song> = emptyList()

    fun submit(list: List<Song>) {
        songs = list
        notifyDataSetChanged()
    }

    fun songs(): List<Song> = songs

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val art: ImageView = view.findViewById(R.id.ivArt)
        val title: TextView = view.findViewById(R.id.tvTitle)
        val subtitle: TextView = view.findViewById(R.id.tvSubtitle)
        val duration: TextView = view.findViewById(R.id.tvDuration)
        val fav: ImageView = view.findViewById(R.id.ivFav)
        val addPlaylist: ImageView = view.findViewById(R.id.ivAddPlaylist)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_song, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = songs.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val song = songs[position]
        holder.title.text = song.title
        holder.subtitle.text = song.artist.ifBlank { context.getString(R.string.unknown_artist) }
        holder.duration.text = if (song.durationMs > 0) formatDuration(song.durationMs) else ""

        Artwork.loadAlbumArt(context, holder.art, song.albumId)

        val isFav = MusicStore.isFavorite(song.uri)
        holder.fav.setImageResource(if (isFav) R.drawable.ic_heart_filled else R.drawable.ic_heart)
        holder.fav.imageTintList = ColorStateList.valueOf(
            context.getColor(if (isFav) R.color.orange else R.color.chrome_dark),
        )

        holder.fav.setOnClickListener { onFavorite(song) }
        holder.addPlaylist.setOnClickListener { onAddToPlaylist(song) }
        holder.itemView.setOnClickListener { onPlay(song, position) }
        holder.itemView.setOnLongClickListener {
            onLongClick(song)
            true
        }
    }
}
