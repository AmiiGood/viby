package com.sweetcode.viby.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sweetcode.viby.playback.SleepTimer
import kotlinx.coroutines.delay

/** Las opciones que se ofrecen, en minutos; 0 significa "al terminar la canción". */
private val OPCIONES = listOf(15, 30, 60, 0)

/**
 * Botón del temporizador de apagado.
 *
 * Habla directamente con [SleepTimer] en vez de pasar por el ViewModel porque el
 * temporizador no es estado de esta pantalla: vive con la reproducción, y quien
 * lo programa se va a dormir y cierra la app.
 */
@Composable
fun BotonTemporizador(acento: Color, modifier: Modifier = Modifier) {
    var abierto by remember { mutableStateOf(false) }
    val hasta by SleepTimer.hasta.collectAsStateWithLifecycle()
    val alTerminar by SleepTimer.alTerminarPista.collectAsStateWithLifecycle()
    val activo = hasta != null || alTerminar

    IconButton(onClick = { abierto = true }, modifier = modifier) {
        Icon(
            Icons.Rounded.Bedtime,
            contentDescription = "Temporizador de apagado",
            tint = if (activo) acento else Color.White.copy(alpha = 0.8f),
        )
    }
    if (abierto) {
        DialogoTemporizador(
            activo = activo,
            onCerrar = { abierto = false },
        )
    }
}

/**
 * Cuánto queda, visible solo mientras hay cuenta atrás.
 *
 * Sin esto, la única señal de que el temporizador está puesto sería el tinte del
 * icono, y nadie deja el móvil fiándose de eso.
 */
@Composable
fun AvisoTemporizador(acento: Color, modifier: Modifier = Modifier) {
    val restante = recordarRestante()
    val alTerminar by SleepTimer.alTerminarPista.collectAsStateWithLifecycle()
    val texto = when {
        restante != null -> "Se apaga en ${formatoCuentaAtras(restante)}"
        alTerminar -> "Se apaga al terminar la canción"
        else -> return
    }
    Text(
        text = texto,
        modifier = modifier.fillMaxWidth(),
        textAlign = TextAlign.Center,
        style = MaterialTheme.typography.labelSmall,
        color = acento,
    )
}

/** Milisegundos que faltan, refrescados cada segundo mientras haya temporizador. */
@Composable
private fun recordarRestante(): Long? {
    val hasta by SleepTimer.hasta.collectAsStateWithLifecycle()
    var restante by remember(hasta) { mutableStateOf(SleepTimer.restante()) }
    LaunchedEffect(hasta) {
        if (hasta == null) {
            restante = null
            return@LaunchedEffect
        }
        while (true) {
            restante = SleepTimer.restante()
            if (restante == null || restante == 0L) break
            delay(1000)
        }
    }
    return restante?.takeIf { hasta != null }
}

@Composable
private fun DialogoTemporizador(activo: Boolean, onCerrar: () -> Unit) {
    // Se arranca en 30 minutos, que es lo que más se usa para dormirse.
    var elegido by remember { mutableStateOf(30) }
    AlertDialog(
        onDismissRequest = onCerrar,
        icon = { Icon(Icons.Rounded.Bedtime, contentDescription = null) },
        title = { Text("Apagar la música en…") },
        text = {
            Column {
                OPCIONES.forEach { minutos ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { elegido = minutos }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = elegido == minutos, onClick = { elegido = minutos })
                        Spacer(Modifier.width(4.dp))
                        Text(etiqueta(minutos))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (elegido == 0) SleepTimer.alTerminarLaCancion() else SleepTimer.programar(elegido)
                onCerrar()
            }) { Text("Activar") }
        },
        dismissButton = {
            // Con un temporizador puesto, lo que hace falta es quitarlo, no salir.
            if (activo) {
                TextButton(onClick = { SleepTimer.cancelar(); onCerrar() }) { Text("Desactivar") }
            } else {
                TextButton(onClick = onCerrar) { Text("Cancelar") }
            }
        },
    )
}

private fun etiqueta(minutos: Int) =
    if (minutos == 0) "Al terminar la canción" else "$minutos minutos"

/** "24:13", y con horas por delante si pasa de una. */
private fun formatoCuentaAtras(ms: Long): String {
    val total = ms / 1000
    val horas = total / 3600
    val minutos = (total % 3600) / 60
    val segundos = total % 60
    return if (horas > 0) "%d:%02d:%02d".format(horas, minutos, segundos)
    else "%d:%02d".format(minutos, segundos)
}
