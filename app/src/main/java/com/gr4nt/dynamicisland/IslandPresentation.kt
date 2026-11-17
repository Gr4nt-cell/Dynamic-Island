package com.gr4nt.dynamicisland

/** Four shared shells. App identity and content never contribute to their geometry. */
enum class IslandTier(val width: Float, val height: Float, val radius: Float) {
    LARGE(366f, 201.6f, 44f),
    MEDIUM(366f, 96f, 36f),
    SMALL(250f, 48f, 24f),
    PILL(148f, 37f, 18.5f)
}
enum class IslandEvent { NOTIFICATION, CHARGING, LOW_BATTERY, RINGER, HEADPHONES }

object IslandPresentation {
    fun tier(item: IslandItem, expanded: Boolean, replying: Boolean = false): IslandTier = when {
        replying -> IslandTier.LARGE
        (item.call && !item.incoming || item.media != null && !item.call) && !expanded -> IslandTier.PILL
        item.call -> IslandTier.MEDIUM
        item.media != null -> IslandTier.LARGE
        item.event != IslandEvent.NOTIFICATION -> IslandTier.SMALL
        item.code != null || item.actions.isNotEmpty() || item.text.length > 80 || '\n' in item.text -> IslandTier.LARGE
        else -> IslandTier.MEDIUM
    }
}
