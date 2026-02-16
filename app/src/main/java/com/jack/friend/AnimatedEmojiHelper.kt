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
import kotlin.random.Random

object AnimatedEmojiHelper {
    private const val BASE_URL = "https://fonts.gstatic.com/s/e/notoemoji/latest"

    fun getAnimUrl(emoji: String): String {
        val codepoints = mutableListOf<String>()
        var i = 0
        while (i < emoji.length) {
            val cp = emoji.codePointAt(i)
            codepoints.add(Integer.toHexString(cp).lowercase(Locale.ROOT))
            i += Character.charCount(cp)
        }
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
        return cp == 0x200D || cp in 0x1F3FB..0x1F3FF || cp == 0xFE0F
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
    
    // Estados de animação de escala e rotação
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    
    // Controlar a reprodução da animação
    val composition by rememberLottieComposition(LottieCompositionSpec.Url(url))
    
    // Progress manual para permitir "reset" ao clicar
    var restartTrigger by remember { mutableStateOf(0) }
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        restartOnPlay = true
    )

    // Efeito de feedback tátil e visual ao tocar
    val interactionModifier = Modifier
        .graphicsLayer(
            scaleX = scale.value,
            scaleY = scale.value,
            rotationZ = rotation.value
        )
        .pointerInput(emoji) {
            detectTapGestures(
                onTap = {
                    // Feedback Tátil (Vibração)
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    
                    coroutineScope.launch {
                        // Reset da animação Lottie (opcional, aqui simulado pelo pulo)
                        restartTrigger++ 
                        
                        // Efeito de Pulo (Bounce) e Rotação Aleatória
                        val randomRotation = (Random.nextFloat() * 20f) - 10f // -10 a 10 graus
                        
                        launch {
                            scale.animateTo(1.5f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                            scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                        }
                        launch {
                            rotation.animateTo(randomRotation, tween(100))
                            rotation.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                        }
                    }
                },
                onLongPress = { onLongClick() }
            )
        }

    if (composition != null) {
        LottieAnimation(
            composition = composition,
            progress = { progress },
            modifier = modifier.then(interactionModifier)
        )
    } else {
        Box(
            modifier = modifier.then(interactionModifier),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.material3.Text(text = emoji, fontSize = 64.sp)
        }
    }
}
