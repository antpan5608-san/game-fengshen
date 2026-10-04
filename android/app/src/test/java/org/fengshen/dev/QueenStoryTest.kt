package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Controlled snapshots and unchanged original CPU expectations, not App evidence. */
class QueenStoryTest {
    private val destination=StoryDestination(117,7,5,Key.UP,0,null)
    private val introFlag="runtime.story.117.event14.intro.complete"
    private val intro=SceneStoryDefinition("rom.scene-story.117.queen-intro","rom.npc.117.1",introFlag,
        StoryEntryTrigger(117,7,5),StoryContinuation(listOf("rom.dialogue.127.13"),null,destination,setOf(introFlag)),
        StoryMovement(destination,0),emptyMap())
    private val completion=setOf("rom.map.117.flag.128","rom.global.7c6.64","rom.npccontext.115.208",
        "rom.npccontext.116.210","rom.npccontext.164.220","rom.npccontext.117.231")
    private val boss=StoryBattleDefinition("rom.boss.157","rom.npc.117.1","rom.map.117.flag.128",
        EncounterGroup(0,listOf(EncounterMember(3,157))),"rom.dialogue.127.15").also{
        it.commitAfterDialogue=true;it.entryTrigger=StoryEntryTrigger(117,7,5);it.intro=intro
        it.continuation=StoryContinuation(listOf(it.victoryDialogue),null,null,completion)
    }
    private val before=SaveSnapshot("fixture",117,120,88,Key.UP,
        listOf(CharacterState("nezha",20,12000,100,100,0,80,40,60,10,statusMask=2)),
        mapOf("rom.special.13" to 1,HerbUse.ID to 3),mapOf("unrelated" to true),3000,encounterSteps=17)
    @Test fun originalNpcOnlyIntroHasNoPlayerStepPoisonCostOrReward(){
        val opening=StoryFollowup.begin(before,intro)
        assertTrue(opening.applied);assertEquals("rom.dialogue.127.13",opening.nextDialogue)
        assertEquals(before.characters,opening.snapshot.characters);assertEquals(before.inventory,opening.snapshot.inventory)
        assertEquals(before.money,opening.snapshot.money);assertEquals(before.x,opening.snapshot.x);assertEquals(before.y,opening.snapshot.y)
        assertEquals(17,opening.snapshot.encounterSteps)
        val close=StoryFollowup.advance(opening.snapshot,intro,"rom.dialogue.127.13")
        assertTrue(close.applied);assertNull(close.nextDialogue);assertTrue(close.snapshot.flags[introFlag]==true)
        assertFalse(StoryFollowup.advance(close.snapshot,intro,"rom.dialogue.127.13").applied)
        assertTrue(boss.triggersAt(117,7,5,close.snapshot.flags))
    }
    @Test fun zeroStepIntroCannotTeleportOrProduceAnExtraStepCost(){
        val wrong=intro.copy(openingMovement=StoryMovement(destination.copy(x=8),0))
        try{StoryFollowup.begin(before,wrong);fail("Zero player steps must not move the player")}
        catch(_:IllegalArgumentException){}
        assertFalse(StoryFollowup.begin(before.copy(mapId=16),intro).applied)
    }
    @Test fun originalCompletionFlagsCommitAfterRealVictoryDialogueOnlyOnce(){
        val rows=javaClass.getResourceAsStream("/queen117-completion-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(1024,rows.size)
        val bits=listOf(1,2,4,8,16,32,64,128)
        for(row in rows){val r=row.split('\t').map(String::toInt)
            val flags=bits.associate{"rom.map.117.flag.$it" to(r[0]and it!=0)}+
                bits.associate{"rom.global.7c6.$it" to(r[1]and it!=0)}
            // Cases with an existing completion flag are already finished and
            // must never be used to start a second reward transaction.
            if(r[0]and 128!=0){assertTrue(boss.alreadyWon(flags));continue}
            val rewarded=before.copy(flags=boss.rewardFlags(flags))
            assertEquals(flags["rom.global.7c6.64"],rewarded.flags["rom.global.7c6.64"])
            assertTrue(rewarded.flags[boss.pendingFlag]==true)
            val result=StoryFollowup.advance(rewarded,boss,boss.victoryDialogue,emptyMap())
            assertTrue(result.applied)
            for(bit in bits){
                assertEquals(row,r[2]and bit!=0,result.snapshot.flags["rom.map.117.flag.$bit"]==true)
                assertEquals(row,r[3]and bit!=0,result.snapshot.flags["rom.global.7c6.$bit"]==true)
            }
            assertTrue(completion.all{result.snapshot.flags[it]==true})
            assertFalse(StoryFollowup.advance(result.snapshot,boss,boss.victoryDialogue,emptyMap()).applied)
            assertEquals(before.characters,result.snapshot.characters);assertEquals(before.inventory,result.snapshot.inventory)
            assertEquals(before.money,result.snapshot.money);assertEquals(before.x,result.snapshot.x);assertEquals(before.y,result.snapshot.y)
        }
    }
}
