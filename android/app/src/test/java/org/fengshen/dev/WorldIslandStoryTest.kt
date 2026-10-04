package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class WorldIslandStoryTest {
    private val flag="runtime.story.76.event7.intro.complete"
    private val destination=StoryDestination(76,9,11,Key.UP,0,null)
    private val intro=SceneStoryDefinition("rom.scene-story.76.four-villains-intro","rom.npc.76.0",flag,
        StoryEntryTrigger(76,12,12),StoryContinuation((3..6).map{"rom.dialogue.86.$it"},null,destination,setOf(flag)),
        StoryMovement(destination,4).also{it.accumulateEncounterSteps=true},emptyMap())
    private val boss=StoryBattleDefinition("rom.boss.152","rom.npc.76.0","rom.map.76.flag.128",
        EncounterGroup(62,listOf(EncounterMember(0,152),EncounterMember(2,153),EncounterMember(4,154),EncounterMember(6,155))),
        "rom.dialogue.86.6").also{it.entryTrigger=intro.entryTrigger;it.intro=intro;it.finalizeWithoutDialogue=true;it.victoryFlags=setOf("rom.global.7c6.16")}
    private fun before()=SaveSnapshot("fixture",76,12*16+8,12*16+8,Key.UP,
        listOf(CharacterState("nezha",26,32000,99,128,30,44,100,68,35,statusMask=2)),
        mapOf(HerbUse.ID to 4),mapOf("unrelated" to true),1019,encounterSteps=1)
    @Test fun capturedFourActualStepsApplyOnceBeforeFourDurableDialogues(){
        val source=before();var current=StoryFollowup.begin(source,intro).snapshot
        assertEquals(95,current.characters.single().hp);assertEquals(5,current.encounterSteps)
        assertEquals(9,current.x/16);assertEquals(11,current.y/16)
        assertFalse(StoryFollowup.begin(current,intro).applied)
        for(id in intro.continuation.dialogueIds){
            assertTrue(intro.validPending(current));val old=current
            val result=StoryFollowup.advance(old,intro,id);assertTrue(result.applied);current=result.snapshot
            assertFalse(StoryFollowup.advance(current,intro,id).applied)
            assertEquals(95,current.characters.single().hp);assertEquals(source.inventory,current.inventory)
            assertEquals(source.money,current.money);assertEquals(5,current.encounterSteps)
        }
        assertTrue(current.flags[flag]==true);assertFalse(current.flags[intro.pendingFlag]==true)
        assertTrue(boss.triggersAt(76,9,11,current.flags));assertFalse(boss.alreadyWon(current.flags))
        assertTrue(boss.triggersAt(76,12,12,source.flags));assertFalse(boss.triggersAt(76,5,5,source.flags))
    }
    @Test fun fortyOriginalFinalizationBoundariesPreserveOtherFlagsWithoutInventedVictoryText(){
        val rows=javaClass.getResourceAsStream("/world-island-event7-victory-cpu.tsv")!!.bufferedReader().use{it.readLines()}.drop(1)
        assertEquals(40,rows.size)
        for(line in rows){
            val r=line.split('\t').map(String::toInt)
            val initial=(1..128).filter{it and(it-1)==0}.associate{"rom.map.76.flag.$it" to(r[0]and it!=0)}+
                mapOf("rom.global.7c6.16" to(r[1]and 16!=0),"unrelated" to true)
            val final=boss.rewardFlags(initial)
            for(bit in listOf(1,2,4,8,16,32,64,128))assertEquals(line,r[2]and bit!=0,final["rom.map.76.flag.$bit"]==true)
            assertEquals(line,r[3]and 16!=0,final["rom.global.7c6.16"]==true)
            assertTrue(final["unrelated"]==true);assertFalse(final[boss.pendingFlag]==true)
            assertTrue(boss.alreadyWon(final));assertFalse(boss.triggersAt(76,12,12,final))
            assertEquals(final,boss.rewardFlags(final))
        }
    }
    @Test fun cutsceneCounterUsesCurrentValueAndCannotRewardDuringInspectionOrStaleIntro(){
        val source=before().copy(encounterSteps=253);val begun=StoryFollowup.begin(source,intro)
        assertEquals(1,begun.snapshot.encounterSteps);assertEquals(source.money,begun.snapshot.money)
        assertEquals(source.characters.map{it.experience},begun.snapshot.characters.map{it.experience})
        assertFalse(StoryFollowup.advance(source,intro,intro.continuation.dialogueIds.first()).applied)
        assertFalse(StoryFollowup.begin(source.copy(mapId=77),intro).applied)
        assertFalse(StoryFollowup.begin(source.copy(flags=source.flags+(flag to true)),intro).applied)
    }
    @Test fun actualFormationKeepsFourIndependentInstancesAndSupportedBehavior(){
        val definitions=listOf(EnemyDefinition(152,"原名未核",1400,172,115,1000,800,behaviorByte=0,hitByte=193),
            EnemyDefinition(153,"原名未核",1600,178,112,1000,800,behaviorByte=9,hitByte=243),
            EnemyDefinition(154,"原名未核",1400,182,145,1000,800,behaviorByte=0,hitByte=204),
            EnemyDefinition(155,"原名未核",1800,206,118,1000,800,behaviorByte=3,hitByte=193,iceBaseDamage=57)).associateBy{it.id}
        assertTrue(validEncounterGroup(boss.group,definitions));assertEquals(listOf(0,2,4,6),boss.group.members.map{it.slot})
        assertEquals(6200,definitions.values.sumOf{it.hp});assertEquals(4000,definitions.values.sumOf{it.experienceReward})
        assertFalse(validEncounterGroup(boss.group.copy(members=boss.group.members+EncounterMember(0,155)),definitions))
    }
    @Test fun originalMoneyChestBoundariesAndRepeatDoNotInventInventoryOrLoseOtherFlags(){
        val treasure=MoneyTreasureDefinition("rom.map.76.flag.4",100,999999,"game-data/provenance/world-island-chests.json")
        val rows=javaClass.getResourceAsStream("/world-island-money-grant-cpu.tsv")!!.bufferedReader().use{it.readLines()}.drop(1)
        assertEquals(35,rows.size)
        for(line in rows){
            val r=line.split('\t').map(String::toInt)
            val f=listOf(1,2,4,8,16,32,64,128).associate{"rom.map.76.flag.$it" to(r[1]and it!=0)}+mapOf("unrelated" to true)
            val s=before().copy(money=r[0],flags=f);val result=WorldItems.openMoneyTreasure(s,treasure)
            assertEquals(line,r[2]==1,result.applied);assertEquals(line,r[4],result.snapshot.money)
            for(bit in listOf(1,2,4,8,16,32,64,128))assertEquals(line,r[5]and bit!=0,result.snapshot.flags["rom.map.76.flag.$bit"]==true)
            assertEquals(s.characters,result.snapshot.characters);assertEquals(s.inventory,result.snapshot.inventory)
            assertTrue(result.snapshot.flags["unrelated"]==true)
            assertFalse(WorldItems.openMoneyTreasure(result.snapshot,treasure).applied)
        }
    }
    @Test fun originalNpcCallbackIsNotUnrelatedWorldEventOrInventedRewards(){
        val rows=javaClass.getResourceAsStream("/world-island-talk31-cpu.tsv")!!.bufferedReader().use{it.readLines()}.drop(1)
        assertEquals(1024,rows.size)
        for(line in rows){
            val r=line.split('\t').map(String::toInt);val mask=r[1]
            val rule=OriginalNpcTalkDefinition(78,"rom.map.78.flag.$mask","rom.global.7c6.16","",
                "rom.dialogue.88.${r[0]}","rom.dialogue.88.2").also{it.actionId=31;it.messageDialogues=mapOf(0 to it.firstDialogue,2 to it.repeatDialogue)}
            val flags=listOf(1,2,4,8,16,32,64,128).associate{"rom.map.78.flag.$it" to(r[2]and it!=0)}+
                mapOf("rom.global.7c6.16" to(r[3]and 16!=0),"unrelated" to true)
            val base=before().copy(mapId=78,flags=flags);val result=OriginalNpcTalk.begin(base,rule)
            assertTrue(result.applied);assertEquals("rom.dialogue.88.${r[4]}",result.nextDialogue)
            for(bit in listOf(1,2,4,8,16,32,64,128))assertEquals(line,r[5]and bit!=0,result.snapshot.flags["rom.map.78.flag.$bit"]==true)
            assertEquals(base.inventory,result.snapshot.inventory);assertEquals(base.characters,result.snapshot.characters)
            assertEquals(base.money,result.snapshot.money);assertTrue(result.snapshot.flags["unrelated"]==true)
            assertEquals(0,r[6])
        }
    }
    @Test fun originalProtectedTargetsCannotBeDamagedByIgnoringBattleMarker(){
        val rows=javaClass.getResourceAsStream("/world-island-damage-gate.tsv")!!.bufferedReader().use{it.readLines()}.drop(1)
        assertEquals(80,rows.size)
        for(line in rows){val r=line.split('\t').map(String::toInt)
            assertTrue(r[0]in 152..155);assertEquals(line,r[3],originalBoundTargetDamage(1,r[1],r[2]))}
        assertEquals(1000,originalBoundTargetDamage(0,0,1000))
        assertEquals(0,originalBoundTargetDamage(1,2,1000)) // Another artefact marker is not this one.
    }
    @Test fun unverifiedOrOutOfScopeMoneyInputStaysUntouched(){
        val treasure=MoneyTreasureDefinition("rom.map.76.flag.4",100,999999,"game-data/provenance/world-island-chests.json")
        for(s in listOf(before().copy(money=1000000),before().copy(mapId=77))){
            val result=WorldItems.openMoneyTreasure(s,treasure);assertFalse(result.applied);assertEquals(s,result.snapshot)
        }
        val result=WorldItems.openMoneyTreasure(before(),treasure.copy(amount=500))
        assertFalse(result.applied);assertEquals(before(),result.snapshot)
    }
}
