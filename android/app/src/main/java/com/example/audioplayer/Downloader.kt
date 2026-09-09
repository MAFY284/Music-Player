package com.example.audioplayer

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File

object Downloader {

    private val audioFormats = listOf("mp3", "wav", "flac", "m4a", "opus")

    @Volatile
    private var quickjsPath: String = ""

    @Volatile
    private var inited = false

    fun init(context: Context) {
        if (inited) return
        synchronized(this) {
            if (inited) return
            YoutubeDL.getInstance().init(context)
            FFmpeg.getInstance().init(context)
            quickjsPath = File(context.applicationInfo.nativeLibraryDir, "libqjs.so").absolutePath
            try {
                YoutubeDL.getInstance().updateYoutubeDL(context)
            } catch (_: Exception) {
            }
            inited = true
        }
    }

    fun isReady(): Boolean = inited

    fun formats(): List<String> = audioFormats

    private fun newRequest(url: String): YoutubeDLRequest {
        val request = YoutubeDLRequest(url)
        request.addOption("--no-playlist")
        request.addOption("--socket-timeout", "30")
        if (quickjsPath.isNotEmpty()) {
            request.addOption("--js-runtimes", "quickjs:$quickjsPath")
        }
        return request
    }

    @Throws(Exception::class)
    fun fetchInfo(url: String): AudioInfo {
        val info = YoutubeDL.getInstance().getInfo(newRequest(url))
        return AudioInfo(
            title = info.title ?: "Audio",
            uploader = info.uploader ?: "",
            thumbnail = info.thumbnail ?: "",
            durationSeconds = info.duration,
            audioFormats = audioFormats,
        )
    }

    @Throws(Exception::class)
    fun download(
        url: String,
        audioFormat: String,
        outputDir: File,
        processId: String,
        onProgress: (Float) -> Unit,
    ): File {
        val request = newRequest(url)
        request.addOption("-f", "bestaudio/best")
        request.addOption("-x")
        request.addOption("--audio-format", audioFormat)
        if (audioFormat == "mp3" || audioFormat == "m4a") {
            request.addOption("--audio-quality", "320")
        }
        request.addOption("-o", File(outputDir, "%(id)s.%(ext)s").absolutePath)

        YoutubeDL.getInstance().execute(request, processId) { progress, _, _ ->
            onProgress(progress)
        }

        return outputDir.listFiles { f ->
            f.isFile && !f.name.endsWith(".part") && f.length() > 0
        }?.maxByOrNull { it.length() }
            ?: throw IllegalStateException("No se encontró el archivo descargado")
    }

    fun cancel(processId: String) {
        try {
            YoutubeDL.getInstance().destroyProcessById(processId)
        } catch (_: Exception) {
        }
    }
}
