package com.gr4nt.dynamicisland
import org.junit.Assert.*
import org.junit.Test
class NotificationGateTest {
    @Test fun echoWindowsExpireIndependently() {
        val gate=NotificationGate();gate.suppressReply("message","chat",100)
        assertTrue(gate.blocked("new-message","chat",2599))
        assertFalse(gate.blocked("new-message","chat",2600))
        assertTrue(gate.blocked("message","chat",10099))
        assertFalse(gate.blocked("message","chat",10100))
        assertFalse(gate.blocked("other","mail",101))
    }
    @Test fun deduplicationIsAppScopedAndDoesNotExtendOnEveryEcho() {
        val gate=NotificationGate()
        assertFalse(gate.duplicate("chat","A","hello",0))
        assertTrue(gate.duplicate("chat","A","hello",2499))
        assertFalse(gate.duplicate("chat","A","hello",2500))
        assertFalse(gate.duplicate("mail","A","hello",2501))
        assertFalse(gate.duplicate("chat","A","changed",2502))
    }
    @Test fun failedReplyRestoresNotifications() {
        val gate=NotificationGate();gate.suppressReply("a","chat",0);gate.undoReply("a","chat")
        assertFalse(gate.blocked("a","chat",1))
    }
}
