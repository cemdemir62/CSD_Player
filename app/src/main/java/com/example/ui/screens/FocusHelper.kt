package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.FocusedBorder

@Composable
fun Modifier.tvFocusBorder(
    isTvMode: Boolean,
    interactionSource: MutableInteractionSource,
    shape: Shape = RoundedCornerShape(8.dp),
    borderWidth: Dp = 2.5.dp,
    borderColor: Color = FocusedBorder
): Modifier {
    if (!isTvMode) return this
    val isFocused by interactionSource.collectIsFocusedAsState()
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "tv_focus_scale"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isFocused) borderColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "tv_focus_border"
    )
    return this
        .scale(animatedScale)
        .border(width = borderWidth, color = animatedBorderColor, shape = shape)
}

@Composable
fun Modifier.tvClickable(
    isTvMode: Boolean,
    shape: Shape = RoundedCornerShape(8.dp),
    borderWidth: Dp = 2.5.dp,
    borderColor: Color = FocusedBorder,
    onClick: () -> Unit
): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    val isFocused by interactionSource.collectIsFocusedAsState()
    
    val animatedScale by animateFloatAsState(
        targetValue = if (isFocused && isTvMode) 1.05f else 1.0f,
        animationSpec = tween(durationMillis = 200),
        label = "tv_clickable_scale"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isFocused && isTvMode) borderColor else Color.Transparent,
        animationSpec = tween(durationMillis = 200),
        label = "tv_clickable_border"
    )

    val base = this
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current,
            onClick = onClick
        )
    return if (isTvMode) {
        base
            .focusable(interactionSource = interactionSource)
            .scale(animatedScale)
            .border(width = borderWidth, color = animatedBorderColor, shape = shape)
    } else {
        base
    }
}

