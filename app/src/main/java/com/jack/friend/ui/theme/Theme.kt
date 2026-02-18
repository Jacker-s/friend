package com.jack.friend.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

@Immutable
data class ChatColors(
    val bubbleMe: Color,
    val bubbleOther: Color,
    val background: Color,
    val topBar: Color,
    val onTopBar: Color,
    val primary: Color,
    val secondaryBackground: Color,
    val tertiaryBackground: Color,
    val separator: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

/**
 * Mantemos LocalChatColors para NÃO quebrar o app.
 * Agora ele usa a paleta iOS (SwiftUI 3).
 */
val LocalChatColors = staticCompositionLocalOf {
    ChatColors(
        bubbleMe = iOSBubbleMe,
        bubbleOther = iOSBubbleOtherLight,
        background = iOSBackgroundLight,
        topBar = iOSSecondaryBackgroundLight,
        onTopBar = iOSLabelPrimaryLight,
        primary = iOSAccent,
        secondaryBackground = iOSSecondaryBackgroundLight,
        tertiaryBackground = iOSTertiaryBackgroundLight,
        separator = iOSSeparatorLight,
        textPrimary = iOSLabelPrimaryLight,
        textSecondary = iOSLabelSecondaryLight
    )
}

/**
 * Tipografia com “iOS/SwiftUI 3 feel”.
 * (Sem SF Pro oficial; usa Default com pesos/size parecidos.)
 */
val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Light,
        fontSize = 34.sp,
        letterSpacing = (-0.3).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        letterSpacing = (-0.2).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 17.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp
        // não setar cor fixa aqui, deixa a tela escolher (MaterialTheme)
    )
)

@Composable
fun FriendTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isDarkModeOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE) }
    var isDarkModePref by remember { mutableStateOf(uiPrefs.getBoolean("dark_mode", false)) }

    DisposableEffect(uiPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "dark_mode") {
                isDarkModePref = prefs.getBoolean("dark_mode", false)
            }
        }
        uiPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { uiPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    val actualDark = when {
        isDarkModeOverride != null -> isDarkModeOverride
        uiPrefs.contains("dark_mode") -> isDarkModePref
        else -> darkTheme
    }

    // ✅ iOS / SwiftUI 3 color scheme mapping (Material3)
    val colorScheme = if (actualDark) {
        darkColorScheme(
            primary = iOSBlue,
            secondary = iOSIndigo,
            tertiary = iOSTeal,

            background = iOSBackgroundDark,
            surface = iOSSecondaryBackgroundDark,
            surfaceVariant = iOSTertiaryBackgroundDark,

            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.Black,

            onBackground = iOSLabelPrimaryDark,
            onSurface = iOSLabelPrimaryDark,
            onSurfaceVariant = iOSLabelSecondaryDark,

            outline = iOSSeparatorDark
        )
    } else {
        lightColorScheme(
            primary = iOSBlue,
            secondary = iOSIndigo,
            tertiary = iOSTeal,

            background = iOSBackgroundLight,
            surface = iOSSecondaryBackgroundLight,
            surfaceVariant = iOSTertiaryBackgroundLight,

            onPrimary = Color.White,
            onSecondary = Color.White,
            onTertiary = Color.Black,

            onBackground = iOSLabelPrimaryLight,
            onSurface = iOSLabelPrimaryLight,
            onSurfaceVariant = iOSLabelSecondaryLight,

            outline = iOSSeparatorLight
        )
    }

    // ✅ Chat colors com paleta iOS (mantém API do app)
    val chatColors = if (actualDark) {
        ChatColors(
            bubbleMe = iOSBubbleMe,
            bubbleOther = iOSBubbleOtherDark,
            background = iOSBackgroundDark,
            topBar = iOSSecondaryBackgroundDark,
            onTopBar = iOSLabelPrimaryDark,
            primary = iOSAccent,
            secondaryBackground = iOSSecondaryBackgroundDark,
            tertiaryBackground = iOSTertiaryBackgroundDark,
            separator = iOSSeparatorDark,
            textPrimary = iOSLabelPrimaryDark,
            textSecondary = iOSLabelSecondaryDark
        )
    } else {
        ChatColors(
            bubbleMe = iOSBubbleMe,
            bubbleOther = iOSBubbleOtherLight,
            background = iOSBackgroundLight,
            topBar = iOSSecondaryBackgroundLight,
            onTopBar = iOSLabelPrimaryLight,
            primary = iOSAccent,
            secondaryBackground = iOSSecondaryBackgroundLight,
            tertiaryBackground = iOSTertiaryBackgroundLight,
            separator = iOSSeparatorLight,
            textPrimary = iOSLabelPrimaryLight,
            textSecondary = iOSLabelSecondaryLight
        )
    }

    CompositionLocalProvider(LocalChatColors provides chatColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
