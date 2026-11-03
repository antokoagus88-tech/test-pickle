package com.pickle.assistant.stt

import android.content.Context
import android.content.Intent
import android.os.Build
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.pickle.assistant.PickleApp
import com.pickle.assistant.config.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.Locale

class SpeechManager private constructor(
    private val context: Context = PickleApp.getContext()
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs by lazy { PickleApp.getContext().preferencesRepository }
    private var speechRecognizer: SpeechRecognizer? = null
    private var isListening = false
    private var currentListener: RecognitionListener? = null
    private val resultChannel = Channel<String>(10)
    private val errorChannel = Channel<String>(10)

    companion object {
        @Volatile private var INSTANCE: SpeechManager? = null
        fun getInstance(context: Context = PickleApp.getContext()): SpeechManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SpeechManager(context).also { INSTANCE = it }
            }
        }
    }

    init {
        initializeRecognizer()
    }

    private fun initializeRecognizer() {
        if (SpeechRecognizer.isRecognitionAvailable(context)) {
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(context)
            speechRecognizer?.setRecognitionListener(defaultListener)
            Log.d("PickleSpeech", "SpeechRecognizer initialized")
        } else {
            Log.w("PickleSpeech", "Speech recognition not available on this device")
        }
    }

    private val defaultListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            Log.d("PickleSpeech", "Ready for speech")
        }

        override fun onBeginningOfSpeech() {
            Log.d("PickleSpeech", "Beginning of speech")
        }

        override fun onRmsChanged(rmsdB: Float) {
        }

        override fun onBufferReceived(buffer: ByteArray?) {
        }

        override fun onEndOfSpeech() {
            Log.d("PickleSpeech", "End of speech")
        }

        override fun onError(error: Int) {
            val errorMsg = when (error) {
                SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                SpeechRecognizer.ERROR_NETWORK -> "Network error"
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                SpeechRecognizer.ERROR_NO_MATCH -> "No match found"
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                SpeechRecognizer.ERROR_SERVER -> "Server error"
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Speech timeout"
                else -> "Unknown error: $error"
            }
            Log.e("PickleSpeech", "Recognition error: $errorMsg")
            errorChannel.trySend(errorMsg)
        }

        override fun onResults(results: Bundle) {
            val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            matches?.firstOrNull()?.let { text ->
                Log.d("PickleSpeech", "Recognized: $text")
                resultChannel.trySend(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle) {
            val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            matches?.firstOrNull()?.let { text ->
                Log.d("PickleSpeech", "Partial: $text")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) {
        }
    }

    fun startListening(
        onResult: (String) -> Unit,
        onError: (String) -> Unit,
        onPartialResult: ((String) -> Unit)? = null
    ) {
        if (isListening) return
        
        scope.launch {
            val language = prefs.getLanguage()
            val useOffline = prefs.getUseOfflineStt()

            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, language)
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
                
                if (useOffline && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
                }
            }

            val listener = object : RecognitionListener {
                override fun onReadyForSpeech(params: Bundle?) {
                    Log.d("PickleSpeech", "One-shot: Ready for speech")
                }

                override fun onBeginningOfSpeech() {
                    Log.d("PickleSpeech", "One-shot: Beginning of speech")
                }

                override fun onRmsChanged(rmsdB: Float) {}

                override fun onBufferReceived(buffer: ByteArray?) {}

                override fun onEndOfSpeech() {
                    Log.d("PickleSpeech", "One-shot: End of speech")
                }

                override fun onError(error: Int) {
                    val errorMsg = when (error) {
                        SpeechRecognizer.ERROR_AUDIO -> "Audio recording error"
                        SpeechRecognizer.ERROR_CLIENT -> "Client side error"
                        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Insufficient permissions"
                        SpeechRecognizer.ERROR_NETWORK -> "Network error"
                        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> "Network timeout"
                        SpeechRecognizer.ERROR_NO_MATCH -> "Tidak mendengar apa-apa, coba lagi"
                        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "Recognizer busy"
                        SpeechRecognizer.ERROR_SERVER -> "Server error"
                        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> "Waktu habis, tidak ada suara"
                        else -> "Unknown error: $error"
                    }
                    Log.e("PickleSpeech", "One-shot error: $errorMsg")
                    isListening = false
                    onError(errorMsg)
                }

                override fun onResults(results: Bundle) {
                    val matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { text ->
                        Log.d("PickleSpeech", "One-shot result: $text")
                        isListening = false
                        onResult(text)
                    } ?: run {
                        isListening = false
                        onError("Tidak ada hasil pengenalan")
                    }
                }

                override fun onPartialResults(partialResults: Bundle) {
                    val matches = partialResults.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    matches?.firstOrNull()?.let { text ->
                        onPartialResult?.invoke(text)
                    }
                }

                override fun onEvent(eventType: Int, params: Bundle?) {}
            }

            isListening = true
            currentListener = listener
            speechRecognizer?.setRecognitionListener(listener)
            speechRecognizer?.startListening(intent)
        }
    }

    fun stopListening() {
        if (!isListening) return
        isListening = false
        speechRecognizer?.stopListening()
        speechRecognizer?.setRecognitionListener(defaultListener)
        currentListener = null
        Log.d("PickleSpeech", "Stopped listening")
    }

    fun isCurrentlyListening(): Boolean = isListening

    fun isAvailable(): Boolean = SpeechRecognizer.isRecognitionAvailable(context)

    fun getSupportedLanguages(): List<String> {
        return listOf("id-ID", "en-US", "en-GB", "ja-JP", "ko-KR", "zh-CN", "zh-TW")
    }

    fun destroy() {
        stopListening()
        speechRecognizer?.destroy()
        speechRecognizer = null
        resultChannel.close()
        errorChannel.close()
        Log.d("PickleSpeech", "SpeechRecognizer destroyed")
    }
}