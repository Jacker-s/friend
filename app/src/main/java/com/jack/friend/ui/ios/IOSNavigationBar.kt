package com.jack.friend.ui.ios

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.jack.friend.ui.theme.LocalChatColors

/**
 * Barra estilo iOS:
 * - Large title no topo
 * - Quando rola, vira título pequeno “inline”
 *
 * Você passa scrollY (px) ou um float “progress” de 0..1.
 */
@Composable
fun IOSNavigationStackTopBar(
    title: String,
    subtitle: String? = null,
    collapsedProgress: Float, // 0f = large, 1f = collapsed
    modifier: Modifier = Modifier,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    val cs = MaterialTheme.colorScheme
    val chat = LocalChatColors.current

    val p = collapsedProgress.coerceIn(0f, 1f)
    val largeAlpha by animateFloatAsState(
        targetValue = 1f - p,
        animationSpec = SwiftSpring.soft,
        label = "largeAlpha"
    )
    val inlineAlpha by animateFloatAsState(
        targetValue = p,
        animationSpec = SwiftSpring.soft,
        label = "inlineAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(chat.topBar)
            .padding(horizontal = 16.dp)
            .padding(top = 10.dp, bottom = 10.dp)
    ) {

        // inline (quando colapsa)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(28.dp)
                .alpha(inlineAlpha),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                color = chat.onTopBar,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.weight(1f))
            if (trailing != null) {
                Row(verticalAlignment = Alignment.CenterVertically, content = trailing)
            }
        }

        // large title
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .alpha(largeAlpha)
        ) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = title,
                color = chat.onTopBar,
                style = MaterialTheme.typography.displayLarge
            )
            if (!subtitle.isNullOrBlank()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = subtitle,
                    color = cs.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}
