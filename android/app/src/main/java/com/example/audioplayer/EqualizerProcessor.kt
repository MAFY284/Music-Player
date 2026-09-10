package com.example.audioplayer

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.AudioProcessor.AudioFormat
import androidx.media3.common.audio.AudioProcessor.UnhandledAudioFormatException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

class EqualizerProcessor : AudioProcessor {

    companion object {
        const val BAND_COUNT = 8
        val BAND_FREQS = intArrayOf(40, 100, 250, 630, 1600, 4000, 8000, 16000)
        private const val Q = 1.0f
        private const val MAX_CHANNELS = 8
    }

    @Volatile
    var gains = FloatArray(BAND_COUNT)

    @Volatile
    var version = 0

    private var cachedVersion = -1
    private var sampleRate = 48000
    private var channelCount = 2

    private var inputAudioFormat = AudioFormat.NOT_SET
    private var outputAudioFormat = AudioFormat.NOT_SET
    private var buffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var outputBuffer: ByteBuffer = AudioProcessor.EMPTY_BUFFER
    private var inputEnded = false

    private val coefs = Array(BAND_COUNT) { FloatArray(5) }
    private val state = Array(BAND_COUNT) { Array(MAX_CHANNELS) { FloatArray(4) } }

    override fun configure(inputAudioFormat: AudioFormat): AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw UnhandledAudioFormatException(inputAudioFormat)
        }
        this.inputAudioFormat = inputAudioFormat
        this.outputAudioFormat = inputAudioFormat
        sampleRate = inputAudioFormat.sampleRate
        channelCount = inputAudioFormat.channelCount.coerceIn(1, MAX_CHANNELS)
        cachedVersion = -1
        return outputAudioFormat
    }

    override fun isActive(): Boolean = true

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        if (version != cachedVersion) {
            recomputeCoefs()
            cachedVersion = version
        }
        val inputSize = inputBuffer.remaining()
        val shortInput = inputBuffer.asShortBuffer()
        val sampleCount = shortInput.remaining()

        if (buffer.capacity() < inputSize) {
            buffer = ByteBuffer.allocateDirect(inputSize).order(ByteOrder.nativeOrder())
        } else {
            buffer.clear()
        }
        val shortOutput = buffer.asShortBuffer()

        val allZero = !gains.any { it != 0f }
        for (i in 0 until sampleCount) {
            val x = shortInput.get()
            shortOutput.put(if (allZero) x else processSample(x, i % channelCount))
        }

        buffer.limit(inputSize)
        buffer.position(0)
        outputBuffer = buffer
        inputBuffer.position(inputBuffer.position() + inputSize)
    }

    override fun queueEndOfStream() {
        inputEnded = true
    }

    override fun getOutput(): ByteBuffer {
        val out = outputBuffer
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        return out
    }

    override fun isEnded(): Boolean = inputEnded && outputBuffer === AudioProcessor.EMPTY_BUFFER

    override fun flush() {
        outputBuffer = AudioProcessor.EMPTY_BUFFER
        inputEnded = false
        for (b in 0 until BAND_COUNT) {
            for (c in 0 until MAX_CHANNELS) {
                state[b][c].fill(0f)
            }
        }
    }

    override fun reset() {
        flush()
        inputAudioFormat = AudioFormat.NOT_SET
        outputAudioFormat = AudioFormat.NOT_SET
    }

    private fun recomputeCoefs() {
        for (b in 0 until BAND_COUNT) {
            val g = gains[b].toDouble()
            val a = 10.0.pow(g / 40.0)
            val omega = 2.0 * PI * BAND_FREQS[b] / sampleRate
            val c = cos(omega)
            val alpha = sin(omega) / (2.0 * Q)
            val b0 = 1.0 + alpha * a
            val b1 = -2.0 * c
            val b2 = 1.0 - alpha * a
            val a0 = 1.0 + alpha / a
            val a1 = -2.0 * c
            val a2 = 1.0 - alpha / a
            coefs[b][0] = (b0 / a0).toFloat()
            coefs[b][1] = (b1 / a0).toFloat()
            coefs[b][2] = (b2 / a0).toFloat()
            coefs[b][3] = (a1 / a0).toFloat()
            coefs[b][4] = (a2 / a0).toFloat()
        }
    }

    private fun processSample(x: Short, ch: Int): Short {
        var v = x.toFloat() / 32768f
        for (b in 0 until BAND_COUNT) {
            val c = coefs[b]
            val s = state[b][ch]
            val y = c[0] * v + c[1] * s[0] + c[2] * s[1] - c[3] * s[2] - c[4] * s[3]
            s[1] = s[0]
            s[0] = v
            s[3] = s[2]
            s[2] = y
            v = y
        }
        val clamped = (v * 32768f).toInt().coerceIn(-32768, 32767)
        return clamped.toShort()
    }
}
