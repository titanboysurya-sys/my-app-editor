package com.example.cutline.media

import android.content.Context
import android.media.MediaRecorder
import android.speech.tts.TextToSpeech
import java.io.File
import java.util.Locale

class VoiceOverRecorder(private val context: Context) {
    private var recorder: MediaRecorder? = null
    private var output: File? = null

    fun start(file: File) {
        check(recorder == null) { "Voice recording is already active" }
        output = file
        recorder = MediaRecorder().apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioSamplingRate(48_000)
            setAudioEncodingBitRate(192_000)
            setOutputFile(file.absolutePath)
            prepare()
            start()
        }
    }

    fun stop(): File? {
        val active = recorder ?: return null
        runCatching { active.stop() }
        active.reset()
        active.release()
        recorder = null
        return output
    }
}

class LocalTextToSpeech(context: Context, onReady: (Boolean) -> Unit = {}) :
    TextToSpeech.OnInitListener {
    private val engine = TextToSpeech(context, this)
    private var ready = false
    private val readyCallback = onReady

    override fun onInit(status: Int) {
        ready = status == TextToSpeech.SUCCESS
        if (ready) engine.language = Locale.getDefault()
        readyCallback(ready)
    }

    fun speak(text: String) {
        if (ready) engine.speak(text, TextToSpeech.QUEUE_FLUSH, null, "cutline-tts")
    }

    fun shutdown() = engine.shutdown()
}
