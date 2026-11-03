package com.pickle.assistant.tts

import android.content.Context
import android.os.Build
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.util.Log
import com.pickle.assistant.PickleApp
import com.pickle.assistant.config.PreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

class TtsManager private constructor(
    private val context: Context = PickleApp.getContext()
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs by lazy { PickleApp.getContext().preferencesRepository }
    private var tts: TextToSpeech? = null
    private var isInitialized = false
    private var availableVoices = ConcurrentHashMap<String, Voice>()
    private var currentVoice: Voice? = null
    private var initListeners = mutableListOf<(Boolean) -> Unit>()

    companion object {
        @Volatile private var INSTANCE: TtsManager? = null
        fun getInstance(context: Context = PickleApp.getContext()): TtsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: TtsManager(context).also { INSTANCE = it }
            }
        }
    }

    init {
        initializeTts()
    }

    private fun initializeTts() {
        tts = TextToSpeech(context, { status ->
            if (status == TextToSpeech.SUCCESS) {
                isInitialized = true
                loadAvailableVoices()
                restoreSelectedVoice()
                initListeners.forEach { it(true) }
                initListeners.clear()
                Log.d("PickleTTS", "TTS initialized successfully")
            } else {
                isInitialized = false
                initListeners.forEach { it(false) }
                initListeners.clear()
                Log.e("PickleTTS", "TTS initialization failed")
            }
        })
    }

    private fun loadAvailableVoices() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val voices = tts?.voices ?: return
            availableVoices.clear()
            voices.forEach { voice ->
                if (isSupportedLanguage(voice.locale)) {
                    availableVoices[voice.name] = voice
                    Log.d("PickleTTS", "Available voice: ${voice.name} (${voice.locale}) gender=${voice.quality}")
                }
            }
        }
    }

    private fun isSupportedLanguage(locale: Locale): Boolean {
        val lang = locale.language
        return lang == "id" || lang == "en"
    }

    private fun restoreSelectedVoice() {
        scope.launch {
            val savedVoiceName = prefs.getSelectedVoice()
            val savedGender = prefs.getVoiceGender()
            
            if (savedVoiceName != null && availableVoices.containsKey(savedVoiceName)) {
                setVoiceByName(savedVoiceName)
            } else if (savedGender.isNotBlank()) {
                findVoiceByGender(savedGender)?.let { setVoice(it) }
            } else {
                availableVoices.values.firstOrNull()?.let { setVoice(it) }
            }
        }
    }

    fun setVoice(voice: Voice) {
        tts?.let { it.voice = voice }
        currentVoice = voice
        scope.launch { prefs.setSelectedVoice(voice.name) }
        Log.d("PickleTTS", "Voice set to: ${voice.name}")
    }

    fun setVoiceByName(name: String) {
        availableVoices[name]?.let { setVoice(it) }
    }

    private fun findVoiceByGender(gender: String): Voice? {
        return availableVoices.values.firstOrNull { voice ->
            when (gender.lowercase()) {
                "male" -> voice.name.lowercase().contains("male") || 
                          voice.name.lowercase().contains("pria") ||
                          (voice.locale.language == "id" && voice.quality >= TextToSpeech.QUALITY_HIGH)
                "female" -> voice.name.lowercase().contains("female") || 
                            voice.name.lowercase().contains("wanita") ||
                            voice.name.lowercase().contains("perempuan") ||
                            (voice.locale.language == "id" && voice.quality >= TextToSpeech.QUALITY_HIGH)
                else -> false
            }
        }
    }

    fun getAvailableVoices(): List<VoiceInfo> {
        return availableVoices.values.map { voice ->
            VoiceInfo(
                name = voice.name,
                locale = voice.locale.toLanguageTag(),
                quality = voice.quality,
                isCurrent = voice == currentVoice,
                genderHint = guessGender(voice)
            )
        }.toList()
    }

    private fun guessGender(voice: Voice): String {
        val name = voice.name.lowercase()
        return when {
            name.contains("male") || name.contains("pria") -> "male"
            name.contains("female") || name.contains("wanita") || name.contains("perempuan") -> "female"
            else -> "unknown"
        }
    }

    fun speak(text: String, onComplete: (() -> Unit)? = null) {
        if (!isInitialized || text.isBlank()) {
            onComplete?.invoke()
            return
        }

        scope.launch(Dispatchers.Main) {
            val params = android.os.Bundle().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    putString(TextToSpeech.Engine.KEY_PARAM_UTTERANCE_ID, "pickle_${System.currentTimeMillis()}")
                }
            }

            val result = tts?.speak(text, TextToSpeech.QUEUE_FLUSH, params, "pickle_utterance")
            
            if (result != TextToSpeech.SUCCESS) {
                Log.e("PickleTTS", "Speak failed: $result")
                onComplete?.invoke()
            }
        }
    }

    fun stop() {
        tts?.stop()
    }

    fun isSpeaking(): Boolean = tts?.isSpeaking == true

    fun setLanguage(locale: Locale): Boolean {
        return tts?.setLanguage(locale) == TextToSpeech.LANG_AVAILABLE
    }

    fun setLanguage(languageTag: String): Boolean {
        return setLanguage(Locale.forLanguageTag(languageTag))
    }

    fun addInitListener(listener: (Boolean) -> Unit) {
        if (isInitialized) {
            listener(true)
        } else {
            initListeners.add(listener)
        }
    }

    fun shutdown() {
        stop()
        tts?.shutdown()
        tts = null
        isInitialized = false
        availableVoices.clear()
        currentVoice = null
    }

    data class VoiceInfo(
        val name: String,
        val locale: String,
        val quality: Int,
        val isCurrent: Boolean,
        val genderHint: String
    ) {
        val displayName: String
            get() = "$name (${locale.split("-").first().uppercase()})"
        val qualityLabel: String
            get() = when (quality) {
                TextToSpeech.QUALITY_HIGH -> "Tinggi"
                TextToSpeech.QUALITY_NORMAL -> "Normal"
                TextToSpeech.QUALITY_LOW -> "Rendah"
                else -> "Tidak diketahui"
            }
    }
}