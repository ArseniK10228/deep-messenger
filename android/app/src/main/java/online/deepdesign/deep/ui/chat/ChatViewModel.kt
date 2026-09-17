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
import online.deepdesign.deep.AppForegroundState
import online.deepdesign.deep.data.ChatEvent
import online.deepdesign.deep.data.ChatNotifier
import online.deepdesign.deep.data.ChatSocket
import online.deepdesign.deep.data.DeleteMessageRequest
import online.deepdesign.deep.data.MediaUploader
import online.deepdesign.deep.data.MessageDto
import online.deepdesign.deep.data.PickedFile
import online.deepdesign.deep.data.PresenceStore
import online.deepdesign.deep.data.ClientStateDto
import online.deepdesign.deep.data.OperatorAccess
import online.deepdesign.deep.data.SendMessageRequest
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import online.deepdesign.deep.data.VideoNoteRecorder
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
    val videoNoteRecording: Boolean = false,
    val videoNoteDurationMs: Long = 0,
    val videoNoteLocked: Boolean = false,
    val error: String? = null,
    val peerTyping: Boolean = false,
    val peerOnline: Boolean = false,
    val peerLastSeenAt: String? = null,
    val peerAppVersion: String? = null,
    val peerClientState: String? = null,
    val messageKeys: Map<String, String> = emptyMap()
)

class ChatViewModel(
    private val conversationId: String
) : ViewModel() {
    private val api = DeepApp.instance.api
    private val draftStore = DeepApp.instance.chatDraftStore
    private val socket = ChatSocket { DeepApp.instance.currentToken() }
    private val voiceRecorder = VoiceRecorder(DeepApp.instance)
    private val videoNoteRecorder = VideoNoteRecorder(DeepApp.instance)
    private var videoNoteTickJob: Job? = null
    private var boundPreview: PreviewView? = null
    private var boundLifecycle: LifecycleOwner? = null
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
        viewModelScope.launch {
            runCatching { api.markConversationRead(conversationId) }
        }
        loadDraft()
        loadMessages()
        loadPeer()
        viewModelScope.launch {
            AppForegroundState.foreground.collectLatest { fg ->
                if (fg) connectWs() else disconnectWs()
            }
        }
        viewModelScope.launch {
            PresenceStore.users.collectLatest { applyPeerPresence() }
        }
        viewModelScope.launch {
            ChatNotifier.events.collect { event ->
                if (event is ChatEvent.MessageStatus && event.conversationId == conversationId) {
                    updateMessageStatus(
                        event.messageId,
                        peerDelivered = event.peerDelivered,
                        peerRead = event.peerRead
                    )
                } else if (event is ChatEvent.RefreshChats) {
                    loadPeer()
                }
            }
        }
        viewModelScope.launch {
            while (true) {
                delay(45_000)
                loadPeer()
            }
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
                applyPeerPresence(
                    peer.appVersionName,
                    if (OperatorAccess.canViewPresence) formatClientState(peer.clientState) else null
                )
            }
        }
    }

    private fun applyPeerPresence(peerAppVersion: String? = _state.value.peerAppVersion, peerClientState: String? = _state.value.peerClientState) {
        val peerId = peerUserId ?: return
        val (online, lastSeen) = PresenceStore.peerOnline(peerId, peerApiOnline, peerApiLastSeen)
        _state.update {
            it.copy(
                peerOnline = online,
                peerLastSeenAt = lastSeen,
                peerAppVersion = if (OperatorAccess.canViewPresence) peerAppVersion ?: it.peerAppVersion else null,
                peerClientState = if (OperatorAccess.canViewPresence) peerClientState ?: it.peerClientState else null
            )
        }
    }

    private fun formatClientState(state: ClientStateDto?): String? {
        state ?: return null
        val parts = mutableListOf<String>()
        parts += if (state.foreground == true) "на экране" else "в фоне"
        state.batteryPct?.let { parts += "$it%" }
        state.network?.let { parts += it }
        if (state.inCall == true) parts += "в звонке"
        return parts.joinToString(" · ")
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
        if (wsJob?.isActive == true) return
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

    private fun disconnectWs() {
        wsJob?.cancel()
        wsJob = null
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

    fun startVideoNoteRecording() {
        if (_state.value.videoNoteRecording || _state.value.recording || _state.value.uploading) return
        _state.update {
            it.copy(
                videoNoteRecording = true,
                videoNoteLocked = false,
                videoNoteDurationMs = 0,
                error = null
            )
        }
    }

    fun onVideoNotePreviewReady(previewView: PreviewView, lifecycleOwner: LifecycleOwner) {
        if (!_state.value.videoNoteRecording) return
        boundPreview = previewView
        boundLifecycle = lifecycleOwner
        viewModelScope.launch {
            try {
                videoNoteRecorder.bindPreview(previewView, lifecycleOwner)
                if (!videoNoteRecorder.isRecording) {
                    videoNoteRecorder.start()
                    startVideoNoteTicker()
                }
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message, videoNoteRecording = false) }
                videoNoteRecorder.release()
            }
        }
    }

    fun flipVideoNoteCamera() {
        val preview = boundPreview ?: return
        val lifecycle = boundLifecycle ?: return
        videoNoteRecorder.switchCamera(preview, lifecycle)
    }

    fun lockVideoNoteRecording() {
        _state.update { it.copy(videoNoteLocked = true) }
    }

    fun cancelVideoNoteRecording() {
        if (!_state.value.videoNoteRecording) return
        videoNoteTickJob?.cancel()
        videoNoteRecorder.cancel()
        videoNoteRecorder.release()
        boundPreview = null
        boundLifecycle = null
        _state.update {
            it.copy(videoNoteRecording = false, videoNoteLocked = false, videoNoteDurationMs = 0)
        }
    }

    fun stopVideoNoteAndSend() {
        if (!_state.value.videoNoteRecording) return
        videoNoteTickJob?.cancel()
        _state.update { it.copy(videoNoteRecording = false, videoNoteLocked = false) }
        val result = videoNoteRecorder.stop()
        videoNoteRecorder.release()
        boundPreview = null
        boundLifecycle = null
        if (result == null) {
            _state.update { it.copy(videoNoteDurationMs = 0) }
            return
        }
        val (file, durationMs) = result
        viewModelScope.launch {
            _state.update { it.copy(uploading = true, error = null) }
            try {
                val bytes = file.readBytes()
                file.delete()
                val msg = MediaUploader.upload(
                    conversationId = conversationId,
                    fileName = "video_note.mp4",
                    mimeType = "video/mp4",
                    bytes = bytes,
                    durationMs = durationMs,
                    videoNote = true
                )
                appendMessage(msg)
            } catch (e: Exception) {
                _state.update { it.copy(error = e.message) }
            } finally {
                _state.update { it.copy(uploading = false, videoNoteDurationMs = 0) }
            }
        }
    }

    private fun startVideoNoteTicker() {
        videoNoteTickJob?.cancel()
        val started = System.currentTimeMillis()
        videoNoteTickJob = viewModelScope.launch {
            while (_state.value.videoNoteRecording) {
                val elapsed = System.currentTimeMillis() - started
                _state.update { it.copy(videoNoteDurationMs = elapsed) }
                if (elapsed >= 60_000L) {
                    stopVideoNoteAndSend()
                    break
                }
                delay(100)
            }
        }
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
            viewModelScope.launch {
                ackDelivered(msg.id)
                runCatching { api.markRead(msg.id) }
            }
        }
    }

    private suspend fun ackDelivered(messageId: String) {
        socket.sendDelivered(messageId)
        DeepApp.instance.signalingHub.sendDelivered(messageId)
        runCatching { api.markDelivered(messageId) }
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
        if (msgs.none { !isMine(it) }) return
        viewModelScope.launch {
            runCatching { api.markConversationRead(conversationId) }
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
        videoNoteTickJob?.cancel()
        videoNoteRecorder.release()
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
