package com.example.clashtracker

import android.app.AppOpsManager
import android.app.usage.UsageStatsManager
import android.content.Context
import android.os.Process

object ForegroundAppChecker {
    const val CLASH_ROYALE_PACKAGE = "com.supercell.clashroyale"

    fun hasUsageAccess(context: Context): Boolean {
        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            Process.myUid(),
            context.packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun isClashRoyaleForeground(context: Context): Boolean {
        val usageStatsManager =
            context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        // Ask for usage stats over a trailing window and take whichever
        // app was MOST RECENTLY used — more reliable in practice than
        // parsing the raw foreground/background event log, which can lag.
        val stats = usageStatsManager.queryUsageStats(
            UsageStatsManager.INTERVAL_BEST,
            now - 60_000,
            now
        )
        if (stats.isNullOrEmpty()) return false
        val mostRecent = stats.maxByOrNull { it.lastTimeUsed }
        return mostRecent?.packageName == CLASH_ROYALE_PACKAGE
    }
}
