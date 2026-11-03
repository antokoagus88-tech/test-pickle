package com.pickle.assistant.util

import android.content.Context
import com.pickle.assistant.PickleApp
import com.pickle.assistant.config.PreferencesRepository
import com.pickle.assistant.llm.ApiClient
import com.pickle.assistant.llm.ChatMessage
import com.pickle.assistant.service.NotificationListener
import com.pickle.assistant.service.ReplySender
import android.service.notification.StatusBarNotification
import com.pickle.assistant.stt.SpeechManager
import com.pickle.assistant.tts.TtsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class ConfirmFlow(
    private val context: Context = PickleApp.getContext()
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs by lazy { PickleApp.getContext().preferencesRepository }
    private val apiClient by lazy { ApiClient(prefs) }
    private val speechManager = SpeechManager.getInstance(context)
    private val ttsManager = TtsManager.getInstance(context)
    private val replySender = ReplySender()

    private val pendingConfirmations = ConcurrentHashMap<String, PendingConfirmation>()

    data class PendingConfirmation(
        val id: String,
        val notification: StatusBarNotification,
        val proposedReply: String,
        val onConfirm: (Boolean) -> Unit
    )

    fun processVoiceCommand(
        voiceText: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        scope.launch {
            try {
                val systemPrompt = buildSystemPrompt()
                
                val messages = listOf(
                    ChatMessage(role = "system", content = systemPrompt),
                    ChatMessage(role = "user", content = voiceText)
                )

                val result = apiClient.sendChatRequest(messages)
                
                result.onSuccess { response ->
                    response.choices.firstOrNull()?.message?.let { msg ->
                        msg.tool_calls?.firstOrNull()?.let { toolCall ->
                            handleToolCall(toolCall, msg.content ?: "", onResult, onError)
                            return@onSuccess
                        }
                        
                        msg.content?.let { onResult(it) } ?: onError("Tidak ada respons dari asisten")
                    }
                }.onFailure { e ->
                    onError("Error: ${e.message}")
                }
            } catch (e: Exception) {
                onError("Error memproses perintah: ${e.message}")
            }
        }
    }

    private fun buildSystemPrompt(): String {
        return """Kamu adalah Pickle, asisten suara pribadi di Android.
Fungsi utama: bantu pengguna balas/kirim pesan di WhatsApp, Telegram, Line, SMS, Email, dll.

ATURAN:
1. Selalu konfirmasi ke pengguna SEBELUM mengirim balasan apa pun
2. Gunakan tool send_reply hanya setelah user menyetujui
3. Jawab dalam bahasa Indonesia, singkat dan natural
4. Jika user minta baca notifikasi, gunakan tool get_recent_notifications
5. Jika user minta baca isi notifikasi spesifik, gunakan read_notification_content
6. Jangan pernah mengirim balasan tanpa konfirmasi eksplisit

TOOLS TERSEDIA:
- send_reply: kirim balasan ke notifikasi (butuh package_name, notification_id, reply_text)
- get_recent_notifications: lihat notifikasi terbaru
- read_notification_content: baca isi lengkap notifikasi

CONTOH ALUR:
User: "Balas wa dari budi"
Kamu: [get_recent_notifications -> cari notifikasi WA dari Budi] -> "Ada pesan dari Budi: 'Makan siang?' Balas apa?"
User: "Sudah makan, thanks"
Kamu: [Konfirmasi: 'Kirim: Sudah makan, thanks ke Budi?'] -> User: "Ya" -> [send_reply] -> "Terkirim"
"""
    }

    private fun handleToolCall(
        toolCall: com.pickle.assistant.llm.ToolCall,
        assistantResponse: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        when (toolCall.function.name) {
            "send_reply" -> {
                val args = parseJson(toolCall.function.arguments)
                val packageName = args["package_name"] as? String
                val notificationId = (args["notification_id"] as? Number)?.toInt()
                val notificationTag = args["notification_tag"] as? String
                val replyText = args["reply_text"] as? String

                if (packageName == null || notificationId == null || replyText == null) {
                    onError("Data balasan tidak lengkap")
                    return
                }

                val sbn = findNotification(packageName, notificationId, notificationTag)
                if (sbn == null) {
                    onError("Notifikasi tidak ditemukan (mungkin sudah dihapus)")
                    return
                }

                if (!replySender.hasReplyAction(sbn)) {
                    onError("Notifikasi ini tidak mendukung balas inline")
                    return
                }

                val confirmationId = "${packageName}_${notificationId}_${System.currentTimeMillis()}"
                val pending = PendingConfirmation(
                    id = confirmationId,
                    notification = sbn,
                    proposedReply = replyText,
                    onConfirm = { confirmed ->
                        if (confirmed) {
                            sendConfirmedReply(sbn, replyText, onResult, onError)
                        } else {
                            onResult("Balasan dibatalkan")
                        }
                    }
                )
                pendingConfirmations[confirmationId] = pending

                val confirmText = "Mau kirim balasan ke ${getAppName(packageName)}: \"$replyText\". Kirim?"
                ttsManager.speak(confirmText) {
                    startConfirmationListening(confirmationId, onResult, onError)
                }
            }
            "get_recent_notifications" -> {
                onResult("Silakan buka aplikasi untuk lihat notifikasi terbaru")
            }
            "read_notification_content" -> {
                onResult("Fitur baca isi notifikasi akan segera hadir")
            }
            else -> {
                onError("Tool tidak dikenal: ${toolCall.function.name}")
            }
        }
    }

    private fun startConfirmationListening(
        confirmationId: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        speechManager.startListening(
            onResult = { text ->
                val lower = text.lowercase()
                val confirmed = lower.contains("ya") || lower.contains("oke") || lower.contains("kirim") || 
                               lower.contains("setuju") || lower.contains("yaa") || lower.contains("yes")
                val denied = lower.contains("tidak") || lower.contains("batal") || lower.contains("jangan") || 
                            lower.contains("no") || lower.contains("cancel")
                
                val pending = pendingConfirmations.remove(confirmationId)
                pending?.onConfirm(confirmed && !denied)
                
                if (!confirmed && !denied) {
                    ttsManager.speak("Maaf, tidak mengerti. Katakan 'ya' untuk kirim atau 'tidak' untuk batal.") {
                        startConfirmationListening(confirmationId, onResult, onError)
                    }
                }
            },
            onError = { err ->
                pendingConfirmations.remove(confirmationId)
                onError("Gagal mendengar konfirmasi: $err")
            }
        )
    }

    private fun sendConfirmedReply(
        sbn: StatusBarNotification,
        replyText: String,
        onResult: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        replySender.sendReply(sbn, replyText) { success, error ->
            if (success) {
                ttsManager.speak("Balasan terkirim")
                onResult("Balasan terkirim ke ${getAppName(sbn.packageName)}")
            } else {
                ttsManager.speak("Gagal kirim balasan")
                onError("Gagal kirim: $error")
            }
        }
    }

    private fun findNotification(packageName: String, id: Int, tag: String?): StatusBarNotification? {
        return null
    }

    private fun getAppName(packageName: String): String = when (packageName) {
        "com.whatsapp", "com.whatsapp.w4b" -> "WhatsApp"
        "com.facebook.orca" -> "Messenger"
        "org.telegram.messenger" -> "Telegram"
        "jp.naver.line.android" -> "Line"
        "com.google.android.apps.messaging" -> "Google Messages"
        "com.android.email" -> "Email"
        else -> packageName
    }

    private fun parseJson(json: String): Map<String, Any> {
        return try {
            kotlinx.serialization.json.Json { ignoreUnknownKeys = true }
                .decodeFromString<Map<String, Any>>(json)
        } catch (e: Exception) {
            emptyMap()
        }
    }
}