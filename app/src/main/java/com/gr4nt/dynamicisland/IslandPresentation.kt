package com.gr4nt.dynamicisland

/** Four shared shells. App identity and content never contribute to their geometry. */
enum class IslandTier(val width: Float, val height: Float, val radius: Float) {
    LARGE(366f, 191.52f, 44f),
    MEDIUM(366f, 96f, 36f),
    SMALL(250f, 48f, 24f),
    PILL(148f, 37f, 18.5f)
}
enum class IslandEvent { NOTIFICATION, CHARGING, LOW_BATTERY, RINGER, HEADPHONES, UNLOCK, HOTSPOT, DND }

object IslandPresentation {
    fun tier(item: IslandItem, expanded: Boolean, replying: Boolean = false): IslandTier = when {
        replying -> IslandTier.LARGE
        (item.call && !item.incoming || item.media != null && !item.call || item.activity!=null) && !expanded -> IslandTier.PILL
        item.call -> IslandTier.MEDIUM
        item.media != null -> IslandTier.LARGE
        item.activity!=null -> if(item.actions.isNotEmpty()) IslandTier.LARGE else IslandTier.MEDIUM
        item.event != IslandEvent.NOTIFICATION -> IslandTier.SMALL
        item.code != null || item.actions.isNotEmpty() || MessageSizeRules.lines(item.text,45)>2 -> IslandTier.LARGE
        item.title.length<=12 && MessageSizeRules.lines(item.text,12)<=1 -> IslandTier.SMALL
        else -> IslandTier.MEDIUM
    }
}

object MessageSizeRules {
    fun lines(text: String, charactersPerLine: Int): Int = text.split('\n').sumOf { maxOf(1,(it.length+charactersPerLine-1)/charactersPerLine) }
    fun measured(smallBodyLines: Int, smallTitleLines: Int, mediumBodyLines: Int): IslandTier = when {
        smallBodyLines<=1 && smallTitleLines<=1 -> IslandTier.SMALL
        mediumBodyLines<=2 -> IslandTier.MEDIUM
        else -> IslandTier.LARGE
    }
}
