package com.pickle.assistant.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.pickle.assistant.PickleApp
import com.pickle.assistant.R
import com.pickle.assistant.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class PickleForegroundService : Service() {

    private val scope = CoroutineScope(Dispatchers.Default)
    private var isListening = false
    private var notificationId = 1001
    private var channelId = "pickle_foreground_service"
    private val job = Job()

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        Log.d("PickleForeground", "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action ?: "START_LISTENING"
        
        when (action) {
            "START_LISTENING" -> startListening()
            "STOP_LISTENING" -> stopListening()
            "TOGGLE_LISTENING" -> {
                if (isListening) stopListening() else startListening()
            }
        }

        return START_STICKY
    }

    private fun startListening() {
        if (isListening) return
        
        isListening = true
        Log.d("PickleForeground", "Starting voice listening")

        showForegroundNotification("Pickle mendengarkan...", "Tap untuk berhenti")

        scope.launch {
        }
    }

    private fun stopListening() {
        if (!isListening) return
        
        isListening = false
        Log.d("PickleForeground", "Stopping voice listening")

        scope.launch {
        }

        stopForeground(true)
        stopSelf()
    }

    private fun showForegroundNotification(title: String, text: String) {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val stopIntent = Intent(this, PickleForegroundService::class.java).apply {
            action = "STOP_LISTENING"
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title)
            .setContentText(text)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .addAction(R.mipmap.ic_launcher, "Berhenti", stopPendingIntent)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE)
        } else {
            startForeground(notificationId, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Pickle Voice Assistant",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Persistent voice listening service"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        scope.cancel()
        stopListening()
        Log.d("PickleForeground", "Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        fun startService(context: Context) {
            val intent = Intent(context, PickleForegroundService::class.java).apply {
                action = "START_LISTENING"
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, PickleForegroundService::class.java).apply {
                action = "STOP_LISTENING"
            }
            context.startService(intent)
        }

        fun toggleService(context: Context) {
            val intent = Intent(context, PickleForegroundService::class.java).apply {
                action = "TOGGLE_LISTENING"
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}