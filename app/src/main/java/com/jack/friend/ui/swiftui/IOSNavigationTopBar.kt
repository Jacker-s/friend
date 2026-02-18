package com.jack.friend.ui.swiftui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Compat TopBar:
 * - Mantém parâmetros antigos usados no MainActivity
 * - Permite usar rightActions (ex: 3 pontinhos) E trailing (ex: settings) juntos
 */
@Composable
fun IOSNavigationTopBar(
    title: String,
    modifier: Modifier = Modifier,

    // ===== API ANTIGA =====
    showBack: Boolean = false,
    onBack: (() -> Unit)? = null,

    searchEnabled: Boolean = false,
    searchText: String = "",
    onSearchTextChange: ((String) -> Unit)? = null,
    onCancelSearch: (() -> Unit)? = null,

    // Antigo "rightActions": conteúdo custom na direita (ex: 3 pontinhos)
    rightActions: (@Composable RowScope.() -> Unit)? = null,

    // ===== API NOVA =====
    leading: IOSNavLeading? = null,
    trailing: IOSNavTrailing? = null,

    backgroundColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface
) {
    Surface(
        color = backgroundColor,
        contentColor = contentColor,
        modifier = modifier.fillMaxWidth()
    ) {
        CompositionLocalProvider(LocalContentColor provides contentColor) {

            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .fillMaxWidth()
            ) {

                // Barra principal
                Box(
                    modifier = Modifier
                        .height(52.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp)
                ) {

                    // LEFT: back ou leading
                    Row(
                        modifier = Modifier.align(Alignment.CenterStart),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        when {
                            showBack && onBack != null -> {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .clickable { onBack() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Voltar"
                                    )
                                }
                            }
                            leading != null -> leading.Render()
                        }
                    }

                    // CENTER: title
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.align(Alignment.Center)
                    )

                    // RIGHT: rightActions + trailing (OS DOIS!)
                    Row(
                        modifier = Modifier.align(Alignment.CenterEnd),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 1) Mantém os 3 pontinhos / menu (se você tiver)
                        if (rightActions != null) {
                            rightActions.invoke(this)
                            Spacer(Modifier.width(6.dp))
                        }

                        // 2) Adiciona trailing (Settings etc)
                        if (trailing != null) {
                            trailing.Render()
                        }
                    }
                }

                // Linha de busca (quando habilitado)
                AnimatedVisibility(visible = searchEnabled) {
                    SearchRow(
                        text = searchText,
                        onTextChange = onSearchTextChange ?: {},
                        onCancel = onCancelSearch ?: {}
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchRow(
    text: String,
    onTextChange: (String) -> Unit,
    onCancel: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .weight(1f)
                .height(40.dp)
                .clip(RoundedCornerShape(20.dp))
                .padding(horizontal = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            if (text.isBlank()) {
                Text(
                    text = "Buscar…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .size(38.dp)
                .clip(CircleShape)
                .clickable { onCancel() },
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Rounded.Close, contentDescription = "Cancelar")
        }
    }
}

/** Leading slot */
sealed interface IOSNavLeading {
    @Composable fun Render()

    data class TextButton(
        val text: String,
        val onClick: () -> Unit
    ) : IOSNavLeading {
        @Composable
        override fun Render() {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyLarge,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { onClick() }
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

/** Trailing slot */
sealed interface IOSNavTrailing {
    @Composable fun Render()

    data class IconsRow(
        val items: List<Item>,
        val spacing: Dp = 6.dp
    ) : IOSNavTrailing {

        data class Item(
            val icon: @Composable () -> Unit,
            val onClick: () -> Unit
        )

        @Composable
        override fun Render() {
            Row(
                horizontalArrangement = Arrangement.spacedBy(spacing),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { item ->
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .clickable { item.onClick() },
                        contentAlignment = Alignment.Center
                    ) {
                        item.icon()
                    }
                }
            }
        }
    }
}
