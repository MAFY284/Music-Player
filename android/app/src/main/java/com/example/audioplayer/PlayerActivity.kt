package com.example.audioplayer

import android.content.res.ColorStateList
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.GestureDetector
import android.view.MotionEvent
import android.widget.SeekBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.audioplayer.databinding.ActivityPlayerBinding
import kotlin.math.abs

class PlayerActivity : AppCompatActivity() {

    private lateinit var binding: ActivityPlayerBinding
    private val handler = Handler(Looper.getMainLooper())
    private var trackingTouch = false

    private val playerListener = object : Player.Listener {
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) = updateNowPlaying()
        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) =
            updateNowPlaying()
        override fun onIsPlayingChanged(isPlaying: Boolean) = updatePlayPause()
        override fun onPlaybackStateChanged(playbackState: Int) = updatePlayPause()
        override fun onRepeatModeChanged(repeatMode: Int) = updateRepeatIcon()
        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) = updateShuffleIcon()
    }

    private val ticker = object : Runnable {
        override fun run() {
            updateSeek()
            handler.postDelayed(this, 500)
        }
    }

    private val gestureDetector = GestureDetector(
        this,
        object : GestureDetector.SimpleOnGestureListener() {
            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float,
            ): Boolean {
                if (abs(velocityX) < 2000 || abs(velocityX) < abs(velocityY)) return false
                if (velocityX < 0) PlayerManager.next() else PlayerManager.previous()
                return true
            }
        },
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener { finish() }
        binding.btnPlayPause.setOnClickListener { PlayerManager.togglePlayPause() }
        binding.btnNext.setOnClickListener { PlayerManager.next() }
        binding.btnPrev.setOnClickListener { PlayerManager.previous() }
        binding.btnForward.setOnClickListener { PlayerManager.seekForward() }
        binding.btnRewind.setOnClickListener { PlayerManager.seekBackward() }
        binding.btnRepeat.setOnClickListener { cycleRepeat() }
        binding.btnShuffle.setOnClickListener { toggleShuffle() }
        binding.btnFavorite.setOnClickListener { toggleFavorite() }

        binding.ivCover.setOnTouchListener { _, e ->
            gestureDetector.onTouchEvent(e)
            true
        }

        binding.sbProgress.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser) {
                    val c = PlayerManager.controller() ?: return
                    val dur = c.duration
                    if (dur > 0) binding.tvPosition.text = formatDuration(progress * dur / 1000)
                }
            }

            override fun onStartTrackingTouch(seekBar: SeekBar?) {
                trackingTouch = true
            }

            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                trackingTouch = false
                val c = PlayerManager.controller() ?: return
                val dur = c.duration
                if (dur > 0) PlayerManager.seekTo(binding.sbProgress.progress * dur / 1000)
            }
        })
    }

    override fun onStart() {
        super.onStart()
        PlayerManager.addListener(playerListener)
        updateAll()
        handler.postDelayed(ticker, 500)
    }

    override fun onStop() {
        super.onStop()
        PlayerManager.removeListener(playerListener)
        handler.removeCallbacks(ticker)
    }

    private fun updateAll() {
        updateNowPlaying()
        updatePlayPause()
        updateRepeatIcon()
        updateShuffleIcon()
        updateFavoriteIcon()
        updateSeek()
    }

    private fun updateNowPlaying() {
        binding.tvTitle.text = PlayerManager.currentTitle() ?: getString(R.string.no_track)
        binding.tvArtist.text = PlayerManager.currentArtist() ?: ""
        Artwork.loadUri(this, binding.ivCover, PlayerManager.currentArtworkUri())
    }

    private fun updatePlayPause() {
        val playing = PlayerManager.isPlaying()
        binding.ivPlayPause.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
    }

    private fun updateRepeatIcon() {
        val mode = PlayerManager.controller()?.repeatMode ?: Player.REPEAT_MODE_OFF
        when (mode) {
            Player.REPEAT_MODE_ONE -> {
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat_one)
                binding.btnRepeat.imageTintList = ColorStateList.valueOf(getColor(R.color.orange))
            }
            Player.REPEAT_MODE_ALL -> {
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
                binding.btnRepeat.imageTintList = ColorStateList.valueOf(getColor(R.color.orange))
            }
            else -> {
                binding.btnRepeat.setImageResource(R.drawable.ic_repeat)
                binding.btnRepeat.imageTintList = ColorStateList.valueOf(getColor(R.color.chrome))
            }
        }
    }

    private fun updateShuffleIcon() {
        val on = PlayerManager.controller()?.shuffleModeEnabled == true
        binding.btnShuffle.imageTintList =
            ColorStateList.valueOf(getColor(if (on) R.color.orange else R.color.chrome))
    }

    private fun updateFavoriteIcon() {
        val uri = PlayerManager.currentSongUri()
        val fav = uri != null && MusicStore.isFavorite(uri)
        binding.btnFavorite.setImageResource(if (fav) R.drawable.ic_heart_filled else R.drawable.ic_heart)
        binding.btnFavorite.imageTintList =
            ColorStateList.valueOf(getColor(if (fav) R.color.orange else R.color.chrome))
    }

    private fun toggleFavorite() {
        val uri = PlayerManager.currentSongUri() ?: return
        MusicStore.toggleFavorite(uri)
        updateFavoriteIcon()
    }

    private fun updateSeek() {
        val c = PlayerManager.controller() ?: return
        val dur = c.duration
        val pos = c.currentPosition
        binding.tvRemaining.text = formatRemaining(if (dur > 0) dur - pos else 0)
        if (!trackingTouch) {
            binding.tvPosition.text = formatDuration(pos)
            binding.sbProgress.max = 1000
            binding.sbProgress.progress = if (dur > 0) (pos * 1000 / dur).toInt() else 0
        }
    }

    private fun cycleRepeat() {
        PlayerManager.cycleRepeatMode()
        val mode = PlayerManager.controller()?.repeatMode ?: return
        toast(
            when (mode) {
                Player.REPEAT_MODE_ALL -> getString(R.string.repeat_all)
                Player.REPEAT_MODE_ONE -> getString(R.string.repeat_one)
                else -> getString(R.string.repeat_off)
            },
        )
        updateRepeatIcon()
    }

    private fun toggleShuffle() {
        PlayerManager.toggleShuffle()
        val on = PlayerManager.controller()?.shuffleModeEnabled == true
        toast(if (on) getString(R.string.shuffle_on) else getString(R.string.shuffle_off))
        updateShuffleIcon()
    }

    private fun toast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
