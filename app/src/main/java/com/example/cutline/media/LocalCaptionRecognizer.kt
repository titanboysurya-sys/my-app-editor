package com.example.cutline.media

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

class LocalCaptionRecognizer(
    context: Context,
    private val onText: (String) -> Unit,
    private val onRecognitionError: (Int) -> Unit = {}
) {
    private val recognizer: SpeechRecognizer? = when {
        !SpeechRecognizer.isRecognitionAvailable(context) -> null
        Build.VERSION.SDK_INT >= 31 && SpeechRecognizer.isOnDeviceRecognitionAvailable(context) ->
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        else -> SpeechRecognizer.createSpeechRecognizer(context)
    }

    init {
        recognizer?.setRecognitionListener(object : RecognitionListener {
            override fun onResults(results: android.os.Bundle) {
                results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()?.let(onText)
            }
            override fun onError(error: Int) = onRecognitionError(error)
            override fun onReadyForSpeech(params: android.os.Bundle?) = Unit
            override fun onBeginningOfSpeech() = Unit
            override fun onRmsChanged(rmsdB: Float) = Unit
            override fun onBufferReceived(buffer: ByteArray?) = Unit
            override fun onEndOfSpeech() = Unit
            override fun onPartialResults(partialResults: android.os.Bundle?) = Unit
            override fun onEvent(eventType: Int, params: android.os.Bundle?) = Unit
        })
    }

    fun start(languageTag: String = "en-US") {
        recognizer?.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, languageTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        })
    }

    fun stop() = recognizer?.stopListening()
    fun release() = recognizer?.destroy()
}
