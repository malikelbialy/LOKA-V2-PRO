package com.loka.viva.logic

import android.content.Context
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class VoiceAssistant(
    private val context: Context,
    private val onCommand: (String) -> Unit,
    private val onState: (State) -> Unit
) {
    private var recognizer: SpeechRecognizer? = null
    private var listening = false
    private val wakeWords = listOf("viva", "فيفا", "فيڤا")
    private val scope = CoroutineScope(Dispatchers.IO)

    init {
        initRecognizer()
    }

    private fun initRecognizer() {
        recognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
            setRecognitionListener(object : RecognitionListener {
                override fun onReadyForSpeech(params: android.os.Bundle?) = onState(State.LISTENING)
                override fun onBeginningOfSpeech() {}
                override fun onRmsChanged(rmsdB: Float) {}
                override fun onBufferReceived(buffer: ByteArray) {}
                override fun onEndOfSpeech() {}
                override fun onError(error: Int) {
                    onState(State.IDLE)
                    if (listening) restartListening()
                }
                override fun onResults(results: android.os.Bundle?) {
                    val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.lowercase() ?: ""
                    if (isWakeWord(text)) {
                        onState(State.WAKE_DETECTED)
                        listenForCommand()
                    } else if (listening) {
                        onCommand(text)
                    }
                    if (listening) restartListening()
                }
                override fun onPartialResults(partial: android.os.Bundle?) {}
                override fun onEvent(eventType: Int, params: android.os.Bundle?) {}
            })
        }
    }

    private fun isWakeWord(text: String): Boolean {
        return wakeWords.any { text.contains(it) }
    }

    fun start() {
        if (listening) return
        listening = true
        onState(State.IDLE)
        restartListening()
    }

    fun stop() {
        listening = false
        recognizer?.cancel()
        onState(State.STOPPED)
    }

    private fun restartListening() {
        val intent = android.content.Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ar")
        }
        recognizer?.startListening(intent)
    }

    fun destroy() {
        stop()
        recognizer?.destroy()
    }

    enum class State { IDLE, LISTENING, WAKE_DETECTED, STOPPED }
}
