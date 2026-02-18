package com.jack.friend

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Stars
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * ✅ Emoji picker com abas estilo WhatsApp/Telegram:
 * Recentes / Carinhas / Mãos / Corações / Objetos
 *
 * IMPORTANTÍSSIMO:
 * - Este arquivo usa o nome MetaEmojiPicker() para NÃO conflitar com o EmojiPicker antigo no MainActivity.
 * - Depois você troca a chamada no MainActivity e remove o EmojiPicker antigo de lá.
 */

enum class EmojiCategory { RECENTS, FACES, HANDS, HEARTS, OBJECTS }

data class EmojiTab(
    val category: EmojiCategory,
    val icon: ImageVector,
    val label: String
)

object EmojiData {
    val faces = listOf(
        "😀","😃","😄","😁","😆","😅","😂","🤣","😊","😇","🙂","🙃","😉","😍","🥰","😘","😗","😙","😚",
        "😋","😛","😜","🤪","😝","🤑","🤗","🤭","🤫","🤔","🤐","🤨","😐","😑","😶","😏","😒","🙄","😬","🤥",
        "😌","😔","😪","🤤","😴","😷","🤒","🤕","🤢","🤮","🤧","🥵","🥶","🥴","😵","🤯","🥳","😎","🤓","🧐",
        "😕","😟","🙁","😮","😯","😲","😳","🥺","😦","😧","😨","😰","😥","😢","😭","😱","😖","😣","😞","😓",
        "😩","😫","🥱","😤","😡","😠","🤬","😈","👿","💀","💩","🤡","👻","👽","👾","🤖"
    )

    val hands = listOf(
        "👋","🤚","✋","🖖","👌","✌️","🤞","🤟","🤘","🤙","👈","👉","👆","🖕","👇","👍","👎","✊","👊","🤛","🤜",
        "👏","🙌","👐","🤲","🤝","🙏","💪"
    )

    val hearts = listOf(
        "❤️","🧡","💛","💚","💙","💜","🖤","🤍","🤎","💔","❣️","💕","💞","💓","💗","💖","💘","💝","💟"
    )

    val objects = listOf(
        "🎉","🎊","🎁","🎈","🔥","✨","⭐","🌟","💫","⚡","☀️","🌙","☁️","🌧️","❄️",
        "🍕","🍔","🍟","🌭","🍿","🍣","🍩","🍪","🍫","☕","🥤",
        "⚽","🏀","🏈","🎮","🎧","📷","📎","📌","🧠","💡","🔔","🔒","🔑","🧩","🛠️"
    )
}

object EmojiRecentsStore {
    private const val PREFS = "emoji_prefs"
    private const val KEY = "emoji_recents"
    private const val MAX = 32
    private const val SEP = "||"

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(SEP).filter { it.isNotBlank() }.distinct()
    }

    fun push(context: Context, emoji: String) {
        val current = load(context).toMutableList()
        current.remove(emoji)
        current.add(0, emoji)
        val trimmed = current.take(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, trimmed.joinToString(SEP))
            .apply()
    }
}

@Composable
fun MetaEmojiPicker(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    heightDp: Int = 280
) {
    val context = LocalContext.current

    val tabs = remember {
        listOf(
            EmojiTab(EmojiCategory.RECENTS, Icons.Rounded.AccessTime, "Recentes"),
            EmojiTab(EmojiCategory.FACES, Icons.Rounded.EmojiEmotions, "Carinhas"),
            EmojiTab(EmojiCategory.HANDS, Icons.Rounded.PanTool, "Mãos"),
            EmojiTab(EmojiCategory.HEARTS, Icons.Rounded.Favorite, "Corações"),
            EmojiTab(EmojiCategory.OBJECTS, Icons.Rounded.Stars, "Objetos")
        )
    }

    var selectedTab by remember { mutableStateOf(EmojiCategory.RECENTS) }
    var recents by remember { mutableStateOf(EmojiRecentsStore.load(context)) }

    val emojis = remember(selectedTab, recents) {
        when (selectedTab) {
            EmojiCategory.RECENTS -> recents
            EmojiCategory.FACES -> EmojiData.faces
            EmojiCategory.HANDS -> EmojiData.hands
            EmojiCategory.HEARTS -> EmojiData.hearts
            EmojiCategory.OBJECTS -> EmojiData.objects
        }
    }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp),
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Abas (ícones)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                tabs.forEach { tab ->
                    val selected = tab.category == selectedTab
                    IconButton(
                        onClick = { selectedTab = tab.category },
                        modifier = Modifier.size(44.dp)
                    ) {
                        Icon(
                            imageVector = tab.icon,
                            contentDescription = tab.label,
                            tint = if (selected) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            HorizontalDivider()

            if (selectedTab == EmojiCategory.RECENTS && emojis.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "Sem recentes ainda 🙂\nToque em um emoji para ele aparecer aqui.",
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(minSize = 44.dp),
                    contentPadding = PaddingValues(10.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(emojis) { emoji ->
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clickable {
                                    EmojiRecentsStore.push(context, emoji)
                                    recents = EmojiRecentsStore.load(context)
                                    onEmojiSelected(emoji)
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(text = emoji, fontSize = 24.sp)
                        }
                    }
                }
            }
        }
    }
}
