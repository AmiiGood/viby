package com.sweetcode.viby.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.sweetcode.viby.radio.NewsTopic
import com.sweetcode.viby.radio.RadioScheduler
import com.sweetcode.viby.radio.RadioSettings
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RadioScreen(onBack: () -> Unit) {
    val ctx = LocalContext.current
    val settings = remember { RadioSettings(ctx) }
    val keyboard = LocalSoftwareKeyboardController.current

    var enabled by remember { mutableStateOf(settings.enabled) }
    var apiKey by remember { mutableStateOf(settings.apiKey) }
    var everyN by remember { mutableStateOf(settings.everyNSongs) }
    var wifiOnly by remember { mutableStateOf(settings.wifiOnly) }
    var topics by remember { mutableStateOf(settings.topics) }

    fun persist() {
        settings.enabled = enabled
        settings.apiKey = apiKey
        settings.everyNSongs = everyN
        settings.wifiOnly = wifiOnly
        settings.topics = topics
        if (enabled) RadioScheduler.schedule(ctx) else RadioScheduler.cancel(ctx)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Viby FM", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Text(
                "Tu DJ te da noticias entre canciones, con onda de locutor. " +
                    "Descarga las noticias del día en segundo plano y las lee sin conexión.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            // Activar
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Activar Viby FM", fontWeight = FontWeight.SemiBold)
                Switch(checked = enabled, onCheckedChange = { enabled = it; persist() })
            }

            // API key
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("API key de Anthropic", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text("sk-ant-...") },
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { keyboard?.hide(); persist() }),
                )
                Text(
                    "Se guarda solo en tu teléfono. Consíguela en console.anthropic.com.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Temas
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Temas de noticias", fontWeight = FontWeight.SemiBold)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NewsTopic.entries.forEach { topic ->
                        val selected = topics.contains(topic.id)
                        FilterChip(
                            selected = selected,
                            onClick = {
                                topics = if (selected) topics - topic.id else topics + topic.id
                                persist()
                            },
                            label = { Text(topic.label) },
                            colors = FilterChipDefaults.filterChipColors(),
                        )
                    }
                }
            }

            // Frecuencia
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("El DJ habla cada $everyN canciones", fontWeight = FontWeight.SemiBold)
                Slider(
                    value = everyN.toFloat(),
                    onValueChange = { everyN = it.roundToInt() },
                    onValueChangeFinished = { persist() },
                    valueRange = 2f..10f,
                    steps = 7,
                )
            }

            // Solo WiFi
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Descargar solo con WiFi", fontWeight = FontWeight.SemiBold)
                Switch(checked = wifiOnly, onCheckedChange = { wifiOnly = it; persist() })
            }

            // Estado + actualizar
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    val last = settings.lastFetchEpochMs
                    val lastText = if (last <= 0L) "Aún no descargas noticias" else
                        "Última descarga: " + SimpleDateFormat("d MMM, HH:mm", Locale("es"))
                            .format(Date(last))
                    Text(lastText, style = MaterialTheme.typography.bodyMedium)
                    OutlinedButton(
                        onClick = { persist(); RadioScheduler.refreshNow(ctx) },
                        enabled = apiKey.isNotBlank() && topics.isNotEmpty(),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null)
                        Spacer(Modifier.height(0.dp))
                        Text("  Actualizar noticias ahora")
                    }
                    if (apiKey.isBlank()) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Falta tu API key") },
                        )
                    } else if (topics.isEmpty()) {
                        AssistChip(
                            onClick = {},
                            label = { Text("Elige al menos un tema") },
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
        }
    }
}
