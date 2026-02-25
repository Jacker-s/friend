package com.jack.friend

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBackIos
import androidx.compose.material.icons.automirrored.rounded.Message
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.database.FirebaseDatabase
import com.jack.friend.ui.theme.*
import java.text.SimpleDateFormat
import java.util.*

class UserProfileActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val userId = intent.getStringExtra(EXTRA_USER_ID).orEmpty()

        setContent {
            FriendTheme {
                UserProfileScreen(
                    userId = userId,
                    onBack = { finish() }
                )
            }
        }
    }

    companion object {
        const val EXTRA_USER_ID = "extra_user_id"
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UserProfileScreen(
    userId: String,
    onBack: () -> Unit
) {
    val db = remember { FirebaseDatabase.getInstance().reference }
    var profile by remember { mutableStateOf<UserProfile?>(null) }
    var loading by remember { mutableStateOf(true) }

    LaunchedEffect(userId) {
        loading = true
        if (userId.isBlank()) {
            profile = null
            loading = false
            return@LaunchedEffect
        }

        db.child("users").child(userId).get()
            .addOnSuccessListener { snap ->
                profile = snap.getValue(UserProfile::class.java)
                loading = false
            }
            .addOnFailureListener {
                profile = null
                loading = false
            }
    }

    Box(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        if (loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MessengerBlue
            )
        } else if (profile == null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Rounded.ErrorOutline, null, tint = MetaGray4, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(16.dp))
                Text("Perfil não encontrado", color = MetaGray4)
                TextButton(onClick = onBack) { Text("Voltar", color = MessengerBlue) }
            }
        } else {
            val p = profile!!
            
            // Background Immersive (Blurred Photo)
            Box(modifier = Modifier.fillMaxWidth().height(300.dp)) {
                AsyncImage(
                    model = p.photoUrl,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize().blur(40.dp),
                    contentScale = ContentScale.Crop,
                    alpha = 0.3f
                )
                Box(
                    modifier = Modifier.fillMaxSize()
                        .background(Brush.verticalGradient(listOf(Color.Transparent, MaterialTheme.colorScheme.background)))
                )
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Top Bar Custom
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBackIos, null, tint = MessengerBlue)
                        }
                        Text("Perfil", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        IconButton(onClick = { /* Share */ }) {
                            Icon(Icons.Rounded.IosShare, null, tint = MessengerBlue)
                        }
                    }
                }

                // Profile Photo
                item {
                    Spacer(Modifier.height(20.dp))
                    Box(contentAlignment = Alignment.BottomEnd) {
                        AsyncImage(
                            model = p.photoUrl,
                            contentDescription = null,
                            modifier = Modifier
                                .size(150.dp)
                                .clip(CircleShape)
                                .background(LocalChatColors.current.separator)
                                .border(4.dp, Color.White, CircleShape)
                                .shadow(12.dp, CircleShape),
                            contentScale = ContentScale.Crop
                        )
                        
                        val presenceColor = when (p.presenceStatus) {
                            "Online" -> iOSGreen
                            "Ocupado" -> iOSRed
                            "Ausente" -> iOSOrange
                            else -> Color.Gray
                        }
                        
                        if (p.isOnline && p.presenceStatus != "Invisível") {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White)
                                    .padding(4.dp)
                                    .background(presenceColor, CircleShape)
                                    .border(2.dp, Color.White, CircleShape)
                            )
                        }
                    }
                }

                // Name & ID
                item {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = p.displayName,
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Black
                    )
                    Text(
                        text = "@${p.id.lowercase()}",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MessengerBlue,
                        fontWeight = FontWeight.Medium
                    )
                    Spacer(Modifier.height(24.dp))
                }

                // Quick Actions
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        ProfileActionButton(Icons.AutoMirrored.Rounded.Message, "Chat")
                        ProfileActionButton(Icons.Rounded.Call, "Áudio")
                        ProfileActionButton(Icons.Rounded.VideoCall, "Vídeo")
                        ProfileActionButton(Icons.Rounded.Notifications, "Mudo")
                    }
                    Spacer(Modifier.height(32.dp))
                }

                // Info Sections
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(LocalChatColors.current.secondaryBackground)
                    ) {
                        ProfileInfoItem(Icons.Rounded.Info, "Recado", p.status)
                        ProfileInfoItem(Icons.Rounded.History, "Visto por último", if (p.isOnline) "Agora" else formatLastSeen(p.lastActive))
                        ProfileInfoItem(Icons.Rounded.Lock, "Privacidade", if (p.isHiddenFromSearch) "Oculto" else "Público", isLast = true)
                    }
                    Spacer(Modifier.height(24.dp))
                }

                // More Sections
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(LocalChatColors.current.secondaryBackground)
                    ) {
                        ProfileInfoItem(Icons.Rounded.Image, "Mídia e Links", "Fotos, vídeos e arquivos", showArrow = true)
                        ProfileInfoItem(Icons.Rounded.Groups, "Grupos em comum", "Nenhum grupo encontrado", isLast = true, showArrow = true)
                    }
                    Spacer(Modifier.height(32.dp))
                }

                // Block/Report Actions
                item {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        shape = RoundedCornerShape(20.dp),
                        color = LocalChatColors.current.secondaryBackground
                    ) {
                        Column {
                            ListItem(
                                headlineContent = { Text("Bloquear Usuário", color = iOSRed, fontWeight = FontWeight.Bold) },
                                leadingContent = { Icon(Icons.Rounded.Block, null, tint = iOSRed) },
                                modifier = Modifier.clickable { /* Block logic */ },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                            HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp), thickness = 0.5.dp, color = LocalChatColors.current.separator)
                            ListItem(
                                headlineContent = { Text("Denunciar Perfil", color = iOSRed, fontWeight = FontWeight.Bold) },
                                leadingContent = { Icon(Icons.Rounded.Report, null, tint = iOSRed) },
                                modifier = Modifier.clickable { /* Report logic */ },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent)
                            )
                        }
                    }
                    Spacer(Modifier.height(60.dp))
                }
            }
        }
    }
}

@Composable
private fun ProfileActionButton(icon: ImageVector, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.width(75.dp)
    ) {
        Surface(
            modifier = Modifier.size(54.dp),
            shape = RoundedCornerShape(16.dp),
            color = LocalChatColors.current.secondaryBackground,
            shadowElevation = 2.dp
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = MessengerBlue, modifier = Modifier.size(26.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
    }
}

@Composable
private fun ProfileInfoItem(
    icon: ImageVector,
    title: String,
    value: String,
    isLast: Boolean = false,
    showArrow: Boolean = false
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(MessengerBlue.copy(alpha = 0.1f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = MessengerBlue, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, fontSize = 12.sp, color = MetaGray4, fontWeight = FontWeight.Medium)
            Text(value, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        if (showArrow) {
            Icon(Icons.Rounded.ChevronRight, null, tint = MetaGray4, modifier = Modifier.size(20.dp))
        }
    }
    if (!isLast) {
        HorizontalDivider(modifier = Modifier.padding(start = 68.dp), thickness = 0.5.dp, color = LocalChatColors.current.separator)
    }
}

private fun formatLastSeen(timestamp: Long): String {
    if (timestamp == 0L) return "Recentemente"
    val date = Date(timestamp)
    val sdf = SimpleDateFormat("dd/MM/yy 'às' HH:mm", Locale.getDefault())
    return sdf.format(date)
}
