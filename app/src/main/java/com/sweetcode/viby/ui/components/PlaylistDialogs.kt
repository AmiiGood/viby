package com.sweetcode.viby.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sweetcode.viby.model.Playlist

/** Crear una lista, o renombrar una que ya existe. */
@Composable
fun DialogoDeLista(
    titulo: String,
    nombreInicial: String = "",
    descripcionInicial: String = "",
    onGuardar: (nombre: String, descripcion: String) -> Unit,
    onCerrar: () -> Unit,
) {
    var nombre by remember { mutableStateOf(nombreInicial) }
    var descripcion by remember { mutableStateOf(descripcionInicial) }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text(titulo) },
        text = {
            Column {
                OutlinedTextField(
                    value = nombre,
                    onValueChange = { nombre = it },
                    label = { Text("Nombre") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = descripcion,
                    onValueChange = { descripcion = it },
                    label = { Text("Descripción (opcional)") },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            // Sin nombre no hay lista: el resto se puede rellenar después.
            TextButton(onClick = { onGuardar(nombre, descripcion) }, enabled = nombre.isNotBlank()) {
                Text("Guardar")
            }
        },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

/** Borrar una lista no se deshace, así que se pregunta por su nombre. */
@Composable
fun ConfirmarBorradoDeLista(nombre: String, onBorrar: () -> Unit, onCerrar: () -> Unit) {
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("¿Borrar \"$nombre\"?") },
        text = { Text("Se borra la lista, no las canciones. No se puede deshacer.") },
        confirmButton = { TextButton(onClick = onBorrar) { Text("Borrar") } },
        dismissButton = { TextButton(onClick = onCerrar) { Text("Cancelar") } },
    )
}

/**
 * Elegir a qué lista va una canción.
 *
 * Se puede crear una nueva desde aquí: obligar a salir, crearla y volver sería
 * perder de vista la canción que se quería guardar.
 */
@Composable
fun DialogoAnadirALista(
    listas: List<Playlist>,
    onElegir: (Playlist) -> Unit,
    onCrear: (String) -> Unit,
    onCerrar: () -> Unit,
) {
    var creando by remember { mutableStateOf(listas.isEmpty()) }
    if (creando) {
        DialogoDeLista(
            titulo = "Nueva lista",
            onGuardar = { nombre, _ -> onCrear(nombre) },
            onCerrar = onCerrar,
        )
        return
    }
    AlertDialog(
        onDismissRequest = onCerrar,
        title = { Text("Añadir a una lista") },
        text = {
            LazyColumn(Modifier.heightIn(max = 320.dp)) {
                item {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { creando = true }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Add, contentDescription = null)
                        Spacer(Modifier.width(12.dp))
                        Text("Nueva lista", color = MaterialTheme.colorScheme.primary)
                    }
                }
                items(listas, key = { it.id }) { lista ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onElegir(lista) }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PortadaDeListaPequena(lista.portada)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(lista.nombre, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(
                                text = if (lista.canciones.size == 1) "1 canción"
                                else "${lista.canciones.size} canciones",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onCerrar) { Text("Cerrar") } },
    )
}

@Composable
private fun PortadaDeListaPequena(portada: String?) {
    com.sweetcode.viby.ui.PortadaDeLista(portada, Modifier.size(44.dp))
}
