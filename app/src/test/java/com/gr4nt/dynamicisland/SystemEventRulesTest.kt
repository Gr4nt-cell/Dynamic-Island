package com.gr4nt.dynamicisland
import org.junit.Assert.*
import org.junit.Test
class SystemEventRulesTest {
    @Test fun chargingUsesEitherSignalOnce() {
        val rules=SystemEventRules()
        assertNull(rules.battery(70,false,10000))
        assertEquals(IslandEvent.CHARGING,rules.battery(70,true,11000))
        assertFalse(rules.powerConnected(11100))
        rules.battery(70,false,14000)
        assertTrue(rules.powerConnected(17000))
        assertNull(rules.battery(71,true,17100))
    }
    @Test fun lowBatteryCrossingDoesNotRequireExactlyTwentyAndDeduplicatesBroadcast() {
        val rules=SystemEventRules()
        rules.battery(21,false,10000)
        assertEquals(IslandEvent.LOW_BATTERY,rules.battery(19,false,11000))
        assertFalse(rules.lowBattery())
        assertNull(rules.battery(18,false,12000))
        rules.battery(22,true,13000)
        assertFalse(rules.lowBattery())
        rules.battery(21,false,14000)
        assertEquals(IslandEvent.LOW_BATTERY,rules.battery(20,false,15000))
    }
    @Test fun broadcastAndKeyguardConfirmationProduceOneUnlock() {
        val rules=SystemEventRules()
        assertTrue(rules.unlocked(10000));assertFalse(rules.unlocked(10250));assertTrue(rules.unlocked(14000))
    }
}
