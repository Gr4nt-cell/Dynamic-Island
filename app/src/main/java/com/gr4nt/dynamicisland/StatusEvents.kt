package com.gr4nt.dynamicisland

import android.content.*
import android.media.*
import android.os.BatteryManager
import android.os.SystemClock
import android.app.NotificationManager

/** Feeds iPhone-style system pills: ring mode, charging, low battery, headset, hotspot and Do Not Disturb. */
object StatusEvents {
    private var receiver: BroadcastReceiver? = null
    private var callback: AudioDeviceCallback? = null
    private var startedAt = 0L
    private var lastLevel = -1
    private var charging = false

    fun start(c: Context) {
        stop(c)
        startedAt = SystemClock.elapsedRealtime()
        runCatching {
            val sticky = c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            lastLevel = level(sticky); charging = (sticky?.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) ?: 0) != 0
        }
        val r = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, i: Intent) { runCatching { handle(ctx, i) }.onFailure { android.util.Log.e("DynamicIsland", "status event failed", it) } }
        }
        val f = IntentFilter().apply {
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION); addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_POWER_CONNECTED); addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        if (android.os.Build.VERSION.SDK_INT >= 33) c.registerReceiver(r, f, Context.RECEIVER_EXPORTED) else c.registerReceiver(r, f)
        receiver = r
        val audio = c.getSystemService(AudioManager::class.java)
        callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(devices: Array<out AudioDeviceInfo>) {
                // Registration replays connected devices; only announce ones that appear afterwards.
                if (SystemClock.elapsedRealtime() - startedAt < 2500) return
                devices.firstOrNull { it.isSink && it.type in HEADSETS }?.let {
                    IslandController.postStatus(StatusInfo("headset", "", it.productName?.toString()?.take(18) ?: "Connected", 0xFFFFFFFF.toInt()))
                }
            }
        }
        audio.registerAudioDeviceCallback(callback, android.os.Handler(c.mainLooper))
    }
    fun stop(c: Context) {
        receiver?.let { runCatching { c.unregisterReceiver(it) } }; receiver = null
        callback?.let { runCatching { c.getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(it) } }; callback = null
    }
    private val HEADSETS = setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLE_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES, AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_USB_HEADSET)
    private fun level(i: Intent?): Int { val l = i?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1; val s = i?.getIntExtra(BatteryManager.EXTRA_SCALE, 100) ?: 100; return if (l < 0) -1 else l * 100 / s }

    private fun handle(c: Context, i: Intent) {
        when (i.action) {
            AudioManager.RINGER_MODE_CHANGED_ACTION -> when (i.getIntExtra(AudioManager.EXTRA_RINGER_MODE, -1)) {
                AudioManager.RINGER_MODE_SILENT -> IslandController.postStatus(StatusInfo("silent", "", "Silent", 0xFFFF3B30.toInt()))
                AudioManager.RINGER_MODE_VIBRATE -> IslandController.postStatus(StatusInfo("vibrate", "", "Vibrate"))
                AudioManager.RINGER_MODE_NORMAL -> IslandController.postStatus(StatusInfo("ring", "", "Ring"))
            }
            Intent.ACTION_POWER_CONNECTED -> {
                val l = level(c.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)))
                IslandController.postStatus(StatusInfo("charging", "Charging", "$l%", 0xFF30D158.toInt(), l))
            }
            Intent.ACTION_BATTERY_CHANGED -> {
                val l = level(i); val plugged = i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0) != 0
                if (!plugged && lastLevel > 20 && l == 20) IslandController.postStatus(StatusInfo("lowbattery", "Low Battery", "20%", 0xFFFF3B30.toInt(), 20, "battery_saver"), false, 5000)
                lastLevel = l; charging = plugged
            }
            "android.net.wifi.WIFI_AP_STATE_CHANGED" ->
                if (i.getIntExtra("wifi_state", 0) == 13) IslandController.postStatus(StatusInfo("hotspot", "", "Hotspot", 0xFF30D158.toInt()))
            NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                val filter = c.getSystemService(NotificationManager::class.java).currentInterruptionFilter
                if (filter != NotificationManager.INTERRUPTION_FILTER_ALL && filter != NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
                    IslandController.postStatus(StatusInfo("dnd", "", "Do Not Disturb", 0xFF64D2FF.toInt()))
            }
        }
    }
}
