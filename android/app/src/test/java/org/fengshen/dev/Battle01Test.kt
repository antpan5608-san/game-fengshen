package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class Battle01Test {
    private val enemies=mapOf(
        1 to EnemyDefinition(1,"enemy1",8,5,1,1,1,230,0),
        2 to EnemyDefinition(2,"enemy2",10,7,2,2,1,179,0),
        3 to EnemyDefinition(3,"enemy3",11,9,3,3,2,204,0))
    private val group=EncounterGroup(11,listOf(EncounterMember(2,2),EncounterMember(4,3)))
    private val content=BattleContent(16,listOf(EncounterRect(193,128,216,151),EncounterRect(187,100,221,117)),
        listOf(group),enemies,listOf(GrowthRow(2,12,3,0,2,1,1,0,true),GrowthRow(3,27,3,0,2,1,1,1,true)),
        2,6,50,16)
    private val hero=CharacterState("nezha",1,0,20,20,0,8,4,2,4,0,EquipmentState(0,-1,0,28))

    @Test fun zoneCoordinatesAndFinishedStepsOnly(){
        val encounter=OpeningEncounter(content)
        repeat(5){assertNull(encounter.onCompletedStep(114,201,151){0})}
        assertEquals(5,encounter.steps)
        assertNull(encounter.onCompletedStep(16,193,151){0}) // lower bound exclusive
        assertNull(encounter.onCompletedStep(16,201,152){0}) // first rectangle upper Y is 151
        assertEquals(group,encounter.onCompletedStep(16,201,151){0})
        assertEquals(0,encounter.steps)
        repeat(49){assertNull(encounter.onCompletedStep(16,201,151){255})}
        assertEquals(group,encounter.onCompletedStep(16,201,151){255}) // ROM counter 50 bypasses byte gate
    }
    @Test fun enemyMissAndPhysicalDamageMatchFirstObservedRound(){
        val battle=OpeningBattle(group,content,hero,2)
        val rolls=ArrayDeque(listOf(192,171)) // ROM sample: enemy 2 misses, enemy 3 acts
        val turn=battle.attack(2){rolls.removeFirst()}
        assertEquals(8,turn?.playerDamage)
        assertEquals(3,turn?.enemyDamage)
        assertEquals(1,turn?.enemyMisses)
        assertEquals(17,battle.hero.hp)
        assertEquals(2,battle.enemies.first{it.slot==2}.hp)
        assertNull(battle.attack(6){0}) // invalid target has no effect
        assertEquals(17,battle.hero.hp)
    }
    @Test fun victoryRewardsAreGroupSumAndSettleOnce(){
        val battle=OpeningBattle(group,content,hero.copy(experience=11),2)
        battle.attack(2){255};battle.attack(2){255}
        battle.attack(4){255};val last=battle.attack(4){255}
        assertEquals(BattlePhase.VICTORY,last?.phase)
        assertEquals(listOf(2,3),battle.enemies.map{it.definition.id})
        val settled=battle.settle(10)
        assertEquals(5,settled?.experience)
        assertEquals(13,settled?.money)
        assertEquals(16,settled?.character?.experience)
        assertEquals(2,settled?.character?.level)
        assertEquals(23,settled?.character?.maxHp)
        assertEquals(listOf(2),settled?.levels)
        assertNull(battle.settle(10))
        assertNull(battle.attack(4){0})
    }
    @Test fun thresholdCrossingUsesCumulativeRomValues(){
        val battle=OpeningBattle(group,content,hero.copy(experience=23),2)
        battle.attack(2){255};battle.attack(2){255};battle.attack(4){255};battle.attack(4){255}
        val reward=battle.settle(0)!!
        assertEquals(28,reward.character.experience)
        assertEquals(listOf(2,3),reward.levels)
        assertEquals(26,reward.character.maxHp)
        assertEquals(12,reward.character.strength)
    }
    @Test fun unequippedKnifeDoesNotGrantWeaponBonus(){
        val without=OpeningBattle(group,content,hero.copy(equipment=EquipmentState(-1,-1,0,28)),0)
        val turn=without.attack(2){255}
        assertEquals(6,turn?.playerDamage)
    }
    @Test fun defeatNeverSettlesRewards(){
        val battle=OpeningBattle(group,content,hero.copy(hp=1),2)
        assertEquals(BattlePhase.DEFEAT,battle.attack(2){0}?.phase)
        assertEquals(0,battle.hero.hp)
        assertNull(battle.settle(100))
        assertNull(battle.attack(2){0})
    }
    @Test fun worldPublishesOneCompletedStepEvenWhenStickRemainsHeld(){
        val w=4;val h=4;val scene=Scene("test",w,h,IntArray(w*h),IntArray(w*h),(0 until w*h).toSet(),1,1)
        val world=World(scene)
        repeat(8){world.tick(Key.RIGHT)}
        assertEquals(1L,world.completedStepSeq)
        assertEquals(CompletedStep(114,2,1,false),world.lastCompletedStep)
        repeat(30){world.tick(null)}
        assertEquals(1L,world.completedStepSeq)
    }
}
