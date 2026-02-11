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
        bubbleOther = MessengerBubbleOther,
        background = Color.White,
        topBar = Color.White,
        onTopBar = Color.Black,
        primary = MessengerBlue,
        secondaryBackground = MetaGray1,
        tertiaryBackground = MetaGray2,
        separator = MetaGray3,
        textPrimary = Color.Black,
        textSecondary = MetaGray4
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
        fontSize = 24.sp,
        letterSpacing = (-0.3).sp
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp
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
        color = MetaGray4
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
            background = MetaBlack,
            surface = MetaDarkGray,
            onSurface = Color.White,
            onSurfaceVariant = MetaGray4,
            outline = MetaDarkSurface
        )
    } else {
        lightColorScheme(
            primary = MessengerBlue,
            background = Color.White,
            surface = Color.White,
            onSurface = Color.Black,
            onSurfaceVariant = MetaGray4,
            outline = MetaGray3
        )
    }

    val chatColors = ChatColors(
        bubbleMe = MessengerBlue,
        bubbleOther = if (actualDark) MessengerBubbleOtherDark else MessengerBubbleOther,
        background = if (actualDark) MetaBlack else Color.White,
        topBar = if (actualDark) MetaBlack else Color.White,
        onTopBar = if (actualDark) Color.White else Color.Black,
        primary = MessengerBlue,
        secondaryBackground = if (actualDark) MetaDarkSurface else MetaGray1,
        tertiaryBackground = if (actualDark) MetaDarkGray else MetaGray2,
        separator = if (actualDark) MetaDarkSurface else MetaGray3,
        textPrimary = if (actualDark) Color.White else Color.Black,
        textSecondary = MetaGray4
    )

    CompositionLocalProvider(LocalChatColors provides chatColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AppTypography,
            content = content
        )
    }
}
