package online.deepdesign.deep.data

import android.content.Context
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import java.util.concurrent.Executor
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class VideoNoteRecorder(private val context: Context) {
    private val mainExecutor: Executor = ContextCompat.getMainExecutor(context)
    private var cameraProvider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var preview: Preview? = null
    private var recording: Recording? = null
    private var outputFile: File? = null
    private var startedAt = 0L
    private var lensFacing = CameraSelector.LENS_FACING_FRONT

    val isRecording: Boolean
        get() = recording != null

    suspend fun bindPreview(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        val provider = getCameraProvider(context)
        cameraProvider = provider
        bindUseCases(provider, previewView, lifecycleOwner)
    }

    fun switchCamera(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_FRONT) {
            CameraSelector.LENS_FACING_BACK
        } else {
            CameraSelector.LENS_FACING_FRONT
        }
        val provider = cameraProvider ?: return
        bindUseCases(provider, previewView, lifecycleOwner)
    }

    private fun bindUseCases(
        provider: ProcessCameraProvider,
        previewView: PreviewView,
        lifecycleOwner: LifecycleOwner
    ) {
        provider.unbindAll()
        val previewUseCase = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
            preview = it
        }
        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.SD))
            .build()
        val capture = VideoCapture.withOutput(recorder)
        videoCapture = capture
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        provider.bindToLifecycle(lifecycleOwner, selector, previewUseCase, capture)
    }

    fun start(): File {
        val capture = videoCapture ?: throw IllegalStateException("Camera not ready")
        val file = File.createTempFile("video_note_", ".mp4", context.cacheDir)
        outputFile = file
        startedAt = System.currentTimeMillis()
        val options = FileOutputOptions.Builder(file).build()
        recording = capture.output
            .prepareRecording(context, options)
            .withAudioEnabled()
            .start(mainExecutor) { }
        return file
    }

    fun stop(): Pair<File, Long>? {
        val active = recording ?: return null
        val file = outputFile
        recording = null
        outputFile = null
        return try {
            active.stop()
            if (file == null || !file.exists() || file.length() < 1024) {
                file?.delete()
                null
            } else {
                val duration = (System.currentTimeMillis() - startedAt).coerceAtLeast(500L)
                file to duration
            }
        } catch (_: Exception) {
            file?.delete()
            null
        } finally {
            startedAt = 0L
        }
    }

    fun cancel() {
        runCatching { recording?.stop() }
        recording = null
        outputFile?.delete()
        outputFile = null
        startedAt = 0L
    }

    fun release() {
        cancel()
        cameraProvider?.unbindAll()
        cameraProvider = null
        videoCapture = null
        preview = null
    }

    private suspend fun getCameraProvider(context: Context): ProcessCameraProvider =
        suspendCoroutine { cont ->
            val future = ProcessCameraProvider.getInstance(context)
            future.addListener({
                try {
                    cont.resume(future.get())
                } catch (e: Exception) {
                    cont.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
}
