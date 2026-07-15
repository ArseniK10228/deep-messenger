package online.deepdesign.deep.ui.chat

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
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
import androidx.lifecycle.viewmodel.compose.viewModel
import online.deepdesign.deep.data.MessageDto
import online.deepdesign.deep.ui.components.ChatAvatar
import online.deepdesign.deep.ui.components.VoiceWaveform
import online.deepdesign.deep.ui.components.deepAppear
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
    vm: ChatViewModel = viewModel(factory = ChatViewModel.factory(conversationId))
) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()
    val context = LocalContext.current
    var showAttach by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<MessageDto?>(null) }
    val attachSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val deleteSheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)

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

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) vm.startRecording()
    }

    val callPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) onStartCall()
    }

    fun requestCall() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) onStartCall()
        else callPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    LaunchedEffect(state.messages.lastOrNull()?.id) {
        val last = state.messages.lastIndex
        if (last < 0) return@LaunchedEffect
        val visible = listState.layoutInfo.visibleItemsInfo
        val atBottom = visible.isEmpty() || visible.lastOrNull()?.index?.let { it >= last - 1 } == true
        if (atBottom) listState.animateScrollToItem(last)
    }

    Scaffold(
        containerColor = DeepBg,
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        ChatAvatar(name = title, size = 40.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                title,
                                color = DeepText,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (state.peerTyping) "печатает…" else "личный чат",
                                color = if (state.peerTyping) DeepAccent else DeepMuted,
                                style = MaterialTheme.typography.labelSmall
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = DeepText
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { requestCall() }) {
                        Icon(Icons.Default.Call, contentDescription = "Звонок", tint = DeepAccent)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBg)
            )
        },
        bottomBar = {
            if (!state.recording) {
                Surface(
                    color = DeepSurface,
                    shadowElevation = 8.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .imePadding()
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
                    if (state.input.isBlank()) {
                        IconButton(
                            onClick = {
                                val granted = ContextCompat.checkSelfPermission(
                                    context,
                                    Manifest.permission.RECORD_AUDIO
                                ) == android.content.pm.PackageManager.PERMISSION_GRANTED
                                if (granted) vm.startRecording()
                                else micPermission.launch(Manifest.permission.RECORD_AUDIO)
                            },
                            enabled = !state.uploading && !state.sending
                        ) {
                            Icon(Icons.Default.Mic, contentDescription = "Голосовое", tint = DeepAccent)
                        }
                    } else {
                        IconButton(
                            onClick = vm::send,
                            enabled = !state.sending && !state.uploading
                        ) {
                            if (state.sending) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(22.dp),
                                    strokeWidth = 2.dp,
                                    color = DeepAccent
                                )
                            } else {
                                Icon(
                                    Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Отправить",
                                    tint = DeepAccent
                                )
                            }
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
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.loading -> {
                    CircularProgressIndicator(
                        color = DeepAccent,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                state.messages.isEmpty() && !state.recording -> {
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
                            bottom = if (state.peerTyping) 36.dp else 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.messages, key = { it.id }) { msg ->
                            MessageBubble(
                                msg = msg,
                                mine = vm.isMine(msg),
                                onLongClick = { deleteTarget = msg }
                            )
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

            if (state.peerTyping) {
                Text(
                    text = "печатает…",
                    color = DeepMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(16.dp)
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
