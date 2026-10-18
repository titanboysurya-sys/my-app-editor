package com.example.cutline.export

enum class ExportStatus { IDLE, EXPORTING, COMPLETE, FAILED, CANCELLED }

data class ExportSnapshot(
    val status: ExportStatus = ExportStatus.IDLE,
    val progress: Int = 0,
    val outputPath: String? = null,
    val error: String? = null
)
