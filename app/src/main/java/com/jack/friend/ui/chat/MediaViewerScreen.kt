package com.jack.friend.ui.chat

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import coil.compose.SubcomposeAsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

sealed interface MediaViewerItem {
    data class Image(val url: String) : MediaViewerItem
    data class Video(val url: String) : MediaViewerItem
}

@Composable
fun MediaViewerScreen(
    mediaItem: MediaViewerItem?,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val view = LocalView.current
    
    // Core states for gestures
    val offsetY = remember { Animatable(0f) }
    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val imageOffsetY = remember { Animatable(0f) }
    
    var isUiVisible by remember { mutableStateOf(true) }
    var containerSize by remember { mutableStateOf(Size.Zero) }
    var isDraggingToDismiss by remember { mutableStateOf(false) }

    // Auto-hide UI after delay
    LaunchedEffect(isUiVisible) {
        if (isUiVisible && mediaItem != null) {
            delay(3000)
            isUiVisible = false
        }
    }

    // Reset animations when item closes
    LaunchedEffect(mediaItem) {
        if (mediaItem == null) {
            offsetY.snapTo(0f)
            scale.snapTo(1f)
            offsetX.snapTo(0f)
            imageOffsetY.snapTo(0f)
            isUiVisible = false
            isDraggingToDismiss = false
        } else {
            isUiVisible = true
        }
    }

    val bgAlpha by animateFloatAsState(
        targetValue = if (mediaItem != null) (1f - (abs(offsetY.value) / 800f)).coerceIn(0f, 1f) else 0f,
        animationSpec = tween(300), label = "bg_alpha"
    )

    val dragScale by animateFloatAsState(
        targetValue = if (isDraggingToDismiss) (1f - (abs(offsetY.value) / 3000f)).coerceIn(0.85f, 1f) else 1f,
        label = "drag_scale"
    )

    val dragRotation by animateFloatAsState(
        targetValue = if (isDraggingToDismiss) (offsetY.value / 40f).coerceIn(-15f, 15f) else 0f,
        label = "drag_rotation"
    )

    AnimatedVisibility(
        visible = mediaItem != null,
        enter = fadeIn(tween(300)) + scaleIn(tween(300), initialScale = 0.9f),
        exit = fadeOut(tween(250)) + scaleOut(tween(250), targetScale = 0.8f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = bgAlpha))
                .onGloballyPositioned { containerSize = it.size.toSize() }
                .pointerInput(Unit) {
                    detectTapGestures(onTap = { isUiVisible = !isUiVisible })
                },
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .offset { IntOffset(0, offsetY.value.roundToInt()) }
                    .graphicsLayer {
                        scaleX = dragScale
                        scaleY = dragScale
                        rotationZ = dragRotation
                    }
                    .pointerInput(scale.value) {
                        // Dismiss gesture logic - only active when not zoomed in
                        if (scale.value <= 1.05f) {
                            val velocityTracker = VelocityTracker()
                            detectDragGestures(
                                onDragStart = { 
                                    isDraggingToDismiss = true
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    velocityTracker.addPosition(change.uptimeMillis, change.position)
                                    scope.launch { offsetY.snapTo(offsetY.value + dragAmount.y) }
                                },
                                onDragEnd = {
                                    val velocity = velocityTracker.calculateVelocity().y
                                    if (abs(offsetY.value) > 300f || abs(velocity) > 1500f) {
                                        onDismiss()
                                    } else {
                                        scope.launch {
                                            isDraggingToDismiss = false
                                            offsetY.animateTo(0f, spring(dampingRatio = 0.75f, stiffness = Spring.StiffnessLow))
                                        }
                                    }
                                },
                                onDragCancel = {
                                    scope.launch { 
                                        offsetY.animateTo(0f)
                                        isDraggingToDismiss = false
                                    }
                                }
                            )
                        }
                    }
            ) {
                when (mediaItem) {
                    is MediaViewerItem.Image -> {
                        val state = rememberTransformableState { zoomChange, panChange, _ ->
                            scope.launch {
                                val newScale = (scale.value * zoomChange).coerceIn(1f, 6f)
                                scale.snapTo(newScale)
                                
                                if (newScale > 1f) {
                                    val maxX = (containerSize.width * (newScale - 1)) / 2
                                    val maxY = (containerSize.height * (newScale - 1)) / 2
                                    offsetX.snapTo((offsetX.value + panChange.x * newScale).coerceIn(-maxX, maxX))
                                    imageOffsetY.snapTo((imageOffsetY.value + panChange.y * newScale).coerceIn(-maxY, maxY))
                                    isUiVisible = false
                                } else {
                                    offsetX.snapTo(0f)
                                    imageOffsetY.snapTo(0f)
                                }
                            }
                        }

                        SubcomposeAsyncImage(
                            model = mediaItem.url,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            loading = {
                                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                    CircularProgressIndicator(color = Color.White.copy(alpha = 0.3f), strokeWidth = 2.dp)
                                }
                            },
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer {
                                    scaleX = scale.value
                                    scaleY = scale.value
                                    translationX = offsetX.value
                                    translationY = imageOffsetY.value
                                }
                                .transformable(state = state)
                                .pointerInput(Unit) {
                                    detectTapGestures(
                                        onDoubleTap = { tapOffset ->
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            scope.launch {
                                                if (scale.value > 1f) {
                                                    launch { scale.animateTo(1f, tween(300)) }
                                                    launch { offsetX.animateTo(0f, tween(300)) }
                                                    launch { imageOffsetY.animateTo(0f, tween(300)) }
                                                    isUiVisible = true
                                                } else {
                                                    isUiVisible = false
                                                    val targetScale = 3f
                                                    val center = Offset(containerSize.width / 2, containerSize.height / 2)
                                                    val targetOffset = (center - tapOffset) * (targetScale - 1f)
                                                    
                                                    val maxX = (containerSize.width * (targetScale - 1)) / 2
                                                    val maxY = (containerSize.height * (targetScale - 1)) / 2
                                                    
                                                    launch { offsetX.animateTo(targetOffset.x.coerceIn(-maxX, maxX), tween(300)) }
                                                    launch { imageOffsetY.animateTo(targetOffset.y.coerceIn(-maxY, maxY), tween(300)) }
                                                    launch { scale.animateTo(targetScale, tween(300)) }
                                                }
                                            }
                                        },
                                        onTap = { isUiVisible = !isUiVisible }
                                    )
                                }
                        )
                    }
                    is MediaViewerItem.Video -> {
                        VideoPlayerComposable(
                            videoUrl = mediaItem.url,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    null -> {}
                }
            }

            // Overlay UI
            AnimatedVisibility(
                visible = isUiVisible,
                enter = fadeIn(tween(400)),
                exit = fadeOut(tween(400))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.verticalGradient(
                                listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent, Color.Transparent, Color.Black.copy(alpha = 0.4f))
                            )
                        )
                ) {
                    // Close Button
                    IconButton(
                        onClick = {
                            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                            onDismiss()
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .statusBarsPadding()
                            .padding(top = 16.dp, end = 20.dp)
                            .size(44.dp)
                            .background(Color.White.copy(alpha = 0.15f), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "Fechar",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }
    }
}
