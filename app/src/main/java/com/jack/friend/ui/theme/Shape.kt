package com.jack.friend.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val Shapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

// Custom shapes for chat bubbles
val ChatBubbleShapeMe = RoundedCornerShape(18.dp, 18.dp, 2.dp, 18.dp)
val ChatBubbleShapeOther = RoundedCornerShape(18.dp, 18.dp, 18.dp, 2.dp)
val ChatBubbleShapeGroup = RoundedCornerShape(18.dp)
