package com.sweetcode.viby

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.sweetcode.viby.download.DownloadProgress
import com.sweetcode.viby.ui.DetailScreen
import com.sweetcode.viby.ui.DiscoverScreen
import com.sweetcode.viby.ui.DownloadScreen
import com.sweetcode.viby.ui.EqualizerScreen
import com.sweetcode.viby.ui.HomeScreen
import com.sweetcode.viby.ui.PlayerViewModel
import com.sweetcode.viby.ui.QueueScreen
import com.sweetcode.viby.ui.RadioScreen
import com.sweetcode.viby.ui.theme.VibyTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VibyTheme {
                RequestNotificationPermission()
                RequestBatteryExemption()
                VibyApp()
            }
        }
    }
}

@Composable
private fun VibyApp() {
    val vm: PlayerViewModel = viewModel()
    val nav = rememberNavController()

    // Refresca la biblioteca cuando termina una descarga (estés en la pantalla que estés).
    val downloadsDone by DownloadProgress.completed.collectAsStateWithLifecycle()
    LaunchedEffect(downloadsDone) {
        if (downloadsDone > 0) vm.refreshLibrary()
    }

    NavHost(navController = nav, startDestination = "home") {
        composable("home") {
            HomeScreen(
                vm = vm,
                onOpenQueue = { nav.navigate("queue") },
                onOpenEqualizer = { nav.navigate("equalizer") },
                onOpenDownload = { nav.navigate("download") },
                onOpenDiscover = { nav.navigate("discover") },
                onOpenRadio = { nav.navigate("radio") },
                onOpenAlbum = { name -> nav.navigate("album/${Uri.encode(name)}") },
                onOpenArtist = { clave -> nav.navigate("artist/${Uri.encode(clave)}") },
            )
        }
        composable("equalizer") {
            EqualizerScreen(onBack = { nav.popBackStack() })
        }
        composable("download") {
            DownloadScreen(player = vm, onBack = { nav.popBackStack() })
        }
        composable("radio") {
            val state by vm.uiState.collectAsStateWithLifecycle()
            RadioScreen(
                onBack = { nav.popBackStack() },
                onPlay = vm::playStation,
                playingStation = state.currentStation,
                nowPlaying = state.currentSong,
                isPlaying = state.isPlaying,
                onPlayPause = vm::togglePlay,
                artworkUri = state.artworkUri,
                onExpandPlayer = {
                    vm.pedirAbrirReproductor()
                    nav.popBackStack()
                },
            )
        }
        composable("discover") {
            val songs by vm.songs.collectAsStateWithLifecycle()
            val favorites by vm.favorites.collectAsStateWithLifecycle()
            DiscoverScreen(
                songs = songs,
                favorites = favorites,
                player = vm,
                onBack = { nav.popBackStack() },
            )
        }
        composable("queue") {
            val queue by vm.queue.collectAsStateWithLifecycle()
            val state by vm.uiState.collectAsStateWithLifecycle()
            QueueScreen(
                queue = queue,
                currentIndex = state.currentIndex,
                onBack = { nav.popBackStack() },
                onPlayIndex = vm::playQueueIndex,
                onRemoveIndex = vm::removeFromQueue,
            )
        }
        composable("album/{name}") { entry ->
            val name = Uri.decode(entry.arguments?.getString("name").orEmpty())
            DetailContent(vm, name, onBack = { nav.popBackStack() }) { it.album == name }
        }
        composable("artist/{clave}") { entry ->
            // Se navega por clave, no por el nombre de la etiqueta: el artista puede
            // estar escrito de varias formas y sus canciones vienen ya agrupadas.
            val clave = Uri.decode(entry.arguments?.getString("clave").orEmpty())
            val artistas by vm.artistas.collectAsStateWithLifecycle()
            val artista = artistas.firstOrNull { it.clave == clave }
            val suyas = remember(artista) { artista?.canciones?.mapTo(HashSet()) { it.id }.orEmpty() }
            DetailContent(vm, artista?.nombre ?: clave, onBack = { nav.popBackStack() }) {
                it.id in suyas
            }
        }
    }
}

@Composable
private fun DetailContent(
    vm: PlayerViewModel,
    title: String,
    onBack: () -> Unit,
    filter: (com.sweetcode.viby.model.Song) -> Boolean,
) {
    val songs by vm.songs.collectAsStateWithLifecycle()
    val state by vm.uiState.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()
    DetailScreen(
        title = title,
        songs = songs.filter(filter),
        currentId = state.currentSong?.id,
        favorites = favorites,
        onBack = onBack,
        onPlay = { list, index -> vm.play(list, index) },
        onToggleFavorite = vm::toggleFavorite,
    )
}

@Composable
private fun RequestNotificationPermission() {
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* el usuario decide; la reproducción funciona igual */ }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

/**
 * Pide la exención de optimización de batería (diálogo oficial de Android).
 * Sin esto, Nothing OS restringe la app tras un rato inactiva y le quita la
 * notificación/controles del reproductor aunque el audio siga sonando.
 */
@Composable
private fun RequestBatteryExemption() {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { /* el sistema aplica el cambio; no necesitamos el resultado */ }

    LaunchedEffect(Unit) {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        if (!pm.isIgnoringBatteryOptimizations(context.packageName)) {
            val intent = Intent(
                Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                Uri.parse("package:${context.packageName}"),
            )
            runCatching { launcher.launch(intent) }
        }
    }
}
