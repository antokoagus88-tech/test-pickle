package com.pickle.assistant.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.Mic
import androidx.compose.material3.icons.filled.MicOff
import androidx.compose.material3.icons.filled.Settings
import androidx.compose.material3.icons.filled.VolumeUp
import androidx.compose.material3.icons.filled.VolumeOff
import androidx.compose.material3.icons.filled.CheckCircle
import androidx.compose.material3.icons.filled.CloseCircle
import androidx.compose.material3.icons.filled.Info
import androidx.compose.material3.icons.filled.Send
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: MainViewModel,
    confirmFlow: ConfirmFlow,
    onOpenNotificationSettings: () -> Unit,
    onRequestPermissions: () -> Unit
) {
    val context = LocalContext.current
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val lastText by viewModel.lastRecognizedText.collectAsStateWithLifecycle()
    val lastError by viewModel.lastError.collectAsStateWithLifecycle()
    val listenerEnabled by remember { mutableStateOf(viewModel.notificationListenerEnabled) }
    val apiConfigured by viewModel.apiConfigured.collectAsStateWithLifecycle()
    val showSettings by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pickle Assistant", fontWeight = FontWeight.Bold) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                actions = {
                    IconButton(onClick = { showSettings = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            Column(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                StatusCard(
                    title = "Notifikasi Listener",
                    subtitle = "Akses notifikasi WA, Telegram, SMS, dll",
                    isEnabled = listenerEnabled,
                    onClick = { onOpenNotificationSettings() },
                    icon = if (listenerEnabled) Icons.Default.CheckCircle else Icons.Default.CloseCircle,
                    iconColor = if (listenerEnabled) Color.Green else Color.Red
                )

                StatusCard(
                    title = "API LLM Terkonfigurasi",
                    subtitle = "NVIDIA NIM / OpenAI / Groq / Ollama",
                    isEnabled = apiConfigured,
                    onClick = { showSettings = true },
                    icon = if (apiConfigured) Icons.Default.CheckCircle else Icons.Default.CloseCircle,
                    iconColor = if (apiConfigured) Color.Green else Color.Red
                )

                MicButton(
                    isListening = isListening,
                    onClick = {
                        if (isListening) {
                            viewModel.stopListening()
                        } else {
                            viewModel.startListening(
                                onResult = { text ->
                                    confirmFlow.processVoiceCommand(
                                        voiceText = text,
                                        onResult = { response ->
                                            viewModel.speak(response)
                                        },
                                        onError = { error ->
                                            viewModel.speak("Error: $error")
                                        }
                                    )
                                },
                                onError = { error ->
                                }
                            )
                        }
                    }
                )

                if (lastText.isNotBlank()) {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            Text(
                                text = "Kamu: $lastText",
                                style = MaterialTheme.typography.bodyLarge,
                                maxLines = 3,
                                overflow = androidx.compose.ui.text.TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                lastError?.let { error ->
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                        colors = androidx.compose.material3.CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        )
                    ) {
                        androidx.compose.foundation.layout.Box(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Info, contentDescription = "Error", tint = MaterialTheme.colorScheme.onErrorContainer)
                                androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Error: $error",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer
                                )
                            }
                        }
                    }
                }

                QuickActionsRow(viewModel = viewModel)

                TtsTestRow(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun StatusCard(
    title: String,
    subtitle: String,
    isEnabled: Boolean,
    onClick: () -> Unit,
    icon: androidx.compose.material3.icons.filled.Icon,
    iconColor: Color
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        onClick = onClick,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = "", tint = iconColor)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
            androidx.compose.foundation.layout.Column(modifier = Modifier.weight(1f)) {
                Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text(text = subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Icon(
                androidx.compose.material3.icons.filled.ArrowForwardIos,
                contentDescription = "",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MicButton(
    isListening: Boolean,
    onClick: () -> Unit
) {
    val size = 120.dp
    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        if (isListening) {
            androidx.compose.animation.core.AnimatedVisibility(
                visible = true,
                enter = androidx.compose.animation.core.fadeIn() + androidx.compose.animation.core.expandHorizontally(),
                exit = androidx.compose.animation.core.fadeOut() + androidx.compose.animation.core.shrinkHorizontally()
            ) {
                androidx.compose.foundation.shape.CircleShape.background(Color.Red.copy(alpha = 0.1f))
            }
        }

        Button(
            onClick = onClick,
            modifier = Modifier.size(size),
            shape = androidx.compose.foundation.shape.CircleShape,
            colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                containerColor = if (isListening) Color.Red else MaterialTheme.colorScheme.primary,
                contentColor = if (isListening) Color.White else MaterialTheme.colorScheme.onPrimary
            )
        ) {
            Icon(
                imageVector = if (isListening) Icons.Default.MicOff else Icons.Default.Mic,
                contentDescription = if (isListening) "Berhenti mendengar" else "Mulai bicara",
                tint = if (isListening) Color.White else MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

@Composable
fun QuickActionsRow(viewModel: MainViewModel) {
    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        QuickActionButton(
            icon = Icons.Default.Send,
            label = "Test Kirim",
            onClick = { viewModel.speak("Tes kirim balasan") }
        )
        QuickActionButton(
            icon = Icons.Default.VolumeUp,
            label = "Test Suara",
            onClick = { viewModel.testTts() }
        )
    }
}

@Composable
fun QuickActionButton(icon: androidx.compose.material3.icons.filled.Icon, label: String, onClick: () -> Unit) {
    Card(
        modifier = Modifier.weight(1f).height(80.dp),
        onClick = onClick,
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        androidx.compose.foundation.layout.Column(
            modifier = Modifier.fillMaxSize().padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(icon, contentDescription = "", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(24.dp))
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(4.dp))
            Text(text = label, style = MaterialTheme.typography.labelLarge, textAlign = androidx.compose.ui.text.TextAlign.Center)
        }
    }
}

@Composable
fun TtsTestRow(viewModel: MainViewModel) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        androidx.compose.foundation.layout.Column(modifier = Modifier.padding(16.dp)) {
            Text("Test Text-to-Speech", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(8.dp))
            androidx.compose.foundation.layout.Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(onClick = { viewModel.testTts() }) {
                    Text("Putar Tes")
                }
                Button(onClick = { viewModel.stopSpeaking() }) {
                    Text("Berhenti")
                }
            }
        }
    }
}