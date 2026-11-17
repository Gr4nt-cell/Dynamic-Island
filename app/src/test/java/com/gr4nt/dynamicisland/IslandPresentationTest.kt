package com.gr4nt.dynamicisland

import org.junit.Assert.*
import org.junit.Test

class IslandPresentationTest {
    private val message=IslandItem("test","messages","Messages","Ama","See you soon")
    @Test fun fixedFourTiersAcrossApps() {
        assertEquals(4,IslandTier.entries.size)
        for(pkg in listOf("whatsapp","telegram","gmail")) {
            val item=message.copy(pkg=pkg)
            assertEquals(IslandTier.MEDIUM,IslandPresentation.tier(item,true))
            assertEquals(IslandTier.LARGE,IslandPresentation.tier(item.copy(text="Long message ".repeat(40)),true))
            assertEquals(IslandTier.LARGE,IslandPresentation.tier(item,true,replying=true))
            assertEquals(IslandTier.MEDIUM,IslandPresentation.tier(item.copy(call=true,incoming=true),true))
            assertEquals(IslandTier.PILL,IslandPresentation.tier(item.copy(call=true),false))
        }
    }
    @Test fun mediaAndRichNotificationsShareLargeGeometry() {
        val media=message.copy(media=MediaInfo(null,1000,0,0,1f,true))
        assertEquals(IslandTier.LARGE,IslandPresentation.tier(media,true))
        assertEquals(IslandTier.PILL,IslandPresentation.tier(media,false))
        assertEquals(IslandTier.LARGE,IslandPresentation.tier(message.copy(code="123456"),true))
    }
    @Test fun allSystemEventsShareOneSmallShell() {
        IslandEvent.entries.filter { it!=IslandEvent.NOTIFICATION }.forEach {
            assertEquals(IslandTier.SMALL,IslandPresentation.tier(message.copy(event=it),true))
        }
    }
    @Test fun callCategoryTakesPriorityOverMediaMetadata() {
        val call=message.copy(call=true,incoming=true,media=MediaInfo(null,0,0,0,1f,true))
        assertEquals(IslandTier.MEDIUM,IslandPresentation.tier(call,true))
        assertEquals(IslandTier.PILL,IslandPresentation.tier(call.copy(incoming=false),false))
    }
}
