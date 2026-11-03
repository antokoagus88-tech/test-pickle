package com.pickle.assistant.config

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.PreferencesKeys
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.mutations
import androidx.datastore.preferences.rx.preferencesDataStore
import androidx.datastore.rx.RxDataStore
import com.pickle.assistant.util.Companion.toPreferencesKey
import io.reactivex.rxjava3.core.Flowable
import io.reactivex.rxjava3.core.Single
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.rx3.toFlowable

object PrefKeys {
    val API_KEY = stringPreferencesKey("api_key")
    val BASE_URL = stringPreferencesKey("base_url")
    val MODEL = stringPreferencesKey("model")
    val PROTOCOL = stringPreferencesKey("protocol")
    val SELECTED_VOICE = stringPreferencesKey("selected_voice")
    val VOICE_GENDER = stringPreferencesKey("voice_gender")
    val TARGET_APPS = stringSetPreferencesKey("target_apps")
    val AUTO_CONFIRM = booleanPreferencesKey("auto_confirm")
    val LANGUAGE = stringPreferencesKey("language")
    val USE_OFFLINE_STT = booleanPreferencesKey("use_offline_stt")
    val NOTIFICATION_SERVICE_ENABLED = booleanPreferencesKey("notification_service_enabled")
}

class PreferencesRepository(
    private val dataStore: RxDataStore<Preferences>
) {

    val apiKey: Flow<String?> = dataStore.data
        .toFlowable()
        .map { it[PrefKeys.API_KEY] }
        .distinctUntilChanged()
        .asFlow()

    val baseUrl: Flow<String> = dataStore.data
        .toFlowable()
        .map { it[PrefKeys.BASE_URL] ?: "https://integrate.api.nvidia.com/v1" }
        .distinctUntilChanged()
        .asFlow()

    val model: Flow<String> = dataStore.data
        .toFlowable()
        .map { it[PrefKeys.MODEL] ?: "nvidia/nemotron-3-ultra-550b-a55b" }
        .distinctUntilChanged()
        .asFlow()

    val protocol: Flow<String> = dataStore.data
        .toFlowable()
        .map { it[PrefKeys.PROTOCOL] ?: "openai" }
        .distinctUntilChanged()
        .asFlow()

    val selectedVoice: Flow<String?> = dataStore.data
        .toFlowable()
        .map { it[PrefKeys.SELECTED_VOICE] }
        .distinctUntilChanged()
        .asFlow()

    val voiceGender: Flow<String> = dataStore.data
        .toFlowable()
        .map { it[PrefKeys.VOICE_GENDER] ?: "female" }
        .distinctUntilChanged()
        .asFlow()

    val targetApps: Flow<Set<String>> = dataStore.data
        .toFlowable()
        .map { it[PrefKeys.TARGET_APPS] ?: setOf("com.whatsapp", "com.whatsapp.w4b", "com.facebook.orca", "org.telegram.messenger", "jp.naver.line.android", "com.google.android.apps.messaging", "com.android.email") }
        .distinctUntilChanged()
        .asFlow()

    val autoConfirm: Flow<Boolean> = dataStore.data
        .toFlowable()
        .map { it[PrefKeys.AUTO_CONFIRM] ?: false }
        .distinctUntilChanged()
        .asFlow()

    val language: Flow<String> = dataStore.data
        .toFlowable()
        .map { it[PrefKeys.LANGUAGE] ?: "id-ID" }
        .distinctUntilChanged()
        .asFlow()

    val useOfflineStt: Flow<Boolean> = dataStore.data
        .toFlowable()
        .map { it[PrefKeys.USE_OFFLINE_STT] ?: false }
        .distinctUntilChanged()
        .asFlow()

    val notificationServiceEnabled: Flow<Boolean> = dataStore.data
        .toFlowable()
        .map { it[PrefKeys.NOTIFICATION_SERVICE_ENABLED] ?: false }
        .distinctUntilChanged()
        .asFlow()

    suspend fun setApiKey(key: String?) = dataStore.updateData { it[PrefKeys.API_KEY] = key }
    suspend fun setBaseUrl(url: String) = dataStore.updateData { it[PrefKeys.BASE_URL] = url }
    suspend fun setModel(model: String) = dataStore.updateData { it[PrefKeys.MODEL] = model }
    suspend fun setProtocol(protocol: String) = dataStore.updateData { it[PrefKeys.PROTOCOL] = protocol }
    suspend fun setSelectedVoice(voice: String?) = dataStore.updateData { it[PrefKeys.SELECTED_VOICE] = voice }
    suspend fun setVoiceGender(gender: String) = dataStore.updateData { it[PrefKeys.VOICE_GENDER] = gender }
    suspend fun setTargetApps(apps: Set<String>) = dataStore.updateData { it[PrefKeys.TARGET_APPS] = apps }
    suspend fun setAutoConfirm(enabled: Boolean) = dataStore.updateData { it[PrefKeys.AUTO_CONFIRM] = enabled }
    suspend fun setLanguage(lang: String) = dataStore.updateData { it[PrefKeys.LANGUAGE] = lang }
    suspend fun setUseOfflineStt(enabled: Boolean) = dataStore.updateData { it[PrefKeys.USE_OFFLINE_STT] = enabled }
    suspend fun setNotificationServiceEnabled(enabled: Boolean) = dataStore.updateData { it[PrefKeys.NOTIFICATION_SERVICE_ENABLED] = enabled }

    suspend fun getApiKey(): String? = dataStore.data.firstOrNull()?.let { it[PrefKeys.API_KEY] }
    suspend fun getBaseUrl(): String = dataStore.data.firstOrNull()?.let { it[PrefKeys.BASE_URL] ?: "https://integrate.api.nvidia.com/v1" } ?: "https://integrate.api.nvidia.com/v1"
    suspend fun getModel(): String = dataStore.data.firstOrNull()?.let { it[PrefKeys.MODEL] ?: "nvidia/nemotron-3-ultra-550b-a55b" } ?: "nvidia/nemotron-3-ultra-550b-a55b"
    suspend fun getProtocol(): String = dataStore.data.firstOrNull()?.let { it[PrefKeys.PROTOCOL] ?: "openai" } ?: "openai"
    suspend fun getSelectedVoice(): String? = dataStore.data.firstOrNull()?.let { it[PrefKeys.SELECTED_VOICE] }
    suspend fun getVoiceGender(): String = dataStore.data.firstOrNull()?.let { it[PrefKeys.VOICE_GENDER] ?: "female" } ?: "female"
    suspend fun getTargetApps(): Set<String> = dataStore.data.firstOrNull()?.let { it[PrefKeys.TARGET_APPS] ?: defaultTargetApps() } ?: defaultTargetApps()
    suspend fun getAutoConfirm(): Boolean = dataStore.data.firstOrNull()?.let { it[PrefKeys.AUTO_CONFIRM] ?: false } ?: false
    suspend fun getLanguage(): String = dataStore.data.firstOrNull()?.let { it[PrefKeys.LANGUAGE] ?: "id-ID" } ?: "id-ID"
    suspend fun getUseOfflineStt(): Boolean = dataStore.data.firstOrNull()?.let { it[PrefKeys.USE_OFFLINE_STT] ?: false } ?: false
    suspend fun getNotificationServiceEnabled(): Boolean = dataStore.data.firstOrNull()?.let { it[PrefKeys.NOTIFICATION_SERVICE_ENABLED] ?: false } ?: false

    private fun defaultTargetApps(): Set<String> = setOf(
        "com.whatsapp",
        "com.whatsapp.w4b",
        "com.facebook.orca",
        "org.telegram.messenger",
        "jp.naver.line.android",
        "com.google.android.apps.messaging",
        "com.android.email"
    )
}

private fun String.toPreferencesKey(): Preferences.Key<String> = stringPreferencesKey(this)
private fun String.toPreferencesKeySet(): Preferences.Key<Set<String>> = stringSetPreferencesKey(this)
private fun String.toPreferencesKeyBoolean(): Preferences.Key<Boolean> = booleanPreferencesKey(this)