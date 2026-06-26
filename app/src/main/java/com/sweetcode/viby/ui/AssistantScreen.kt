package com.sweetcode.viby.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sweetcode.viby.assistant.AssistantController

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val state by AssistantController.state.collectAsStateWithLifecycle()
    var accessKey by remember { mutableStateOf(AssistantController.accessKey(context)) }

    LaunchedEffect(Unit) { AssistantController.syncFrom(context) }

    val micPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) AssistantController.setEnabled(context, true)
    }

    fun toggle(want: Boolean) {
        if (!want) {
            AssistantController.setEnabled(context, false)
            return
        }
        AssistantController.setAccessKey(context, accessKey)
        val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) AssistantController.setEnabled(context, true)
        else micPermission.launch(Manifest.permission.RECORD_AUDIO)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Asistente Viby", fontWeight = FontWeight.Bold) },
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
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        "Activar \"Hey Viby\"",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onBackground,
                    )
                    Text(
                        state.status,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.enabled, onCheckedChange = { toggle(it) })
            }

            OutlinedTextField(
                value = accessKey,
                onValueChange = {
                    accessKey = it
                    AssistantController.setAccessKey(context, it)
                },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                label = { Text("Picovoice Access Key") },
                singleLine = true,
            )

            Spacer(Modifier.height(20.dp))

            Text(
                "Cómo activarlo",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                INSTRUCTIONS,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(20.dp))
            Text(
                "Comandos que entiende",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                COMMANDS,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private const val INSTRUCTIONS =
    "1. Crea una cuenta gratis en console.picovoice.ai y copia tu AccessKey en el campo de arriba.\n\n" +
        "2. En Picovoice Console crea la palabra clave \"Viby\" (idioma English, plataforma Android), " +
        "descarga el archivo .ppn, renómbralo a \"Viby.ppn\" y colócalo en la carpeta " +
        "app/src/main/assets/ del proyecto (vuelve a compilar).\n\n" +
        "3. Activa el interruptor y concede el permiso de micrófono.\n\n" +
        "Nota: con el asistente encendido, el micrófono escucha en segundo plano (verás el indicador " +
        "de micrófono de Android). Apágalo cuando no lo uses."

private const val COMMANDS =
    "Di \"Viby\" y luego:\n" +
        "• \"siguiente\" / \"anterior\"\n" +
        "• \"pausa\" / \"reanuda\"\n" +
        "• \"reproduce [canción o artista]\"\n" +
        "• \"qué canción es esta\"\n" +
        "• \"sube/baja el volumen\""
