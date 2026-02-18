package com.jack.friend.ui.swiftui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring

/**
 * SwiftUI-like springs.
 * ✅ Corrigido: agora o tipo genérico é explícito (spring<Float>).
 * ✅ Também deixei funções genéricas pra usar em Dp/Color/etc quando precisar.
 */
object SwiftUISpring {

    // Pro uso mais comum (Float)
    val Bouncy: SpringSpec<Float> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    val Smooth: SpringSpec<Float> = spring(
        dampingRatio = 0.95f,
        stiffness = 650f
    )

    val Medium: SpringSpec<Float> = spring(
        dampingRatio = 0.85f,
        stiffness = 800f
    )

    // Versões genéricas (quando você quiser aplicar em Dp/Color/etc)
    fun <T> bouncy(): SpringSpec<T> = spring(
        dampingRatio = Spring.DampingRatioMediumBouncy,
        stiffness = Spring.StiffnessMedium
    )

    fun <T> smooth(): SpringSpec<T> = spring(
        dampingRatio = 0.95f,
        stiffness = 650f
    )

    fun <T> medium(): SpringSpec<T> = spring(
        dampingRatio = 0.85f,
        stiffness = 800f
    )
}
