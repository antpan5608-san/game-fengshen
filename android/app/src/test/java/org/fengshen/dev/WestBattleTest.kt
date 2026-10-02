package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Controlled rules fixtures; original sources are world-west-services01.json. */
class WestBattleTest {
    private val west=EnemyDefinition(138,"西海龍王",150,21,17,80,150,217,3,10,
        BattleLoot("rom.medicine.1",50,"medicine"))
    private val group=EncounterGroup(155,listOf(EncounterMember(3,138)))
    private val hero=CharacterState("nezha",8,874,57,57,0,20,12,20,4,0,EquipmentState(3,-1,0,28))
    private val physical=PhysicalRules(mapOf(-1 to 64,0 to 54,1 to 54,2 to 51,3 to 51),List(36){255})
    private val content=BattleContent(16,emptyList(),emptyList(),mapOf(138 to west),emptyList(),2,6,50,16,
        mapOf(138 to 12),true,true,physicalRules=physical)
    @Test fun westUsesItsActualIceValueAndCurrentStepSnapshots(){
        var reads=0;val battle=OpeningBattle(group,content,hero,16)
        val turn=battle.attack(3){reads++;0}!!
        assertEquals(2,reads);assertEquals(19,turn.playerDamage);assertEquals(10,turn.enemyDamage)
        assertEquals(57,turn.actions.first().heroHp);assertEquals(150,turn.actions.first().enemyHp[3])
        assertEquals(131,turn.actions.first{it.kind==BattleActionKind.ICE}.enemyHp[3])
        assertEquals(47,turn.actions.last().heroHp)
    }
    @Test fun failedBossEscapeConsumesTheOriginalActionAndEnemyResponse(){
        var reads=0;val battle=OpeningBattle(group,content,hero,16)
        val turn=battle.escape{reads++;0}!!
        assertEquals(2,reads);assertEquals(BattlePhase.TARGET,turn.phase);assertEquals(10,turn.enemyDamage)
        assertEquals(150,battle.enemies.single().hp)
        assertTrue(turn.actions.any{it.kind==BattleActionKind.ESCAPE_FAILED})
    }
    @Test fun oneVictoryRewardsOnceAndKeepsTheOriginalDropIdentity(){
        val battle=OpeningBattle(group,content,hero.copy(strength=250),16)
        val turn=battle.attack(3){0}!!;assertEquals(BattlePhase.VICTORY,turn.phase)
        val reward=battle.settle(123)!!;assertEquals(273,reward.money);assertEquals(80,reward.experience)
        assertNull(battle.settle(123));assertEquals("rom.medicine.1",west.loot!!.itemId)
        assertFalse(OriginalStatus.enemySupported(west.copy(iceBaseDamage=null)))
    }
    @Test fun fishboneUsesTheActualRightHandHitBoundary(){
        for(value in 0..255)assertEquals((value and 63)<51,physical.hits(3,value))
        assertTrue(physical.hits(3,50));assertFalse(physical.hits(3,51))
    }
}
