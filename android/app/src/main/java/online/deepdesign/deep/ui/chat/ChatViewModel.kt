package online.deepdesign.deep.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.data.ChatSocket
import online.deepdesign.deep.data.MediaUploader
import online.deepdesign.deep.data.MessageDto
import online.deepdesign.deep.data.PickedFile
import online.deepdesign.deep.data.SendMessageRequest
import online.deepdesign.deep.data.VoiceRecorder
import online.deepdesign.deep.data.readPickedFile

data class ChatUiState(
    val loading: Boolean = true,
    val messages: List<MessageDto> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val uploading: Boolean = false,
    val recording: Boolean = false,
    val error: String? = null,
    val peerTyping: Boolean = false
)

class ChatViewModel(
    private val conversationId: String
) : ViewModel() {
    private val api = DeepApp.instance.api
    private val signaling = DeepApp.instance.signalingHub
    private val socket = ChatSocket { DeepApp.instance.currentToken() }
    private val voiceRecorder = VoiceRecorder(DeepApp.instance)
    private var wsJob: Job? = null
    private var receiptJob: Job? = null

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        loadMessages()
        connectWs()
        listenReceipts()
    }

    private fun loadMessages() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val msgs = api.messages(conversationId).messages
                _state.update { it.copy(loading = false, messages = msgs) }
                markIncomingRead(msgs)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    private fun connectWs() {
        wsJob?.cancel()
        wsJob = viewModelScope.launch {
            socket.events(conversationId).collect { event ->
                when (event.type) {
                    "message" -> event.message?.let { onIncomingMessage(it) }
                    "message_deleted" -> event.messageId?.let { removeMessage(it) }
                    "typing" -> _state.update { it.copy(peerTyping = true) }
                    "message_delivered" -> event.messageId?.let {
                        updateMessageStatus(it, peerDelivered = true)
                    }
                    "message_read" -> event.messageId?.let {
                        updateMessageStatus(it, peerDelivered = true, peerRead = true)
                    }
                }
            }
        }
    }

    private fun listenReceipts() {
        receiptJob?.cancel()
        receiptJob = viewModelScope.launch {
            signaling.events.collect { event ->
                if (event.conversationId != null && event.conversationId != conversationId) return@collect
                when (event.type) {
                    "message_delivered" -> event.messageId?.let {
                        updateMessageStatus(it, peerDelivered = true)
                    }
                    "message_read" -> event.messageId?.let {
                        updateMessageStatus(it, peerDelivered = true, peerRead = true)
                    }
                    "message" -> event.message?.takeIf { it.conversationId == conversationId }
                        ?.let { onIncomingMessage(it) }
                }
            }
        }
    }

    fun onInputChange(v: String) {
        _state.update { it.copy(input = v) }
    }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.sending || _state.value.uploading) return
        viewModelScope.launch {
            _state.update { it.copy(sending = true, input = "") }
            try {
                val msg = api.sendMessage(
                    conversationId,
                    SendMessageRequest(kind = "text", body = text)
                ).message
                appendMessage(msg)
            } catch (e: Exception) {
                _state.update { it.copy(sending = false, input = text, error = e.message) }
            } finally {
                _state.update { it.copy(sending = false) }
            }
        }
    }

    fun uploadUri(uri: Uri) {
        if (_state.value.uploading || _state.value.sending) return
        viewModelScope.launch {
            _state.update { it.copy(uploading = true, error = null) }
            try {
                val picked = readPickedFile(DeepApp.instance, uri)
                    ?: throw IllegalStateException("Не удалось прочитать файл")
                uploadPicked(picked)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(uploading = false) }
            }
        }
    }

    fun uploadPicked(picked: PickedFile) {
        if (_state.value.uploading) return
        viewModelScope.launch {
            _state.update { it.copy(uploading = true, error = null) }
            try {
                val msg = MediaUploader.upload(
                    conversationId = conversationId,
                    fileName = picked.fileName,
                    mimeType = picked.mimeType,
                    bytes = picked.bytes
                )
                appendMessage(msg)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(uploading = false) }
            }
        }
    }

    fun startRecording() {
        if (_state.value.recording || _state.value.uploading) return
        try {
            voiceRecorder.start()
            _state.update { it.copy(recording = true, error = null) }
        } catch (e: Exception) {
            _state.update { it.copy(error = e.message ?: "Нет доступа к микрофону") }
        }
    }

    fun stopRecordingAndSend() {
        if (!_state.value.recording) return
        _state.update { it.copy(recording = false) }
        val result = voiceRecorder.stop() ?: return
        val (file, durationMs) = result
        viewModelScope.launch {
            _state.update { it.copy(uploading = true, error = null) }
            try {
                val bytes = file.readBytes()
                file.delete()
                val msg = MediaUploader.upload(
                    conversationId = conversationId,
                    fileName = "voice.m4a",
                    mimeType = "audio/mp4",
                    bytes = bytes,
                    durationMs = durationMs
                )
                appendMessage(msg)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(uploading = false) }
            }
        }
    }

    fun cancelRecording() {
        if (!_state.value.recording) return
        voiceRecorder.cancel()
        _state.update { it.copy(recording = false) }
    }

    private fun onIncomingMessage(msg: MessageDto) {
        appendMessage(msg)
        if (!isMine(msg)) {
            socket.sendDelivered(msg.id)
            signaling.sendDelivered(msg.id)
            viewModelScope.launch { api.markRead(msg.id) }
        }
    }

    private fun appendMessage(msg: MessageDto) {
        _state.update { s ->
            if (s.messages.any { it.id == msg.id }) s
            else s.copy(messages = s.messages + msg)
        }
    }

    private fun markIncomingRead(msgs: List<MessageDto>) {
        viewModelScope.launch {
            msgs.filter { !isMine(it) }.forEach { api.markRead(it.id) }
        }
    }

    private fun updateMessageStatus(messageId: String, peerDelivered: Boolean, peerRead: Boolean = false) {
        _state.update { s ->
            s.copy(
                messages = s.messages.map { m ->
                    if (m.id != messageId) m
                    else m.copy(
                        peerDelivered = peerDelivered || m.peerDelivered == true,
                        peerRead = peerRead || m.peerRead == true
                    )
                }
            )
        }
    }

    private fun removeMessage(id: String) {
        _state.update { s -> s.copy(messages = s.messages.filterNot { it.id == id }) }
    }

    fun isMine(msg: MessageDto): Boolean = msg.senderId == DeepApp.instance.currentUserId

    override fun onCleared() {
        voiceRecorder.cancel()
        wsJob?.cancel()
        receiptJob?.cancel()
        super.onCleared()
    }

    companion object {
        fun factory(conversationId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(conversationId) as T
                }
            }
    }
}
