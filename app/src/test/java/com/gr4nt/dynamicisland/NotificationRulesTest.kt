package com.gr4nt.dynamicisland
import org.junit.Assert.*
import org.junit.Test
class NotificationRulesTest {
    @Test fun cameraRectAtScreenEdgeUsesEstimatedLensTop() { assertEquals(8, NotificationRules.cameraTop(0,40,32)); assertEquals(12,NotificationRules.cameraTop(12,44,32)) }
    @Test fun codesRequireContextAndDigitBoundaries() {
        assertEquals("123456",NotificationRules.code("Your verification code is 123456"))
        assertNull(NotificationRules.code("Your order is 123456"))
        assertNull(NotificationRules.code("OTP 123456789"))
        assertEquals("4321",NotificationRules.code("PIN: 4321"))
    }
    @Test fun callActionLabelsOverrideOrder() { assertEquals("decline",NotificationRules.actionKind("Hang up")); assertEquals("accept",NotificationRules.actionKind("Answer")); assertEquals("read",NotificationRules.actionKind("Mark as read")) }
}
