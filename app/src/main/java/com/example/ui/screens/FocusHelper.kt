package com.example.ui.screens

import androidx.compose.animation.animateColorAsState
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
    return if (isFocused) {
        this.border(width = borderWidth, color = borderColor, shape = shape)
    } else {
        this
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
    val base = this
        .clickable(
            interactionSource = interactionSource,
            indication = androidx.compose.foundation.LocalIndication.current,
            onClick = onClick
        )
    return if (isTvMode) {
        val isFocused by interactionSource.collectIsFocusedAsState()
        val focusableModifier = base.focusable(interactionSource = interactionSource)
        if (isFocused) {
            focusableModifier.border(width = borderWidth, color = borderColor, shape = shape)
        } else {
            focusableModifier
        }
    } else {
        base
    }
}
