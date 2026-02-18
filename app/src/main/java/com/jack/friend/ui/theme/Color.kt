package com.jack.friend.ui.theme

import androidx.compose.ui.graphics.Color

// --- SwiftUI / iOS 15+ System Palette ---
val iOSBlue = Color(0xFF007AFF)
val iOSGreen = Color(0xFF34C759)
val iOSIndigo = Color(0xFF5856D6)
val iOSOrange = Color(0xFFFF9500)
val iOSPink = Color(0xFFFF2D55)
val iOSPurple = Color(0xFFAF52DE)
val iOSRed = Color(0xFFFF3B30)
val iOSTeal = Color(0xFF5AC8FA)
val iOSYellow = Color(0xFFFFCC00)
val iOSGray = Color(0xFF8E8E93)

// Light Mode System Colors
val iOSBackgroundLight = Color(0xFFF2F2F7)
val iOSSecondaryBackgroundLight = Color(0xFFFFFFFF)
val iOSTertiaryBackgroundLight = Color(0xFFE5E5EA)
val iOSLabelPrimaryLight = Color(0xFF000000)
val iOSLabelSecondaryLight = Color(0xFF3C3C43).copy(alpha = 0.6f)
val iOSSeparatorLight = Color(0xFFC6C6C8)

// Dark Mode System Colors
val iOSBackgroundDark = Color(0xFF000000)
val iOSSecondaryBackgroundDark = Color(0xFF1C1C1E)
val iOSTertiaryBackgroundDark = Color(0xFF2C2C2E)
val iOSLabelPrimaryDark = Color(0xFFFFFFFF)
val iOSLabelSecondaryDark = Color(0xFFEBEBF5).copy(alpha = 0.6f)
val iOSSeparatorDark = Color(0xFF38383A)

// Chat Specific Bubbles
val iOSBubbleMe = iOSBlue
val iOSBubbleOtherLight = Color(0xFFE9E9EB)
val iOSBubbleOtherDark = Color(0xFF262629)

// --- SwiftUI Semantic System Colors (iOS 15+) ---
// Fill Colors
val iOSFillPrimaryLight = Color(0x33000000)      // 20% black
val iOSFillSecondaryLight = Color(0x28000000)    // 16% black
val iOSFillTertiaryLight = Color(0x1E000000)     // 12% black
val iOSFillQuaternaryLight = Color(0x14000000)   // 8% black

val iOSFillPrimaryDark = Color(0x33FFFFFF)       // 20% white
val iOSFillSecondaryDark = Color(0x28FFFFFF)     // 16% white
val iOSFillTertiaryDark = Color(0x1EFFFFFF)      // 12% white
val iOSFillQuaternaryDark = Color(0x14FFFFFF)    // 8% white

// Grouped Backgrounds (List style)
val iOSGroupedBackgroundLight = iOSBackgroundLight
val iOSSecondaryGroupedBackgroundLight = iOSSecondaryBackgroundLight
val iOSTertiaryGroupedBackgroundLight = iOSTertiaryBackgroundLight

val iOSGroupedBackgroundDark = iOSBackgroundDark
val iOSSecondaryGroupedBackgroundDark = iOSSecondaryBackgroundDark
val iOSTertiaryGroupedBackgroundDark = iOSTertiaryBackgroundDark

// Additional Labels (SwiftUI semantic text colors)
val iOSLabelTertiaryLight = Color(0xFF3C3C43).copy(alpha = 0.3f)
val iOSLabelQuaternaryLight = Color(0xFF3C3C43).copy(alpha = 0.18f)

val iOSLabelTertiaryDark = Color(0xFFEBEBF5).copy(alpha = 0.3f)
val iOSLabelQuaternaryDark = Color(0xFFEBEBF5).copy(alpha = 0.18f)

// Link & Accent
val iOSLink = iOSBlue
val iOSAccent = iOSBlue

// System Gray Scale (Light)
val iOSSystemGray2 = Color(0xFFAEAEB2)
val iOSSystemGray3 = Color(0xFFC7C7CC)
val iOSSystemGray4 = Color(0xFFD1D1D6)
val iOSSystemGray5 = Color(0xFFE5E5EA)
val iOSSystemGray6 = Color(0xFFF2F2F7)

// System Gray Scale (Dark)
val iOSSystemGray2Dark = Color(0xFF636366)
val iOSSystemGray3Dark = Color(0xFF48484A)
val iOSSystemGray4Dark = Color(0xFF3A3A3C)
val iOSSystemGray5Dark = Color(0xFF2C2C2E)
val iOSSystemGray6Dark = Color(0xFF1C1C1E)

// --- Telegram Colors ---
val TelegramBlue = Color(0xFF24A1DE)
val TelegramBlueDark = Color(0xFF1E88BE)
val TelegramLightBlue = Color(0xFF54A9EB)

// --- Warm Theme Colors ---
val WarmPrimary = Color(0xFFE65100) // Deep Orange
val WarmSecondary = Color(0xFFFFB300) // Amber
val WarmBackgroundLight = Color(0xFFFFFBF0)
val WarmSurfaceLight = Color(0xFFFFF9F2)
val WarmBackgroundDark = Color(0xFF1C1B17)
val WarmSurfaceDark = Color(0xFF2C2B26)
val WarmOnBackgroundLight = Color(0xFF3E2723)
val WarmOnBackgroundDark = Color(0xFFEFEBE9)
val WarmTextSecondary = Color(0xFF795548) // Warm Brown

// --- Legacy Compatibility ---
val MessengerBlue = iOSBlue
val MessengerBlueDark = Color(0xFF0062CC)
val MessengerWhite = Color(0xFFFFFFFF)
val MessengerBlack = Color(0xFF000000)
val MessengerGrayLight = iOSBackgroundLight
val MessengerGrayDark = iOSBackgroundDark
val MessengerTextPrimaryLight = iOSLabelPrimaryLight
val MessengerTextPrimaryDark = iOSLabelPrimaryDark
val MessengerTextSecondary = iOSGray
val MessengerOnline = iOSGreen
val MessengerBusy = iOSRed
val MessengerAway = iOSOrange
val MessengerSeparatorLight = iOSSeparatorLight
val MessengerSeparatorDark = iOSSeparatorDark
val MessengerBubbleOtherLight = iOSBubbleOtherLight
val MessengerBubbleOtherDark = iOSBubbleOtherDark

val MetaGray4 = iOSGray
val InstagramStoryBorder = listOf(
    Color(0xFFF9CE34),
    Color(0xFFEE2A7B),
    Color(0xFF6228D7)
)
val InstagramPink = Color(0xFFE1306C)
val MetaBlack = Color(0xFF000000)
val MetaDarkGray = Color(0xFF121212)
val MetaDarkSurface = Color(0xFF262626)
val MetaGray1 = Color(0xFFFAFAFA)
val MetaGray2 = Color(0xFFEFEFEF)
val MetaGray3 = Color(0xFFDBDBDB)
val MetaGray5 = Color(0xFF262626)
