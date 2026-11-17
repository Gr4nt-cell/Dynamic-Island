package com.gr4nt.dynamicisland

import android.content.Context
import android.content.ComponentName
import kotlinx.coroutines.*
import java.util.concurrent.TimeUnit

/** All root commands are fixed, opt-in, bounded, and run off the UI thread. */
class RootExtras(private val context: Context) {
    private val prefs = context.getSharedPreferences("root_restore", Context.MODE_PRIVATE)
    private val lock = kotlinx.coroutines.sync.Mutex()
    private suspend fun command(cmd: String): String? = withContext(Dispatchers.IO) {
        runCatching {
            val p = ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start()
            // Drain concurrently so a large dumpsys cannot deadlock a pipe.
            val output = async { p.inputStream.bufferedReader().use { it.readText().take(2_000_000) } }
            try { if (!p.waitFor(8, TimeUnit.SECONDS)) { p.destroyForcibly(); output.cancel(); null }
                else output.await().takeIf { p.exitValue() == 0 }
            } finally { p.destroy() }
        }.getOrNull()
    }
    val hidden: Boolean get() = prefs.getBoolean("changed", false)
    suspend fun nativeHeadsUp(hide: Boolean): Boolean {
        lock.lock()
        return try {
            if (hide && hidden) true
            else if (hide) {
                val old = command("settings get global heads_up_notifications_enabled")?.trim()
                if (old !in listOf("0","1","null")) false else {
                    // Persist restore data before changing system state, including if the process dies.
                    prefs.edit().putString("old",old).putBoolean("changed",true).commit()
                    val ok = command("settings put global heads_up_notifications_enabled 0") != null
                    if (!ok) prefs.edit().putBoolean("changed",false).commit()
                    ok
                }
            } else if (!hidden) true else {
                val old = prefs.getString("old","null")
                val cmd = if (old == "null") "settings delete global heads_up_notifications_enabled" else "settings put global heads_up_notifications_enabled $old"
                val ok = command(cmd) != null
                if(ok) prefs.edit().putBoolean("changed",false).commit()
                ok
            }
        } finally { lock.unlock() }
    }
    suspend fun recoverText(key: String): String? {
        val dump = command("dumpsys notification --noredact") ?: return null
        // Confine recovery to the exact notification record; never match another app's OTP.
        val records = dump.split(Regex("(?m)(?=^\\s*NotificationRecord\\()"))
        val record = records.firstOrNull { it.lineSequence().firstOrNull()?.contains("key=$key") == true } ?: return null
        return record.lineSequence().filter { it.contains("android.text=") || it.contains("android.bigText=") }
            .joinToString(" ") { it.substringAfter('=') }.takeIf { it.isNotBlank() }
    }
    suspend fun enableAccessibility(): Boolean {
        val component = ComponentName(context,IslandAccessibilityService::class.java).flattenToString()
        // Read with the Android API, merge instead of overwriting other enabled services.
        val existing = android.provider.Settings.Secure.getString(context.contentResolver,"enabled_accessibility_services").orEmpty()
        val merged = (existing.split(':').filter { it.isNotBlank() } + component).distinct().joinToString(":")
        if (!Regex("[a-zA-Z0-9_./:$-]+").matches(merged)) return false
        return command("settings put secure enabled_accessibility_services '$merged' && settings put secure accessibility_enabled 1") != null
    }
}
