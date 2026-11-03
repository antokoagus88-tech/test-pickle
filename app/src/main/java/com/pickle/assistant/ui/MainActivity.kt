package com.pickle.assistant.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.lifecycle.lifecycleScope
import com.pickle.assistant.PickleApp
import com.pickle.assistant.R
import com.pickle.assistant.service.NotificationListener
import com.pickle.assistant.util.ConfirmFlow
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()
    private val confirmFlow = ConfirmFlow(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            PickleAssistantTheme {
                MainScreen(
                    viewModel = viewModel,
                    confirmFlow = confirmFlow,
                    onOpenNotificationSettings = { openNotificationListenerSettings() },
                    onRequestPermissions = { requestPermissions() }
                )
            }
        }

        checkNotificationListener()
    }

    override fun onResume() {
        super.onResume()
        viewModel.refreshState()
    }

    private fun checkNotificationListener() {
        val enabled = NotificationListener.isNotificationListenerEnabled(this)
        viewModel.notificationListenerEnabled = enabled
    }

    private fun openNotificationListenerSettings() {
        NotificationListener.openNotificationListenerSettings(this)
    }

    private fun requestPermissions() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            permissions += android.Manifest.permission.POST_NOTIFICATIONS
        }
        permissions += android.Manifest.permission.RECORD_AUDIO
        
        val toRequest = permissions.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (toRequest.isNotEmpty()) {
            requestPermissions(toRequest.toTypedArray(), 1001)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 1001) {
            viewModel.refreshState()
        }
    }
}