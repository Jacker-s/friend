package com.jack.friend

import android.Manifest
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.view.HapticFeedbackConstants
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.jack.friend.ui.theme.*
import kotlinx.coroutines.delay
import java.io.ByteArrayOutputStream
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.roundToInt

class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        WebRTCManager.initialize(applicationContext)
        setContent {
            FriendTheme {
                val viewModel: ChatViewModel = viewModel()
                val isUserLoggedIn by viewModel.isUserLoggedIn.collectAsStateWithLifecycle()
                SecurityWrapper(isUserLoggedIn, viewModel)
            }
        }
    }
}

@Composable
fun SecurityWrapper(isUserLoggedIn: Boolean, viewModel: ChatViewModel) {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("security_prefs", Context.MODE_PRIVATE) }
    val isPinEnabled = remember { prefs.getBoolean("pin_enabled", false) }
    val isBiometricEnabled = remember { prefs.getBoolean("biometric_enabled", false) }
    val correctPin = remember { prefs.getString("security_pin", "") ?: "" }
    var isUnlocked by remember { mutableStateOf(!(isPinEnabled || isBiometricEnabled)) }

    val permissions = remember {
        mutableListOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO).apply {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
                add(Manifest.permission.READ_MEDIA_IMAGES)
            } else add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { }
    LaunchedEffect(Unit) { launcher.launch(permissions.toTypedArray()) }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        when {
            !isUserLoggedIn -> LoginScreen(viewModel)
            !isUnlocked -> PinLockScreen(correctPin, isBiometricEnabled) { isUnlocked = true }
            else -> ChatScreen(viewModel)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(viewModel: ChatViewModel) {
    val myUsername by viewModel.myUsername.collectAsStateWithLifecycle("")
    val myPhotoUrl by viewModel.myPhotoUrl.collectAsStateWithLifecycle(null)
    val targetId by viewModel.targetId.collectAsStateWithLifecycle("")
    val targetProfile by viewModel.targetProfile.collectAsStateWithLifecycle(null)
    val targetGroup by viewModel.targetGroup.collectAsStateWithLifecycle(null)
    val messages by viewModel.messages.collectAsStateWithLifecycle(emptyList())
    val activeChats by viewModel.activeChats.collectAsStateWithLifecycle(emptyList())
    val searchResults by viewModel.searchResults.collectAsStateWithLifecycle(emptyList())
    val statuses by viewModel.statuses.collectAsStateWithLifecycle(emptyList())
    val isTargetTyping by viewModel.isTargetTyping.collectAsStateWithLifecycle(false)
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle(emptyList())
    val contacts by viewModel.contacts.collectAsStateWithLifecycle(emptyList())
    val recordingDuration by viewModel.recordingDuration.collectAsStateWithLifecycle()
    val pinnedMessage by viewModel.pinnedMessage.collectAsStateWithLifecycle()

    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()
    var textState by remember { mutableStateOf("") }
    var searchInput by remember { mutableStateOf("") }
    var isSearching by remember { mutableStateOf(false) }
    var fullScreenImageUrl by remember { mutableStateOf<String?>(null) }
    var showAttachmentMenu by remember { mutableStateOf(false) }
    var showOptionsMenu by remember { mutableStateOf(false) }
    var showEmojiPicker by remember { mutableStateOf(false) }
    var tempMessageDuration by remember { mutableIntStateOf(0) }
    var showClearChatDialog by remember { mutableStateOf(false) }
    var showDeleteGroupDialog by remember { mutableStateOf(false) }
    var showContacts by remember { mutableStateOf(false) }
    var showAddContactDialog by remember { mutableStateOf(false) }
    var showCreateGroup by remember { mutableStateOf(false) }
    var replyingTo by remember { mutableStateOf<Message?>(null) }
    var editingMessage by remember { mutableStateOf<Message?>(null) }
    var viewingStatuses by remember { mutableStateOf<List<UserStatus>?>(null) }
    var selectedFilter by remember { mutableStateOf("Tudo") }
    var selectedChatForOptions by remember { mutableStateOf<ChatSummary?>(null) }

    val filteredChats by remember(activeChats, selectedFilter) {
        derivedStateOf {
            when (selectedFilter) {
                "Grupos" -> activeChats.filter { it.isGroup }
                "Não Lidas" -> activeChats.filter { it.hasUnread }
                else -> activeChats
            }
        }
    }

    LaunchedEffect(messages.size) { if (messages.isNotEmpty()) listState.animateScrollToItem(messages.size - 1) }
    LaunchedEffect(targetId) {
        if (targetId.isNotEmpty()) {
            viewModel.markAsRead()
            FriendApplication.currentOpenedChatId = targetId
        } else {
            FriendApplication.currentOpenedChatId = "LISTA_CONVERSAS"
        }
    }

    BackHandler(enabled = targetId.isNotEmpty() || isSearching || fullScreenImageUrl != null || showContacts || viewingStatuses != null || showCreateGroup || showEmojiPicker) {
        if (showEmojiPicker) showEmojiPicker = false
        else if (viewingStatuses != null) viewingStatuses = null
        else if (fullScreenImageUrl != null) fullScreenImageUrl = null
        else if (isSearching) { isSearching = false; searchInput = ""; viewModel.searchUsers("") }
        else if (showCreateGroup) showCreateGroup = false
        else if (showContacts) showContacts = false
        else viewModel.setTargetId("")
    }

    val imageLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> 
        uri?.let { viewModel.uploadImage(it, targetGroup != null, tempMessageDuration) } 
    }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) { bitmap ->
        bitmap?.let {
            val bytes = ByteArrayOutputStream()
            it.compress(Bitmap.CompressFormat.JPEG, 100, bytes)
            @Suppress("DEPRECATION")
            val path = MediaStore.Images.Media.insertImage(context.contentResolver, it, "Image_${System.currentTimeMillis()}", null)
            viewModel.uploadImage(Uri.parse(path), targetGroup != null, tempMessageDuration)
        }
    }
    val statusLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> 
        uri?.let { viewModel.uploadStatus(it) } 
    }

    Scaffold(
        topBar = {
            if (viewingStatuses == null) {
                ChatTopBar(
                    targetId = targetId,
                    targetGroup = targetGroup,
                    targetProfile = targetProfile,
                    activeChats = activeChats,
                    myPhotoUrl = myPhotoUrl,
                    isTargetTyping = isTargetTyping,
                    showContacts = showContacts,
                    showCreateGroup = showCreateGroup,
                    isSearching = isSearching,
                    searchInput = searchInput,
                    onBack = { 
                        if (showEmojiPicker) showEmojiPicker = false
                        else if (showCreateGroup) showCreateGroup = false
                        else if (showContacts) showContacts = false
                        else viewModel.setTargetId("")
                    },
                    onSearchChange = { searchInput = it; viewModel.searchUsers(it) },
                    onSearchActiveChange = { isSearching = it },
                    onCallClick = {
                        val uniqueRoomId = "Call_${UUID.randomUUID().toString().take(8)}"
                        viewModel.startCall(isVideo = false, isGroup = false, uniqueRoomId)
                        val currentChat = activeChats.find { it.friendId == targetId }
                        context.startActivity(Intent(context, CallActivity::class.java).apply {
                            putExtra("roomId", uniqueRoomId)
                            putExtra("targetId", targetId)
                            putExtra("targetPhotoUrl", targetProfile?.photoUrl ?: currentChat?.friendPhotoUrl)
                            putExtra("isOutgoing", true)
                            putExtra("isVideo", false)
                        })
                    },
                    onOptionClick = { showOptionsMenu = true },
                    onAddGroup = { showCreateGroup = true },
                    onAddContact = { showAddContactDialog = true }
                )
            }
        },
        floatingActionButton = {
            if (targetId.isEmpty() && !showContacts && viewingStatuses == null) {
                FloatingActionButton(onClick = { showContacts = true }, containerColor = MessengerBlue, contentColor = Color.White, shape = CircleShape) {
                    Icon(Icons.Rounded.Add, null)
                }
            }
        },
        bottomBar = {
            if (viewingStatuses == null) {
                if (targetId.isNotBlank()) {
                    Column {
                        ChatInputSection(
                            textState = textState,
                            onTextChange = { textState = it; viewModel.setTyping(it.isNotEmpty()) },
                            replyingTo = replyingTo,
                            editingMessage = editingMessage,
                            pinnedMessage = pinnedMessage,
                            recordingDuration = recordingDuration,
                            onSend = {
                                if (textState.isNotBlank()) {
                                    if (editingMessage != null) viewModel.editMessage(editingMessage!!, textState)
                                    else viewModel.sendMessage(textState, targetGroup != null, tempMessageDuration, replyingTo)
                                    textState = ""; replyingTo = null; editingMessage = null
                                    showEmojiPicker = false
                                }
                            },
                            onAddClick = { showAttachmentMenu = true },
                            onAudioStart = { viewModel.startRecording(context.cacheDir) },
                            onAudioStop = { cancel -> viewModel.stopRecording(targetGroup != null, tempMessageDuration, cancel) },
                            onEmojiClick = { 
                                if (showEmojiPicker) {
                                    keyboardController?.show()
                                    showEmojiPicker = false
                                } else {
                                    keyboardController?.hide()
                                    showEmojiPicker = true
                                }
                            },
                            onCancelReply = { replyingTo = null; editingMessage = null; if(editingMessage != null) textState = "" },
                            onUnpin = { viewModel.unpinMessage() }
                        )
                        AnimatedVisibility(visible = showEmojiPicker) {
                            EmojiPicker(onEmojiSelected = { textState += it })
                        }
                    }
                } else if (!showContacts && !showCreateGroup && !isSearching) {
                    Surface(
                        color = LocalChatColors.current.topBar,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.navigationBarsPadding()) {
                            HorizontalDivider(thickness = 0.5.dp, color = LocalChatColors.current.separator)
                            TelegramTabs(selectedFilter) { selectedFilter = it }
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding).fillMaxSize().background(MaterialTheme.colorScheme.background)) {
            when {
                showCreateGroup -> CreateGroupScreen(contacts) { name, members, uri ->
                    viewModel.createGroup(name, members, uri) { success, id ->
                        if (success) { showCreateGroup = false; showContacts = false; viewModel.setTargetId(id!!, true) }
                        else Toast.makeText(context, id ?: "Erro", Toast.LENGTH_SHORT).show()
                    }
                }
                showContacts -> ContactsScreen(contacts) { user -> showContacts = false; viewModel.setTargetId(user.id, false) }
                targetId.isEmpty() -> ConversationListContent(
                    isSearching = isSearching,
                    searchInput = searchInput,
                    searchResults = searchResults,
                    filteredChats = filteredChats,
                    statuses = statuses,
                    myPhotoUrl = myPhotoUrl,
                    myUsername = myUsername,
                    contacts = contacts,
                    onStatusAdd = { statusLauncher.launch("image/*") },
                    onStatusView = { viewModel.markStatusAsViewed(it.first().id); viewingStatuses = it },
                    onChatClick = { summary -> viewModel.setTargetId(summary.friendId, summary.isGroup) },
                    onChatLongClick = { summary -> selectedChatForOptions = summary },
                    onUserSearchClick = { user -> isSearching = false; viewModel.setTargetId(user.id, false) },
                    onAddContactSearch = { viewModel.addContact(it) { _, _ -> } }
                )
                else -> MessageListContent(
                    messages = messages,
                    listState = listState,
                    myUsername = myUsername,
                    targetProfile = targetProfile,
                    onImageClick = { fullScreenImageUrl = it },
                    onDelete = { m -> viewModel.deleteMessage(m.id, if (m.senderId == myUsername) m.receiverId else m.senderId, m.isGroup) },
                    onReply = { replyingTo = it },
                    onReact = { m, e -> viewModel.addReaction(m, e) },
                    onEdit = { m -> editingMessage = m; textState = m.text },
                    onPin = { viewModel.pinMessage(it) }
                )
            }
            
            if (showOptionsMenu) {
                ChatOptionsMenu(
                    expanded = showOptionsMenu,
                    onDismiss = { showOptionsMenu = false },
                    targetGroup = targetGroup,
                    myUsername = myUsername,
                    targetId = targetId,
                    blockedUsers = blockedUsers,
                    tempMessageDuration = tempMessageDuration,
                    onClearChat = { showClearChatDialog = true },
                    onDeleteGroup = { showDeleteGroupDialog = true },
                    onToggleTempMessages = { tempMessageDuration = if(tempMessageDuration == 0) 24 else 0 },
                    onBlockToggle = { if(blockedUsers.contains(targetId)) viewModel.unblockUser(targetId) else viewModel.blockUser(targetId) }
                )
            }
            
            if (showAttachmentMenu) AttachmentBottomSheet(onCamera = { cameraLauncher.launch() }, onGallery = { imageLauncher.launch("image/*") }, onDismiss = { showAttachmentMenu = false })
            if (fullScreenImageUrl != null) FullScreenImage(url = fullScreenImageUrl!!, onDismiss = { fullScreenImageUrl = null })
            if (showClearChatDialog) AlertDialog(onDismissRequest = { showClearChatDialog = false }, title = { Text("Limpar Conversa") }, text = { Text("Isso apagará todas as mensagens para ambos.") }, confirmButton = { TextButton(onClick = { viewModel.clearChat(targetId, targetGroup != null); showClearChatDialog = false }) { Text("Limpar", color = iOSRed) } }, dismissButton = { TextButton(onClick = { showClearChatDialog = false }) { Text("Cancelar") } })
            if (showDeleteGroupDialog) AlertDialog(onDismissRequest = { showDeleteGroupDialog = false }, title = { Text("Apagar Grupo") }, text = { Text("Isso excluirá o grupo e todas as mensagens para todos os membros.") }, confirmButton = { TextButton(onClick = { viewModel.deleteGroup(targetId) { s, e -> if (s) { showDeleteGroupDialog = false; viewModel.setTargetId("") } else Toast.makeText(context, e ?: "Erro", Toast.LENGTH_SHORT).show() } }) { Text("Apagar", color = iOSRed) } }, dismissButton = { TextButton(onClick = { showDeleteGroupDialog = false }) { Text("Cancelar") } })
            if (showAddContactDialog) AddContactDialog(onDismiss = { showAddContactDialog = false }, onAdd = { u -> viewModel.addContact(u) { s, e -> if (s) showAddContactDialog = false else Toast.makeText(context, e ?: "Erro", Toast.LENGTH_SHORT).show() } })
            if (selectedChatForOptions != null) ChatOptionsBottomSheet(summary = selectedChatForOptions!!, isBlocked = blockedUsers.contains(selectedChatForOptions?.friendId), onDismiss = { selectedChatForOptions = null }, onOpen = { viewModel.setTargetId(it.friendId, it.isGroup) }, onClear = { viewModel.clearChat(it.friendId, it.isGroup) }, onDelete = { viewModel.deleteChat(it.friendId) }, onBlockToggle = { if(blockedUsers.contains(it)) viewModel.unblockUser(it) else viewModel.blockUser(it) })
            if (viewingStatuses != null) StatusViewer(userStatuses = viewingStatuses!!, myUsername = myUsername, onClose = { viewingStatuses = null }, onDelete = { viewModel.deleteStatus(it) })
        }
    }
}

// --- Sub-Composables Otimizados ---

@Composable
fun EmojiPicker(onEmojiSelected: (String) -> Unit) {
    val emojis = listOf(
        "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "😊", "😇", "🙂", "🙃", "😉", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😜", "🤪", "😝", "🤑", "🤗", "🤭", "🤫", "🤔", "🤐", "🤨", "😐", "😑", "😶", "😏", "😒", "🙄", "😬", "🤥", "😌", "😔", "😪", "🤤", "😴", "😷", "🤒", "🤕", "🤢", "🤮", "🤧", "🥵", "🥶", "🥴", "😵", "🤯", "🤠", "🥳", "😎", "🤓", "🧐", "😕", "😟", "🙁", "😮", "😯", "😲", "😳", "🥺", "😦", "😧", "😨", "😰", "😥", "😢", "😭", "😱", "😖", "😣", "😞", "😓", "😩", "😫", "🥱", "😤", "😡", "😠", "🤬", "😈", "👿", "💀", "💩", "🤡", "👹", "👺", "👻", "👽", "👾", "🤖",
        "👋", "🤚", "✋", "🖖", "👌", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "🖕", "👇", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "💪",
        "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝",
        "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐨", "🐯", "🦁", "🐮", "🐷", "🐽", "🐸", "🐵", "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🐛", "🦋", "🐌", "🐞", "🐜", "🦟", "🦗", "🕷️", "🦂", "🐢", "🐍", "🦎", "🦖", "🦕", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🐊", "🐅", "🐆", "🦓", "🦍", "🦧", "🐘", "🦛", "🦏", "🐪", "🐫", "🦒", "🦘", "🐃", "🐂", "🐄", "🐎", "🐖", "🐏", "🐑", "🐐", "🦌", "🐕", "🐩", "🦮", "🐕‍🦺", "🐈", "🐓", "🦃", "🦚", "🦜", "🦢", "🦩", "🕊️", "🐇", "🦝", "🦨", "🦡", "🦦", "🦥", "🐁", "🐀", "🐿️", "🦔", "🐾", "🐉", "🐲", "🌵", "🎄", "🌲", "🌳", "🌴", "🌱", "🌿", "☘️", "🍀", "🎍", "🎋", "🍃", "🍂", "🍁", "🍄", "🐚", "🌾", "💐", "🌷", "🌹", "🥀", "🌺", "🌸", "🌼", "🌻", "🌞", "🌝", "🌛", "🌜", "🌚", "🌕", "🌖", "🌗", "🌘", "🌑", "🌒", "🌓", "🌔", "🌙", "🌎", "🌍", "🌏", "🪐", "💫", "⭐", "🌟", "✨", "⚡", "☄️", "💥", "🔥", "🌪️", "🌈", "☀️", "🌤️", "⛅", "🌥️", "☁️", "🌦️", "🌧️", "⛈️", "🌩️", "❄️", "☃️", "⛄", "🌬️", "💨", "💧", "💦", "☔", "☂️", "🌊", "🌫️"
    )
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp),
        color = LocalChatColors.current.topBar
    ) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 45.dp),
            contentPadding = PaddingValues(8.dp)
        ) {
            items(emojis) { emoji ->
                Box(
                    modifier = Modifier
                        .size(45.dp)
                        .clickable { onEmojiSelected(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 24.sp)
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatTopBar(
    targetId: String,
    targetGroup: Group?,
    targetProfile: UserProfile?,
    activeChats: List<ChatSummary>,
    myPhotoUrl: String?,
    isTargetTyping: Boolean,
    showContacts: Boolean,
    showCreateGroup: Boolean,
    isSearching: Boolean,
    searchInput: String,
    onBack: () -> Unit,
    onSearchChange: (String) -> Unit,
    onSearchActiveChange: (Boolean) -> Unit,
    onCallClick: () -> Unit,
    onOptionClick: () -> Unit,
    onAddGroup: () -> Unit,
    onAddContact: () -> Unit
) {
    val context = LocalContext.current
    Column(modifier = Modifier.background(LocalChatColors.current.topBar)) {
        CenterAlignedTopAppBar(
            title = {
                if (targetId.isNotEmpty()) {
                    val currentChat = activeChats.find { it.friendId == targetId }
                    ChatHeaderTitle(targetGroup, targetProfile, currentChat, isTargetTyping)
                } else {
                    Text(if (showCreateGroup) "Novo Grupo" else if (showContacts) "Contatos" else "Conversas", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.ExtraBold)
                }
            },
            navigationIcon = {
                if (targetId.isNotEmpty() || showCreateGroup || showContacts) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, null, tint = MessengerBlue) }
                } else {
                    IconButton(onClick = { context.startActivity(Intent(context, SettingsActivity::class.java)) }) { 
                        AsyncImage(model = myPhotoUrl, contentDescription = null, modifier = Modifier.size(32.dp).clip(CircleShape).background(LocalChatColors.current.separator), contentScale = ContentScale.Crop) 
                    }
                }
            },
            actions = {
                if (targetId.isNotEmpty()) {
                    if (targetGroup == null) IconButton(onClick = onCallClick) { Icon(Icons.Rounded.Phone, null, tint = MessengerBlue) }
                    IconButton(onClick = onOptionClick) { Icon(Icons.Rounded.MoreHoriz, null, tint = MessengerBlue) }
                } else if (showContacts && !showCreateGroup) {
                    IconButton(onClick = onAddGroup) { Icon(Icons.Rounded.GroupAdd, null, tint = MessengerBlue) }
                    IconButton(onClick = onAddContact) { Icon(Icons.Rounded.PersonAdd, null, tint = MessengerBlue) }
                }
            },
            colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = LocalChatColors.current.topBar)
        )
        if (targetId.isEmpty() && !showCreateGroup) {
            MetaSearchBar(value = searchInput, onValueChange = onSearchChange, isSearching = isSearching, onActiveChange = onSearchActiveChange)
        }
    }
}

@Composable
fun ChatHeaderTitle(targetGroup: Group?, targetProfile: UserProfile?, currentChat: ChatSummary?, isTargetTyping: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        AsyncImage(model = targetGroup?.photoUrl ?: targetProfile?.photoUrl ?: currentChat?.friendPhotoUrl, contentDescription = null, modifier = Modifier.size(36.dp).clip(CircleShape).background(LocalChatColors.current.separator), contentScale = ContentScale.Crop)
        Spacer(Modifier.width(10.dp))
        Column {
            Text(targetGroup?.name ?: targetProfile?.name ?: currentChat?.friendName ?: "", style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (isTargetTyping) Text("Digitando...", style = MaterialTheme.typography.labelSmall, color = MessengerBlue)
            else if (targetGroup != null) Text("${targetGroup.members.size} membros", style = MaterialTheme.typography.labelSmall, color = MetaGray4)
            else targetProfile?.let { PresenceIndicator(it) }
        }
    }
}

@Composable
fun PresenceIndicator(profile: UserProfile) {
    val presenceColor = when(profile.presenceStatus) {
        "Online" -> iOSGreen
        "Ocupado" -> iOSRed
        "Ausente" -> iOSOrange
        else -> MetaGray4
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        if (profile.isOnline && profile.presenceStatus != "Invisível") {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(presenceColor))
            Spacer(Modifier.width(4.dp))
            Text(profile.presenceStatus, style = MaterialTheme.typography.labelSmall, color = presenceColor)
        } else {
            Text("Offline", style = MaterialTheme.typography.labelSmall, color = MetaGray4)
        }
    }
}

@Composable
fun ConversationListContent(
    isSearching: Boolean,
    searchInput: String,
    searchResults: List<UserProfile>,
    filteredChats: List<ChatSummary>,
    statuses: List<UserStatus>,
    myPhotoUrl: String?,
    myUsername: String,
    contacts: List<UserProfile>,
    onStatusAdd: () -> Unit,
    onStatusView: (List<UserStatus>) -> Unit,
    onChatClick: (ChatSummary) -> Unit,
    onChatLongClick: (ChatSummary) -> Unit,
    onUserSearchClick: (UserProfile) -> Unit,
    onAddContactSearch: (String) -> Unit
) {
    val view = LocalView.current
    Column(modifier = Modifier.fillMaxSize()) {
        if (!isSearching) MetaStatusRow(statuses, myPhotoUrl, myUsername, onStatusAdd, onStatusView)
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (isSearching && searchInput.isNotEmpty()) {
                items(searchResults, key = { it.id }) { user ->
                    val isContact = contacts.any { it.id == user.id }
                    MetaUserItem(user, isContact = isContact, onChatClick = { onUserSearchClick(user) }, onAddContactClick = { onAddContactSearch(user.id) })
                }
            } else {
                itemsIndexed(filteredChats, key = { _, s -> s.friendId }) { index, summary ->
                    Column {
                        MetaChatItem(summary, onClick = { onChatClick(summary) }, onLongClick = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); onChatLongClick(summary) })
                        if (index < filteredChats.size - 1) HorizontalDivider(modifier = Modifier.padding(start = 88.dp), thickness = 0.5.dp, color = LocalChatColors.current.separator)
                    }
                }
            }
        }
    }
}

@Composable
fun MessageListContent(
    messages: List<Message>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    myUsername: String,
    targetProfile: UserProfile?,
    onImageClick: (String) -> Unit,
    onDelete: (Message) -> Unit,
    onReply: (Message) -> Unit,
    onReact: (Message, String) -> Unit,
    onEdit: (Message) -> Unit,
    onPin: (Message) -> Unit
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp), contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
            val isMe = message.senderId == myUsername
            val prevMsg = if (index > 0) messages[index - 1] else null
            val nextMsg = if (index < messages.size - 1) messages[index + 1] else null
            val isFirstInGroup = prevMsg == null || prevMsg.senderId != message.senderId || (message.timestamp - prevMsg.timestamp > 60000)
            val isLastInGroup = nextMsg == null || nextMsg.senderId != message.senderId || (nextMsg.timestamp - message.timestamp > 60000)
            if (isFirstInGroup) {
                val dateText = formatDateHeader(message.timestamp)
                if (dateText != (prevMsg?.let { formatDateHeader(it.timestamp) } ?: "")) DateHeader(dateText)
            }
            MetaMessageBubble(message = message, isMe = isMe, targetPhotoUrl = if (message.isGroup) message.senderPhotoUrl else targetProfile?.photoUrl, isFirstInGroup = isFirstInGroup, isLastInGroup = isLastInGroup, onImageClick = onImageClick, onDelete = { onDelete(message) }, onReply = { onReply(message) }, onReact = { onReact(message, it) }, onEdit = { onEdit(message) }, onPin = { onPin(message) })
        }
    }
}

@Composable
fun ChatInputSection(textState: String, onTextChange: (String) -> Unit, replyingTo: Message?, editingMessage: Message?, pinnedMessage: Message?, recordingDuration: Long, onSend: () -> Unit, onAddClick: () -> Unit, onAudioStart: () -> Unit, onAudioStop: (Boolean) -> Unit, onEmojiClick: () -> Unit, onCancelReply: () -> Unit, onUnpin: () -> Unit) {
    Column {
        if (pinnedMessage != null) PinnedHeader(pinnedMessage, onUnpin)
        if (replyingTo != null || editingMessage != null) ReplyHeader(replyingTo, editingMessage, onCancelReply)
        MetaInput(text = textState, onValueChange = onTextChange, onAddClick = onAddClick, onSend = onSend, onAudioStart = onAudioStart, onAudioStop = onAudioStop, onEmojiClick = onEmojiClick, recordingDuration = recordingDuration)
    }
}

@Composable
fun PinnedHeader(message: Message, onUnpin: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), color = LocalChatColors.current.secondaryBackground, shape = RoundedCornerShape(8.dp)) {
        Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(2.dp).height(30.dp).background(MessengerBlue)); Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) { Text("Mensagem Fixada", color = MessengerBlue, fontSize = 11.sp, fontWeight = FontWeight.Bold); Text(message.text.ifEmpty { "Mídia" }, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 13.sp) }
            IconButton(onClick = onUnpin, modifier = Modifier.size(24.dp)) { Icon(Icons.Default.Close, null, modifier = Modifier.size(16.dp)) }
        }
    }
}

@Composable
fun ReplyHeader(replyingTo: Message?, editingMessage: Message?, onCancel: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), color = LocalChatColors.current.secondaryBackground, shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(4.dp).height(40.dp).background(MessengerBlue, RoundedCornerShape(2.dp))); Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (editingMessage != null) "Editar mensagem" else (replyingTo?.senderName ?: ""), color = MessengerBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                val text = editingMessage?.text ?: replyingTo?.text ?: if (replyingTo?.imageUrl != null) "📷 Imagem" else if (replyingTo?.audioUrl != null) "🎤 Áudio" else ""
                Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            }
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
fun ChatOptionsMenu(expanded: Boolean, onDismiss: () -> Unit, targetGroup: Group?, myUsername: String, targetId: String, blockedUsers: List<String>, tempMessageDuration: Int, onClearChat: () -> Unit, onDeleteGroup: () -> Unit, onToggleTempMessages: () -> Unit, onBlockToggle: () -> Unit) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss, modifier = Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)).width(220.dp).border(0.5.dp, LocalChatColors.current.separator, RoundedCornerShape(16.dp))) {
        DropdownMenuItem(text = { Text("Limpar conversa") }, leadingIcon = { Icon(Icons.Rounded.DeleteSweep, null, tint = MessengerBlue) }, onClick = { onDismiss(); onClearChat() })
        if (targetGroup != null) {
            if (targetGroup.createdBy == myUsername) {
                HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp, color = LocalChatColors.current.separator)
                DropdownMenuItem(text = { Text("Apagar Grupo", color = iOSRed, fontWeight = FontWeight.SemiBold) }, leadingIcon = { Icon(Icons.Rounded.DeleteForever, null, tint = iOSRed) }, onClick = { onDismiss(); onDeleteGroup() })
            }
        } else {
            DropdownMenuItem(text = { Text("Mensagens Temporárias (${if(tempMessageDuration > 0) "${tempMessageDuration}h" else "Off"})") }, leadingIcon = { Icon(Icons.Rounded.Timer, null) }, onClick = { onDismiss(); onToggleTempMessages() })
            HorizontalDivider(modifier = Modifier.padding(horizontal = 12.dp), thickness = 0.5.dp, color = LocalChatColors.current.separator)
            val isBlocked = blockedUsers.contains(targetId)
            DropdownMenuItem(text = { Text(if(isBlocked) "Desbloquear" else "Bloquear", color = iOSRed, fontWeight = FontWeight.SemiBold) }, leadingIcon = { Icon(if(isBlocked) Icons.Default.LockOpen else Icons.Default.Block, null, tint = iOSRed) }, onClick = { onDismiss(); onBlockToggle() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatOptionsBottomSheet(summary: ChatSummary, isBlocked: Boolean, onDismiss: () -> Unit, onOpen: (ChatSummary) -> Unit, onClear: (ChatSummary) -> Unit, onDelete: (ChatSummary) -> Unit, onBlockToggle: (String) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LocalChatColors.current.secondaryBackground) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 32.dp)) {
            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(model = summary.friendPhotoUrl, contentDescription = null, modifier = Modifier.size(48.dp).clip(CircleShape).background(LocalChatColors.current.separator), contentScale = ContentScale.Crop)
                Spacer(Modifier.width(16.dp)); Text(summary.friendName ?: summary.friendId, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(color = LocalChatColors.current.separator, thickness = 0.5.dp)
            ListItem(headlineContent = { Text("Abrir") }, leadingContent = { Icon(Icons.Rounded.Chat, null, tint = MessengerBlue) }, modifier = Modifier.clickable { onOpen(summary); onDismiss() })
            ListItem(headlineContent = { Text("Limpar conversa") }, leadingContent = { Icon(Icons.Rounded.DeleteSweep, null, tint = MessengerBlue) }, modifier = Modifier.clickable { onClear(summary); onDismiss() })
            ListItem(headlineContent = { Text("Excluir conversa", color = iOSRed) }, leadingContent = { Icon(Icons.Rounded.Delete, null, tint = iOSRed) }, modifier = Modifier.clickable { onDelete(summary); onDismiss() })
            if (!summary.isGroup) ListItem(headlineContent = { Text(if(isBlocked) "Desbloquear" else "Bloquear", color = iOSRed) }, leadingContent = { Icon(if(isBlocked) Icons.Default.LockOpen else Icons.Default.Block, null, tint = iOSRed) }, modifier = Modifier.clickable { onBlockToggle(summary.friendId); onDismiss() })
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AttachmentBottomSheet(onCamera: () -> Unit, onGallery: () -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = LocalChatColors.current.secondaryBackground) {
        Row(modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp, top = 20.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            AttachmentItem(icon = Icons.Rounded.PhotoCamera, label = "Câmera", color = iOSRed) { onDismiss(); onCamera() }
            AttachmentItem(icon = Icons.Rounded.Image, label = "Galeria", color = Color(0xFF5856D6)) { onDismiss(); onGallery() }
        }
    }
}

@Composable
fun FullScreenImage(url: String, onDismiss: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(Color.Black).clickable { onDismiss() }, contentAlignment = Alignment.Center) {
        AsyncImage(model = url, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd).padding(40.dp)) { Icon(Icons.Rounded.Close, null, tint = Color.White, modifier = Modifier.size(30.dp)) }
    }
}

// --- Restante dos Componentes Originais (Login, Status, Mensagens, etc.) ---

@Composable
fun LoginScreen(viewModel: ChatViewModel) {
    var email by remember { mutableStateOf("") }; var password by remember { mutableStateOf("") }
    var username by remember { mutableStateOf("") }; var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    var isSignUp by remember { mutableStateOf(false) }; var loading by remember { mutableStateOf(false) }
    var showResetPassword by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> selectedImageUri = uri }
    Box(modifier = Modifier.fillMaxSize()) {
        Box(modifier = Modifier.fillMaxWidth().height(300.dp).background(Brush.verticalGradient(listOf(MessengerBlue.copy(alpha = 0.1f), Color.Transparent))))
        Column(modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(modifier = Modifier.height(80.dp))
            Surface(modifier = Modifier.size(90.dp), shape = RoundedCornerShape(24.dp), color = MessengerBlue, shadowElevation = 8.dp) { Box(contentAlignment = Alignment.Center) { Icon(Icons.Rounded.ChatBubble, null, modifier = Modifier.size(45.dp), tint = Color.White) } }
            Spacer(modifier = Modifier.height(24.dp)); Text("Friend", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Black, color = MessengerBlue); Text(if (isSignUp) "Crie sua conta agora" else "Conecte-se com seus amigos", style = MaterialTheme.typography.bodyMedium, color = MetaGray4); Spacer(modifier = Modifier.height(48.dp))
            if (isSignUp) {
                Box(modifier = Modifier.size(100.dp).clip(CircleShape).background(LocalChatColors.current.tertiaryBackground).clickable { photoLauncher.launch("image/*") }.border(2.dp, if(selectedImageUri != null) MessengerBlue else Color.Transparent, CircleShape), contentAlignment = Alignment.Center) {
                    if (selectedImageUri != null) AsyncImage(model = selectedImageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    else Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.AddAPhoto, null, tint = MessengerBlue); Text("Foto", fontSize = 10.sp, color = MessengerBlue, fontWeight = FontWeight.Bold) }
                }
                Spacer(modifier = Modifier.height(24.dp)); MetaTextField(username, { username = it }, "Nome de usuário", Icons.Rounded.Person); Spacer(modifier = Modifier.height(16.dp))
            }
            MetaTextField(email, { email = it }, "E-mail", Icons.Rounded.Email, keyboardType = KeyboardType.Email); Spacer(modifier = Modifier.height(16.dp)); MetaTextField(password, { password = it }, "Senha", Icons.Rounded.Lock, isPassword = true)
            if (!isSignUp) Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterEnd) { TextButton(onClick = { showResetPassword = true }) { Text("Esqueceu a senha?", color = MessengerBlue, fontWeight = FontWeight.SemiBold, fontSize = 13.sp) } }
            else Spacer(modifier = Modifier.height(16.dp))
            Spacer(modifier = Modifier.height(24.dp))
            if (loading) CircularProgressIndicator(color = MessengerBlue, modifier = Modifier.size(32.dp))
            else {
                Button(onClick = { loading = true; if (isSignUp) viewModel.signUp(email, password, username, selectedImageUri) { s, e -> loading = false; if (!s) Toast.makeText(context, e ?: "Erro", Toast.LENGTH_SHORT).show() } else viewModel.login(email, password) { s, e -> loading = false; if (!s) Toast.makeText(context, e ?: "Erro", Toast.LENGTH_SHORT).show() } }, modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp), colors = ButtonDefaults.buttonColors(containerColor = MessengerBlue)) { Text(if (isSignUp) "Criar Conta" else "Entrar", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.height(24.dp)); Row(verticalAlignment = Alignment.CenterVertically) { HorizontalDivider(modifier = Modifier.weight(1f), color = LocalChatColors.current.separator); Text("OU", modifier = Modifier.padding(horizontal = 16.dp), color = MetaGray4, fontSize = 12.sp, fontWeight = FontWeight.Bold); HorizontalDivider(modifier = Modifier.weight(1f), color = LocalChatColors.current.separator) }
                TextButton(onClick = { isSignUp = !isSignUp }, modifier = Modifier.padding(vertical = 16.dp)) { Text(if (isSignUp) "Já tem uma conta? Conectar" else "Novo por aqui? Crie um perfil", color = MessengerBlue, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold) }
            }
            Spacer(modifier = Modifier.height(40.dp))
        }
    }
    if (showResetPassword) {
        var resetEmail by remember { mutableStateOf(email) }
        AlertDialog(onDismissRequest = { showResetPassword = false }, title = { Text("Recuperar Senha") }, text = { Column { Text("Enviaremos um link de redefinição para o seu e-mail."); Spacer(Modifier.height(16.dp)); MetaTextField(resetEmail, { resetEmail = it }, "E-mail de cadastro", Icons.Rounded.Email) } }, confirmButton = { Button(onClick = { if (resetEmail.isNotBlank()) viewModel.resetPassword(resetEmail) { s, e -> if (s) { Toast.makeText(context, "Link enviado!", Toast.LENGTH_LONG).show(); showResetPassword = false } else Toast.makeText(context, e ?: "Erro", Toast.LENGTH_SHORT).show() } }) { Text("Enviar Link") } }, dismissButton = { TextButton(onClick = { showResetPassword = false }) { Text("Cancelar") } }, shape = RoundedCornerShape(20.dp))
    }
}

@Composable
fun MetaTextField(value: String, onValueChange: (String) -> Unit, placeholder: String, icon: ImageVector, isPassword: Boolean = false, keyboardType: KeyboardType = KeyboardType.Text) {
    TextField(value = value, onValueChange = onValueChange, placeholder = { Text(placeholder, color = MetaGray4) }, modifier = Modifier.fillMaxWidth(), leadingIcon = { Icon(icon, null, tint = MetaGray4, modifier = Modifier.size(20.dp)) }, visualTransformation = if (isPassword) PasswordVisualTransformation() else VisualTransformation.None, keyboardOptions = KeyboardOptions(keyboardType = keyboardType), shape = RoundedCornerShape(16.dp), singleLine = true, textStyle = MaterialTheme.typography.bodyLarge, colors = TextFieldDefaults.colors(focusedContainerColor = LocalChatColors.current.tertiaryBackground, unfocusedContainerColor = LocalChatColors.current.tertiaryBackground, focusedIndicatorColor = Color.Transparent, unfocusedIndicatorColor = Color.Transparent, cursorColor = MessengerBlue, focusedTextColor = MaterialTheme.colorScheme.onSurface, unfocusedTextColor = MaterialTheme.colorScheme.onSurface))
}

@Composable
fun PinLockScreen(correctPin: String, isBiometricEnabled: Boolean, onUnlock: () -> Unit) {
    var pinInput by remember { mutableStateOf("") }; val activity = LocalActivity.current; val view = LocalView.current
    fun showBiometricPrompt() { val fragmentActivity = activity as? FragmentActivity ?: return; val executor = ContextCompat.getMainExecutor(fragmentActivity); val biometricPrompt = androidx.biometric.BiometricPrompt(fragmentActivity, executor, object : androidx.biometric.BiometricPrompt.AuthenticationCallback() { override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) { super.onAuthenticationSucceeded(result); onUnlock() } }); val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder().setTitle("Bloqueio de Segurança").setSubtitle("Uma biometria para entrar").setNegativeButtonText("Usar PIN").build(); biometricPrompt.authenticate(promptInfo) }
    LaunchedEffect(Unit) { if (isBiometricEnabled) showBiometricPrompt() }
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Rounded.Lock, null, modifier = Modifier.size(64.dp), tint = MessengerBlue); Spacer(modifier = Modifier.height(24.dp)); Text("App Protegido", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(modifier = Modifier.height(32.dp)); Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) { repeat(4) { index -> Box(modifier = Modifier.size(16.dp).clip(CircleShape).background(if (pinInput.length > index) MessengerBlue else LocalChatColors.current.separator)) } }; Spacer(modifier = Modifier.height(48.dp))
        val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "DEL")
        LazyVerticalGrid(columns = GridCells.Fixed(3), modifier = Modifier.width(280.dp), verticalArrangement = Arrangement.spacedBy(20.dp), horizontalArrangement = Arrangement.spacedBy(20.dp)) { items(keys) { key -> if (key.isNotEmpty()) { val isDel = key == "DEL"; Box(modifier = Modifier.size(75.dp).clip(CircleShape).background(LocalChatColors.current.tertiaryBackground).clickable { view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY); if (isDel) { if (pinInput.isNotEmpty()) pinInput = pinInput.dropLast(1) } else if (pinInput.length < 4) { pinInput += key; if (pinInput.length == 4) { if (pinInput == correctPin) onUnlock() else { pinInput = ""; if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) view.performHapticFeedback(HapticFeedbackConstants.REJECT) } } } }, contentAlignment = Alignment.Center) { if (isDel) Icon(Icons.AutoMirrored.Filled.Backspace, null, tint = MaterialTheme.colorScheme.onSurface) else Text(key, style = MaterialTheme.typography.titleMedium.copy(fontSize = 28.sp)) } } else Spacer(Modifier.size(75.dp)) } }
        if (isBiometricEnabled) IconButton(onClick = { showBiometricPrompt() }, modifier = Modifier.padding(top = 24.dp)) { Icon(Icons.Rounded.Fingerprint, null, modifier = Modifier.size(40.dp), tint = MessengerBlue) }
    }
}

@Composable
fun CreateGroupScreen(contacts: List<UserProfile>, onCreate: (String, List<String>, Uri?) -> Unit) {
    var groupName by remember { mutableStateOf("") }; var selectedMembers by remember { mutableStateOf(setOf<String>()) }; var selectedImageUri by remember { mutableStateOf<Uri?>(null) }
    val photoLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> selectedImageUri = uri }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(64.dp).clip(CircleShape).background(LocalChatColors.current.separator).clickable { photoLauncher.launch("image/*") }, contentAlignment = Alignment.Center) { if (selectedImageUri != null) AsyncImage(model = selectedImageUri, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) else Icon(Icons.Rounded.PhotoCamera, null, tint = MessengerBlue) }; Spacer(Modifier.width(16.dp)); MetaTextField(groupName, { groupName = it }, "Nome do grupo", Icons.Rounded.Group) }
        Spacer(Modifier.height(24.dp)); Text("Selecionar Membros (${selectedMembers.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(8.dp)); LazyColumn(modifier = Modifier.weight(1f)) { items(contacts) { contact -> Row(modifier = Modifier.fillMaxWidth().clickable { if (selectedMembers.contains(contact.id)) selectedMembers -= contact.id else selectedMembers += contact.id }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(model = contact.photoUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape).background(LocalChatColors.current.separator), contentScale = ContentScale.Crop); Spacer(Modifier.width(12.dp)); Text(contact.name, modifier = Modifier.weight(1f)); Checkbox(checked = selectedMembers.contains(contact.id), onCheckedChange = null) } } }
        Button(onClick = { if (groupName.isNotBlank() && selectedMembers.isNotEmpty()) onCreate(groupName, selectedMembers.toList(), selectedImageUri) }, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), colors = ButtonDefaults.buttonColors(containerColor = MessengerBlue)) { Text("Criar Grupo", color = Color.White, fontWeight = FontWeight.Bold) }
    }
}

@Composable
fun TelegramTabs(selected: String, onSelect: (String) -> Unit) {
    val tabs = listOf("Tudo", "Grupos", "Não Lidas")
    TabRow(selectedTabIndex = tabs.indexOf(selected), containerColor = Color.Transparent, contentColor = MessengerBlue, divider = {}, indicator = { tabPositions -> TabRowDefaults.SecondaryIndicator(modifier = Modifier.tabIndicatorOffset(tabPositions[tabs.indexOf(selected)]), color = MessengerBlue) }) { tabs.forEach { title -> Tab(selected = selected == title, onClick = { onSelect(title) }, text = { Text(title, style = MaterialTheme.typography.labelLarge) }) } }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StatusViewer(userStatuses: List<UserStatus>, myUsername: String, onClose: () -> Unit, onDelete: (String) -> Unit) {
    var currentIndex by remember { mutableIntStateOf(0) }; val currentStatus = userStatuses[currentIndex]; var progress by remember { mutableStateOf(0f) }; var showViewers by remember { mutableStateOf(false) }
    LaunchedEffect(currentIndex) { progress = 0f; val duration = 5000L; val steps = 100; val stepTime = duration / steps; for (i in 1..steps) { if (!showViewers) { delay(stepTime); progress = i.toFloat() / steps } }; if (currentIndex < userStatuses.size - 1) currentIndex++ else onClose() }
    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AsyncImage(model = currentStatus.imageUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        Row(modifier = Modifier.fillMaxSize()) { Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable { if (currentIndex > 0) currentIndex-- }); Box(modifier = Modifier.weight(1f).fillMaxHeight().clickable { if (currentIndex < userStatuses.size - 1) currentIndex++ else onClose() }) }
        Column(modifier = Modifier.fillMaxWidth().padding(top = 40.dp)) { Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) { userStatuses.forEachIndexed { index, _ -> LinearProgressIndicator(progress = { if (index < currentIndex) 1f else if (index == currentIndex) progress else 0f }, modifier = Modifier.weight(1f).height(2.dp).clip(RoundedCornerShape(1.dp)), color = Color.White, trackColor = Color.White.copy(alpha = 0.3f)) } }; Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(model = currentStatus.userPhotoUrl, contentDescription = null, modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.Gray), contentScale = ContentScale.Crop); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(currentStatus.username, color = Color.White, fontWeight = FontWeight.Bold); Text(SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(currentStatus.timestamp)), color = Color.White.copy(0.7f), fontSize = 12.sp) }; if (currentStatus.userId == myUsername) { IconButton(onClick = { onDelete(currentStatus.id) }) { Icon(Icons.Rounded.Delete, null, tint = Color.White) } }; IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, null, tint = Color.White) } } }
        if (currentStatus.userId == myUsername) { Column(modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) { IconButton(onClick = { showViewers = true }) { Column(horizontalAlignment = Alignment.CenterHorizontally) { Icon(Icons.Rounded.Visibility, null, tint = Color.White); Text(currentStatus.viewers.size.toString(), color = Color.White, fontSize = 12.sp) } }; Text("Vistos", color = Color.White.copy(0.8f), fontSize = 12.sp) } }
    }
    if (showViewers) { ModalBottomSheet(onDismissRequest = { showViewers = false }, containerColor = MaterialTheme.colorScheme.surface) { Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) { Text("Visto por ${currentStatus.viewers.size} pessoas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold); Spacer(Modifier.height(16.dp)); LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp)) { items(currentStatus.viewers.keys.toList()) { viewerId -> Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.size(40.dp).clip(CircleShape).background(Color.LightGray)); Spacer(Modifier.width(12.dp)); Text(viewerId, style = MaterialTheme.typography.bodyLarge) } } } } } }
}

@Composable
fun ContactsScreen(contacts: List<UserProfile>, onContactClick: (UserProfile) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) { if (contacts.isEmpty()) { item { Box(modifier = Modifier.fillParentMaxSize(), contentAlignment = Alignment.Center) { Text("Nenhum contato adicionado", color = MetaGray4) } } } else { items(contacts) { contact -> Row(modifier = Modifier.fillMaxWidth().clickable { onContactClick(contact) }.padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Box { AsyncImage(model = contact.photoUrl, contentDescription = null, modifier = Modifier.size(50.dp).clip(CircleShape).background(LocalChatColors.current.separator), contentScale = ContentScale.Crop); val presenceColor = when(contact.presenceStatus) { "Online" -> iOSGreen; "Ocupado" -> iOSRed; "Ausente" -> iOSOrange; else -> Color.Gray }; if (contact.isOnline && contact.presenceStatus != "Invisível") Box(modifier = Modifier.align(Alignment.BottomEnd).size(14.dp).background(MaterialTheme.colorScheme.background, CircleShape).padding(2.dp).background(presenceColor, CircleShape)) }; Column(modifier = Modifier.padding(start = 12.dp)) { Text(contact.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold); Text(contact.status, style = MaterialTheme.typography.bodyMedium, color = MetaGray4, maxLines = 1, overflow = TextOverflow.Ellipsis) } } } } }
}

@Composable
fun AddContactDialog(onDismiss: () -> Unit, onAdd: (String) -> Unit) {
    var username by remember { mutableStateOf("") }; AlertDialog(onDismissRequest = onDismiss, title = { Text("Adicionar Contato") }, text = { Column { Text("Digite o username exato do usuário:"); Spacer(Modifier.height(12.dp)); MetaTextField(username, { username = it }, "Username", Icons.Rounded.Person) } }, confirmButton = { TextButton(onClick = { if (username.isNotBlank()) onAdd(username) }) { Text("Adicionar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

fun formatDateHeader(timestamp: Long): String {
    val calendar = Calendar.getInstance(); val now = Calendar.getInstance(); calendar.timeInMillis = timestamp
    return when { calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) && calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> "HOJE"; calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) && calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1 -> "ONTEM"; else -> SimpleDateFormat("d 'DE' MMMM", Locale("pt", "BR")).format(Date(timestamp)).uppercase() }
}

@Composable
fun DateHeader(text: String) {
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = MetaGray4, fontWeight = FontWeight.Bold, modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp), textAlign = TextAlign.Center)
}

@Composable
fun AttachmentItem(icon: ImageVector, label: String, color: Color, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) { Box(modifier = Modifier.size(60.dp).background(color, CircleShape), contentAlignment = Alignment.Center) { Icon(icon, null, tint = Color.White, modifier = Modifier.size(30.dp)) }; Spacer(modifier = Modifier.height(8.dp)); Text(label, style = MaterialTheme.typography.labelSmall) }
}

@Composable
fun MetaSearchBar(value: String, onValueChange: (String) -> Unit, isSearching: Boolean, onActiveChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(LocalChatColors.current.topBar).padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { Box(modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(LocalChatColors.current.tertiaryBackground).padding(horizontal = 12.dp), contentAlignment = Alignment.CenterStart) { Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Rounded.Search, null, tint = MetaGray4, modifier = Modifier.size(20.dp)); Spacer(Modifier.width(8.dp)); Box(modifier = Modifier.weight(1f)) { if (value.isEmpty()) Text("Pesquisar", color = MetaGray4, style = MaterialTheme.typography.bodyLarge); BasicTextField(value = value, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth().onFocusChanged { if (it.isFocused) onActiveChange(true) }, singleLine = true, textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MessengerBlue)) }; if (value.isNotEmpty()) IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(24.dp)) { Icon(Icons.Rounded.Close, null, tint = MetaGray4, modifier = Modifier.size(18.dp)) } } }; AnimatedVisibility(visible = isSearching, enter = expandHorizontally() + fadeIn(), exit = shrinkHorizontally() + fadeOut()) { Text(text = "Cancelar", color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(start = 12.dp).clickable { onActiveChange(false); onValueChange("") }, style = MaterialTheme.typography.bodyLarge) } }
}

@Composable
fun MetaStatusRow(statuses: List<UserStatus>, myPhotoUrl: String?, myUsername: String, onAdd: () -> Unit, onViewUserStatuses: (List<UserStatus>) -> Unit) {
    val grouped = statuses.groupBy { it.userId }; val myStatuses = grouped[myUsername] ?: emptyList(); val otherStatuses = grouped.filter { it.key != myUsername }; val instaGradient = Brush.linearGradient(colors = InstagramStoryBorder)
    LazyRow(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) { item { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { if (myStatuses.isNotEmpty()) onViewUserStatuses(myStatuses) else onAdd() }) { Box { AsyncImage(model = myPhotoUrl, contentDescription = null, modifier = Modifier.size(65.dp).clip(CircleShape).background(LocalChatColors.current.separator), contentScale = ContentScale.Crop); if (myStatuses.isEmpty()) Icon(Icons.Rounded.Add, null, modifier = Modifier.align(Alignment.BottomEnd).size(22.dp).background(Color.White, CircleShape).padding(2.dp).background(MessengerBlue, CircleShape).padding(2.dp), tint = Color.White) else Box(modifier = Modifier.size(65.dp).drawBehind { drawCircle(brush = instaGradient, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())) }) }; Text("Meu status", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp)) } }; otherStatuses.forEach { (_, userList) -> val first = userList.first(); item { Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onViewUserStatuses(userList) }) { Box(modifier = Modifier.size(65.dp).drawBehind { drawCircle(brush = instaGradient, style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx())) }.padding(4.dp).clip(CircleShape)) { AsyncImage(model = first.userPhotoUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop) }; Text(first.username, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis) } } } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetaChatItem(summary: ChatSummary, onClick: () -> Unit, onLongClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().combinedClickable(onClick = onClick, onLongClick = onLongClick).padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) { Box { AsyncImage(model = summary.friendPhotoUrl, contentDescription = null, modifier = Modifier.size(60.dp).clip(CircleShape).background(LocalChatColors.current.separator), contentScale = ContentScale.Crop); val presenceColor = when(summary.presenceStatus) { "Online" -> iOSGreen; "Ocupado" -> iOSRed; "Ausente" -> iOSOrange; else -> Color.Gray }; if (!summary.isGroup && summary.isOnline && summary.presenceStatus != "Invisível") Box(modifier = Modifier.align(Alignment.BottomEnd).size(16.dp).background(MaterialTheme.colorScheme.background, CircleShape).padding(2.dp).background(presenceColor, CircleShape)) }; Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) { Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(summary.friendName ?: summary.friendId, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = if (summary.hasUnread) FontWeight.Bold else FontWeight.Normal), maxLines = 1, overflow = TextOverflow.Ellipsis); val time = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(summary.timestamp)); Text(time, style = MaterialTheme.typography.labelSmall, color = if (summary.hasUnread) MessengerBlue else MetaGray4) }; Row(verticalAlignment = Alignment.CenterVertically) { val lastMessage = if (summary.isTyping) "Digitando..." else summary.lastMessage; val color = if (summary.isTyping) MessengerBlue else if (summary.hasUnread) MaterialTheme.colorScheme.onSurface else MetaGray4; Text(text = lastMessage, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = if (summary.hasUnread) FontWeight.Bold else FontWeight.Normal), color = color, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f)); if (summary.hasUnread) Box(modifier = Modifier.size(12.dp).background(MessengerBlue, CircleShape)) } } }
}

@Composable
fun MetaUserItem(user: UserProfile, isContact: Boolean, onChatClick: () -> Unit, onAddContactClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) { AsyncImage(model = user.photoUrl, contentDescription = null, modifier = Modifier.size(50.dp).clip(CircleShape).background(LocalChatColors.current.separator), contentScale = ContentScale.Crop); Spacer(modifier = Modifier.width(12.dp)); Column(modifier = Modifier.weight(1f)) { Text(user.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold); Text("@${user.id.lowercase()}", style = MaterialTheme.typography.labelSmall, color = MetaGray4) }; Row { if (!isContact) IconButton(onClick = onAddContactClick) { Icon(Icons.Rounded.PersonAdd, null, tint = MessengerBlue) }; IconButton(onClick = onChatClick) { Icon(Icons.Rounded.ChatBubble, null, tint = MessengerBlue) } } }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetaMessageBubble(message: Message, isMe: Boolean, targetPhotoUrl: String?, isFirstInGroup: Boolean, isLastInGroup: Boolean, onImageClick: (String) -> Unit, onDelete: (String) -> Unit, onReply: () -> Unit, onReact: (String) -> Unit, onEdit: () -> Unit, onPin: () -> Unit) {
    val isSingleEmoji = AnimatedEmojiHelper.isSingleEmoji(message.text) &&
                       message.imageUrl == null &&
                       message.audioUrl == null &&
                       message.replyToId == null

    val animUrl = if (isSingleEmoji) AnimatedEmojiHelper.getAnimUrl(message.text) else null

    val bubbleColor = if (isMe) MessengerBlue else LocalChatColors.current.bubbleOther
    val textColor = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    var showContext by remember { mutableStateOf(false) }

    val shape = if (isMe) RoundedCornerShape(topStart = 20.dp, topEnd = if (isFirstInGroup) 20.dp else 4.dp, bottomEnd = if (isLastInGroup) 20.dp else 4.dp, bottomStart = 20.dp)
                else RoundedCornerShape(topStart = if (isFirstInGroup) 20.dp else 4.dp, topEnd = 20.dp, bottomEnd = 20.dp, bottomStart = if (isLastInGroup) 20.dp else 4.dp)

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 1.dp), horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start, verticalAlignment = Alignment.Bottom) {
        if (!isMe) {
            if (isLastInGroup) AsyncImage(model = targetPhotoUrl, contentDescription = null, modifier = Modifier.size(28.dp).clip(CircleShape).background(LocalChatColors.current.separator), contentScale = ContentScale.Crop)
            else Spacer(Modifier.width(36.dp))
        }
        Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start) {
            if (message.isGroup && !isMe && isFirstInGroup) Text(message.senderName ?: "", style = MaterialTheme.typography.labelSmall, color = MetaGray4, modifier = Modifier.padding(start = 12.dp, bottom = 2.dp))

            if (isSingleEmoji) {
                Box(modifier = Modifier.padding(8.dp)) {
                    if (animUrl != null) {
                        AnimatedEmoji(emoji = message.text, modifier = Modifier.size(100.dp), onLongClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                            showContext = true
                        })
                    } else {
                        Text(
                            text = message.text,
                            fontSize = 56.sp,
                            modifier = Modifier.combinedClickable(
                                onClick = { showContext = true },
                                onLongClick = {
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    showContext = true
                                }
                            )
                        )
                    }
                }
            } else {
                Surface(color = bubbleColor, shape = shape, modifier = Modifier.widthIn(max = 280.dp).combinedClickable(onClick = { if (message.imageUrl != null) onImageClick(message.imageUrl!!) }, onLongClick = { view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS); showContext = true })) {
                    Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)) {
                        if (message.replyToId != null) {
                            Surface(color = Color.Black.copy(0.1f), shape = RoundedCornerShape(8.dp), modifier = Modifier.padding(bottom = 4.dp).fillMaxWidth()) {
                                Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(modifier = Modifier.width(2.dp).height(30.dp).background(MessengerBlue)); Column(modifier = Modifier.padding(start = 8.dp)) { Text(message.replyToName ?: "", color = MessengerBlue, fontWeight = FontWeight.Bold, fontSize = 11.sp); Text(message.replyToText ?: "", color = textColor.copy(0.7f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                                }
                            }
                        }
                        if (message.imageUrl != null) AsyncImage(model = message.imageUrl, contentDescription = null, modifier = Modifier.fillMaxWidth().heightIn(max = 300.dp).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Crop)
                        if (message.audioUrl != null) AudioPlayerBubble(message.audioUrl!!, message.localAudioPath, isMe)
                        Row(verticalAlignment = Alignment.Bottom) {
                            if (message.text.isNotEmpty()) Text(text = message.text, style = MaterialTheme.typography.bodyLarge.copy(color = textColor), modifier = Modifier.weight(1f, fill = false))
                            if (message.isEdited) Text("editada", style = MaterialTheme.typography.labelSmall, color = textColor.copy(alpha = 0.6f), modifier = Modifier.padding(start = 4.dp))
                        }
                    }
                }
            }

            if (!message.reactions.isNullOrEmpty()) {
                Row(modifier = Modifier.offset(y = (-8).dp, x = if(isMe) (-12).dp else 12.dp).background(MaterialTheme.colorScheme.surface, CircleShape).border(1.dp, LocalChatColors.current.separator, CircleShape).padding(horizontal = 6.dp, vertical = 2.dp)) {
                    message.reactions?.values?.distinct()?.take(3)?.forEach { Text(it, fontSize = 12.sp) }
                    if (message.reactions!!.size > 1) Text(message.reactions!!.size.toString(), fontSize = 10.sp, modifier = Modifier.padding(start = 2.dp))
                }
            }
            if (isMe && isLastInGroup) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp, end = 4.dp)) {
                    val tint = if (message.isRead) MessengerBlue else MetaGray4.copy(0.5f)
                    Icon(if (message.isRead) Icons.Default.DoneAll else Icons.Default.Check, null, tint = tint, modifier = Modifier.size(14.dp))
                }
            }
        }
    }
    if (showContext) SwiftUIMessageMenu(isMe = isMe, onDismiss = { showContext = false }, onReply = { onReply(); showContext = false }, onEdit = { onEdit(); showContext = false }, onPin = { onPin(); showContext = false }, onCopy = { clipboardManager.setText(AnnotatedString(message.text)); showContext = false }, onDelete = { onDelete(message.id); showContext = false }, onReact = { onReact(it); showContext = false })
}

@Composable
fun SwiftUIMessageMenu(isMe: Boolean, onDismiss: () -> Unit, onReply: () -> Unit, onEdit: () -> Unit, onPin: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit, onReact: (String) -> Unit) {
    val emojis = listOf("❤️", "😂", "😮", "😢", "🙏", "👍"); Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) { Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.4f)).clickable { onDismiss() }, contentAlignment = Alignment.Center) { Column(modifier = Modifier.width(250.dp).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) { Surface(shape = RoundedCornerShape(30.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.padding(bottom = 12.dp)) { Row(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) { emojis.forEach { emoji -> Text(emoji, modifier = Modifier.clickable { onReact(emoji) }, fontSize = 26.sp) } } }; Surface(shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.surface, modifier = Modifier.fillMaxWidth()) { Column { SwiftUIMenuItem("Responder", Icons.AutoMirrored.Rounded.Reply, onClick = onReply); SwiftUIDivider(); if (isMe) { SwiftUIMenuItem("Editar", Icons.Rounded.Edit, onClick = onEdit); SwiftUIDivider() }; SwiftUIMenuItem("Fixar", Icons.Rounded.PushPin, onClick = onPin); SwiftUIDivider(); SwiftUIMenuItem("Copiar", Icons.Rounded.ContentCopy, onClick = onCopy); SwiftUIDivider(); SwiftUIMenuItem("Remover", Icons.Rounded.Delete, color = iOSRed, onClick = onDelete) } } } } }
}

@Composable
fun SwiftUIMenuItem(text: String, icon: ImageVector, color: Color = MaterialTheme.colorScheme.onSurface, onClick: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 16.dp, vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) { Text(text, style = MaterialTheme.typography.bodyLarge, color = color); Icon(icon, null, tint = color, modifier = Modifier.size(20.dp)) }
}

@Composable
fun SwiftUIDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 0.dp), thickness = 0.5.dp, color = LocalChatColors.current.separator)
}

@Composable
fun AudioPlayerBubble(url: String, localPath: String?, isMe: Boolean) {
    var isPlaying by remember { mutableStateOf(false) }; var progress by remember { mutableFloatStateOf(0f) }; val mediaPlayer = remember { MediaPlayer() }; val textColor = if (isMe) Color.White else MaterialTheme.colorScheme.onSurface
    LaunchedEffect(isPlaying) { if (isPlaying) { while (isPlaying) { try { if (mediaPlayer.duration > 0) progress = mediaPlayer.currentPosition.toFloat() / mediaPlayer.duration } catch (e: Exception) {}; delay(100); if (!mediaPlayer.isPlaying) isPlaying = false } } }
    DisposableEffect(url) { onDispose { try { mediaPlayer.stop(); mediaPlayer.release() } catch (e: Exception) {} } }
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp).width(200.dp)) { IconButton(onClick = { if (isPlaying) { mediaPlayer.pause(); isPlaying = false } else { try { if (progress > 0f && progress < 0.99f) { mediaPlayer.start(); isPlaying = true } else { mediaPlayer.reset(); mediaPlayer.setDataSource(localPath ?: url); mediaPlayer.prepareAsync(); mediaPlayer.setOnPreparedListener { it.start(); isPlaying = true } }; mediaPlayer.setOnCompletionListener { isPlaying = false; progress = 0f } } catch (e: Exception) { Log.e("AudioPlayer", "Error: ${e.message}") } } }, modifier = Modifier.size(36.dp)) { Icon(imageVector = if (isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled, contentDescription = null, tint = textColor, modifier = Modifier.size(32.dp)) }; Spacer(Modifier.width(8.dp)); Column(modifier = Modifier.weight(1f)) { AudioVisualizer(isPlaying = isPlaying, color = textColor); Spacer(Modifier.height(4.dp)); LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(2.dp).clip(CircleShape), color = textColor, trackColor = textColor.copy(alpha = 0.2f)) }; Spacer(Modifier.width(8.dp)); Icon(Icons.Rounded.Mic, null, tint = textColor.copy(alpha = 0.7f), modifier = Modifier.size(16.dp)) }
}

@Composable
fun AudioVisualizer(isPlaying: Boolean, color: Color) {
    val infiniteTransition = rememberInfiniteTransition(label = "visualizer"); val heights = remember { List(15) { (4..12).random() } }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) { heights.forEachIndexed { index, baseHeight -> val duration = remember { (400..800).random() }; val animatedHeight by if (isPlaying) infiniteTransition.animateFloat(initialValue = 0.5f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(duration), RepeatMode.Reverse), label = "bar_$index") else remember { mutableStateOf(1f) }; Box(modifier = Modifier.width(2.dp).height((baseHeight.dp * (if (isPlaying) animatedHeight * 2f else 1f)).coerceAtMost(22.dp)).background(color.copy(alpha = if (isPlaying) 0.8f else 0.4f), RoundedCornerShape(1.dp))) } }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun MetaInput(text: String, onValueChange: (String) -> Unit, onAddClick: () -> Unit, onSend: () -> Unit, onAudioStart: () -> Unit, onAudioStop: (Boolean) -> Unit, onEmojiClick: () -> Unit, recordingDuration: Long) {
    var isRecording by remember { mutableStateOf(false) }; var isLocked by remember { mutableStateOf(false) }; var dragX by remember { mutableStateOf(0f) }; var dragY by remember { mutableStateOf(0f) }; val isCancelled = dragX < -150f; val isLockAction = dragY < -150f; val infiniteTransition = rememberInfiniteTransition(label = "audio_pulse"); val dotAlpha by infiniteTransition.animateFloat(initialValue = 0.4f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dot_alpha"); val pulseScale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.25f, animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse"); val color by animateColorAsState(if (isCancelled) Color.Gray else if (isRecording) iOSRed else MessengerBlue, label = "button_color"); val view = LocalView.current
    Surface(
        color = LocalChatColors.current.topBar,
        modifier = Modifier.fillMaxWidth().imePadding()
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            Box(modifier = Modifier.width(96.dp)) { if (isLocked) IconButton(onClick = { onAudioStop(true); isRecording = false; isLocked = false; dragX = 0f; dragY = 0f }) { Icon(Icons.Rounded.Delete, null, tint = iOSRed) } else if (!isRecording) Row { IconButton(onClick = onEmojiClick) { Icon(Icons.Rounded.EmojiEmotions, null, tint = MessengerBlue) }; IconButton(onClick = onAddClick) { Icon(Icons.Default.AddCircle, null, tint = MessengerBlue, modifier = Modifier.size(26.dp)) } } }
            Box(modifier = Modifier.weight(1f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(LocalChatColors.current.tertiaryBackground).padding(horizontal = 16.dp, vertical = 8.dp), contentAlignment = Alignment.CenterStart) { if (isRecording) Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) { Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(iOSRed).alpha(dotAlpha)); Spacer(Modifier.width(8.dp)); val minutes = (recordingDuration / 60000); val seconds = (recordingDuration % 60000) / 1000; Text(text = String.format("%02d:%02d", minutes, seconds), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); if (!isLocked) Text(text = if (isCancelled) "Solte para cancelar" else "< Deslize para cancelar", color = MetaGray4, fontSize = 12.sp, modifier = Modifier.offset { IntOffset(dragX.roundToInt().coerceAtMost(0), 0) }) else Text("Gravando...", color = iOSRed, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold) } else { if (text.isEmpty()) Text("Mensagem...", color = MetaGray4, style = MaterialTheme.typography.bodyLarge); BasicTextField(value = text, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MessengerBlue)) } }
            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) { if (text.isNotEmpty() && !isRecording) IconButton(onClick = onSend) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = MessengerBlue) } else if (isLocked) IconButton(onClick = { onAudioStop(false); isRecording = false; isLocked = false; dragX = 0f; dragY = 0f }) { Box(modifier = Modifier.size(40.dp).background(MessengerBlue, CircleShape), contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp)) } } else Box(modifier = Modifier.offset { IntOffset(0, dragY.roundToInt().coerceAtMost(0)) }.scale(if (isRecording) pulseScale else 1f).pointerInput(Unit) { detectDragGesturesAfterLongPress(onDragStart = { isRecording = true; dragX = 0f; dragY = 0f; onAudioStart(); view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) }, onDragEnd = { if (!isLocked) onAudioStop(isCancelled); isRecording = false; dragX = 0f; dragY = 0f }, onDragCancel = { if (!isLocked) { onAudioStop(true); isRecording = false } }, onDrag = { _, dragAmount -> if (!isLocked) { dragX += dragAmount.x; dragY += dragAmount.y; if (isLockAction) { isLocked = true; dragY = 0f; dragX = 0f; view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS) } } }) }) { IconButton(onClick = {}) { Icon(Icons.Rounded.Mic, null, tint = color, modifier = Modifier.size(26.dp)) }; if (isRecording && !isLocked) Icon(Icons.Rounded.Lock, null, tint = MetaGray4, modifier = Modifier.align(Alignment.TopCenter).offset(y = (-40).dp).size(16.dp).alpha(((-dragY)/150f).coerceIn(0f, 1f))) } }
        }
    }
}
