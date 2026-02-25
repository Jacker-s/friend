package com.jack.friend.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.jack.friend.ui.theme.LocalChatColors
import kotlin.math.sin

enum class BackgroundStyle {
    WAVES,      // Estilo atual de ondas
    BLOBLOOP,   // Bolhas flutuantes orgânicas (substitui Mesh)
    PARTICLES,  // Partículas flutuantes discretas
    AURORA,     // Efeito Aurora Boreal
    SOLID       // Cor sólida limpa
}

@Composable
fun DynamicBackground(
    modifier: Modifier = Modifier,
    style: BackgroundStyle = BackgroundStyle.WAVES
) {
    val chatColors = LocalChatColors.current
    val context = LocalContext.current
    val uiPrefs = remember { context.getSharedPreferences("ui_prefs", android.content.Context.MODE_PRIVATE) }
    
    // Escuta a preferência de desativar animações
    var disableAnimations by remember { mutableStateOf(uiPrefs.getBoolean("disable_animations", false)) }
    
    DisposableEffect(uiPrefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { prefs, key ->
            if (key == "disable_animations") {
                disableAnimations = prefs.getBoolean("disable_animations", false)
            }
        }
        uiPrefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { uiPrefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    Box(modifier = modifier.fillMaxSize().background(chatColors.background)) {
        when (style) {
            BackgroundStyle.WAVES -> DynamicWaveBackground(animate = !disableAnimations)
            BackgroundStyle.BLOBLOOP -> BlobLoopBackground(animate = !disableAnimations)
            BackgroundStyle.PARTICLES -> ParticlesBackground(animate = !disableAnimations)
            BackgroundStyle.AURORA -> AuroraBackground(animate = !disableAnimations)
            BackgroundStyle.SOLID -> { /* Apenas o fundo do Box */ }
        }
    }
}

@Composable
fun BlobLoopBackground(animate: Boolean = true) {
    val colors = LocalChatColors.current.waveColors
    val infiniteTransition = rememberInfiniteTransition(label = "blobs")
    
    val animProgress by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(12000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "blobsAnim"
        )
    } else remember { mutableFloatStateOf(0f) }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        colors.forEachIndexed { index, color ->
            val phase = (index.toFloat() / colors.size) * 2 * Math.PI
            val x = width * 0.5f + width * 0.3f * sin(animProgress * 2 * Math.PI + phase).toFloat()
            val y = height * 0.5f + height * 0.2f * sin(animProgress * 4 * Math.PI + phase).toFloat()
            
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.4f), Color.Transparent),
                    center = Offset(x, y),
                    radius = width * 0.7f
                ),
                center = Offset(x, y),
                radius = width * 0.7f
            )
        }
    }
}

@Composable
fun AuroraBackground(animate: Boolean = true) {
    val colors = LocalChatColors.current.waveColors
    val infiniteTransition = rememberInfiniteTransition(label = "aurora")
    
    val shift by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(
                animation = tween(15000, easing = LinearOutSlowInEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "shift"
        )
    } else remember { mutableFloatStateOf(0f) }

    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        colors.forEachIndexed { index, color ->
            val offsetMult = (index + 1) * 0.2f
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = 0.15f), Color.Transparent),
                    center = Offset(width * (0.5f + sin(shift * offsetMult) * 0.3f), height * (0.3f + index * 0.1f)),
                    radius = width * 1.2f
                ),
                center = Offset(width * (0.5f + sin(shift * offsetMult) * 0.3f), height * (0.3f + index * 0.1f)),
                radius = width * 1.5f
            )
        }
    }
}

@Composable
fun ParticlesBackground(animate: Boolean = true) {
    val color = LocalChatColors.current.primary.copy(alpha = 0.15f)
    val particles = remember { List(15) { ParticleData() } }

    Box(modifier = Modifier.fillMaxSize()) {
        particles.forEach { particle ->
            FloatingParticle(particle, color, animate)
        }
    }
}

private data class ParticleData(
    val x: Float = (0..100).random() / 100f,
    val y: Float = (0..100).random() / 100f,
    val size: Float = (10..40).random().toFloat(),
    val duration: Int = (4000..8000).random()
)

@Composable
private fun FloatingParticle(data: ParticleData, color: Color, animate: Boolean) {
    val infiniteTransition = rememberInfiniteTransition(label = "particle")
    
    val alpha by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0.2f,
            targetValue = 0.8f,
            animationSpec = infiniteRepeatable(
                animation = tween(data.duration),
                repeatMode = RepeatMode.Reverse
            ),
            label = "alpha"
        )
    } else remember { mutableFloatStateOf(0.5f) }

    val posY by if (animate) {
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = -50f,
            animationSpec = infiniteRepeatable(
                animation = tween(data.duration + 1000),
                repeatMode = RepeatMode.Reverse
            ),
            label = "pos"
        )
    } else remember { mutableFloatStateOf(0f) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(start = (data.x * 300).dp, top = (data.y * 600).dp)
            .graphicsLayer { 
                translationY = posY
                this.alpha = alpha 
            }
            .size(data.size.dp)
            .background(color, CircleShape)
    )
}

@Composable
fun DynamicWaveBackground(
    modifier: Modifier = Modifier,
    animate: Boolean = true,
    phase: Float = 0f
) {
    val colors = LocalChatColors.current.waveColors
    
    val time by if (animate) {
        val infiniteTransition = rememberInfiniteTransition(label = "waveTransition")
        infiniteTransition.animateFloat(
            initialValue = 0f,
            targetValue = 1000f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 600000, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "waveTime"
        )
    } else {
        remember { mutableFloatStateOf(0f) }
    }

    val finalPhase = time + phase

    Canvas(modifier = modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val path1 = Path().apply {
            moveTo(0f, 0f)
            for (x in 0..width.toInt() step 5) {
                val y = height * 0.22f + 35 * sin(x * 0.008f + finalPhase * 0.8f)
                lineTo(x.toFloat(), y)
            }
            lineTo(width, 0f)
            close()
        }

        val path2 = Path().apply {
            moveTo(0f, 0f)
            for (x in 0..width.toInt() step 5) {
                val y = height * 0.20f + 45 * sin(x * 0.01f + finalPhase * 0.6f + 1.5f)
                lineTo(x.toFloat(), y)
            }
            lineTo(width, 0f)
            close()
        }
        
        val path3 = Path().apply {
            moveTo(0f, 0f)
            for (x in 0..width.toInt() step 5) {
                val y = height * 0.18f + 55 * sin(x * 0.006f + finalPhase * 0.4f + 3f)
                lineTo(x.toFloat(), y)
            }
            lineTo(width, 0f)
            close()
        }

        if (colors.size >= 3) {
            drawPath(
                path = path3,
                brush = Brush.verticalGradient(
                    colors = listOf(colors[2].copy(alpha = 0.35f), Color.Transparent),
                    startY = 0f,
                    endY = height * 0.35f
                )
            )
            drawPath(
                path = path2,
                brush = Brush.verticalGradient(
                    colors = listOf(colors[1].copy(alpha = 0.45f), Color.Transparent),
                    startY = 0f,
                    endY = height * 0.35f
                )
            )
            drawPath(
                path = path1,
                brush = Brush.verticalGradient(
                    colors = listOf(colors[0].copy(alpha = 0.55f), Color.Transparent),
                    startY = 0f,
                    endY = height * 0.35f
                )
            )
        }
    }
}
