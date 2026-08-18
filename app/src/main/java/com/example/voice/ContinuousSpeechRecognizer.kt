package com.example.voice

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Continuous 24/7 Voice Recognition Engine for Zoya AI Chatbox.
 * Keeps the microphone open continuously, captures speech with high fidelity,
 * and automatically forwards finalized speech directly to the AI assistant.
 */
class ContinuousSpeechRecognizer(
    private val context: Context,
    private val onSpeechRecognized: (String) -> Unit
) {
    private var speechRecognizer: SpeechRecognizer? = null
    private val mainHandler = Handler(Looper.getMainLooper())

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _is24HourModeActive = MutableStateFlow(false)
    val is24HourModeActive: StateFlow<Boolean> = _is24HourModeActive.asStateFlow()

    private val _partialText = MutableStateFlow("")
    val partialText: StateFlow<String> = _partialText.asStateFlow()

    private val _lastForwardedText = MutableStateFlow("")
    val lastForwardedText: StateFlow<String> = _lastForwardedText.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    private var shouldKeepListening = false
    private var isDestroyed = false

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("ContinuousSpeech", "Microphone ready for speech input")
            _isListening.value = true
        }

        override fun onBeginningOfSpeech() {
            Log.d("ContinuousSpeech", "User began speaking")
        }

        override fun onRmsChanged(rmsdB: Float) {
            _rmsDb.value = rmsdB.coerceIn(0f, 10f)
        }

        override fun onBufferReceived(buffer: ByteArray?) {}

        override fun onEndOfSpeech() {
            Log.d("ContinuousSpeech", "Speech paused/ended, processing recognition...")
        }

        override fun onError(error: Int) {
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Missing RECORD_AUDIO permission"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No recognition match"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout (silence)"
                else -> "Speech error code $error"
            }
            Log.d("ContinuousSpeech", "onError: $errorMsg")
            _rmsDb.value = 0f

            // Auto-restart loop to keep the microphone open 24 hours continuously
            if (shouldKeepListening && !isDestroyed) {
                mainHandler.postDelayed({
                    if (shouldKeepListening && !isDestroyed) {
                        startListeningInternal()
                    }
                }, 350)
            } else {
                _isListening.value = false
            }
        }

        override fun onResults(results: Bundle?) {
            val matches = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val recognizedText = matches?.firstOrNull()?.trim()
            _rmsDb.value = 0f
            _partialText.value = ""

            if (!recognizedText.isNullOrBlank()) {
                Log.i("ContinuousSpeech", "Recognized voice query: '$recognizedText' -> Forwarding to AI")
                _lastForwardedText.value = recognizedText
                onSpeechRecognized(recognizedText)
            }

            // Immediately restart the recognition loop so the mic stays open 24/7
            if (shouldKeepListening && !isDestroyed) {
                mainHandler.postDelayed({
                    if (shouldKeepListening && !isDestroyed) {
                        startListeningInternal()
                    }
                }, 250)
            } else {
                _isListening.value = false
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val matches = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            val partial = matches?.firstOrNull()?.trim() ?: ""
            if (partial.isNotEmpty()) {
                _partialText.value = partial
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    /**
     * Toggles the 24-hour continuous mic mode.
     */
    fun toggle24HourListening() {
        if (_is24HourModeActive.value) {
            stopListening()
        } else {
            start24HourListening()
        }
    }

    /**
     * Starts continuous 24-hour microphone listening.
     */
    fun start24HourListening() {
        shouldKeepListening = true
        isDestroyed = false
        _is24HourModeActive.value = true
        mainHandler.post {
            startListeningInternal()
        }
    }

    /**
     * Stops the continuous microphone listening.
     */
    fun stopListening() {
        shouldKeepListening = false
        _is24HourModeActive.value = false
        mainHandler.post {
            try {
                speechRecognizer?.stopListening()
                speechRecognizer?.cancel()
            } catch (e: Exception) {
                Log.e("ContinuousSpeech", "Error stopping speech recognizer", e)
            }
            _isListening.value = false
            _partialText.value = ""
            _rmsDb.value = 0f
        }
    }

    private fun startListeningInternal() {
        if (!shouldKeepListening || isDestroyed) return
        try {
            if (speechRecognizer == null) {
                if (SpeechRecognizer.isRecognitionAvailable(context)) {
                    speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context).apply {
                        setRecognitionListener(listener)
                    }
                } else {
                    Log.e("ContinuousSpeech", "Speech recognition service not available on device")
                    return
                }
            }

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault())
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 500L)
            }

            speechRecognizer?.startListening(intent)
            _isListening.value = true
        } catch (e: Exception) {
            Log.e("ContinuousSpeech", "Error starting listening session", e)
            if (shouldKeepListening && !isDestroyed) {
                mainHandler.postDelayed({ startListeningInternal() }, 800)
            }
        }
    }

    fun destroy() {
        isDestroyed = true
        shouldKeepListening = false
        _is24HourModeActive.value = false
        mainHandler.post {
            try {
                speechRecognizer?.destroy()
                speechRecognizer = null
            } catch (e: Exception) {
                Log.e("ContinuousSpeech", "Error destroying speech recognizer", e)
            }
            _isListening.value = false
        }
    }
}
