package com.pickle.assistant.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.icons.Icons
import androidx.compose.material3.icons.filled.ArrowBack
import androidx.compose.material3.icons.filled.Check
import androidx.compose.material3.icons.filled.Delete
import androidx.compose.material3.icons.filled.Edit
import androidx.compose.material3.icons.filled.Language
import androidx.compose.material3.icons.filled.Mic
import androidx.compose.material3.icons.filled.Person
import androidx.compose.material3.icons.filled.Save
import androidx.compose.material3.icons.filled.Visibility
import androidx.compose.material3.icons.filled.VisibilityOff
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardOptions
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Pengaturan Pickle") },
                navigationIcon = {
                    androidx.compose.material3.IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Kembali")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surfaceContainer)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.padding(padding).fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection(title = "Konfigurasi LLM") {
                ApiKeyField(viewModel = viewModel)
                BaseUrlField(viewModel = viewModel)
                ModelField(viewModel = viewModel)
                ProtocolDropdown(viewModel = viewModel)
                TestConnectionButton(viewModel = viewModel)
            }

            SettingsSection(title = "Pengaturan Suara") {
                VoiceGenderDropdown(viewModel = viewModel)
                VoiceSelectionList(viewModel = viewModel)
                LanguageDropdown(viewModel = viewModel)
                OfflineSttSwitch(viewModel = viewModel)
                TestTtsButton(viewModel = viewModel)
            }

            SettingsSection(title = "Aplikasi Target Notifikasi") {
                TargetAppsList(viewModel = viewModel)
            }

            SettingsSection(title = "Perilaku") {
                AutoConfirmSwitch(viewModel = viewModel)
                NotificationServiceSwitch(viewModel = viewModel)
            }

            SettingsSection(title = "Tentang") {
                AboutCard()
            }
        }
    }
}

@Composable
fun SettingsSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        colors = androidx.compose.material3.CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = androidx.compose.ui.text.font.FontWeight.Bold)
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun ApiKeyField(viewModel: SettingsViewModel) {
    var apiKey by remember { mutableStateOf(viewModel.apiKey.value) }
    var showPassword by remember { mutableStateOf(false) }

    OutlinedTextField(
        value = apiKey,
        onValueChange = { newText ->
            apiKey = newText
            viewModel.updateApiKey(newText)
        },
        label = { Text("API Key") },
        placeholder = { Text("nvapi-... atau sk-...") },
        isError = apiKey.isNotBlank() && apiKey.length < 10,
        keyboardOptions = KeyboardOptions.Default,
        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
        trailingIcon = {
            IconButton(onClick = { showPassword = !showPassword }) {
                Icon(
                    if (showPassword) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                    contentDescription = if (showPassword) "Sembunyikan" else "Tampilkan"
                )
            }
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
fun BaseUrlField(viewModel: SettingsViewModel) {
    var baseUrl by remember { mutableStateOf(viewModel.baseUrl.value) }

    OutlinedTextField(
        value = baseUrl,
        onValueChange = { newText ->
            baseUrl = newText
            viewModel.updateBaseUrl(newText)
        },
        label = { Text("Base URL") },
        placeholder = { Text("https://integrate.api.nvidia.com/v1") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
fun ModelField(viewModel: SettingsViewModel) {
    var model by remember { mutableStateOf(viewModel.model.value) }

    OutlinedTextField(
        value = model,
        onValueChange = { newText ->
            model = newText
            viewModel.updateModel(newText)
        },
        label = { Text("Model ID") },
        placeholder = { Text("nvidia/nemotron-3-ultra-550b-a55b") },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@Composable
fun ProtocolDropdown(viewModel: SettingsViewModel) {
    val protocols = listOf("openai", "anthropic")
    val currentProtocol by viewModel.protocol.collectAsStateWithLifecycle()

    androidx.compose.material3.MenuDropdown(
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Protocol") },
        selectedValue = currentProtocol,
        options = protocols,
        onValueChange = { viewModel.updateProtocol(it) }
    )
}

@Composable
fun TestConnectionButton(viewModel: SettingsViewModel) {
    var testing by remember { mutableStateOf(false) }
    var result by remember { mutableStateOf<String?>(null) }

    Button(
        onClick = {
            testing = true
            result = null
            viewModel.testConnection { success, message ->
                testing = false
                result = message
            }
        },
        modifier = Modifier.fillMaxWidth(),
        enabled = !testing
    ) {
        if (testing) {
            androidx.compose.material3.CircularProgressIndicator(modifier = Modifier.size(20.dp))
            androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
            Text("Menguji...")
        } else {
            Text("Test Koneksi API")
        }
    }

    result?.let { msg ->
        Text(
            text = msg,
            style = MaterialTheme.typography.bodySmall,
            color = if (msg.contains("Berhasil")) Color.Green else Color.Red,
            modifier = Modifier.padding(top = 8.dp).fillMaxWidth()
        )
    }
}

@Composable
fun VoiceGenderDropdown(viewModel: SettingsViewModel) {
    val genders = listOf("female" to "Perempuan", "male" to "Laki-laki")
    val currentGender by viewModel.voiceGender.collectAsStateWithLifecycle()

    androidx.compose.material3.MenuDropdown(
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Jenis Suara") },
        selectedValue = genders.firstOrNull { it.first == currentGender }?.second ?: "Perempuan",
        options = genders.map { it.second },
        onValueChange = { label ->
            val gender = genders.first { it.second == label }.first
            viewModel.updateVoiceGender(gender)
        }
    )
}

@Composable
fun VoiceSelectionList(viewModel: SettingsViewModel) {
    val voices by viewModel.ttsVoices.collectAsStateWithLifecycle()
    val selectedVoice by viewModel.selectedVoiceName.collectAsStateWithLifecycle()

    if (voices.isEmpty()) {
        Text("Tidak ada suara tersedia. Pastikan TTS Google terinstall.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        voices.forEach { voice ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.updateSelectedVoice(voice.name) },
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = if (voice.isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Mic, contentDescription = "", tint = MaterialTheme.colorScheme.primary)
                    androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(text = voice.displayName, style = MaterialTheme.typography.bodyLarge, fontWeight = if (voice.isCurrent) androidx.compose.ui.text.font.FontWeight.Bold else androidx.compose.ui.text.font.FontWeight.Normal)
                        Text(text = "${voice.locale} • ${voice.qualityLabel} • ${voice.genderHint}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    if (voice.isCurrent) {
                        Icon(Icons.Default.Check, contentDescription = "Dipilih", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun LanguageDropdown(viewModel: SettingsViewModel) {
    val languages = listOf("id-ID" to "Indonesia", "en-US" to "English (US)", "en-GB" to "English (UK)", "ja-JP" to "日本語", "ko-KR" to "한국어", "zh-CN" to "中文(简体)", "zh-TW" to "中文(繁體)")
    val currentLang by viewModel.language.collectAsStateWithLifecycle()

    androidx.compose.material3.MenuDropdown(
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Bahasa Pengenalan Suara") },
        selectedValue = languages.firstOrNull { it.first == currentLang }?.second ?: "Indonesia",
        options = languages.map { it.second },
        onValueChange = { label ->
            val lang = languages.first { it.second == label }.first
            viewModel.updateLanguage(lang)
        }
    )
}

@Composable
fun OfflineSttSwitch(viewModel: SettingsViewModel) {
    val useOffline by viewModel.useOfflineStt.collectAsStateWithLifecycle()

    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("STT Offline (Prefer Offline)", style = MaterialTheme.typography.bodyLarge)
            Text("Gunakan pengenalan suara offline jika tersedia (lebih privat, tapi mungkin kurang akurat)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = useOffline, onCheckedChange = { viewModel.updateUseOfflineStt(it) })
    }
}

@Composable
fun TestTtsButton(viewModel: SettingsViewModel) {
    Button(onClick = { viewModel.testTts() }, modifier = Modifier.fillMaxWidth()) {
        Icon(Icons.Default.VolumeUp, contentDescription = "")
        androidx.compose.foundation.layout.Spacer(modifier = Modifier.width(8.dp))
        Text("Test Suara Saat Ini")
    }
}

@Composable
fun TargetAppsList(viewModel: SettingsViewModel) {
    val targetApps by viewModel.targetApps.collectAsStateWithLifecycle()
    val allApps = listOf(
        "com.whatsapp" to "WhatsApp",
        "com.whatsapp.w4b" to "WhatsApp Business",
        "com.facebook.orca" to "Messenger",
        "org.telegram.messenger" to "Telegram",
        "jp.naver.line.android" to "Line",
        "com.google.android.apps.messaging" to "Google Messages",
        "com.android.email" to "Email"
    )

    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        allApps.forEach { (pkg, name) ->
            val isSelected = targetApps.contains(pkg)
            Card(
                modifier = Modifier.fillMaxWidth(),
                onClick = { viewModel.toggleTargetApp(pkg, !isSelected) },
                colors = androidx.compose.material3.CardDefaults.cardColors(
                    containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                )
            ) {
                androidx.compose.foundation.layout.Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = name, style = MaterialTheme.typography.bodyLarge)
                    if (isSelected) {
                        Icon(Icons.Default.Check, contentDescription = "Dipilih", tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

@Composable
fun AutoConfirmSwitch(viewModel: SettingsViewModel) {
    val autoConfirm by viewModel.autoConfirm.collectAsStateWithLifecycle()

    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Kirim Otomatis Tanpa Konfirmasi", style = MaterialTheme.typography.bodyLarge)
            Text("Jika aktif, balasan akan dikirim langsung tanpa meminta konfirmasi suara (TIDAK DIREKOMENDASIKAN)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = autoConfirm, onCheckedChange = { viewModel.updateAutoConfirm(it) })
    }
}

@Composable
fun NotificationServiceSwitch(viewModel: SettingsViewModel) {
    val enabled by viewModel.notificationServiceEnabled.collectAsStateWithLifecycle()

    androidx.compose.foundation.layout.Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text("Layanan Notifikasi Aktif", style = MaterialTheme.typography.bodyLarge)
            Text("Aktifkan/matikan pemantauan notifikasi (butuh izin di Settings)", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = enabled, onCheckedChange = { viewModel.updateNotificationServiceEnabled(it) })
    }
}

@Composable
fun AboutCard() {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Versi", style = MaterialTheme.typography.bodyMedium)
            Text("1.0.0", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Build", style = MaterialTheme.typography.bodyMedium)
            Text("Debug", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        androidx.compose.foundation.layout.Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Sumber", style = MaterialTheme.typography.bodyMedium)
            Text("GitHub: github.com/username/pickle-android", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}