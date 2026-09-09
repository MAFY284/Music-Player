package com.example.audioplayer

import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.dialog.MaterialAlertDialogBuilder

object PlaylistDialogs {

    fun showAddToPlaylist(activity: AppCompatActivity, songUri: String) {
        val playlists = MusicStore.playlists()
        if (playlists.isEmpty()) {
            showCreate(activity) { p ->
                MusicStore.addToPlaylist(p.id, songUri)
                toast(activity, activity.getString(R.string.added_to_playlist))
            }
            return
        }
        val names = playlists.map { it.name }.toTypedArray()
        MaterialAlertDialogBuilder(activity, R.style.App_Dialog)
            .setTitle(R.string.add_to_playlist)
            .setItems(names) { _, which ->
                val ok = MusicStore.addToPlaylist(playlists[which].id, songUri)
                toast(
                    activity,
                    activity.getString(
                        if (ok) R.string.added_to_playlist else R.string.already_in_playlist,
                    ),
                )
            }
            .setNeutralButton(R.string.new_playlist) { _, _ ->
                showCreate(activity) { p ->
                    MusicStore.addToPlaylist(p.id, songUri)
                    toast(activity, activity.getString(R.string.added_to_playlist))
                }
            }
            .show()
    }

    private fun showCreate(activity: AppCompatActivity, onCreated: (Playlist) -> Unit) {
        val input = EditText(activity).apply {
            hint = activity.getString(R.string.playlist_name_hint)
            setTextColor(activity.getColor(R.color.text_primary))
            setHintTextColor(activity.getColor(R.color.text_secondary))
            setPadding(dp(activity, 20), dp(activity, 16), dp(activity, 20), dp(activity, 16))
        }
        MaterialAlertDialogBuilder(activity, R.style.App_Dialog)
            .setTitle(R.string.new_playlist)
            .setView(input)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = input.text.toString().trim()
                    .ifEmpty { activity.getString(R.string.new_playlist) }
                onCreated(MusicStore.createPlaylist(name))
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private fun dp(context: android.content.Context, value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun toast(activity: AppCompatActivity, message: String) {
        Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
    }
}
