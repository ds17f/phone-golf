package com.phonegolf.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Process
import android.provider.Settings
import java.time.LocalDate
import java.time.ZoneId

/**
 * Counts unlocks the same way Digital Wellbeing does: one unlock each time the
 * lock screen (keyguard) is dismissed. A glance at a notification on the lock
 * screen does not count.
 *
 * Needs the "Usage access" special permission, which the user grants in Settings.
 */
object UnlockCounter {

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java)
        @Suppress("DEPRECATION")
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        } else {
            @Suppress("DEPRECATION")
            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun usageAccessSettingsIntent(): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Unlocks on a local calendar day. For today, counts up to now.
     * Returns null when Android has no usage events at all for that day (phone
     * off, or the data is too old and Android deleted it). A real day of use
     * always has events, so null means "unknown", not "zero".
     */
    fun countUnlocks(context: Context, date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Int? {
        val start = date.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = minOf(date.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli(), System.currentTimeMillis())
        if (end <= start) return null
        val usm = context.getSystemService(UsageStatsManager::class.java)
        val events = usm.queryEvents(start, end)
        val event = UsageEvents.Event()
        var unlocks = 0
        var any = false
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            any = true
            if (event.eventType == UsageEvents.Event.KEYGUARD_HIDDEN) unlocks++
        }
        return if (any) unlocks else null
    }
}
