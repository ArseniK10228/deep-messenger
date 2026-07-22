package online.deepdesign.deep.call

import android.content.Context
import org.webrtc.AudioTrack
import org.webrtc.audio.AudioTrackSink
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Records WebRTC audio tracks to WAV — works while the mic is owned by WebRTC. */
class WebRtcAudioRecorder {
    private val lock = Any()
    private val sinks = mutableListOf<AttachedSink>()
    private var startedAtMs: Long = 0L

    fun attach(track: AudioTrack) {
        synchronized(lock) {
            if (sinks.any { it.track.id() == track.id() }) return
            if (startedAtMs == 0L) startedAtMs = System.currentTimeMillis()
            val attached = AttachedSink(track, ByteArrayOutputStream())
            val sink = AudioTrackSink { audioData, _, sampleRate, channels, _, _ ->
                val bytes = ByteArray(audioData.remaining())
                audioData.get(bytes)
                synchronized(lock) {
                    attached.sampleRate = sampleRate
                    attached.channels = channels.coerceAtLeast(1)
                    attached.buffer.write(bytes)
                }
            }
            attached.sink = sink
            track.addSink(sink)
            sinks += attached
        }
    }

    fun stopToWavFile(context: Context): File? {
        val snapshot = synchronized(lock) {
            val copy = sinks.toList()
            sinks.forEach { attached ->
                attached.sink?.let { attached.track.removeSink(it) }
            }
            sinks.clear()
            copy
        }
        if (snapshot.isEmpty()) return null
        val pcm = ByteArrayOutputStream()
        var sampleRate = 48000
        var channels = 1
        snapshot.forEach { attached ->
            sampleRate = attached.sampleRate
            channels = attached.channels
            pcm.write(attached.buffer.toByteArray())
        }
        val data = pcm.toByteArray()
        if (data.isEmpty()) return null
        val file = File(context.cacheDir, "call_${System.currentTimeMillis()}.wav")
        writeWav(file, data, sampleRate, channels)
        return file
    }

    fun startedAtIso(): String? {
        if (startedAtMs <= 0L) return null
        return java.time.Instant.ofEpochMilli(startedAtMs).toString()
    }

    fun durationMs(): Long {
        if (startedAtMs <= 0L) return 0L
        return (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
    }

    fun reset() {
        synchronized(lock) {
            sinks.forEach { attached ->
                attached.sink?.let { attached.track.removeSink(it) }
            }
            sinks.clear()
            startedAtMs = 0L
        }
    }

    private class AttachedSink(
        val track: AudioTrack,
        val buffer: ByteArrayOutputStream,
        var sampleRate: Int = 48000,
        var channels: Int = 1
    ) {
        var sink: AudioTrackSink? = null
    }

    private fun writeWav(file: File, pcm: ByteArray, sampleRate: Int, channels: Int) {
        val bitsPerSample = 16
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = ByteBuffer.allocate(44).order(ByteOrder.LITTLE_ENDIAN)
        header.put("RIFF".toByteArray())
        header.putInt(36 + pcm.size)
        header.put("WAVE".toByteArray())
        header.put("fmt ".toByteArray())
        header.putInt(16)
        header.putShort(1)
        header.putShort(channels.toShort())
        header.putInt(sampleRate)
        header.putInt(byteRate)
        header.putShort((channels * bitsPerSample / 8).toShort())
        header.putShort(bitsPerSample.toShort())
        header.put("data".toByteArray())
        header.putInt(pcm.size)
        RandomAccessFile(file, "rw").use { out ->
            out.write(header.array())
            out.write(pcm)
        }
    }
}
