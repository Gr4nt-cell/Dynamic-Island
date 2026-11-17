package com.gr4nt.dynamicisland

/** State transitions and de-duplication shared by broadcasts and the sticky battery fallback. */
class SystemEventRules {
    private var plugged: Boolean?=null
    private var level: Int?=null
    private var lowAnnounced=false
    private var lastChargeAt=Long.MIN_VALUE
    private var lastUnlockAt=Long.MIN_VALUE
    fun battery(percent: Int, connected: Boolean, now: Long): IslandEvent? {
        val before=plugged;val oldLevel=level
        plugged=connected;level=percent
        if(connected || percent>20) lowAnnounced=false
        if(before==false && connected && chargeAllowed(now)) return IslandEvent.CHARGING
        if(!connected && oldLevel!=null && oldLevel>20 && percent<=20 && lowAllowed()) return IslandEvent.LOW_BATTERY
        return null
    }
    fun powerConnected(now: Long): Boolean { plugged=true;lowAnnounced=false;return chargeAllowed(now) }
    fun lowBattery(): Boolean = plugged!=true && lowAllowed()
    private fun lowAllowed(): Boolean { if(lowAnnounced)return false;lowAnnounced=true;return true }
    private fun chargeAllowed(now: Long): Boolean {
        if(lastChargeAt!=Long.MIN_VALUE && now-lastChargeAt<2000) return false
        lastChargeAt=now;return true
    }
    fun unlocked(now: Long): Boolean {
        if(lastUnlockAt!=Long.MIN_VALUE && now-lastUnlockAt<2000)return false
        lastUnlockAt=now;return true
    }
}
