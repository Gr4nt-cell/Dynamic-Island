package com.gr4nt.dynamicisland

import android.app.KeyguardManager
import android.app.NotificationManager
import android.content.*
import android.media.*
import android.os.*
import kotlinx.coroutines.*

/** One monitor shared by overlay and listener, so either connection keeps system events alive. */
class SystemIslandEvents(private val context: Context, private val scope: CoroutineScope) : BroadcastReceiver() {
    private var registered=false
    private val rules=SystemEventRules()
    private var ringer=-1
    private var dnd=-1
    private var hotspot=-1
    private var percent=0
    private var unlockWatch: Job?=null
    private var audioCallback: AudioDeviceCallback?=null
    private val knownAudio=mutableSetOf<Int>()
    private val audio get()=context.getSystemService(AudioManager::class.java)
    fun start() {
        if(registered)return
        ringer=audio.ringerMode
        dnd=runCatching { context.getSystemService(NotificationManager::class.java).currentInterruptionFilter }.getOrDefault(NotificationManager.INTERRUPTION_FILTER_UNKNOWN)
        val sticky=context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        sticky?.let { battery(it) }
        val filter=IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED);addAction(Intent.ACTION_BATTERY_LOW)
            addAction(Intent.ACTION_POWER_CONNECTED);addAction(Intent.ACTION_POWER_DISCONNECTED)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(Intent.ACTION_USER_PRESENT);addAction(Intent.ACTION_SCREEN_ON);addAction(Intent.ACTION_SCREEN_OFF)
            addAction("android.net.wifi.WIFI_AP_STATE_CHANGED")
            addAction(NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED)
        }
        androidx.core.content.ContextCompat.registerReceiver(context,this,filter,androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        registered=true
        knownAudio.clear();knownAudio.addAll(audio.getDevices(AudioManager.GET_DEVICES_OUTPUTS).map { it.id })
        audioCallback=object: AudioDeviceCallback() {
            override fun onAudioDevicesAdded(devices: Array<out AudioDeviceInfo>) {
                devices.forEach { device ->
                    val new=knownAudio.add(device.id)
                    if(new && device.isSink && device.type in setOf(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,AudioDeviceInfo.TYPE_BLUETOOTH_SCO,AudioDeviceInfo.TYPE_BLE_HEADSET,AudioDeviceInfo.TYPE_WIRED_HEADSET,AudioDeviceInfo.TYPE_WIRED_HEADPHONES,AudioDeviceInfo.TYPE_USB_HEADSET)) {
                        post(IslandEvent.HEADPHONES,"Headphones",runCatching { device.productName.toString() }.getOrDefault("Connected"),0xff64d2ff.toInt())
                    }
                }
            }
            override fun onAudioDevicesRemoved(devices: Array<out AudioDeviceInfo>) { devices.forEach { knownAudio.remove(it.id) } }
        }.also { audio.registerAudioDeviceCallback(it,Handler(Looper.getMainLooper())) }
        if(context.getSystemService(PowerManager::class.java).isInteractive) watchUnlock()
    }
    fun stop() {
        if(registered) { context.unregisterReceiver(this);registered=false }
        audioCallback?.let { audio.unregisterAudioDeviceCallback(it) };audioCallback=null
        unlockWatch?.cancel();unlockWatch=null;knownAudio.clear()
    }
    override fun onReceive(context: Context, intent: Intent) {
        runCatching {
            when(intent.action) {
                Intent.ACTION_BATTERY_CHANGED -> battery(intent)
                Intent.ACTION_POWER_CONNECTED -> {
                    context.registerReceiver(null,IntentFilter(Intent.ACTION_BATTERY_CHANGED))?.let { updatePercent(it) }
                    if(rules.powerConnected(SystemClock.elapsedRealtime())) charging()
                }
                Intent.ACTION_POWER_DISCONNECTED -> rules.battery(percent,false,SystemClock.elapsedRealtime())?.let { if(it==IslandEvent.LOW_BATTERY)lowBattery() }
                Intent.ACTION_BATTERY_LOW -> if(rules.lowBattery())lowBattery()
                AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                    val next=intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE,AudioManager.RINGER_MODE_NORMAL)
                    if(next!=ringer) { ringer=next
                        post(IslandEvent.RINGER,if(next==AudioManager.RINGER_MODE_NORMAL)"Ring" else "Silent",if(next==AudioManager.RINGER_MODE_VIBRATE)"Vibrate" else "On",if(next==AudioManager.RINGER_MODE_NORMAL)0xffff9f0a.toInt() else 0xffff2d55.toInt())
                    }
                }
                Intent.ACTION_SCREEN_ON -> watchUnlock()
                Intent.ACTION_SCREEN_OFF -> { unlockWatch?.cancel();unlockWatch=null }
                Intent.ACTION_USER_PRESENT -> unlocked()
                "android.net.wifi.WIFI_AP_STATE_CHANGED" -> {
                    val next=intent.getIntExtra("wifi_state",0)
                    if(next==13 && hotspot!=13)post(IslandEvent.HOTSPOT,"Hotspot","On",0xff30d158.toInt())
                    hotspot=next
                }
                NotificationManager.ACTION_INTERRUPTION_FILTER_CHANGED -> {
                    val next=context.getSystemService(NotificationManager::class.java).currentInterruptionFilter
                    if(next!=dnd && next!=NotificationManager.INTERRUPTION_FILTER_UNKNOWN)post(IslandEvent.DND,"Do Not Disturb",if(next==NotificationManager.INTERRUPTION_FILTER_ALL)"Off" else "On",0xff64d2ff.toInt())
                    dnd=next
                }
            }
        }.onFailure { android.util.Log.w("DynamicIsland","System event unavailable",it) }
    }
    private fun updatePercent(intent: Intent) {
        val raw=intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1);val scale=intent.getIntExtra(BatteryManager.EXTRA_SCALE,100)
        if(raw>=0 && scale>0)percent=(raw*100/scale).coerceIn(0,100)
    }
    private fun battery(intent: Intent) {
        updatePercent(intent)
        val event=rules.battery(percent,intent.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0,SystemClock.elapsedRealtime())
        if(event==IslandEvent.CHARGING)charging() else if(event==IslandEvent.LOW_BATTERY)lowBattery()
    }
    private fun charging()=post(IslandEvent.CHARGING,"Charging","$percent%",0xff34c759.toInt(),5000)
    private fun lowBattery()=post(IslandEvent.LOW_BATTERY,"Low Battery","$percent%",0xffff453a.toInt(),5000)
    private fun unlocked() {
        if(rules.unlocked(SystemClock.elapsedRealtime()))post(IslandEvent.UNLOCK,"Unlocked","",0xff30d158.toInt(),1600)
    }
    private fun watchUnlock() {
        unlockWatch?.cancel()
        val keyguard=context.getSystemService(KeyguardManager::class.java)
        if(!keyguard.isDeviceLocked) {
            if(keyguard.isDeviceSecure && keyguard.isKeyguardLocked)unlocked()
            return
        }
        unlockWatch=scope.launch {
            // Also catches face authentication that leaves the keyguard visible until a swipe.
            val until=SystemClock.elapsedRealtime()+30000
            while(isActive && context.getSystemService(PowerManager::class.java).isInteractive && SystemClock.elapsedRealtime()<until) {
                if(!keyguard.isDeviceLocked) { unlocked();break };delay(250)
            }
        }
    }
    private fun post(event: IslandEvent,title: String,text: String,accent: Int,millis: Long=3200) {
        IslandController.post(IslandItem("system-${event.name}",context.packageName,"System",title,text,accent=accent,event=event,statusDurationMillis=millis))
    }
}
