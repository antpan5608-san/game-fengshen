package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Original CPU rows plus isolated scheduler fixtures, never normal App proof. */
class JiamengBindingTest {
    private val item=ItemDefinition("rom.special.18","鑽心釘",null,"original",category="special",originalId=18,maxCount=1)
        .also{it.battleBindingUse=BattleBindingUseDefinition("game-data/provenance/world-jiameng-binding.json")
            .also{use->use.bindingMarker=5;use.targetLabel="魔家四将"}}
    private val hero=CharacterState("nezha",20,12000,3000,3000,0,400,100,120,20,equipment=EquipmentState(0,-1,0,28))
    private val physical=PhysicalRules(mapOf(-1 to 64,0 to 64),List(36){255})
    // Fixture stats deliberately isolate marker/command ordering; not Boss tuning.
    private fun battle(ids:List<Int> = listOf(159,160,161),party:List<CharacterState> = listOf(hero),marker:Int=5):OpeningBattle {
        val enemies=ids.associateWith{id->EnemyDefinition(id,"fixture-$id",7000,372,178,0,0,230,0)
            .also{it.requiredBindingMarker=marker}}
        val group=EncounterGroup(0,ids.mapIndexed{i,id->EncounterMember(i*3,id)})
        val growth=listOf(GrowthRow(21,14000,1,1,1,1,1,1,false))
        val content=BattleContent(148,emptyList(),listOf(group),enemies,growth,0,6,50,16,
            enemyAgility=ids.associateWith{110},escapeEnabled=true,physicalRules=physical).also{
            it.characterPhysicalRules=party.associate{p->p.id to physical}
            it.characterGrowth=party.associate{p->p.id to growth}}
        return OpeningBattle(group,content,party.first(),0,0).also{
            if(party.size>1)it.configureParty(party,party.mapIndexed{i,p->p.id to i}.toMap(),
                party.associate{p->p.id to 0},party.associate{p->p.id to 0})}
    }
    @Test fun allOriginalSpecial18TargetsAndExistingMarkersMatch(){
        val rows=javaClass.getResourceAsStream("/jiameng18-binding-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(1536,rows.size)
        rows.forEach{row->val r=row.split('\t').map(String::toInt)
            assertEquals(row,r[2],originalSpecialBindingMarker(18,r[0],r[1]))
            assertEquals(1,r[3]);assertEquals(0,r[4])}
    }
    @Test fun originalFourProtectionRowsRejectWrongMarkersWithoutChangingDamage(){
        val rows=javaClass.getResourceAsStream("/jiameng18-damage-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(120,rows.size)
        rows.forEach{row->val r=row.split('\t').map(String::toInt)
            assertEquals(5,originalProtectionProfile(r[0])!!.marker)
            assertEquals(row,r[3],originalBoundTargetDamage(5,r[1],r[2]))}
    }
    @Test fun onlyEvidenceBoundEnabledGroupsCanSubmit(){
        assertTrue(battle(listOf(158)).bindingAvailable(1,item))
        assertTrue(battle().bindingAvailable(1,item))
        for(ids in listOf(listOf(159),listOf(158,159),listOf(157),listOf(159,160)))
            assertFalse(battle(ids).bindingAvailable(1,item))
        val b=battle();val revision=b.inputRevision
        for(count in listOf(-1,0,2))assertNull(b.useBinding(count,item,false){error("Invalid count consumes RNG")})
        assertNull(b.useBinding(1,item,true){error("Used bit consumes RNG")})
        assertNull(battle(marker=2).useBinding(1,item,false){error("Wrong protection consumes RNG")})
        val wrong=item.copy(id="rom.special.13").also{it.battleBindingUse=item.battleBindingUse}
        assertNull(b.useBinding(1,wrong,false){error("Wrong stable ID consumes RNG")})
        assertEquals(revision,b.inputRevision)
    }
    @Test fun reusableBindingUsesOneOrderedActionAndDoesNotDisableEnemies(){
        val b=battle();assertEquals(0,b.attack(0){0}!!.playerDamage)
        val inventory=mapOf(item.id to 1,HerbUse.ID to 4)
        val turn=b.useBinding(1,item,false){0}!!
        assertEquals(1,turn.actions.count{it.kind==BattleActionKind.SPECIAL})
        assertTrue(turn.actions.any{it.actorSlot!=null})
        assertEquals(inventory,b.inventoryAfterBattle(inventory))
        assertTrue(b.attack(3){0}!!.playerDamage>0)
        assertEquals(0,battle().attack(3){0}!!.playerDamage) // Battle-local marker.
    }
    @Test fun collectionCannotApplyMarkerAheadOfTheActualActorSlot(){
        val slow=hero.copy(agility=1);val fast=hero.copy(id="yangjian",agility=121)
        val b=battle(party=listOf(slow,fast))
        assertNull(b.useBinding(1,item,false){error("Collection consumes RNG")})
        assertEquals(7000,b.enemies.first().hp)
        assertEquals(0,b.attack(0){0}!!.playerDamage) // Faster actor attacks before binding.
        assertEquals(mapOf(item.id to 1),b.inventoryAfterBattle(mapOf(item.id to 1)))
    }
    @Test fun fasterLethalEnemySkipsEffectAndKeepsTheItem(){
        val b=battle(listOf(158),listOf(hero.copy(hp=1,agility=1)))
        val turn=b.useBinding(1,item,false){0}!!
        assertFalse(turn.actions.any{it.kind==BattleActionKind.SPECIAL})
        assertEquals(0,b.party.first().hp)
        assertEquals(mapOf(item.id to 1),b.inventoryAfterBattle(mapOf(item.id to 1)))
    }
}
