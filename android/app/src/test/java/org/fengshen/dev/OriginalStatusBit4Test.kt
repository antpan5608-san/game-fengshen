package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Golden boundaries from original mapper/CPU evidence in world-status-bit4.json. */
class OriginalStatusBit4Test {
    private val hero=CharacterState("nezha",8,814,40,146,12,30,20,12,5,statusMask=0)

    @Test fun specialUsesTheObservedSameByteIntervals() {
        val observed=(0..40).toSet()+(128..168).toSet()
        assertEquals(observed,(0..255).filter{OriginalStatus.choosesStatus4(it)}.toSet())
        for(byte in listOf(-1,256))assertThrows(IllegalArgumentException::class.java){OriginalStatus.choosesStatus4(byte)}
    }
    @Test fun exactOriginalPriorityPreservesPoisonAndHigherConditions() {
        for((before,after) in listOf(0 to 4,1 to 1,2 to 2,4 to 4,8 to 8,16 to 16,32 to 32,64 to 64,6 to 6)) {
            val original=hero.copy(statusMask=before)
            assertEquals(original.copy(statusMask=after),OriginalStatus.applyStatus4(original))
            assertEquals(before,original.statusMask)
        }
    }
    @Test fun InvalidDeadTargetCannotAcquireAStatusOrReceiveHealing() {
        for(mask in listOf(0,4,32)) {
            val dead=hero.copy(hp=0,statusMask=mask)
            assertEquals(dead,OriginalStatus.applyStatus4(dead))
            assertEquals(OriginalStatus.Status4Recovery(dead,0,false),OriginalStatus.recoverStatus4AtRoundEnd(dead,0))
        }
    }
    @Test fun damageMatchesOriginalPositiveAndMinimumOneBranches() {
        // Original enemy attack29: no defense, body12/stamina12, exact defense, and excess defense.
        val cases=listOf(Triple(29,0,29),Triple(29,4,58),Triple(5,0,5),Triple(5,4,10),
            Triple(1,0,1),Triple(1,4,2),Triple(0,0,1),Triple(0,4,1),
            Triple(-1,0,1),Triple(-1,4,1),Triple(-6,0,1),Triple(-6,4,1))
        for((computed,mask,expected) in cases)assertEquals(expected,OriginalStatus.incomingDamage(computed,mask))
        for(mask in listOf(2,8,16,32,64))assertEquals(5,OriginalStatus.incomingDamage(5,mask))
        assertEquals(65534,OriginalStatus.incomingDamage(32767,4))
        assertEquals(0,OriginalStatus.incomingDamage(32768,4))
        assertEquals(65534,OriginalStatus.incomingDamage(65535,4))
        assertThrows(IllegalArgumentException::class.java){OriginalStatus.incomingDamage(65536,4)}
    }
    @Test fun roundRecoveryUsesObservedRotateAndPreviousBitOne() {
        // These are original CPU outputs, including the injected carry1 at the scheduler boundary.
        val cleared=listOf(Triple(0,128,0),Triple(1,128,0),Triple(4,130,0),Triple(5,130,0),
            Triple(128,192,0),Triple(129,192,0),Triple(252,254,0),Triple(253,254,0))
        val retained=listOf(Triple(2,129,4),Triple(3,129,4),Triple(6,131,4),Triple(7,131,4),
            Triple(130,193,4),Triple(131,193,4),Triple(254,255,4),Triple(255,255,4))
        val original=hero.copy(statusMask=4)
        for((random,nextRandom,status) in cleared+retained) {
            val result=OriginalStatus.recoverStatus4AtRoundEnd(original,random)
            assertTrue(result.rotated)
            assertEquals(nextRandom,result.randomAfter)
            assertEquals(original.copy(statusMask=status),result.character)
        }
        assertEquals(4,original.statusMask)
    }
    @Test fun unrelatedStatusDoesNotConsumeRecoveryRandomOrEnableSleepBehavior() {
        for(mask in listOf(0,1,2,8,16,32,64))for(random in listOf(0,3,128,255)) {
            val original=hero.copy(statusMask=mask)
            assertEquals(OriginalStatus.Status4Recovery(original,random,false),OriginalStatus.recoverStatus4AtRoundEnd(original,random))
        }
    }
    @Test fun aRecoveredStateRequiresNoFurtherStatus4Recovery() {
        val recovered=OriginalStatus.recoverStatus4AtRoundEnd(hero.copy(statusMask=4),0)
        assertEquals(hero,recovered.character)
        assertEquals(OriginalStatus.Status4Recovery(hero,128,false),OriginalStatus.recoverStatus4AtRoundEnd(recovered.character,recovered.randomAfter))
    }
    @Test fun mapStepsPreserveBit4WithoutPoisonDamageOrInventedCure() {
        val affected=hero.copy(statusMask=4)
        assertEquals(listOf(affected),OriginalStatus.step(listOf(affected)))
        assertFalse(OriginalStatus.allDisabled(listOf(affected)))
        assertEquals("异常 04",OriginalStatus.label(4))
        // Helpers alone do not authorize behavior9 while the actual scheduler is not integrated.
        assertFalse(OriginalStatus.enemySupported(EnemyDefinition(16,"原版敌人 16",44,29,15,36,12,217,9)))
    }
}
