package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Isolated scheduler/capacity fixtures, never evidence of normal App completion. */
class WorldBindingCommandTest {
    private val item=ItemDefinition("rom.special.9","遁龍樁",null,"original",category="special",originalId=9,maxCount=1)
        .also{it.battleBindingUse=BattleBindingUseDefinition("game-data/provenance/world-teacher163-binding.json")}
    private val hero=CharacterState("nezha",12,2000,1000,1000,0,120,30,30,20,equipment=EquipmentState(0,-1,0,28))
    private val girl=hero.copy(id="xiaolongnv",agility=1)
    private val physical=PhysicalRules(mapOf(-1 to 64,0 to 64),List(36){255})
    private fun battle(party:List<CharacterState> = listOf(hero)):OpeningBattle {
        val defs=(152..155).associateWith{id->EnemyDefinition(id,"fixture",1000,1,1,1000,800,hitByte=255,behaviorByte=0)
            .also{it.requiredBindingMarker=1}}
        val group=EncounterGroup(62,(152..155).mapIndexed{i,id->EncounterMember(i*2,id)})
        val c=BattleContent(76,emptyList(),listOf(group),defs,listOf(GrowthRow(13,2500,1,1,1,1,1,1,false)),0,6,50,16,
            enemyAgility=defs.keys.associateWith{2},escapeEnabled=true,physicalRules=physical).also{
            it.characterPhysicalRules=mapOf("xiaolongnv" to physical)
            it.characterGrowth=mapOf("xiaolongnv" to listOf(GrowthRow(13,2500,1,1,1,1,1,1,false)))
        }
        return OpeningBattle(group,c,party.first(),0,0).also{if(party.size>1)it.configureParty(party,
            party.mapIndexed{i,p->p.id to i}.toMap(),party.associate{it.id to 0},party.associate{it.id to 0})}
    }
    @Test fun noDamageBeforeOriginalItemAndQuantityPreservedWhileEnemyRoundStillRuns(){
        val b=battle();var rolls=0
        val before=b.attack(0){rolls++;0}!!;assertEquals(0,before.playerDamage)
        rolls=0;val used=b.useBinding(1,item,false){rolls++;0}!!
        assertEquals(4,rolls);assertEquals(4,used.actions.count{it.kind==BattleActionKind.ATTACK&&it.actorSlot!=null})
        assertEquals(1,used.actions.count{it.kind==BattleActionKind.SPECIAL&&it.actorId==hero.id})
        val inventory=mapOf(item.id to 1,HerbUse.ID to 3);assertEquals(inventory,b.inventoryAfterBattle(inventory))
        assertTrue(b.attack(0){0}!!.playerDamage>0)
        assertEquals(0,battle().attack(0){0}!!.playerDamage) // Marker never leaks into another battle.
    }
    @Test fun collectedBindingUsesOriginalActorOrderNotMenuSelectionTime(){
        for(firstFaster in listOf(false,true)){
            val b=battle(listOf(hero.copy(agility=if(firstFaster)30 else 1),girl.copy(agility=if(firstFaster)1 else 30)))
            assertNull(b.useBinding(1,item,false){error("First command must not resolve or read RNG")})
            assertEquals(1,b.inputRevision);assertEquals(4000,b.enemies.sumOf{it.hp})
            val turn=b.attack(0){0}!!
            assertEquals(firstFaster,turn.playerDamage>0)
            val ordered=turn.actions.filter{it.actorSlot==null&&it.actorId!=null}
            assertEquals(if(firstFaster)hero.id else girl.id,ordered.first().actorId)
        }
    }
    @Test fun missingUsedInvalidOrOtherBattleCannotSubmitOrConsume(){
        val b=battle();val revision=b.inputRevision
        for(q in listOf(0,2,-1))assertNull(b.useBinding(q,item,false){error("Rejected input must not read RNG")})
        assertNull(b.useBinding(1,item,true){error("Used original special cannot be selected")})
        assertNull(b.useBinding(1,item.copy(originalId=8),false){error("Wrong item must not read RNG")})
        assertEquals(revision,b.inputRevision);assertEquals(4000,b.enemies.sumOf{it.hp})
        assertEquals(mapOf(item.id to 1),b.inventoryAfterBattle(mapOf(item.id to 1)))
    }
    @Test fun actorKilledBeforeBindingCannotUnlockDamageOrEraseReusableItem(){
        val b=battle(listOf(hero.copy(hp=1,agility=1),girl.copy(agility=30)))
        assertNull(b.useBinding(1,item,false){error("Collect before resolving")})
        val turn=b.attack(0){0}!!
        assertEquals(0,turn.playerDamage);assertEquals(0,b.party.first().hp)
        assertFalse(turn.actions.any{it.kind==BattleActionKind.SPECIAL&&it.actorId==hero.id})
        assertEquals(mapOf(item.id to 1),b.inventoryAfterBattle(mapOf(item.id to 1)))
        assertEquals(0,b.attack(0){0}!!.playerDamage)
    }
    @Test fun teacherOriginalFirstFlagAndFullCategoryFailureRemainOnceOnly(){
        val rule=OriginalNpcTalkDefinition(163,"rom.map.163.flag.2","",item.id,"rom.dialogue.173.2","rom.dialogue.173.3")
            .also{it.actionId=1}
        val base=SaveSnapshot("fixture",163,120,184,Key.UP,listOf(hero),emptyMap(),mapOf("unrelated" to true),100)
        val rows=javaClass.getResourceAsStream("/world-teacher163-selector.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(256,rows.size)
        for(line in rows){
            val r=line.split('\t').map(String::toInt)
            val flags=(1..128).filter{it and(it-1)==0}.associate{"rom.map.163.flag.$it" to(r[0]and it!=0)}
            val result=OriginalNpcTalk.begin(base.copy(flags=flags),rule,item)
            assertTrue(result.applied);assertEquals("rom.dialogue.173.${r[1]}",result.nextDialogue)
            for(bit in listOf(1,2,4,8,16,32,64,128))assertEquals(r[2]and bit!=0,result.snapshot.flags["rom.map.163.flag.$bit"]==true)
        }
        val full=base.copy(inventory=(0..16).filter{it!=9}.associate{"rom.special.$it" to 1})
        val result=OriginalNpcTalk.begin(full,rule,item)
        assertTrue(result.applied);assertEquals(full.inventory,result.snapshot.inventory);assertTrue(result.snapshot.flags[rule.mapFlagId]==true)
        val retry=OriginalNpcTalk.begin(result.snapshot.copy(inventory=emptyMap()),rule,item)
        assertEquals(rule.repeatDialogue,retry.nextDialogue);assertTrue(retry.snapshot.inventory.isEmpty())
        val valid=OriginalNpcTalk.begin(base,rule,item);assertEquals(1,valid.snapshot.inventory[item.id]);assertEquals(base.money,valid.snapshot.money)
        assertEquals(valid.snapshot,OriginalNpcTalk.begin(valid.snapshot,rule,item).snapshot)
    }
}
