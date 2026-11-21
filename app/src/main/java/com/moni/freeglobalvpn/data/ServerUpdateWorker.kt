package com.moni.freeglobalvpn.data

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.moni.freeglobalvpn.FreeVpnApp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Automatic server updates in the background (every 6 h, only on a working network and when the
 * battery is not low): downloads the newest tested list and re-discovers live servers.
 * WorkManager survives reboots and app restarts and respects Doze / OEM battery savers.
 */
class ServerUpdateWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val app = applicationContext as? FreeVpnApp ?: return Result.success()
        if (!app.isReady) return Result.success()
        return runCatching { withContext(Dispatchers.Main) { app.container.repository.refresh() } }
            .fold({ Result.success() }, { Result.retry() })
    }

    companion object {
        private const val NAME = "server-auto-update"

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<ServerUpdateWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .setRequiresBatteryNotLow(true)
                        .build()
                ).build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
