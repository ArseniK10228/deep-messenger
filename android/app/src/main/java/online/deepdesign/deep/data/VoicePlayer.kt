package online.deepdesign.deep.data

import android.media.MediaPlayer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class VoicePlayState(
    val messageId: String? = null,
    val playing: Boolean = false,
    val progress: Float = 0f
)

/** Single shared player — avoids multiple MediaPlayer instances crashing each other. */
class VoicePlayer {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var player: MediaPlayer? = null

    private val _state = MutableStateFlow(VoicePlayState())
    val state: StateFlow<VoicePlayState> = _state.asStateFlow()

    fun toggle(messageId: String, url: String) {
        if (_state.value.messageId == messageId && _state.value.playing) {
            pause()
            return
        }
        stop()
        scope.launch(Dispatchers.IO) {
            try {
                val mp = MediaPlayer()
                player = mp
                mp.setDataSource(url)
                mp.setOnPreparedListener {
                    it.start()
                    _state.value = VoicePlayState(messageId, playing = true, progress = 0f)
                    pollProgress(messageId, it)
                }
                mp.setOnCompletionListener { stop() }
                mp.prepareAsync()
            } catch (_: Exception) {
                stop()
            }
        }
    }

    fun pause() {
        player?.pause()
        _state.value = _state.value.copy(playing = false)
    }

    fun stop() {
        runCatching {
            player?.stop()
            player?.release()
        }
        player = null
        _state.value = VoicePlayState()
    }

    private fun pollProgress(messageId: String, mp: MediaPlayer) {
        scope.launch {
            while (_state.value.messageId == messageId && mp.isPlaying) {
                val dur = mp.duration.coerceAtLeast(1)
                val pos = mp.currentPosition.coerceAtLeast(0)
                _state.value = _state.value.copy(
                    playing = true,
                    progress = (pos.toFloat() / dur).coerceIn(0f, 1f)
                )
                kotlinx.coroutines.delay(80)
            }
        }
    }
}
