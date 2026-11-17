package com.gr4nt.dynamicisland

import org.junit.Assert.*
import org.junit.Test

class CallTimingTest {
    @Test fun ongoingStyleDoesNotProveAnAnsweredCall() {
        assertFalse(CallTiming.incoming(0,false,"Call in progress"))
        assertFalse(CallTiming.connected(false,false,"Calling…"))
        assertFalse(CallTiming.connected(false,true,"Ringing"))
        assertFalse(CallTiming.connected(false,true,"Connecting"))
        assertFalse(CallTiming.connected(true,true,"Connected"))
        assertNull(CallTiming.start(false,false,null,true,null,50000))
    }
    @Test fun onlyConnectionSignalsStartTheTimer() {
        assertTrue(CallTiming.connected(false,true,""))
        assertTrue(CallTiming.connected(false,false,"Call in progress"))
        assertEquals(50000L,CallTiming.start(false,true,null,true,30000,50000))
        assertEquals(45000L,CallTiming.start(false,true,null,false,45000,50000))
        assertEquals(45000L,CallTiming.start(false,false,45000,false,null,60000))
        assertNull(CallTiming.start(true,true,45000,false,45000,60000))
    }
    @Test fun incomingUsesAnswerActionInsteadOfActionCount() {
        assertTrue(CallTiming.incoming(1,false,""))
        assertTrue(CallTiming.incoming(0,true,""))
        assertFalse(CallTiming.incoming(2,false,""))
        assertFalse(CallTiming.incoming(0,false,"Mute · Hang up"))
    }
}
