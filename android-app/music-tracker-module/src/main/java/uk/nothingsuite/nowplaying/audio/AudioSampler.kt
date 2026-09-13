package uk.nothingsuite.nowplaying.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

/**
 * Records one short PCM clip from the built-in mic. 8 kHz mono is plenty for
 * fingerprinting (ACRCloud recommends ≥ 8 kHz) and keeps the upload ~80 KB.
 */
object AudioSampler {

    const val SAMPLE_RATE = 8000
    private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
    private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

    data class Clip(val pcm: ByteArray, val rms: Double) {
        fun toWav(): ByteArray = WavEncoder.wrap(pcm, SAMPLE_RATE)
    }

    /** Blocking; call on Dispatchers.IO. Requires RECORD_AUDIO already granted. */
    @SuppressLint("MissingPermission")
    fun record(seconds: Int = 5): Clip? {
        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) return null

        // Plain MIC source: no voice-call processing, no beam-forming — we want the room, not a voice.
        val recorder = AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE, CHANNEL, ENCODING, maxOf(minBuf, SAMPLE_RATE))
        if (recorder.state != AudioRecord.STATE_INITIALIZED) return null

        val target = SAMPLE_RATE * 2 * seconds
        val out = ByteArrayOutputStream(target)
        val buf = ByteArray(4096)
        try {
            recorder.startRecording()
            while (out.size() < target) {
                val n = recorder.read(buf, 0, buf.size)
                if (n <= 0) break
                out.write(buf, 0, n)
            }
        } finally {
            runCatching { recorder.stop() }
            recorder.release()
        }
        val pcm = out.toByteArray()
        return Clip(pcm, rms(pcm))
    }

    private fun rms(pcm: ByteArray): Double {
        val bb = ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN)
        var sum = 0.0
        val n = pcm.size / 2
        repeat(n) { val s = bb.short.toDouble(); sum += s * s }
        return if (n == 0) 0.0 else sqrt(sum / n)
    }
}

/** Minimal 16-bit PCM WAV container. */
object WavEncoder {
    fun wrap(pcm: ByteArray, sampleRate: Int, channels: Int = 1): ByteArray {
        val byteRate = sampleRate * channels * 2
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray()).putInt(36 + pcm.size).put("WAVE".toByteArray())
        header.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(channels.toShort())
        header.putInt(sampleRate).putInt(byteRate).putShort((channels * 2).toShort()).putShort(16)
        header.put("data".toByteArray()).putInt(pcm.size)
        return header.array() + pcm
    }
}
