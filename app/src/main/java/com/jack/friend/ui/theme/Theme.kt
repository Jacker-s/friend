package com.jack.friend.ui.theme

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat

enum class AppTheme(val title: String) {
    DEFAULT("Padrão iOS"),
    WARM("Cálido"),
    TELEGRAM("Telegram"),
    OCEAN("Oceano"),
    LAVENDER("Lavanda"),
    MIDNIGHT("Midnight (OLED)"),
    FOREST("Floresta"),
    SAKURA("Sakura"),
    COFFEE("Café"),
    CYBERPUNK("Cyberpunk"),
    DRACULA("Dracula"),
    NORD("Nord")
}

@Immutable
data class ChatColors(
    val bubbleMe: Color,
    val bubbleOther: Color,
    val background: Color,
    val topBar: Color,
    val onTopBar: Color,
    val primary: Color,
    val primaryBackground: Color,
    val secondaryBackground: Color,
    val tertiaryBackground: Color,
    val separator: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val titlePrimary: Color,
    val waveColors: List<Color>
)

val LocalChatColors = staticCompositionLocalOf {
    ChatColors(
        bubbleMe = iOSBubbleMe,
        bubbleOther = iOSBubbleOtherLight,
        background = iOSBackgroundLight,
        topBar = iOSSecondaryBackgroundLight,
        onTopBar = iOSLabelPrimaryLight,
        primary = iOSAccent,
        primaryBackground = iOSBackgroundLight,
        secondaryBackground = iOSSecondaryBackgroundLight,
        tertiaryBackground = iOSTertiaryBackgroundLight,
        separator = iOSSeparatorLight,
        textPrimary = iOSLabelPrimaryLight,
        textSecondary = iOSLabelSecondaryLight,
        titlePrimary = iOSLabelPrimaryLight,
        waveColors = listOf(WaveBlue, WavePurple, WaveDeepPurple)
    )
}

@Composable
fun getAppTypography(): Typography {
    val chatColors = LocalChatColors.current
    return Typography(
        displayLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Light,
            fontSize = 34.sp,
            letterSpacing = (-0.3).sp,
            color = chatColors.titlePrimary
        ),
        titleLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.SemiBold,
            fontSize = 22.sp,
            letterSpacing = (-0.2).sp,
            color = chatColors.titlePrimary
        ),
        titleMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 17.sp,
            color = chatColors.titlePrimary
        ),
        bodyLarge = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 16.sp,
            color = chatColors.textPrimary
        ),
        bodyMedium = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Normal,
            fontSize = 14.sp,
            color = chatColors.textSecondary
        ),
        labelSmall = TextStyle(
            fontFamily = FontFamily.Default,
            fontWeight = FontWeight.Medium,
            fontSize = 12.sp,
            color = chatColors.textSecondary
        )
    )
}

@Composable
fun FriendTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    isDarkModeOverride: Boolean? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("ui_prefs", Context.MODE_PRIVATE) }
    
    // Estados reativos baseados nas preferências
    var isDarkModePref by remember { mutableStateOf(uiPrefs.getBoolean("dark_mode", false)) }
    var followSystemPref by remember { mutableStateOf(uiPrefs.getBoolean("follow_system", true)) }
    var selectedThemeName by remember { mutableStateOf(uiPrefs.getString("app_theme", AppTheme.DEFAULT.name) ?: AppTheme.DEFAULT.name) }
    
    // Listener para mudanças nas SharedPreferences em tempo real
    DisposableEffect(uiPrefs) {
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            when (key) {
                "dark_mode" -> isDarkModePref = prefs.getBoolean("dark_mode", false)
                "follow_system" -> followSystemPref = prefs.getBoolean("follow_system", true)
                "app_theme" -> selectedThemeName = prefs.getString("app_theme", AppTheme.DEFAULT.name) ?: AppTheme.DEFAULT.name
            }
        }
        uiPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { uiPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    // Lógica consolidada para decidir o tema escuro
    val actualDark = when {
        isDarkModeOverride != null -> isDarkModeOverride
        followSystemPref -> darkTheme
        else -> isDarkModePref
    }

    val selectedTheme = try { AppTheme.valueOf(selectedThemeName) } catch (e: Exception) { AppTheme.DEFAULT }
    val chatColors = getThemeColors(selectedTheme, actualDark)

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = chatColors.topBar.toArgb()
            window.navigationBarColor = chatColors.background.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !actualDark
                isAppearanceLightNavigationBars = !actualDark
            }
        }
    }

    // Atualização do ColorScheme do Material3 com base no ChatColors selecionado
    val colorScheme = if (actualDark) {
        darkColorScheme(
            primary = chatColors.primary,
            onPrimary = Color.White,
            primaryContainer = chatColors.bubbleMe,
            onPrimaryContainer = Color.White,
            secondary = chatColors.bubbleMe,
            tertiary = chatColors.bubbleOther,
            background = chatColors.background,
            surface = chatColors.secondaryBackground,
            onBackground = chatColors.textPrimary,
            onSurface = chatColors.textPrimary,
            outline = chatColors.separator,
            surfaceVariant = chatColors.tertiaryBackground
        )
    } else {
        lightColorScheme(
            primary = chatColors.primary,
            onPrimary = Color.White,
            primaryContainer = chatColors.bubbleMe,
            onPrimaryContainer = Color.White,
            secondary = chatColors.bubbleMe,
            tertiary = chatColors.bubbleOther,
            background = chatColors.background,
            surface = chatColors.secondaryBackground,
            onBackground = chatColors.textPrimary,
            onSurface = chatColors.textPrimary,
            outline = chatColors.separator,
            surfaceVariant = chatColors.tertiaryBackground
        )
    }

    CompositionLocalProvider(LocalChatColors provides chatColors) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = getAppTypography(),
            shapes = Shapes,
            content = content
        )
    }
}

fun getThemeColors(theme: AppTheme, isDark: Boolean): ChatColors {
    return when (theme) {
        AppTheme.DEFAULT -> if (isDark) {
            ChatColors(iOSBubbleMe, iOSBubbleOtherDark, iOSBackgroundDark, iOSSecondaryBackgroundDark, iOSLabelPrimaryDark, iOSBlue, iOSBackgroundDark, iOSSecondaryBackgroundDark, iOSTertiaryBackgroundDark, iOSSeparatorDark, iOSLabelPrimaryDark, iOSLabelSecondaryDark, Color.White, listOf(WaveBlue, WavePurple, WaveDeepPurple))
        } else {
            ChatColors(iOSBubbleMe, iOSBubbleOtherLight, iOSBackgroundLight, iOSSecondaryBackgroundLight, iOSLabelPrimaryLight, iOSBlue, iOSBackgroundLight, iOSSecondaryBackgroundLight, iOSTertiaryBackgroundLight, iOSSeparatorLight, iOSLabelPrimaryLight, iOSLabelSecondaryLight, Color.Black, listOf(WaveBlue, WavePurple, WaveDeepPurple))
        }
        AppTheme.WARM -> if (isDark) {
            ChatColors(WarmPrimary, WarmSurfaceDark, WarmBackgroundDark, WarmSurfaceDark, WarmOnBackgroundDark, WarmPrimary, WarmBackgroundDark, WarmSurfaceDark, WarmBackgroundDark, Color.DarkGray, WarmOnBackgroundDark, WarmTextSecondary, WarmPrimary, listOf(Color(0xFFFF9800), Color(0xFFFF5722), Color(0xFFE65100)))
        } else {
            ChatColors(WarmPrimary, WarmSurfaceLight, WarmBackgroundLight, WarmSurfaceLight, WarmOnBackgroundLight, WarmPrimary, WarmBackgroundLight, WarmSurfaceLight, WarmBackgroundLight, Color.LightGray, WarmOnBackgroundLight, WarmTextSecondary, WarmPrimary, listOf(Color(0xFFFFCC80), Color(0xFFFFAB91), Color(0xFFFF8A65)))
        }
        AppTheme.TELEGRAM -> if (isDark) {
            ChatColors(TelegramBlue, Color(0xFF212D3B), Color(0xFF17212B), Color(0xFF242F3D), Color.White, TelegramBlue, Color(0xFF17212B), Color(0xFF242F3D), Color(0xFF17212B), Color.Black, Color.White, Color.Gray, TelegramBlue, listOf(Color(0xFF24A1DE), Color(0xFF1E88BE), Color(0xFF1565C0)))
        } else {
            ChatColors(TelegramBlue, Color.White, Color(0xFFDEE4E8), Color.White, Color.Black, TelegramBlue, Color(0xFFDEE4E8), Color.White, Color(0xFFDEE4E8), Color.LightGray, Color.Black, Color.Gray, TelegramBlue, listOf(Color(0xFF54A9EB), Color(0xFF24A1DE), Color(0xFF1E88BE)))
        }
        AppTheme.OCEAN -> if (isDark) {
            ChatColors(Color(0xFF006064), Color(0xFF004D40), Color(0xFF002424), Color(0xFF004D40), Color.White, Color(0xFF00BCD4), Color(0xFF002424), Color(0xFF004D40), Color(0xFF002424), Color.DarkGray, Color.White, Color.Cyan, Color(0xFF00838F), listOf(Color(0xFF00ACC1), Color(0xFF00838F), Color(0xFF006064)))
        } else {
            ChatColors(Color(0xFF00BCD4), Color(0xFFE0F7FA), Color(0xFFB2EBF2), Color(0xFF00BCD4), Color.White, Color(0xFF00838F), Color(0xFFB2EBF2), Color.White, Color(0xFFE0F7FA), Color.Cyan, Color.Black, Color(0xFF006064), Color(0xFF006064), listOf(Color(0xFF80DEEA), Color(0xFF4DD0E1), Color(0xFF26C6DA)))
        }
        AppTheme.LAVENDER -> if (isDark) {
            ChatColors(Color(0xFF7E57C2), Color(0xFF4527A0), Color(0xFF311B92), Color(0xFF4527A0), Color.White, Color(0xFF9575CD), Color(0xFF311B92), Color(0xFF4527A0), Color(0xFF311B92), Color.Black, Color.White, Color(0xFFB39DDB), Color(0xFF7E57C2), listOf(Color(0xFF9575CD), Color(0xFF7E57C2), Color(0xFF673AB7)))
        } else {
            ChatColors(Color(0xFF9575CD), Color(0xFFF3E5F5), Color(0xFFEDE7F6), Color(0xFF9575CD), Color.White, Color(0xFF673AB7), Color(0xFFEDE7F6), Color.White, Color(0xFFF3E5F5), Color(0xFFD1C4E9), Color.Black, Color(0xFF512DA8), Color(0xFF512DA8), listOf(Color(0xFFD1C4E9), Color(0xFFB39DDB), Color(0xFF9575CD)))
        }
        AppTheme.MIDNIGHT -> if (isDark) {
            ChatColors(MidnightBubbleMe, MidnightBubbleOther, MidnightBackground, MidnightSecondary, Color.White, MidnightAccent, MidnightBackground, MidnightSecondary, MidnightTertiary, Color.DarkGray, Color.White, Color.LightGray, MidnightAccent, listOf(Color(0xFF3700B3), Color(0xFF6200EE), Color(0xFFBB86FC)))
        } else {
            ChatColors(Color.Black, Color(0xFFE0E0E0), Color.White, Color.White, Color.Black, Color.Black, Color.White, Color.White, Color(0xFFF5F5F5), Color.LightGray, Color.Black, Color.Gray, Color.Black, listOf(Color.Black, Color.DarkGray, Color.Gray))
        }
        AppTheme.FOREST -> if (isDark) {
            ChatColors(ForestGreenPrimary, ForestGreenDark, ForestBackgroundDark, ForestGreenDark, Color.White, ForestAccent, ForestBackgroundDark, ForestGreenDark, ForestBackgroundDark, Color(0xFF1B3017), Color.White, Color(0xFFAED581), ForestAccent, listOf(Color(0xFF2D5A27), Color(0xFF388E3C), Color(0xFF4CAF50)))
        } else {
            ChatColors(ForestGreenPrimary, ForestGreenLight, ForestBackgroundLight, ForestGreenPrimary, Color.White, ForestGreenPrimary, ForestBackgroundLight, Color.White, ForestGreenLight, Color(0xFFC8E6C9), Color.Black, Color(0xFF2E7D32), Color(0xFF1B5E20), listOf(Color(0xFF81C784), Color(0xFF66BB6A), Color(0xFF4CAF50)))
        }
        AppTheme.SAKURA -> if (isDark) {
            ChatColors(SakuraPinkPrimary, SakuraBackgroundDark, SakuraBackgroundDark, SakuraBackgroundDark, Color.White, SakuraAccent, SakuraBackgroundDark, SakuraBackgroundDark, Color(0xFF3D2B2E), Color(0xFF4D3B3E), Color.White, Color(0xFFF48FB1), SakuraAccent, listOf(Color(0xFFF06292), Color(0xFFEC407A), Color(0xFFE91E63)))
        } else {
            ChatColors(SakuraPinkPrimary, SakuraPinkLight, SakuraBackgroundLight, SakuraPinkPrimary, Color.White, SakuraPinkPrimary, SakuraBackgroundLight, Color.White, SakuraPinkLight, Color(0xFFF8BBD0), Color.Black, Color(0xFFC2185B), Color(0xFFAD1457), listOf(Color(0xFFF48FB1), Color(0xFFF06292), Color(0xFFEC407A)))
        }
        AppTheme.COFFEE -> if (isDark) {
            ChatColors(CoffeePrimary, CoffeeSurfaceDark, CoffeeBackgroundDark, CoffeeSurfaceDark, Color.White, CoffeeSecondary, CoffeeBackgroundDark, CoffeeSurfaceDark, CoffeeBackgroundDark, Color(0xFF3E2C1C), Color.White, Color(0xFFA67B5B), CoffeeSecondary, listOf(Color(0xFF6F4E37), Color(0xFF5D4037), Color(0xFF4E342E)))
        } else {
            ChatColors(CoffeePrimary, CoffeeSurfaceLight, CoffeeBackgroundLight, CoffeePrimary, Color.White, CoffeePrimary, CoffeeBackgroundLight, Color.White, CoffeeSurfaceLight, Color(0xFFD7CCC8), Color.Black, Color(0xFF5D4037), Color(0xFF3E2C1C), listOf(Color(0xFFA67B5B), Color(0xFF8D6E63), Color(0xFF795548)))
        }
        AppTheme.CYBERPUNK -> if (isDark) {
            ChatColors(CyberpunkPink, CyberpunkBlue, CyberpunkBlack, CyberpunkDarkBlue, CyberpunkCyan, CyberpunkCyan, CyberpunkBlack, CyberpunkDarkBlue, CyberpunkBlack, CyberpunkPink.copy(alpha = 0.3f), Color.White, CyberpunkCyan, CyberpunkPink, listOf(CyberpunkPink, CyberpunkCyan, CyberpunkYellow))
        } else {
            ChatColors(CyberpunkPink, Color(0xFFF2F2F2), Color.White, Color.White, CyberpunkBlack, CyberpunkPink, Color.White, Color.White, Color(0xFFF5F5F5), Color.LightGray, CyberpunkBlack, Color.Gray, CyberpunkPink, listOf(CyberpunkPink, CyberpunkCyan, CyberpunkYellow))
        }
        AppTheme.DRACULA -> if (isDark) {
            ChatColors(DraculaPurple, DraculaCurrentLine, DraculaBackground, DraculaCurrentLine, DraculaForeground, DraculaPurple, DraculaBackground, DraculaCurrentLine, DraculaBackground, Color.Black, DraculaForeground, DraculaComment, DraculaPink, listOf(DraculaPurple, DraculaPink, DraculaCyan))
        } else {
            ChatColors(DraculaPurple, Color(0xFFF2F2F2), Color.White, Color.White, Color.Black, DraculaPurple, Color.White, Color.White, Color(0xFFF5F5F5), Color.LightGray, Color.Black, Color.Gray, DraculaPurple, listOf(DraculaPurple, DraculaPink, DraculaCyan))
        }
        AppTheme.NORD -> if (isDark) {
            ChatColors(Nord10, Nord2, Nord0, Nord1, Nord4, Nord8, Nord0, Nord1, Nord0, Nord3, Nord6, Nord4, Nord9, listOf(Nord7, Nord8, Nord9))
        } else {
            ChatColors(Nord10, Nord5, Nord6, Color.White, Nord0, Nord10, Nord6, Color.White, Nord5, Nord4, Nord0, Nord3, Nord10, listOf(Nord7, Nord8, Nord9))
        }
    }
}
