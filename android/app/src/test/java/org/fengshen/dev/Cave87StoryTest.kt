package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Target script37/38 and CE6A/CE8A CPU boundaries, not normal App evidence. */
class Cave87StoryTest {
    private val ids=(11..17).map{"rom.dialogue.97.$it"}
    private fun story():StoryBattleDefinition {
        val chain=StoryContinuation(ids,null,StoryDestination(87,4,6,Key.RIGHT,0,0),
            setOf("rom.map.87.flag.128","rom.global.7bf.16"))
        chain.departureCharacterId="xiaolongnv"
        chain.movementsBeforeDialogue=mapOf(
            3 to StoryMovement(StoryDestination(87,1,7,Key.DOWN,0,null),6).also{it.accumulateEncounterSteps=true},
            5 to StoryMovement(StoryDestination(87,4,6,Key.RIGHT,0,null),4).also{it.accumulateEncounterSteps=true})
        return StoryBattleDefinition("rom.boss.156","rom.npc.87.0","rom.map.87.flag.128",
            EncounterGroup(0,listOf(EncounterMember(3,156))),ids.first()).also{
            it.entryTrigger=StoryEntryTrigger(87,1,7);it.commitAfterDialogue=true;it.continuation=chain
            it.approach=StoryMovement(StoryDestination(87,5,5,Key.UP,0,null),6).also{m->m.accumulateEncounterSteps=true}
        }
    }
    private fun base(status:Int=0)=SaveSnapshot("fixture",87,1*16+8,7*16+8,Key.UP,
        listOf(CharacterState("nezha",19,9876,95,105,20,60,40,35,12),
            CharacterState("xiaolongnv",18,8765,81,102,18,54,39,32,11,statusMask=status),
            CharacterState("yangjian",19,9800,88,110,25,65,42,37,13)),
        mapOf(HerbUse.ID to 8,"rom.weapon.22" to 1),mapOf("unrelated" to true),12345,encounterSteps=1)
    @Test fun originalDepartureStatusAndCompletionMatchEveryWonCpuCase(){
        val rows=javaClass.getResourceAsStream("/world-cave87-state-original.tsv")!!.bufferedReader().use{it.readLines()}.drop(1)
            .map{it.split('\t')}.filter{it[0]=="final"&&it[1]=="1"}
        assertEquals(1536,rows.size)
        val boss=story();val chain=boss.continuation!!
        for(r in rows){
            val oldFlag=r[2].toInt();val oldGlobal=r[3].toInt();val oldStatus=r[4].toInt()
            val flags=base().flags+(boss.pendingFlag to true)+(0..5).associate{chain.stageKey(boss.id,it) to true}+
                (listOf(1,2,4,8,16,32,64,128).associate{"rom.map.87.flag.$it" to(oldFlag and it!=0)})+
                ("rom.global.7bf.16" to(oldGlobal and 16!=0))
            val before=base(oldStatus).copy(x=4*16+8,y=6*16+8,flags=flags)
            val result=StoryFollowup.advance(before,boss,ids.last(),emptyMap())
            assertTrue(r.toString(),result.applied)
            val after=result.snapshot
            assertEquals(r[7].toInt(),after.characters[1].statusMask)
            assertEquals(r[5].toInt(),listOf(1,2,4,8,16,32,64,128).sumOf{if(after.flags["rom.map.87.flag.$it"]==true)it else 0})
            assertEquals(r[6].toInt()and 16,if(after.flags["rom.global.7bf.16"]==true)16 else 0)
            assertEquals(before.characters[0],after.characters[0]);assertEquals(before.characters[2],after.characters[2])
            assertEquals(before.characters[1].copy(statusMask=oldStatus or 64),after.characters[1])
            assertEquals(before.inventory,after.inventory);assertEquals(before.money,after.money)
            assertEquals(4,after.x/16);assertEquals(6,after.y/16);assertEquals(Key.RIGHT,after.direction)
            assertEquals(0,after.encounterSteps);assertFalse(boss.triggersAt(87,1,7,after.flags))
            assertFalse(StoryFollowup.advance(after,boss,ids.last(),emptyMap()).applied)
        }
    }
    @Test fun actualApproachAndSevenMessagesPreserveDurableStateAndNoDuplicateSteps(){
        val boss=story();val before=base(2)
        val approach=StoryFollowup.approachBattle(before,boss)
        assertTrue(approach.applied);assertEquals(5,approach.snapshot.x/16);assertEquals(5,approach.snapshot.y/16)
        assertEquals(7,approach.snapshot.encounterSteps);assertEquals(75,approach.snapshot.characters[1].hp)
        assertTrue(boss.triggersAt(87,5,5,approach.snapshot.flags))
        assertFalse(StoryFollowup.approachBattle(approach.snapshot,boss).applied)
        // Isolated victorious fixture: ordinary battle rewards have already committed once.
        var s=approach.snapshot.copy(flags=boss.rewardFlags(approach.snapshot.flags))
        for((i,id)in ids.withIndex()){
            assertEquals(id,boss.pendingDialogue(s.flags));assertTrue(s.flags[boss.flagId]!=true)
            assertEquals(2,s.characters[1].statusMask)
            val previous=s;val result=StoryFollowup.advance(previous,boss,id,emptyMap());assertTrue(result.applied);s=result.snapshot
            assertFalse(StoryFollowup.advance(s,boss,id,emptyMap()).applied)
            if(i==2){assertEquals(1,s.x/16);assertEquals(7,s.y/16);assertEquals(13,s.encounterSteps)}
            if(i==4){assertEquals(4,s.x/16);assertEquals(6,s.y/16);assertEquals(17,s.encounterSteps)}
            assertEquals(before.inventory,s.inventory);assertEquals(before.money,s.money)
        }
        assertEquals(65,s.characters[1].hp);assertEquals(66,s.characters[1].statusMask)
        assertEquals(true,s.flags[boss.flagId]);assertEquals(true,s.flags["rom.global.7bf.16"])
        assertTrue(s.flags[boss.pendingFlag]!=true);assertTrue(s.flags[boss.approachFlag]!=true)
    }
    @Test fun pendingCheckpointPositionsAndNoncontiguousFlagsAreRejectedWithoutReset(){
        val boss=story();val chain=boss.continuation!!
        var s=StoryFollowup.approachBattle(base(),boss).snapshot.let{it.copy(flags=boss.rewardFlags(it.flags))}
        for(id in ids){
            assertTrue(boss.validScopedContinuation(s))
            for(bad in listOf(s.copy(mapId=135),s.copy(x=s.x+16),s.copy(flags=s.flags+(boss.flagId to true)))){
                assertFalse(boss.validScopedContinuation(bad))
                val denied=StoryFollowup.advance(bad,boss,id,emptyMap());assertFalse(denied.applied);assertEquals(bad,denied.snapshot)
            }
            s=StoryFollowup.advance(s,boss,id,emptyMap()).snapshot
        }
        assertTrue(boss.validScopedContinuation(s))
        val pending=StoryFollowup.approachBattle(base(),boss).snapshot.let{it.copy(flags=boss.rewardFlags(it.flags))}
        assertFalse(boss.validScopedContinuation(pending.copy(flags=pending.flags+(chain.stageKey(boss.id,5) to true))))
    }
    @Test fun originalMoney550AndInventoryScopesRejectRepeatOrChangedDefinitions(){
        val treasure=MoneyTreasureDefinition("rom.map.87.flag.8",550,999999,"game-data/provenance/world-cave87-chests.json")
        val rows=javaClass.getResourceAsStream("/world-cave87-money-original.tsv")!!.bufferedReader().use{it.readLines()}.drop(1)
        assertEquals(35,rows.size)
        for(line in rows){
            val r=line.split('\t').map(String::toInt)
            val f=listOf(1,2,4,8,16,32,64,128).associate{"rom.map.87.flag.$it" to(r[1]and it!=0)}
            val before=base().copy(money=r[0],flags=f)
            val result=WorldItems.openMoneyTreasure(before,treasure)
            assertEquals(line,r[2]==1,result.applied);assertEquals(line,r[4],result.snapshot.money)
            for(bit in listOf(1,2,4,8,16,32,64,128))assertEquals(line,r[5]and bit!=0,result.snapshot.flags["rom.map.87.flag.$bit"]==true)
            assertEquals(before.inventory,result.snapshot.inventory);assertEquals(before.characters,result.snapshot.characters)
            assertFalse(WorldItems.openMoneyTreasure(result.snapshot,treasure).applied)
        }
        for(t in listOf(treasure.copy(amount=500),treasure.copy(flagId="rom.map.87.flag.4"),treasure.copy(evidence="UNKNOWN"))){
            val r=WorldItems.openMoneyTreasure(base(),t);assertFalse(r.applied);assertEquals(base(),r.snapshot)
        }
        assertTrue(WorldItems.categoryGrantEvidenceSupported(treasure.evidence,87,"rom.npc.87.6",0))
        assertFalse(WorldItems.categoryGrantEvidenceSupported(treasure.evidence,87,"rom.npc.87.6",2))
        assertFalse(WorldItems.categoryGrantEvidenceSupported(treasure.evidence,135,"rom.npc.87.6",0))
    }
    @Test fun staleTriggerOrMissingActorCannotInventDepartureOrReward(){
        val boss=story();val before=base()
        for(s in listOf(before.copy(mapId=135),before.copy(x=2*16+8),before.copy(flags=mapOf(boss.flagId to true))))
            assertFalse(StoryFollowup.approachBattle(s,boss).applied)
        val chain=boss.continuation!!
        val pending=before.copy(characters=listOf(before.characters.first()),flags=mapOf(boss.pendingFlag to true)+
            (0..5).associate{chain.stageKey(boss.id,it) to true})
        val denied=StoryFollowup.advance(pending,boss,ids.last(),emptyMap())
        assertFalse(denied.applied);assertEquals(pending,denied.snapshot)
    }
}
