package com.jack.friend.ui.theme

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.*
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

val LocalChatColors = staticCompositionLocalOf {
    ChatColors(
        bubbleMe = MessengerBlue,
        bubbleOther = MessengerBubbleOtherLight,
        background = MessengerWhite,
        topBar = MessengerWhite,
        onTopBar = MessengerTextPrimaryLight,
        primary = MessengerBlue,
        secondaryBackground = MessengerGrayLight,
        tertiaryBackground = MessengerWhite,
        separator = MessengerSeparatorLight,
        textPrimary = MessengerTextPrimaryLight,
        textSecondary = MessengerTextSecondary
    )
}

val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 30.sp,
        letterSpacing = (-0.5).sp
    ),
    titleLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
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
        fontSize = 12.sp,
        color = MessengerTextSecondary
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
        onDispose {
            uiPrefs.unregisterOnSharedPreferenceChangeListener(listener)
        }
    }

    val actualDark = when {
        isDarkModeOverride != null -> isDarkModeOverride
        uiPrefs.contains("dark_mode") -> isDarkModePref
        else -> darkTheme
    }

    val colorScheme = if (actualDark) {
        darkColorScheme(
            primary = MessengerBlue,
            background = MessengerBlack,
            surface = MessengerGrayDark,
            onSurface = MessengerTextPrimaryDark,
            onSurfaceVariant = MessengerTextSecondary,
            outline = MessengerSeparatorDark
        )
    } else {
        lightColorScheme(
            primary = MessengerBlue,
            secondary = MessengerBlueDark,
            background = MessengerWhite,
            surface = MessengerWhite,
            onSurface = MessengerTextPrimaryLight,
            onSurfaceVariant = MessengerTextSecondary,
            outline = MessengerSeparatorLight
        )
    }

    val chatColors = ChatColors(
        bubbleMe = MessengerBlue,
        bubbleOther = if (actualDark) MessengerBubbleOtherDark else MessengerBubbleOtherLight,
        background = if (actualDark) MessengerBlack else MessengerWhite,
        topBar = if (actualDark) MessengerGrayDark else MessengerWhite,
        onTopBar = if (actualDark) MessengerTextPrimaryDark else MessengerTextPrimaryLight,
        primary = MessengerBlue,
        secondaryBackground = if (actualDark) MessengerGrayDark else MessengerGrayLight,
        tertiaryBackground = if (actualDark) Color(0xFF242526) else Color(0xFFF0F2F5),
        separator = if (actualDark) MessengerSeparatorDark else MessengerSeparatorLight,
        textPrimary = if (actualDark) MessengerTextPrimaryDark else MessengerTextPrimaryLight,
        textSecondary = MessengerTextSecondary
    )

    CompositionLocalProvider(LocalChatColors provides chatColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
