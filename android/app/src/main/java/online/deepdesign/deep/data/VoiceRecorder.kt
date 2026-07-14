package online.deepdesign.deep.data

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import java.io.File

class VoiceRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null
    private var startedAt = 0L

    val isRecording: Boolean
        get() = recorder != null

    fun start(): File {
        stop()
        val file = File.createTempFile("voice_", ".m4a", context.cacheDir)
        val mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
        mediaRecorder.setAudioSamplingRate(44_100)
        mediaRecorder.setAudioEncodingBitRate(96_000)
        mediaRecorder.setOutputFile(file.absolutePath)
        mediaRecorder.prepare()
        mediaRecorder.start()
        recorder = mediaRecorder
        output = file
        startedAt = System.currentTimeMillis()
        return file
    }

    fun stop(): Pair<File, Long>? {
        val mediaRecorder = recorder ?: return null
        val file = output ?: return null
        return try {
            mediaRecorder.stop()
            val duration = (System.currentTimeMillis() - startedAt).coerceAtLeast(300L)
            file to duration
        } catch (_: Exception) {
            file.delete()
            null
        } finally {
            mediaRecorder.release()
            recorder = null
            output = null
            startedAt = 0L
        }
    }

    fun cancel() {
        val mediaRecorder = recorder
        recorder = null
        output?.delete()
        output = null
        startedAt = 0L
        try {
            mediaRecorder?.stop()
        } catch (_: Exception) { }
        mediaRecorder?.release()
    }
}
