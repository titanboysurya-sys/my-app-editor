package com.example.cutline.media

import android.content.Context
import android.net.Uri
import java.io.File

data class LocalSticker(val id: String, val name: String, val uri: Uri)
data class LocalTemplate(val id: String, val name: String, val projectFile: File)

class LocalCreativeCatalog(private val context: Context) {
    private val stickerDir get() = File(context.filesDir, "stickers")
    private val templateDir get() = File(context.filesDir, "templates")

    fun stickers(): List<LocalSticker> = stickerDir.listFiles()
        ?.filter { it.isFile }
        ?.map { LocalSticker(it.nameWithoutExtension, it.nameWithoutExtension, Uri.fromFile(it)) }
        .orEmpty()

    fun templates(): List<LocalTemplate> = templateDir.listFiles()
        ?.filter { it.extension == "json" }
        ?.map { LocalTemplate(it.nameWithoutExtension, it.nameWithoutExtension, it) }
        .orEmpty()
}
