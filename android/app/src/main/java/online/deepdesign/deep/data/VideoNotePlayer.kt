package online.deepdesign.deep.data

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

data class VideoNotePlayState(
    val messageId: String? = null,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val progress: Float = 0f,
    val error: Boolean = false
)

class VideoNotePlayer(context: Context) {
    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, "video_note_cache").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: ExoPlayer? = null
    private var progressJob: kotlinx.coroutines.Job? = null

    private val _state = MutableStateFlow(VideoNotePlayState())
    val state: StateFlow<VideoNotePlayState> = _state.asStateFlow()

    fun toggle(messageId: String, url: String) {
        if (_state.value.messageId == messageId && _state.value.playing) {
            pause()
            return
        }
        if (_state.value.messageId == messageId && _state.value.loading) return
        stop()
        _state.value = VideoNotePlayState(messageId = messageId, loading = true)
        scope.launch {
            val local = ensureCached(url)
            if (local == null) {
                _state.value = VideoNotePlayState(messageId = messageId, error = true)
                return@launch
            }
            playLocal(messageId, local)
        }
    }

    fun pause() {
        player?.pause()
        _state.value = _state.value.copy(playing = false, loading = false)
        progressJob?.cancel()
    }

    fun stop() {
        progressJob?.cancel()
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        _state.value = VideoNotePlayState()
    }

    fun activeExoPlayer(): ExoPlayer? = player

    private suspend fun ensureCached(url: String): File? = withContext(Dispatchers.IO) {
        val file = File(cacheDir, sha1(url) + ".mp4")
        if (file.exists() && file.length() > 1024) return@withContext file
        runCatching {
            val request = Request.Builder().url(url).build()
            ApiClient.okHttp { null }.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                file.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
            }
            if (file.length() > 1024) file else null
        }.getOrNull()
    }

    private fun playLocal(messageId: String, file: File) {
        runCatching {
            val exo = ExoPlayer.Builder(appContext).build()
            exo.setMediaItem(MediaItem.fromUri(file.toURI().toString()))
            exo.prepare()
            exo.playWhenReady = true
            exo.addListener(object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_ENDED) {
                        stop()
                    }
                }
            })
            player = exo
            _state.value = VideoNotePlayState(messageId = messageId, playing = true, loading = false)
            startProgressLoop(exo, messageId)
        }.onFailure {
            _state.value = VideoNotePlayState(messageId = messageId, error = true)
        }
    }

    private fun startProgressLoop(exo: ExoPlayer, messageId: String) {
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive && player == exo) {
                val duration = exo.duration.coerceAtLeast(1L)
                val pos = exo.currentPosition
                _state.value = _state.value.copy(
                    messageId = messageId,
                    playing = exo.isPlaying,
                    progress = (pos.toFloat() / duration).coerceIn(0f, 1f)
                )
                delay(50)
            }
        }
    }

    private fun sha1(text: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(text.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
