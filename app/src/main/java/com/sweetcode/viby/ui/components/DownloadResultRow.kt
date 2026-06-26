package com.sweetcode.viby.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.sweetcode.viby.download.DownloadStatus
import com.sweetcode.viby.download.SearchResult
import com.sweetcode.viby.ui.formatDuration

/** Fila de un resultado de YouTube: carátula, info, preview y descarga. Reutilizable. */
@Composable
fun DownloadResultRow(
    result: SearchResult,
    status: DownloadStatus?,
    previewPlaying: Boolean,
    previewLoading: Boolean,
    onPreview: () -> Unit,
    onDownload: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Thumbnail(result.thumbnailUrl)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                result.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Row {
                Text(
                    result.uploader,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (result.durationSeconds > 0) {
                    Text(
                        " · " + formatDuration(result.durationSeconds * 1000),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (status is DownloadStatus.Error) {
                Text(
                    status.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (status is DownloadStatus.AlreadyExists) {
                Text(
                    "Ya la tienes en tu biblioteca",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        PreviewButton(previewPlaying, previewLoading, onPreview)
        DownloadButton(status, onDownload)
    }
}

@Composable
private fun Thumbnail(url: String?) {
    Box(
        Modifier.size(52.dp).clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url != null) {
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Icon(
                Icons.Rounded.MusicNote,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun PreviewButton(playing: Boolean, loading: Boolean, onClick: () -> Unit) {
    when {
        loading -> Box(Modifier.size(40.dp), Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(22.dp))
        }
        else -> IconButton(onClick = onClick) {
            Icon(
                imageVector = if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pausar preview" else "Escuchar preview",
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

@Composable
private fun DownloadButton(status: DownloadStatus?, onDownload: () -> Unit) {
    when (status) {
        is DownloadStatus.Downloading -> Box(Modifier.size(40.dp), Alignment.Center) {
            if (status.progress > 0f) {
                CircularProgressIndicator(progress = { status.progress }, modifier = Modifier.size(24.dp))
            } else {
                CircularProgressIndicator(modifier = Modifier.size(24.dp))
            }
        }
        DownloadStatus.Done -> Icon(
            Icons.Rounded.Check,
            contentDescription = "Descargado",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(40.dp).padding(8.dp),
        )
        DownloadStatus.AlreadyExists -> Icon(
            Icons.Rounded.Check,
            contentDescription = "Ya descargada",
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(40.dp).padding(8.dp),
        )
        is DownloadStatus.Error -> IconButton(onClick = onDownload) {
            Icon(Icons.Rounded.ErrorOutline, contentDescription = "Reintentar", tint = MaterialTheme.colorScheme.error)
        }
        null -> IconButton(onClick = onDownload) {
            Icon(Icons.Rounded.Download, contentDescription = "Descargar", tint = MaterialTheme.colorScheme.primary)
        }
    }
}
