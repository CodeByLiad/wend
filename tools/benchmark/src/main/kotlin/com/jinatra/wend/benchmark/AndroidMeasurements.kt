package com.jinatra.wend.benchmark

import android.app.ActivityManager
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.Debug
import android.os.PowerManager
import android.os.SystemClock
import org.json.JSONObject

class AndroidMeasurements(private val context: Context) : Measurements {
    override fun elapsedNanos() = SystemClock.elapsedRealtimeNanos()
    override fun wallTimeMillis() = System.currentTimeMillis()
    override fun sample(): Telemetry {
        val memory = Debug.MemoryInfo().also { Debug.getMemoryInfo(it) }
        val battery = context.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val manager = context.getSystemService(BatteryManager::class.java)
        val charge = manager.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER).takeIf { it > 0 }
        val level = battery?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = battery?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val temperature = battery?.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, Int.MIN_VALUE)
        return Telemetry(
            memory.totalPss,
            if (level >= 0 && scale > 0) level * 100 / scale else null,
            charge,
            temperature?.takeUnless { it == Int.MIN_VALUE }?.div(10.0),
            battery?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0,
            if (Build.VERSION.SDK_INT >= 29) context.getSystemService(PowerManager::class.java).currentThermalStatus else null,
        )
    }

    fun device(): JSONObject {
        val memory = ActivityManager.MemoryInfo().also { context.getSystemService(ActivityManager::class.java).getMemoryInfo(it) }
        return JSONObject().put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
            .put("androidRelease", Build.VERSION.RELEASE).put("sdk", Build.VERSION.SDK_INT)
            .put("soc", if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL else Build.HARDWARE)
            .put("abi", Build.SUPPORTED_ABIS.firstOrNull()).put("totalRamBytes", memory.totalMem)
            .put("debugBuild", BuildConfig.DEBUG)
    }
}
