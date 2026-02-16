package com.jack.friend

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
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

    fun getEffectEmojis(emoji: String): List<String> {
        return when (emoji) {
            "🔥" -> listOf("✨", "💥", "⚡", "🔥")
            "❤️", "🥰", "😍" -> listOf("💖", "💕", "✨", "💓")
            "😂", "🤣" -> listOf("💧", "✨", "😆")
            "🎉", "🥳" -> listOf("🎊", "✨", "🎈", "🎈")
            "👍" -> listOf("✨", "✅", "⭐")
            "💪" -> listOf("⚡", "✨", "💥")
            "🚀" -> listOf("🔥", "✨", "💨", "💨")
            "😭", "😢" -> listOf("💧", "🌊", "❄️")
            "🤯" -> listOf("🧠", "💥", "✨")
            "💰", "🤑" -> listOf("💵", "💎", "💰")
            else -> listOf("✨", "⭐") // Efeito padrão de brilho
        }
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

data class EmojiParticle(
    val id: Long,
    val angle: Float,
    val velocity: Float,
    val emoji: String,
    val scale: Float
)

@Composable
fun AnimatedEmoji(emoji: String, modifier: Modifier = Modifier, onLongClick: () -> Unit = {}) {
    val url = AnimatedEmojiHelper.getAnimUrl(emoji)
    val view = LocalView.current
    val coroutineScope = rememberCoroutineScope()
    
    val scale = remember { Animatable(1f) }
    val rotation = remember { Animatable(0f) }
    val particles = remember { mutableStateListOf<EmojiParticle>() }
    var particleId by remember { mutableStateOf(0L) }

    val composition by rememberLottieComposition(LottieCompositionSpec.Url(url))
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever
    )

    Box(contentAlignment = Alignment.Center, modifier = Modifier.size(140.dp)) {
        // Renderizar Partículas de Efeito
        particles.forEach { particle ->
            key(particle.id) {
                ParticleEffect(particle) {
                    particles.remove(particle)
                }
            }
        }

        val interactionModifier = Modifier
            .size(100.dp)
            .graphicsLayer(
                scaleX = scale.value,
                scaleY = scale.value,
                rotationZ = rotation.value
            )
            .pointerInput(emoji) {
                detectTapGestures(
                    onTap = {
                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                        
                        // Disparar Partículas Relacionadas
                        val effectEmojis = AnimatedEmojiHelper.getEffectEmojis(emoji)
                        repeat(10) {
                            particles.add(
                                EmojiParticle(
                                    id = particleId++,
                                    angle = Random.nextFloat() * 360f,
                                    velocity = Random.nextFloat() * 180f + 120f,
                                    emoji = effectEmojis.random(),
                                    scale = Random.nextFloat() * 0.5f + 0.3f
                                )
                            )
                        }

                        coroutineScope.launch {
                            // Efeito de Pulo e Giro Aleatório
                            launch {
                                scale.animateTo(1.6f, spring(dampingRatio = Spring.DampingRatioMediumBouncy))
                                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            }
                            launch {
                                rotation.animateTo((Random.nextFloat() * 40f) - 20f, tween(100))
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
                modifier = interactionModifier
            )
        } else {
            Box(
                modifier = interactionModifier,
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Text(text = emoji, fontSize = 64.sp)
            }
        }
    }
}

@Composable
fun ParticleEffect(particle: EmojiParticle, onEnd: () -> Unit) {
    val animationProgress = remember { Animatable(0f) }
    
    LaunchedEffect(Unit) {
        animationProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 1000, easing = LinearOutSlowInEasing)
        )
        onEnd()
    }

    val angleRad = Math.toRadians(particle.angle.toDouble())
    val distance = particle.velocity * animationProgress.value
    val offsetX = (Math.cos(angleRad) * distance).dp
    val offsetY = (Math.sin(angleRad) * distance).dp
    val alpha = 1f - (animationProgress.value * animationProgress.value) // Desaparece no final

    Box(
        modifier = Modifier
            .offset(x = offsetX, y = offsetY)
            .graphicsLayer(
                scaleX = particle.scale,
                scaleY = particle.scale,
                alpha = alpha,
                rotationZ = animationProgress.value * 720f
            )
    ) {
        androidx.compose.material3.Text(text = particle.emoji, fontSize = 22.sp)
    }
}
