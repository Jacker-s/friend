package com.jack.friend

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.jack.friend.ui.profile.IOS17ContactProfileSheet
import com.jack.friend.ui.theme.*
import java.util.UUID

class ContactsActivity : ComponentActivity() {
    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            FriendTheme {
                ContactsScreenIOS17(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onOpenChat = { contact ->
                        val intent = Intent(this, MainActivity::class.java).apply {
                            putExtra("targetId", contact.id)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                        finish()
                    },
                    onStartCall = { contact, isVideo ->
                        viewModel.setTargetId(contact.id)
                        val uniqueRoomId = "Call_${UUID.randomUUID().toString().take(8)}"
                        viewModel.startCall(isVideo = isVideo, customRoomId = uniqueRoomId)

                        startActivity(
                            Intent(this, CallActivity::class.java).apply {
                                putExtra("roomId", uniqueRoomId)
                                putExtra("targetId", contact.id)
                                putExtra("targetPhotoUrl", contact.photoUrl)
                                putExtra("isOutgoing", true)
                                putExtra("isVideo", isVideo)
                            }
                        )
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContactsScreenIOS17(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenChat: (UserProfile) -> Unit,
    onStartCall: (UserProfile, Boolean) -> Unit
) {
    val context = LocalContext.current
    val contacts by viewModel.contacts.collectAsStateWithLifecycle(emptyList())
    val myUsername by viewModel.myUsername.collectAsStateWithLifecycle("")
    val activeChats by viewModel.activeChats.collectAsStateWithLifecycle(emptyList())
    val blockedUsers by viewModel.blockedUsers.collectAsStateWithLifecycle(emptyList())

    var showAddContactDialog by remember { mutableStateOf(false) }
    var showDeleteDialog by remember { mutableStateOf<UserProfile?>(null) }
    var selectedProfile by remember { mutableStateOf<UserProfile?>(null) }
    var longPressContact by remember { mutableStateOf<UserProfile?>(null) }
    var showLongPressMenu by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf("") }

    val filteredContacts by remember(contacts, query) {
        derivedStateOf {
            val q = query.trim()
            if (q.isEmpty()) contacts
            else contacts.filter {
                it.name.contains(q, ignoreCase = true) ||
                        it.id.contains(q, ignoreCase = true) ||
                        (it.status).contains(q, ignoreCase = true)
            }
        }
    }

    val groupedContacts by remember(filteredContacts) {
        derivedStateOf {
            filteredContacts
                .sortedBy { it.displayName.lowercase() }
                .groupBy { p -> p.displayName.firstOrNull()?.uppercaseChar()?.toString() ?: "#" }
                .toSortedMap()
        }
    }

    val flatList by remember(groupedContacts) {
        derivedStateOf {
            buildList<ContactsRowItem> {
                groupedContacts.forEach { (letter, list) ->
                    add(ContactsRowItem.Header(letter))
                    list.forEach { add(ContactsRowItem.Contact(it)) }
                }
            }
        }
    }

    val listState = rememberLazyListState()

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Contatos", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        if (contacts.isNotEmpty()) {
                            Text(
                                text = if (query.isBlank()) "${contacts.size} contatos" else "${filteredContacts.size} encontrados",
                                style = MaterialTheme.typography.labelSmall,
                                color = MetaGray4
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, "Voltar", tint = MessengerBlue, modifier = Modifier.size(22.dp))
                    }
                },
                actions = {
                    IconButton(onClick = { showAddContactDialog = true }) {
                        Icon(Icons.Rounded.PersonAdd, "Adicionar", tint = MessengerBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            IOS17SearchPill(
                value = query,
                onValueChange = { query = it },
                placeholder = "Pesquisar contatos",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                if (contacts.isEmpty()) {
                    item {
                        EmptyContactsState(onAddClick = { showAddContactDialog = true })
                    }
                } else if (filteredContacts.isEmpty()) {
                    item {
                        EmptySearchState(query = query)
                    }
                } else {
                    items(
                        items = flatList,
                        key = { item ->
                            when (item) {
                                is ContactsRowItem.Header -> "h_${item.letter}"
                                is ContactsRowItem.Contact -> "c_${item.profile.id}"
                            }
                        }
                    ) { item ->
                        when (item) {
                            is ContactsRowItem.Header -> LetterHeader(item.letter)
                            is ContactsRowItem.Contact -> {
                                ContactRow(
                                    contact = item.profile,
                                    isBlocked = blockedUsers.contains(item.profile.id),
                                    onClick = { selectedProfile = item.profile },
                                    onLongClick = {
                                        longPressContact = item.profile
                                        showLongPressMenu = true
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }

        if (showLongPressMenu && longPressContact != null) {
            val contact = longPressContact!!
            ContactActionSheet(
                contact = contact,
                isBlocked = blockedUsers.contains(contact.id),
                onDismiss = { showLongPressMenu = false },
                onOpenChat = {
                    showLongPressMenu = false
                    onOpenChat(contact)
                },
                onCall = { isVideo ->
                    showLongPressMenu = false
                    onStartCall(contact, isVideo)
                },
                onBlock = {
                    showLongPressMenu = false
                    if (blockedUsers.contains(contact.id)) viewModel.unblockUser(contact.id)
                    else viewModel.blockUser(contact.id)
                },
                onDelete = {
                    showLongPressMenu = false
                    showDeleteDialog = contact
                }
            )
        }

        if (selectedProfile != null) {
            val user = selectedProfile!!
            val chat = activeChats.firstOrNull { !it.isGroup && it.friendId == user.id }
            IOS17ContactProfileSheet(
                user = user,
                myUsername = myUsername,
                isMuted = chat?.isMuted ?: false,
                isBlocked = blockedUsers.contains(user.id),
                onDismiss = { selectedProfile = null },
                onMessage = {
                    selectedProfile = null
                    onOpenChat(it)
                },
                onAudioCall = {
                    selectedProfile = null
                    onStartCall(it, false)
                },
                onVideoCall = {
                    selectedProfile = null
                    onStartCall(it, true)
                },
                onToggleMute = { viewModel.toggleMuteChat(user.id, chat?.isMuted ?: false) },
                onToggleBlock = {
                    if (blockedUsers.contains(user.id)) viewModel.unblockUser(user.id)
                    else viewModel.blockUser(user.id)
                },
                onRemove = {
                    selectedProfile = null
                    showDeleteDialog = it
                }
            )
        }

        if (showDeleteDialog != null) {
            AlertDialog(
                onDismissRequest = { showDeleteDialog = null },
                title = { Text("Remover Contato") },
                text = { Text("Deseja remover ${showDeleteDialog?.name} da sua lista?") },
                confirmButton = {
                    TextButton(onClick = {
                        showDeleteDialog?.let { viewModel.deleteContact(it.id) { _, _ -> } }
                        showDeleteDialog = null
                    }) { Text("Remover", color = iOSRed) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) { Text("Cancelar") }
                }
            )
        }

        if (showAddContactDialog) {
            AddContactDialog(
                icon = Icons.Rounded.PersonAdd,
                onDismiss = { showAddContactDialog = false },
                onAdd = { username ->
                    viewModel.addContact(username) { success, err ->
                        if (success) showAddContactDialog = false
                        else Toast.makeText(context, err ?: "Erro", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

private sealed class ContactsRowItem {
    data class Header(val letter: String) : ContactsRowItem()
    data class Contact(val profile: UserProfile) : ContactsRowItem()
}

@Composable
private fun LetterHeader(letter: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(LocalChatColors.current.background.copy(alpha = 0.95f))
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(
            text = letter,
            style = MaterialTheme.typography.labelMedium,
            color = MessengerBlue,
            fontWeight = FontWeight.Bold
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ContactRow(
    contact: UserProfile,
    isBlocked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
            .padding(horizontal = 16.dp)
    ) {
        Row(
            modifier = Modifier.padding(vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AsyncImage(
                    model = contact.photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(LocalChatColors.current.separator),
                    contentScale = ContentScale.Crop
                )
                if (contact.isOnline && contact.isVisibleOnline) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .background(Color.White, CircleShape)
                            .padding(2.dp)
                            .background(iOSGreen, CircleShape)
                    )
                }
            }

            Spacer(Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = contact.displayName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = contact.status.takeIf { it.isNotBlank() } ?: "@${contact.id.lowercase()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isBlocked) iOSRed else MetaGray4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            
            Icon(
                Icons.Rounded.ChevronRight,
                null,
                tint = MetaGray4.copy(alpha = 0.5f),
                modifier = Modifier.size(20.dp)
            )
        }
        HorizontalDivider(
            modifier = Modifier.padding(start = 62.dp),
            thickness = 0.5.dp,
            color = LocalChatColors.current.separator
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ContactActionSheet(
    contact: UserProfile,
    isBlocked: Boolean,
    onDismiss: () -> Unit,
    onOpenChat: () -> Unit,
    onCall: (Boolean) -> Unit,
    onBlock: () -> Unit,
    onDelete: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        dragHandle = { BottomSheetDefaults.DragHandle() }
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AsyncImage(
                    model = contact.photoUrl,
                    contentDescription = null,
                    modifier = Modifier.size(50.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(contact.displayName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("@${contact.id.lowercase()}", color = MetaGray4, fontSize = 14.sp)
                }
            }
            
            HorizontalDivider(color = LocalChatColors.current.separator, thickness = 0.5.dp)
            
            ActionItem("Enviar Mensagem", Icons.Rounded.ChatBubble, MessengerBlue, onOpenChat)
            ActionItem("Chamada de Áudio", Icons.Rounded.Call, MessengerBlue) { onCall(false) }
            ActionItem("Chamada de Vídeo", Icons.Rounded.Videocam, MessengerBlue) { onCall(true) }
            ActionItem(if (isBlocked) "Desbloquear" else "Bloquear", Icons.Rounded.Block, iOSRed, onBlock)
            ActionItem("Excluir Contato", Icons.Rounded.Delete, iOSRed, onDelete)
        }
    }
}

@Composable
private fun ActionItem(label: String, icon: ImageVector, color: Color, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(label, color = color) },
        leadingContent = { Icon(icon, null, tint = color) },
        modifier = Modifier.clickable(onClick = onClick)
    )
}

@Composable
private fun EmptyContactsState(onAddClick: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.People, null, modifier = Modifier.size(80.dp), tint = MetaGray4.copy(alpha = 0.3f))
        Spacer(Modifier.height(16.dp))
        Text("Sua lista está vazia", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Adicione amigos para começar a conversar.", color = MetaGray4, textAlign = TextAlign.Center)
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onAddClick,
            shape = RoundedCornerShape(12.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MessengerBlue)
        ) {
            Text("Adicionar Agora", fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun EmptySearchState(query: String) {
    Column(
        modifier = Modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(Icons.Rounded.SearchOff, null, modifier = Modifier.size(80.dp), tint = MetaGray4.copy(alpha = 0.3f))
        Spacer(Modifier.height(16.dp))
        Text("Nenhum resultado", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Text("Não encontramos ninguém para \"$query\"", color = MetaGray4)
    }
}

@Composable
fun IOS17SearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = LocalChatColors.current.secondaryBackground.copy(alpha = 0.5f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Rounded.Search, null, tint = MetaGray4, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Box(Modifier.weight(1f)) {
                if (value.isBlank()) Text(placeholder, color = MetaGray4, fontSize = 16.sp)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotBlank()) {
                IconButton(onClick = { onValueChange("") }, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Rounded.Cancel, null, tint = MetaGray4)
                }
            }
        }
    }
}
