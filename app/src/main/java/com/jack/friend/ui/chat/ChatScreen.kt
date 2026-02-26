package com.jack.friend.ui.chat

import android.app.Activity
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.view.WindowManager
import android.widget.Toast
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.jack.friend.*
import com.jack.friend.ui.components.*
import com.jack.friend.ui.profile.IOS17ContactProfileSheet
import com.jack.friend.ui.screens.ChatListScreenIOS
import com.jack.friend.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

/**
 * Main screen for the chat functionality, handling both the conversation list and individual chat sessions.
 *
 * This composable manages various states including:
 * - Message history and real-time updates.
 * - User search and contact management.
 * - Media attachments (images, videos, stickers).
 * - Status viewing and uploading.
 * - Call initiation (audio/video).
 * - Privacy features like temporary messages.
 *
 * @param viewModel The [ChatViewModel] that provides data and handles business logic for the chat.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val myUsername by viewModel.myUsername.collectAsStateWithLifecycle("")
    val myPhotoUrl by viewModel.myPhotoUrl.collectAsStateWithLifecycle(null)
    val targetId by viewModel.targetId.collectAsStateWithLifecycle("")
    val targetProfileState by viewModel.targetProfile.collectAsStateWithLifecycle(null)
    val messages by viewModel.messages.collectAsStateWithLifecycle(emptyList())
    val activeChats by viewModel.activeChats.collectAsStateWithLifecycle(emptyList())
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle(emptyList())
    val statuses by viewModel.statuses.collectAsStateWithLifecycle(emptyList())
    val isTargetTyping by viewModel.isTargetTyping.collectAsStateWithLifecycle(false)
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle(emptyList())
    val contacts by viewModel.contacts.collectAsStateWithLifecycle(emptyList())
    val recordingDuration by viewModel.recordingDuration.collectAsStateWithLifecycle(0L)
    val pinnedMessage by viewModel.pinnedMessage.collectAsStateWithLifecycle(null)
    val showReadReceipts by viewModel.showReadReceipts.collectAsStateWithLifecycle(true)

    // External sharing
    val pendingSharedMedia by viewModel.pendingSharedMedia.collectAsStateWithLifecycle()
    val pendingSharedText by viewModel.pendingSharedText.collectAsStateWithLifecycle()
    val hasPendingShare = pendingSharedMedia.isNotEmpty() || !pendingSharedText.isNullOrEmpty()
    var showShareConfirmDialog by remember { mutableStateOf<String?>(null) } // friendId

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var textState by remember { mutableStateOf("") }
    var searchInput by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var mediaViewerItem by remember { mutableStateOf<MediaViewerItem?>(null) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showInAppCamera by remember { mutableStateOf(false) }
    var showModernGallery by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var showStickerPicker by remember { mutableStateOf(false) }
    var tempMessageDuration by remember { mutableLongStateOf(0L) }
    var showTempMessageSelector by remember { mutableStateOf(false) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var showChatInfo by remember { mutableStateOf(false) }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var viewingStatuses by remember { mutableStateOf<List<UserStatus>?>(null) }
    var selectedFilter by remember { mutableStateOf("Tudo") }
    var selectedChatForOptions by remember { mutableStateOf<ChatSummary?>(null) }
    var currentBottomRoute by remember { mutableStateOf(BottomBarScreen.Home.route) }

    // State to open profile from search
    var searchingUserProfile by remember { mutableStateOf<UserProfile?>(null) }

    val filteredChats by remember(activeChats, selectedFilter) {
        derivedStateOf { if (selectedFilter == "Não Lidas") activeChats.filter { it.hasUnread } else activeChats }
    }

    // Ensure screenshots are always allowed
    LaunchedEffect(Unit) {
        val activity = context as? Activity ?: return@LaunchedEffect
        activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
    }

    LaunchedEffect(messages.size) { 
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1) 
            // Se a conversa está aberta e chegou mensagem, marca como lida
            if (targetId.isNotEmpty()) {
                viewModel.markAsRead()
            }
        }
    }

    LaunchedEffect(targetId) {
        if (targetId.isNotEmpty()) {
            viewModel.markAsRead()
            FriendApplication.currentOpenedChatId = targetId
            // Remove a notificação da barra de status ao abrir a conversa
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(targetId.hashCode())
        } else {
            FriendApplication.currentOpenedChatId = "LISTA_CONVERSAS"
        }
    }

    // Sincronizar duração das mensagens temporárias com o chat ativo
    LaunchedEffect(targetId, activeChats) {
        if (targetId.isNotEmpty()) {
            val currentChat = activeChats.find { it.friendId == targetId }
            tempMessageDuration = currentChat?.tempDuration ?: 0L
        }
    }

    LaunchedEffect(hasPendingShare, targetId) {
        if (hasPendingShare && targetId.isNotEmpty()) {
            showShareConfirmDialog = targetId
        }
    }

    val callLogic = { isVideo: Boolean ->
        val uniqueRoomId = "Call_${UUID.randomUUID().toString().take(8)}"
        viewModel.startCall(isVideo = isVideo, customRoomId = uniqueRoomId)
        val currentChat = activeChats.find { it.friendId == targetId }
        context.startActivity(Intent(context, CallActivity::class.java).apply {
            putExtra("roomId", uniqueRoomId)
            putExtra("targetId", targetId)
            putExtra("targetPhotoUrl", targetProfileState?.photoUrl ?: currentChat?.friendPhotoUrl)
            putExtra("isOutgoing", true)
            putExtra("isVideo", isVideo)
        })
    }

    BackHandler(enabled = targetId.isNotEmpty() || isSearching || mediaViewerItem != null || currentBottomRoute != BottomBarScreen.Home.route || viewingStatuses != null || showEmojiPicker || showStickerPicker || showChatInfo || showInAppCamera || showModernGallery || showAttachmentMenu || searchingUserProfile != null || showTempMessageSelector) {
        when {
            showTempMessageSelector -> showTempMessageSelector = false
            searchingUserProfile != null -> searchingUserProfile = null
            showInAppCamera -> showInAppCamera = false
            showModernGallery -> showModernGallery = false
            showAttachmentMenu -> showAttachmentMenu = false
            showEmojiPicker -> showEmojiPicker = false
            showStickerPicker -> showStickerPicker = false
            showChatInfo -> showChatInfo = false
            viewingStatuses != null -> viewingStatuses = null
            mediaViewerItem != null -> mediaViewerItem = null
            targetId.isNotEmpty() -> viewModel.setTargetId("")
            isSearching -> {
                isSearching = false
                searchInput = ""
                viewModel.searchUsers("")
                currentBottomRoute = BottomBarScreen.Home.route
            }
            currentBottomRoute != BottomBarScreen.Home.route -> {
                currentBottomRoute = BottomBarScreen.Home.route
                selectedFilter = "Tudo"
            }
        }
    }

    val statusLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
        if (uris.isNotEmpty()) viewModel.uploadStatus(uris)
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            if (viewingStatuses == null && !showChatInfo && !showInAppCamera && !showModernGallery && mediaViewerItem == null && searchingUserProfile == null) {
                ChatTopBar(
                    targetId = targetId, targetProfile = targetProfileState, activeChats = activeChats, myPhotoUrl = myPhotoUrl,
                    isTargetTyping = isTargetTyping, showContacts = false, isSearching = isSearching, searchInput = searchInput,
                    onBack = { if (targetId.isNotEmpty()) viewModel.setTargetId("") else { isSearching = false; currentBottomRoute = BottomBarScreen.Home.route } },
                    onSearchChange = { searchInput = it; viewModel.searchUsers(it) },
                    onSearchActiveChange = { isSearching = it; if (!it) currentBottomRoute = BottomBarScreen.Home.route },
                    onCallClick = { callLogic(false) }, onVideoCallClick = { callLogic(true) },
                    onOptionClick = { showOptionsMenu = true },
                    onAddContact = { showAddContactDialog = true },
                    onChatHeaderClick = { showChatInfo = true }
                )
            }
        },
        bottomBar = {
            if (viewingStatuses == null && !showChatInfo && !showInAppCamera && !showModernGallery && mediaViewerItem == null && searchingUserProfile == null) {
                if (targetId.isNotBlank()) {
                    Column {
                        ChatInputSection(
                            textState = textState, onTextChange = { textState = it; viewModel.setTyping(it.isNotEmpty()) },
                            replyingTo = replyingTo, editingMessage = editingMessage, pinnedMessage = pinnedMessage, recordingDuration = recordingDuration,
                            onSend = {
                                if (textState.isNotBlank()) {
                                    val currentEditingMessage = editingMessage
                                    if (currentEditingMessage != null) viewModel.editMessage(currentEditingMessage, textState)
                                    else viewModel.sendMessage(textState, tempMessageDuration, replyingTo)
                                    textState = ""
                                    replyingTo = null
                                    editingMessage = null
                                    showEmojiPicker = false
                                    showStickerPicker = false
                                }
                            },
                            onAddClick = { showAttachmentMenu = true }, onCameraClick = { showInAppCamera = true },
                            onAudioStart = { viewModel.startRecording(context.cacheDir) },
                            onAudioStop = { cancel -> viewModel.stopRecording(tempMessageDuration, cancel) },
                            onEmojiClick = {
                                if (showEmojiPicker) {
                                    keyboardController?.show()
                                    showEmojiPicker = false
                                } else {
                                    keyboardController?.hide()
                                    showEmojiPicker = true
                                    showStickerPicker = false
                                }
                            },
                            onStickerClick = {
                                if (showStickerPicker) {
                                    keyboardController?.show()
                                    showStickerPicker = false
                                } else {
                                    keyboardController?.hide()
                                    showStickerPicker = true
                                    showEmojiPicker = false
                                }
                            },
                            onCancelReply = { replyingTo = null; editingMessage = null; if (editingMessage != null) textState = "" },
                            onUnpin = { viewModel.unpinMessage() },
                            onPinnedClick = { msg ->
                                val index = messages.indexOfFirst { it.id == msg.id }
                                if (index != -1) {
                                    scope.launch { listState.animateScrollToItem(index) }
                                }
                            }
                        )
                        AnimatedVisibility(visible = showEmojiPicker) { MetaEmojiPickerPro(onEmojiSelected = { textState += it }, heightDp = 290) }
                        AnimatedVisibility(visible = showStickerPicker) {
                            StickerPicker(
                                onStickerSelected = {
                                    viewModel.sendSticker(it, replyingTo)
                                    replyingTo = null
                                    showStickerPicker = false
                                },
                                heightDp = 290
                            )
                        }
                    }
                } else {
                    ResponsiveFloatingDock(
                        currentRoute = currentBottomRoute,
                        onNavigate = { screen ->
                            if (screen == BottomBarScreen.Contacts) {
                                context.startActivity(Intent(context, ContactsActivity::class.java))
                            } else {
                                currentBottomRoute = screen.route
                                isSearching = screen == BottomBarScreen.Search
                                if (screen == BottomBarScreen.Home) selectedFilter = "Tudo"
                            }
                        },
                        onFabClick = { }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
            when {
                targetId.isNotEmpty() -> {
                    MessageListContent(
                        messages = messages, listState = listState, myUsername = myUsername, targetProfile = targetProfileState,
                        showReadReceipts = showReadReceipts,
                        onImageClick = { url -> mediaViewerItem = MediaViewerItem.Image(url) },
                        onVideoClick = { url -> mediaViewerItem = MediaViewerItem.Video(url) },
                        onDelete = { m -> viewModel.deleteMessage(m.id, if (m.senderId == myUsername) m.receiverId else m.senderId) },
                        onReply = { m -> replyingTo = m }, onReact = { m, e -> viewModel.addReaction(m, e) },
                        onEdit = { m -> editingMessage = m; textState = m.text }, onPin = { m -> viewModel.pinMessage(m) },
                        onAudioPlayed = { m -> viewModel.markAudioAsPlayed(m.id) }
                    )

                    val targetProfile = targetProfileState
                    if (showChatInfo && targetProfile != null) {
                        val currentChat = activeChats.find { it.friendId == targetId }
                        IOS17ContactProfileSheet(
                            user = targetProfile,
                            myUsername = myUsername,
                            isMuted = currentChat?.isMuted ?: false,
                            isBlocked = blockedUsers.contains(targetId),
                            onDismiss = { showChatInfo = false },
                            onMessage = {
                                showChatInfo = false
                                viewModel.setTargetId(it.id)
                             },
                            onAudioCall = { callLogic(false) },
                            onVideoCall = { callLogic(true) },
                            onToggleMute = { viewModel.toggleMuteChat(targetProfile.id, currentChat?.isMuted ?: false) },
                            onToggleBlock = { if (blockedUsers.contains(targetId)) viewModel.unblockUser(targetId) else viewModel.blockUser(targetId) },
                            onRemove = { profile -> viewModel.deleteContact(profile.id) { s, _ -> if (s) { showChatInfo = false; viewModel.setTargetId("") } } }
                        )
                    }

                    if (showOptionsMenu) {
                        val currentChat = activeChats.find { it.friendId == targetId }
                        ModalBottomSheet(
                            onDismissRequest = { showOptionsMenu = false },
                            containerColor = LocalChatColors.current.secondaryBackground,
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        ) {
                            ChatOptionsMenuSheet(
                                isMuted = currentChat?.isMuted ?: false,
                                isPinned = currentChat?.isPinned ?: false,
                                tempMessageDuration = tempMessageDuration,
                                isBlocked = blockedUsers.contains(targetId),
                                onDismiss = { showOptionsMenu = false },
                                onViewInfo = { showChatInfo = true },
                                onToggleMute = { viewModel.toggleMuteChat(targetId, currentChat?.isMuted ?: false) },
                                onTogglePin = { viewModel.togglePinChat(targetId, currentChat?.isPinned ?: false) },
                                onToggleTempMessages = { showTempMessageSelector = true },
                                onClearChat = { showClearChatDialog = true },
                                onBlockToggle = { if (blockedUsers.contains(targetId)) viewModel.unblockUser(targetId) else viewModel.blockUser(targetId) }
                            )
                        }
                    }

                    if (showTempMessageSelector) {
                        ModalBottomSheet(
                            onDismissRequest = { showTempMessageSelector = false },
                            containerColor = LocalChatColors.current.secondaryBackground,
                            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
                        ) {
                            TempMessageSelectorSheet(
                                currentDuration = tempMessageDuration,
                                onSelect = {
                                    viewModel.setTempMessageDuration(targetId, it)
                                    tempMessageDuration = it
                                    showTempMessageSelector = false
                                }
                            )
                        }
                    }
                }
                else -> {
                    Column {
                        if (hasPendingShare) {
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(12.dp),
                                tonalElevation = 4.dp
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Rounded.Share, null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(Modifier.width(12.dp))
                                    Text("Conteúdo pronto para compartilhar. Selecione um contato.", Modifier.weight(1f), fontSize = 14.sp)
                                    TextButton(onClick = { viewModel.clearPendingShare() }) {
                                        Text("Cancelar", color = iOSRed)
                                    }
                                }
                            }
                        }
                        ChatListScreenIOS(
                            isSearching = isSearching, searchInput = searchInput, searchResults = searchResults, filteredChats = filteredChats, statuses = statuses,
                            myPhotoUrl = myPhotoUrl, myUsername = myUsername, contacts = contacts, onStatusAdd = { statusLauncher.launch("image/*") },
                            onStatusView = { userStatuses -> viewModel.markStatusAsViewed(userStatuses.first().id); viewingStatuses = userStatuses },
                            onChatClick = { summary ->
                                if (hasPendingShare) showShareConfirmDialog = summary.friendId
                                else viewModel.setTargetId(summary.friendId)
                            },
                            onChatLongClick = { summary -> selectedChatForOptions = summary },
                            onUserSearchClick = { user ->
                                searchingUserProfile = user
                            },
                            onAddContactSearch = { id -> viewModel.addContact(id) { _, _ -> } },
                            onUserChatClick = { user ->
                                isSearching = false
                                searchInput = ""
                                viewModel.searchUsers("")
                                viewModel.setTargetId(user.id)
                            }
                        )
                    }
                }
            }

            if (showAttachmentMenu) MediaAttachmentSheet(viewModel = viewModel, onDismiss = { showAttachmentMenu = false }, onOpenCamera = { showInAppCamera = true }, onOpenGallery = { showModernGallery = true }, onMediaSelected = { uri, isV -> if (isV) viewModel.uploadVideo(uri, tempMessageDuration) else viewModel.uploadImage(uri, tempMessageDuration) }, onOpenFile = { })
            if (showInAppCamera) Box(modifier = Modifier.fillMaxSize().zIndex(10f)) { InAppCameraView(onDismiss = { showInAppCamera = false }, onPhotoCaptured = { uri -> viewModel.uploadImage(uri, tempMessageDuration) }, onVideoCaptured = { uri -> viewModel.uploadVideo(uri, tempMessageDuration) }) }
            if (showModernGallery) Box(modifier = Modifier.fillMaxSize().zIndex(10f)) { ModernGalleryPicker(viewModel = viewModel, onDismiss = { showModernGallery = false }, onSend = { uris -> uris.forEach { u -> viewModel.uploadImage(u, tempMessageDuration) } }) }

            MediaViewerScreen(mediaItem = mediaViewerItem, onDismiss = { mediaViewerItem = null })

            if (showClearChatDialog) AlertDialog(onDismissRequest = { showClearChatDialog = false }, title = { Text("Limpar Conversa") }, text = { Text("Isso apagará todas as mensagens para ambos.") }, confirmButton = { TextButton(onClick = { viewModel.clearChat(targetId); showClearChatDialog = false }) { Text("Limpar", color = iOSRed) } }, dismissButton = { TextButton(onClick = { showClearChatDialog = false }) { Text("Cancelar") } })
            if (showAddContactDialog) AddContactDialog(icon = Icons.Default.Person, onDismiss = { showAddContactDialog = false }, onAdd = { u -> viewModel.addContact(u) { s, e -> if (s) showAddContactDialog = false else Toast.makeText(context, e ?: "Erro", Toast.LENGTH_SHORT).show() } })

            val currentSelectedChat = selectedChatForOptions
            if (currentSelectedChat != null) {
                ChatPopUpMenu(
                    summary = currentSelectedChat,
                    isBlocked = blockedUsers.contains(currentSelectedChat.friendId),
                    onDismiss = { selectedChatForOptions = null },
                    onOpen = { summary -> viewModel.setTargetId(summary.friendId) },
                    onClear = { summary -> viewModel.clearChat(summary.friendId) },
                    onDelete = { summary -> viewModel.deleteChat(summary.friendId) },
                    onBlockToggle = { friendId -> if (blockedUsers.contains(friendId)) viewModel.unblockUser(friendId) else viewModel.blockUser(friendId) },
                    onTogglePin = { id, pinned -> viewModel.togglePinChat(id, pinned) },
                    onToggleMute = { id, muted -> viewModel.toggleMuteChat(id, muted) }
                )
            }

            val currentViewingStatuses = viewingStatuses
            if (currentViewingStatuses != null) {
                StatusViewer(userStatuses = currentViewingStatuses, myUsername = myUsername, viewModel = viewModel, onClose = { viewingStatuses = null }, onDelete = { id -> viewModel.deleteStatus(id) })
            }

            val shareTargetId = showShareConfirmDialog
            if (shareTargetId != null) {
                val shareTargetName = (activeChats.find { it.friendId == shareTargetId }?.friendName
                    ?: contacts.find { it.id == shareTargetId }?.name
                    ?: shareTargetId)

                AlertDialog(
                    onDismissRequest = { showShareConfirmDialog = null },
                    title = { Text("Compartilhar com $shareTargetName?") },
                    text = { Text("Deseja enviar o conteúdo selecionado para este contato?") },
                    confirmButton = {
                        TextButton(onClick = {
                            viewModel.sendPendingShare(shareTargetId, tempMessageDuration)
                            showShareConfirmDialog = null
                            if (targetId.isEmpty()) viewModel.setTargetId(shareTargetId)
                        }) { Text("Enviar", fontWeight = FontWeight.Bold) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showShareConfirmDialog = null }) { Text("Cancelar") }
                    }
                )
            }

            if (searchingUserProfile != null) {
                val user = searchingUserProfile!!
                val isContact = contacts.any { it.id == user.id }
                val chat = activeChats.firstOrNull { !it.isGroup && it.friendId == user.id }

                IOS17ContactProfileSheet(
                    user = user,
                    myUsername = myUsername,
                    isMuted = chat?.isMuted ?: false,
                    isBlocked = blockedUsers.contains(user.id),
                    onDismiss = { searchingUserProfile = null },
                    onMessage = {
                        searchingUserProfile = null
                        isSearching = false
                        viewModel.setTargetId(it.id)
                    },
                    onAudioCall = {
                        if (blockedUsers.contains(it.id)) {
                            Toast.makeText(context, "Desbloqueie para ligar", Toast.LENGTH_SHORT).show()
                        } else {
                            searchingUserProfile = null
                            isSearching = false
                            viewModel.setTargetId(it.id)
                            callLogic(false)
                        }
                    },
                    onVideoCall = {
                        if (blockedUsers.contains(it.id)) {
                            Toast.makeText(context, "Desbloqueie para ligar", Toast.LENGTH_SHORT).show()
                        } else {
                            searchingUserProfile = null
                            isSearching = false
                            viewModel.setTargetId(it.id)
                            callLogic(true)
                        }
                    },
                    onToggleMute = { viewModel.toggleMuteChat(user.id, chat?.isMuted ?: false) },
                    onToggleBlock = {
                        if (blockedUsers.contains(user.id)) viewModel.unblockUser(user.id)
                        else viewModel.blockUser(user.id)
                    },
                    onRemove = { profile ->
                        viewModel.deleteContact(profile.id) { s, _ ->
                            if (s) searchingUserProfile = null
                        }
                    },
                    isContact = isContact,
                    onAddContact = { profile ->
                        viewModel.addContact(profile.id) { s, e ->
                            if (!s) Toast.makeText(context, e ?: "Erro", Toast.LENGTH_SHORT).show()
                        }
                    }
                )
            }
        }
    }
}

/**
 * A full-screen viewer for user statuses (similar to stories), supporting progress indicators and navigation.
 *
 * @param userStatuses List of statuses to be displayed for a specific user.
 * @param myUsername The username of the current user, used to determine if they can see viewer info or delete.
 * @param viewModel The [ChatViewModel] to handle status-related actions like marking as viewed or deleting.
 * @param onClose Callback to be invoked when the viewer is closed.
 * @param onDelete Callback to be invoked when a status is deleted.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusViewer(
    userStatuses: List<UserStatus>,
    myUsername: String,
    viewModel: ChatViewModel,
    onClose: () -> Unit,
    onDelete: (String) -> Unit
) {
    var currentIndex by remember { mutableIntStateOf(0) }
    val currentStatus = userStatuses[currentIndex]
    var progress by remember { mutableStateOf(0f) }
    var showViewers by remember { mutableStateOf(false) }

    LaunchedEffect(currentIndex) {
        progress = 0f
        val duration = 5000L
        val steps = 100
        val stepTime = duration / steps
        for (i in 1..steps) {
            delay(stepTime)
            if (!showViewers) progress = i.toFloat() / steps
        }
        if (!showViewers) {
            if (currentIndex < userStatuses.size - 1) currentIndex++ else onClose()
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(model = currentStatus.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)

        Row(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable { if (currentIndex > 0) currentIndex-- })
            Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable { if (currentIndex < userStatuses.size - 1) currentIndex++ else onClose() })
        }

        Column(modifier = Modifier.fillMaxWidth().padding(top = 40.dp)) {
            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                userStatuses.forEachIndexed { index, _ ->
                    LinearProgressIndicator(
                        progress = { if (index < currentIndex) 1f else if (index == currentIndex) progress else 0f },
                        modifier = Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp)),
                        color = Color.White,
                        trackColor = Color.White.copy(alpha = 0.3f)
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = currentStatus.userPhotoUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(currentStatus.username, color = Color.White, fontWeight = FontWeight.Bold)
                    Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(currentStatus.timestamp)), color = Color.White.copy(0.7f), fontSize = 12.sp)
                }
                if (currentStatus.userId == myUsername) {
                    IconButton(onClick = { showViewers = true }) {
                        Icon(Icons.Rounded.Visibility, null, tint = Color.White)
                    }
                    IconButton(onClick = { onDelete(currentStatus.id) }) { Icon(Icons.Rounded.Delete, null, tint = Color.White) }
                }
                IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null, tint = Color.White) }
            }
        }

        if (showViewers && currentStatus.userId == myUsername) {
            ModalBottomSheet(
                onDismissRequest = { showViewers = false },
                containerColor = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
            ) {
                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Text("Visto por", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 16.dp))

                    val viewers = currentStatus.viewers.keys.toList()
                    if (viewers.isEmpty()) {
                        Text("Nenhuma visualização ainda.", color = Color.Gray, modifier = Modifier.padding(vertical = 32.dp).align(Alignment.CenterHorizontally))
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 400.dp)) {
                            items(viewers) { viewerId ->
                                var viewerProfile by remember { mutableStateOf<UserProfile?>(null) }
                                LaunchedEffect(viewerId) {
                                    com.google.firebase.database.FirebaseDatabase.getInstance().reference
                                        .child("users").child(viewerId).get().addOnSuccessListener {
                                            viewerProfile = it.getValue(UserProfile::class.java)
                                        }
                                }

                                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    AsyncImage(model = viewerProfile?.photoUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray), contentScale = ContentScale.Crop)
                                    Spacer(Modifier.width(12.dp))
                                    Text(viewerProfile?.name ?: viewerId, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(32.dp))
                }
            }
        }
    }
}

/**
 * A specialized player for video statuses using ExoPlayer.
 *
 * @param url The URL of the video to play.
 * @param onComplete Callback invoked when the video playback finishes.
 * @param isPaused Whether the video playback should be currently paused.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun VideoStatusPlayer(url: String, onComplete: () -> Unit, isPaused: Boolean) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.parse(url)))
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(isPaused) {
        if (isPaused) exoPlayer.pause() else exoPlayer.play()
    }

    DisposableEffect(Unit) {
        val listener = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                if (state == androidx.media3.common.Player.STATE_ENDED) onComplete()
            }
        }
        exoPlayer.addListener(listener)
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = false
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        modifier = Modifier.fillMaxSize()
    )
}

/**
 * A pop-up dialog menu providing quick actions for a specific chat from the chat list.
 *
 * @param summary The summary information of the selected chat.
 * @param isBlocked Whether the friend in this chat is currently blocked.
 * @param onDismiss Callback to close the menu.
 * @param onOpen Callback to open the full conversation.
 * @param onClear Callback to clear the message history for this chat.
 * @param onDelete Callback to delete the chat entry.
 * @param onBlockToggle Callback to toggle the block status of the user.
 * @param onTogglePin Callback to toggle the pinned status of the chat.
 * @param onToggleMute Callback to toggle the mute status of the chat.
 */
@Composable
fun ChatPopUpMenu(
    summary: ChatSummary,
    isBlocked: Boolean,
    onDismiss: () -> Unit,
    onOpen: (ChatSummary) -> Unit,
    onClear: (ChatSummary) -> Unit,
    onDelete: (ChatSummary) -> Unit,
    onBlockToggle: (String) -> Unit,
    onTogglePin: (String, Boolean) -> Unit,
    onToggleMute: (String, Boolean) -> Unit
) {
    val chatColors = LocalChatColors.current

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.4f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            Column(
                modifier = Modifier
                    .width(280.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(chatColors.secondaryBackground)
                    .clickable(enabled = false) {}
            ) {
                // Header com Preview
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        AsyncImage(
                            model = summary.friendPhotoUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(70.dp)
                                .clip(CircleShape)
                                .background(chatColors.separator),
                            contentScale = ContentScale.Crop
                        )
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = summary.friendName ?: summary.friendId,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = chatColors.textPrimary
                        )
                        Text(
                            text = if (summary.isOnline) "Online" else "Visto por último recentemente",
                            style = MaterialTheme.typography.bodySmall,
                            color = if (summary.isOnline) iOSGreen else chatColors.textSecondary
                        )
                    }
                }

                HorizontalDivider(color = chatColors.separator.copy(alpha = 0.3f), thickness = 0.5.dp)

                // Opções
                ChatPopOptionItem(
                    text = "Abrir Conversa",
                    icon = Icons.Rounded.Chat,
                    onClick = { onOpen(summary); onDismiss() }
                )

                ChatPopOptionItem(
                    text = if (summary.isPinned) "Desafixar" else "Fixar Conversa",
                    icon = Icons.Rounded.PushPin,
                    iconColor = if (summary.isPinned) MessengerBlue else null,
                    onClick = { onTogglePin(summary.friendId, summary.isPinned); onDismiss() }
                )

                ChatPopOptionItem(
                    text = if (summary.isMuted) "Ativar Sons" else "Silenciar",
                    icon = if (summary.isMuted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                    onClick = { onToggleMute(summary.friendId, summary.isMuted); onDismiss() }
                )

                ChatPopOptionItem(
                    text = "Limpar Histórico",
                    icon = Icons.Rounded.DeleteSweep,
                    textColor = iOSRed,
                    iconColor = iOSRed,
                    onClick = { onClear(summary); onDismiss() }
                )

                ChatPopOptionItem(
                    text = "Excluir Chat",
                    icon = Icons.Rounded.DeleteOutline,
                    textColor = iOSRed,
                    iconColor = iOSRed,
                    onClick = { onDelete(summary); onDismiss() }
                )

                ChatPopOptionItem(
                    text = if (isBlocked) "Desbloquear" else "Bloquear",
                    icon = if (isBlocked) Icons.Rounded.LockOpen else Icons.Rounded.Block,
                    textColor = if (isBlocked) null else iOSRed,
                    iconColor = if (isBlocked) null else iOSRed,
                    onClick = { onBlockToggle(summary.friendId); onDismiss() }
                )
            }
        }
    }
}

/**
 * A single menu item within the [ChatPopUpMenu].
 *
 * @param text The display text for the option.
 * @param icon The icon representing the action.
 * @param onClick The action to perform when the item is clicked.
 * @param textColor Optional color for the text (e.g., red for destructive actions).
 * @param iconColor Optional color for the icon.
 */
@Composable
fun ChatPopOptionItem(
    text: String,
    icon: ImageVector,
    onClick: () -> Unit,
    textColor: Color? = null,
    iconColor: Color? = null
) {
    val chatColors = LocalChatColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconColor ?: chatColors.textPrimary.copy(alpha = 0.7f),
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = text,
            color = textColor ?: chatColors.textPrimary,
            fontWeight = FontWeight.Medium,
            fontSize = 16.sp
        )
    }
}

@Composable
fun TempMessageSelectorSheet(
    currentDuration: Long,
    onSelect: (Long) -> Unit
) {
    val chatColors = LocalChatColors.current
    val options = listOf(
        0L to "Desativado",
        15000L to "15 segundos",
        30000L to "30 segundos",
        60000L to "60 segundos",
        300000L to "5 minutos",
        600000L to "10 minutos",
        1800000L to "30 minutos",
        86400000L to "24 horas"
    )

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 16.dp)
    ) {
        Text(
            text = "Mensagens Temporárias",
            modifier = Modifier.padding(16.dp),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = chatColors.textPrimary
        )

        options.forEach { (duration, label) ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onSelect(duration) }
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = label,
                    modifier = Modifier.weight(1f),
                    color = chatColors.textPrimary,
                    fontSize = 16.sp
                )
                if (currentDuration == duration) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = "Selecionado",
                        tint = MessengerBlue,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }
    }
}
