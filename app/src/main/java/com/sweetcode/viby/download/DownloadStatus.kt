package com.sweetcode.viby.download

/** Estado de una descarga (por URL). */
sealed class DownloadStatus {
    data class Downloading(val progress: Float) : DownloadStatus()
    object Done : DownloadStatus()
    object AlreadyExists : DownloadStatus()
    data class Error(val message: String) : DownloadStatus()
}
