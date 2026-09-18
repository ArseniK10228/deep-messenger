package online.deepdesign.deep.ui.chat

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import online.deepdesign.deep.data.MessageDto
import online.deepdesign.deep.data.resolveMediaUrl
import online.deepdesign.deep.data.OperatorAccess
import online.deepdesign.deep.ui.components.AnimatedChatStatus
import online.deepdesign.deep.ui.components.ChatAvatar
import online.deepdesign.deep.ui.components.TypingBubbleIndicator
import online.deepdesign.deep.ui.components.VoiceWaveform
import online.deepdesign.deep.ui.components.deepAppear
import online.deepdesign.deep.ui.util.rememberDismissKeyboard
import online.deepdesign.deep.ui.theme.DeepAccent
import online.deepdesign.deep.ui.theme.DeepBg
import online.deepdesign.deep.ui.theme.DeepError
import online.deepdesign.deep.ui.theme.DeepMuted
import online.deepdesign.deep.ui.theme.DeepSurface
import online.deepdesign.deep.ui.theme.DeepSurfaceHigh
import online.deepdesign.deep.ui.theme.DeepText

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    title: String,
    onBack: () -> Unit,
    onStartCall: () -> Unit = {},
    onStartVideoCall: () -> Unit = {},
    vm: ChatViewModel = viewModel(factory = ChatViewModel.factory(conversationId))
) {
    val state by vm.state.collectAsState()
    val showPresence = true
    val showPeerTelemetry = OperatorAccess.canViewPresence
    val listState = rememberLazyListState()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current
    val imeBottomPx = WindowInsets.ime.getBottom(density)
    var showAttach by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<MessageDto?>(null) }
    var mediaViewer by remember { mutableStateOf<MessageDto?>(null) }
    val attachSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val deleteSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val dismissKeyboard = rememberDismissKeyboard()

    fun leaveChat() {
        dismissKeyboard()
        onBack()
    }

    BackHandler { leaveChat() }

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let(vm::uploadUri)
        showAttach = false
    }

    val filePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let(vm::uploadUri)
        showAttach = false
    }

    var pendingCallAction by remember { mutableStateOf<(() -> Unit)?>(null) }

    val voiceMicPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startRecording()
    }

    val videoNotePermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val mic = results[Manifest.permission.RECORD_AUDIO] == true
        val cam = results[Manifest.permission.CAMERA] == true
        when {
            mic && cam -> vm.startVideoNoteRecording()
            !cam -> vm.showError("Нужен доступ к камере для кружка")
            else -> vm.showError("Нужен доступ к микрофону для кружка")
        }
    }

    fun requestVideoNote() {
        val mic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        val cam = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        when {
            mic && cam -> vm.startVideoNoteRecording()
            else -> videoNotePermissions.launch(
                arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CAMERA)
            )
        }
    }

    val callMicPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingCallAction?.invoke()
        else vm.showError("Нужен доступ к микрофону для звонка")
        pendingCallAction = null
    }

    val videoCallPermissions = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        val mic = results[Manifest.permission.RECORD_AUDIO] == true
        val cam = results[Manifest.permission.CAMERA] == true
        when {
            !mic -> vm.showError("Нужен доступ к микрофону для видеозвонка")
            !cam -> vm.showError("Нужен доступ к камере для видеозвонка")
            else -> pendingCallAction?.invoke()
        }
        pendingCallAction = null
    }

    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) pendingCallAction?.invoke()
        else vm.showError("Нужен доступ к камере для видеозвонка")
        pendingCallAction = null
    }

    fun requestCall() {
        dismissKeyboard()
        if (online.deepdesign.deep.call.CallPermissions.hasMic(context)) onStartCall()
        else {
            pendingCallAction = onStartCall
            callMicPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    fun requestVideoCall() {
        dismissKeyboard()
        val missing = online.deepdesign.deep.call.CallPermissions.missingForVideo(context)
        when {
            missing.isEmpty() -> onStartVideoCall()
            missing.size > 1 -> {
                pendingCallAction = onStartVideoCall
                videoCallPermissions.launch(missing)
            }
            missing[0] == Manifest.permission.RECORD_AUDIO -> {
                pendingCallAction = { requestVideoCall() }
                callMicPermission.launch(Manifest.permission.RECORD_AUDIO)
            }
            else -> {
                pendingCallAction = onStartVideoCall
                cameraPermission.launch(Manifest.permission.CAMERA)
            }
        }
    }

    LaunchedEffect(state.loading, state.messages.size) {
        if (!state.loading && state.messages.isNotEmpty()) {
            listState.scrollToItem(state.messages.lastIndex)
        }
    }

    LaunchedEffect(state.messages.lastOrNull()?.id) {
        if (state.messages.isEmpty()) return@LaunchedEffect
        val nearBottom = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index
            ?.let { it >= state.messages.lastIndex - 1 } != false
        if (nearBottom) {
            listState.scrollToItem(state.messages.lastIndex)
        }
    }

    fun chatScrollTargetIndex(): Int? = when {
        state.peerTyping -> if (state.messages.isEmpty()) 0 else state.messages.size
        state.messages.isNotEmpty() -> state.messages.lastIndex
        else -> null
    }

    fun isNearChatBottom(): Boolean {
        val lastContent = if (state.messages.isEmpty()) 0 else state.messages.lastIndex
        val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: return true
        return lastVisible >= lastContent - 1
    }

    LaunchedEffect(state.peerTyping) {
        if (!state.peerTyping) return@LaunchedEffect
        kotlinx.coroutines.delay(32)
        val target = chatScrollTargetIndex() ?: return@LaunchedEffect
        if (isNearChatBottom()) {
            listState.animateScrollToItem(target)
        }
    }

    val inputBarColor = DeepBg.copy(alpha = 0.94f)

    // Скролл вниз синхронно с анимацией клавиатуры (на каждый кадр IME inset).
    LaunchedEffect(imeBottomPx, state.messages.size, state.peerTyping) {
        if (imeBottomPx <= 0) return@LaunchedEffect
        val target = chatScrollTargetIndex() ?: return@LaunchedEffect
        if (isNearChatBottom()) {
            listState.scrollToItem(target)
        }
    }

    Scaffold(
        containerColor = DeepBg,
        contentWindowInsets = WindowInsets.statusBars,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChatAvatar(name = title, size = 40.dp, online = showPresence && state.peerOnline)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                title,
                                color = DeepText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (showPresence) {
                                AnimatedChatStatus(
                                    online = state.peerOnline,
                                    lastSeenAt = state.peerLastSeenAt,
                                    typing = state.peerTyping
                                )
                                state.peerAppVersion?.takeIf { showPeerTelemetry }?.let { version ->
                                    Text(
                                        text = "v$version",
                                        color = DeepMuted,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                                state.peerClientState?.takeIf { showPeerTelemetry }?.let { clientState ->
                                    Text(
                                        text = clientState,
                                        color = DeepMuted,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            } else if (state.peerTyping) {
                                AnimatedChatStatus(
                                    online = false,
                                    lastSeenAt = null,
                                    typing = true
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = { leaveChat() }) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = DeepText
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { requestVideoCall() }) {
                        Icon(Icons.Default.Videocam, contentDescription = "Видеозвонок", tint = DeepAccent)
                    }
                    IconButton(onClick = { requestCall() }) {
                        Icon(Icons.Default.Call, contentDescription = "Звонок", tint = DeepAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DeepBg.copy(alpha = 0.92f)
                )
            )
        },
        bottomBar = {
            if (!state.recording && !state.videoNoteRecording) {
                Surface(
                    color = inputBarColor,
                    shadowElevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.fillMaxWidth()) {
                    state.pendingAttachment?.let { pending ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(DeepSurfaceHigh)
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                Icons.Default.AttachFile,
                                contentDescription = null,
                                tint = DeepAccent,
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    pending.fileName,
                                    color = DeepText,
                                    style = MaterialTheme.typography.bodyMedium,
                                    maxLines = 1
                                )
                                pending.sizeBytes?.let { sz ->
                                    Text(
                                        formatAttachSize(sz),
                                        color = DeepMuted,
                                        style = MaterialTheme.typography.labelSmall
                                    )
                                }
                            }
                            IconButton(onClick = vm::clearPendingAttachment) {
                                Icon(Icons.Default.Close, contentDescription = "Убрать", tint = DeepMuted)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .windowInsetsPadding(
                                WindowInsets.ime
                                    .union(WindowInsets.navigationBars)
                                    .only(WindowInsetsSides.Bottom)
                            )
                            .padding(horizontal = 6.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { showAttach = true },
                            enabled = !state.uploading
                        ) {
                            Icon(Icons.Default.AttachFile, contentDescription = "Вложение", tint = DeepMuted)
                        }
                        OutlinedTextField(
                            modifier = Modifier.weight(1f),
                            value = state.input,
                            onValueChange = vm::onInputChange,
                            placeholder = { Text("Сообщение", color = DeepMuted) },
                            maxLines = 4,
                            shape = RoundedCornerShape(20.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                            keyboardActions = KeyboardActions(onSend = { vm.send() }),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = DeepAccent,
                                unfocusedBorderColor = DeepSurfaceHigh,
                                focusedContainerColor = DeepSurfaceHigh,
                                unfocusedContainerColor = DeepSurfaceHigh,
                                cursorColor = DeepAccent
                            )
                        )
                        if (state.input.isBlank() && state.pendingAttachment == null) {
                            IconButton(
                                onClick = { requestVideoNote() },
                                enabled = !state.uploading
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(28.dp)
                                        .clip(CircleShape)
                                        .background(DeepAccent.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Videocam,
                                        contentDescription = "Видеокружок",
                                        tint = DeepAccent,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                            IconButton(
                                onClick = {
                                    val granted = ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.RECORD_AUDIO
                                    ) == PackageManager.PERMISSION_GRANTED
                                    if (granted) vm.startRecording()
                                    else voiceMicPermission.launch(Manifest.permission.RECORD_AUDIO)
                                },
                                enabled = !state.uploading
                            ) {
                                Icon(Icons.Default.Mic, contentDescription = "Голосовое", tint = DeepAccent)
                            }
                        } else {
                            IconButton(
                                onClick = vm::send,
                                enabled = !state.uploading
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Отправить",
                                    tint = DeepAccent
                                )
                            }
                        }
                        if (state.uploading) {
                            CircularProgressIndicator(
                                modifier = Modifier
                                    .padding(start = 4.dp)
                                    .size(22.dp),
                                strokeWidth = 2.dp,
                                color = DeepAccent
                            )
                        }
                    }
                    }
                }
            }
        }
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color(0xFF12101A),
                            DeepBg,
                            Color(0xFF0A0810)
                        )
                    )
                )
        ) {
            when {
                state.loading -> {
                    CircularProgressIndicator(
                        color = DeepAccent,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.messages.isEmpty() && !state.recording && !state.peerTyping -> {
                    Text(
                        text = "Напиши первое сообщение",
                        color = DeepMuted,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .deepAppear()
                    )
                }
                else -> {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 12.dp,
                            top = 8.dp,
                            end = 12.dp,
                            bottom = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(
                            items = state.messages,
                            key = { msg -> state.messageKeys[msg.id] ?: msg.id }
                        ) { msg ->
                            MessageBubble(
                                msg = msg,
                                mine = vm.isMine(msg),
                                onLongClick = { deleteTarget = msg },
                                onOpenMedia = { mediaViewer = it }
                            )
                        }
                        item(key = "peer_typing") {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .animateContentSize(
                                        animationSpec = tween(260, easing = FastOutSlowInEasing)
                                    )
                            ) {
                                if (state.peerTyping) {
                                    TypingBubbleIndicator(
                                        asMessageBubble = true,
                                        compact = true,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (state.recording) {
                RecordingOverlay(
                    onCancel = vm::cancelRecording,
                    onSend = vm::stopRecordingAndSend
                )
            }

            if (state.videoNoteRecording) {
                VideoNoteRecordingOverlay(
                    durationMs = state.videoNoteDurationMs,
                    locked = state.videoNoteLocked,
                    onPreviewView = { preview ->
                        vm.onVideoNotePreviewReady(preview, lifecycleOwner)
                    },
                    onFlipCamera = vm::flipVideoNoteCamera,
                    onCancel = vm::cancelVideoNoteRecording,
                    onSend = vm::stopVideoNoteAndSend,
                    onLock = vm::lockVideoNoteRecording
                )
            }

            state.error?.let {
                Text(
                    text = it,
                    color = DeepError,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 8.dp)
                        .background(DeepSurface.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }

    if (showAttach) {
        ModalBottomSheet(
            onDismissRequest = { showAttach = false },
            sheetState = attachSheet,
            containerColor = DeepSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text("Вложение", color = DeepText, style = MaterialTheme.typography.titleLarge)
                TextButton(
                    onClick = { imagePicker.launch("image/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Image, contentDescription = null, tint = DeepAccent)
                    Text("  Фото", color = DeepText, modifier = Modifier.weight(1f))
                }
                TextButton(
                    onClick = { filePicker.launch("*/*") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AttachFile, contentDescription = null, tint = DeepAccent)
                    Text("  Файл", color = DeepText, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    deleteTarget?.let { msg ->
        ModalBottomSheet(
            onDismissRequest = { deleteTarget = null },
            sheetState = deleteSheet,
            containerColor = DeepSurface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
                    .padding(bottom = 32.dp)
            ) {
                Text("Удалить сообщение?", color = DeepText, style = MaterialTheme.typography.titleLarge)
                TextButton(
                    onClick = {
                        vm.deleteMessage(msg, "me")
                        deleteTarget = null
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Удалить у меня", color = DeepError, modifier = Modifier.weight(1f))
                }
                if (vm.canDeleteForEveryone(msg)) {
                    TextButton(
                        onClick = {
                            vm.deleteMessage(msg, "everyone")
                            deleteTarget = null
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Удалить у всех", color = DeepError, modifier = Modifier.weight(1f))
                    }
                }
                TextButton(
                    onClick = { deleteTarget = null },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Отмена", color = DeepMuted, modifier = Modifier.weight(1f))
                }
            }
        }
    }

    mediaViewer?.let { msg ->
        ChatFileViewerSheet(
            visible = true,
            title = msg.body?.takeIf { it.isNotBlank() } ?: if (msg.kind == "image") "Фото" else "Файл",
            url = resolveMediaUrl(msg.mediaUrl),
            mimeHint = msg.mediaMime,
            onDismiss = { mediaViewer = null }
        )
    }
}

private fun formatAttachSize(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return "${bytes / 1024} KB"
    return String.format("%.1f MB", bytes / (1024.0 * 1024.0))
}

@Composable
private fun RecordingOverlay(onCancel: () -> Unit, onSend: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.72f)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .padding(32.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(DeepSurface.copy(alpha = 0.95f))
                .padding(horizontal = 28.dp, vertical = 32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(DeepError.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Mic, contentDescription = null, tint = DeepError, modifier = Modifier.size(36.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text("Голосовое сообщение", color = DeepText, style = MaterialTheme.typography.titleMedium)
            Text("Отпусти кнопку отправки", color = DeepMuted, style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(20.dp))
            VoiceWaveform(
                seed = "recording",
                progress = 1f,
                playing = true,
                activeColor = DeepAccent,
                inactiveColor = DeepMuted.copy(alpha = 0.3f),
                modifier = Modifier.fillMaxWidth(0.85f)
            )
            Row(
                modifier = Modifier.padding(top = 28.dp),
                horizontalArrangement = Arrangement.spacedBy(32.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = onCancel,
                        containerColor = DeepSurfaceHigh,
                        contentColor = DeepText,
                        modifier = Modifier.size(56.dp)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = "Отмена")
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Отмена", color = DeepMuted, style = MaterialTheme.typography.labelSmall)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    FloatingActionButton(
                        onClick = onSend,
                        containerColor = DeepAccent,
                        contentColor = DeepText,
                        shape = CircleShape,
                        modifier = Modifier.size(64.dp)
                    ) {
                        Icon(Icons.Default.Stop, contentDescription = "Отправить", modifier = Modifier.size(28.dp))
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Отправить", color = DeepMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
