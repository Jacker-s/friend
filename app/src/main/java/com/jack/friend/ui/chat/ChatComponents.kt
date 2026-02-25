package com.jack.friend.ui.chat

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jack.friend.ChatSummary
import com.jack.friend.UserProfile
import com.jack.friend.UserStatus
import com.jack.friend.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MetaStatusRow(
    statuses: List<UserStatus>,
    myPhotoUrl: String?,
    myUsername: String,
    contacts: List<UserProfile>,
    onAdd: () -> Unit,
    onViewUserStatuses: (List<UserStatus>) -> Unit
) {
    val contactIds = contacts.map { it.id }.toSet()
    
    // Filtra para mostrar apenas status próprios ou de contatos
    val filteredStatuses = statuses.filter { it.userId == myUsername || contactIds.contains(it.userId) }
    
    val grouped = filteredStatuses.groupBy { it.userId }
    val myStatuses = grouped[myUsername] ?: emptyList()
    val otherStatuses = grouped.filter { it.key != myUsername }
    
    val hasUnread = { list: List<UserStatus> -> list.any { !it.viewers.containsKey(myUsername) } }

    LazyRow(
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Meu Status
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.width(72.dp).clickable { if (myStatuses.isNotEmpty()) onViewUserStatuses(myStatuses) else onAdd() }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    val borderColor = if (myStatuses.isNotEmpty()) MessengerBlue else Color.Transparent
                    
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .then(if (myStatuses.isNotEmpty()) Modifier.border(2.dp, borderColor, CircleShape) else Modifier)
                            .padding(4.dp)
                    ) {
                        AsyncImage(
                            model = myPhotoUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize().clip(CircleShape).background(LocalChatColors.current.separator),
                            contentScale = ContentScale.Crop
                        )
                    }
                    
                    if (myStatuses.isEmpty()) {
                        Surface(
                            modifier = Modifier.align(Alignment.BottomEnd).size(22.dp).offset(x = (-2).dp, y = (-2).dp),
                            shape = CircleShape,
                            color = MessengerBlue,
                            border = BorderStroke(2.dp, Color.White)
                        ) {
                            Icon(Icons.Rounded.Add, null, tint = Color.White, modifier = Modifier.padding(2.dp))
                        }
                    }
                }
                Text("Meu status", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp), maxLines = 1)
            }
        }

        // Status dos Contatos
        otherStatuses.forEach { (_, userList) ->
            val first = userList.first()
            val unread = hasUnread(userList)
            
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.width(72.dp).clickable { onViewUserStatuses(userList) }
                ) {
                    Box(
                        modifier = Modifier
                            .size(68.dp)
                            .drawBehind {
                                val segmentColor = if (unread) MessengerBlue else Color.LightGray.copy(alpha = 0.5f)
                                val strokeWidth = 2.5.dp.toPx()
                                val gap = 4f
                                val count = userList.size
                                
                                if (count == 1) {
                                    drawCircle(color = segmentColor, style = Stroke(strokeWidth))
                                } else {
                                    val sweep = (360f / count) - gap
                                    for (i in 0 until count) {
                                        val start = (i * (360f / count)) - 90f + (gap / 2)
                                        val color = if (userList[i].viewers.containsKey(myUsername)) Color.LightGray.copy(alpha = 0.5f) else MessengerBlue
                                        drawArc(
                                            color = color,
                                            startAngle = start,
                                            sweepAngle = sweep,
                                            useCenter = false,
                                            style = Stroke(strokeWidth)
                                        )
                                    }
                                }
                            }
                            .padding(5.dp)
                    ) {
                        AsyncImage(
                            model = first.userPhotoUrl,
                            contentDescription = null, 
                            modifier = Modifier.fillMaxSize().clip(CircleShape).background(LocalChatColors.current.separator),
                            contentScale = ContentScale.Crop
                        )
                    }
                    Text(first.username, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp), maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetaChatItem(
    summary: ChatSummary, 
    myId: String,
    onClick: () -> Unit, 
    onLongClick: () -> Unit
) {
    val chatColors = LocalChatColors.current
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            AsyncImage(
                model = summary.friendPhotoUrl, 
                contentDescription = null, 
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(chatColors.separator), 
                contentScale = ContentScale.Crop
            )
            
            if (summary.isOnline && summary.presenceStatus != "Invisível") {
                val presenceColor = when (summary.presenceStatus) {
                    "Online" -> iOSGreen
                    "Ocupado" -> iOSRed
                    "Ausente" -> iOSOrange
                    else -> Color.Gray
                }
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(18.dp)
                        .background(chatColors.secondaryBackground, CircleShape)
                        .padding(3.dp)
                        .background(presenceColor, CircleShape)
                )
            }
        }
        
        Column(modifier = Modifier.weight(1f).padding(start = 12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(), 
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                    Text(
                        text = summary.friendName ?: summary.friendId, 
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontWeight = if (summary.hasUnread) FontWeight.Bold else FontWeight.SemiBold
                        ), 
                        maxLines = 1, 
                        overflow = TextOverflow.Ellipsis
                    )
                    
                    if (summary.isMuted) {
                        Icon(
                            imageVector = Icons.Rounded.NotificationsOff,
                            contentDescription = null,
                            tint = MetaGray4,
                            modifier = Modifier.padding(start = 4.dp).size(14.dp)
                        )
                    }
                    
                    if (summary.isPinned) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = null,
                            tint = MessengerBlue,
                            modifier = Modifier.padding(start = 4.dp).size(14.dp)
                        )
                    }
                }
                
                Text(
                    text = formatChatTime(summary.timestamp), 
                    style = MaterialTheme.typography.labelSmall, 
                    color = if (summary.hasUnread) MessengerBlue else MetaGray4
                )
            }
            
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 2.dp)
            ) {
                val lastMessageText = if (summary.isTyping) {
                    "Digitando..."
                } else {
                    val prefix = if (summary.lastSenderId == myId) "Você: " else ""
                    "$prefix${summary.lastMessage}"
                }
                
                val contentColor = if (summary.isTyping) {
                    MessengerBlue
                } else if (summary.hasUnread) {
                    chatColors.textPrimary
                } else {
                    MetaGray4
                }
                
                if (summary.isEphemeral && !summary.isTyping) {
                    Icon(
                        imageVector = Icons.Rounded.Timer,
                        contentDescription = null,
                        tint = contentColor.copy(alpha = 0.7f),
                        modifier = Modifier.padding(end = 4.dp).size(14.dp)
                    )
                }

                Text(
                    text = lastMessageText, 
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontWeight = if (summary.hasUnread) FontWeight.Medium else FontWeight.Normal
                    ), 
                    color = contentColor, 
                    maxLines = 1, 
                    overflow = TextOverflow.Ellipsis, 
                    modifier = Modifier.weight(1f)
                )
                
                if (summary.hasUnread) {
                    Box(
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(12.dp)
                            .background(MessengerBlue, CircleShape)
                    )
                }
            }
        }
    }
}

private fun formatChatTime(timestamp: Long): String {
    if (timestamp == 0L) return ""
    val now = Calendar.getInstance()
    val chatTime = Calendar.getInstance().apply { timeInMillis = timestamp }
    
    val isSameDay = now.get(Calendar.YEAR) == chatTime.get(Calendar.YEAR) &&
                    now.get(Calendar.DAY_OF_YEAR) == chatTime.get(Calendar.DAY_OF_YEAR)
                    
    return when {
        isSameDay -> {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
        }
        else -> {
            val diffDays = (now.timeInMillis - chatTime.timeInMillis) / (24 * 60 * 60 * 1000)
            when {
                diffDays < 1 -> SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(timestamp))
                diffDays < 2 -> "Ontem"
                diffDays < 7 -> SimpleDateFormat("EEE", Locale.getDefault()).format(Date(timestamp))
                else -> SimpleDateFormat("dd/MM/yy", Locale.getDefault()).format(Date(timestamp))
            }
        }
    }
}

@Composable
fun MetaUserItem(
    user: UserProfile, 
    isContact: Boolean, 
    onItemClick: () -> Unit,
    onChatClick: () -> Unit, 
    onAddContactClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onItemClick() }
            .padding(16.dp), 
        verticalAlignment = Alignment.CenterVertically
    ) {
        AsyncImage(model = user.photoUrl, contentDescription = null, modifier = Modifier.size(50.dp).clip(CircleShape).background(LocalChatColors.current.separator), contentScale = ContentScale.Crop)
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(user.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            Text("@${user.id.lowercase()}", style = MaterialTheme.typography.labelSmall, color = MetaGray4)
        }
        Row {
            if (!isContact) IconButton(onClick = onAddContactClick) { Icon(Icons.Rounded.PersonAdd, null, tint = MessengerBlue) }
            IconButton(onClick = onChatClick) { Icon(Icons.Rounded.ChatBubble, null, tint = MessengerBlue) }
        }
    }
}
