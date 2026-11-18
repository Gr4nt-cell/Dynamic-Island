package com.gr4nt.dynamicisland

enum class ActivityKind { TIMER, STOPWATCH, RECORDING, NAVIGATION }
data class LiveActivity(val kind: ActivityKind, val baseElapsed: Long? = null, val countdown: Boolean = false, val value: String = "")

object ActivityRules {
    fun kind(ongoing: Boolean, category: String?, chronometer: Boolean, countdown: Boolean, pkg: String, title: String, text: String): ActivityKind? {
        if(!ongoing || Regex("(?i)\\b(paused|stopped|finished|cancelled|canceled)\\b").containsMatchIn("$title $text")) return null
        if(category=="navigation") return ActivityKind.NAVIGATION
        if(!chronometer) return null
        val label="$pkg $title $text"
        return when {
            Regex("(?i)record|dictaphone|voice memo").containsMatchIn(label) -> ActivityKind.RECORDING
            countdown || Regex("(?i)\\btimer\\b|countdown").containsMatchIn(label) -> ActivityKind.TIMER
            else -> ActivityKind.STOPWATCH
        }
    }
    fun base(whenMillis: Long, wallNow: Long, elapsedNow: Long, countdown: Boolean): Long? =
        if(whenMillis<=0) null else if(countdown) elapsedNow+(whenMillis-wallNow) else (elapsedNow-(wallNow-whenMillis)).coerceAtLeast(0)
    fun summary(text: String): String = Regex("\\b\\d{1,2}:\\d{2}\\b|\\b\\d+(?:[.,]\\d+)?\\s?(?:min|km|mi|m)\\b").find(text)?.value ?: text
}

/** Keeps all apps, not just the most recently posted music session. */
class LiveIslandRegistry {
    private val items=LinkedHashMap<String,IslandItem>()
    private val hidden=mutableSetOf<String>()
    fun get(key: String)=items[key]
    fun put(item: IslandItem) {
        val previous=items[item.key]
        if(item.media?.playing==true && previous?.media?.playing!=true) hidden.remove(item.key)
        items[item.key]=item
    }
    fun active(): List<IslandItem> = items.values.toList().asReversed().filter {
        it.key !in hidden && (it.activity!=null || it.media?.playing==true)
    }.sortedBy { if(it.activity!=null)0 else 1 }
    fun hide(key: String) { hidden.add(key) }
    fun remove(key: String) { items.remove(key);hidden.remove(key) }
}
