package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Structural transaction boundaries. Original East content is separately ROM validated. */
class StoryFollowupTest {
    private val hero=CharacterState("nezha",13,3318,50,128,0,36,15,14,4,equipment=EquipmentState(3,-1,2,29))
    private val girl=CharacterState("xiaolongnv",12,2000,92,92,44,22,14,26,43,44,EquipmentState(19,-1,11,38))
    private val story=StoryBattleDefinition("rom.boss.141","npc","rom.event.95.46.2",EncounterGroup(0,listOf(EncounterMember(3,141))),"text.3").also{
        it.continuation=StoryContinuation(listOf("text.3","text.4","text.5"),girl.id,
            StoryDestination(23,54,92,Key.DOWN,0,null),setOf("rom.map.95.flag.128"))
    }
    private fun before()=SaveSnapshot("test",95,12*16+8,5*16+8,Key.UP,listOf(hero),
        mapOf(HerbUse.ID to 1,"rom.special.11" to 1),mapOf(story.flagId to true,story.pendingFlag to true,"old.flag" to true),1627,5)
    private fun advance(s:SaveSnapshot,text:String)=StoryFollowup.advance(s,story,text,mapOf(girl.id to girl))
    @Test fun eachConfirmedPageChangesOnlyDurableStageUntilTheLast(){
        val original=before();val first=advance(original,"text.3")
        assertTrue(first.applied);assertEquals("text.4",first.nextDialogue)
        assertEquals(original,first.snapshot.copy(flags=original.flags))
        assertEquals("text.4",story.pendingDialogue(first.snapshot.flags))
        assertFalse(original.flags.containsKey(story.continuation!!.stageKey(story.id,0)))
        val second=advance(first.snapshot,"text.4")
        assertTrue(second.applied);assertEquals("text.5",second.nextDialogue)
        assertEquals(listOf(hero),second.snapshot.characters)
    }
    @Test fun finalPageJoinsAndMovesInOneProposalWithoutRewardingAgain(){
        val first=advance(before(),"text.3");val second=advance(first.snapshot,"text.4")
        val result=advance(second.snapshot,"text.5");val s=result.snapshot
        assertTrue(result.applied);assertNull(result.nextDialogue)
        assertEquals(listOf(hero,girl),s.characters);assertEquals(before().inventory,s.inventory)
        assertEquals(1627,s.money);assertEquals(3318,s.characters[0].experience)
        assertEquals(23,s.mapId);assertEquals(54*16+8,s.x);assertEquals(92*16+8,s.y)
        assertEquals(Key.DOWN,s.direction);assertEquals(5,s.encounterSteps)
        assertEquals(true,s.flags[story.flagId]);assertEquals(true,s.flags["rom.map.95.flag.128"])
        assertFalse(s.flags.containsKey(story.pendingFlag));assertEquals(true,s.flags["old.flag"])
        val repeat=advance(s,"text.5");assertFalse(repeat.applied);assertEquals(s,repeat.snapshot)
    }
    @Test fun staleDialogueAndMissingPendingCannotAdvanceOrJoin(){
        val first=advance(before(),"text.3").snapshot
        val stale=advance(first,"text.3");assertFalse(stale.applied);assertEquals(first,stale.snapshot)
        val noPending=first.copy(flags=first.flags-story.pendingFlag)
        val result=advance(noPending,"text.4");assertFalse(result.applied);assertEquals(noPending,result.snapshot)
    }
    @Test fun duplicateOrUnavailableActorRejectsFinalTransactionEntirely(){
        val last=advance(advance(before(),"text.3").snapshot,"text.4").snapshot
        for(s in listOf(last.copy(characters=listOf(hero,girl)),last.copy(characters=List(4){hero.copy(id="actor$it")}))){
            val result=advance(s,"text.5");assertFalse(result.applied);assertEquals(s,result.snapshot)
        }
        val missing=StoryFollowup.advance(last,story,"text.5",emptyMap())
        assertFalse(missing.applied);assertEquals(last,missing.snapshot)
    }
    @Test fun evidencedPreserveFieldsDoNotAcquireDefaultDirectionOrPlane(){
        val prior=before()
        val original=story.continuation!!
        story.continuation=original.copy(dialogueIds=listOf("text.3"),destination=StoryDestination(23,54,92,null,null,0))
        val result=advance(prior,"text.3").snapshot
        assertEquals(prior.direction,result.direction);assertEquals(prior.terrainMode,result.terrainMode)
        assertEquals(0,result.encounterSteps)
    }

}
