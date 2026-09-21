package com.sweetcode.viby.ui.components

import androidx.compose.foundation.layout.RowScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow

/**
 * Cabecera común de todas las pantallas.
 *
 * Antes cada pantalla repetía el mismo `TopAppBar` con los mismos colores copiados
 * a mano: siete copias que había que tocar una por una para cambiar cualquier cosa.
 *
 * Dos formas, según haya o no a dónde volver:
 * - Con [onBack]: flecha a la izquierda. Es el caso de las pantallas de detalle.
 * - Sin él: solo título y acciones. Es la pantalla de inicio.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibyTopBar(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    VibyTopBar(
        modifier = modifier,
        onBack = onBack,
        actions = actions,
        title = {
            // Una sola línea siempre: un nombre de álbum largo no debe empujar el layout.
            Text(
                text = title,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
    )
}

/**
 * Variante con el título como slot, para cuando no es texto: la pantalla de inicio
 * cambia el título por el campo de búsqueda al activarla.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VibyTopBar(
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
    title: @Composable () -> Unit,
) {
    TopAppBar(
        modifier = modifier,
        title = title,
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Volver")
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            // Transparente: si pintara su propio fondo cortaría en seco el degradado
            // que la pantalla saca de la carátula.
            containerColor = Color.Transparent,
            titleContentColor = MaterialTheme.colorScheme.onBackground,
            navigationIconContentColor = MaterialTheme.colorScheme.onBackground,
            actionIconContentColor = MaterialTheme.colorScheme.onBackground,
        ),
    )
}
