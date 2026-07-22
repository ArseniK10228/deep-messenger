package online.deepdesign.deep.ui.admin

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.TabRowDefaults
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import online.deepdesign.deep.DeepApp
import online.deepdesign.deep.data.CallRecordingDto
import online.deepdesign.deep.data.ConversationDto
import online.deepdesign.deep.data.MessageDto
import online.deepdesign.deep.data.UserDto
import online.deepdesign.deep.data.resolveMediaUrl
import online.deepdesign.deep.ui.components.ChatAvatar
import online.deepdesign.deep.ui.components.deepAppear
import online.deepdesign.deep.ui.theme.DeepAccent
import online.deepdesign.deep.ui.theme.DeepBg
import online.deepdesign.deep.ui.theme.DeepMuted
import online.deepdesign.deep.ui.theme.DeepSurface
import online.deepdesign.deep.ui.theme.DeepSurfaceHigh
import online.deepdesign.deep.ui.theme.DeepText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminScreen(
    onBack: () -> Unit,
    vm: AdminViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val voicePlayer = DeepApp.instance.voicePlayer
    val voiceState by voicePlayer.state.collectAsState()

    Scaffold(
        containerColor = DeepBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (val dest = state.destination) {
                            is AdminDestination.Home -> "Панель"
                            is AdminDestination.User -> state.selectedUser?.let { userLabel(it) } ?: "Пользователь"
                            is AdminDestination.Chat -> dest.title
                        },
                        color = DeepText,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        if (state.destination is AdminDestination.Home) onBack() else vm.back()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад", tint = DeepText)
                    }
                },
                actions = {
                    IconButton(onClick = vm::refreshAll) {
                        Icon(Icons.Default.Refresh, contentDescription = "Обновить", tint = DeepMuted)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = DeepBg)
            )
        }
    ) { padding ->
        AnimatedContent(
            targetState = state.destination,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            transitionSpec = {
                (slideInHorizontally(tween(280, easing = FastOutSlowInEasing)) { it / 4 } + fadeIn(tween(220))) togetherWith
                    (slideOutHorizontally(tween(220)) { -it / 4 } + fadeOut(tween(180)))
            },
            label = "adminNav"
        ) { dest ->
            when (dest) {
                is AdminDestination.Home -> AdminHome(
                    state = state,
                    onTab = vm::setTab,
                    onUser = vm::openUser,
                    onRefresh = vm::refreshAll,
                    onPlayRecording = { rec ->
                        val url = resolveMediaUrl(rec.mediaUrl) ?: return@AdminHome
                        voicePlayer.toggle(rec.id, url)
                    },
                    playingId = voiceState.messageId,
                    isPlaying = voiceState.playing,
                    loadingId = if (voiceState.loading) voiceState.messageId else null
                )
                is AdminDestination.User -> AdminUserDetail(
                    state = state,
                    onChat = vm::openChat,
                    onDiag = vm::requestDiag
                )
                is AdminDestination.Chat -> AdminChatViewer(
                    messages = state.chatMessages,
                    loading = state.chatLoading,
                    currentUserId = state.selectedUser?.id
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AdminHome(
    state: AdminUiState,
    onTab: (Int) -> Unit,
    onUser: (String) -> Unit,
    onRefresh: () -> Unit,
    onPlayRecording: (CallRecordingDto) -> Unit,
    playingId: String?,
    isPlaying: Boolean,
    loadingId: String?
) {
    Column(Modifier.fillMaxSize()) {
        TabRow(
            selectedTabIndex = state.tab,
            containerColor = DeepBg,
            contentColor = DeepAccent,
            indicator = { positions ->
                if (state.tab < positions.size) {
                    TabRowDefaults.SecondaryIndicator(
                        modifier = Modifier.tabIndicatorOffset(positions[state.tab]),
                        color = DeepAccent
                    )
                }
            }
        ) {
            Tab(selected = state.tab == 0, onClick = { onTab(0) }, text = { Text("Пользователи") })
            Tab(selected = state.tab == 1, onClick = { onTab(1) }, text = { Text("Записи") })
        }

        PullToRefreshBox(
            isRefreshing = state.refreshing,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when (state.tab) {
                0 -> {
                    if (state.loading && state.users.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DeepAccent)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(state.users, key = { _, u -> u.id }) { index, user ->
                                AdminUserCard(
                                    modifier = Modifier.deepAppear(delayMillis = index * 25),
                                    user = user,
                                    nowMs = state.nowMs,
                                    onClick = { onUser(user.id) }
                                )
                            }
                        }
                    }
                }
                1 -> {
                    if (state.recordings.isEmpty() && state.loading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = DeepAccent)
                        }
                    } else if (state.recordings.isEmpty()) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("Записей звонков пока нет", color = DeepMuted)
                        }
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            itemsIndexed(state.recordings, key = { _, r -> r.id }) { index, rec ->
                                RecordingRow(
                                    modifier = Modifier.deepAppear(delayMillis = index * 25),
                                    recording = rec,
                                    playing = playingId == rec.id && isPlaying,
                                    loading = loadingId == rec.id,
                                    onPlay = { onPlayRecording(rec) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminUserCard(
    user: UserDto,
    nowMs: Long,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(18.dp),
        color = DeepSurface
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            ChatAvatar(
                name = userLabel(user),
                size = 48.dp,
                online = user.online == true,
                inCall = userHasActiveCall(user)
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(userLabel(user), color = DeepText, fontWeight = FontWeight.SemiBold)
                user.username?.let {
                    Text("@$it", color = DeepMuted, style = MaterialTheme.typography.labelMedium)
                }
                Text(
                    formatUserPresenceLine(user),
                    color = if (user.online == true) DeepAccent else DeepMuted,
                    style = MaterialTheme.typography.labelSmall
                )
                formatLastSeenAbsolute(user.lastSeenAt)?.let { absolute ->
                    if (user.online != true) {
                        Text(
                            absolute,
                            color = DeepMuted,
                            style = MaterialTheme.typography.labelSmall
                        )
                    }
                }
                formatClientState(user.clientState, user.activeCall, nowMs)?.let {
                    Text(
                        it,
                        color = if (userHasActiveCall(user)) DeepAccent else DeepMuted,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
                user.appVersionName?.let {
                    Text("v$it", color = DeepMuted, style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}

@Composable
private fun AdminUserDetail(
    state: AdminUiState,
    onChat: (String, String) -> Unit,
    onDiag: (String) -> Unit
) {
    val user = state.selectedUser
    if (user == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            if (state.loading) {
                CircularProgressIndicator(color = DeepAccent)
            } else {
                Text(state.error ?: "Не удалось загрузить", color = DeepMuted)
            }
        }
        return
    }

    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Surface(shape = RoundedCornerShape(18.dp), color = DeepSurface) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(userLabel(user), color = DeepText, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    user.email?.let { Text(it, color = DeepMuted) }
                    user.phone.takeIf { it.isNotBlank() }?.let { Text(it, color = DeepMuted) }
                    Text(
                        formatUserPresenceLine(user),
                        color = if (user.online == true) DeepAccent else DeepMuted
                    )
                    formatLastSeenAbsolute(user.lastSeenAt)?.let { absolute ->
                        if (user.online != true) {
                            Text(absolute, color = DeepMuted)
                        }
                    }
                    formatClientState(user.clientState, user.activeCall, state.nowMs)?.let {
                        Text(
                            it,
                            color = if (userHasActiveCall(user)) DeepAccent else DeepMuted
                        )
                    }
                    user.appVersionName?.let { Text("Версия v$it", color = DeepMuted) }
                    TextButton(
                        onClick = { onDiag(user.id) },
                        enabled = state.diagPending != user.id
                    ) {
                        if (state.diagPending == user.id) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = DeepAccent, strokeWidth = 2.dp)
                        } else {
                            Text("Обновить статус с телефона", color = DeepAccent)
                        }
                    }
                }
            }
        }

        item {
            Text("Переписки", color = DeepText, fontWeight = FontWeight.SemiBold)
        }

        itemsIndexed(state.userConversations, key = { _, c -> c.id }) { index, conv ->
            AdminConversationRow(
                modifier = Modifier.deepAppear(delayMillis = index * 20),
                conversation = conv,
                onClick = {
                    val title = conv.peers?.firstOrNull()?.let { userLabel(it) } ?: "Чат"
                    onChat(conv.id, title)
                }
            )
        }
    }
}

@Composable
private fun AdminConversationRow(
    conversation: ConversationDto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val peer = conversation.peers?.firstOrNull()
    val preview = conversation.lastMessage?.body
        ?: conversation.lastMessage?.kind?.let { "[$it]" }
        ?: "Нет сообщений"
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = DeepSurfaceHigh.copy(alpha = 0.55f)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            ChatAvatar(name = peer?.let { userLabel(it) } ?: "?", size = 42.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    peer?.let { userLabel(it) } ?: conversation.id.take(8),
                    color = DeepText,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(preview, color = DeepMuted, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
private fun AdminChatViewer(
    messages: List<MessageDto>,
    loading: Boolean,
    currentUserId: String?
) {
    if (loading && messages.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = DeepAccent)
        }
        return
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        itemsIndexed(messages, key = { _, m -> m.id }) { index, msg ->
            AdminMessageBubble(
                modifier = Modifier.deepAppear(delayMillis = (index % 12) * 15),
                message = msg,
                mine = msg.senderId == currentUserId
            )
        }
    }
}

@Composable
private fun AdminMessageBubble(
    message: MessageDto,
    mine: Boolean,
    modifier: Modifier = Modifier
) {
    val align = if (mine) Alignment.End else Alignment.Start
    val bg = if (mine) DeepAccent.copy(alpha = 0.25f) else DeepSurface
    Column(modifier.fillMaxWidth(), horizontalAlignment = align) {
        Surface(shape = RoundedCornerShape(16.dp), color = bg) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                val text = when (message.kind) {
                    "text" -> message.body.orEmpty()
                    "voice" -> "🎤 голосовое"
                    "image" -> "🖼 фото"
                    else -> message.body ?: "[${message.kind}]"
                }
                Text(text, color = DeepText)
                Text(
                    formatMessageTime(message.createdAt),
                    color = DeepMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

@Composable
private fun RecordingRow(
    recording: CallRecordingDto,
    playing: Boolean,
    loading: Boolean,
    onPlay: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = DeepSurface
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = DeepAccent,
                        strokeWidth = 2.dp
                    )
                } else {
                    IconButton(onClick = onPlay) {
                        Icon(
                            if (playing) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = null,
                            tint = DeepAccent
                        )
                    }
                }
            }
            Column(Modifier.weight(1f)) {
                Text(
                    recording.title ?: formatRecordingTitle(recording),
                    color = DeepText,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    buildString {
                        append(formatDuration(recording.durationMs))
                        if (recording.video == true) append(" · видео")
                        recording.startedAt?.let {
                            append(" · ")
                            append(formatMessageTime(it))
                        }
                    },
                    color = DeepMuted,
                    style = MaterialTheme.typography.labelSmall
                )
            }
        }
    }
}

private fun formatRecordingTitle(recording: CallRecordingDto): String {
    val caller = recording.callerName
        ?: recording.callerUsername?.let { "@$it" }
    val callee = recording.calleeName
        ?: recording.calleeUsername?.let { "@$it" }
    return when {
        caller != null && callee != null -> "$caller → $callee"
        caller != null -> caller
        callee != null -> callee
        recording.uploadedByName != null -> "Запись: ${recording.uploadedByName}"
        else -> "Звонок"
    }
}

private fun formatMessageTime(iso: String?): String {
    if (iso.isNullOrBlank()) return ""
    return runCatching {
        val dt = Instant.parse(iso).atZone(ZoneId.systemDefault())
        DateTimeFormatter.ofPattern("dd.MM HH:mm").format(dt)
    }.getOrDefault(iso)
}
