package com.sweetcode.viby.radio

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Baja las noticias del día en segundo plano (lo agenda RadioScheduler). */
class RadioRefreshWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val settings = RadioSettings(applicationContext)
        if (!settings.enabled || !settings.hasApiKey() || settings.activeTopics().isEmpty()) {
            return@withContext Result.success()
        }
        val count = runCatching { RadioRepository(applicationContext).refresh() }.getOrDefault(0)
        if (count > 0) Result.success() else Result.retry()
    }
}
