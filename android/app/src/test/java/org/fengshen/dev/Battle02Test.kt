package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class Battle02Test {
    private val enemy=EnemyDefinition(2,"臭甲蟲",10,7,2,2,1,179,0)
    private val group=EncounterGroup(11,listOf(EncounterMember(2,2)))
    private val rules=BattleContent(16,emptyList(),listOf(group),mapOf(2 to enemy),emptyList(),2,6,50,16,mapOf(2 to 2),true,true)
    private val hero=CharacterState("nezha",1,0,20,20,0,8,4,2,4,0,EquipmentState(0,-1,0,28))
    @Test fun originalEscapeSamplesAndNoReward(){
        for((byte,success)in listOf(139 to false,232 to false,190 to false,37 to false,135 to false,3 to true)){
            val battle=OpeningBattle(group,rules,hero,2);var calls=0
            val turn=battle.escape{if(calls++==0)byte else 0}!!
            assertEquals(if(success)BattlePhase.ESCAPED else BattlePhase.TARGET,turn.phase)
            assertEquals(if(success)20 else 19,battle.hero.hp)
            assertEquals(if(success)1 else 2,calls)
            assertNull(battle.settle(123))
            if(success){assertNull(battle.escape{error("No repeat escape RNG")});assertNull(battle.attack(2){0})}
        }
    }
    @Test fun failedEscapeConsumesEnemyTurnAndCanDefeat(){
        val b=OpeningBattle(group,rules,hero.copy(hp=1),2);var n=0
        val t=b.escape{if(n++==0)139 else 0}!!
        assertEquals(BattlePhase.DEFEAT,t.phase);assertEquals(0,b.hero.hp);assertNull(b.settle(100))
    }
    @Test fun legitimateOneHitHasSeparatePresentationAndOneSettlement(){
        val b=OpeningBattle(group,rules,hero.copy(level=4,strength=14),2)
        val p=BattlePresentation();p.tick(400);p.targets()
        val turn=b.attack(2){error("Dead enemy cannot retaliate")}!!
        assertEquals(10,turn.playerDamage);assertEquals(BattlePhase.VICTORY,turn.phase)
        assertEquals(3,turn.actions.size);assertTrue(p.present(turn));assertFalse(p.present(turn))
        assertEquals(BattlePresentation.Screen.ACTING,p.screen)
        repeat(2){assertFalse(p.tick(650))};assertTrue(p.tick(650))
        assertEquals(BattlePresentation.Screen.RESULT,p.screen)
        assertNotNull(b.settle(0));assertNull(b.settle(0));assertFalse(p.tick(10000))
    }
    @Test fun presentationNeverChangesComputedHpOrRandomness(){
        val b=OpeningBattle(group,rules,hero,2);var calls=0
        val p=BattlePresentation();p.tick(400);p.targets();val t=b.attack(2){calls++;0}!!
        assertTrue(p.present(t));val before=b.hero;val enemyHp=b.enemies.map{it.hp}
        repeat(100){p.tick(16)};assertEquals(before,b.hero);assertEquals(enemyHp,b.enemies.map{it.hp});assertEquals(1,calls)
        p.back();assertEquals(BattlePresentation.Screen.ACTING,p.screen)
    }
}
