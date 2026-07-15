package online.deepdesign.deep.ui.chats

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import online.deepdesign.deep.data.ConversationDto
import online.deepdesign.deep.data.UserDto
import online.deepdesign.deep.ui.components.deepAppear
import online.deepdesign.deep.ui.theme.DeepAccent
import online.deepdesign.deep.ui.theme.DeepBg
import online.deepdesign.deep.ui.theme.DeepError
import online.deepdesign.deep.ui.theme.DeepMuted
import online.deepdesign.deep.ui.theme.DeepSurface
import online.deepdesign.deep.ui.theme.DeepSurfaceHigh
import online.deepdesign.deep.ui.theme.DeepText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsScreen(
    onOpenChat: (conversationId: String, title: String) -> Unit,
    vm: ChatsViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    Scaffold(
        containerColor = DeepBg,
        topBar = {
            TopAppBar(
                title = {
                    Text("Deep", color = DeepAccent, fontWeight = FontWeight.Bold)
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBg)
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { vm.toggleNewChat(true) },
                containerColor = DeepAccent,
                contentColor = DeepText,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Новый чат")
            }
        }
    ) { padding ->
        PullToRefreshBox(
            isRefreshing = state.loading && state.conversations.isNotEmpty(),
            onRefresh = vm::refresh,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                state.loading && state.conversations.isEmpty() -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = DeepAccent)
                    }
                }
                state.conversations.isEmpty() -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(32.dp)
                            .deepAppear(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Пока нет чатов", color = DeepText, style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "Нажми + чтобы найти контакт по номеру",
                            color = DeepMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(vertical = 8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(state.conversations, key = { _, c -> c.id }) { index, conv ->
                            ConversationRow(
                                modifier = Modifier.deepAppear(delayMillis = index * 40),
                                title = vm.peerTitle(conv),
                                preview = vm.previewText(conv),
                                time = formatTime(conv.lastMessage?.createdAt),
                                onClick = { onOpenChat(conv.id, vm.peerTitle(conv)) }
                            )
                        }
                    }
                }
            }
        }

        state.error?.let {
            Text(
                text = it,
                color = DeepError,
                modifier = Modifier.padding(16.dp)
            )
        }
    }

    if (state.showNewChat) {
        ModalBottomSheet(
            onDismissRequest = { vm.toggleNewChat(false) },
            sheetState = sheetState,
            containerColor = DeepSurface
        ) {
            NewChatSheet(
                query = state.searchQuery,
                onQueryChange = vm::onSearchQueryChange,
                searching = state.searching,
                results = state.searchResults,
                onPick = { user -> vm.startChatWithUser(user, onOpenChat) },
                onClose = { vm.toggleNewChat(false) }
            )
        }
    }
}

@Composable
private fun ConversationRow(
    title: String,
    preview: String,
    time: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(DeepSurfaceHigh),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = title.firstOrNull()?.uppercase() ?: "?",
                color = DeepAccent,
                style = MaterialTheme.typography.titleMedium
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Text(
                text = title,
                color = DeepText,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = preview,
                color = DeepMuted,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        time?.let {
            Text(text = it, color = DeepMuted, style = MaterialTheme.typography.labelMedium)
        }
    }
}

@Composable
private fun NewChatSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean,
    results: List<UserDto>,
    onPick: (UserDto) -> Unit,
    onClose: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp)
            .padding(bottom = 32.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Новый чат", color = DeepText, style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = DeepMuted)
            }
        }
        OutlinedTextField(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            value = query,
            onValueChange = onQueryChange,
            label = { Text("Email или имя") },
            placeholder = { Text("user@mail.ru") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
            shape = RoundedCornerShape(16.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = DeepAccent,
                unfocusedBorderColor = DeepSurfaceHigh,
                focusedContainerColor = DeepSurfaceHigh,
                unfocusedContainerColor = DeepSurfaceHigh,
                cursorColor = DeepAccent
            )
        )
        if (searching) {
            CircularProgressIndicator(
                color = DeepAccent,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp)
            )
        } else if (query.trim().length >= 3 && results.isEmpty()) {
            Text("Никого не нашли", color = DeepMuted, modifier = Modifier.padding(8.dp))
        }
        results.forEach { user ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onPick(user) }
                    .padding(vertical = 12.dp, horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = user.displayName.ifBlank {
                        user.email.orEmpty().ifBlank { user.phone }
                    },
                    color = DeepText,
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = user.email.orEmpty().ifBlank { user.phone },
                    color = DeepMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

private fun formatTime(iso: String?): String? {
    if (iso.isNullOrBlank()) return null
    return try {
        val dt = Instant.parse(iso).atZone(ZoneId.systemDefault())
        val now = java.time.LocalDate.now()
        val day = dt.toLocalDate()
        when {
            day == now -> DateTimeFormatter.ofPattern("HH:mm").format(dt)
            day == now.minusDays(1) -> "вчера"
            else -> DateTimeFormatter.ofPattern("dd.MM").format(dt)
        }
    } catch (_: Exception) {
        null
    }
}
