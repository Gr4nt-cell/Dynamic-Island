package com.gr4nt.dynamicisland
import org.junit.Assert.*
import org.junit.Test
class NotificationRulesTest {
    @Test fun cameraRectAtScreenEdgeUsesEstimatedLensTop() { assertEquals(8, NotificationRules.cameraTop(0,40,32)); assertEquals(12,NotificationRules.cameraTop(12,44,32)) }
    @Test fun overlayNeverStartsAboveTheScreen() {
        assertEquals(0,NotificationRules.overlayTop(24,16f,3f))
        assertEquals(0,NotificationRules.overlayTop(0,16f,3f))
        assertEquals(24,NotificationRules.overlayTop(72,16f,3f))
        assertEquals(72,NotificationRules.overlayTop(24,-16f,3f))
    }
    @Test fun codesRequireContextAndDigitBoundaries() {
        assertEquals("123456",NotificationRules.code("Your verification code is 123456"))
        assertNull(NotificationRules.code("Your order is 123456"))
        assertNull(NotificationRules.code("OTP 123456789"))
        assertEquals("4321",NotificationRules.code("PIN: 4321"))
    }
    @Test fun callDurationUsesConnectedChronometerRatherThanRingingTime() {
        assertEquals(70000L, NotificationRules.callStart(970000L,true,false,1000000L,100000L))
        assertNull(NotificationRules.callStart(970000L,true,true,1000000L,100000L))
        assertNull(NotificationRules.callStart(970000L,false,false,1000000L,100000L))
        assertNull(NotificationRules.callStart(1000001L,true,false,1000000L,100000L))
    }
    @Test fun callActionLabelsOverrideOrder() { assertEquals("decline",NotificationRules.actionKind("Hang up")); assertEquals("accept",NotificationRules.actionKind("Answer")); assertEquals("read",NotificationRules.actionKind("Mark as read")) }
}
