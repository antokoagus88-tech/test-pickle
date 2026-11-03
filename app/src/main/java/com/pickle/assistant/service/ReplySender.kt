package com.pickle.assistant.service

import android.app.Notification
import android.app.PendingIntent
import android.app.RemoteInput
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.core.app.NotificationCompat
import com.pickle.assistant.PickleApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class ReplySender {

    private val scope = CoroutineScope(Dispatchers.IO)
    private val context: Context = PickleApp.getContext()

    fun sendReply(
        sbn: StatusBarNotification,
        replyText: String,
        onResult: (Boolean, String?) -> Unit
    ) {
        scope.launch {
            try {
                val notification = sbn.notification
                val actions = notification.actions ?: emptyArray()

                var replyAction: Notification.Action? = null
                for (action in actions) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                        val remoteInputs = action.remoteInputs
                        if (remoteInputs != null && remoteInputs.size > 0) {
                            replyAction = action
                            break
                        }
                    } else {
                        val remoteInputs = RemoteInput.getRemoteInputsFromAction(action)
                        if (remoteInputs != null && remoteInputs.size > 0) {
                            replyAction = action
                            break
                        }
                    }
                }

                if (replyAction == null) {
                    onResult(false, "No reply action found in notification")
                    return@launch
                }

                val remoteInputs: Array<RemoteInput>?
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    remoteInputs = replyAction.remoteInputs
                } else {
                    remoteInputs = RemoteInput.getRemoteInputsFromAction(replyAction)
                }

                if (remoteInputs == null || remoteInputs.isEmpty()) {
                    onResult(false, "No RemoteInput found in reply action")
                    return@launch
                }

                val remoteInputKey = remoteInputs[0].resultKey

                val resultsBundle = Bundle()
                resultsBundle.putCharSequence(remoteInputKey, replyText)

                val sendIntent = replyAction.actionIntent
                if (sendIntent == null) {
                    onResult(false, "No PendingIntent found in reply action")
                    return@launch
                }

                try {
                    val filledIntent = Intent()
                    RemoteInput.addResultsToIntent(remoteInputs, sendIntent, filledIntent)
                    
                    sendIntent.send(context, 0, filledIntent)
                    
                    Log.d("PickleReply", "Reply sent successfully to ${sbn.packageName}")
                    onResult(true, null)
                } catch (e: Exception) {
                    Log.e("PickleReply", "Failed to send reply", e)
                    onResult(false, "Failed to send: ${e.message}")
                }

            } catch (e: Exception) {
                Log.e("PickleReply", "Error in sendReply", e)
                onResult(false, "Error: ${e.message}")
            }
        }
    }

    fun hasReplyAction(sbn: StatusBarNotification): Boolean {
        val notification = sbn.notification
        val actions = notification.actions ?: emptyArray()
        
        for (action in actions) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                if (action.remoteInputs != null && action.remoteInputs.size > 0) {
                    return true
                }
            } else {
                val remoteInputs = RemoteInput.getRemoteInputsFromAction(action)
                if (remoteInputs != null && remoteInputs.size > 0) {
                    return true
                }
            }
        }
        return false
    }

    fun getNotificationActions(sbn: StatusBarNotification): List<NotificationAction> {
        val notification = sbn.notification
        val actions = notification.actions ?: emptyArray()
        val result = mutableListOf<NotificationAction>()
        
        for (action in actions) {
            val hasRemoteInput = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                action.remoteInputs != null && action.remoteInputs.size > 0
            } else {
                RemoteInput.getRemoteInputsFromAction(action)?.size ?: 0 > 0
            }
            
            result.add(NotificationAction(
                title = action.title.toString(),
                hasReply = hasRemoteInput,
                actionIntent = action.actionIntent
            ))
        }
        
        return result
    }
}

data class NotificationAction(
    val title: String,
    val hasReply: Boolean,
    val actionIntent: PendingIntent?
)