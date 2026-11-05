package com.nazzeh.turbo

import android.app.ActivityManager
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.os.StatFs
import android.provider.Settings

data class DeviceStats(
    val ramUsedPct: Int,
    val ramUsedMb: Long,
    val ramTotalMb: Long,
    val batteryPct: Int,
    val batteryTempC: Float,
    val charging: Boolean,
    val thermal: String,
    val storageUsedPct: Int,
)

object StatsReader {
    fun read(ctx: Context): DeviceStats {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mi = ActivityManager.MemoryInfo().also { am.getMemoryInfo(it) }
        val totalMb = mi.totalMem / 1048576
        val usedMb = totalMb - mi.availMem / 1048576
        val ramPct = if (totalMb > 0) (usedMb * 100 / totalMb).toInt() else 0

        val b = ctx.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = b?.getIntExtra(BatteryManager.EXTRA_LEVEL, 0) ?: 0
        val scale = b?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100
        val temp = (b?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0) ?: 0) / 10f
        val status = b?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL

        val thermal = if (Build.VERSION.SDK_INT >= 29) {
            val pm = ctx.getSystemService(Context.POWER_SERVICE) as PowerManager
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "Cool"
                PowerManager.THERMAL_STATUS_LIGHT -> "Warm"
                PowerManager.THERMAL_STATUS_MODERATE -> "Hot"
                PowerManager.THERMAL_STATUS_SEVERE -> "Throttling"
                else -> "Critical"
            }
        } else "N/A"

        val st = StatFs(Environment.getDataDirectory().path)
        val storagePct = if (st.blockCountLong > 0)
            ((st.blockCountLong - st.availableBlocksLong) * 100 / st.blockCountLong).toInt() else 0

        return DeviceStats(ramPct, usedMb, totalMb, level * 100 / scale, temp, charging, thermal, storagePct)
    }
}

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("turbo", Context.MODE_PRIVATE)

    var games: List<String>
        get() = sp.getString("games", "")!!.split(",").filter { it.isNotBlank() }
        set(v) = sp.edit().putString("games", v.joinToString(",")).apply()

    var dnd: Boolean
        get() = sp.getBoolean("dnd", false)
        set(v) = sp.edit().putBoolean("dnd", v).apply()

    var overlay: Boolean
        get() = sp.getBoolean("overlay", true)
        set(v) = sp.edit().putBoolean("overlay", v).apply()

    var cleanFirst: Boolean
        get() = sp.getBoolean("clean", true)
        set(v) = sp.edit().putBoolean("clean", v).apply()

    var sessionActive: Boolean
        get() = sp.getBoolean("session", false)
        set(v) = sp.edit().putBoolean("session", v).apply()

    /** Interruption filter to restore when the session ends (-1 = nothing saved). */
    var savedFilter: Int
        get() = sp.getInt("savedFilter", -1)
        set(v) = sp.edit().putInt("savedFilter", v).apply()
}

object Turbo {
    fun hasOverlayPermission(ctx: Context) = Settings.canDrawOverlays(ctx)

    fun hasDndAccess(ctx: Context) =
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
            .isNotificationPolicyAccessGranted

    /** Asks Android to end cached background processes of other apps. Returns how many apps were targeted. */
    fun cleanRam(ctx: Context): Int {
        val am = ctx.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val pm = ctx.packageManager
        val launcher = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pkgs = pm.queryIntentActivities(launcher, 0)
            .map { it.activityInfo.packageName }
            .filter { it != ctx.packageName }
            .distinct()
        pkgs.forEach { am.killBackgroundProcesses(it) }
        return pkgs.size
    }

    fun startSession(ctx: Context, pkg: String): Boolean {
        val store = Store(ctx)
        val intent = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false

        if (store.cleanFirst) cleanRam(ctx)

        if (store.dnd && hasDndAccess(ctx) && !store.sessionActive) {
            val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            store.savedFilter = nm.currentInterruptionFilter
            nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALARMS)
        }
        store.sessionActive = true

        if (store.overlay && hasOverlayPermission(ctx)) {
            ctx.startForegroundService(Intent(ctx, OverlayService::class.java))
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(intent)
        return true
    }

    fun endSession(ctx: Context) {
        val store = Store(ctx)
        if (store.savedFilter != -1 && hasDndAccess(ctx)) {
            (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .setInterruptionFilter(store.savedFilter)
        }
        store.savedFilter = -1
        store.sessionActive = false
        ctx.stopService(Intent(ctx, OverlayService::class.java))
    }
}
