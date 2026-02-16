package com.jack.friend

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.airbnb.lottie.compose.*
import kotlinx.coroutines.launch
import java.util.*

object AnimatedEmojiHelper {
    private const val BASE_URL = "https://fonts.gstatic.com/s/e/notoemoji/latest"

    /**
     * Gera a URL da animação Lottie baseada no codepoint do emoji.
     * Exemplo: ❤️ -> 2764_fe0f
     */
    fun getAnimUrl(emoji: String): String {
        val codepoints = mutableListOf<String>()
        var i = 0
        while (i < emoji.length) {
            val cp = emoji.codePointAt(i)
            codepoints.add(Integer.toHexString(cp).lowercase(Locale.ROOT))
            i += Character.charCount(cp)
        }
        
        // Remove seletores de variação desnecessários para a URL do Google se houver mais de um codepoint
        // Mas o Google costuma usar o formato completo ou específico. 
        // Vamos tentar o formato padrão que eles usam.
        val fileName = codepoints.joinToString("_")
        return "$BASE_URL/$fileName/lottie.json"
    }

    fun isSingleEmoji(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return false
        
        var count = 0
        var hasEmoji = false
        var i = 0
        while (i < trimmed.length) {
            val cp = trimmed.codePointAt(i)
            if (!isIgnorableInEmoji(cp)) {
                count++
                if (isEmoji(cp)) hasEmoji = true
            }
            i += Character.charCount(cp)
        }
        return count == 1 && hasEmoji
    }

    private fun isIgnorableInEmoji(cp: Int): Boolean {
        return cp == 0x200D || cp in 0x1F3FB..0x1F3FF || cp == 0xFE0F // Joiners, Skin tones, Variation Selector-16
    }

    private fun isEmoji(codePoint: Int): Boolean {
        return codePoint in 0x1F300..0x1F9FF || 
               codePoint in 0x1F600..0x1F64F || 
               codePoint in 0x1F680..0x1F6FF || 
               codePoint in 0x2600..0x26FF ||   
               codePoint in 0x2700..0x27BF ||   
               codePoint in 0x1F900..0x1F9FF || 
               codePoint in 0x1FA00..0x1FAFF    
    }
}

@Composable
fun AnimatedEmoji(emoji: String, modifier: Modifier = Modifier, onLongClick: () -> Unit = {}) {
    val url = AnimatedEmojiHelper.getAnimUrl(emoji)
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    val scale = remember { Animatable(1f) }
    
    // Tenta carregar a composição.
    val composition by rememberLottieComposition(LottieCompositionSpec.Url(url))
    
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    if (composition != null) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = modifier
                .graphicsLayer(scaleX = scale.value, scaleY = scale.value)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            coroutineScope.launch {
                                scale.animateTo(1.4f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            }
                        },
                        onLongPress = { onLongClick() }
                    )
                }
        )
    } else {
        // Fallback: Se o Google não tiver a animação, mostra o emoji estático grande
        Box(
            modifier = modifier
                .graphicsLayer(scaleX = scale.value, scaleY = scale.value)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            coroutineScope.launch {
                                scale.animateTo(1.4f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            }
                        },
                        onLongPress = { onLongClick() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text(text = emoji, fontSize = 64.sp)
        }
    }
}
