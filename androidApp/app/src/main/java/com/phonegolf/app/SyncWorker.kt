package com.phonegolf.app

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.TimeUnit

/**
 * Sends unlock counts to the server. Runs about once an hour in the background
 * and each time the app opens. It re-sends the last few days, so a missed run
 * (phone off, no network) fixes itself. Today's number is sent too, so the
 * leaderboard shows a live "today" column.
 */
object Sync {
    /** Android keeps detailed usage events for only a few days. */
    private const val DAYS_BACK = 6L

    suspend fun run(context: Context) {
        val prefs = Prefs(context)
        if (!prefs.isSignedIn || !UnlockCounter.hasUsageAccess(context)) return
        val api = prefs.backend()
        val today = LocalDate.now()
        try {
            for (i in DAYS_BACK downTo 0L) {
                val day = today.minusDays(i)
                val count = UnlockCounter.countUnlocks(context, day) ?: continue // no data: do not post a fake 0
                api.putScore(day, count, metric = "unlocks", source = "auto")
            }
            prefs.lastSyncMillis = System.currentTimeMillis()
            prefs.lastSyncError = null
        } catch (e: Exception) {
            prefs.lastSyncError = e.message ?: e.javaClass.simpleName
            throw e
        }
    }
}

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = try {
        Sync.run(applicationContext)
        Result.success()
    } catch (e: IOException) {
        Result.retry()
    } catch (e: ApiException) {
        if (e.status >= 500) Result.retry() else Result.failure()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<SyncWorker>(1, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("score-sync", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}
