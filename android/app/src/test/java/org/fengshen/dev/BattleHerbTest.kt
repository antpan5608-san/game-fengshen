package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Scoped normal reference + explicit original-state experiments in mobile-play01-battle-herb.json. */
class BattleHerbTest {
    private val boss=EnemyDefinition(137,"南海龍王",120,16,13,60,100,243,3,8)
    private val group=EncounterGroup(156,listOf(EncounterMember(3,137)))
    private val rules=PhysicalRules(mapOf(-1 to 64,0 to 54,2 to 51),List(36){255})
    private val content=BattleContent(16,emptyList(),listOf(group),mapOf(137 to boss),emptyList(),3,6,50,16,
        enemyAgility=mapOf(137 to 8),escapeEnabled=true,physicalRules=rules)
    private val item=ItemDefinition(HerbUse.ID,"藥草",null,"scoped-original-evidence",category="medicine",originalId=0,buyPrice=15,herbUse=HerbUseDefinition(50,true,"existing map definition + scoped battle provenance"))
    private fun hero(hp:Int=30,agility:Int=2)=CharacterState("nezha",2,12,hp,100,0,8,4,agility,4,0,EquipmentState(2,-1,0,28))
    @Test fun fasterEnemyActsThenHerbHealsAndConsumesNoPlayerRandom(){
        val b=OpeningBattle(group,content,hero(),20,3);var rolls=0
        val turn=b.useHerb("nezha",3,item){rolls++;96}!!
        assertEquals(1,rolls);assertEquals(1,b.herbsConsumed);assertEquals(71,b.hero.hp)
        assertEquals(3,turn.actions.first().actorSlot)
        val heal=turn.actions.single{it.kind==BattleActionKind.HEAL}
        assertEquals(21,heal.beforeHeroHp);assertEquals(71,heal.heroHp);assertEquals(50,heal.hpDelta)
        assertEquals(HerbUse.ID,heal.abilityId)
        assertEquals(BattleVisualPose.IDLE,battleVisualPose(heal,"nezha"))
        assertTrue(turn.actions.filter{it.actorSlot!=null}.all{it.abilityId==null})
        assertEquals(9,turn.enemyDamage);assertEquals(0,turn.playerDamage);assertEquals(120,b.enemies.single().hp)
    }
    @Test fun fullHealthStillConsumesAndEnemyContinuesAfterEqualAgilityPlayer(){
        val b=OpeningBattle(group,content,hero(100,8),20,3);var rolls=0
        val turn=b.useHerb("nezha",1,item){rolls++;96}!!
        assertEquals(BattleActionKind.HEAL,turn.actions.first().kind);assertEquals(0,turn.actions.first().hpDelta)
        assertEquals(91,b.hero.hp);assertEquals(1,rolls);assertEquals(1,b.herbsConsumed)
        assertTrue(b.inventoryAfterBattle(mapOf(HerbUse.ID to 1)).isEmpty())
    }
    @Test fun capUsesHealthAtOrderedActionNotHealthAtSelection(){
        val b=OpeningBattle(group,content,hero(95),20,3)
        val t=b.useHerb("nezha",3,item){96}!!;val heal=t.actions.single{it.kind==BattleActionKind.HEAL}
        assertEquals(86,heal.beforeHeroHp);assertEquals(100,heal.heroHp);assertEquals(14,heal.hpDelta)
        val input=mapOf(HerbUse.ID to 3,"rom.weapon.2" to 1)
        assertEquals(mapOf(HerbUse.ID to 2,"rom.weapon.2" to 1),b.inventoryAfterBattle(input));assertEquals(3,input[HerbUse.ID])
    }
    @Test fun confirmedItemRemainsConsumedIfFasterEnemyKillsActor(){
        val b=OpeningBattle(group,content,hero(5),20,3)
        val t=b.useHerb("nezha",3,item){96}!!
        assertEquals(BattlePhase.DEFEAT,t.phase);assertEquals(1,b.herbsConsumed);assertEquals(0,b.hero.hp)
        assertFalse(t.actions.any{it.kind==BattleActionKind.HEAL});assertEquals(2,b.inventoryAfterBattle(mapOf(HerbUse.ID to 3))[HerbUse.ID])
    }
    @Test fun unavailableTargetsInventoryAndOtherItemsHaveNoSideEffects(){
        val b=OpeningBattle(group,content,hero(),20,3)
        val rng={error("Rejected command must not read RNG");0}
        assertNull(b.useHerb("nezha",0,item,rng));assertNull(b.useHerb("other",3,item,rng))
        assertNull(b.useHerb("nezha",3,item.copy(id="rom.medicine.6"),rng))
        assertEquals(0,b.herbsConsumed);assertEquals(30,b.hero.hp)
        val dead=OpeningBattle(group,content,hero(0),20,3);assertNull(dead.useHerb("nezha",3,item,rng));assertEquals(0,dead.herbsConsumed)
        val one=OpeningBattle(group,content,hero(),20,3);one.useHerb("nezha",1,item){255}
        assertNull(one.useHerb("nezha",1,item,rng));assertEquals(1,one.herbsConsumed)
    }
}
