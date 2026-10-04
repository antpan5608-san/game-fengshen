package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Original CPU expectations, separate from normal Android route evidence. */
class AbsentPartyTest {
    private fun actor(index:Int,status:Int=0)=CharacterState("actor$index",12,2000,101+index,150,
        5,20,10,20,10,maxMp=10,equipment=EquipmentState(0,-1,0,28),statusMask=status)

    @Test fun matchesAllOriginalUnavailableProjectionAndExitRows(){
        val rows=javaClass.getResourceAsStream("/world-party-unavailable-original.tsv")!!
            .bufferedReader().readLines().drop(1)
        assertEquals(1536,rows.size)
        for(row in rows){
            val columns=row.split('\t');val count=columns[0].toInt();val tested=columns[1].toInt()
            val status=columns[2].toInt();val roster=(0 until count).map{actor(it,if(it==tested)status else 0)}
            val active=OriginalPartyRules.battleCharacters(roster)
            assertEquals(row,columns[3].toInt(),active.size)
            assertEquals(row,columns[4],active.joinToString(","){(roster.indexOf(it)+1).toString()}.ifEmpty{"-"})
            val restored=OriginalPartyRules.restoreBattleCharacters(roster,active)
            assertEquals(row,columns[7].toInt(),restored[tested].hp)
            assertEquals(row,columns[8].toInt(),restored[tested].statusMask)
            assertEquals(row,roster.map{it.equipment},restored.map{it.equipment})
            assertEquals(row,roster.map{it.experience},restored.map{it.experience})
        }
    }

    @Test fun inactiveActorIsNeverTargetedOrAwardedButRetainsOwnSaveRecord(){
        val hero=actor(0).copy(id="nezha",hp=50)
        val gone=actor(1,72).copy(id="xiaolongnv",experience=5000)
        val enemy=EnemyDefinition(18,"fixture",1,25,0,39,13,255,0)
        val group=EncounterGroup(1,listOf(EncounterMember(3,18)))
        val physical=PhysicalRules(mapOf(-1 to 64,0 to 64),List(36){255})
        val rules=BattleContent(23,emptyList(),listOf(group),mapOf(18 to enemy),
            listOf(GrowthRow(13,2010,8,0,2,1,1,0,false)),0,6,50,16,
            enemyAgility=mapOf(18 to 1),escapeEnabled=true,physicalRules=physical).also{
                it.characterPhysicalRules=mapOf(gone.id to physical)
                it.characterGrowth=mapOf(gone.id to listOf(GrowthRow(13,2525,6,4,1,1,2,2,false)))
            }
        val battle=OpeningBattle(group,rules,hero,0,0).also{it.configureParty(listOf(hero,gone),
            mapOf(hero.id to 0,gone.id to 1),mapOf(hero.id to 0,gone.id to 0),mapOf(hero.id to 0,gone.id to 0))}
        assertEquals(listOf(hero),battle.party)
        assertFalse(battle.herbAvailable(gone.id,1,ItemDefinition(HerbUse.ID,"藥草",null,"verified herb",
            category="medicine",originalId=0,herbUse=HerbUseDefinition(50,true,"world-two-party"))))
        var rolls=0
        val turn=battle.attack(3){rolls++;0}!!
        assertEquals(1,rolls);assertEquals(BattlePhase.VICTORY,turn.phase)
        assertEquals(setOf(hero.id),turn.actions.last().partyHp.keys)
        val settled=battle.settle(100)!!
        assertEquals(113,settled.money)
        assertEquals(mapOf(hero.id to 39),settled.experienceByCharacter)
        assertEquals(gone.copy(statusMask=64),settled.characters.single{it.id==gone.id})
        assertEquals(2039,settled.characters.first().experience)
        assertNull(battle.settle(settled.money))
        assertEquals(listOf(hero.copy(hp=58,maxHp=158,level=13,experience=2039,strength=22,stamina=11,agility=21)),battle.party)
    }
}
