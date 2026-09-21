package com.sweetcode.viby.ui

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.sweetcode.viby.playback.EqualizerManager
import com.sweetcode.viby.ui.components.VibyTopBar
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EqualizerScreen(onBack: () -> Unit) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            VibyTopBar(title = "Ecualizador", onBack = onBack)
        },
    ) { padding ->
        if (!EqualizerManager.isAvailable) {
            Column(
                Modifier.fillMaxSize().padding(padding).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    "El ecualizador no está disponible.\nReproduce una canción y vuelve a intentar.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyLarge,
                )
            }
            return@Scaffold
        }

        val minLevel = EqualizerManager.minLevel.toFloat()
        val maxLevel = EqualizerManager.maxLevel.toFloat()
        val bandCount = EqualizerManager.bandCount
        val presets = remember { EqualizerManager.presets }

        var enabled by remember { mutableStateOf(EqualizerManager.enabled) }
        val levels = remember {
            mutableStateListOf<Float>().apply {
                for (b in 0 until bandCount) add(EqualizerManager.bandLevel(b).toFloat())
            }
        }

        Column(
            Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            // Interruptor on/off
            Row(
                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Activar ecualizador",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = {
                        enabled = it
                        EqualizerManager.enabled = it
                    },
                )
            }

            // Presets
            if (presets.isNotEmpty()) {
                Text(
                    "Presets",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    presets.forEachIndexed { i, name ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                EqualizerManager.usePreset(i)
                                for (b in 0 until bandCount) {
                                    levels[b] = EqualizerManager.bandLevel(b).toFloat()
                                }
                                if (!enabled) {
                                    enabled = true
                                    EqualizerManager.enabled = true
                                }
                            },
                            label = { Text(name) },
                        )
                    }
                }
            }

            Spacer(Modifier.padding(8.dp))

            // Bandas
            for (b in 0 until bandCount) {
                Row(
                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        formatFreq(EqualizerManager.centerFreqHz(b)),
                        modifier = Modifier.width(64.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Slider(
                        value = levels[b],
                        onValueChange = {
                            levels[b] = it
                            EqualizerManager.setBandLevel(b, it.roundToInt().toShort())
                        },
                        valueRange = minLevel..maxLevel,
                        enabled = enabled,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        "${(levels[b] / 100).roundToInt()} dB",
                        modifier = Modifier.width(52.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
            }
        }
    }
}

private fun formatFreq(hz: Int): String =
    if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
