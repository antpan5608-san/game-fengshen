package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class SceneStoryTest {
    private val ids=(2..12).map{"rom.dialogue.96.$it"}
    private val story=SceneStoryDefinition("rom.scene-story.86.rebirth","rom.npc.86.0","rom.map.86.flag.128",
        StoryEntryTrigger(86,12,5),StoryContinuation(ids,null,StoryDestination(16,238,160,Key.UP,0,0),setOf("rom.map.86.flag.128")),
        StoryMovement(StoryDestination(86,7,4,Key.UP,0,6),6),
        mapOf(8 to StoryMovement(StoryDestination(86,7,5,Key.LEFT,0,7),1)))
    private fun before(poison:Boolean=false)=SaveSnapshot("fixture",86,12*16+8,5*16+8,Key.UP,
        listOf(CharacterState("nezha",12,1234,35,50,0,34,20,18,5,statusMask=if(poison)2 else 0),
            CharacterState("xiaolongnv",11,1111,29,40,0,28,18,16,4,statusMask=if(poison)2 else 0)),
        mapOf(HerbUse.ID to 2,WorldItems.FIELD_PROTECTION_ID to 1),mapOf("unrelated" to true),887)
    private fun flags(byte:Int)=listOf(1,2,4,8,16,32,64,128).associate{"rom.map.86.flag.$it" to(byte and it!=0)}
    @Test fun originalTriggerCompletionAndNoRewardMatchCapturedCpuBoundaries(){
        val rows=javaClass.getResourceAsStream("/rebirth-script-cpu.tsv")!!.bufferedReader().use{it.readLines()}
            .map{it.split('\t').map(String::toInt)}
        assertEquals(56,rows.size)
        for(r in rows){
            val base=before().copy(flags=flags(r[1]))
            when(r[0]){
                0,5->{
                    val s=base.copy(x=r[2]*16+8,y=r[3]*16+8)
                    val actual=StoryFollowup.begin(s,story)
                    assertEquals(r.toString(),r[4]==2,actual.applied)
                    assertEquals(s.characters,actual.snapshot.characters)
                    assertEquals(s.inventory,actual.snapshot.inventory);assertEquals(s.money,actual.snapshot.money)
                }
                1->{
                    if(r[1]and 128==0){val a=StoryFollowup.begin(base,story);assertTrue(a.applied);assertEquals(ids.first(),a.nextDialogue)}
                }
                3->{
                    // Exact final-stage boundary: each previous dialogue was already committed.
                    val f=base.flags+(story.pendingFlag to true)+(0..9).associate{story.continuation.stageKey(story.id,it) to true}
                    val s=base.copy(x=7*16+8,y=5*16+8,flags=f)
                    if(r[1]and 128==0){
                        val a=StoryFollowup.advance(s,story,ids.last());assertTrue(a.applied);assertNull(a.nextDialogue)
                        assertEquals(r[7],a.snapshot.mapId);assertTrue(a.snapshot.flags[story.flagId]==true)
                        assertEquals(s.characters,a.snapshot.characters);assertEquals(s.inventory,a.snapshot.inventory);assertEquals(s.money,a.snapshot.money)
                        assertEquals(238,a.snapshot.x/16);assertEquals(160,a.snapshot.y/16)
                    }
                }
            }
        }
    }
    @Test fun everyDialogueIsDurableAndStaleInputCannotRepeatMovementOrCompletion(){
        val base=before();var s=StoryFollowup.begin(base,story).snapshot
        assertEquals(7,s.x/16);assertEquals(4,s.y/16);assertEquals(6,s.encounterSteps)
        assertFalse(StoryFollowup.begin(s,story).applied)
        for((i,id)in ids.withIndex()){
            assertTrue(story.validPending(s));assertEquals(id,story.pendingDialogue(s.flags))
            val prior=s;val a=StoryFollowup.advance(prior,story,id);assertTrue(a.applied);s=a.snapshot
            assertFalse(StoryFollowup.advance(s,story,id).applied)
            assertEquals(base.characters,s.characters);assertEquals(base.inventory,s.inventory);assertEquals(base.money,s.money)
            if(i==7){assertEquals(5,s.y/16);assertEquals(Key.LEFT,s.direction);assertEquals(7,s.encounterSteps)}
        }
        assertEquals(16,s.mapId);assertEquals(0,s.encounterSteps);assertEquals(Key.UP,s.direction)
        assertTrue(s.flags[story.flagId]==true);assertTrue(s.flags[story.pendingFlag]!=true)
        assertFalse(StoryFollowup.begin(s.copy(mapId=86,x=12*16+8,y=5*16+8),story).applied)
    }
    @Test fun automaticStepsKeepActualPoisonCostsAndCannotInventInventoryOrStatGrowth(){
        val source=before(true).copy(mapId=68)
        val arrived=source.copy(mapId=86,characters=OriginalStatus.step(source.characters,68))
        var s=StoryFollowup.begin(arrived,story).snapshot
        assertEquals(listOf(28,22),s.characters.map{it.hp})
        for(id in ids)s=StoryFollowup.advance(s,story,id).snapshot
        assertEquals(listOf(27,21),s.characters.map{it.hp});assertEquals(listOf(2,2),s.characters.map{it.statusMask})
        assertEquals(source.characters.map{it.experience},s.characters.map{it.experience})
        assertEquals(source.characters.map{it.level},s.characters.map{it.level})
        assertEquals(source.inventory,s.inventory);assertEquals(source.money,s.money)
        val low=before(true).copy(characters=before(true).characters.map{it.copy(hp=1)})
        val failed=StoryFollowup.begin(low,story).snapshot
        assertTrue(OriginalStatus.allDisabled(failed.characters));assertEquals(listOf(0,0),failed.characters.map{it.hp})
    }
    @Test fun inconsistentPendingStageOrSceneCannotBeSilentlyResetOrAwarded(){
        val initial=StoryFollowup.begin(before(),story).snapshot
        for(s in listOf(initial.copy(mapId=16),initial.copy(flags=initial.flags+(story.flagId to true)),
            initial.copy(flags=initial.flags+(story.continuation.stageKey(story.id,4) to true)))){
            assertFalse(story.validPending(s));assertFalse(StoryFollowup.advance(s,story,ids.first()).applied)
        }
        assertFalse(StoryFollowup.advance(before(),story,ids.first()).applied)
    }
}
