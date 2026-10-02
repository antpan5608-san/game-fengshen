package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Isolated scheduler fixtures; not evidence of a normal Android route. Original CPU
 * cases and range hashes: game-data/provenance/world-status-bit4.json. */
class Status4RoundTest {
    private val statusEnemy=EnemyDefinition(16,"原版敌人 16",200,29,15,36,12,217,9)
    private val physical=EnemyDefinition(13,"妖螺",200,28,14,21,10,230,0)
    private val rules=PhysicalRules(mapOf(-1 to 64),List(36){255})
    private val hero=CharacterState("nezha",9,1011,100,100,0,20,12,9,8)
    private class Bytes(vararg values:Int){
        private val queue=ArrayDeque(values.toList());var calls=0
        fun next():Int { calls++;check(queue.isNotEmpty()){ "Extra RNG read $calls" };return queue.removeFirst() }
        fun exhausted()=assertTrue("Missing RNG read",queue.isEmpty())
    }
    private fun battle(h:CharacterState=hero,enemies:List<EnemyDefinition> = listOf(statusEnemy),
        speeds:List<Int> = List(enemies.size){10},armor:Int=0):OpeningBattle {
        val group=EncounterGroup(0,enemies.mapIndexed{i,e->EncounterMember(i,e.id)},6)
        val content=BattleContent(16,emptyList(),listOf(group),enemies.associateBy{it.id},emptyList(),2,6,50,16,
            enemies.mapIndexed{i,e->e.id to speeds[i]}.toMap(),true,true,physicalRules=rules)
        return OpeningBattle(group,content,h,0,armor)
    }
    @Test fun specialAppliesBeforePlayerButOnlyRecoversAfterTheLastActionByte(){
        for((last,expected) in listOf(0 to 0,2 to 4)){
            val bytes=Bytes(0,last);val b=battle();val turn=b.attack(0,bytes::next)!!
            assertEquals(2,bytes.calls);bytes.exhausted()
            assertEquals(0,turn.enemyDamage);assertEquals(100,b.hero.hp)
            val status=turn.actions.first{it.kind==BattleActionKind.STATUS}
            assertEquals(4,status.heroStatusMask);assertEquals(200,status.enemyHp[0])
            assertEquals(2,turn.playerDamage) // 5 >> 1; status04 still executes the selected command.
            assertEquals(expected,b.hero.statusMask);assertEquals(expected,turn.actions.last().heroStatusMask)
        }
    }
    @Test fun subsequentEnemyDamageDoublesBeforeTheSingleCompletedRoundRecovery(){
        val bytes=Bytes(0,41,0)
        val b=battle(enemies=listOf(statusEnemy,physical),speeds=listOf(12,10))
        val turn=b.attack(0,bytes::next)!!;bytes.exhausted()
        assertEquals(32,turn.enemyDamage);assertEquals(68,b.hero.hp);assertEquals(0,b.hero.statusMask)
        val damage=turn.actions.single{it.kind==BattleActionKind.DAMAGE&&it.actorSlot==1}
        assertEquals(4,damage.heroStatusMask);assertEquals(100,damage.beforeHeroHp);assertEquals(68,damage.heroHp)
        assertEquals(0,turn.actions.last().heroStatusMask);assertEquals(3,bytes.calls)
    }
    @Test fun minimumOneBypassesDoublingAndExactPoisonPriorityIsPreserved(){
        var bytes=Bytes(41,2);var b=battle(hero.copy(statusMask=4),armor=99)
        assertEquals(1,b.attack(0,bytes::next)!!.enemyDamage);assertEquals(4,b.hero.statusMask);bytes.exhausted()
        bytes=Bytes(0,0);b=battle(hero.copy(statusMask=2))
        val turn=b.attack(0,bytes::next)!!;bytes.exhausted()
        assertEquals(2,b.hero.statusMask);assertEquals(0,turn.enemyDamage)
        assertFalse(turn.actions.any{it.text=="异常 04 解除"})
    }
    @Test fun knownIceUsesSamePositiveDamageRuleWithoutAnExtraRoll(){
        val ice=physical.copy(id=12,behaviorByte=3,iceBaseDamage=13)
        val bytes=Bytes(0,2);val b=battle(hero.copy(statusMask=4),listOf(ice))
        assertEquals(26,b.attack(0,bytes::next)!!.enemyDamage);assertEquals(74,b.hero.hp)
        assertEquals(4,b.hero.statusMask);bytes.exhausted()
    }
    @Test fun earlierVictoryDoesNotRunEndOfRoundRecoveryOrSampleKilledEnemy(){
        val bytes=Bytes(0);val b=battle(hero.copy(statusMask=4,agility=20,strength=40),
            listOf(statusEnemy.copy(hp=1)))
        assertEquals(BattlePhase.VICTORY,b.attack(0,bytes::next)!!.phase)
        assertEquals(4,b.hero.statusMask);assertEquals(1,bytes.calls);bytes.exhausted()
        assertEquals(4,b.settle(100)!!.character.statusMask);assertNull(b.settle(100))
    }
    @Test fun successfulEscapePreservesStatusAndConsumesOnlyItsActualActionByte(){
        val bytes=Bytes(0);val b=battle(hero.copy(statusMask=4,agility=20))
        assertEquals(BattlePhase.ESCAPED,b.escape(bytes::next)!!.phase)
        assertEquals(4,b.hero.statusMask);assertEquals(1,bytes.calls);bytes.exhausted()
    }
    @Test fun defeatDoesNotRunThePlayerOrTheRecoveryBranch(){
        val bytes=Bytes(41);val b=battle(hero.copy(statusMask=4,hp=1))
        assertEquals(BattlePhase.DEFEAT,b.attack(0,bytes::next)!!.phase)
        assertEquals(0,b.hero.hp);assertEquals(OriginalStatus.DEAD,b.hero.statusMask)
        assertEquals(1,bytes.calls);bytes.exhausted()
    }
    @Test fun herbHasNoRandomReadAndRecoveryUsesTheLastRealEnemyByte(){
        val item=ItemDefinition(HerbUse.ID,"藥草",null,"VERIFIED",category="medicine",originalId=0,
            herbUse=HerbUseDefinition(50,true,"game-data/provenance/town02-herb.json"))
        val bytes=Bytes(0);val b=battle(hero.copy(hp=50))
        val turn=b.useHerb(hero.id,1,item,bytes::next)!!
        assertEquals(100,b.hero.hp);assertEquals(0,b.hero.statusMask);assertEquals(1,b.herbsConsumed)
        assertEquals(listOf(4,4,0),turn.actions.filter{it.kind in setOf(BattleActionKind.STATUS,BattleActionKind.HEAL)}.map{it.heroStatusMask})
        assertEquals(emptyMap<String,Int>(),b.inventoryAfterBattle(mapOf(item.id to 1)))
        assertEquals(1,bytes.calls);bytes.exhausted()
    }
}
