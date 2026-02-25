package com.jack.friend

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.automirrored.filled.CallReceived
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.firebase.database.*
import com.jack.friend.ui.theme.FriendTheme
import com.jack.friend.ui.theme.LocalChatColors
import com.jack.friend.ui.theme.MetaGray4
import com.jack.friend.ui.theme.MessengerBlue
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class CallsActivity : androidx.fragment.app.FragmentActivity() {

    private val viewModel: ChatViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            FriendTheme {
                CallsScreen(
                    viewModel = viewModel,
                    onBack = { finish() },
                    onOpenCall = { roomId, targetId, targetPhotoUrl, isOutgoing, isVideo ->
                        startActivity(
                            Intent(this, CallActivity::class.java).apply {
                                putExtra("roomId", roomId)
                                putExtra("targetId", targetId)
                                putExtra("targetPhotoUrl", targetPhotoUrl)
                                putExtra("isOutgoing", isOutgoing)
                                putExtra("isVideo", isVideo)
                            }
                        )
                    }
                )
            }
        }
    }
}

/** Item pronto para UI */
data class CallItemUi(
    val roomId: String,
    val otherId: String,
    val otherName: String,
    val otherPhotoUrl: String?,
    val isOutgoing: Boolean,
    val isVideo: Boolean,
    val status: String,
    val timeMs: Long,
    val durationSec: Long? = null
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CallsScreen(
    viewModel: ChatViewModel,
    onBack: () -> Unit,
    onOpenCall: (roomId: String, targetId: String, targetPhotoUrl: String?, isOutgoing: Boolean, isVideo: Boolean) -> Unit
) {
    val myUsername by viewModel.myUsername.collectAsStateWithLifecycle("")
    val contacts by viewModel.contacts.collectAsStateWithLifecycle(emptyList())
    val activeChats by viewModel.activeChats.collectAsStateWithLifecycle(emptyList())

    var calls by remember { mutableStateOf<List<CallItemUi>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableIntStateOf(0) } // 0: Todas, 1: Perdidas
    var showMenu by remember { mutableStateOf(false) }
    var showDeleteAllDialog by remember { mutableStateOf(false) }
    var logToDelete by remember { mutableStateOf<CallItemUi?>(null) }

    DisposableEffect(myUsername, contacts, activeChats) {
        if (myUsername.isBlank()) {
            calls = emptyList()
            return@DisposableEffect onDispose { }
        }

        val me = myUsername.uppercase().trim()
        val db = FirebaseDatabase.getInstance().reference
        val ref = db.child("calls").limitToLast(300)

        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val list = mutableListOf<CallItemUi>()
                snapshot.children.forEach { callSnap ->
                    val roomId = callSnap.key ?: return@forEach
                    val caller = callSnap.child("callerId").getValue(String::class.java).orEmpty().uppercase().trim()
                    val receiver = callSnap.child("receiverId").getValue(String::class.java).orEmpty().uppercase().trim()
                    val status = callSnap.child("status").getValue(String::class.java).orEmpty().ifBlank { "RINGING" }
                    val isVideo = callSnap.child("isVideo").getValue(Boolean::class.java) ?: false
                    val timeMs = (callSnap.child("timestamp").value as? Long) ?: 0L
                    val durationSec = callSnap.child("durationSec").getValue(Long::class.java)

                    if (caller == me || receiver == me) {
                        val isOutgoing = caller == me
                        val otherId = if (isOutgoing) receiver else caller
                        val otherProfile = contacts.firstOrNull { it.id.equals(otherId, true) }
                        val otherChat = activeChats.firstOrNull { it.friendId.equals(otherId, true) }
                        val otherName = otherProfile?.name ?: otherChat?.friendName ?: otherId
                        val otherPhotoUrl = otherProfile?.photoUrl ?: otherChat?.friendPhotoUrl

                        list.add(CallItemUi(roomId, otherId, otherName, otherPhotoUrl, isOutgoing, isVideo, status, timeMs, durationSec))
                    }
                }
                calls = list.sortedByDescending { it.timeMs }
            }
            override fun onCancelled(error: DatabaseError) {
                Log.e("CallsActivity", "Firebase error: ${error.message}")
            }
        }

        ref.addValueEventListener(listener)
        onDispose { ref.removeEventListener(listener) }
    }

    val filteredLogs = remember(calls, searchQuery, selectedTab) {
        calls.filter { log ->
            val matchesSearch = log.otherName.contains(searchQuery, ignoreCase = true) ||
                    log.otherId.contains(searchQuery, ignoreCase = true)
            val matchesTab = if (selectedTab == 1) log.status == "MISSED" || log.status == "REJECTED" else true
            matchesSearch && matchesTab
        }
    }

    if (showDeleteAllDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteAllDialog = false },
            title = { Text("Limpar Histórico") },
            text = { Text("Deseja apagar todo o seu histórico de chamadas? Esta ação não pode ser desfeita.") },
            confirmButton = {
                TextButton(onClick = {
                    val db = FirebaseDatabase.getInstance().reference
                    calls.forEach { db.child("calls").child(it.roomId).removeValue() }
                    showDeleteAllDialog = false
                }) {
                    Text("Limpar Tudo", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAllDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    logToDelete?.let { log ->
        AlertDialog(
            onDismissRequest = { logToDelete = null },
            title = { Text("Excluir Chamada") },
            text = { Text("Deseja remover este registro de chamada?") },
            confirmButton = {
                TextButton(onClick = {
                    FirebaseDatabase.getInstance().reference.child("calls").child(log.roomId).removeValue()
                    logToDelete = null
                }) {
                    Text("Excluir", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { logToDelete = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(LocalChatColors.current.topBar)) {
                CenterAlignedTopAppBar(
                    title = {
                        Text("Chamadas", fontWeight = FontWeight.Black)
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, "Voltar", tint = MessengerBlue)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Default.MoreVert, "Mais", tint = MessengerBlue)
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Limpar histórico") },
                                leadingIcon = { Icon(Icons.Default.DeleteSweep, null) },
                                onClick = {
                                    showMenu = false
                                    showDeleteAllDialog = true
                                }
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = Color.Transparent
                    )
                )

                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    placeholder = { Text("Buscar contatos ou números") },
                    leadingIcon = { Icon(Icons.Default.Search, null, tint = MetaGray4) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Close, null, tint = MetaGray4)
                            }
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MessengerBlue,
                        unfocusedBorderColor = Color.Transparent,
                        focusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    ),
                    singleLine = true
                )

                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color.Transparent,
                    contentColor = MessengerBlue,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = MessengerBlue
                        )
                    },
                    divider = {}
                ) {
                    Tab(
                        selected = selectedTab == 0,
                        onClick = { selectedTab = 0 },
                        text = { Text("Todas", fontWeight = if (selectedTab == 0) FontWeight.Bold else FontWeight.Normal) }
                    )
                    Tab(
                        selected = selectedTab == 1,
                        onClick = { selectedTab = 1 },
                        text = { Text("Perdidas", fontWeight = if (selectedTab == 1) FontWeight.Bold else FontWeight.Normal) }
                    )
                }
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { padding ->

        if (filteredLogs.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        if (selectedTab == 1) Icons.AutoMirrored.Filled.CallMissed else Icons.Default.Call,
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = MetaGray4.copy(alpha = 0.5f)
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(
                        if (searchQuery.isNotEmpty()) "Nenhum resultado encontrado"
                        else if (selectedTab == 1) "Nenhuma chamada perdida"
                        else "Seu histórico está vazio",
                        color = MetaGray4,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentPadding = PaddingValues(bottom = 20.dp)
            ) {
                items(filteredLogs, key = { it.roomId }) { log ->
                    CallRowItem(
                        log = log,
                        onDelete = { logToDelete = log },
                        onClick = {
                            onOpenCall(log.roomId, log.otherId, log.otherPhotoUrl, log.isOutgoing, log.isVideo)
                        }
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 72.dp),
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.05f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CallRowItem(
    log: CallItemUi,
    onDelete: () -> Unit,
    onClick: () -> Unit
) {
    val isMissed = (log.status == "MISSED" || log.status == "REJECTED") && !log.isOutgoing
    val isVideo = log.isVideo

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onDelete
            )
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(
            model = log.otherPhotoUrl,
            contentDescription = null,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.1f))
        )

        Spacer(Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = log.otherName,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontWeight = FontWeight.SemiBold,
                    color = if (isMissed) Color.Red else MaterialTheme.colorScheme.onSurface
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(Modifier.height(2.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when {
                        log.isOutgoing -> Icons.Default.CallMade
                        isMissed -> Icons.AutoMirrored.Filled.CallMissed
                        else -> Icons.AutoMirrored.Filled.CallReceived
                    },
                    contentDescription = null,
                    tint = if (isMissed) Color.Red.copy(alpha = 0.7f) else MetaGray4,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = buildString {
                        if (log.isOutgoing) append("Efetuada") else append("Recebida")
                        log.durationSec?.let {
                            if (it > 0) {
                                append(" • ${formatDuration(it)}")
                            }
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MetaGray4
                )
            }
        }

        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = formatCallTime(log.timeMs),
                style = MaterialTheme.typography.labelSmall,
                color = MetaGray4
            )
            Spacer(Modifier.height(4.dp))
            Icon(
                imageVector = if (isVideo) Icons.Default.Videocam else Icons.Default.Call,
                contentDescription = null,
                tint = MessengerBlue.copy(alpha = 0.8f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

private fun formatCallTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val now = System.currentTimeMillis()
    val diff = now - timestamp
    val dayMs = 86400000L

    return when {
        diff < dayMs -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        diff < 2 * dayMs -> "Ontem"
        diff < 7 * dayMs -> SimpleDateFormat("EEEE", Locale.getDefault()).format(Date(timestamp))
        else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
    }
}

private fun formatDuration(seconds: Long): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return if (mins > 0) "${mins}m ${secs}s" else "${secs}s"
}
