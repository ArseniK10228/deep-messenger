package online.deepdesign.deep.call

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File
import java.time.Instant

class CallRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAtMs: Long = 0L

    fun start(): Boolean {
        if (recorder != null) return true
        return runCatching {
            val file = File(context.cacheDir, "call_${System.currentTimeMillis()}.m4a")
            outputFile = file
            startedAtMs = System.currentTimeMillis()
            val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            mediaRecorder.setAudioSamplingRate(44100)
            mediaRecorder.setAudioEncodingBitRate(96000)
            mediaRecorder.setOutputFile(file.absolutePath)
            mediaRecorder.prepare()
            mediaRecorder.start()
            recorder = mediaRecorder
            true
        }.getOrDefault(false)
    }

    fun stop(): File? {
        val file = outputFile
        runCatching { recorder?.stop() }
        runCatching { recorder?.release() }
        recorder = null
        outputFile = null
        return file?.takeIf { it.exists() && it.length() > 0L }
    }

    fun startedAtIso(): String? {
        if (startedAtMs <= 0L) return null
        return Instant.ofEpochMilli(startedAtMs).toString()
    }

    fun durationMs(): Long {
        if (startedAtMs <= 0L) return 0L
        return (System.currentTimeMillis() - startedAtMs).coerceAtLeast(0L)
    }

    fun resetTiming() {
        startedAtMs = 0L
    }
}
