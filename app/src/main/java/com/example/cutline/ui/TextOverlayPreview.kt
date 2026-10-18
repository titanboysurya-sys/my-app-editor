package com.example.cutline.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.cutline.model.TextAlignment
import com.example.cutline.model.TextAnimation
import com.example.cutline.model.TextStyle

@Composable
fun TextOverlayPreview(style: TextStyle, modifier: Modifier = Modifier) {
    val alpha by animateFloatAsState(
        targetValue = if (style.animation == TextAnimation.FADE_IN) 1f else 1f,
        label = "textAlpha"
    )
    val align = when (style.alignment) {
        TextAlignment.START -> TextAlign.Start
        TextAlignment.CENTER -> TextAlign.Center
        TextAlignment.END -> TextAlign.End
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(24.dp)
            .then(style.backgroundArgb?.let { Modifier.background(Color(it.toULong())) } ?: Modifier)
    ) {
        Text(
            text = style.text,
            color = Color(style.colorArgb.toULong()).copy(alpha = alpha),
            fontSize = style.fontSizeSp.sp,
            fontFamily = FontFamily.Default,
            textAlign = align,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
