package com.example.audioplayer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView

class PlaylistAdapter(
    private val context: android.content.Context,
    private val onClick: (Playlist) -> Unit,
    private val onEdit: (Playlist) -> Unit,
) : RecyclerView.Adapter<PlaylistAdapter.VH>() {

    private var playlists: List<Playlist> = emptyList()

    fun submit(list: List<Playlist>) {
        playlists = list
        notifyDataSetChanged()
    }

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val cover: ImageView = view.findViewById(R.id.ivCover)
        val name: TextView = view.findViewById(R.id.tvName)
        val count: TextView = view.findViewById(R.id.tvCount)
        val edit: ImageView = view.findViewById(R.id.ivEdit)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_playlist, parent, false)
        return VH(view)
    }

    override fun getItemCount(): Int = playlists.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        val p = playlists[position]
        holder.name.text = p.name
        val n = p.songUris.size
        holder.count.text =
            if (n == 1) context.getString(R.string.one_song)
            else context.getString(R.string.songs_count, n)
        MusicStore.coverFile(p.id)?.let { Artwork.loadCover(context, holder.cover, it) }
        (holder.itemView as MaterialCardView).setOnClickListener { onClick(p) }
        holder.edit.setOnClickListener { onEdit(p) }
    }
}
