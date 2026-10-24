package com.luc4n3x.levyra.ui.soundcloud

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.luc4n3x.levyra.data.soundcloud.SoundCloudClient
import com.luc4n3x.levyra.ui.components.LevyraBackButton
import com.luc4n3x.levyra.ui.theme.LevyraCyan
import com.luc4n3x.levyra.ui.theme.LevyraMuted
import com.luc4n3x.levyra.ui.theme.LevyraPink
import com.luc4n3x.levyra.ui.theme.LevyraText
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SoundCloudAccountScreen(
    client: SoundCloudClient = remember { SoundCloudClient() },
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var clientId by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(Unit) {
        isLoading = true
        try {
            clientId = client.getClientId()
        } catch (e: Exception) {
            clientId = "V7f0p2q7JD4voO1Esqg6bwwtNYcYAtLC"
        } finally {
            isLoading = false
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
                title = { Text("SoundCloud", fontWeight = FontWeight.Bold) },
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
                // Status Card
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
                        Surface(
                            modifier = Modifier.size(56.dp).clip(CircleShape),
                            color = MaterialTheme.colorScheme.surfaceContainerHigh,
                            shape = CircleShape
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    Icons.Rounded.Cloud,
                                    contentDescription = null,
                                    modifier = Modifier.size(32.dp),
                                    tint = LevyraPink
                                )
                            }
                        }

                        Column(Modifier.weight(1f)) {
                            Text(
                                "SoundCloud",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(Modifier.height(4.dp))
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Surface(
                                    modifier = Modifier.size(8.dp),
                                    shape = CircleShape,
                                    color = LevyraCyan
                                ) {}
                                Text(
                                    "Доступ активен (публичный API)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = LevyraCyan
                                )
                            }
                        }
                    }
                }

                // Info Section
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Rounded.Info, contentDescription = null, tint = LevyraCyan)
                            Text("Как это работает", fontWeight = FontWeight.SemiBold, color = LevyraText)
                        }
                        Text(
                            "SoundCloud интегрирован напрямую в поиск и плеер Levyra. Для прослушивания треков, ремиксов и подкастов авторизация не требуется — плеер автоматически получает актуальный Client ID из сети.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = LevyraMuted
                        )
                    }
                }

                // Diagnostic & Verification
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHighest),
                    shape = MaterialTheme.shapes.large,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("Статус подключения", style = MaterialTheme.typography.labelLarge, color = LevyraMuted)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Текущий Client ID:", style = MaterialTheme.typography.bodyMedium, color = LevyraText)
                            Text(
                                text = if (isLoading) "Определение..." else (clientId ?: "Не определен"),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                color = LevyraCyan
                            )
                        }

                        Button(
                            onClick = {
                                coroutineScope.launch {
                                    isLoading = true
                                    testResult = null
                                    try {
                                        val tracks = client.search("chill", 5)
                                        testResult = if (tracks.isNotEmpty()) {
                                            "Подключение успешно: найдено ${tracks.size} треков"
                                        } else {
                                            "Поиск вернул 0 результатов"
                                        }
                                    } catch (e: Exception) {
                                        testResult = "Ошибка проверки: ${e.message}"
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            enabled = !isLoading,
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = LevyraPink, contentColor = MaterialTheme.colorScheme.surface)
                        ) {
                            Icon(Icons.Rounded.Refresh, contentDescription = null, Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text("Проверить доступность сети")
                        }

                        if (testResult != null) {
                            Text(
                                text = testResult!!,
                                style = MaterialTheme.typography.bodySmall,
                                color = if (testResult!!.startsWith("Подключение успешно")) LevyraCyan else MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }
}
