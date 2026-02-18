package com.jack.friend.ui.chat

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import com.jack.friend.ui.ios.SwiftSpring
import com.jack.friend.ui.theme.LocalChatColors

/**
 * Campo iMessage-like:
 * - pill input
 * - botão send circular com spring
 * - callbacks prontos
 */
@Composable
fun MessageComposerIOS(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onAttach: () -> Unit,
    onRecord: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val cs = MaterialTheme.colorScheme
    val chat = LocalChatColors.current

    val hasText = text.trim().isNotEmpty()

    var pressed by remember { mutableStateOf(false) }
    val sendScale by animateFloatAsState(
        targetValue = if (pressed) 0.92f else 1f,
        animationSpec = SwiftSpring.bouncy,
        label = "sendScale"
    )

    Surface(
        modifier = modifier
            .fillMaxWidth(),
        color = cs.background,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // + anexar
            IconButton(
                onClick = onAttach,
                enabled = enabled
            ) {
                Text("＋", color = cs.onSurface, style = MaterialTheme.typography.titleLarge)
            }

            // input pill
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 44.dp),
                shape = RoundedCornerShape(18.dp),
                color = cs.surfaceVariant,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp
            ) {
                TextField(
                    value = text,
                    onValueChange = onTextChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    placeholder = { Text("Mensagem", color = cs.onSurfaceVariant) },
                    singleLine = false,
                    maxLines = 5,
                    enabled = enabled,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        disabledContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = chat.primary,
                        focusedTextColor = cs.onSurface,
                        unfocusedTextColor = cs.onSurface
                    )
                )
            }

            Spacer(Modifier.width(10.dp))

            // botão send / mic
            val bg = if (hasText) chat.primary else cs.surfaceVariant
            val fg = if (hasText) Color.White else cs.onSurface

            Surface(
                modifier = Modifier
                    .size(44.dp)
                    .scale(sendScale)
                    .clip(CircleShape),
                color = bg,
                tonalElevation = 0.dp,
                shadowElevation = 0.dp,
                onClick = {
                    pressed = true
                    if (hasText) onSend() else onRecord()
                    pressed = false
                },
                enabled = enabled
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = if (hasText) "↑" else "🎤",
                        color = fg,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            }
        }
    }
}
