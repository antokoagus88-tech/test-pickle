package com.pickle.assistant.service

import android.annotation.SuppressLint
import android.app.Notification
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.content.ContextCompat
import com.pickle.assistant.PickleApp
import com.pickle.assistant.config.PreferencesRepository
import com.pickle.assistant.util.ConfirmFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class NotificationListener : NotificationListenerService() {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs by lazy { PickleApp.getContext().preferencesRepository }
    private val processedNotifications = ConcurrentHashMap<String, Long>()
    private val CLEANUP_THRESHOLD_MS = 10 * 60 * 1000

    override fun onCreate() {
        super.onCreate()
        Log.d("PickleNotification", "NotificationListener created")
    }

    @SuppressLint("UnsafeProtectedBroadcastReceiver")
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        scope.launch {
            val pkg = sbn.packageName
            val targetApps = prefs.getTargetApps()
            
            if (!targetApps.contains(pkg)) return@launch

            if (!prefs.getNotificationServiceEnabled()) return@launch

            val notification = sbn.notification
            val extras = notification.extras

            val title = extras.getString(Notification.EXTRA_TITLE) ?: ""
            val text = extras.getString(Notification.EXTRA_TEXT) ?: ""
            val bigText = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString() ?: ""
            val messages = extractMessages(extras)

            val content = if (bigText.isNotBlank()) bigText else if (text.isNotBlank()) text else title
            if (content.isBlank()) return@launch

            val key = "$pkg-${sbn.id}-${content.hashCode()}"
            val now = System.currentTimeMillis()
            
            if (processedNotifications.containsKey(key)) {
                val lastTime = processedNotifications[key] ?: 0
                if (now - lastTime < CLEANUP_THRESHOLD_MS) {
                    return@launch
                }
            }
            processedNotifications[key] = now

            cleanupOldEntries(now)

            val notificationData = NotificationData(
                packageName = pkg,
                tag = sbn.tag,
                id = sbn.id,
                title = title,
                content = content,
                messages = messages,
                timestamp = sbn.postTime,
                notification = notification,
                sbn = sbn
            )

            processNotification(notificationData)
        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        val key = "${sbn.packageName}-${sbn.id}"
        processedNotifications.remove(key)
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.d("PickleNotification", "Notification listener connected")
        sendBroadcast(Intent("com.pickle.assistant.NOTIFICATION_LISTENER_CONNECTED"))
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w("PickleNotification", "Notification listener disconnected")
        sendBroadcast(Intent("com.pickle.assistant.NOTIFICATION_LISTENER_DISCONNECTED"))
    }

    private fun extractMessages(extras: Bundle): List<MessageItem> {
        val messages = mutableListOf<MessageItem>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            val persons = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
            persons?.forEach { parcel ->
                if (parcel is Bundle) {
                    val sender = parcel.getString(Notification.EXTRA_MESSAGING_PERSON) ?: "Unknown"
                    val text = parcel.getCharSequence(Notification.EXTRA_MESSAGING_TEXT)?.toString() ?: ""
                    val timestamp = parcel.getLong(Notification.EXTRA_MESSAGING_TIMESTAMP, 0)
                    if (text.isNotBlank()) {
                        messages.add(MessageItem(sender, text, timestamp))
                    }
                }
            }
        }
        
        return messages
    }

    private fun processNotification(data: NotificationData) {
        Log.d("PickleNotification", "New notification from ${data.packageName}: ${data.content}")
        
        val intent = Intent("com.pickle.assistant.NEW_NOTIFICATION").apply {
            putExtra("package_name", data.packageName)
            putExtra("title", data.title)
            putExtra("content", data.content)
            putExtra("timestamp", data.timestamp)
            putExtra("notification_id", data.id)
            putExtra("notification_tag", data.tag ?: "")
        }
        sendBroadcast(intent)
    }

    private fun cleanupOldEntries(now: Long) {
        val keysToRemove = processedNotifications.entries
            .filter { now - it.value > CLEANUP_THRESHOLD_MS }
            .map { it.key }
            .toList()
        keysToRemove.forEach { processedNotifications.remove(it) }
    }

    companion object {
        fun isNotificationListenerEnabled(context: Context): Boolean {
            val flat = android.provider.Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: ""
            val componentName = android.content.ComponentName(context, NotificationListener::class.java)
            return flat.contains(componentName.flattenToString())
        }

        fun openNotificationListenerSettings(context: Context) {
            val intent = Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS")
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }
    }
}

data class NotificationData(
    val packageName: String,
    val tag: String?,
    val id: Int,
    val title: String,
    val content: String,
    val messages: List<MessageItem>,
    val timestamp: Long,
    val notification: Notification,
    val sbn: StatusBarNotification
)

data class MessageItem(
    val sender: String,
    val text: String,
    val timestamp: Long
)