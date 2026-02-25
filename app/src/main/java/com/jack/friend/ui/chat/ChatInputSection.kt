package com.jack.friend.ui.chat

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jack.friend.Message
import com.jack.friend.ui.theme.LocalChatColors
import com.jack.friend.ui.theme.MessengerBlue
import com.jack.friend.ui.theme.MetaGray4
import com.jack.friend.ui.theme.iOSRed
import kotlin.math.roundToInt

@Composable
fun ChatInputSection(
    textState: String,
    onTextChange: (String) -> Unit,
    replyingTo: Message?,
    editingMessage: Message?,
    pinnedMessage: Message?,
    recordingDuration: Long,
    onSend: () -> Unit,
    onAddClick: () -> Unit,
    onCameraClick: () -> Unit,
    onAudioStart: () -> Unit,
    onAudioStop: (Boolean) -> Unit,
    onEmojiClick: () -> Unit,
    onStickerClick: () -> Unit,
    onCancelReply: () -> Unit,
    onUnpin: () -> Unit,
    onPinnedClick: (Message) -> Unit = {}
) {
    Column {
        if (pinnedMessage != null) PinnedHeader(pinnedMessage, onUnpin, onPinnedClick)
        if (replyingTo != null || editingMessage != null) ReplyHeader(replyingTo, editingMessage, onCancelReply)
        MetaInput(
            text = textState,
            onValueChange = onTextChange,
            onAddClick = onAddClick,
            onCameraClick = onCameraClick,
            onSend = onSend,
            onAudioStart = onAudioStart,
            onAudioStop = onAudioStop,
            onEmojiClick = onEmojiClick,
            onStickerClick = onStickerClick,
            recordingDuration = recordingDuration
        )
    }
}

@Composable
fun PinnedHeader(message: Message, onUnpin: () -> Unit, onClick: (Message) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .clickable { onClick(message) },
        color = LocalChatColors.current.secondaryBackground.copy(alpha = 0.9f),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(2.dp)
                    .height(30.dp)
                    .background(MessengerBlue)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "Mensagem Fixada",
                    color = MessengerBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    message.text.ifEmpty { if (message.imageUrl != null) "📷 Imagem" else if (message.audioUrl != null) "🎤 Áudio" else if (message.videoUrl != null) "📹 Vídeo" else "Mídia" },
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 13.sp,
                    color = LocalChatColors.current.textPrimary
                )
            }
            IconButton(
                onClick = onUnpin,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = null,
                    tint = LocalChatColors.current.textSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

@Composable
fun ReplyHeader(replyingTo: Message?, editingMessage: Message?, onCancel: () -> Unit) {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        color = LocalChatColors.current.secondaryBackground.copy(alpha = 0.9f),
        shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp)
    ) {
        Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.width(4.dp).height(40.dp).background(MessengerBlue, RoundedCornerShape(2.dp))){
                Icon(Icons.AutoMirrored.Filled.Reply, null, tint = Color.White, modifier = Modifier.size(20.dp).align(Alignment.Center))
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(if (editingMessage != null) "Editar mensagem" else (replyingTo?.senderName ?: ""), color = MessengerBlue, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                val text = editingMessage?.text ?: replyingTo?.text ?: if (replyingTo?.imageUrl != null) "📷 Imagem" else if (replyingTo?.audioUrl != null) "🎤 Áudio" else if (replyingTo?.videoUrl != null) "📹 Vídeo" else if (replyingTo?.isSticker == true) "Sticker" else ""
                Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis, fontSize = 14.sp)
            }
            IconButton(onClick = onCancel) { Icon(Icons.Default.Close, null, modifier = Modifier.size(18.dp)) }
        }
    }
}

@Composable
fun MetaInput(
    text: String,
    onValueChange: (String) -> Unit,
    onAddClick: () -> Unit,
    onCameraClick: () -> Unit,
    onSend: () -> Unit,
    onAudioStart: () -> Unit,
    onAudioStop: (Boolean) -> Unit,
    onEmojiClick: () -> Unit,
    onStickerClick: () -> Unit,
    recordingDuration: Long
) {
    var isRecording by remember { mutableStateOf(false) }
    var isLocked by remember { mutableStateOf(false) }
    var dragX by remember { mutableStateOf(0f) }
    var dragY by remember { mutableStateOf(0f) }

    val isCancelled = dragX < -150f
    val isLockAction = dragY < -150f

    val infiniteTransition = rememberInfiniteTransition(label = "audio_pulse")
    val dotAlpha by infiniteTransition.animateFloat(initialValue = 0.4f, targetValue = 1f, animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse), label = "dot_alpha")
    val pulseScale by infiniteTransition.animateFloat(initialValue = 1f, targetValue = 1.25f, animationSpec = infiniteRepeatable(tween(800), RepeatMode.Reverse), label = "pulse")

    val color by animateColorAsState(if (isCancelled) Color.Gray else if (isRecording) iOSRed else MessengerBlue, label = "button_color")
    val view = LocalView.current

    Surface(color = LocalChatColors.current.topBar.copy(alpha = 0.9f), modifier = Modifier.fillMaxWidth().imePadding()) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isRecording) {
                IconButton(onClick = onAddClick) {
                    Icon(Icons.Default.Add, null, tint = MessengerBlue, modifier = Modifier.size(32.dp))
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(LocalChatColors.current.tertiaryBackground.copy(alpha = 0.8f))
                    .padding(horizontal = 4.dp, vertical = 4.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isRecording) {
                        IconButton(onClick = onEmojiClick, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.EmojiEmotions, null, tint = MetaGray4)
                        }
                        // Sticker function temporarily disabled
                        /*
                        IconButton(onClick = onStickerClick, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.StickyNote2, null, tint = MetaGray4)
                        }
                        */
                    }

                    Box(modifier = Modifier.weight(1f).padding(horizontal = 8.dp)) {
                        if (isRecording) {
                            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                                Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(iOSRed).alpha(dotAlpha))
                                Spacer(Modifier.width(8.dp))
                                val minutes = (recordingDuration / 60000)
                                val seconds = (recordingDuration % 60000) / 1000
                                Text(text = String.format(java.util.Locale.getDefault(), "%02d:%02d", minutes, seconds), color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                                Spacer(Modifier.weight(1f))
                                if (!isLocked) {
                                    Text(
                                        text = if (isCancelled) "Solte para cancelar" else "< Deslize para cancelar",
                                        color = MetaGray4,
                                        fontSize = 12.sp,
                                        modifier = Modifier.offset { IntOffset(dragX.roundToInt().coerceAtMost(0), 0) }
                                    )
                                } else {
                                    Text("Gravando...", color = iOSRed, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold)
                                }
                            }
                        } else {
                            if (text.isEmpty()) Text("Mensagem", color = MetaGray4, style = MaterialTheme.typography.bodyLarge)
                            BasicTextField(value = text, onValueChange = onValueChange, modifier = Modifier.fillMaxWidth(), textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface), cursorBrush = SolidColor(MessengerBlue))
                        }
                    }

                    if (!isRecording && text.isEmpty()) {
                        IconButton(onClick = onCameraClick, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.PhotoCamera, null, tint = MetaGray4)
                        }
                    }

                    if (!isRecording && text.isNotEmpty()) {
                        IconButton(onClick = onAddClick, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Rounded.AttachFile, null, tint = MetaGray4, modifier = Modifier.rotate(-45f))
                        }
                    }
                }
            }

            Box(modifier = Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                if (text.isNotEmpty() && !isRecording) {
                    IconButton(onClick = onSend) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = MessengerBlue) }
                } else if (isLocked) {
                    IconButton(onClick = { onAudioStop(false); isRecording = false; isLocked = false; dragX = 0f; dragY = 0f }) {
                        Box(modifier = Modifier.size(40.dp).background(MessengerBlue, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier.offset { IntOffset(0, dragY.roundToInt().coerceAtMost(0)) }
                            .scale(if (isRecording) pulseScale else 1f)
                            .pointerInput(Unit) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        isRecording = true; dragX = 0f; dragY = 0f
                                        onAudioStart()
                                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    },
                                    onDragEnd = {
                                        if (!isLocked) onAudioStop(isCancelled)
                                        isRecording = false; dragX = 0f; dragY = 0f
                                    },
                                    onDragCancel = {
                                        if (!isLocked) { onAudioStop(true); isRecording = false }
                                    },
                                    onDrag = { _, dragAmount ->
                                        if (!isLocked) {
                                            dragX += dragAmount.x
                                            dragY += dragAmount.y
                                            if (isLockAction) {
                                                isLocked = true; dragY = 0f; dragX = 0f
                                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            }
                                        }
                                    }
                                )
                            }
                    ) {
                        IconButton(onClick = {}) { Icon(Icons.Rounded.Mic, null, tint = color, modifier = Modifier.size(26.dp)) }
                        if (isRecording && !isLocked) {
                            Icon(
                                Icons.Rounded.Lock,
                                null,
                                tint = MetaGray4,
                                modifier = Modifier.align(Alignment.TopCenter).offset(y = (-40).dp).size(16.dp)
                                    .alpha(((-dragY) / 150f).coerceIn(0f, 1f))
                            )
                        }
                    }
                }
            }
        }
    }
}
