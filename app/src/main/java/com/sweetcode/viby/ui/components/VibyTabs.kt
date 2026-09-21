package com.sweetcode.viby.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Conmutador de sección en la cabecera.
 *
 * Sustituye a la NavigationBar del pie: en esta dirección el borde inferior se
 * reserva para el reproductor, que es lo que el usuario toca constantemente.
 *
 * El coste es alcance del pulgar — cambiar de pestaña queda más lejos. A cambio,
 * play/pausa y la canción en curso están siempre a mano.
 *
 * Va en LazyRow porque con cuatro etiquetas largas no caben en 360dp: si no
 * entran, se desplazan en vez de recortarse.
 */
@Composable
fun VibyTabs(
    labels: List<String>,
    selected: Int,
    acento: Color,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
    ) {
        itemsIndexed(labels) { i, label ->
            val activa = i == selected
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .then(
                        if (activa) Modifier
                            .background(acento.copy(alpha = 0.18f))
                            .border(1.dp, acento.copy(alpha = 0.5f), RoundedCornerShape(50))
                        else Modifier
                    )
                    .clickable { onSelect(i) }
                    .padding(horizontal = 14.dp, vertical = 9.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (activa) acento
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
