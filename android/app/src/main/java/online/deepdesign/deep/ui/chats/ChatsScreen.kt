package online.deepdesign.deep.ui.chats

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import android.Manifest
import android.os.Build
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import online.deepdesign.deep.data.AppReleaseDto
import online.deepdesign.deep.data.ConversationDto
import online.deepdesign.deep.data.OperatorAccess
import online.deepdesign.deep.data.UserDto
import online.deepdesign.deep.session.PowerSettings
import androidx.compose.ui.res.stringResource
import online.deepdesign.deep.R
import online.deepdesign.deep.update.ApkInstaller
import online.deepdesign.deep.update.AppUpdateDialog
import online.deepdesign.deep.update.UpdateChecker
import online.deepdesign.deep.ui.components.ChatAvatar
import online.deepdesign.deep.ui.components.TypingBubbleIndicator
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
    onOpenAdmin: () -> Unit = {},
    vm: ChatsViewModel = viewModel()
) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var listFilter by remember { mutableStateOf("") }
    var updateRelease by remember { mutableStateOf<AppReleaseDto?>(null) }
    var showUpdate by remember { mutableStateOf(false) }
    var downloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableFloatStateOf(0f) }
    var showBatteryBanner by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val profileSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS)
                != android.content.pm.PackageManager.PERMISSION_GRANTED
            ) {
                notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        showBatteryBanner = PowerSettings.needsBackgroundSetup(context)
        val release = UpdateChecker.fetchRelease()
        if (release != null && UpdateChecker.needsUpdate(release)) {
            updateRelease = release
            showUpdate = true
        }
    }

    if (showUpdate && updateRelease != null) {
        val release = updateRelease!!
        AppUpdateDialog(
            release = release,
            downloading = downloading,
            progress = downloadProgress,
            onDismiss = { if (release.forceUpdate != true) showUpdate = false },
            onUpdate = {
                scope.launch {
                    if (!ApkInstaller.canInstallPackages(context)) {
                        ApkInstaller.openInstallPermissionSettings(context)
                        return@launch
                    }
                    downloading = true
                    downloadProgress = 0f
                    try {
                        val apk = UpdateChecker.downloadApk(context, release.apkUrl) {
                            downloadProgress = it
                        }
                        ApkInstaller.install(context, apk)
                    } catch (_: Exception) {
                        downloading = false
                    }
                }
            }
        )
    }

    Scaffold(
        containerColor = DeepBg,
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Deep",
                        color = DeepAccent,
                        fontWeight = FontWeight.Bold,
                        modifier = if (OperatorAccess.canViewPresence) {
                            Modifier.pointerInput(Unit) {
                                detectTapGestures(onLongPress = { onOpenAdmin() })
                            }
                        } else {
                            Modifier
                        }
                    )
                },
                actions = {
                    IconButton(onClick = { vm.toggleProfile(true) }) {
                        Icon(Icons.Default.Person, contentDescription = "Профиль", tint = DeepMuted)
                    }
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
        val filtered = remember(state.conversations, listFilter) {
            val q = listFilter.trim().lowercase()
            if (q.isBlank()) state.conversations
            else state.conversations.filter { conv ->
                val title = vm.peerTitle(conv).lowercase()
                val preview = vm.previewText(conv).lowercase()
                title.contains(q) || preview.contains(q)
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Column(Modifier.fillMaxSize()) {
                if (showBatteryBanner) {
                    BackgroundSetupBanner(
                        onConfigure = { PowerSettings.requestIgnoreBatteryOptimizations(context) },
                        onDismiss = { showBatteryBanner = false }
                    )
                }
                PullToRefreshBox(
                    isRefreshing = state.loading && state.conversations.isNotEmpty(),
                    onRefresh = vm::refresh,
                    modifier = Modifier.fillMaxSize()
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
                            "Нажми + чтобы найти по @username или email",
                            color = DeepMuted,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                else -> {
                    Column(modifier = Modifier.fillMaxSize()) {
                        OutlinedTextField(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            value = listFilter,
                            onValueChange = { listFilter = it },
                            placeholder = { Text("Поиск по чатам", color = DeepMuted) },
                            leadingIcon = {
                                Icon(Icons.Default.Search, contentDescription = null, tint = DeepMuted)
                            },
                            trailingIcon = {
                                if (listFilter.isNotBlank()) {
                                    IconButton(onClick = { listFilter = "" }) {
                                        Icon(Icons.Default.Close, contentDescription = "Очистить", tint = DeepMuted)
                                    }
                                }
                            },
                            singleLine = true,
                            shape = RoundedCornerShape(20.dp),
                            colors = outlinedFieldColors()
                        )
                        if (filtered.isEmpty()) {
                            Text(
                                "Ничего не нашли",
                                color = DeepMuted,
                                modifier = Modifier.padding(24.dp)
                            )
                        } else {
                            LazyColumn(
                                contentPadding = PaddingValues(bottom = 88.dp),
                                modifier = Modifier.fillMaxSize()
                            ) {
                                itemsIndexed(filtered, key = { _, c -> c.id }) { index, conv ->
                                    val (online, _) = vm.peerPresence(conv)
                                    val peerTyping = vm.isPeerTyping(conv.id)
                                    ConversationRow(
                                        modifier = Modifier.deepAppear(delayMillis = index * 30),
                                        title = vm.peerTitle(conv),
                                        preview = vm.previewText(conv),
                                        peerTyping = peerTyping,
                                        time = formatTime(conv.lastMessage?.createdAt),
                                        online = OperatorAccess.canViewPresence && online,
                                        onClick = { onOpenChat(conv.id, vm.peerTitle(conv)) }
                                    )
                                    HorizontalDivider(
                                        color = DeepSurfaceHigh.copy(alpha = 0.5f),
                                        modifier = Modifier.padding(start = 78.dp)
                                    )
                                }
                            }
                        }
                    }
                }
                }
            }
            }

            state.error?.let {
                Text(
                    text = it,
                    color = DeepError,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier
                        .align(Alignment.TopCenter)
                        .padding(16.dp)
                        .background(DeepSurface.copy(alpha = 0.95f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 12.dp, vertical = 8.dp)
                )
            }
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
                searchError = state.searchError,
                results = state.searchResults,
                userTitle = vm::userTitle,
                userSubtitle = vm::userSubtitle,
                onPick = { user -> vm.startChatWithUser(user, onOpenChat) },
                onClose = { vm.toggleNewChat(false) }
            )
        }
    }

    if (state.showProfile) {
        ModalBottomSheet(
            onDismissRequest = { vm.toggleProfile(false) },
            sheetState = profileSheetState,
            containerColor = DeepSurface
        ) {
            ProfileSheet(
                displayName = state.profileDisplayName,
                username = state.profileUsername,
                saving = state.profileSaving,
                loading = state.profileLoading,
                error = state.profileError,
                onDisplayNameChange = vm::onProfileDisplayNameChange,
                onUsernameChange = vm::onProfileUsernameChange,
                onSave = vm::saveProfile,
                onClose = { vm.toggleProfile(false) }
            )
        }
    }
}

@Composable
private fun ConversationRow(
    title: String,
    preview: String,
    peerTyping: Boolean,
    time: String?,
    online: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ChatAvatar(name = title, size = 52.dp, online = online)
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    color = DeepText,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                time?.let {
                    Text(
                        text = it,
                        color = DeepMuted,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
            AnimatedContent(
                targetState = peerTyping,
                transitionSpec = {
                    (fadeIn(tween(240, easing = FastOutSlowInEasing)) +
                        scaleIn(
                            initialScale = 0.82f,
                            animationSpec = tween(240, easing = FastOutSlowInEasing)
                        )) togetherWith
                        (fadeOut(tween(180)) + scaleOut(targetScale = 0.94f))
                },
                label = "preview"
            ) { typing ->
                if (typing) {
                    TypingBubbleIndicator(compact = true)
                } else {
                    Text(
                        text = preview,
                        color = DeepMuted,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
private fun NewChatSheet(
    query: String,
    onQueryChange: (String) -> Unit,
    searching: Boolean,
    searchError: String?,
    results: List<UserDto>,
    userTitle: (UserDto) -> String,
    userSubtitle: (UserDto) -> String,
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
            label = { Text("@username, имя или email") },
            placeholder = { Text("nickname") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
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
        } else if (searchError != null) {
            Text(searchError, color = DeepError, modifier = Modifier.padding(8.dp))
        } else if (query.trim().length >= 2 && results.isEmpty()) {
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
                ChatAvatar(name = userTitle(user), size = 44.dp)
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 12.dp)
                ) {
                    Text(
                        text = userTitle(user),
                        color = DeepText,
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Text(
                        text = userSubtitle(user),
                        color = DeepMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun ProfileSheet(
    displayName: String,
    username: String,
    saving: Boolean,
    loading: Boolean,
    error: String?,
    onDisplayNameChange: (String) -> Unit,
    onUsernameChange: (String) -> Unit,
    onSave: () -> Unit,
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
            Text("Профиль", color = DeepText, style = MaterialTheme.typography.titleLarge)
            IconButton(onClick = onClose) {
                Icon(Icons.Default.Close, contentDescription = "Закрыть", tint = DeepMuted)
            }
        }
        Text(
            text = "Username нужен, чтобы тебя находили в поиске",
            color = DeepMuted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(bottom = 12.dp)
        )
        if (loading) {
            CircularProgressIndicator(
                color = DeepAccent,
                modifier = Modifier
                    .align(Alignment.CenterHorizontally)
                    .padding(16.dp)
            )
        } else {
            OutlinedTextField(
                modifier = Modifier.fillMaxWidth(),
                value = displayName,
                onValueChange = onDisplayNameChange,
                label = { Text("Имя") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = outlinedFieldColors()
            )
            OutlinedTextField(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                value = username,
                onValueChange = onUsernameChange,
                label = { Text("Username") },
                placeholder = { Text("nickname") },
                prefix = { Text("@", color = DeepMuted) },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = outlinedFieldColors()
            )
            error?.let {
                Text(it, color = DeepError, modifier = Modifier.padding(top = 8.dp))
            }
            Button(
                onClick = onSave,
                enabled = !saving,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = DeepAccent)
            ) {
                if (saving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(22.dp),
                        strokeWidth = 2.dp,
                        color = DeepText
                    )
                } else {
                    Text("Сохранить")
                }
            }
        }
    }
}

@Composable
private fun BackgroundSetupBanner(
    onConfigure: () -> Unit,
    onDismiss: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = DeepSurface
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    stringResource(R.string.battery_banner_title),
                    color = DeepText,
                    fontWeight = FontWeight.SemiBold,
                    style = MaterialTheme.typography.titleSmall
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    stringResource(R.string.battery_banner_text),
                    color = DeepMuted,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            TextButton(onClick = onConfigure) {
                Text(stringResource(R.string.battery_banner_action), color = DeepAccent)
            }
            IconButton(onClick = onDismiss) {
                Icon(Icons.Default.Close, contentDescription = "Скрыть", tint = DeepMuted)
            }
        }
    }
}

@Composable
private fun outlinedFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = DeepAccent,
    unfocusedBorderColor = DeepSurfaceHigh,
    focusedContainerColor = DeepSurfaceHigh,
    unfocusedContainerColor = DeepSurfaceHigh,
    cursorColor = DeepAccent
)

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
