package com.jack.friend

import android.content.Context
import android.graphics.Paint
import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccessTime
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PanTool
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Inventory2
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import kotlin.math.abs
import androidx.compose.material.icons.rounded.Pets
import androidx.compose.material.icons.rounded.Restaurant
import androidx.compose.material.icons.rounded.Inventory2

enum class MetaEmojiCategory { RECENTS, FACES, HANDS, HEARTS, ANIMALS, FOOD, OBJECTS, SYMBOLS, ALL }

data class MetaEmojiTab(
    val category: MetaEmojiCategory,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val label: String
)

private object MetaEmojiRecentsStore {
    private const val PREFS = "meta_emoji_prefs"
    private const val KEY = "meta_emoji_recents"
    const val MAX = 48
    private const val SEP = "||"

    fun load(context: Context): List<String> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split(SEP).map { it.trim() }.filter { it.isNotEmpty() }.distinct().take(MAX)
    }

    fun push(context: Context, emoji: String) {
        val current = load(context).toMutableList()
        current.remove(emoji)
        current.add(0, emoji)
        val trimmed = current.distinct().take(MAX)
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY, trimmed.joinToString(SEP))
            .apply()
    }
}

private object MetaEmojiSafeData {
    val fallbackRecents = listOf("😂", "❤️", "😍", "🙏", "👍", "😭", "🔥", "✨", "😮", "😢", "🥰", "😎", "👌", "🤝")

    // CARINHAS
    val faces = listOf(
        "😀","😃","😄","😁","😆","😅","😂","🤣",
        "🥲","☺️","😊","😇","🙂","🙃","😉","😌",
        "😍","🥰","😘","😗","😙","😚","😋","😛",
        "😜","🤪","😝","🤑","🤗","🤭","🤫","🤔",
        "🤐","🤨","😐","😑","😶","🫥","😏","😒",
        "🙄","😬","😮‍💨","🤥","😔","😪","🤤","😴",
        "😷","🤒","🤕","🤢","🤮","🤧","🥵","🥶",
        "🥴","😵","😵‍💫","🤯","🤠","🥳","🥸",
        "😎","🤓","🧐","😕","😟","🙁","☹️","😮",
        "😯","😲","😳","🥺","😦","😧","😨","😰",
        "😥","😢","😭","😱","😖","😣","😞","😓",
        "😩","😫","🥱","😤","😡","😠","🤬","😈",
        "👿","💀","☠️","💩","🤡","👹","👺","👻",
        "👽","👾","🤖","😺","😸","😹","😻","😼",
        "😽","🙀","😿","😾"
    )

    // MÃOS
    val hands = listOf(
        "👋","🤚","🖐️","✋","🖖","👌","🤌","🤏",
        "✌️","🤞","🫰","🤟","🤘","🤙",
        "👈","👉","👆","🖕","👇","☝️",
        "👍","👎","✊","👊","🤛","🤜",
        "👏","🙌","👐","🤲","🤝","🙏",
        "✍️","💅","🤳","💪","🦾","🦿"
    )

    // CORAÇÕES
    val hearts = listOf(
        "❤️","🧡","💛","💚","💙","💜",
        "🖤","🤍","🤎","💔","❣️",
        "💕","💞","💓","💗","💖",
        "💘","💝","💟","❤️‍🔥","❤️‍🩹"
    )

    // ANIMAIS
    val animals = listOf(
        "🐶","🐱","🐭","🐹","🐰","🦊","🐻","🐼","🐻‍❄️",
        "🐨","🐯","🦁","🐮","🐷","🐸","🐵","🙈","🙉","🙊",
        "🐔","🐧","🐦","🐤","🦆","🦅","🦉","🦇",
        "🐺","🐗","🐴","🦄","🐝","🐛","🦋","🐌",
        "🐞","🐜","🪲","🐢","🐍","🦎","🦂",
        "🦀","🦞","🦐","🐙","🦑",
        "🐬","🐳","🐋","🦈",
        "🐘","🦒","🦓","🦍","🦧","🦘",
        "🐄","🐎","🐖","🐏","🐑","🐐",
        "🐕","🐩","🐈","🐓"
    )

    // COMIDA
    val food = listOf(
        "🍎","🍏","🍐","🍊","🍋","🍌","🍉","🍇",
        "🍓","🫐","🍈","🍒","🍑","🥭","🍍","🥥",
        "🥝","🍅","🥑","🍆","🥔","🥕","🌽",
        "🍞","🥖","🥨","🧀","🥚","🍳",
        "🥞","🧇","🥓","🍔","🍟","🍕",
        "🌭","🥪","🌮","🌯","🥗",
        "🍿","🍩","🍪","🎂","🍰","🧁",
        "🍫","🍬","🍭","🍮","🍦","🍨",
        "☕","🍵","🥤","🍺","🍷"
    )

    // OBJETOS
    val objects = listOf(
        "⌚","📱","💻","⌨️","🖥️","🖨️",
        "📷","📹","🎥","📺",
        "💡","🔦","🕯️",
        "📦","📚","📖","📎","✏️","🖊️",
        "💰","💳","💎",
        "🔨","⚙️","🛠️","🔧",
        "🚗","🚕","🚙","🚌","🚓","🚑","🚒",
        "✈️","🚀","🚁",
        "🏠","🏢","🏭",
        "🎮","🎧","🎤","🎹","🥁",
        "⚽","🏀","🏈","⚾","🎾"
    )

    // SÍMBOLOS
    val symbols = listOf(
        "✨","⭐","🌟","💫","🔥","⚡","💥","💢","💤","💦",
        "🎉","🎊","🎈","🎁",
        "✅","☑️","✔️","✖️","❌","❗","❕","❓","‼️","⁉️",
        "🔴","🟠","🟡","🟢","🔵","🟣","⚫","⚪","🟤",
        "⬆️","⬇️","⬅️","➡️","↗️","↘️","↙️","↖️",
        "🔼","🔽","⏩","⏪","⏫","⏬",
        "🔒","🔓","🔑","🗝️","🔔","🔕",
        "💲","💯","♻️","⚠️","🚫","⭕"
    )
}

private object MetaEmojiSupport {
    private val paint by lazy { Paint() }

    fun filterSupported(list: List<String>): List<String> {
        return list.asSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .filter { emoji ->
                try { paint.hasGlyph(emoji) } catch (_: Throwable) { false }
            }
            .distinct()
            .toList()
    }
}

private fun modIndex(page: Int, size: Int): Int {
    val m = page % size
    return if (m < 0) m + size else m
}

private fun nearestPageForIndex(currentPage: Int, size: Int, targetIndex: Int): Int {
    val curIndex = modIndex(currentPage, size)
    val forward = (targetIndex - curIndex + size) % size
    val backward = forward - size
    return if (abs(backward) < abs(forward)) currentPage + backward else currentPage + forward
}

@Composable
fun MetaEmojiPickerPro(
    onEmojiSelected: (String) -> Unit,
    modifier: Modifier = Modifier,
    heightDp: Int = 300
) {
    val context = LocalContext.current
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val tabs = remember {
        listOf(
            MetaEmojiTab(MetaEmojiCategory.RECENTS, Icons.Rounded.AccessTime, "Recentes"),
            MetaEmojiTab(MetaEmojiCategory.FACES, Icons.Rounded.EmojiEmotions, "Carinhas"),
            MetaEmojiTab(MetaEmojiCategory.HANDS, Icons.Rounded.PanTool, "Mãos"),
            MetaEmojiTab(MetaEmojiCategory.HEARTS, Icons.Rounded.Favorite, "Corações"),
            MetaEmojiTab(MetaEmojiCategory.ANIMALS, Icons.Rounded.Pets, "Animais"),
            MetaEmojiTab(MetaEmojiCategory.FOOD, Icons.Rounded.Restaurant, "Comida"),
            MetaEmojiTab(MetaEmojiCategory.OBJECTS, Icons.Rounded.Inventory2, "Objetos"),
            MetaEmojiTab(MetaEmojiCategory.SYMBOLS, Icons.Rounded.Category, "Símbolos"),
            MetaEmojiTab(MetaEmojiCategory.ALL, Icons.Rounded.Category, "Tudo"),
        )
    }


    val categories = remember { tabs.map { it.category } }
    val catCount = categories.size

    var recents by remember { mutableStateOf(MetaEmojiRecentsStore.load(context)) }

    // Pré-filtradas (evita tofu/X)
    val safeFaces = remember { MetaEmojiSupport.filterSupported(MetaEmojiSafeData.faces) }
    val safeHands = remember { MetaEmojiSupport.filterSupported(MetaEmojiSafeData.hands) }
    val safeHearts = remember { MetaEmojiSupport.filterSupported(MetaEmojiSafeData.hearts) }
    val safeAnimals = remember { MetaEmojiSupport.filterSupported(MetaEmojiSafeData.animals) }
    val safeFood = remember { MetaEmojiSupport.filterSupported(MetaEmojiSafeData.food) }
    val safeObjects = remember { MetaEmojiSupport.filterSupported(MetaEmojiSafeData.objects) }
    val safeSymbols = remember { MetaEmojiSupport.filterSupported(MetaEmojiSafeData.symbols) }
    val safeFallbackRecents = remember { MetaEmojiSupport.filterSupported(MetaEmojiSafeData.fallbackRecents) }

    val safeAll = remember {
        MetaEmojiSupport.filterSupported(
            safeFaces + safeHands + safeHearts + safeAnimals + safeFood + safeObjects + safeSymbols + safeFallbackRecents
        )
    }

    fun emojisFor(cat: MetaEmojiCategory): List<String> {
        return when (cat) {
            MetaEmojiCategory.RECENTS -> {
                val loaded = MetaEmojiSupport.filterSupported(recents)
                if (loaded.isEmpty()) safeFallbackRecents else loaded
            }
            MetaEmojiCategory.FACES -> safeFaces
            MetaEmojiCategory.HANDS -> safeHands
            MetaEmojiCategory.HEARTS -> safeHearts
            MetaEmojiCategory.ANIMALS -> safeAnimals
            MetaEmojiCategory.FOOD -> safeFood
            MetaEmojiCategory.OBJECTS -> safeObjects
            MetaEmojiCategory.SYMBOLS -> safeSymbols
            MetaEmojiCategory.ALL -> safeAll
        }
    }

    val startPage = remember {
        val mid = Int.MAX_VALUE / 2
        val hasRecents = MetaEmojiRecentsStore.load(context).isNotEmpty()
        val targetIndex = if (hasRecents)
            categories.indexOf(MetaEmojiCategory.RECENTS)
        else
            categories.indexOf(MetaEmojiCategory.FACES)

        nearestPageForIndex(mid, catCount, targetIndex.coerceAtLeast(0))
    }

    val pagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { Int.MAX_VALUE }
    )

    val selectedCategory by remember {
        derivedStateOf { categories[modIndex(pagerState.currentPage, catCount)] }
    }

    val recentsIndex = remember { categories.indexOf(MetaEmojiCategory.RECENTS).coerceAtLeast(0) }

    // Visual: “sheet” com cantos arredondados e barra superior
    val sheetShape = remember { RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp) }
    val pillShape = remember { RoundedCornerShape(999.dp) }
    val handleColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.25f)

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .height(heightDp.dp)
            .navigationBarsPadding()
            .clip(sheetShape),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        shape = sheetShape
    ) {
        Column(modifier = Modifier.fillMaxSize()) {

            // Header premium
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 10.dp, bottom = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .width(44.dp)
                        .height(5.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(handleColor)
                )

                Spacer(Modifier.height(10.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AnimatedContent(
                        targetState = selectedCategory,
                        transitionSpec = {
                            (fadeIn(tween(140)) togetherWith fadeOut(tween(120)))
                                .using(SizeTransform(clip = false))
                        },
                        label = "category_title"
                    ) { cat ->
                        Text(
                            text = when (cat) {
                                MetaEmojiCategory.RECENTS -> "Recentes"
                                MetaEmojiCategory.FACES -> "Carinhas"
                                MetaEmojiCategory.HANDS -> "Mãos"
                                MetaEmojiCategory.HEARTS -> "Corações"
                                MetaEmojiCategory.ANIMALS -> "Animais"
                                MetaEmojiCategory.FOOD -> "Comida"
                                MetaEmojiCategory.OBJECTS -> "Objetos"
                                MetaEmojiCategory.SYMBOLS -> "Símbolos"
                                MetaEmojiCategory.ALL -> "Tudo"
                            },
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.padding(start = 2.dp)
                        )
                    }

                    // dica discreta
                    Text(
                        text = "Deslize ← →",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f))

            // Pager infinito entre categorias
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) { page ->
                val cat = categories[modIndex(page, catCount)]
                val emojis = remember(cat, recents) { emojisFor(cat) }

                if (cat == MetaEmojiCategory.RECENTS && emojis.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "Sem recentes ainda 🙂\nToque em um emoji para ele aparecer aqui.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(18.dp)
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 44.dp),
                        contentPadding = PaddingValues(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 72.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(emojis, key = { it }) { emoji ->
                            val interaction = remember { MutableInteractionSource() }
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                                    .clickable(
                                        interactionSource = interaction,
                                        indication = ripple(bounded = true),
                                    ) {
                                        // Haptic premium
                                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)

                                        // salva recente
                                        MetaEmojiRecentsStore.push(context, emoji)
                                        recents = MetaEmojiRecentsStore.load(context)

                                        // envia pro input
                                        onEmojiSelected(emoji)

                                        // pula pra Recentes automaticamente
                                        if (selectedCategory != MetaEmojiCategory.RECENTS) {
                                            val targetPage = nearestPageForIndex(
                                                currentPage = pagerState.currentPage,
                                                size = catCount,
                                                targetIndex = recentsIndex
                                            )
                                            scope.launch { pagerState.animateScrollToPage(targetPage) }
                                        }
                                    }
                                    .semantics { contentDescription = "Emoji $emoji" },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = emoji, fontSize = 22.sp)
                            }
                        }
                    }
                }
            }

            // Tabs em “pill” (mais profissional)
            Surface(
                tonalElevation = 2.dp,
                color = MaterialTheme.colorScheme.surface,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    tabs.forEach { tab ->
                        val isSelected = tab.category == selectedCategory
                        val bg = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                        val fg = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant

                        Row(
                            modifier = Modifier
                                .height(34.dp)
                                .clip(pillShape)
                                .background(bg)
                                .clickable {
                                    val targetIndex = categories.indexOf(tab.category)
                                    if (targetIndex >= 0) {
                                        val targetPage = nearestPageForIndex(pagerState.currentPage, catCount, targetIndex)
                                        scope.launch { pagerState.animateScrollToPage(targetPage) }
                                    }
                                }
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(tab.icon, contentDescription = tab.label, tint = fg, modifier = Modifier.size(18.dp))
                            if (isSelected) {
                                Text(
                                    text = tab.label,
                                    color = fg,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
