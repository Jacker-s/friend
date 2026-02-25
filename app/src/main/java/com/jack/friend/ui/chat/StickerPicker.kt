package com.jack.friend.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.google.firebase.database.FirebaseDatabase

@Composable
fun StickerPicker(
    onStickerSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    heightDp: Int = 300
) {
    var stickers by remember { mutableStateOf<List<String>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(Unit) {
        // Fallback imediato para garantir que apareça algo se o Firebase demorar
        val fallbackList = listOf(
            "https://raw.githubusercontent.com/Telegram Messenger/Tato/master/stickers/1.webp",
            "https://raw.githubusercontent.com/Telegram Messenger/Tato/master/stickers/2.webp",
            "https://raw.githubusercontent.com/Telegram Messenger/Tato/master/stickers/3.webp",
            "https://raw.githubusercontent.com/Telegram Messenger/Tato/master/stickers/4.webp",
            "https://raw.githubusercontent.com/Telegram Messenger/Tato/master/stickers/5.webp",
            "https://raw.githubusercontent.com/Telegram Messenger/Tato/master/stickers/6.webp",
            "https://raw.githubusercontent.com/Telegram Messenger/Tato/master/stickers/7.webp",
            "https://raw.githubusercontent.com/Telegram Messenger/Tato/master/stickers/8.webp",
            "https://raw.githubusercontent.com/Telegram Messenger/Tato/master/stickers/9.webp",
            "https://raw.githubusercontent.com/Telegram Messenger/Tato/master/stickers/10.webp"
        )
        
        FirebaseDatabase.getInstance().reference.child("stickers").get()
            .addOnSuccessListener { snapshot ->
                val list = snapshot.children.mapNotNull { it.getValue(String::class.java) }
                stickers = if (list.isNotEmpty()) list else fallbackList
                isLoading = false
            }
            .addOnFailureListener {
                stickers = fallbackList
                isLoading = false
            }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
    ) {
        if (isLoading && stickers.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(12.dp))
                    Text("Carregando Stickers...", style = MaterialTheme.typography.labelMedium, color = Color.Gray)
                }
            }
        } else {
            Column {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp, 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color.LightGray.copy(alpha = 0.5f))
                    )
                }
                
                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(stickers) { stickerUrl ->
                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onStickerSelected(stickerUrl) },
                            contentAlignment = Alignment.Center
                        ) {
                            AsyncImage(
                                model = stickerUrl,
                                contentDescription = "Sticker",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
        }
    }
}
