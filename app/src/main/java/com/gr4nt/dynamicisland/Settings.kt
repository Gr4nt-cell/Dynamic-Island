package com.gr4nt.dynamicisland

import android.app.Application
import android.content.Context
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

val Context.preferences by preferencesDataStore("island_layout")
class IslandApp : Application() {
    override fun onCreate() {
        super.onCreate(); IslandController.init(this)
        // Android disables an accessibility service that crashes, so keep the last crash for diagnosis.
        val previous=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { t,e ->
            runCatching { java.io.File(filesDir,"last_crash.txt").writeText(android.util.Log.getStackTraceString(e)) }
            previous?.uncaughtException(t,e)
        }
    }
}
data class IslandLayout(
    val left: Float = 10f, val right: Float = 10f, val gap: Float = 2f,
    val cameraSpace: Float = 2f, val bottomSpace: Float = 8f, val size: Float = 100f,
    val topLeft: Float = 44f, val topRight: Float = 44f,
    val bottomLeft: Float = 44f, val bottomRight: Float = 44f,
    val seconds: Float = 6f, val countdown: Boolean = false,
    val allApps: Boolean = true, val allowList: Set<String> = emptySet(),
    val rootRecovery: Boolean = false
)
class LayoutStore(private val context: Context, scope: CoroutineScope) {
    val flow = context.preferences.data.catch { emit(emptyPreferences()) }.map { p ->
        fun f(k: String, d: Float) = p[floatPreferencesKey(k)] ?: d
        fun b(k: String, d: Boolean) = p[booleanPreferencesKey(k)] ?: d
        IslandLayout(f("left",10f), f("right",10f), f("gap",2f), f("cameraSpace",2f), f("bottomSpace",8f), f("size",100f),
            f("topLeft",44f),f("topRight",44f),f("bottomLeft",44f),f("bottomRight",44f),f("seconds",6f),
            b("countdown",false),b("allApps",true),p[stringSetPreferencesKey("allowList")] ?: emptySet(),b("rootRecovery",false))
    }.stateIn(scope, SharingStarted.Eagerly, IslandLayout())
    suspend fun number(key: String, value: Float) { context.preferences.edit { it[floatPreferencesKey(key)] = value } }
    suspend fun toggle(key: String, value: Boolean) { context.preferences.edit { it[booleanPreferencesKey(key)] = value } }
    suspend fun allow(packages: Set<String>) { context.preferences.edit { it[stringSetPreferencesKey("allowList")] = packages } }
}

object NotificationRules {
    fun code(text: String): String? = if (Regex("(?i)\\b(code|otp|verification|pin|passcode)\\b").containsMatchIn(text))
        Regex("(?<!\\d)\\d{4,8}(?!\\d)").find(text)?.value else null
    fun callStart(whenMillis: Long, chronometer: Boolean, incoming: Boolean, wallNow: Long, elapsedNow: Long): Long? =
        if(!incoming && chronometer && whenMillis in 1..wallNow) (elapsedNow-(wallNow-whenMillis)).coerceAtLeast(0) else null
    fun cameraTop(top: Int, bottom: Int, width: Int): Int = if(top == 0) (bottom-width).coerceAtLeast(0) else top
    fun actionKind(label: String): String {
        val t = label.lowercase()
        return when {
            Regex("decline|reject|hang.?up|end|dismiss").containsMatchIn(t) -> "decline"
            Regex("answer|accept").containsMatchIn(t) -> "accept"
            Regex("reply|respond").containsMatchIn(t) -> "reply"
            Regex("read|seen").containsMatchIn(t) -> "read"
            Regex("delete|trash").containsMatchIn(t) -> "delete"
            Regex("archive").containsMatchIn(t) -> "archive"
            else -> "other"
        }
    }
}
