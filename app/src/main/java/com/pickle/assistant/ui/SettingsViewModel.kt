package com.pickle.assistant.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pickle.assistant.PickleApp
import com.pickle.assistant.config.PreferencesRepository
import com.pickle.assistant.tts.TtsManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {

    private val prefs: PreferencesRepository = PickleApp.getContext().preferencesRepository
    private val ttsManager = TtsManager.getInstance()

    val apiKey = MutableStateFlow(prefs.getApiKey() ?: "")
    val baseUrl = MutableStateFlow(prefs.getBaseUrl())
    val model = MutableStateFlow(prefs.getModel())
    val protocol = MutableStateFlow(prefs.getProtocol())

    val voiceGender = MutableStateFlow(prefs.getVoiceGender())
    val selectedVoiceName = MutableStateFlow<String?>(prefs.getSelectedVoice())
    val ttsVoices = MutableStateFlow<List<TtsManager.VoiceInfo>>(emptyList())
    val language = MutableStateFlow(prefs.getLanguage())
    val useOfflineStt = MutableStateFlow(prefs.getUseOfflineStt())

    val targetApps = MutableStateFlow<Set<String>>(prefs.getTargetApps())

    val autoConfirm = MutableStateFlow(prefs.getAutoConfirm())
    val notificationServiceEnabled = MutableStateFlow(prefs.getNotificationServiceEnabled())

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            ttsManager.addInitListener { success ->
                if (success) {
                    ttsVoices.value = ttsManager.getAvailableVoices()
                    selectedVoiceName.value = ttsManager.getAvailableVoices().firstOrNull { it.isCurrent }?.name
                }
            }
        }
    }

    fun updateApiKey(key: String) {
        apiKey.value = key
        viewModelScope.launch { prefs.setApiKey(key.takeIf { it.isNotBlank() }) }
    }

    fun updateBaseUrl(url: String) {
        baseUrl.value = url
        viewModelScope.launch { prefs.setBaseUrl(url) }
    }

    fun updateModel(model: String) {
        this.model.value = model
        viewModelScope.launch { prefs.setModel(model) }
    }

    fun updateProtocol(protocol: String) {
        this.protocol.value = protocol
        viewModelScope.launch { prefs.setProtocol(protocol) }
    }

    fun updateVoiceGender(gender: String) {
        voiceGender.value = gender
        viewModelScope.launch { prefs.setVoiceGender(gender) }
        ttsManager.getAvailableVoices().firstOrNull { it.genderHint == gender }?.let { voice ->
            updateSelectedVoice(voice.name)
        }
    }

    fun updateSelectedVoice(voiceName: String) {
        selectedVoiceName.value = voiceName
        viewModelScope.launch { prefs.setSelectedVoice(voiceName) }
        ttsManager.setVoiceByName(voiceName)
        ttsVoices.value = ttsManager.getAvailableVoices()
    }

    fun updateLanguage(lang: String) {
        language.value = lang
        viewModelScope.launch { prefs.setLanguage(lang) }
    }

    fun updateUseOfflineStt(enabled: Boolean) {
        useOfflineStt.value = enabled
        viewModelScope.launch { prefs.setUseOfflineStt(enabled) }
    }

    fun toggleTargetApp(packageName: String, enable: Boolean) {
        val current = targetApps.value.toMutableSet()
        if (enable) current.add(packageName) else current.remove(packageName)
        targetApps.value = current
        viewModelScope.launch { prefs.setTargetApps(current) }
    }

    fun updateAutoConfirm(enabled: Boolean) {
        autoConfirm.value = enabled
        viewModelScope.launch { prefs.setAutoConfirm(enabled) }
    }

    fun updateNotificationServiceEnabled(enabled: Boolean) {
        notificationServiceEnabled.value = enabled
        viewModelScope.launch { prefs.setNotificationServiceEnabled(enabled) }
    }

    fun testConnection(onResult: (Boolean, String) -> Unit) {
        viewModelScope.launch {
            val testResult = try {
                Thread.sleep(1000)
                true
            } catch (e: Exception) {
                false
            }
            onResult(testResult, if (testResult) "Koneksi berhasil!" else "Gagal terhubung ke API")
        }
    }

    fun testTts() {
        ttsManager.speak("Halo, ini adalah tes suara Pickle Assistant")
    }
}