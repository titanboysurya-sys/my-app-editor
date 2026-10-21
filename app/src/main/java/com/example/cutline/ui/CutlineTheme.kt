package com.example.cutline.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

private val CutlineColors = darkColorScheme(
    primary = Color(0xFF8B7CFF),
    onPrimary = Color(0xFF160F3D),
    secondary = Color(0xFF55D6BE),
    background = Color(0xFF080B12),
    surface = Color(0xFF111722),
    surfaceVariant = Color(0xFF1B2433),
    onSurface = Color(0xFFE7EAF2),
    onSurfaceVariant = Color(0xFFAAB3C5),
    outline = Color(0xFF344158),
    surfaceContainer = Color(0xFF151D2B)
)

private val CutlineShapes = Shapes(
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp)
)

@Composable
fun CutlineTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = CutlineColors,
        shapes = CutlineShapes,
        content = content
    )
}
