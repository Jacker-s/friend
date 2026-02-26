package com.jack.friend.ui.chat

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jack.friend.Message
import com.jack.friend.UserProfile
import com.jack.friend.ui.theme.MetaGray4
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MessageListContent(
    messages: List<Message>,
    listState: LazyListState,
    myUsername: String,
    targetProfile: UserProfile?,
    showReadReceipts: Boolean = true,
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onDelete: (Message) -> Unit,
    onReply: (Message) -> Unit,
    onReact: (Message, String) -> Unit,
    onEdit: (Message) -> Unit,
    onPin: (Message) -> Unit,
    onAudioPlayed: (Message) -> Unit
) {
    LazyColumn(
        state = listState,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 20.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        itemsIndexed(messages, key = { _, m -> m.id }) { index, message ->
            val isMe = message.senderId == myUsername
            val prevMsg = if (index > 0) messages[index - 1] else null
            val nextMsg = if (index < messages.size - 1) messages[index + 1] else null
            
            val isFirstInGroup = prevMsg == null || prevMsg.senderId != message.senderId || (message.timestamp - prevMsg.timestamp > 60000)
            val isLastInGroup = nextMsg == null || nextMsg.senderId != message.senderId || (nextMsg.timestamp - message.timestamp > 60000)
            
            if (isFirstInGroup) {
                val dateText = formatDateHeader(message.timestamp)
                if (dateText != (prevMsg?.let { formatDateHeader(it.timestamp) } ?: "")) {
                    DateHeader(dateText)
                }
            }
            
            MetaMessageBubble(
                message = message,
                isMe = isMe,
                targetPhotoUrl = targetProfile?.photoUrl,
                isFirstInGroup = isFirstInGroup,
                isLastInGroup = isLastInGroup,
                showReadReceipts = showReadReceipts,
                onImageClick = onImageClick,
                onVideoClick = onVideoClick,
                onDelete = { onDelete(message) },
                onReply = { onReply(message) },
                onReact = { onReact(message, it) },
                onEdit = { onEdit(message) },
                onPin = { onPin(message) },
                onAudioPlayed = { onAudioPlayed(message) }
            )
        }
    }
}

@Composable
fun DateHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = MetaGray4,
        fontWeight = FontWeight.Bold,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 16.dp),
        textAlign = TextAlign.Center
    )
}

fun formatDateHeader(timestamp: Long): String {
    val calendar = Calendar.getInstance()
    val now = Calendar.getInstance()
    calendar.timeInMillis = timestamp
    return when {
        calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) && calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) -> "HOJE"
        calendar.get(Calendar.YEAR) == now.get(Calendar.YEAR) && calendar.get(Calendar.DAY_OF_YEAR) == now.get(Calendar.DAY_OF_YEAR) - 1 -> "ONTEM"
        else -> SimpleDateFormat("d 'DE' MMMM", Locale("pt", "BR")).format(Date(timestamp)).uppercase()
    }
}
