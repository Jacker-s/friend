package com.jack.friend.ui.profile

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Message
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.database.FirebaseDatabase
import com.jack.friend.UserProfile
import com.jack.friend.ui.chat.MediaViewerItem
import com.jack.friend.ui.chat.MediaViewerScreen
import com.jack.friend.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IOS17ContactProfileSheet(
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
    onRemove: (UserProfile) -> Unit,
    isContact: Boolean = true,
    onAddContact: (UserProfile) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val colors = LocalChatColors.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var fullScreenPhotoUrl by remember { mutableStateOf<String?>(null) }

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
        sheetState = sheetState,
        containerColor = colors.primaryBackground,
        dragHandle = null,
        shape = RoundedCornerShape(0.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                // Top Cover & Profile Image
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp)
                ) {
                    AsyncImage(
                        model = user.photoUrl,
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            .blur(50.dp)
                            .clickable { fullScreenPhotoUrl = user.photoUrl },
                        contentScale = ContentScale.Crop,
                        alpha = 0.4f
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color.Transparent, colors.primaryBackground)
                                )
                            )
                    )
                    
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Box(
                            contentAlignment = Alignment.BottomEnd,
                            modifier = Modifier.padding(bottom = 20.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                border = BorderStroke(4.dp, Color.White),
                                shadowElevation = 16.dp,
                                modifier = Modifier
                                    .size(150.dp)
                                    .clickable { fullScreenPhotoUrl = user.photoUrl }
                            ) {
                                AsyncImage(
                                    model = user.photoUrl,
                                    contentDescription = null,
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            }
                            if (user.isOnline && user.isVisibleOnline) {
                                Surface(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .offset(x = (-8).dp, y = (-8).dp),
                                    shape = CircleShape,
                                    color = Color.White,
                                    shadowElevation = 4.dp
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .padding(4.dp)
                                            .background(presenceColor, CircleShape)
                                            .border(2.dp, Color.White, CircleShape)
                                    )
                                }
                            }
                        }
                    }
                }

                // Name & ID
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = user.displayName,
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = colors.textPrimary
                        )
                        if (isVerified) {
                            Icon(
                                Icons.Rounded.Verified,
                                null,
                                tint = MessengerBlue,
                                modifier = Modifier.padding(start = 8.dp).size(24.dp)
                            )
                        }
                    }
                    Text(
                        text = "@${user.id.lowercase()} • $presenceText",
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.textSecondary,
                        fontWeight = FontWeight.Medium
                    )

                    Spacer(Modifier.height(32.dp))

                    // Quick Actions
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ModernActionButton(
                            label = "Mensagem",
                            icon = Icons.AutoMirrored.Rounded.Message,
                            containerColor = MessengerBlue,
                            onClick = { onMessage(user) }
                        )
                        ModernActionButton(
                            label = "Áudio",
                            icon = Icons.Rounded.Phone,
                            containerColor = iOSGreen,
                            onClick = { onAudioCall(user) }
                        )
                        ModernActionButton(
                            label = "Vídeo",
                            icon = Icons.Rounded.Videocam,
                            containerColor = iOSPurple,
                            onClick = { onVideoCall(user) }
                        )
                        if (!isContact) {
                            ModernActionButton(
                                label = "Adicionar",
                                icon = Icons.Rounded.PersonAdd,
                                containerColor = MessengerBlue,
                                onClick = { onAddContact(user) }
                            )
                        }
                    }
                }

                Spacer(Modifier.height(32.dp))

                // Info Sections
                ProfileCard(colors) {
                    InfoItem(
                        icon = Icons.Rounded.Info,
                        label = "Recado",
                        value = user.status.ifBlank { "Olá! Estou usando o Wappi Messenger." },
                        colors = colors
                    )
                    HorizontalDivider(color = colors.separator.copy(0.3f), thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))
                    InfoItem(
                        icon = Icons.Rounded.Group,
                        label = "Grupos em Comum",
                        value = "$mutualGroups grupos",
                        colors = colors
                    )
                }

                Spacer(Modifier.height(16.dp))

                ProfileCard(colors) {
                    ActionItem(
                        icon = if (isMuted) Icons.Rounded.NotificationsActive else Icons.Rounded.NotificationsOff,
                        label = if (isMuted) "Ativar Notificações" else "Silenciar Notificações",
                        colors = colors,
                        onClick = onToggleMute
                    )
                    HorizontalDivider(color = colors.separator.copy(0.3f), thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))
                    ActionItem(
                        icon = Icons.Rounded.ContentCopy,
                        label = "Copiar @ID de Usuário",
                        colors = colors,
                        onClick = {
                            clipboard.setText(AnnotatedString("@${user.id.lowercase()}"))
                            Toast.makeText(context, "ID copiado!", Toast.LENGTH_SHORT).show()
                        }
                    )
                }

                Spacer(Modifier.height(16.dp))

                ProfileCard(colors) {
                    ActionItem(
                        icon = if (isBlocked) Icons.Rounded.LockOpen else Icons.Rounded.Block,
                        label = if (isBlocked) "Desbloquear" else "Bloquear Contato",
                        contentColor = iOSRed,
                        colors = colors,
                        onClick = onToggleBlock
                    )
                    if (isContact) {
                        HorizontalDivider(color = colors.separator.copy(0.3f), thickness = 0.5.dp, modifier = Modifier.padding(start = 48.dp))
                        ActionItem(
                            icon = Icons.Rounded.PersonRemove,
                            label = "Remover Contato",
                            contentColor = iOSRed,
                            colors = colors,
                            onClick = { onRemove(user) }
                        )
                    }
                }

                Spacer(Modifier.height(60.dp))
            }
            
            // Close Button
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .statusBarsPadding()
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
                    .background(Color.Black.copy(0.2f), CircleShape)
            ) {
                Icon(Icons.Rounded.Close, null, tint = Color.White)
            }

            // Full Screen Photo Viewer integration
            if (fullScreenPhotoUrl != null) {
                MediaViewerScreen(
                    mediaItem = MediaViewerItem.Image(fullScreenPhotoUrl!!),
                    onDismiss = { fullScreenPhotoUrl = null }
                )
            }
        }
    }
}

@Composable
private fun ModernActionButton(
    label: String,
    icon: ImageVector,
    containerColor: Color,
    onClick: () -> Unit
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            modifier = Modifier.size(56.dp),
            shape = RoundedCornerShape(16.dp),
            color = containerColor.copy(alpha = 0.12f),
            onClick = onClick
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = containerColor, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = label,
            fontSize = 13.sp,
            color = containerColor,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ProfileCard(colors: ChatColors, content: @Composable ColumnScope.() -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(24.dp),
        color = colors.secondaryBackground,
        tonalElevation = 1.dp
    ) {
        Column(modifier = Modifier.padding(vertical = 4.dp)) {
            content()
        }
    }
}

@Composable
private fun InfoItem(icon: ImageVector, label: String, value: String, colors: ChatColors) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = MessengerBlue, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(16.dp))
        Column {
            Text(label, fontSize = 12.sp, color = colors.textSecondary, fontWeight = FontWeight.Bold)
            Text(value, fontSize = 17.sp, color = colors.textPrimary, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun ActionItem(
    icon: ImageVector,
    label: String,
    colors: ChatColors,
    contentColor: Color? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(18.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            icon,
            null,
            tint = contentColor ?: MessengerBlue,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.width(16.dp))
        Text(
            text = label,
            fontSize = 17.sp,
            color = contentColor ?: colors.textPrimary,
            fontWeight = FontWeight.SemiBold
        )
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
        sameYear && dayNow == dayThen -> "hoje às $timeFmt"
        sameYear && dayNow == dayThen + 1 -> "ontem às $timeFmt"
        else -> {
            val dateFmt = SimpleDateFormat("d MMM", Locale("pt", "BR")).format(Date(lastActive))
            "$dateFmt às $timeFmt"
        }
    }
}
