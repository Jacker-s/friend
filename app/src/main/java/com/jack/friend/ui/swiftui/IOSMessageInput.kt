package com.jack.friend.ui.swiftui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jack.friend.ui.theme.LocalChatColors
import com.jack.friend.ui.theme.MessengerBlue
import com.jack.friend.ui.theme.MetaGray4

/**
 * iMessage-like composer:
 * - pill input
 * - plus / emoji left
 * - send on right (when text)
 * - mic when empty
 */
@Composable
fun IOSMessageInput(
    text: String,
    onTextChange: (String) -> Unit,
    onPlus: () -> Unit,
    onEmoji: () -> Unit,
    onSend: () -> Unit,
    onMicHoldStart: (() -> Unit)? = null,
    onMicHoldStop: ((cancel: Boolean) -> Unit)? = null
) {
    val colors = LocalChatColors.current
    val hasText = text.isNotBlank()

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = colors.topBar
    ) {
        Row(
            modifier = Modifier
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            IconButton(onClick = onPlus) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = MessengerBlue)
            }

            IconButton(onClick = onEmoji) {
                Icon(Icons.Rounded.EmojiEmotions, contentDescription = null, tint = MessengerBlue)
            }

            val minHeight = 40.dp
            val maxHeight = 130.dp
            val targetHeight = if (hasText) 48.dp else 40.dp

            // ✅ Aqui estava o erro: AnimationSpec<Dp> != SpringSpec<Float>
            // ✅ Agora usamos o spring genérico para Dp
            val pillHeight by animateDpAsState(
                targetValue = targetHeight,
                animationSpec = SwiftUISpring.smooth<Dp>(),
                label = "pillHeight"
            )

            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = minHeight, max = maxHeight)
                    .heightIn(min = pillHeight) // opcional, deixa o “pill” responder à animação
                    .clip(RoundedCornerShape(22.dp))
                    .background(colors.tertiaryBackground)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                if (text.isEmpty()) {
                    Text(
                        text = "Mensagem…",
                        color = MetaGray4,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }

                BasicTextField(
                    value = text,
                    onValueChange = onTextChange,
                    textStyle = MaterialTheme.typography.bodyLarge.copy(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Normal
                    ),
                    cursorBrush = SolidColor(MessengerBlue),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.width(8.dp))

            AnimatedContent(
                targetState = hasText,
                transitionSpec = {
                    (fadeInFast() togetherWith fadeOutFast())
                        .using(SizeTransform(clip = false))
                },
                label = "sendOrMic"
            ) { showSend ->
                if (showSend) {
                    IconButton(onClick = onSend) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(MessengerBlue, CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.AutoMirrored.Filled.Send,
                                contentDescription = null,
                                tint = Color.White
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = {
                            // clique simples; segurar/arrastar dá pra colocar depois
                        }
                    ) {
                        Icon(Icons.Rounded.Mic, contentDescription = null, tint = MessengerBlue)
                    }
                }
            }
        }
    }
}

private fun fadeInFast() = androidx.compose.animation.fadeIn(animationSpec = SwiftUISpring.Smooth)
private fun fadeOutFast() = androidx.compose.animation.fadeOut(animationSpec = SwiftUISpring.Smooth)
