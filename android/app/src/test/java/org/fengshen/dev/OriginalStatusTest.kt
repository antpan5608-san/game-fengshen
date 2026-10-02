package org.fengshen.dev
import org.junit.Test
import org.junit.Assert.*
class OriginalStatusTest {
    private val hero=CharacterState("nezha",1,0,5,20,0,8,4,5,3)
    private val item=ItemDefinition(AntidoteUse.ID,"牛黃丸",null,"GAMEPLAY_VERIFIED",category="medicine",originalId=6,
        antidoteUse=AntidoteUseDefinition("game-data/provenance/world-status.json"))
    @Test fun poisonUsesOriginalRankAndOneCompletedStep(){
        for(mask in listOf(0,2,4,8))assertEquals(2,OriginalStatus.poison(hero.copy(statusMask=mask)).statusMask)
        for(mask in listOf(1,16,32,64))assertEquals(mask,OriginalStatus.poison(hero.copy(statusMask=mask)).statusMask)
        val poisoned=hero.copy(hp=1,statusMask=2)
        val dead=OriginalStatus.step(listOf(poisoned)).single();assertEquals(0,dead.hp);assertEquals(32,dead.statusMask)
        assertEquals(dead,OriginalStatus.step(listOf(dead)).single());assertEquals(hero,OriginalStatus.step(listOf(hero)).single())
    }
    @Test fun poisonChoiceUsesExactByteGates(){
        for(r in 0..255)assertEquals((r and 127)<41,OriginalStatus.choosesPoison(r))
        assertTrue(OriginalStatus.allDisabled(listOf(hero.copy(hp=0,statusMask=32))))
        assertFalse(OriginalStatus.allDisabled(listOf(hero.copy(statusMask=16))))
    }
    @Test fun antidoteReproducesObservedConditionalSecondConsumption(){
        for(count in listOf(1,3)){
            val poisoned=hero.copy(statusMask=2)
            val r=AntidoteUse.apply(listOf(poisoned),mapOf(item.id to count),hero.id,item,true)
            assertTrue(r.applied);assertEquals(0,r.characters.single().statusMask);assertEquals(5,r.characters.single().hp)
            assertEquals(maxOf(0,count-2),r.inventory[item.id]?:0)
        }
        for(mask in listOf(0,1,8,32)){
            val target=hero.copy(hp=if(mask==32)0 else 5,statusMask=mask)
            val r=AntidoteUse.apply(listOf(target),mapOf(item.id to 3),hero.id,item,true)
            assertEquals(target,r.characters.single());assertEquals(2,r.inventory[item.id])
        }
    }
    @Test fun failedAntidoteCommandsNeverConsumeOrModifyOtherTargets(){
        for(target in listOf("missing",hero.id)){
            val r=AntidoteUse.apply(listOf(hero),emptyMap(),target,item,true)
            assertFalse(r.applied);assertEquals(listOf(hero),r.characters);assertTrue(r.inventory.isEmpty())
        }
        assertFalse(AntidoteUse.available(listOf(hero),mapOf(item.id to 3),hero.id,item,false))
        val poisoned=hero.copy(statusMask=6);val other=hero.copy(id="other",statusMask=2)
        val r=AntidoteUse.apply(listOf(poisoned,other),mapOf(item.id to 3),hero.id,item,true)
        assertEquals(4,r.characters.first().statusMask);assertEquals(other,r.characters.last())
    }
    @Test fun poisonBattleUsesOneByteAndSnapshotsBeforeAndAfterTheStatusAction(){
        val enemy=EnemyDefinition(10,"原版敌人 10",30,24,12,10,6,217,7)
        val group=EncounterGroup(0,listOf(EncounterMember(3,10)),4)
        val rules=PhysicalRules(mapOf(-1 to 64,0 to 54,1 to 54,2 to 51),List(36){255})
        val content=BattleContent(16,emptyList(),listOf(group),mapOf(10 to enemy),emptyList(),2,6,50,16,
            mapOf(10 to 6),true,true,physicalRules=rules)
        var calls=0
        val battle=OpeningBattle(group,content,hero,0)
        val turn=battle.attack(3){calls++;0}!!
        assertEquals(2,calls);assertEquals(0,turn.enemyDamage);assertEquals(5,battle.hero.hp)
        assertEquals(2,battle.hero.statusMask)
        assertEquals(0,turn.actions.first().heroStatusMask)
        val status=turn.actions.first{it.kind==BattleActionKind.STATUS};assertEquals(2,status.heroStatusMask)
        assertEquals(30,status.enemyHp[3]);assertEquals(29,turn.actions.last().enemyHp[3])
        assertEquals(2,turn.actions.last().heroStatusMask)
    }
    @Test fun seaRegionBoundariesRetainOriginalDefaultInsteadOfBecomingEncounterFree(){
        val one=EncounterGroup(0,listOf(EncounterMember(3,5)),1)
        val four=EncounterGroup(0,listOf(EncounterMember(3,10)),4)
        val zones=listOf(
            EncounterZone(25,listOf(EncounterRect(2,23,30,63),EncounterRect(31,35,63,63)),listOf(one),16),
            EncounterZone(25,listOf(EncounterRect(2,0,30,22),EncounterRect(31,0,63,35)),listOf(four),16),
            EncounterZone(25,emptyList(),listOf(one),16))
        val c=BattleContent(16,emptyList(),emptyList(),emptyMap(),emptyList(),2,6,50,16,zones=zones)
        for((x,y,expected)in listOf(Triple(26,14,4),Triple(32,35,4),Triple(32,36,1),Triple(31,35,1),Triple(3,23,1))){
            val e=OpeningEncounter(c,50);assertEquals(expected,e.onCompletedStep(25,x,y){0}!!.zoneId)
        }
        assertNull(OpeningEncounter(c,50).onCompletedStep(0,6,25){error("Village must not roll encounters")})
    }
}
