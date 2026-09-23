package com.sweetcode.viby.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sweetcode.viby.ui.UpdateUiState

/**
 * Aviso de versión nueva.
 *
 * Viby no está en ninguna tienda, así que este es el único sitio donde alguien se
 * entera de que hay algo más reciente.
 */
@Composable
fun UpdateDialog(
    state: UpdateUiState,
    versionInstalada: String,
    onActualizar: () -> Unit,
    onPosponer: () -> Unit,
) {
    val act = state.disponible ?: return
    AlertDialog(
        // Mientras baja no se cierra tocando fuera: parecería que se canceló.
        onDismissRequest = { if (!state.descargando) onPosponer() },
        icon = { Icon(Icons.Rounded.CloudDownload, contentDescription = null) },
        title = { Text("Viby ${act.version} disponible") },
        text = {
            Column {
                Text(
                    "Tienes la ${versionInstalada.ifBlank { "actual" }}.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (act.notas.isNotBlank()) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        act.notas,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .heightIn(max = 220.dp)
                            .verticalScroll(rememberScrollState()),
                    )
                }
                if (state.descargando) {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "Descargando…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    // Sin tamaño conocido, barra indeterminada en vez de un 0% quieto.
                    val p = state.progreso
                    if (p != null) {
                        LinearProgressIndicator({ p }, Modifier.fillMaxWidth())
                    } else {
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                    }
                }
                state.error?.let { mensaje ->
                    Spacer(Modifier.height(12.dp))
                    Text(
                        mensaje,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onActualizar, enabled = !state.descargando) {
                Text(if (state.error != null) "Reintentar" else "Actualizar")
            }
        },
        dismissButton = {
            TextButton(onClick = onPosponer, enabled = !state.descargando) {
                Text("Ahora no")
            }
        },
    )
}
