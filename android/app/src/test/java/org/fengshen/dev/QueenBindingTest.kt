package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Controlled scheduler/CPU fixtures. These are not normal App-route evidence. */
class QueenBindingTest {
    private val item=ItemDefinition("rom.special.13","捆妖繩（名称暂定）",null,"original",category="special",originalId=13,maxCount=1)
        .also{it.battleBindingUse=BattleBindingUseDefinition("game-data/provenance/world-queen117-state.json")
            .also{use->use.bindingMarker=2;use.targetLabel="女王"}}
    private val hero=CharacterState("nezha",20,12000,3000,3000,0,400,100,120,20,equipment=EquipmentState(0,-1,0,28))
    private val physical=PhysicalRules(mapOf(-1 to 64,0 to 64),List(36){255})
    private fun battle(enemyId:Int=157,marker:Int=2,party:List<CharacterState> = listOf(hero)):OpeningBattle {
        val enemy=EnemyDefinition(enemyId,"女王",7000,372,178,3000,2400,230,9).also{it.requiredBindingMarker=marker}
        val group=EncounterGroup(0,listOf(EncounterMember(3,enemyId)))
        val content=BattleContent(117,emptyList(),listOf(group),mapOf(enemyId to enemy),
            listOf(GrowthRow(21,14000,1,1,1,1,1,1,false)),0,6,50,16,
            enemyAgility=mapOf(enemyId to 110),escapeEnabled=true,physicalRules=physical).also{
            it.characterPhysicalRules=party.associate{p->p.id to physical}
            it.characterGrowth=party.associate{p->p.id to listOf(GrowthRow(21,14000,1,1,1,1,1,1,false))}
        }
        return OpeningBattle(group,content,party.first(),0,0).also{
            if(party.size>1)it.configureParty(party,party.mapIndexed{i,p->p.id to i}.toMap(),
                party.associate{p->p.id to 0},party.associate{p->p.id to 0})
        }
    }
    @Test fun unchangedOriginalSpecial13CpuAllTargetsAndExistingMarkers(){
        val rows=javaClass.getResourceAsStream("/queen13-binding-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(1536,rows.size)
        for(row in rows){val r=row.split('\t').map(String::toInt)
            assertEquals(row,r[2],originalSpecialBindingMarker(13,r[0],r[1]))
            assertEquals(1,r[3]);assertEquals(0,r[4])
        }
    }
    @Test fun realQueenProtectionAndReusableCommandShareExistingScheduler(){
        val b=battle();assertEquals(0,b.attack(3){0}!!.playerDamage)
        val inventory=mapOf(item.id to 1,HerbUse.ID to 4)
        val turn=b.useBinding(1,item,false){100}!!
        assertEquals(1,turn.actions.count{it.kind==BattleActionKind.SPECIAL&&it.actorId==hero.id})
        assertTrue(turn.actions.any{it.actorSlot==3}) // Enemy still takes its real action.
        assertEquals(inventory,b.inventoryAfterBattle(inventory));assertTrue(b.attack(3){0}!!.playerDamage>0)
        assertEquals(0,battle().attack(3){0}!!.playerDamage) // No persistent/free immunity bypass.
    }
    @Test fun collectDoesNotApplyEffectBeforeActorAndInvalidInputsConsumeNothing(){
        val second=hero.copy(id="yangjian",agility=121)
        val b=battle(party=listOf(hero.copy(agility=1),second))
        assertNull(b.useBinding(1,item,false){error("Collecting first actor must not consume RNG")})
        assertEquals(7000,b.enemies.single().hp)
        val turn=b.attack(3){0}!!;assertEquals(0,turn.playerDamage)
        assertTrue(b.attack(3){0}==null) // Next round has only collected its first command.
        val fresh=battle();val revision=fresh.inputRevision
        for(count in listOf(-1,0,2))assertNull(fresh.useBinding(count,item,false){error("Invalid quantity read RNG")})
        assertNull(fresh.useBinding(1,item,true){error("Used bit read RNG")})
        assertNull(battle(174).useBinding(1,item,false){error("Other original target not yet enabled")})
        assertNull(battle(marker=1).useBinding(1,item,false){error("Wrong protection read RNG")})
        assertEquals(revision,fresh.inputRevision);assertEquals(7000,fresh.enemies.single().hp)
    }
    @Test fun fasterLethalEnemySkipsBindingWithoutDestroyingItem(){
        val b=battle(party=listOf(hero.copy(hp=1,agility=1)))
        val turn=b.useBinding(1,item,false){100}!!
        assertFalse(turn.actions.any{it.kind==BattleActionKind.SPECIAL});assertEquals(0,b.party.first().hp)
        assertEquals(mapOf(item.id to 1),b.inventoryAfterBattle(mapOf(item.id to 1)))
    }
}
