package com.gr4nt.dynamicisland

/** Monotonic milliseconds are supplied by the caller, keeping timing independently testable. */
class NotificationGate {
    private val keys=mutableMapOf<String,Long>()
    private val packages=mutableMapOf<String,Long>()
    private val seen=mutableMapOf<String,Pair<String,Long>>()
    fun suppressReply(key: String, pkg: String, now: Long) { keys[key]=now+10000;packages[pkg]=now+2500 }
    fun undoReply(key: String, pkg: String) { keys.remove(key);packages.remove(pkg) }
    fun blocked(key: String, pkg: String, now: Long): Boolean {
        keys.entries.removeAll { it.value<=now };packages.entries.removeAll { it.value<=now }
        return (keys[key] ?: 0)>now || (packages[pkg] ?: 0)>now
    }
    fun duplicate(pkg: String, title: String, text: String, now: Long): Boolean {
        val signature=title+"\u0000"+text;val previous=seen[pkg]
        if(previous?.first==signature && now-previous.second<2500) return true
        seen.entries.removeAll {now-it.value.second>10000}
        seen[pkg]=signature to now;return false
    }
}
