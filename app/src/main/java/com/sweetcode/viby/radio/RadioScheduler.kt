package com.sweetcode.viby.radio

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.Calendar
import java.util.concurrent.TimeUnit

/** Programa la descarga diaria de noticias (~8AM, con WiFi) y la manual. */
object RadioScheduler {
    private const val PERIODIC = "viby_radio_daily"
    private const val ONESHOT = "viby_radio_now"

    fun schedule(context: Context) {
        val settings = RadioSettings(context)
        val net = if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder().setRequiredNetworkType(net).build()

        val request = PeriodicWorkRequestBuilder<RadioRefreshWorker>(1, TimeUnit.DAYS)
            .setConstraints(constraints)
            .setInitialDelay(millisUntilNextMorning(), TimeUnit.MILLISECONDS)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request,
        )
    }

    /** Fuerza una descarga ahora (botón "Actualizar noticias"). */
    fun refreshNow(context: Context) {
        val settings = RadioSettings(context)
        val net = if (settings.wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val request = OneTimeWorkRequestBuilder<RadioRefreshWorker>()
            .setConstraints(Constraints.Builder().setRequiredNetworkType(net).build())
            .build()
        WorkManager.getInstance(context)
            .enqueueUniqueWork(ONESHOT, ExistingWorkPolicy.REPLACE, request)
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(PERIODIC)
    }

    private fun millisUntilNextMorning(hour: Int = 8): Long {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            if (before(now)) add(Calendar.DAY_OF_MONTH, 1)
        }
        return (target.timeInMillis - now.timeInMillis).coerceAtLeast(0)
    }
}
