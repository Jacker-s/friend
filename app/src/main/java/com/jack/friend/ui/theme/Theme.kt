package com.jack.friend.ui.theme

import android.content.Context
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

@Immutable
data class ChatColors(
    val bubbleMe: Color,
    val bubbleOther: Color,
    val background: Color,
    val topBar: Color,
    val onTopBar: Color,
    val primary: Color,
    val fab: Color
)

val LocalChatColors = staticCompositionLocalOf {
    ChatColors(
        bubbleMe = WhatsMessageMe,
        bubbleOther = WhatsMessageOther,
        background = WhatsChatBackground,
        topBar = WhatsTeal,
        onTopBar = Color.White,
        primary = WhatsTeal,
        fab = WhatsGreen
    )
}

private val DarkColorScheme = darkColorScheme(
    primary = Purple80,
    secondary = PurpleGrey80,
    tertiary = Pink80
)

private val LightColorScheme = lightColorScheme(
    primary = Purple40,
    secondary = PurpleGrey40,
    tertiary = Pink40
)

private val SwiftUIColorScheme = lightColorScheme(
    primary = AppleBlue,
    background = AppleBackground,
    surface = AppleSystemBackground,
    onPrimary = Color.White,
    onBackground = Color.Black,
    onSurface = Color.Black
)

@Composable
fun FriendTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val themePrefs = remember { context.getSharedPreferences("theme_prefs", Context.MODE_PRIVATE) }
    val appTheme = themePrefs.getString("app_theme", "Material Design")

    val colorScheme = when (appTheme) {
        "SwiftUI" -> SwiftUIColorScheme
        else -> {
            when {
                dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                    if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
                }
                darkTheme -> DarkColorScheme
                else -> LightColorScheme
            }
        }
    }

    val chatColors = when (appTheme) {
        "SwiftUI" -> ChatColors(
            bubbleMe = AppleBlue,
            bubbleOther = Color(0xFFE9E9EB),
            background = Color.White,
            topBar = Color(0xFFF9F9F9),
            onTopBar = Color.Black,
            primary = AppleBlue,
            fab = AppleBlue
        )
        else -> ChatColors(
            bubbleMe = WhatsMessageMe,
            bubbleOther = WhatsMessageOther,
            background = WhatsChatBackground,
            topBar = WhatsTeal,
            onTopBar = Color.White,
            primary = WhatsTeal,
            fab = WhatsGreen
        )
    }

    CompositionLocalProvider(LocalChatColors provides chatColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}