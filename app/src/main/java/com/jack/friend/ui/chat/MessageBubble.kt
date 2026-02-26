package com.jack.friend.ui.chat

import android.content.Intent
import android.media.MediaPlayer
import android.net.Uri
import android.util.Log
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Reply
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.jack.friend.AnimatedEmoji
import com.jack.friend.AnimatedEmojiHelper
import com.jack.friend.LinkPreview
import com.jack.friend.Message
import com.jack.friend.MetaEmojiPickerPro
import com.jack.friend.RecentEmojiStore
import com.jack.friend.ui.theme.LocalChatColors
import com.jack.friend.ui.theme.MessengerBlue
import com.jack.friend.ui.theme.MetaGray4
import com.jack.friend.ui.theme.iOSRed
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MetaMessageBubble(
    message: Message,
    isMe: Boolean,
    targetPhotoUrl: String?,
    isFirstInGroup: Boolean,
    isLastInGroup: Boolean,
    showReadReceipts: Boolean = true, // Adicionado
    onImageClick: (String) -> Unit,
    onVideoClick: (String) -> Unit,
    onDelete: (String) -> Unit,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onEdit: () -> Unit,
    onPin: () -> Unit,
    onAudioPlayed: () -> Unit = {}
) {
    val chatColors = LocalChatColors.current
    val isSingleEmoji = AnimatedEmojiHelper.isSingleEmoji(message.text) && message.imageUrl == null && message.audioUrl == null && message.videoUrl == null && message.replyToId == null
    val animUrl = if (isSingleEmoji) AnimatedEmojiHelper.getAnimUrl(message.text) else null
    
    // Theme-aware colors
    val bubbleColor = if (isMe) chatColors.bubbleMe else chatColors.bubbleOther
    val textColor = if (isMe) {
        // High contrast white for dark bubbles, dark for light ones
        if (isColorDark(chatColors.bubbleMe)) Color.White else Color.Black
    } else {
        chatColors.textPrimary
    }
    
    val view = LocalView.current
    val clipboardManager = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    var showContext by remember { mutableStateOf(false) }

    // Swipe to Reply State
    var dragAmount by remember { mutableFloatStateOf(0f) }
    val offsetX = animateFloatAsState(
        targetValue = dragAmount,
        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow),
        label = "offsetX"
    )
    
    var isReplyTriggered by remember { mutableStateOf(false) }

    val shape = if (isMe) RoundedCornerShape(
        topStart = 22.dp,
        topEnd = if (isFirstInGroup) 22.dp else 6.dp,
        bottomEnd = if (isLastInGroup) 22.dp else 6.dp,
        bottomStart = 22.dp
    ) else RoundedCornerShape(
        topStart = if (isFirstInGroup) 22.dp else 6.dp,
        topEnd = 22.dp,
        bottomEnd = 22.dp,
        bottomStart = if (isLastInGroup) 22.dp else 6.dp
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .draggable(
                state = rememberDraggableState { delta ->
                    val newOffset = (dragAmount + delta).coerceIn(0f, 100f)
                    dragAmount = newOffset
                    
                    if (newOffset >= 70f && !isReplyTriggered) {
                        isReplyTriggered = true
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    } else if (newOffset < 70f) {
                        isReplyTriggered = false
                    }
                },
                orientation = Orientation.Horizontal,
                onDragStopped = {
                    if (isReplyTriggered) onReply()
                    dragAmount = 0f
                    isReplyTriggered = false
                }
            )
    ) {
        // Reply icon behind bubble
        if (offsetX.value > 10f) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 20.dp)
                    .graphicsLayer {
                        val progress = (offsetX.value / 70f).coerceIn(0f, 1f)
                        alpha = progress
                        scaleX = progress
                        scaleY = progress
                    }
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.Reply,
                    contentDescription = null,
                    tint = chatColors.primary,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .offset { IntOffset(offsetX.value.roundToInt(), 0) }
                .fillMaxWidth()
                .padding(vertical = 1.dp, horizontal = 8.dp),
            horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
            verticalAlignment = Alignment.Bottom
        ) {
            if (!isMe) {
                if (isLastInGroup) {
                    AsyncImage(
                        model = targetPhotoUrl,
                        contentDescription = null,
                        modifier = Modifier.size(32.dp).clip(CircleShape).background(chatColors.separator).shadow(2.dp, CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Spacer(Modifier.width(40.dp))
                }
            }

            Column(horizontalAlignment = if (isMe) Alignment.End else Alignment.Start, modifier = Modifier.padding(horizontal = 4.dp)) {
                if (message.isSticker) {
                    AsyncImage(
                        model = message.stickerUrl,
                        contentDescription = "Sticker",
                        modifier = Modifier.size(160.dp).combinedClickable(
                            onClick = { },
                            onLongClick = { 
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                showContext = true 
                            }
                        )
                    )
                } else if (isSingleEmoji) {
                    Box(modifier = Modifier.padding(8.dp)) {
                        if (animUrl != null) {
                            AnimatedEmoji(
                                emoji = message.text,
                                modifier = Modifier.size(110.dp),
                                onLongClick = { 
                                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                    showContext = true 
                                }
                            )
                        } else {
                            Text(
                                text = message.text, 
                                fontSize = 64.sp, 
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
                    Surface(
                        color = bubbleColor,
                        shape = shape,
                        tonalElevation = if (isMe) 2.dp else 0.dp,
                        shadowElevation = 1.dp,
                        modifier = Modifier.widthIn(max = 300.dp).combinedClickable(
                            onClick = {
                                if (message.imageUrl != null) onImageClick(message.imageUrl!!)
                                else if (message.videoUrl != null) onVideoClick(message.videoUrl!!)
                            },
                            onLongClick = { 
                                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                showContext = true 
                            }
                        )
                    ) {
                        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                            if (message.replyToId != null) {
                                Surface(
                                    color = if (isMe) textColor.copy(0.15f) else Color.Black.copy(0.05f), 
                                    shape = RoundedCornerShape(10.dp), 
                                    modifier = Modifier.padding(bottom = 6.dp).fillMaxWidth()
                                ) {
                                    Row(modifier = Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        Box(modifier = Modifier.width(3.dp).height(32.dp).background(if (isMe) textColor.copy(0.6f) else chatColors.primary, RoundedCornerShape(2.dp)))
                                        Column(modifier = Modifier.padding(start = 10.dp)) {
                                            Text(message.replyToName ?: "", color = if (isMe) textColor.copy(0.9f) else chatColors.primary, fontWeight = FontWeight.ExtraBold, fontSize = 11.sp)
                                            val replyText = message.replyToText ?: if (message.imageUrl != null) "📷 Imagem" else if (message.audioUrl != null) "🎤 Áudio" else if (message.videoUrl != null) "📹 Vídeo" else ""
                                            Text(replyText, color = textColor.copy(0.8f), fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        }
                                    }
                                }
                            }

                            if (message.imageUrl != null) {
                                Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                    MessageImageItem(imageUrl = message.imageUrl!!, onImageClick = onImageClick, modifier = Modifier.fillMaxWidth())
                                }
                            }

                            if (message.videoUrl != null) {
                                Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.padding(vertical = 4.dp)) {
                                    MessageVideoItem(videoUrl = message.videoUrl!!, onVideoClick = onVideoClick, modifier = Modifier.fillMaxWidth())
                                }
                            }

                            if (message.audioUrl != null) AudioPlayerBubble(
                                url = message.audioUrl!!, 
                                localPath = message.localAudioPath, 
                                isMe = isMe,
                                isPlayed = message.audioPlayed,
                                durationSeconds = message.audioDurationSeconds,
                                onPlay = { if (!isMe && !message.audioPlayed) onAudioPlayed() }
                            )

                            if (message.linkPreview != null) {
                                LinkPreviewCard(preview = message.linkPreview!!, isMe = isMe)
                            }

                            Column(horizontalAlignment = Alignment.End) {
                                if (message.text.isNotEmpty()) {
                                    Text(
                                        text = message.text,
                                        style = MaterialTheme.typography.bodyLarge.copy(color = textColor, lineHeight = 20.sp),
                                        modifier = Modifier.padding(bottom = 2.dp)
                                    )
                                }
                                
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (message.isEdited) {
                                        Text(
                                            "editada",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = textColor.copy(alpha = 0.5f),
                                            modifier = Modifier.padding(end = 4.dp)
                                        )
                                    }
                                    Text(
                                        text = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp)),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = textColor.copy(alpha = 0.6f),
                                        fontSize = 10.sp
                                    )
                                    if (isMe) {
                                        Spacer(Modifier.width(4.dp))
                                        val isReadToDisplay = message.isRead && showReadReceipts
                                        val statusIconColor = if (isReadToDisplay) {
                                            if (isColorDark(bubbleColor)) Color.Cyan else chatColors.primary
                                        } else {
                                            textColor.copy(0.6f)
                                        }
                                        Icon(
                                            if (isReadToDisplay) Icons.Default.DoneAll else Icons.Default.Check,
                                            null, 
                                            tint = statusIconColor, 
                                            modifier = Modifier.size(13.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (!message.reactions.isNullOrEmpty()) {
                    Row(
                        modifier = Modifier.offset(y = (-10).dp, x = if (isMe) (-8).dp else 8.dp)
                            .background(chatColors.secondaryBackground, CircleShape)
                            .border(1.dp, chatColors.separator.copy(0.5f), CircleShape)
                            .shadow(2.dp, CircleShape)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        message.reactions?.values?.distinct()?.take(3)?.forEach { emoji ->
                            Text(text = emoji, fontSize = 13.sp)
                        }
                        if (message.reactions!!.size > 1) {
                            Text(
                                text = message.reactions!!.size.toString(),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = chatColors.textSecondary,
                                modifier = Modifier.padding(start = 2.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showContext) {
        SwiftUIMessageMenu(
            isMe = isMe,
            onDismiss = { showContext = false },
            onReply = { onReply(); showContext = false },
            onEdit = { onEdit(); showContext = false },
            onPin = { onPin(); showContext = false },
            onCopy = { clipboardManager?.setText(AnnotatedString(message.text)); showContext = false },
            onDelete = { onDelete(message.id); showContext = false },
            onReact = { onReact(it); showContext = false }
        )
    }
}

// Utility to check if a color is dark
private fun isColorDark(color: Color): Boolean {
    val luminance = 0.2126 * color.red + 0.7152 * color.green + 0.0722 * color.blue
    return luminance < 0.5
}

@Composable
fun LinkPreviewCard(preview: LinkPreview, isMe: Boolean) {
    val context = LocalContext.current
    val chatColors = LocalChatColors.current
    val backgroundColor = if (isMe) {
        if (isColorDark(chatColors.bubbleMe)) Color.White.copy(alpha = 0.12f) else Color.Black.copy(alpha = 0.08f)
    } else {
        chatColors.tertiaryBackground.copy(alpha = 0.6f)
    }
    val textColor = if (isMe) {
        if (isColorDark(chatColors.bubbleMe)) Color.White else Color.Black
    } else {
        chatColors.textPrimary
    }

    Card(
        modifier = Modifier
            .padding(vertical = 6.dp)
            .fillMaxWidth()
            .clickable {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, preview.url.toUri())
                    context.startActivity(intent)
                } catch (e: Exception) {
                    Log.e("LinkPreview", "Error opening link: ${e.message}")
                }
            },
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(0.5.dp, textColor.copy(alpha = 0.15f))
    ) {
        Column {
            if (preview.imageUrl != null) {
                AsyncImage(
                    model = preview.imageUrl,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(150.dp)
                        .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)),
                    contentScale = ContentScale.Crop
                )
            }
            Column(modifier = Modifier.padding(12.dp)) {
                if (preview.title != null) {
                    Text(
                        text = preview.title!!,
                        style = MaterialTheme.typography.titleSmall,
                        color = textColor,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 18.sp
                    )
                }
                if (preview.description != null) {
                    Text(
                        text = preview.description!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = textColor.copy(alpha = 0.75f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
                    Icon(Icons.Rounded.Link, null, tint = (if (isMe) textColor else chatColors.primary).copy(0.6f), modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = preview.url.toUri().host ?: preview.url,
                        style = MaterialTheme.typography.labelSmall,
                        color = (if (isMe) textColor else chatColors.primary).copy(alpha = 0.7f),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun SwiftUIMessageMenu(isMe: Boolean, onDismiss: () -> Unit, onReply: () -> Unit, onEdit: () -> Unit, onPin: () -> Unit, onCopy: () -> Unit, onDelete: () -> Unit, onReact: (String) -> Unit) {
    val chatColors = LocalChatColors.current
    val recentEmojis = RecentEmojiStore.get().take(6).ifEmpty { listOf("❤️", "😂", "😮", "😢", "🙏", "👍") }
    var showFullEmojiPicker by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.5f)).combinedClickable(
            onClick = onDismiss,
            indication = null,
            interactionSource = remember { MutableInteractionSource() }
        ), contentAlignment = Alignment.Center) {
            Column(modifier = Modifier.width(300.dp).padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Surface(
                    shape = RoundedCornerShape(32.dp), 
                    color = chatColors.secondaryBackground, 
                    tonalElevation = 8.dp,
                    modifier = Modifier.padding(bottom = 16.dp).shadow(12.dp, RoundedCornerShape(32.dp))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp), 
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        LazyRow(
                            modifier = Modifier.weight(1f),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            itemsIndexed(recentEmojis) { index, emoji ->
                                val emojiScale = remember { Animatable(0f) }
                                val emojiAlpha = remember { Animatable(0f) }

                                LaunchedEffect(Unit) {
                                    delay(index * 40L)
                                    launch { emojiAlpha.animateTo(1f, tween(250)) }
                                    emojiScale.animateTo(1f, spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessLow))
                                }

                                Text(
                                    text = emoji, 
                                    modifier = Modifier
                                        .graphicsLayer(scaleX = emojiScale.value, scaleY = emojiScale.value, alpha = emojiAlpha.value)
                                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { 
                                            scope.launch {
                                                emojiScale.animateTo(1.5f, tween(100))
                                                emojiScale.animateTo(1f, tween(100))
                                                onReact(emoji)
                                            }
                                        }
                                        .padding(horizontal = 6.dp), 
                                    fontSize = 30.sp
                                )
                            }
                        }
                        
                        IconButton(onClick = { showFullEmojiPicker = true }) {
                            Icon(Icons.Default.Add, null, tint = chatColors.primary, modifier = Modifier.size(28.dp))
                        }
                    }
                }
                
                if (!showFullEmojiPicker) {
                    Surface(
                        shape = RoundedCornerShape(24.dp), 
                        color = chatColors.secondaryBackground, 
                        tonalElevation = 4.dp,
                        modifier = Modifier.fillMaxWidth().shadow(8.dp, RoundedCornerShape(24.dp))
                    ) {
                        Column {
                            SwiftUIMenuItem("Responder", Icons.AutoMirrored.Rounded.Reply, onClick = onReply)
                            SwiftUIDivider()
                            if (isMe) { SwiftUIMenuItem("Editar", Icons.Rounded.Edit, onClick = onEdit); SwiftUIDivider() }
                            SwiftUIMenuItem("Fixar", Icons.Rounded.PushPin, onClick = onPin)
                            SwiftUIDivider()
                            SwiftUIMenuItem("Copiar", Icons.Rounded.ContentCopy, onClick = onCopy)
                            SwiftUIDivider()
                            SwiftUIMenuItem("Remover", Icons.Rounded.Delete, color = iOSRed, onClick = onDelete)
                        }
                    }
                } else {
                    Card(shape = RoundedCornerShape(28.dp), modifier = Modifier.shadow(16.dp, RoundedCornerShape(28.dp))) {
                        MetaEmojiPickerPro(onEmojiSelected = { onReact(it) }, heightDp = 380)
                    }
                }
            }
        }
    }
}

@Composable
fun SwiftUIMenuItem(text: String, icon: ImageVector, color: Color? = null, onClick: () -> Unit) {
    val chatColors = LocalChatColors.current
    val itemColor = color ?: chatColors.textPrimary
    Row(modifier = Modifier.fillMaxWidth().clickable { onClick() }.padding(horizontal = 20.dp, vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Text(text, style = MaterialTheme.typography.bodyLarge, color = itemColor, fontWeight = FontWeight.SemiBold)
        Icon(icon, null, tint = itemColor.copy(0.8f), modifier = Modifier.size(22.dp))
    }
}

@Composable
fun SwiftUIDivider() {
    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = LocalChatColors.current.separator.copy(0.4f))
}

@Composable
fun AudioPlayerBubble(
    url: String, 
    localPath: String?, 
    isMe: Boolean,
    isPlayed: Boolean,
    durationSeconds: Long?,
    onPlay: () -> Unit
) {
    var isPlaying by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }
    val mediaPlayer = remember { MediaPlayer() }
    val chatColors = LocalChatColors.current
    val textColor = if (isMe) {
        if (isColorDark(chatColors.bubbleMe)) Color.White else Color.Black
    } else {
        chatColors.textPrimary
    }
    
    val playedColor = chatColors.primary // Usa a cor primária do tema atual para o áudio ouvido

    val formattedDuration = remember(durationSeconds) {
        if (durationSeconds == null || durationSeconds <= 0) ""
        else {
            val mins = durationSeconds / 60
            val secs = durationSeconds % 60
            String.format(Locale.getDefault(), "%d:%02d", mins, secs)
        }
    }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            while (isPlaying) {
                try {
                    if (mediaPlayer.duration > 0) progress = mediaPlayer.currentPosition.toFloat() / mediaPlayer.duration
                } catch (_: Exception) { }
                delay(100)
                if (!mediaPlayer.isPlaying) isPlaying = false
            }
        }
    }

    DisposableEffect(url) {
        onDispose {
            try { mediaPlayer.stop(); mediaPlayer.release() } catch (_: Exception) { }
        }
    }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 4.dp).width(220.dp)) {
        IconButton(
            onClick = {
                if (isPlaying) {
                    mediaPlayer.pause()
                    isPlaying = false
                } else {
                    onPlay()
                    try {
                        if (progress > 0f && progress < 0.99f) {
                            mediaPlayer.start()
                            isPlaying = true
                        } else {
                            mediaPlayer.reset()
                            mediaPlayer.setDataSource(localPath ?: url)
                            mediaPlayer.prepareAsync()
                            mediaPlayer.setOnPreparedListener { it.start(); isPlaying = true }
                        }
                        mediaPlayer.setOnCompletionListener { isPlaying = false; progress = 0f }
                    } catch (e: Exception) {
                        Log.e("AudioPlayer", "Error: ${e.message}")
                    }
                }
            },
            modifier = Modifier.size(42.dp)
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Rounded.PauseCircleFilled else Icons.Rounded.PlayCircleFilled, 
                contentDescription = null, 
                tint = if (isPlayed && !isMe) playedColor else textColor, 
                modifier = Modifier.size(38.dp)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            AudioVisualizer(isPlaying = isPlaying, color = if (isPlayed && !isMe) playedColor else textColor)
            Spacer(Modifier.height(6.dp))
            LinearProgressIndicator(
                progress = { progress }, 
                modifier = Modifier.fillMaxWidth().height(3.dp).clip(CircleShape), 
                color = if (isMe) textColor else if (isPlayed) playedColor else chatColors.primary,
                trackColor = textColor.copy(alpha = 0.2f)
            )
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                Icons.Rounded.Mic, 
                null, 
                tint = if (isPlayed && !isMe) playedColor else textColor.copy(alpha = 0.6f), 
                modifier = Modifier.size(18.dp)
            )
            if (formattedDuration.isNotEmpty()) {
                Text(
                    text = formattedDuration,
                    fontSize = 9.sp,
                    color = (if (isPlayed && !isMe) playedColor else textColor).copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AudioVisualizer(isPlaying: Boolean, color: Color) {
    val heights = remember { List(18) { (4..14).random() } }
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
        heights.forEachIndexed { index, baseHeight ->
            val duration = remember { (300..600).random() }
            val animatedHeight = if (isPlaying) {
                val transition = rememberInfiniteTransition(label = "visualizer")
                transition.animateFloat(
                    initialValue = 0.4f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(tween(duration), RepeatMode.Reverse),
                    label = "bar_$index"
                ).value
            } else 1f

            Box(
                modifier = Modifier.width(2.dp)
                    .height((baseHeight.dp * (if (isPlaying) animatedHeight * 2.2f else 1f)).coerceAtMost(24.dp))
                    .background(color.copy(alpha = if (isPlaying) 0.9f else 0.4f), RoundedCornerShape(1.5.dp))
            )
        }
    }
}
