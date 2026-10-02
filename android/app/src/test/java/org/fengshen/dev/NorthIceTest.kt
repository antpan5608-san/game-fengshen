package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Controlled single-party fixture; normal App/ROM captures are tracked separately. */
class NorthIceTest {
    private val enemy=EnemyDefinition(12,"原版敌人 12",35,26,13,21,9,192,3,13)
    private val group=EncounterGroup(0,listOf(EncounterMember(0,12)),5)
    private val hero=CharacterState("nezha",8,874,80,80,0,20,12,20,4,0,EquipmentState(2,-1,0,28))
    private val content=BattleContent(16,emptyList(),emptyList(),mapOf(12 to enemy),emptyList(),2,6,50,16,
        mapOf(12 to 7),true,true,physicalRules=PhysicalRules(mapOf(2 to 51),List(36){255}))
    @Test fun ordinaryIceUsesItsOwnValueWithExistingRoundAndSnapshots(){
        var reads=0;val b=OpeningBattle(group,content,hero,10)
        val turn=b.attack(0){reads++;0}!!
        assertEquals(2,reads);assertEquals(17,turn.playerDamage);assertEquals(13,turn.enemyDamage)
        assertEquals(80,turn.actions.first{it.kind==BattleActionKind.ICE}.heroHp)
        assertEquals(67,turn.actions.last().heroHp)
        assertEquals(18,turn.actions.last().enemyHp[0])
    }
    @Test fun fullByteSelectionKeepsOneRollAndOriginalPhysicalHitBoundary(){
        for(random in 0..255){
            var reads=0;val b=OpeningBattle(group,content,hero,10)
            val turn=b.attack(0){if(reads++==0)0 else random}!!
            val ice=(random and 127)<41
            assertEquals("random=$random",ice,turn.actions.any{it.kind==BattleActionKind.ICE})
            assertEquals(if(ice)13 else if(random<192)12 else 0,turn.enemyDamage)
            assertEquals(2,reads)
        }
    }
}
