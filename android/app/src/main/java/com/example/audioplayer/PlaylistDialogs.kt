package com.example.audioplayer

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDialog
import com.example.audioplayer.databinding.DialogAddPlaylistBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object PlaylistDialogs {

    fun showAddToPlaylist(activity: AppCompatActivity, songUri: String) {
        AddToPlaylistDialog(activity, songUri).show()
    }
}

private class AddToPlaylistDialog(
    context: Context,
    private val songUri: String,
) : AppCompatDialog(context) {

    private val binding: DialogAddPlaylistBinding = DialogAddPlaylistBinding.inflate(layoutInflater)
    private val mainHandler = Handler(Looper.getMainLooper())

    init {
        setContentView(binding.root)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        setCanceledOnTouchOutside(true)

        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnCreate.setOnClickListener { showCreateDialog() }
        populate()
    }

    override fun show() {
        super.show()
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun populate() {
        binding.container.removeAllViews()
        val playlists = MusicStore.playlists()
        if (playlists.isEmpty()) {
            binding.container.addView(
                TextView(context).apply {
                    text = context.getString(R.string.empty_playlists)
                    setTextColor(context.getColor(R.color.text_secondary))
                    textSize = 13f
                    setPadding(dp(16), dp(12), dp(16), dp(12))
                },
            )
            return
        }
        playlists.forEach { p ->
            val row = LayoutInflater.from(context)
                .inflate(R.layout.item_add_playlist, binding.container, false)
            row.findViewById<TextView>(R.id.tvName).text = p.name
            val n = p.songUris.size
            row.findViewById<TextView>(R.id.tvCount).text =
                if (n == 1) context.getString(R.string.one_song)
                else context.getString(R.string.songs_count, n)
            MusicStore.coverFile(p.id)?.let { Artwork.loadCover(context, row.findViewById(R.id.ivCover), it) }
            row.setOnClickListener {
                val ok = MusicStore.addToPlaylist(p.id, songUri)
                toast(
                    context.getString(
                        if (ok) R.string.added_to_playlist else R.string.already_in_playlist,
                    ),
                )
                dismiss()
            }
            binding.container.addView(row)
        }
    }

    private fun showCreateDialog() {
        val input = EditText(context).apply {
            hint = context.getString(R.string.playlist_name_hint)
            setTextColor(context.getColor(R.color.text_primary))
            setHintTextColor(context.getColor(R.color.text_secondary))
            setPadding(dp(20), dp(16), dp(20), dp(16))
        }
        MaterialAlertDialogBuilder(context, R.style.App_Dialog)
            .setTitle(R.string.new_playlist)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                    .ifEmpty { context.getString(R.string.new_playlist) }
                val p = MusicStore.createPlaylist(name)
                MusicStore.addToPlaylist(p.id, songUri)
                toast(context.getString(R.string.added_to_playlist))
                dismiss()
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
