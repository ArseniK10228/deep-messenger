package online.deepdesign.deep.data

import android.content.Context
import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.File
import java.security.MessageDigest

data class VoicePlayState(
    val messageId: String? = null,
    val playing: Boolean = false,
    val loading: Boolean = false,
    val progress: Float = 0f,
    val error: Boolean = false
)

/** Single shared player with local cache — shows loading while buffering/downloading. */
class VoicePlayer(context: Context) {
    private val appContext = context.applicationContext
    private val cacheDir = File(appContext.cacheDir, "voice_cache").apply { mkdirs() }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null

    private val _state = MutableStateFlow(VoicePlayState())
    val state: StateFlow<VoicePlayState> = _state.asStateFlow()

    fun toggle(messageId: String, url: String) {
        if (_state.value.messageId == messageId && _state.value.playing) {
            pause()
            return
        }
        if (_state.value.messageId == messageId && _state.value.loading) return
        stop()
        _state.value = VoicePlayState(messageId = messageId, loading = true)
        scope.launch {
            val local = ensureCached(url)
            if (local == null) {
                _state.value = VoicePlayState(messageId = messageId, error = true)
                return@launch
            }
            playLocal(messageId, local)
        }
    }

    fun pause() {
        player?.pause()
        _state.value = _state.value.copy(playing = false, loading = false)
    }

    fun stop() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        _state.value = VoicePlayState()
    }

    private suspend fun ensureCached(url: String): File? = withContext(Dispatchers.IO) {
        val ext = when {
            url.contains(".wav", ignoreCase = true) -> ".wav"
            url.contains(".ogg", ignoreCase = true) -> ".ogg"
            url.contains(".m4a", ignoreCase = true) -> ".m4a"
            url.contains(".mp3", ignoreCase = true) -> ".mp3"
            else -> ".audio"
        }
        val file = File(cacheDir, sha1(url) + ext)
        if (file.exists() && file.length() > 32) return@withContext file
        runCatching {
            val request = Request.Builder().url(url).build()
            ApiClient.okHttp { null }.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@withContext null
                val body = response.body ?: return@withContext null
                file.outputStream().use { out -> body.byteStream().use { it.copyTo(out) } }
            }
            if (file.length() > 32) file else null
        }.getOrNull()
    }

    private fun playLocal(messageId: String, file: File) {
        runCatching {
            val mp = MediaPlayer()
            player = mp
            mp.setDataSource(file.absolutePath)
            mp.setOnPreparedListener {
                it.start()
                _state.value = VoicePlayState(messageId, playing = true, loading = false, progress = 0f)
                pollProgress(messageId, it)
            }
            mp.setOnCompletionListener { stop() }
            mp.setOnErrorListener { _, _, _ ->
                _state.value = VoicePlayState(messageId = messageId, error = true)
                stop()
                true
            }
            mp.prepareAsync()
        }.onFailure {
            _state.value = VoicePlayState(messageId = messageId, error = true)
            stop()
        }
    }

    private fun pollProgress(messageId: String, mp: MediaPlayer) {
        scope.launch {
            while (_state.value.messageId == messageId && mp.isPlaying) {
                val dur = mp.duration.coerceAtLeast(1)
                val pos = mp.currentPosition.coerceAtLeast(0)
                _state.value = _state.value.copy(
                    playing = true,
                    loading = false,
                    progress = (pos.toFloat() / dur).coerceIn(0f, 1f)
                )
                kotlinx.coroutines.delay(80)
            }
        }
    }

    private fun sha1(input: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(input.toByteArray())
        return digest.joinToString("") { "%02x".format(it) }
    }
}
