package com.jack.friend.ui.components

import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jack.friend.ChatViewModel
import com.jack.friend.LocalMedia
import com.jack.friend.ui.theme.LocalChatColors
import com.jack.friend.ui.theme.MessengerBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MediaAttachmentSheet(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    onOpenCamera: () -> Unit,
    onOpenGallery: () -> Unit,
    onOpenFile: () -> Unit,
    onMediaSelected: (Uri, Boolean) -> Unit
) {
    val context = LocalContext.current
    val localMedia by viewModel.localMedia.collectAsState()
    val selectedUris = remember { mutableStateListOf<Pair<Uri, Boolean>>() }

    LaunchedEffect(Unit) {
        viewModel.fetchLocalMedia(context)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = LocalChatColors.current.secondaryBackground,
        dragHandle = { BottomSheetDefaults.DragHandle() },
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 40.dp)
        ) {
            // Header com botão de enviar se houver seleção múltipla
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Recentes",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                if (selectedUris.isNotEmpty()) {
                    TextButton(onClick = {
                        selectedUris.forEach { (uri, isVideo) ->
                            onMediaSelected(uri, isVideo)
                        }
                        onDismiss()
                    }) {
                        Text("ENVIAR (${selectedUris.size})", fontWeight = FontWeight.Bold, color = MessengerBlue)
                    }
                }
            }

            if (localMedia.isNotEmpty()) {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.height(120.dp)
                ) {
                    items(localMedia.take(20)) { media ->
                        val isSelected = selectedUris.any { it.first == media.uri }
                        
                        Box(
                            modifier = Modifier
                                .size(100.dp, 120.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .border(
                                    width = if (isSelected) 3.dp else 0.dp,
                                    color = if (isSelected) MessengerBlue else Color.Transparent,
                                    shape = RoundedCornerShape(12.dp)
                                )
                                .clickable {
                                    val pair = media.uri to media.isVideo
                                    if (isSelected) selectedUris.remove(pair)
                                    else selectedUris.add(pair)
                                }
                        ) {
                            AsyncImage(
                                model = media.uri,
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            
                            if (isSelected) {
                                Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.3f)))
                                Icon(
                                    Icons.Rounded.CheckCircle,
                                    null,
                                    tint = MessengerBlue,
                                    modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(20.dp).background(Color.White, CircleShape)
                                )
                            }

                            if (media.isVideo) {
                                Icon(
                                    Icons.Rounded.PlayCircle,
                                    null,
                                    tint = Color.White.copy(alpha = 0.8f),
                                    modifier = Modifier.align(Alignment.Center).size(32.dp)
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
            }

            // Ações principais
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MediaActionItem(
                    icon = Icons.Rounded.PhotoCamera,
                    label = "Câmera",
                    color = Color(0xFFE91E63),
                    onClick = { onOpenCamera(); onDismiss() }
                )
                MediaActionItem(
                    icon = Icons.Rounded.Image,
                    label = "Galeria",
                    color = Color(0xFF9C27B0),
                    onClick = { onOpenGallery(); onDismiss() }
                )
                MediaActionItem(
                    icon = Icons.Rounded.Description,
                    label = "Arquivo",
                    color = Color(0xFF5E5EDD),
                    onClick = { onOpenFile(); onDismiss() }
                )
            }
        }
    }
}

@Composable
private fun MediaActionItem(
    icon: ImageVector,
    label: String,
    color: Color,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onClick() }
    ) {
        Surface(
            modifier = Modifier.size(60.dp),
            shape = CircleShape,
            color = color.copy(alpha = 0.15f)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(icon, null, tint = color, modifier = Modifier.size(28.dp))
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(label, fontSize = 13.sp, fontWeight = FontWeight.Medium)
    }
}
