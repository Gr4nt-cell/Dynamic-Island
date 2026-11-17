package com.gr4nt.dynamicisland

import android.content.*
import android.media.AudioManager
import android.os.BatteryManager

/** System events don't depend on an OEM publishing a battery notification. */
class SystemIslandEvents(private val context: Context) : BroadcastReceiver() {
    private var registered=false
    private var plugged: Boolean?=null
    private var level: Int?=null
    private var ringer: Int?=null
    fun start() {
        if(registered) return
        ringer=context.getSystemService(AudioManager::class.java).ringerMode
        val filter=IntentFilter().apply {
            addAction(Intent.ACTION_BATTERY_CHANGED)
            addAction(Intent.ACTION_BATTERY_LOW)
            addAction(AudioManager.RINGER_MODE_CHANGED_ACTION)
            addAction(Intent.ACTION_HEADSET_PLUG)
        }
        androidx.core.content.ContextCompat.registerReceiver(context,this,filter,androidx.core.content.ContextCompat.RECEIVER_EXPORTED)
        registered=true
    }
    fun stop() { if(registered) { context.unregisterReceiver(this);registered=false } }
    override fun onReceive(context: Context, intent: Intent) {
        when(intent.action) {
            Intent.ACTION_BATTERY_CHANGED -> {
                val nextPlugged=intent.getIntExtra(BatteryManager.EXTRA_PLUGGED,0)!=0
                val raw=intent.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)
                val scale=intent.getIntExtra(BatteryManager.EXTRA_SCALE,100)
                if(raw<0 || scale<=0) return
                val next=(raw*100/scale).coerceIn(0,100)
                val before=plugged;val beforeLevel=level
                plugged=nextPlugged;level=next
                // Initial sticky broadcast seeds state; connecting the listener isn't a charging event.
                if(before!=null && !before && nextPlugged) post(IslandEvent.CHARGING,"Charging","$next%",0xff34c759.toInt())
                else if(beforeLevel!=null && beforeLevel>20 && next<=20 && !nextPlugged) lowBattery(next)
            }
            Intent.ACTION_BATTERY_LOW -> lowBattery(level ?: 20)
            AudioManager.RINGER_MODE_CHANGED_ACTION -> {
                val next=intent.getIntExtra(AudioManager.EXTRA_RINGER_MODE,AudioManager.RINGER_MODE_NORMAL)
                val before=ringer;ringer=next
                if(before!=null && before!=next) post(IslandEvent.RINGER,if(next==AudioManager.RINGER_MODE_NORMAL) "Ring" else "Silent",if(next==AudioManager.RINGER_MODE_VIBRATE) "Vibrate" else if(next==AudioManager.RINGER_MODE_NORMAL) "On" else "On",0xffff9f0a.toInt())
            }
            Intent.ACTION_HEADSET_PLUG -> if(!isInitialStickyBroadcast && intent.getIntExtra("state",0)==1) post(IslandEvent.HEADPHONES,"Headphones","Connected",0xff64d2ff.toInt())
        }
    }
    private fun lowBattery(percent: Int) = post(IslandEvent.LOW_BATTERY,"Low Battery","$percent%",0xffff453a.toInt())
    private fun post(event: IslandEvent,title: String,text: String,accent: Int) {
        IslandController.post(IslandItem("system-${event.name}",context.packageName,"System",title,text,accent=accent,event=event))
    }
}
