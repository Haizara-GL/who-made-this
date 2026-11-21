package com.example.ui.screens

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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Call
import androidx.compose.material.icons.rounded.ChatBubbleOutline
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.GroupAdd
import androidx.compose.material.icons.rounded.LocalParking
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import com.example.ui.components.StarOfDavidLoader
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.Chat
import com.example.data.ChatMessage
import com.example.data.ParkingSpot
import com.example.data.PublicProfile
import com.example.data.UiState
import com.example.ui.theme.LocalVestnikColors
import com.google.firebase.Timestamp
import java.text.SimpleDateFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatsListScreen(
    chatsState: UiState<List<Chat>>,
    directoryState: UiState<List<PublicProfile>>,
    currentUserId: String,
    onOpenChat: (String) -> Unit,
    onCreateGroupChat: (String, String, Boolean) -> Unit,
    onStartDirectChat: (String, String) -> Unit,
    onJoinByCode: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var searchQuery by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }
    val extendedColors = LocalVestnikColors.current

    Box(modifier = modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Вестник • Чаты",
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Text(
                            text = "Защищённая связь и каналы районов",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = { showJoinDialog = true },
                        modifier = Modifier.testTag("join_chat_by_code_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GroupAdd,
                            contentDescription = "Войти в чат по коду"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background
                )
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Поиск чатов и районов...") },
                leadingIcon = {
                    Icon(Icons.Rounded.Search, contentDescription = null)
                },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .testTag("chats_search_input")
            )

            when (chatsState) {
                is UiState.Loading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        StarOfDavidLoader(label = "Загрузка чатов...")
                    }
                }
                is UiState.Error -> {
                    ErrorPlaceholderCard(
                        message = chatsState.message,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                is UiState.Success -> {
                    val filtered = remember(chatsState.data, searchQuery) {
                        if (searchQuery.isBlank()) chatsState.data
                        else chatsState.data.filter {
                            it.title.contains(searchQuery, ignoreCase = true) ||
                                it.lastMessageText.contains(searchQuery, ignoreCase = true) ||
                                it.inviteCode.contains(searchQuery, ignoreCase = true)
                        }
                    }

                    if (filtered.isEmpty()) {
                        EmptyChatsState(
                            onCreateClick = { showCreateDialog = true },
                            onJoinClick = { showJoinDialog = true }
                        )
                    } else {
                        LazyColumn(
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 8.dp,
                                bottom = 96.dp
                            ),
                            verticalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(filtered, key = { it.id }) { chat ->
                                ChatListRowCard(
                                    chat = chat,
                                    accentColor = when (chat.chatType) {
                                        "parking" -> extendedColors.amberAlert
                                        "direct" -> extendedColors.emeraldLive
                                        else -> MaterialTheme.colorScheme.primary
                                    },
                                    onClick = { onOpenChat(chat.id) }
                                )
                            }
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showCreateDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
                .testTag("new_chat_fab")
        ) {
            Icon(
                imageVector = Icons.Rounded.Add,
                contentDescription = stringResource(R.string.cd_new_chat)
            )
        }
    }

    if (showCreateDialog) {
        val contacts = (directoryState as? UiState.Success)?.data
            ?.filter { it.userId != currentUserId }
            .orEmpty()
        CreateChatDialog(
            contacts = contacts,
            onDismiss = { showCreateDialog = false },
            onCreateGroup = { title, type, joinable ->
                showCreateDialog = false
                onCreateGroupChat(title, type, joinable)
            },
            onOpenDirect = { peerId, peerName ->
                showCreateDialog = false
                onStartDirectChat(peerId, peerName)
            },
            onOpenSavedNotes = {
                showCreateDialog = false
                onStartDirectChat(currentUserId, "Избранное")
            }
        )
    }

    if (showJoinDialog) {
        JoinByCodeDialog(
            onDismiss = { showJoinDialog = false },
            onJoin = { code ->
                showJoinDialog = false
                onJoinByCode(code)
            }
        )
    }
}

@Composable
private fun ChatListRowCard(
    chat: Chat,
    accentColor: androidx.compose.ui.graphics.Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("chat_item_${chat.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(50.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (chat.chatType) {
                        "parking" -> Icons.Rounded.LocalParking
                        "direct" -> Icons.Rounded.Person
                        else -> Icons.Rounded.Forum
                    },
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(26.dp)
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chat.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatShortTime(chat.updatedAt ?: chat.createdAt),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Text(
                    text = if (chat.lastMessageSender.isNotBlank()) {
                        "${chat.lastMessageSender}: ${chat.lastMessageText}"
                    } else {
                        chat.lastMessageText.ifBlank { "Нет сообщений" }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Surface(
                        color = accentColor.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = when (chat.chatType) {
                                "parking" -> "Парковка"
                                "direct" -> "Личный"
                                else -> "Группа"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                        )
                    }
                    if (chat.joinable && chat.inviteCode.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "Код: ${chat.inviteCode}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyChatsState(
    onCreateClick: () -> Unit,
    onJoinClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Rounded.ChatBubbleOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(36.dp)
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "Пока нет активных чатов",
            style = MaterialTheme.typography.titleLarge
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Создайте канал своего двора для ловли парковки, личный диалог или присоединитесь по коду.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(20.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onCreateClick,
                modifier = Modifier.testTag("empty_create_chat_button")
            ) {
                Text("Создать чат")
            }
            OutlinedButton(
                onClick = onJoinClick,
                modifier = Modifier.testTag("empty_join_chat_button")
            ) {
                Text("Войти по коду")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chat: Chat?,
    messagesState: UiState<List<ChatMessage>>,
    activeSpots: List<ParkingSpot>,
    currentUserId: String,
    onBack: () -> Unit,
    onSendMessage: (String) -> Unit,
    onShareSpot: (ParkingSpot) -> Unit,
    onStartAudioCall: (String, String) -> Unit,
    onStartVideoCall: (String, String) -> Unit,
    onOpenParkingTab: () -> Unit,
    modifier: Modifier = Modifier
) {
    val extendedColors = LocalVestnikColors.current
    val clipboard = LocalClipboardManager.current
    var draftText by remember { mutableStateOf("") }
    var showShareSpotSheet by remember { mutableStateOf(false) }
    var codeCopied by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val messages = (messagesState as? UiState.Success)?.data.orEmpty()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.lastIndex)
        }
    }

    val callPeerId = chat?.memberUids?.firstOrNull { it != currentUserId } ?: currentUserId
    val callPeerName = if (callPeerId == currentUserId) "Эхо-тест (${chat?.title ?: "Вестник"})" else (chat?.title ?: "Собеседник")

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
    ) {
        TopAppBar(
            navigationIcon = {
                IconButton(
                    onClick = onBack,
                    modifier = Modifier.testTag("chat_back_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                        contentDescription = stringResource(R.string.cd_back)
                    )
                }
            },
            title = {
                Column {
                    Text(
                        text = chat?.title ?: "Чат",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (chat != null) {
                        val subtitle = if (chat.joinable && chat.inviteCode.isNotBlank()) {
                            "Код: ${chat.inviteCode} • Участников: ${chat.memberUids.size}"
                        } else {
                            "Участников: ${chat.memberUids.size}"
                        }
                        Text(
                            text = if (codeCopied) "Код скопирован!" else subtitle,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (codeCopied) extendedColors.emeraldLive else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            actions = {
                if (chat != null && chat.joinable && chat.inviteCode.isNotBlank()) {
                    IconButton(
                        onClick = {
                            clipboard.setText(AnnotatedString(chat.inviteCode))
                            codeCopied = true
                        },
                        modifier = Modifier.testTag("copy_invite_code_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ContentCopy,
                            contentDescription = "Скопировать код приглашения"
                        )
                    }
                }
                IconButton(
                    onClick = { onStartAudioCall(callPeerId, callPeerName) },
                    modifier = Modifier.testTag("chat_audio_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Call,
                        contentDescription = stringResource(R.string.cd_audio_call),
                        tint = extendedColors.emeraldLive
                    )
                }
                IconButton(
                    onClick = { onStartVideoCall(callPeerId, callPeerName) },
                    modifier = Modifier.testTag("chat_video_call_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Videocam,
                        contentDescription = stringResource(R.string.cd_video_call),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            },
            colors = TopAppBarDefaults.topAppBarColors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        )

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            when (messagesState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        StarOfDavidLoader(label = "Загрузка сообщений...")
                    }
                }
                is UiState.Error -> {
                    ErrorPlaceholderCard(
                        message = messagesState.message,
                        modifier = Modifier.padding(16.dp)
                    )
                }
                is UiState.Success -> {
                    if (messages.isEmpty()) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(32.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Напишите первое сообщение или поделитесь свободным местом на парковке",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        LazyColumn(
                            state = listState,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxSize()
                        ) {
                            items(messages, key = { it.id }) { msg ->
                                MessageBubble(
                                    message = msg,
                                    isMine = msg.senderId == currentUserId,
                                    onOpenParking = onOpenParkingTab
                                )
                            }
                        }
                    }
                }
            }
        }

        // Message Composer
        Surface(
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 4.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilledTonalIconButton(
                    onClick = { showShareSpotSheet = true },
                    modifier = Modifier.testTag("chat_share_parking_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.DirectionsCar,
                        contentDescription = stringResource(R.string.cd_share_parking),
                        tint = extendedColors.amberAlert
                    )
                }

                OutlinedTextField(
                    value = draftText,
                    onValueChange = { draftText = it },
                    placeholder = { Text("Сообщение...") },
                    maxLines = 4,
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("chat_message_input")
                )

                FilledTonalIconButton(
                    onClick = {
                        val trimmed = draftText.trim()
                        if (trimmed.isNotEmpty()) {
                            onSendMessage(trimmed)
                            draftText = ""
                        }
                    },
                    enabled = draftText.isNotBlank(),
                    modifier = Modifier.testTag("chat_send_button")
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Rounded.Send,
                        contentDescription = stringResource(R.string.cd_send_message)
                    )
                }
            }
        }
    }

    if (showShareSpotSheet) {
        ModalBottomSheet(onDismissRequest = { showShareSpotSheet = false }) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Отправить парковочное место в чат",
                    style = MaterialTheme.typography.titleLarge
                )
                if (activeSpots.isEmpty()) {
                    Text(
                        text = "Сейчас нет активных мест на радаре. Сначала опубликуйте место на вкладке «Парковка».",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            showShareSpotSheet = false
                            onOpenParkingTab()
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Перейти на радар парковки")
                    }
                } else {
                    activeSpots.take(6).forEach { spot ->
                        Card(
                            onClick = {
                                showShareSpotSheet = false
                                onShareSpot(spot)
                            },
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Rounded.LocalParking,
                                    contentDescription = null,
                                    tint = extendedColors.emeraldLive
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = spot.address,
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    Text(
                                        text = "${spot.district} • ${if (spot.leaveInMinutes > 0) "через ${spot.leaveInMinutes} мин" else "свободно сейчас"}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun MessageBubble(
    message: ChatMessage,
    isMine: Boolean,
    onOpenParking: () -> Unit
) {
    val extendedColors = LocalVestnikColors.current
    val bgColor = if (isMine) extendedColors.outgoingBubble else extendedColors.incomingBubble
    val fgColor = if (isMine) extendedColors.onOutgoingBubble else extendedColors.onIncomingBubble

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = if (isMine) Alignment.End else Alignment.Start
    ) {
        Surface(
            color = bgColor,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMine) 18.dp else 4.dp,
                bottomEnd = if (isMine) 4.dp else 18.dp
            ),
            tonalElevation = if (isMine) 0.dp else 2.dp,
            modifier = Modifier.widthIn(max = 310.dp)
        ) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (!isMine) {
                    Text(
                        text = message.senderName,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold
                    )
                }

                if (message.messageType == "parking_share") {
                    Surface(
                        color = extendedColors.amberContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onOpenParking)
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.LocalParking,
                                contentDescription = null,
                                tint = extendedColors.amberAlert
                            )
                            Column {
                                Text(
                                    text = "ПАРКОВОЧНОЕ МЕСТО",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = extendedColors.onAmberContainer
                                )
                                Text(
                                    text = message.spotAddress.ifBlank { message.text },
                                    style = MaterialTheme.typography.titleSmall,
                                    color = extendedColors.onAmberContainer
                                )
                            }
                        }
                    }
                }

                Text(
                    text = message.text,
                    style = MaterialTheme.typography.bodyLarge,
                    color = fgColor
                )

                Text(
                    text = formatShortTime(message.createdAt),
                    style = MaterialTheme.typography.labelSmall,
                    color = fgColor.copy(alpha = 0.72f),
                    modifier = Modifier.align(Alignment.End)
                )
            }
        }
    }
}

@Composable
private fun CreateChatDialog(
    contacts: List<PublicProfile>,
    onDismiss: () -> Unit,
    onCreateGroup: (String, String, Boolean) -> Unit,
    onOpenDirect: (String, String) -> Unit,
    onOpenSavedNotes: () -> Unit
) {
    var title by remember { mutableStateOf("") }
    var chatType by remember { mutableStateOf("parking") }
    var joinable by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новый чат или канал") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название (напр. Двор Тверская 14)") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_chat_title_input")
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = chatType == "parking",
                        onClick = { chatType = "parking" },
                        label = { Text("Парковка") }
                    )
                    FilterChip(
                        selected = chatType == "group",
                        onClick = { chatType = "group" },
                        label = { Text("Группа") }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Вход по коду приглашения",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Switch(checked = joinable, onCheckedChange = { joinable = it })
                }

                FilledTonalButton(
                    onClick = onOpenSavedNotes,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Открыть «Избранное» (заметки)")
                }

                if (contacts.isNotEmpty()) {
                    Text(
                        text = "Или написать участнику:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    contacts.take(4).forEach { person ->
                        OutlinedButton(
                            onClick = { onOpenDirect(person.userId, person.displayName) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("${person.displayName} (@${person.handle})")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onCreateGroup(title, chatType, joinable) },
                enabled = title.trim().isNotEmpty(),
                modifier = Modifier.testTag("confirm_create_chat_button")
            ) {
                Text("Создать")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
private fun JoinByCodeDialog(
    onDismiss: () -> Unit,
    onJoin: (String) -> Unit
) {
    var code by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Войти в канал по коду") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Введите 6-значный код канала, которым поделился сосед или коллега.",
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it.uppercase().take(16) },
                    label = { Text("Код приглашения") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("join_code_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onJoin(code) },
                enabled = code.trim().length >= 4,
                modifier = Modifier.testTag("confirm_join_code_button")
            ) {
                Text("Присоединиться")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Отмена") }
        }
    )
}

@Composable
fun ErrorPlaceholderCard(
    message: String,
    modifier: Modifier = Modifier
) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        shape = RoundedCornerShape(16.dp),
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp)
        )
    }
}

fun formatShortTime(timestamp: Timestamp?): String {
    val date = (timestamp ?: Timestamp.now()).toDate()
    val fmt = SimpleDateFormat("HH:mm", Locale.getDefault())
    return fmt.format(date)
}
