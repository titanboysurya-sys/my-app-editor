package com.example.cutline.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import com.example.cutline.media.MediaIngestor
import com.example.cutline.model.TimelineClip

@Composable
fun MediaThumbnail(
    clip: TimelineClip,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val bitmap by produceState<android.graphics.Bitmap?>(null, clip.source, clip.id) {
        value = runCatching {
            MediaIngestor(context.contentResolver).thumbnail(
                clip.source,
                clip.sourceStartMs
            )
        }.getOrNull()
    }
    if (bitmap != null) {
        Image(
            bitmap = bitmap!!.asImageBitmap(),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier
        )
    } else {
        androidx.compose.foundation.layout.Box(
            modifier = modifier.background(Color(0xFF27344D))
        )
    }
}
