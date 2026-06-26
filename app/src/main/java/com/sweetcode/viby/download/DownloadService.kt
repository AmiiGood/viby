package com.sweetcode.viby.download

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Descarga canciones en segundo plano (cola secuencial) con notificación de progreso. */
class DownloadService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = ArrayDeque<SearchResult>()
    private var processing = false
    private var lastNotifiedPct = -1
    private var doneNotifId = 2000

    private lateinit var repo: DownloadRepository
    private lateinit var notifManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        repo = DownloadRepository(applicationContext)
        notifManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val result = intent?.toSearchResult()
        if (result != null) {
            synchronized(queue) { queue.add(result) }
            DownloadProgress.update(result.url, DownloadStatus.Downloading(0f))
            startForegroundCompat(buildNotification("Preparando descarga…", 0))
            ensureProcessing()
        }
        return START_NOT_STICKY
    }

    private fun ensureProcessing() {
        if (processing) return
        processing = true
        scope.launch {
            while (true) {
                val next = synchronized(queue) { if (queue.isEmpty()) null else queue.removeFirst() }
                if (next == null) break
                processOne(next)
            }
            processing = false
            withContext(Dispatchers.Main) {
                ServiceCompat.stopForeground(this@DownloadService, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
    }

    private suspend fun processOne(result: SearchResult) {
        lastNotifiedPct = -1
        DownloadProgress.update(result.url, DownloadStatus.Downloading(0f))
        updateNotification(result.title, 0f)

        val outcome = repo.download(result) { progress ->
            DownloadProgress.update(result.url, DownloadStatus.Downloading(progress))
            updateNotification(result.title, progress)
        }

        outcome.fold(
            onSuccess = {
                when (it) {
                    DownloadOutcome.DOWNLOADED -> {
                        DownloadProgress.update(result.url, DownloadStatus.Done)
                        DownloadProgress.markCompleted()
                        notifyCompleted(result.title)
                    }
                    DownloadOutcome.ALREADY_EXISTS ->
                        DownloadProgress.update(result.url, DownloadStatus.AlreadyExists)
                }
            },
            onFailure = { e ->
                DownloadProgress.update(result.url, DownloadStatus.Error(e.message ?: "Error"))
            },
        )
    }

    private fun updateNotification(title: String, progress: Float) {
        val pct = (progress * 100).toInt()
        if (pct == lastNotifiedPct) return
        lastNotifiedPct = pct
        notifManager.notify(NOTIF_ID, buildNotification(title, pct))
    }

    private fun buildNotification(text: String, progress: Int) =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Descargando música")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setProgress(100, progress, progress <= 0)
            .build()

    private fun startForegroundCompat(notification: android.app.Notification) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID, notification)
        }
    }

    private fun notifyCompleted(title: String) {
        val launch = packageManager.getLaunchIntentForPackage(packageName)
        val contentIntent = launch?.let {
            PendingIntent.getActivity(
                this, 0, it,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
        }
        val notification = NotificationCompat.Builder(this, DONE_CHANNEL_ID)
            .setContentTitle("Descarga completa")
            .setContentText(title)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .apply { contentIntent?.let { setContentIntent(it) } }
            .build()
        notifManager.notify(doneNotifId++, notification)
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val progress = NotificationChannel(
                CHANNEL_ID,
                "Descargas",
                NotificationManager.IMPORTANCE_LOW,
            ).apply { description = "Progreso de descargas de música" }
            val done = NotificationChannel(
                DONE_CHANNEL_ID,
                "Descargas completadas",
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply { description = "Aviso cuando termina una descarga" }
            notifManager.createNotificationChannel(progress)
            notifManager.createNotificationChannel(done)
        }
    }

    override fun onDestroy() {
        scope.coroutineContext[kotlinx.coroutines.Job]?.cancel()
        super.onDestroy()
    }

    private fun Intent.toSearchResult(): SearchResult? {
        val url = getStringExtra(EXTRA_URL) ?: return null
        return SearchResult(
            title = getStringExtra(EXTRA_TITLE).orEmpty(),
            uploader = getStringExtra(EXTRA_UPLOADER).orEmpty(),
            durationSeconds = getLongExtra(EXTRA_DURATION, 0L),
            url = url,
        )
    }

    companion object {
        private const val CHANNEL_ID = "viby_downloads"
        private const val DONE_CHANNEL_ID = "viby_downloads_done"
        private const val NOTIF_ID = 1001
        private const val EXTRA_URL = "url"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_UPLOADER = "uploader"
        private const val EXTRA_DURATION = "duration"

        fun enqueue(context: Context, result: SearchResult) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_URL, result.url)
                putExtra(EXTRA_TITLE, result.title)
                putExtra(EXTRA_UPLOADER, result.uploader)
                putExtra(EXTRA_DURATION, result.durationSeconds)
            }
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
