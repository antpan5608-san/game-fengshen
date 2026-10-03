package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class OriginalPartyRulesTest {
    private fun actor(id:Int,hp:Int=50,status:Int=0,agility:Int=20,command:Int=0,slot:Int=id)=
        OriginalPartyRules.Actor(id,slot,hp,status,agility,command)
    private fun enemy(slot:Int,hp:Int=30,agility:Int=20)=OriginalPartyRules.Enemy(slot,hp,agility)

    @Test fun normalOriginalFightUsesOwnerIdentityAndSharesOddExperience(){
        // Controller-only original: girl 26 > Nezha 14 > enemy18 in slot3 at 13.
        val party=listOf(actor(0,hp=50,agility=14),actor(1,hp=92,agility=26))
        assertEquals(listOf(1,0,131),OriginalPartyRules.actionOrder(party,listOf(enemy(3,hp=38,agility=13))))
        assertEquals(mapOf(0 to 19,1 to 19),OriginalPartyRules.experienceShares(listOf(39),party))
        assertNull(OriginalPartyRules.retargetEnemy(3,listOf(enemy(3,hp=0)),listOf(1,0,131)))
    }

    @Test fun agilityTiesKeepOriginalIdentityOrderEvenWhenInputListsAreReordered(){
        val party=listOf(actor(1),actor(0))
        assertEquals(listOf(0,1,129,133),OriginalPartyRules.actionOrder(party,listOf(enemy(5),enemy(1))))
        // Dead slots stay in the sort and are filtered immediately before acting.
        assertEquals(listOf(0,1,131),OriginalPartyRules.actionOrder(listOf(actor(0,hp=0),actor(1)),listOf(enemy(3))))
        assertFalse(OriginalPartyRules.canAct(actor(0,hp=0)))
    }

    @Test fun numericCommand3HasPriorityButDoesNotSortAgainstAnotherCommand3(){
        val ordinary=enemy(0,agility=255)
        assertEquals(listOf(0,128,1),OriginalPartyRules.actionOrder(listOf(actor(0,agility=1,command=3),actor(1,agility=80)),listOf(ordinary)))
        assertEquals(listOf(0,1,128),OriginalPartyRules.actionOrder(listOf(actor(1,agility=200,command=3),actor(0,agility=1,command=3)),listOf(ordinary)))
        assertEquals(listOf(1,128,0),OriginalPartyRules.actionOrder(listOf(actor(0,agility=1),actor(1,agility=2,command=3)),listOf(ordinary)))
    }

    @Test fun collectionAndExecutionUseTheirSeparateOriginalStatusAndHpGates(){
        for(id in 0..1)for(status in listOf(0,2,4,8,16,32,6,12)) {
            val living=actor(id,status=status)
            assertEquals(status and 0x38==0,OriginalPartyRules.collectsCommand(living))
            assertEquals(status and 0x38==0,OriginalPartyRules.canAct(living))
            assertFalse(OriginalPartyRules.canAct(living.copy(hp=0)))
        }
    }

    @Test fun enemyTargetsShareTheSuppliedActionByteAndWrapToLivingActors(){
        // CPU cases covered all 256 bytes, both input carries and 4 living subsets.
        for(byte in 0..255) {
            val expected=(byte ushr 2) and 1
            assertEquals(expected,OriginalPartyRules.enemyTarget(listOf(actor(0,status=32),actor(1,status=16)),byte))
            assertEquals(0,OriginalPartyRules.enemyTarget(listOf(actor(0),actor(1,hp=0)),byte))
            assertEquals(1,OriginalPartyRules.enemyTarget(listOf(actor(0,hp=0),actor(1)),byte))
            assertNull(OriginalPartyRules.enemyTarget(listOf(actor(0,hp=0),actor(1,hp=0)),byte))
        }
    }

    @Test fun deadEnemyRetargetUsesCurrentAgilityOrderNotSlotNumberOrDefinition(){
        val enemies=listOf(enemy(2),enemy(3,hp=0),enemy(5))
        val order=listOf(1,0,133,130,131)
        assertEquals(5,OriginalPartyRules.retargetEnemy(3,enemies,order))
        assertEquals(2,OriginalPartyRules.retargetEnemy(2,enemies,order))
        assertEquals(2,OriginalPartyRules.retargetEnemy(5,enemies.map{if(it.slot==5)it.copy(hp=0)else it},order))
        assertNull(OriginalPartyRules.retargetEnemy(3,enemies.map{it.copy(hp=0)},order))
    }

    @Test fun experienceIncludesLivingAbnormalActorsButNotDeadActors(){
        for(status in listOf(0,2,4,8,16,32)) {
            assertEquals(mapOf(0 to 19,1 to 19),OriginalPartyRules.experienceShares(listOf(39),listOf(actor(0,status=status),actor(1,status=status))))
            assertEquals(mapOf(1 to 39),OriginalPartyRules.experienceShares(listOf(39),listOf(actor(0,hp=0),actor(1,status=status))))
        }
        assertEquals(emptyMap<Int,Int>(),OriginalPartyRules.experienceShares(listOf(39),listOf(actor(0,hp=0),actor(1,hp=0))))
        // Original total is a word, not unbounded summed XP.
        assertEquals(mapOf(0 to 1,1 to 1),OriginalPartyRules.experienceShares(listOf(65535,3),listOf(actor(0),actor(1))))
    }

    @Test fun recoveryChainsTheSharedByteAndSetsCarryForEachPresentActor(){
        // First ROR: 2→129 (retain); second:129→192 (clear), not a fresh ROR of2.
        val actual=OriginalPartyRules.recoverStatuses(listOf(actor(1,status=8),actor(0,status=4)),2)
        assertEquals(mapOf(0 to 4,1 to 0),actual.statusByActorIndex)
        assertEquals(192,actual.randomByte)
        // Recovering a combined mask clears the whole byte, including poison.
        assertEquals(mapOf(0 to 0,1 to 2),OriginalPartyRules.recoverStatuses(listOf(actor(0,status=6),actor(1,status=2)),0).statusByActorIndex)
        val unchanged=OriginalPartyRules.recoverStatuses(listOf(actor(0,status=2),actor(1,status=32)),53)
        assertEquals(mapOf(0 to 2,1 to 32),unchanged.statusByActorIndex)
        assertEquals(53,unchanged.randomByte)
    }

    @Test fun recoveryCoversTheOriginalCpuByteAndStatusCrossProduct(){
        for(s0 in listOf(0,2,4,8,16,32))for(s1 in listOf(0,2,4,8,16,32))for(byte in 0..255) {
            // Bit1 of the initial byte decides actor0; for actor1 it is bit2 when
            // actor0 rotated, bit1 otherwise. Neither formula samples another byte.
            val rotates0=s0 and 12!=0;val rotates1=s1 and 12!=0
            val s0After=if(rotates0 && byte and 2==0)0 else s0
            val s1After=if(rotates1 && byte and (if(rotates0)4 else 2)==0)0 else s1
            val rotations=(if(rotates0)1 else 0)+(if(rotates1)1 else 0)
            val expectedByte=when(rotations){0->byte;1->(byte ushr 1) or 128;else->(byte ushr 2) or 192}
            val result=OriginalPartyRules.recoverStatuses(listOf(actor(0,status=s0),actor(1,status=s1)),byte)
            assertEquals(mapOf(0 to s0After,1 to s1After),result.statusByActorIndex)
            assertEquals(expectedByte,result.randomByte)
        }
    }

    @Test fun multiplierUsesTheActualOwnersThresholdsAndOriginalStrictComparison(){
        // Actual CPU Lv13/raw12 Nezha [179,255,0], Lv12/raw11 girl [206,255,0].
        val nezha=MutableList(36){0}.apply{this[2]=179;this[14]=255}
        val girl=MutableList(36){0}.apply{this[2]=206;this[14]=255}
        val tables=mapOf(0 to nezha,1 to girl)
        for(byte in 0..255) {
            assertEquals(if(byte<179)1 else if(byte<255)2 else 4,OriginalPartyRules.physicalMultiplier(0,12,byte,tables))
            assertEquals(if(byte<206)1 else if(byte<255)2 else 4,OriginalPartyRules.physicalMultiplier(1,11,byte,tables))
        }
        val fixture=MutableList(36){0}.apply{this[11]=7;this[23]=13;this[35]=21}
        assertEquals(2,OriginalPartyRules.physicalMultiplier(1,79,7,mapOf(1 to fixture)))
        assertEquals(4,OriginalPartyRules.physicalMultiplier(1,255,21,mapOf(1 to fixture)))
    }

    @Test fun helpersDoNotMutateInputsOrSilentlySubstituteMissingOwnerData(){
        val actors=listOf(actor(0,status=4),actor(1,status=8));val before=actors.toList()
        OriginalPartyRules.recoverStatuses(actors,0);OriginalPartyRules.experienceShares(listOf(39),actors)
        assertEquals(before,actors)
        fun rejected(action:()->Unit){try{action();fail("Expected invalid input rejection")}catch(_:IllegalArgumentException){}}
        rejected{OriginalPartyRules.physicalMultiplier(1,11,0,mapOf(0 to List(36){255}))}
        rejected{OriginalPartyRules.enemyTarget(listOf(actor(0),actor(0,slot=1)),0)}
        rejected{OriginalPartyRules.actionOrder(listOf(actor(0)),listOf(enemy(3),enemy(3)))}
        rejected{OriginalPartyRules.enemyTarget(actors,256)}
        rejected{OriginalPartyRules.retargetEnemy(1,listOf(enemy(1)),listOf(0))}
    }
}
