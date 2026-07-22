package online.deepdesign.deep.ui.chat

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.data.ChatSocket
import online.deepdesign.deep.data.DeleteMessageRequest
import online.deepdesign.deep.data.MediaUploader
import online.deepdesign.deep.data.MessageDto
import online.deepdesign.deep.data.PickedFile
import online.deepdesign.deep.data.PresenceStore
import online.deepdesign.deep.data.SendMessageRequest
import online.deepdesign.deep.data.VoiceRecorder
import online.deepdesign.deep.data.WsEnvelope
import online.deepdesign.deep.data.readPickedFile
import java.time.Duration
import java.time.Instant
import java.util.UUID

data class ChatUiState(
    val loading: Boolean = true,
    val messages: List<MessageDto> = emptyList(),
    val input: String = "",
    val sending: Boolean = false,
    val uploading: Boolean = false,
    val recording: Boolean = false,
    val error: String? = null,
    val peerTyping: Boolean = false,
    val peerOnline: Boolean = false,
    val peerLastSeenAt: String? = null,
    val messageKeys: Map<String, String> = emptyMap()
)

class ChatViewModel(
    private val conversationId: String
) : ViewModel() {
    private val api = DeepApp.instance.api
    private val draftStore = DeepApp.instance.chatDraftStore
    private val socket = ChatSocket { DeepApp.instance.currentToken() }
    private val voiceRecorder = VoiceRecorder(DeepApp.instance)
    private var peerUserId: String? = null
    private var peerApiOnline: Boolean = false
    private var peerApiLastSeen: String? = null
    private var wsJob: Job? = null
    private var typingJob: Job? = null
    private var draftSaveJob: Job? = null
    private var lastTypingSentAt = 0L

    private val _state = MutableStateFlow(ChatUiState())
    val state: StateFlow<ChatUiState> = _state.asStateFlow()

    init {
        loadDraft()
        loadMessages()
        loadPeer()
        connectWs()
        viewModelScope.launch {
            PresenceStore.users.collectLatest { applyPeerPresence() }
        }
    }

    private fun loadDraft() {
        viewModelScope.launch {
            runCatching {
                val draft = draftStore.getDraft(conversationId)
                if (draft.isNotBlank()) {
                    _state.update { it.copy(input = draft) }
                }
            }
        }
    }

    private fun persistDraft(text: String) {
        draftSaveJob?.cancel()
        draftSaveJob = viewModelScope.launch {
            delay(200)
            runCatching { draftStore.saveDraft(conversationId, text) }
        }
    }

    private fun loadPeer() {
        viewModelScope.launch {
            runCatching {
                val peer = api.conversationPeer(conversationId).peer
                peerUserId = peer.id
                peerApiOnline = peer.online == true
                peerApiLastSeen = peer.lastSeenAt
                PresenceStore.setFromApi(peer.id, peer.online, peer.lastSeenAt)
                applyPeerPresence()
            }
        }
    }

    private fun applyPeerPresence() {
        val peerId = peerUserId ?: return
        val (online, lastSeen) = PresenceStore.peerOnline(peerId, peerApiOnline, peerApiLastSeen)
        _state.update {
            it.copy(peerOnline = online, peerLastSeenAt = lastSeen)
        }
    }

    private fun loadMessages() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true, error = null) }
            try {
                val msgs = api.messages(conversationId).messages
                _state.update { s ->
                    s.copy(loading = false, messages = mergeMessages(s.messages, msgs))
                }
                markIncomingRead(msgs)
            } catch (e: Exception) {
                _state.update { it.copy(loading = false, error = e.message) }
            }
        }
    }

    private fun connectWs() {
        wsJob?.cancel()
        wsJob = viewModelScope.launch {
            while (true) {
                try {
                    socket.events(conversationId).collect { handleWsEvent(it) }
                } catch (e: CancellationException) {
                    throw e
                } catch (_: Exception) {
                    // reconnect below
                }
                delay(2_000)
            }
        }
    }

    private fun handleWsEvent(event: WsEnvelope) {
        when (event.type) {
            "message" -> event.message?.let { onIncomingMessage(it) }
            "message_deleted" -> event.messageId?.let { removeMessage(it) }
            "typing" -> {
                _state.update { it.copy(peerTyping = true) }
                typingJob?.cancel()
                typingJob = viewModelScope.launch {
                    delay(3_000)
                    _state.update { it.copy(peerTyping = false) }
                }
            }
            "presence" -> {
                val userId = event.userId ?: return
                if (userId == peerUserId) {
                    PresenceStore.update(userId, event.online == true, event.lastSeenAt)
                    applyPeerPresence()
                }
            }
            "message_delivered" -> event.messageId?.let {
                updateMessageStatus(it, peerDelivered = true)
            }
            "message_read" -> event.messageId?.let {
                updateMessageStatus(it, peerDelivered = true, peerRead = true)
            }
        }
    }

    fun onInputChange(v: String) {
        _state.update { it.copy(input = v) }
        persistDraft(v)
        if (v.isNotBlank()) {
            val now = System.currentTimeMillis()
            if (now - lastTypingSentAt >= 2_000) {
                lastTypingSentAt = now
                socket.sendTyping(conversationId)
            }
        }
    }

    fun showError(message: String) {
        _state.update { it.copy(error = message) }
    }

    fun send() {
        val text = _state.value.input.trim()
        if (text.isEmpty() || _state.value.uploading) return
        val userId = DeepApp.instance.currentUserId ?: return

        val stableKey = UUID.randomUUID().toString()
        val clientId = "pending:$stableKey"
        val optimistic = MessageDto(
            id = clientId,
            conversationId = conversationId,
            senderId = userId,
            kind = "text",
            body = text,
            mediaUrl = null,
            createdAt = Instant.now().toString()
        )

        viewModelScope.launch {
            _state.update {
                it.copy(
                    input = "",
                    messageKeys = it.messageKeys + (clientId to stableKey)
                )
            }
            runCatching { draftStore.saveDraft(conversationId, "") }
            appendMessage(optimistic)
            try {
                val msg = api.sendMessage(
                    conversationId,
                    SendMessageRequest(kind = "text", body = text)
                ).message
                replacePendingMessage(clientId, msg, stableKey)
            } catch (e: Exception) {
                removeMessage(clientId)
                _state.update {
                    it.copy(
                        input = text,
                        error = e.message,
                        messageKeys = it.messageKeys - clientId
                    )
                }
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
        if (msg.conversationId != conversationId) return
        if (isMine(msg)) {
            val pending = _state.value.messages.findLast {
                it.id.startsWith("pending:") && it.body == msg.body
            }
            if (pending != null) {
                val stableKey = _state.value.messageKeys[pending.id]
                replacePendingMessage(pending.id, msg, stableKey)
                return
            }
        }
        val added = appendMessage(msg)
        if (added && !isMine(msg)) {
            socket.sendDelivered(msg.id)
            viewModelScope.launch { runCatching { api.markRead(msg.id) } }
        }
    }

    private fun replacePendingMessage(clientId: String, msg: MessageDto, stableKey: String?) {
        _state.update { s ->
            val keys = s.messageKeys.toMutableMap()
            keys.remove(clientId)
            if (stableKey != null) keys[msg.id] = stableKey
            s.copy(
                messages = sortMessages(
                    s.messages.filterNot { it.id == clientId || it.id == msg.id } + msg
                ),
                messageKeys = keys
            )
        }
    }

    private fun appendMessage(msg: MessageDto): Boolean {
        var added = false
        _state.update { s ->
            if (s.messages.any { it.id == msg.id }) s
            else {
                added = true
                s.copy(messages = sortMessages(s.messages + msg))
            }
        }
        return added
    }

    private fun mergeMessages(existing: List<MessageDto>, incoming: List<MessageDto>): List<MessageDto> {
        return sortMessages(existing + incoming)
    }

    private fun sortMessages(msgs: List<MessageDto>): List<MessageDto> {
        return msgs
            .distinctBy { it.id }
            .sortedWith(
                compareBy<MessageDto> { parseCreatedAt(it.createdAt) }
                    .thenBy { it.id }
            )
    }

    private fun parseCreatedAt(iso: String): Instant {
        return try {
            Instant.parse(iso)
        } catch (_: Exception) {
            Instant.EPOCH
        }
    }

    private fun markIncomingRead(msgs: List<MessageDto>) {
        viewModelScope.launch {
            msgs.filter { !isMine(it) }.forEach { runCatching { api.markRead(it.id) } }
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
        _state.update { s ->
            s.copy(
                messages = s.messages.filterNot { it.id == id },
                messageKeys = s.messageKeys - id
            )
        }
    }

    fun isMine(msg: MessageDto): Boolean = msg.senderId == DeepApp.instance.currentUserId

    fun canDeleteForEveryone(msg: MessageDto): Boolean {
        if (!isMine(msg)) return false
        return try {
            val created = Instant.parse(msg.createdAt)
            Duration.between(created, Instant.now()).toHours() < DELETE_FOR_EVERYONE_HOURS
        } catch (_: Exception) {
            false
        }
    }

    fun deleteMessage(msg: MessageDto, scope: String) {
        viewModelScope.launch {
            try {
                api.deleteMessage(msg.id, DeleteMessageRequest(scope))
                removeMessage(msg.id)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message ?: "Не удалось удалить") }
            }
        }
    }

    override fun onCleared() {
        val draft = _state.value.input
        draftSaveJob?.cancel()
        voiceRecorder.cancel()
        wsJob?.cancel()
        typingJob?.cancel()
        DeepApp.instance.saveChatDraft(conversationId, draft)
        super.onCleared()
    }

    companion object {
        private const val DELETE_FOR_EVERYONE_HOURS = 48L

        fun factory(conversationId: String): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ChatViewModel(conversationId) as T
                }
            }
    }
}
