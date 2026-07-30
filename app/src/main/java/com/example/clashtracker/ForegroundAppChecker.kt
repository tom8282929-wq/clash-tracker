package com.example.clashtracker

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

object ForegroundAppChecker {
    const val CLASH_ROYALE_PACKAGE = "com.supercell.clashroyale"

    private var lastQueryTime = 0L
    private var currentForegroundPackage: String? = null

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    private fun refresh(context: Context) {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        // Query only NEW events since the last check, instead of a fixed
        // trailing window — otherwise a long, uninterrupted session in one
        // app eventually falls outside a fixed window and looks "idle".
        val start = if (lastQueryTime == 0L) now - 10_000 else lastQueryTime
        val events = usageStatsManager.queryEvents(start, now)
        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == UsageEvents.Event.MOVE_TO_FOREGROUND) {
                currentForegroundPackage = event.packageName
            }
        }
        lastQueryTime = now
    }

    fun isClashRoyaleForeground(context: Context): Boolean {
        refresh(context)
        return currentForegroundPackage == CLASH_ROYALE_PACKAGE
    }
}
