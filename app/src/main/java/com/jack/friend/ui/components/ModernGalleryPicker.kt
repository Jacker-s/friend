package com.jack.friend.ui.components

import android.net.Uri
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jack.friend.ChatViewModel
import com.jack.friend.LocalMedia
import com.jack.friend.ui.theme.LocalChatColors
import com.jack.friend.ui.theme.MessengerBlue

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ModernGalleryPicker(
    viewModel: ChatViewModel,
    onDismiss: () -> Unit,
    onSend: (List<Uri>) -> Unit
) {
    val context = LocalContext.current
    val localMedia by viewModel.localMedia.collectAsState()
    val selectedUris = remember { mutableStateListOf<Uri>() }

    LaunchedEffect(Unit) {
        viewModel.fetchLocalMedia(context)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Galeria", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, null) }
                },
                actions = {
                    if (selectedUris.isNotEmpty()) {
                        TextButton(onClick = { onSend(selectedUris.toList()); onDismiss() }) {
                            Text("ENVIAR (${selectedUris.size})", fontWeight = FontWeight.Bold, color = MessengerBlue)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = LocalChatColors.current.secondaryBackground
                )
            )
        },
        containerColor = LocalChatColors.current.secondaryBackground
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            contentPadding = padding,
            verticalArrangement = Arrangement.spacedBy(2.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(localMedia, key = { it.uri.toString() }) { media ->
                val isSelected = selectedUris.contains(media.uri)
                
                Box(
                    modifier = Modifier
                        .aspectRatio(1f)
                        .clickable {
                            if (isSelected) selectedUris.remove(media.uri)
                            else selectedUris.add(media.uri)
                        }
                ) {
                    AsyncImage(
                        model = media.uri,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )

                    if (media.isVideo) {
                        Icon(
                            Icons.Rounded.PlayCircle,
                            null,
                            tint = Color.White.copy(alpha = 0.8f),
                            modifier = Modifier.align(Alignment.BottomStart).padding(8.dp).size(24.dp)
                        )
                    }

                    if (isSelected) {
                        Box(
                            modifier = Modifier.fillMaxSize().background(Color.Black.copy(0.3f))
                        )
                        Icon(
                            Icons.Rounded.CheckCircle,
                            null,
                            tint = MessengerBlue,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).background(Color.White, CircleShape)
                        )
                    }
                }
            }
        }
    }
}
