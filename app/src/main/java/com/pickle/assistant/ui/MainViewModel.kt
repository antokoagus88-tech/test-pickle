package com.pickle.assistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickle.assistant.PickleApp
import com.pickle.assistant.config.PreferencesRepository
import com.pickle.assistant.service.NotificationListener
import com.pickle.assistant.stt.SpeechManager
import com.pickle.assistant.tts.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class MainViewModel : ViewModel() {

    private val prefs: PreferencesRepository = PickleApp.getContext().preferencesRepository
    private val speechManager = SpeechManager.getInstance()
    private val ttsManager = TtsManager.getInstance()

    var notificationListenerEnabled by mutableStateOf(false)
        private set

    val isListening = MutableStateFlow(false)
    val lastRecognizedText = MutableStateFlow("")
    val lastError = MutableStateFlow<String?>(null)
    val ttsVoices = MutableStateFlow<List<TtsManager.VoiceInfo>>(emptyList())
    val selectedVoiceName = MutableStateFlow<String?>(null)
    val apiConfigured = MutableStateFlow(false)

    init {
        loadInitialState()
    }

    private fun loadInitialState() {
        viewModelScope.launch {
            notificationListenerEnabled = NotificationListener.isNotificationListenerEnabled(PickleApp.getContext())
            apiConfigured = prefs.getApiKey() != null
            ttsManager.addInitListener { success ->
                if (success) {
                    ttsVoices.value = ttsManager.getAvailableVoices()
                    selectedVoiceName.value = ttsManager.getAvailableVoices().firstOrNull { it.isCurrent }?.name
                }
            }
        }
    }

    fun refreshState() {
        notificationListenerEnabled = NotificationListener.isNotificationListenerEnabled(PickleApp.getContext())
        apiConfigured = prefs.getApiKey() != null
        ttsVoices.value = ttsManager.getAvailableVoices()
    }

    fun startListening(onResult: (String) -> Unit, onError: (String) -> Unit) {
        if (isListening.value) return
        isListening.value = true
        lastError.value = null
        
        speechManager.startListening(
            onResult = { text ->
                lastRecognizedText.value = text
                isListening.value = false
                onResult(text)
            },
            onError = { error ->
                lastError.value = error
                isListening.value = false
                onError(error)
            }
        )
    }

    fun stopListening() {
        speechManager.stopListening()
        isListening.value = false
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        ttsManager.speak(text, onComplete)
    }

    fun stopSpeaking() {
        ttsManager.stop()
    }

    fun setVoice(voiceName: String) {
        ttsManager.setVoiceByName(voiceName)
        selectedVoiceName.value = voiceName
        ttsVoices.value = ttsManager.getAvailableVoices()
    }

    fun testTts() {
        ttsManager.speak("Halo, ini adalah tes suara Pickle Assistant")
    }
}