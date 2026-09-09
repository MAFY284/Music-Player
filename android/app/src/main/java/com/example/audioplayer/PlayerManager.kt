package com.example.audioplayer

import android.content.ComponentName
import android.content.Context
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import java.util.concurrent.CopyOnWriteArrayList

object PlayerManager {

    private const val SEEK_STEP_MS = 10_000L

    @Volatile
    private var controller: MediaController? = null

    @Volatile
    private var connecting = false

    private val pending = CopyOnWriteArrayList<Player.Listener>()

    private val recentsListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            mediaItem?.localConfiguration?.uri?.toString()?.let {
                MusicStore.recordRecent(it)
            }
        }
    }

    fun init(context: Context) {
        if (controller != null || connecting) return
        connecting = true
        val app = context.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(recentsListener)
                connecting = false
                pending.forEach { c.addListener(it) }
                pending.clear()
            } catch (_: Exception) {
                connecting = false
            }
        }, ContextCompat.getMainExecutor(app))
    }

    fun isReady(): Boolean = controller != null

    fun addListener(listener: Player.Listener) {
        val c = controller
        if (c != null) c.addListener(listener) else pending.add(listener)
    }

    fun removeListener(listener: Player.Listener) {
        controller?.removeListener(listener)
    }

    fun controller(): MediaController? = controller

    fun playQueue(songs: List<Song>, startIndex: Int) {
        val c = controller ?: return
        if (songs.isEmpty()) return
        val items = songs.map { it.toMediaItem() }
        c.setMediaItems(items, startIndex.coerceIn(0, items.size - 1), 0L)
        c.prepare()
        c.play()
    }

    fun togglePlayPause() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() {
        controller?.seekToNext()
    }

    fun previous() {
        val c = controller ?: return
        if (c.currentPosition > 3000) c.seekTo(0) else c.seekToPrevious()
    }

    fun seekTo(positionMs: Long) {
        controller?.seekTo(positionMs)
    }

    fun seekForward() {
        val c = controller ?: return
        if (c.duration == androidx.media3.common.C.TIME_UNSET) return
        c.seekTo(minOf(c.duration, c.currentPosition + SEEK_STEP_MS))
    }

    fun seekBackward() {
        val c = controller ?: return
        c.seekTo(maxOf(0L, c.currentPosition - SEEK_STEP_MS))
    }

    fun cycleRepeatMode() {
        val c = controller ?: return
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    fun toggleShuffle() {
        val c = controller ?: return
        c.shuffleModeEnabled = !c.shuffleModeEnabled
    }

    fun hasMedia(): Boolean = controller?.let { it.mediaItemCount > 0 } ?: false

    fun currentSongId(): String? = controller?.currentMediaItem?.mediaId

    fun currentTitle(): String? = controller?.currentMediaItem?.mediaMetadata?.title?.toString()

    fun currentArtist(): String? = controller?.currentMediaItem?.mediaMetadata?.artist?.toString()

    fun currentArtworkUri() = controller?.currentMediaItem?.mediaMetadata?.artworkUri

    fun currentSongUri(): String? =
        controller?.currentMediaItem?.localConfiguration?.uri?.toString()

    fun isPlaying(): Boolean = controller?.isPlaying == true
}
