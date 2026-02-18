package com.jack.friend

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.firebase.database.FirebaseDatabase
import com.jack.friend.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*
import java.util.UUID
import kotlin.math.max

class ContactsActivity : FragmentActivity() {
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
                            putExtra("TARGET_ID", contact.id)
                            flags = Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        }
                        startActivity(intent)
                        finish()
                    },
                    onStartCall = { contact, isVideo ->
                        // ✅ Lógica Corrigida: Definir o alvo no ViewModel antes de iniciar a chamada
                        viewModel.setTargetId(contact.id, false)
                        
                        val uniqueRoomId = "Call_${UUID.randomUUID().toString().take(8)}"
                        viewModel.startCall(isVideo = isVideo, isGroup = false, customRoomId = uniqueRoomId)

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

    var query by remember { mutableStateOf("") }

    val filteredContacts by remember(contacts, query) {
        derivedStateOf {
            val q = query.trim()
            if (q.isEmpty()) contacts
            else contacts.filter {
                it.name.contains(q, ignoreCase = true) ||
                        it.id.contains(q, ignoreCase = true) ||
                        it.status.contains(q, ignoreCase = true)
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Contatos", fontWeight = FontWeight.Black)
                        Text(
                            text = when {
                                contacts.isEmpty() -> "Nenhum contato"
                                query.isNotBlank() -> "${filteredContacts.size} resultados"
                                else -> "${contacts.size} contatos"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MetaGray4
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = MessengerBlue)
                    }
                },
                actions = {
                    IconButton(onClick = { showAddContactDialog = true }) {
                        Icon(Icons.Default.PersonAdd, "Adicionar contato", tint = MessengerBlue)
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = LocalChatColors.current.topBar
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
                placeholder = "Buscar por nome, @usuário ou recado…",
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
            )

            if (contacts.isNotEmpty() && query.isBlank()) {
                IOS17SummaryCard(
                    title = "Sua lista",
                    subtitle = "${contacts.size} contatos salvos",
                    icon = Icons.Default.People,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                )
                Spacer(Modifier.height(10.dp))
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    contacts.isEmpty() -> {
                        item { IOS17EmptyContactsState(onAddClick = { showAddContactDialog = true }) }
                    }
                    filteredContacts.isEmpty() -> {
                        item { IOS17EmptySearchState(query = query, onClear = { query = "" }) }
                    }
                    else -> {
                        items(filteredContacts, key = { it.id }) { contact ->
                            IOS17ContactCard(
                                contact = contact,
                                onClick = { selectedProfile = contact },
                                onDelete = { showDeleteDialog = contact }
                            )
                        }
                    }
                }
            }
        }

        if (selectedProfile != null) {
            val user = selectedProfile!!
            val chat = activeChats.firstOrNull { !it.isGroup && it.friendId == user.id }
            val isMuted = chat?.isMuted ?: false
            val isBlocked = blockedUsers.contains(user.id)

            IOS17ContactProfileSheet(
                user = user,
                myUsername = myUsername,
                isMuted = isMuted,
                isBlocked = isBlocked,
                onDismiss = { selectedProfile = null },
                onMessage = {
                    selectedProfile = null
                    onOpenChat(it)
                },
                onAudioCall = {
                    if (isBlocked) {
                        Toast.makeText(context, "Desbloqueie para ligar", Toast.LENGTH_SHORT).show()
                    } else {
                        selectedProfile = null
                        onStartCall(it, false)
                    }
                },
                onVideoCall = {
                    if (isBlocked) {
                        Toast.makeText(context, "Desbloqueie para ligar", Toast.LENGTH_SHORT).show()
                    } else {
                        selectedProfile = null
                        onStartCall(it, true)
                    }
                },
                onToggleMute = {
                    viewModel.toggleMuteChat(user.id, isMuted)
                    Toast.makeText(context, if (isMuted) "Notificações ativadas" else "Chat silenciado", Toast.LENGTH_SHORT).show()
                },
                onToggleBlock = {
                    if (isBlocked) {
                        viewModel.unblockUser(user.id)
                        Toast.makeText(context, "Usuário desbloqueado", Toast.LENGTH_SHORT).show()
                    } else {
                        viewModel.blockUser(user.id)
                        Toast.makeText(context, "Usuário bloqueado", Toast.LENGTH_SHORT).show()
                    }
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
                title = { Text("Remover contato") },
                text = { Text("Tem certeza que deseja remover ${showDeleteDialog?.name} da sua lista?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            showDeleteDialog?.let { viewModel.deleteContact(it.id) { success, _ -> 
                                if (success) Toast.makeText(context, "Removido", Toast.LENGTH_SHORT).show()
                            } }
                            showDeleteDialog = null
                        }
                    ) { Text("Remover", color = iOSRed, fontWeight = FontWeight.Bold) }
                },
                dismissButton = {
                    TextButton(onClick = { showDeleteDialog = null }) { Text("Cancelar") }
                },
                shape = RoundedCornerShape(20.dp)
            )
        }

        if (showAddContactDialog) {
            AddContactDialog(
                icon = Icons.Default.Person,
                onDismiss = { showAddContactDialog = false },
                onAdd = { username ->
                    viewModel.addContact(username) { success, err ->
                        if (success) {
                            showAddContactDialog = false
                            Toast.makeText(context, "Contato adicionado", Toast.LENGTH_SHORT).show()
                        } else Toast.makeText(context, err ?: "Erro", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
    }
}

/* ---------------- UI Components ---------------- */

@Composable
private fun IOS17SearchPill(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = LocalChatColors.current.secondaryBackground,
        shadowElevation = 6.dp,
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.Search, contentDescription = null, tint = MetaGray4)
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (value.isBlank()) {
                    Text(placeholder, color = MetaGray4, style = MaterialTheme.typography.bodyMedium)
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (value.isNotBlank()) {
                IconButton(onClick = { onValueChange("") }) {
                    Icon(Icons.Default.Close, contentDescription = "Limpar", tint = MetaGray4)
                }
            }
        }
    }
}

@Composable
private fun IOS17SummaryCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(22.dp),
        color = LocalChatColors.current.secondaryBackground,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(MessengerBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = MessengerBlue)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MetaGray4)
            }
        }
    }
}

@Composable
private fun IOS17EmptySearchState(query: String, onClear: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = LocalChatColors.current.secondaryBackground,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MessengerBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Search, contentDescription = null, tint = MessengerBlue)
            }
            Spacer(Modifier.height(12.dp))
            Text("Nada encontrado", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Não encontramos resultados para “$query”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MetaGray4
            )
            Spacer(Modifier.height(14.dp))
            OutlinedButton(
                onClick = onClear,
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Close, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Limpar busca", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun IOS17EmptyContactsState(onAddClick: () -> Unit) {
    Surface(
        shape = RoundedCornerShape(24.dp),
        color = LocalChatColors.current.secondaryBackground,
        shadowElevation = 8.dp,
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(18.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(MessengerBlue.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.Person, contentDescription = null, tint = MessengerBlue)
            }
            Spacer(Modifier.height(12.dp))
            Text("Nenhum contato adicionado", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(6.dp))
            Text(
                "Adicione alguém pelo @usuário para iniciar uma conversa.",
                style = MaterialTheme.typography.bodyMedium,
                color = MetaGray4
            )
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onAddClick,
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MessengerBlue)
            ) {
                Icon(Icons.Default.PersonAdd, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text("Adicionar contato", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun IOS17ContactCard(
    contact: UserProfile,
    onClick: () -> Unit,
    onDelete: () -> Unit
) {
    val presenceColor = when (contact.presenceStatus) {
        "Online" -> iOSGreen
        "Ocupado" -> iOSRed
        "Ausente" -> iOSOrange
        else -> MetaGray4
    }

    Surface(
        shape = RoundedCornerShape(22.dp),
        color = LocalChatColors.current.secondaryBackground,
        shadowElevation = 6.dp,
        tonalElevation = 1.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClick
            )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                AsyncImage(
                    model = contact.photoUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(LocalChatColors.current.separator),
                    contentScale = ContentScale.Crop
                )
                if (contact.isOnline && contact.isVisibleOnline) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(14.dp)
                            .background(MaterialTheme.colorScheme.background, CircleShape)
                            .padding(2.dp)
                            .background(presenceColor, CircleShape)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = contact.displayName,
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                val subtitle = contact.status.takeIf { it.isNotBlank() } ?: "@${contact.id.lowercase()}"
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MetaGray4,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, contentDescription = "Remover", tint = iOSRed.copy(alpha = 0.78f))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IOS17ContactProfileSheet(
    user: UserProfile,
    myUsername: String,
    isMuted: Boolean,
    isBlocked: Boolean,
    onDismiss: () -> Unit,
    onMessage: (UserProfile) -> Unit,
    onAudioCall: (UserProfile) -> Unit,
    onVideoCall: (UserProfile) -> Unit,
    onToggleMute: () -> Unit,
    onToggleBlock: () -> Unit,
    onRemove: (UserProfile) -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    var isVerified by remember { mutableStateOf(false) }
    var mutualGroups by remember { mutableIntStateOf(0) }

    LaunchedEffect(myUsername, user.id) {
        FirebaseDatabase.getInstance().reference.child("verifiedUsers").child(user.id).get()
            .addOnSuccessListener { isVerified = it.getValue(Boolean::class.java) == true }

        if (myUsername.isNotBlank() && user.id.isNotBlank()) {
            FirebaseDatabase.getInstance().reference.child("groups").get().addOnSuccessListener { snap ->
                var count = 0
                snap.children.forEach { g ->
                    if (g.child("members").hasChild(myUsername) && g.child("members").hasChild(user.id)) count++
                }
                mutualGroups = count
            }
        }
    }

    val presenceColor = when (user.presenceStatus) {
        "Online" -> iOSGreen
        "Ocupado" -> iOSRed
        "Ausente" -> iOSOrange
        else -> MetaGray4
    }

    val presenceText = remember(user.isOnline, user.showLastSeen, user.lastActive, user.presenceStatus, user.isVisibleOnline) {
        when {
            user.isOnline && user.isVisibleOnline -> user.presenceStatus
            user.showLastSeen && user.lastActive > 0L -> "Visto ${formatLastSeen(user.lastActive)}"
            else -> "Offline"
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LocalChatColors.current.secondaryBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Box(modifier = Modifier.fillMaxWidth().height(210.dp)) {
                AsyncImage(model = user.photoUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Black.copy(0.08f), Color.Black.copy(0.78f)))))
                Row(modifier = Modifier.align(Alignment.BottomStart).padding(16.dp), verticalAlignment = Alignment.Bottom) {
                    Box {
                        AsyncImage(model = user.photoUrl, contentDescription = null, modifier = Modifier.size(76.dp).clip(CircleShape).background(LocalChatColors.current.separator), contentScale = ContentScale.Crop)
                        if (user.isOnline && user.isVisibleOnline) {
                            Box(modifier = Modifier.align(Alignment.BottomEnd).size(16.dp).background(Color.White, CircleShape).padding(2.dp).background(presenceColor, CircleShape))
                        }
                    }
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(text = user.displayName, style = MaterialTheme.typography.titleLarge, color = Color.White, fontWeight = FontWeight.Black, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            if (isVerified) {
                                Spacer(Modifier.width(8.dp))
                                Icon(Icons.Default.CheckCircle, null, tint = Color.White, modifier = Modifier.size(18.dp))
                            }
                        }
                        Text(text = "@${user.id.lowercase()}", style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(0.85f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(4.dp))
                        Text(text = presenceText, style = MaterialTheme.typography.labelMedium, color = Color.White.copy(0.92f), fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                IOS17PillAction("Mensagem", Icons.Default.Chat, MessengerBlue, Color.White, Modifier.weight(1f)) { onMessage(user) }
                IOS17PillAction("Ligar", Icons.Default.Phone, LocalChatColors.current.tertiaryBackground, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f)) { onAudioCall(user) }
                IOS17PillAction("Vídeo", Icons.Default.Videocam, LocalChatColors.current.tertiaryBackground, MaterialTheme.colorScheme.onSurface, Modifier.weight(1f)) { onVideoCall(user) }
            }

            Spacer(Modifier.height(14.dp))

            Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = LocalChatColors.current.tertiaryBackground, shape = RoundedCornerShape(20.dp)) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text("Informações", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(10.dp))
                    IOS17InfoRow("Usuário", "@${user.id.lowercase()}")
                    Spacer(Modifier.height(8.dp))
                    IOS17InfoRow("Grupos", "${max(0, mutualGroups)} em comum")
                    Spacer(Modifier.height(8.dp))
                    IOS17InfoRow("Recado", user.status.ifBlank { "Sem recado" })
                }
            }

            Spacer(Modifier.height(12.dp))

            Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp), color = LocalChatColors.current.tertiaryBackground, shape = RoundedCornerShape(20.dp)) {
                Column {
                    ListItem(
                        headlineContent = { Text(if (isMuted) "Ativar notificações" else "Silenciar") },
                        leadingContent = { Icon(if (isMuted) Icons.Default.NotificationsActive else Icons.Default.NotificationsOff, null) },
                        modifier = Modifier.clickable { onToggleMute() }
                    )
                    HorizontalDivider(color = LocalChatColors.current.separator, thickness = 0.5.dp)
                    ListItem(
                        headlineContent = { Text(if (isBlocked) "Desbloquear" else "Bloquear", color = iOSRed, fontWeight = FontWeight.SemiBold) },
                        leadingContent = { Icon(if (isBlocked) Icons.Default.LockOpen else Icons.Default.Block, null, tint = iOSRed) },
                        modifier = Modifier.clickable { onToggleBlock() }
                    )
                    HorizontalDivider(color = LocalChatColors.current.separator, thickness = 0.5.dp)
                    ListItem(
                        headlineContent = { Text("Copiar @usuário") },
                        leadingContent = { Icon(Icons.Default.ContentCopy, null) },
                        modifier = Modifier.clickable {
                            clipboard.setText(AnnotatedString("@${user.id.lowercase()}"))
                            Toast.makeText(context, "Copiado!", Toast.LENGTH_SHORT).show()
                        }
                    )
                    HorizontalDivider(color = LocalChatColors.current.separator, thickness = 0.5.dp)
                    ListItem(
                        headlineContent = { Text("Remover contato", color = iOSRed, fontWeight = FontWeight.SemiBold) },
                        leadingContent = { Icon(Icons.Default.Delete, null, tint = iOSRed) },
                        modifier = Modifier.clickable { onRemove(user) }
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun IOS17PillAction(
    label: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    container: Color, content: Color, modifier: Modifier = Modifier, onClick: () -> Unit
) {
    Surface(
        modifier = modifier.height(48.dp).clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick),
        color = container, shadowElevation = 2.dp, tonalElevation = 1.dp
    ) {
        Row(modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Icon(icon, null, tint = content)
            Spacer(Modifier.width(8.dp))
            Text(label, color = content, fontWeight = FontWeight.Bold, maxLines = 1)
        }
    }
}

@Composable
private fun IOS17InfoRow(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(text = label, style = MaterialTheme.typography.labelMedium, color = MetaGray4, modifier = Modifier.width(72.dp))
        Text(text = value, style = MaterialTheme.typography.bodyMedium, maxLines = 3, overflow = TextOverflow.Ellipsis)
    }
}

private fun formatLastSeen(lastActive: Long): String {
    val now = Calendar.getInstance()
    val c = Calendar.getInstance().apply { timeInMillis = lastActive }
    val timeFmt = SimpleDateFormat("HH:mm", Locale("pt", "BR")).format(Date(lastActive))
    val sameYear = now.get(Calendar.YEAR) == c.get(Calendar.YEAR)
    val dayNow = now.get(Calendar.DAY_OF_YEAR)
    val dayThen = c.get(Calendar.DAY_OF_YEAR)

    return when {
        sameYear && dayNow == dayThen -> "hoje $timeFmt"
        sameYear && dayNow == dayThen + 1 -> "ontem $timeFmt"
        else -> {
            val dateFmt = SimpleDateFormat("d MMM", Locale("pt", "BR")).format(Date(lastActive))
            "$dateFmt • $timeFmt"
        }
    }
}
