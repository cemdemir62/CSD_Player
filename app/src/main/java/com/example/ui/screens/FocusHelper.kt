package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import android.view.SoundEffectConstants
import android.view.HapticFeedbackConstants
import com.example.ui.theme.FocusedBorder

@Composable
fun Modifier.tvFocusBorder(
    isTvMode: Boolean,
    interactionSource: MutableInteractionSource,
    shape: Shape = RoundedCornerShape(8.dp),
    borderWidth: Dp = 2.5.dp,
    borderColor: Color = FocusedBorder
): Modifier {
    val isFocused by interactionSource.collectIsFocusedAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val view = LocalView.current
    LaunchedEffect(isFocused) {
        if (isFocused) {
            try {
                view.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT)
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            } catch (e: Exception) {}
        }
    }
    
    val targetScale = if (isTvMode) {
        if (isPressed) 0.98f else if (isFocused) 1.05f else 1.0f
    } else {
        if (isPressed) 0.96f else 1.0f
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 150),
        label = "tv_focus_scale"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isFocused && isTvMode) borderColor else Color.Transparent,
        animationSpec = tween(durationMillis = 150),
        label = "tv_focus_border"
    )
    
    val modifier = this.scale(animatedScale)
    return if (isTvMode) {
        modifier.border(width = borderWidth, color = animatedBorderColor, shape = shape)
    } else {
        modifier
    }
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
    val isPressed by interactionSource.collectIsPressedAsState()
    
    val view = LocalView.current
    LaunchedEffect(isFocused) {
        if (isFocused) {
            try {
                view.playSoundEffect(SoundEffectConstants.NAVIGATION_RIGHT)
                view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            } catch (e: Exception) {}
        }
    }
    
    val targetScale = if (isTvMode) {
        if (isPressed) 0.98f else if (isFocused) 1.05f else 1.0f
    } else {
        if (isPressed) 0.95f else 1.0f
    }

    val animatedScale by animateFloatAsState(
        targetValue = targetScale,
        animationSpec = tween(durationMillis = 150),
        label = "tv_clickable_scale"
    )
    val animatedBorderColor by animateColorAsState(
        targetValue = if (isFocused && isTvMode) borderColor else Color.Transparent,
        animationSpec = tween(durationMillis = 150),
        label = "tv_clickable_border"
    )

    val base = this
        .scale(animatedScale)
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current,
            onClick = onClick
        )
    return if (isTvMode) {
        base
            .focusable(interactionSource = interactionSource)
            .border(width = borderWidth, color = animatedBorderColor, shape = shape)
    } else {
        base
    }
}

