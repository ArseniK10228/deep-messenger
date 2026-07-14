package online.deepdesign.deep.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import online.deepdesign.deep.data.MessageDto
import online.deepdesign.deep.ui.components.deepAppear
import online.deepdesign.deep.ui.theme.DeepAccent
import online.deepdesign.deep.ui.theme.DeepBg
import online.deepdesign.deep.ui.theme.DeepBubbleIn
import online.deepdesign.deep.ui.theme.DeepBubbleOut
import online.deepdesign.deep.ui.theme.DeepError
import online.deepdesign.deep.ui.theme.DeepMuted
import online.deepdesign.deep.ui.theme.DeepSurfaceHigh
import online.deepdesign.deep.ui.theme.DeepText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    conversationId: String,
    title: String,
    onBack: () -> Unit,
    vm: ChatViewModel = viewModel(
        factory = ChatViewModel.factory(conversationId)
    )
) {
    val state by vm.state.collectAsState()
    val listState = rememberLazyListState()

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty()) {
            listState.animateScrollToItem(state.messages.lastIndex)
        }
    }

    Scaffold(
        containerColor = DeepBg,
        topBar = {
            TopAppBar(
                title = { Text(title, color = DeepText) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Назад",
                            tint = DeepText
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBg)
            )
        },
        bottomBar = {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DeepBg)
                    .imePadding()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
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
                IconButton(
                    onClick = vm::send,
                    enabled = state.input.isNotBlank() && !state.sending,
                    modifier = Modifier.padding(start = 4.dp)
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
                            tint = if (state.input.isNotBlank()) DeepAccent else DeepMuted
                        )
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
                state.messages.isEmpty() -> {
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
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 12.dp,
                            vertical = 8.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(state.messages, key = { it.id }) { msg ->
                            MessageBubble(msg = msg, mine = vm.isMine(msg))
                        }
                    }
                }
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
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun MessageBubble(msg: MessageDto, mine: Boolean) {
    val bg = if (mine) DeepBubbleOut else DeepBubbleIn
    val align = if (mine) Alignment.CenterEnd else Alignment.CenterStart
    val shape = RoundedCornerShape(
        topStart = 18.dp,
        topEnd = 18.dp,
        bottomStart = if (mine) 18.dp else 4.dp,
        bottomEnd = if (mine) 4.dp else 18.dp
    )

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = align) {
        Column(
            modifier = Modifier
                .widthIn(max = 300.dp)
                .clip(shape)
                .background(bg)
                .padding(horizontal = 14.dp, vertical = 10.dp)
                .deepAppear(durationMillis = 260)
        ) {
            Text(
                text = when (msg.kind) {
                    "text" -> msg.body.orEmpty()
                    "image" -> "📷 Фото"
                    "voice" -> "🎤 Голосовое"
                    else -> "📎 Файл"
                },
                color = DeepText,
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(Modifier.size(4.dp))
            Text(
                text = formatMessageTime(msg.createdAt),
                color = DeepMuted,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

private fun formatMessageTime(iso: String): String {
    return try {
        val dt = Instant.parse(iso).atZone(ZoneId.systemDefault())
        DateTimeFormatter.ofPattern("HH:mm").format(dt)
    } catch (_: Exception) {
        ""
    }
}
