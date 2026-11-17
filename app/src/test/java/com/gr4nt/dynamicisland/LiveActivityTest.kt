package com.gr4nt.dynamicisland
import org.junit.Assert.*
import org.junit.Test
class LiveActivityTest {
    private val base=IslandItem("test","test.app","Test","Timer","")
    @Test fun classifiesOnlyLiveNotifications() {
        assertNull(ActivityRules.kind(false,"navigation",false,false,"maps","Head south",""))
        assertEquals(ActivityKind.NAVIGATION,ActivityRules.kind(true,"navigation",false,false,"maps","Head south","300 m"))
        assertEquals(ActivityKind.TIMER,ActivityRules.kind(true,"service",true,true,"clock","Timer",""))
        assertEquals(ActivityKind.STOPWATCH,ActivityRules.kind(true,"service",true,false,"clock","Stopwatch",""))
        assertEquals(ActivityKind.RECORDING,ActivityRules.kind(true,"service",true,false,"recorder","Recording",""))
        assertNull(ActivityRules.kind(true,"service",false,false,"vpn","Connected",""))
        assertNull(ActivityRules.kind(true,"service",true,false,"recorder","Recording paused",""))
    }
    @Test fun chronologyUsesTheSystemBaseAndDirection() {
        assertEquals(15000L,ActivityRules.base(1005000,1000000,10000,true))
        assertEquals(5000L,ActivityRules.base(995000,1000000,10000,false))
        assertNull(ActivityRules.base(0,1000000,10000,false))
        assertEquals("01:06",ActivityRules.summary("Arrive 01:06"))
    }
    @Test fun allAppsRemainAvailableWhenAnotherMusicSessionPosts() {
        val live=LiveIslandRegistry()
        val a=base.copy(key="a",pkg="music.a",media=MediaInfo(null,1000,0,0,1f,true))
        val b=a.copy(key="b",pkg="music.b")
        val nav=base.copy(key="nav",pkg="maps",activity=LiveActivity(ActivityKind.NAVIGATION))
        live.put(a);live.put(b);live.put(nav)
        assertEquals(listOf("nav","b","a"),live.active().map { it.key })
        live.hide("nav");live.put(nav.copy(text="Updated directions"))
        assertEquals(listOf("b","a"),live.active().map { it.key })
        live.put(b.copy(media=b.media!!.copy(playing=false)))
        assertEquals(listOf("a"),live.active().map { it.key })
    }
    @Test fun messageMeasurementUsesWrappedLines() {
        assertEquals(IslandTier.SMALL,MessageSizeRules.measured(1,1,1))
        assertEquals(IslandTier.MEDIUM,MessageSizeRules.measured(3,1,2))
        assertEquals(IslandTier.LARGE,MessageSizeRules.measured(4,1,3))
        assertEquals(3,MessageSizeRules.lines("one\ntwo\nthree",45))
    }
}
