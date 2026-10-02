package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Original CPU boundaries in world-status-bit4-outgoing.json; not App acceptance. */
class OriginalStatusOutgoingTest {
    @Test fun physicalOutputUsesTheOriginalBitFourGateAndByteSumExceptions() {
        val cases=listOf(0 to 0,1 to 1,2 to 1,3 to 1,11 to 5,16 to 8,255 to 127,
            256 to 256,257 to 128,511 to 255,512 to 256,32768 to 16384,
            65281 to 32640,65282 to 65282,65535 to 32767)
        for((before,after) in cases) {
            assertEquals("status04 damage=$before",after,OriginalStatus.outgoingPhysicalDamage(before,4))
            assertEquals("combined mask damage=$before",after,OriginalStatus.outgoingPhysicalDamage(before,6))
            for(mask in listOf(0,1,2,8,16,32,64))
                assertEquals("mask=$mask damage=$before",before,OriginalStatus.outgoingPhysicalDamage(before,mask))
        }
    }
    @Test fun byteSumCarryIsDiscardedBeforeComparingOne() {
        // Original ADC: FF+02 ->01 with carry; CMP ignores that carry.
        assertEquals(0xff02,OriginalStatus.outgoingPhysicalDamage(0xff02,4))
        assertEquals(0x7f81,OriginalStatus.outgoingPhysicalDamage(0xff03,4))
        assertEquals(0x0100,OriginalStatus.outgoingPhysicalDamage(0x0100,4))
        assertEquals(0x0080,OriginalStatus.outgoingPhysicalDamage(0x0101,4))
    }
    @Test fun originalPhysicalResultIsAdjustedAfterDefenseAndMultiplierExactlyOnce() {
        // Multiplier thresholds isolate 1x at roll0 and 4x at roll1, with no RNG in either helper.
        val rules=PhysicalRules(mapOf(-1 to 64),List(36){1})
        val cases=listOf(
            intArrayOf(1,2,0,1,1), // Negative subtraction goes to minimum1 before the gate.
            intArrayOf(10,10,0,0,0), // Exact defense retains original zero.
            intArrayOf(11,0,0,11,5),
            intArrayOf(11,0,1,44,22), // Halving attack first would incorrectly give20.
            intArrayOf(300,44,0,256,256),
            intArrayOf(16384,0,1,0,0), // Original multiplier wraps before the status rule.
            intArrayOf(65282,0,0,65282,65282))
        for((attack,defense,roll,before,after) in cases) {
            val computed=rules.damage(attack,defense,1,roll)
            assertEquals(before,computed)
            assertEquals(after,OriginalStatus.outgoingPhysicalDamage(computed,4))
        }
    }
    @Test fun inputsOutsideOriginalWordAndStatusByteAreRejected() {
        for(damage in listOf(-1,65536))assertThrows(IllegalArgumentException::class.java){
            OriginalStatus.outgoingPhysicalDamage(damage,4)
        }
        for(mask in listOf(-1,256))assertThrows(IllegalArgumentException::class.java){
            OriginalStatus.outgoingPhysicalDamage(11,mask)
        }
    }
    @Test fun enemyIceUsesIncomingGateAndMustNotUsePlayerPhysicalOutputGate() {
        // Actual enemy-special3 CPU calls: Boss137/138/139/141, no armor subtraction.
        for((base,affected) in listOf(8 to 16,10 to 20,13 to 26,18 to 36)) {
            assertEquals(base,OriginalStatus.incomingDamage(base,0))
            assertEquals(affected,OriginalStatus.incomingDamage(base,4))
            assertNotEquals(affected,OriginalStatus.outgoingPhysicalDamage(base,4))
        }
    }
}
