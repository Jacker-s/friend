package com.jack.friend.ui.swiftui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp

/**
 * Helpers estilo SwiftUI:
 * - swiftDpAsState
 * - swiftFloatAsState
 * - swiftColorAsState
 *
 * Usa os springs do SwiftUISpring (com genéricos).
 */
object SwiftUIAnimations {

    @Composable
    fun swiftDpAsState(
        target: Dp,
        spec: AnimationSpec<Dp> = SwiftUISpring.smooth(),
        label: String = "swiftDp"
    ): State<Dp> {
        return animateDpAsState(
            targetValue = target,
            animationSpec = spec,
            label = label
        )
    }

    @Composable
    fun swiftFloatAsState(
        target: Float,
        spec: AnimationSpec<Float> = SwiftUISpring.Smooth,
        label: String = "swiftFloat"
    ): State<Float> {
        return animateFloatAsState(
            targetValue = target,
            animationSpec = spec,
            label = label
        )
    }

    @Composable
    fun swiftColorAsState(
        target: Color,
        spec: AnimationSpec<Color> = SwiftUISpring.smooth(),
        label: String = "swiftColor"
    ): State<Color> {
        return animateColorAsState(
            targetValue = target,
            animationSpec = spec,
            label = label
        )
    }
}
