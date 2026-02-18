package com.jack.friend.ui.ios

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

/**
 * Presets de spring pra ficar “SwiftUI-like”.
 * Use: animationSpec = SwiftSpring.mediumBouncy
 */
object SwiftSpring {
    val soft = spring<Float>(
        stiffness = Spring.StiffnessLow,
        dampingRatio = Spring.DampingRatioNoBouncy
    )

    val medium = spring<Float>(
        stiffness = Spring.StiffnessMedium,
        dampingRatio = Spring.DampingRatioNoBouncy
    )

    val mediumBouncy = spring<Float>(
        stiffness = Spring.StiffnessLow,
        dampingRatio = Spring.DampingRatioMediumBouncy
    )

    val bouncy = spring<Float>(
        stiffness = Spring.StiffnessVeryLow,
        dampingRatio = Spring.DampingRatioHighBouncy
    )
}
