package com.jack.friend

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.*
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.*
import kotlinx.coroutines.launch

object AnimatedEmojiHelper {
    private const val BASE_URL = "https://fonts.gstatic.com/s/e/notoemoji/latest"

    private val emojiToAnimUrl = mapOf(
        // --- SMILEYS & EMOTIONS ---
        "😀" to "$BASE_URL/1f600/lottie.json",
        "😃" to "$BASE_URL/1f603/lottie.json",
        "😄" to "$BASE_URL/1f604/lottie.json",
        "😁" to "$BASE_URL/1f601/lottie.json",
        "😆" to "$BASE_URL/1f606/lottie.json",
        "😅" to "$BASE_URL/1f605/lottie.json",
        "🤣" to "$BASE_URL/1f923/lottie.json",
        "😂" to "$BASE_URL/1f602/lottie.json",
        "🙂" to "$BASE_URL/1f642/lottie.json",
        "🙃" to "$BASE_URL/1f643/lottie.json",
        "😉" to "$BASE_URL/1f609/lottie.json",
        "😊" to "$BASE_URL/1f60a/lottie.json",
        "😇" to "$BASE_URL/1f607/lottie.json",
        "🥰" to "$BASE_URL/1f970/lottie.json",
        "😍" to "$BASE_URL/1f60d/lottie.json",
        "🤩" to "$BASE_URL/1f929/lottie.json",
        "😘" to "$BASE_URL/1f618/lottie.json",
        "😗" to "$BASE_URL/1f617/lottie.json",
        "😚" to "$BASE_URL/1f61a/lottie.json",
        "😙" to "$BASE_URL/1f619/lottie.json",
        "😋" to "$BASE_URL/1f60b/lottie.json",
        "😛" to "$BASE_URL/1f61b/lottie.json",
        "😜" to "$BASE_URL/1f61c/lottie.json",
        "🤪" to "$BASE_URL/1f92a/lottie.json",
        "😝" to "$BASE_URL/1f61d/lottie.json",
        "🤑" to "$BASE_URL/1f911/lottie.json",
        "🤗" to "$BASE_URL/1f917/lottie.json",
        "🤭" to "$BASE_URL/1f92d/lottie.json",
        "🤫" to "$BASE_URL/1f92b/lottie.json",
        "🤔" to "$BASE_URL/1f914/lottie.json",
        "🤐" to "$BASE_URL/1f910/lottie.json",
        "🤨" to "$BASE_URL/1f928/lottie.json",
        "😐" to "$BASE_URL/1f610/lottie.json",
        "😑" to "$BASE_URL/1f611/lottie.json",
        "😶" to "$BASE_URL/1f636/lottie.json",
        "😏" to "$BASE_URL/1f60f/lottie.json",
        "😒" to "$BASE_URL/1f612/lottie.json",
        "🙄" to "$BASE_URL/1f644/lottie.json",
        "😬" to "$BASE_URL/1f62c/lottie.json",
        "🤥" to "$BASE_URL/1f925/lottie.json",
        "😌" to "$BASE_URL/1f60c/lottie.json",
        "😔" to "$BASE_URL/1f614/lottie.json",
        "😪" to "$BASE_URL/1f62a/lottie.json",
        "🤤" to "$BASE_URL/1f924/lottie.json",
        "😴" to "$BASE_URL/1f634/lottie.json",
        "😷" to "$BASE_URL/1f637/lottie.json",
        "🤒" to "$BASE_URL/1f912/lottie.json",
        "🤕" to "$BASE_URL/1f915/lottie.json",
        "🤢" to "$BASE_URL/1f922/lottie.json",
        "🤮" to "$BASE_URL/1f92e/lottie.json",
        "🤧" to "$BASE_URL/1f927/lottie.json",
        "🥵" to "$BASE_URL/1f975/lottie.json",
        "🥶" to "$BASE_URL/1f976/lottie.json",
        "🥴" to "$BASE_URL/1f974/lottie.json",
        "😵" to "$BASE_URL/1f635/lottie.json",
        "🤯" to "$BASE_URL/1f92f/lottie.json",
        "🤠" to "$BASE_URL/1f920/lottie.json",
        "🥳" to "$BASE_URL/1f973/lottie.json",
        "😎" to "$BASE_URL/1f60e/lottie.json",
        "🤓" to "$BASE_URL/1f913/lottie.json",
        "🧐" to "$BASE_URL/1f9d0/lottie.json",
        "😕" to "$BASE_URL/1f615/lottie.json",
        "😟" to "$BASE_URL/1f61f/lottie.json",
        "🙁" to "$BASE_URL/1f641/lottie.json",
        "😮" to "$BASE_URL/1f62e/lottie.json",
        "😯" to "$BASE_URL/1f62f/lottie.json",
        "😲" to "$BASE_URL/1f632/lottie.json",
        "😳" to "$BASE_URL/1f633/lottie.json",
        "🥺" to "$BASE_URL/1f97a/lottie.json",
        "😦" to "$BASE_URL/1f626/lottie.json",
        "😧" to "$BASE_URL/1f627/lottie.json",
        "😨" to "$BASE_URL/1f628/lottie.json",
        "😰" to "$BASE_URL/1f630/lottie.json",
        "😥" to "$BASE_URL/1f625/lottie.json",
        "😢" to "$BASE_URL/1f622/lottie.json",
        "😭" to "$BASE_URL/1f62d/lottie.json",
        "😱" to "$BASE_URL/1f631/lottie.json",
        "😖" to "$BASE_URL/1f616/lottie.json",
        "😣" to "$BASE_URL/1f623/lottie.json",
        "😞" to "$BASE_URL/1f61e/lottie.json",
        "😓" to "$BASE_URL/1f613/lottie.json",
        "😩" to "$BASE_URL/1f629/lottie.json",
        "😫" to "$BASE_URL/1f62b/lottie.json",
        "🥱" to "$BASE_URL/1f971/lottie.json",
        "😤" to "$BASE_URL/1f624/lottie.json",
        "😡" to "$BASE_URL/1f621/lottie.json",
        "😠" to "$BASE_URL/1f620/lottie.json",
        "🤬" to "$BASE_URL/1f92c/lottie.json",
        "😈" to "$BASE_URL/1f608/lottie.json",
        "👿" to "$BASE_URL/1f47f/lottie.json",
        "💀" to "$BASE_URL/1f480/lottie.json",
        "💩" to "$BASE_URL/1f4a9/lottie.json",
        "🤡" to "$BASE_URL/1f921/lottie.json",
        "ghost" to "$BASE_URL/1f47b/lottie.json",
        "👽" to "$BASE_URL/1f47d/lottie.json",
        "👾" to "$BASE_URL/1f47e/lottie.json",
        "🤖" to "$BASE_URL/1f916/lottie.json",

        // --- HANDS & GESTURES ---
        "👋" to "$BASE_URL/1f44b/lottie.json",
        "🤚" to "$BASE_URL/1f91a/lottie.json",
        "✋" to "$BASE_URL/270b/lottie.json",
        "🖖" to "$BASE_URL/1f596/lottie.json",
        "👌" to "$BASE_URL/1f44c/lottie.json",
        "✌️" to "$BASE_URL/270c_fe0f/lottie.json",
        "🤞" to "$BASE_URL/1f91e/lottie.json",
        "🤟" to "$BASE_URL/1f91f/lottie.json",
        "🤘" to "$BASE_URL/1f918/lottie.json",
        "🤙" to "$BASE_URL/1f919/lottie.json",
        "👈" to "$BASE_URL/1f448/lottie.json",
        "👉" to "$BASE_URL/1f449/lottie.json",
        "👆" to "$BASE_URL/1f446/lottie.json",
        "🖕" to "$BASE_URL/1f595/lottie.json",
        "👇" to "$BASE_URL/1f447/lottie.json",
        "👍" to "$BASE_URL/1f44d/lottie.json",
        "👎" to "$BASE_URL/1f44e/lottie.json",
        "✊" to "$BASE_URL/270a/lottie.json",
        "👊" to "$BASE_URL/1f44a/lottie.json",
        "👏" to "$BASE_URL/1f44f/lottie.json",
        "🙌" to "$BASE_URL/1f64c/lottie.json",
        "👐" to "$BASE_URL/1f450/lottie.json",
        "🤲" to "$BASE_URL/1f932/lottie.json",
        "🤝" to "$BASE_URL/1f91d/lottie.json",
        "🙏" to "$BASE_URL/1f64f/lottie.json",
        "💪" to "$BASE_URL/1f4aa/lottie.json",

        // --- HEARTS ---
        "❤️" to "$BASE_URL/2764_fe0f/lottie.json",
        "🧡" to "$BASE_URL/1f9e1/lottie.json",
        "💛" to "$BASE_URL/1f49b/lottie.json",
        "💚" to "$BASE_URL/1f49a/lottie.json",
        "💙" to "$BASE_URL/1f499/lottie.json",
        "💜" to "$BASE_URL/1f49c/lottie.json",
        "🖤" to "$BASE_URL/1f5a4/lottie.json",
        "🤍" to "$BASE_URL/1f90d/lottie.json",
        "🤎" to "$BASE_URL/1f90e/lottie.json",
        "💔" to "$BASE_URL/1f494/lottie.json",
        "❣️" to "$BASE_URL/2763_fe0f/lottie.json",
        "💕" to "$BASE_URL/1f495/lottie.json",
        "💞" to "$BASE_URL/1f49e/lottie.json",
        "💓" to "$BASE_URL/1f493/lottie.json",
        "💗" to "$BASE_URL/1f497/lottie.json",
        "💖" to "$BASE_URL/1f496/lottie.json",
        "💘" to "$BASE_URL/1f498/lottie.json",
        "💝" to "$BASE_URL/1f49d/lottie.json",

        // --- ANIMALS & NATURE ---
        "🐶" to "$BASE_URL/1f436/lottie.json",
        "🐱" to "$BASE_URL/1f431/lottie.json",
        "🐭" to "$BASE_URL/1f42d/lottie.json",
        "🐹" to "$BASE_URL/1f439/lottie.json",
        "🐰" to "$BASE_URL/1f430/lottie.json",
        "🦊" to "$BASE_URL/1f98a/lottie.json",
        "🐻" to "$BASE_URL/1f43b/lottie.json",
        "🐼" to "$BASE_URL/1f43c/lottie.json",
        "🐨" to "$BASE_URL/1f428/lottie.json",
        "🐯" to "$BASE_URL/1f42f/lottie.json",
        "🦁" to "$BASE_URL/1f981/lottie.json",
        "🐮" to "$BASE_URL/1f42e/lottie.json",
        "🐷" to "$BASE_URL/1f437/lottie.json",
        "🐽" to "$BASE_URL/1f43d/lottie.json",
        "🐸" to "$BASE_URL/1f438/lottie.json",
        "🐵" to "$BASE_URL/1f435/lottie.json",
        "🦄" to "$BASE_URL/1f984/lottie.json",
        "🦖" to "$BASE_URL/1f996/lottie.json",
        "🐳" to "$BASE_URL/1f433/lottie.json",
        "🐬" to "$BASE_URL/1f42c/lottie.json",
        "🦋" to "$BASE_URL/1f98b/lottie.json",
        "🐝" to "$BASE_URL/1f41d/lottie.json",

        // --- OTHERS ---
        "🔥" to "$BASE_URL/1f525/lottie.json",
        "🎉" to "$BASE_URL/1f389/lottie.json",
        "✨" to "$BASE_URL/2728/lottie.json",
        "🚀" to "$BASE_URL/1f680/lottie.json",
        "💯" to "$BASE_URL/1f4af/lottie.json",
        "⭐" to "$BASE_URL/2b50/lottie.json",
        "🌈" to "$BASE_URL/1f308/lottie.json",
        "🍕" to "$BASE_URL/1f355/lottie.json",
        "🍔" to "$BASE_URL/1f354/lottie.json",
        "🍟" to "$BASE_URL/1f35f/lottie.json",
        "🍦" to "$BASE_URL/1f366/lottie.json",
        "🍩" to "$BASE_URL/1f369/lottie.json",
        "🍪" to "$BASE_URL/1f36a/lottie.json",
        "🍰" to "$BASE_URL/1f370/lottie.json",
        "☕" to "$BASE_URL/2615/lottie.json",
        "🍺" to "$BASE_URL/1f37a/lottie.json",
        "⚽" to "$BASE_URL/26bd/lottie.json",
        "🎮" to "$BASE_URL/1f3ae/lottie.json",
        "☀️" to "$BASE_URL/2600_fe0f/lottie.json",
        "☁️" to "$BASE_URL/2601_fe0f/lottie.json"
    )

    fun getAnimUrl(emoji: String): String? {
        return emojiToAnimUrl[emoji]
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
        return cp in 0xFE00..0xFE0F || cp == 0x200D || cp in 0x1F3FB..0x1F3FF
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
    
    // Controlar a reprodução da animação
    val composition by rememberLottieComposition(if (url != null) LottieCompositionSpec.Url(url) else LottieCompositionSpec.JsonString(""))
    var isPlaying by remember { mutableStateOf(true) }
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = LottieConstants.IterateForever,
        isPlaying = isPlaying
    )

    if (url != null) {
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
                                // Efeito de bounce (pulo)
                                scale.animateTo(1.4f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                                scale.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy))
                            }
                        },
                        onLongPress = {
                            onLongClick()
                        }
                    )
                }
        )
    }
}
