package com.example.audioplayer

import android.content.Context
import android.content.res.ColorStateList
import android.os.Handler
import android.os.Looper
import android.view.View
import android.view.WindowManager
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AppCompatDialog
import com.example.audioplayer.databinding.DialogDownloadBinding
import com.google.android.material.button.MaterialButton
import com.yausername.youtubedl_android.YoutubeDL
import java.io.File
import java.util.UUID
import kotlin.concurrent.thread

class DownloadDialog(
    context: Context,
    private val onDownloaded: () -> Unit,
    private val onPickLocation: (() -> Unit)? = null,
) : AppCompatDialog(context) {

    private val binding: DialogDownloadBinding = DialogDownloadBinding.inflate(layoutInflater)
    private val mainHandler = Handler(Looper.getMainLooper())
    private val formatButtons = mutableMapOf<String, MaterialButton>()

    private var selectedFormat = "mp3"
    private var running = false
    private var processId: String? = null

    init {
        setContentView(binding.root)
        window?.setBackgroundDrawableResource(android.R.color.transparent)
        setCanceledOnTouchOutside(true)

        buildFormatButtons()
        refreshLocation()

        binding.btnClose.setOnClickListener { dismiss() }
        binding.btnCancel.setOnClickListener { dismiss() }
        binding.btnDownload.setOnClickListener { start() }
        binding.btnCancelDownload.setOnClickListener { cancelRunning() }
        binding.btnPickLocation.setOnClickListener { onPickLocation?.invoke() }
    }

    fun refreshLocation() {
        binding.tvLocation.text =
            context.getString(R.string.dl_location, MusicStore.downloadDisplayName())
    }

    override fun show() {
        super.show()
        refreshLocation()
        window?.setLayout(
            (context.resources.displayMetrics.widthPixels * 0.92).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
        )
    }

    private fun buildFormatButtons() {
        Downloader.formats().forEachIndexed { index, format ->
            val b = MaterialButton(context)
            b.text = format.uppercase()
            b.isAllCaps = false
            b.textSize = 12f
            b.minWidth = 0
            b.insetTop = 0
            b.insetBottom = 0
            b.setPadding(0, 0, 0, 0)
            val params = LinearLayout.LayoutParams(0, dp(40), 1f)
            if (index > 0) params.marginStart = dp(5)
            b.layoutParams = params
            b.setOnClickListener { selectFormat(format) }
            binding.formatContainer.addView(b)
            formatButtons[format] = b
            styleButton(b, format == selectedFormat)
        }
    }

    private fun selectFormat(format: String) {
        selectedFormat = format
        formatButtons.forEach { (f, b) -> styleButton(b, f == format) }
    }

    private fun styleButton(b: MaterialButton, selected: Boolean) {
        b.backgroundTintList = ColorStateList.valueOf(
            if (selected) SettingsStore.accent(context) else context.getColor(R.color.card_gray_dark),
        )
        b.setTextColor(context.getColor(if (selected) R.color.white else R.color.chrome))
        b.strokeColor = ColorStateList.valueOf(
            if (selected) SettingsStore.accent(context) else context.getColor(R.color.strokes),
        )
        b.strokeWidth = dp(1)
    }

    private fun start() {
        val url = binding.etUrl.text.toString().trim()
        if (url.isEmpty() || !url.startsWith("http")) {
            toast(context.getString(R.string.dl_err_url))
            return
        }
        if (!Downloader.isReady()) {
            toast(context.getString(R.string.dl_err_initializing))
            return
        }

        running = true
        setCanceledOnTouchOutside(false)
        setCancelable(false)

        binding.btnDownload.visibility = View.GONE
        binding.btnCancel.visibility = View.GONE
        binding.etUrl.isEnabled = false
        binding.progressArea.visibility = View.VISIBLE
        binding.doneArea.visibility = View.GONE
        binding.pbDownload.visibility = View.GONE
        binding.pbDownload.progress = 0
        binding.tvProgress.text = context.getString(R.string.dl_analyzing)

        val processId = "dl_" + UUID.randomUUID().toString()
        this.processId = processId
        val format = selectedFormat

        thread {
            try {
                val info = Downloader.fetchInfo(url)
                mainHandler.post {
                    binding.tvProgress.text = context.getString(R.string.dl_downloading)
                    binding.pbDownload.visibility = View.VISIBLE
                }
                val outDir = File(context.cacheDir, "ytdl").apply { mkdirs() }
                outDir.listFiles()?.forEach { it.delete() }
                val file = Downloader.download(url, format, outDir, processId) { p ->
                    mainHandler.post { binding.pbDownload.progress = p.toInt() }
                }
                val saved = MediaSaver.save(
                    context, file, info.title, info.uploader, format,
                    info.durationSeconds * 1000L,
                )
                file.delete()
                mainHandler.post { complete(saved != null) }
            } catch (e: YoutubeDL.CanceledException) {
                mainHandler.post { onCanceled() }
            } catch (e: Exception) {
                mainHandler.post { onFailed(e) }
            }
        }
    }

    private fun complete(success: Boolean) {
        running = false
        onDownloaded()
        if (success) {
            binding.progressArea.visibility = View.GONE
            binding.doneArea.visibility = View.VISIBLE
            mainHandler.postDelayed({ if (isShowing) dismiss() }, 1200)
        } else {
            binding.tvProgress.text = context.getString(R.string.dl_err_generic)
            resetToIdle()
        }
    }

    private fun onCanceled() {
        running = false
        resetToIdle()
        toast(context.getString(R.string.dl_canceled))
    }

    private fun onFailed(e: Exception) {
        running = false
        binding.tvProgress.text = "${context.getString(R.string.dl_err_generic)}: ${e.message ?: ""}"
        resetToIdle()
    }

    private fun resetToIdle() {
        binding.progressArea.visibility = View.GONE
        binding.doneArea.visibility = View.GONE
        binding.btnDownload.visibility = View.VISIBLE
        binding.btnCancel.visibility = View.VISIBLE
        binding.etUrl.isEnabled = true
        setCanceledOnTouchOutside(true)
        setCancelable(true)
    }

    private fun cancelRunning() {
        if (!running) return
        processId?.let { Downloader.cancel(it) }
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()

    private fun toast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
