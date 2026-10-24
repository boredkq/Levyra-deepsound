package com.luc4n3x.levyra.ui.yandex

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Logout
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.luc4n3x.levyra.data.yandex.*
import com.luc4n3x.levyra.ui.components.LevyraBackButton
import com.luc4n3x.levyra.ui.theme.LevyraCyan
import com.luc4n3x.levyra.ui.theme.LevyraMuted
import com.luc4n3x.levyra.ui.theme.LevyraText
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
internal fun YandexMusicLibraryLink(account: YandexAccount?, onClick: () -> Unit) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        shape = MaterialTheme.shapes.extraLarge
    ) {
        Row(
            Modifier.fillMaxWidth().padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            YandexAvatar(account, Modifier.size(48.dp))
            Column(Modifier.weight(1f)) {
                Text("Яндекс Музыка", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (account?.isConnected == true) account.displayName else "Подключить аккаунт для 320 kbps",
                    style = MaterialTheme.typography.bodyMedium,
                    color = LevyraMuted
                )
            }
            Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun YandexMusicAccountScreen(
    sessionStore: YandexMusicSessionStore,
    client: YandexMusicClient = remember { YandexMusicClient() },
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val account by sessionStore.accountFlow.collectAsStateWithLifecycle()
    var token by remember { mutableStateOf(sessionStore.loadToken()) }

    var deviceCodeState by remember { mutableStateOf<YandexDeviceCode?>(null) }
    var isPolling by remember { mutableStateOf(false) }
    var isLoadingCode by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var manualTokenInput by remember { mutableStateOf("") }
    var showManualInput by rememberSaveable { mutableStateOf(false) }

    // Initial check for account details if token exists
    LaunchedEffect(token) {
        if (!token.isNullOrBlank() && (account == null || !account!!.isConnected)) {
            try {
                val acc = client.fetchAccount(token!!)
                sessionStore.saveAccount(acc)
            } catch (e: Exception) {
                // Token may be invalid or expired
            }
        }
    }

    // Polling loop when device code is generated
    LaunchedEffect(deviceCodeState) {
        val currentCode = deviceCodeState ?: return@LaunchedEffect
        isPolling = true
        errorMessage = null
        while (isActive && isPolling) {
            delay((currentCode.interval.coerceAtLeast(3) * 1000).toLong())
            try {
                val polledToken = client.pollDeviceAuth(currentCode.deviceCode)
                if (polledToken != null) {
                    token = polledToken
                    sessionStore.saveToken(polledToken)
                    val acc = client.fetchAccount(polledToken)
                    sessionStore.saveAccount(acc)
                    isPolling = false
                    deviceCodeState = null
                    Toast.makeText(context, "Яндекс Музыка успешно подключена!", Toast.LENGTH_SHORT).show()
                    break
                }
            } catch (e: Exception) {
                errorMessage = e.message
                isPolling = false
                break
            }
        }
    }

    BackHandler(onBack = onClose)

    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            TopAppBar(
                title = { Text("Яндекс Музыка", fontWeight = FontWeight.Bold) },
                navigationIcon = { LevyraBackButton(onClick = onClose, contentDescription = "Назад") },
                windowInsets = WindowInsets(0, 0, 0, 0)
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Main Account Card
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                    shape = MaterialTheme.shapes.extraLarge,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        YandexAvatar(account, Modifier.size(56.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = if (account?.isConnected == true) account!!.displayName else "Яндекс Музыка",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = if (account?.isConnected == true) {
                                    if (account!!.hasPlus) "Подключено • Яндекс Плюс (320 kbps)" else "Подключено (Базовый доступ)"
                                } else {
                                    "Аккаунт не подключен"
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = if (account?.isConnected == true) LevyraCyan else LevyraMuted
                            )
                        }
                    }
                }

                // If Connected: Info and Disconnect
                if (!token.isNullOrBlank() && account?.isConnected == true) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                        shape = MaterialTheme.shapes.large,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Icon(Icons.Rounded.HighQuality, contentDescription = null, tint = LevyraCyan)
                                Text("Высокое качество звука", fontWeight = FontWeight.SemiBold, color = LevyraText)
                            }
                            Text(
                                "Потоки 320 kbps MP3 без рекламы активированы для библиотеки и фонового воспроизведения.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = LevyraMuted
                            )
                        }
                    }

                    FilledTonalButton(
                        onClick = {
                            sessionStore.clear()
                            token = null
                            deviceCodeState = null
                            isPolling = false
                            Toast.makeText(context, "Аккаунт отключен", Toast.LENGTH_SHORT).show()
                        },
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Logout, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Отключить аккаунт")
                    }
                } else {
                    // Not Connected: Device Flow Login Section
                    Text(
                        "Подключение аккаунта",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = LevyraText
                    )

                    if (deviceCodeState == null) {
                        Text(
                            "Подключите Яндекс Музыку для доступа к потокам 320 kbps и вашим любимым трекам.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LevyraMuted
                        )

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isLoadingCode = true
                                    errorMessage = null
                                    try {
                                        val code = client.startDeviceAuth()
                                        deviceCodeState = code
                                    } catch (e: Exception) {
                                        errorMessage = e.message ?: "Не удалось начать авторизацию"
                                    } finally {
                                        isLoadingCode = false
                                    }
                                }
                            },
                            enabled = !isLoadingCode,
                            modifier = Modifier.fillMaxWidth().height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LevyraCyan, contentColor = MaterialTheme.colorScheme.surface)
                        ) {
                            if (isLoadingCode) {
                                CircularProgressIndicator(Modifier.size(24.dp), color = MaterialTheme.colorScheme.surface)
                            } else {
                                Icon(Icons.Rounded.Login, contentDescription = null)
                                Spacer(Modifier.width(8.dp))
                                Text("Войти через код устройства", fontWeight = FontWeight.Bold)
                            }
                        }
                    } else {
                        // Display Code
                        Card(
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                            shape = MaterialTheme.shapes.extraLarge,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    "Код подтверждения",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = LevyraMuted
                                )

                                Text(
                                    text = deviceCodeState!!.userCode,
                                    fontSize = 32.sp,
                                    fontWeight = FontWeight.Black,
                                    fontFamily = FontFamily.Monospace,
                                    letterSpacing = 4.sp,
                                    color = LevyraCyan,
                                    textAlign = TextAlign.Center
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = LevyraCyan)
                                    Text("Ожидание подтверждения...", style = MaterialTheme.typography.bodySmall, color = LevyraMuted)
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                            clipboard.setPrimaryClip(ClipData.newPlainText("Yandex Code", deviceCodeState!!.userCode))
                                            Toast.makeText(context, "Код скопирован", Toast.LENGTH_SHORT).show()
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Rounded.ContentCopy, contentDescription = null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Копировать")
                                    }

                                    Button(
                                        onClick = {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(deviceCodeState!!.verificationUrl))
                                            context.startActivity(intent)
                                        },
                                        modifier = Modifier.weight(1f),
                                        shape = RoundedCornerShape(12.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = LevyraCyan, contentColor = MaterialTheme.colorScheme.surface)
                                    ) {
                                        Icon(Icons.Rounded.OpenInBrowser, contentDescription = null, Modifier.size(18.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("ya.ru/device")
                                    }
                                }
                            }
                        }
                    }

                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    // Optional Manual Token
                    Spacer(Modifier.height(8.dp))
                    TextButton(
                        onClick = { showManualInput = !showManualInput },
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    ) {
                        Text(if (showManualInput) "Скрыть ввод токена" else "Указать токен вручную", color = LevyraMuted)
                    }

                    AnimatedVisibility(visible = showManualInput) {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = manualTokenInput,
                                onValueChange = { manualTokenInput = it },
                                label = { Text("OAuth токен Яндекс Музыки") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Button(
                                onClick = {
                                    if (manualTokenInput.isNotBlank()) {
                                        val entered = manualTokenInput.trim()
                                        token = entered
                                        sessionStore.saveToken(entered)
                                        coroutineScope.launch {
                                            try {
                                                val acc = client.fetchAccount(entered)
                                                sessionStore.saveAccount(acc)
                                                Toast.makeText(context, "Токен сохранен!", Toast.LENGTH_SHORT).show()
                                            } catch (e: Exception) {
                                                Toast.makeText(context, "Токен сохранен, но не удалось получить профиль", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Сохранить токен")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun YandexAvatar(account: YandexAccount?, modifier: Modifier) {
    Surface(modifier.clip(CircleShape), color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
        Box(contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.AccountCircle, contentDescription = null, modifier = Modifier.fillMaxSize().padding(8.dp), tint = LevyraCyan)
            if (!account?.avatarUrl.isNullOrBlank()) {
                AsyncImage(account!!.avatarUrl, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
            }
        }
    }
}
