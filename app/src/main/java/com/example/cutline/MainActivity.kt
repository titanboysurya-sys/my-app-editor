package com.example.cutline

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.example.cutline.ui.EditorScreen
import com.example.cutline.ui.CutlineTheme
import com.example.cutline.export.ExportController
import javax.inject.Inject
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var exportController: ExportController
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CutlineTheme {
                Surface { EditorScreen(exportController = exportController) }
            }
        }
    }
}
