package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class StoryCharacterChangeTest {
    private val nezha=CharacterState("nezha",20,12000,100,500,7,80,40,60,10,maxMp=20,equipment=EquipmentState(0,-1,0,28))
    private val xiao=nezha.copy(id="xiaolongnv",level=19,experience=11111,hp=0,maxHp=92,mp=0,maxMp=54,statusMask=64)
    private val yang=nezha.copy(id="yangjian",statusMask=2)
    private val rejoin=StoryCharacterChange("xiaolongnv",statusAndMask=0,restoreHp=true,restoreMp=true)
    @Test fun originalRejoinRestoresOnlySavedHpMpAndStatus(){
        val rows=javaClass.getResourceAsStream("/jiameng-xiao-return-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(80,rows.size)
        for(line in rows){val r=line.split('\t').map(String::toInt)
            val before=xiao.copy(statusMask=r[0],maxHp=r[1],maxMp=r[2])
            val result=applyStoryCharacterChanges(listOf(nezha,before,yang),listOf(rejoin))
            if(r[1]>9999){assertNull(result);continue} // CPU uint16 fixture exceeds current valid save domain.
            assertNotNull(result);val after=result!![1]
            assertEquals(r[3],after.statusMask);assertEquals(r[4],after.hp);assertEquals(r[5],after.mp)
            assertEquals(before.copy(hp=before.maxHp,mp=before.maxMp!!,statusMask=0),after)
            assertEquals(nezha,result[0]);assertEquals(yang,result[2])
        }
    }
    @Test fun originalYangDeparturePreservesLevelsItemsAndStats(){
        val rows=javaClass.getResourceAsStream("/jiameng-three-victory-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(25,rows.size)
        for(line in rows){val r=line.split('\t').map(String::toInt);val before=yang.copy(statusMask=r[0])
            val after=applyStoryCharacterChanges(listOf(nezha,xiao,before),listOf(StoryCharacterChange("yangjian",statusOrMask=64)))!!
            assertEquals(before.copy(statusMask=r[2]),after[2]);assertEquals(nezha,after[0]);assertEquals(xiao,after[1])}
    }
    @Test fun absentActorDuplicateEffectsAndUnknownMaxMpRejectWithoutMutation(){
        val before=listOf(nezha,xiao,yang);val copy=before.toList()
        assertNull(applyStoryCharacterChanges(before,listOf(StoryCharacterChange("jiangziya",restoreHp=true))))
        assertNull(applyStoryCharacterChanges(before,listOf(rejoin,rejoin)))
        assertNull(applyStoryCharacterChanges(listOf(xiao.copy(maxMp=null)),listOf(rejoin)))
        assertEquals(copy,before);assertEquals(before,applyStoryCharacterChanges(before,emptyList()))
    }
    @Test fun dialogueClosureCommitsChangesOnceAndCancellationDoesNothing(){
        val chain=StoryContinuation(listOf("actual.dialogue"),null,null,setOf("original.complete"))
            .also{it.characterChanges=listOf(rejoin)}
        val story=SceneStoryDefinition("actual.scene","actual.npc","original.complete",StoryEntryTrigger(146,2,5),chain,
            StoryMovement(StoryDestination(146,2,5,Key.UP,null,null),0),emptyMap())
        val before=SaveSnapshot("fixture",146,40,88,Key.UP,listOf(nezha,xiao,yang),mapOf("rom.special.18" to 1),emptyMap(),1234)
        val begin=StoryFollowup.begin(before,story);assertTrue(begin.applied);assertEquals(before.characters,begin.snapshot.characters)
        val canceled=StoryFollowup.advance(begin.snapshot,story,"wrong.dialogue");assertFalse(canceled.applied)
        assertEquals(begin.snapshot,canceled.snapshot)
        val finish=StoryFollowup.advance(begin.snapshot,story,"actual.dialogue");assertTrue(finish.applied)
        assertEquals(92,finish.snapshot.characters[1].hp);assertEquals(54,finish.snapshot.characters[1].mp)
        assertEquals(0,finish.snapshot.characters[1].statusMask);assertEquals(before.inventory,finish.snapshot.inventory)
        assertEquals(before.money,finish.snapshot.money);assertEquals(before.x,finish.snapshot.x);assertEquals(before.y,finish.snapshot.y)
        assertFalse(StoryFollowup.advance(finish.snapshot,story,"actual.dialogue").applied)
    }
    @Test fun zeroStepNpcScriptsCannotMoveOrTeleportThePlayer(){
        val before=SaveSnapshot("fixture",146,40,88,Key.UP,listOf(nezha,xiao,yang),emptyMap(),emptyMap(),1234)
        for(destination in listOf(StoryDestination(146,3,5,null,null,null),StoryDestination(147,2,5,null,null,null))){
            val story=SceneStoryDefinition("fixture.scene","fixture.npc","fixture.done",StoryEntryTrigger(146,2,5),
                StoryContinuation(listOf("fixture.dialogue"),null,null,setOf("fixture.done")),
                StoryMovement(destination,0),emptyMap())
            try {StoryFollowup.begin(before,story);fail("Zero-step script must not move the player")}
            catch(expected:IllegalArgumentException){assertEquals(40,before.x);assertEquals(88,before.y);assertTrue(before.flags.isEmpty())}
        }
    }
    @Test fun manualNpcTalkDoesNotAutoTriggerOrInventAnApproachCell(){
        val chain=StoryContinuation(listOf("original.xiao.return"),null,null,setOf("original.done"))
            .also{it.characterChanges=listOf(rejoin)}
        val story=SceneStoryDefinition("original.scene","original.npc","original.done",StoryEntryTrigger(146,2,5),chain,
            StoryMovement(StoryDestination(146,2,5,Key.UP,null,null),0),emptyMap()).also{it.manualActivation=true}
        for((x,y)in listOf(2 to 5,2 to 3,1 to 4,3 to 4)){
            val before=SaveSnapshot("fixture",146,x*16+8,y*16+8,Key.RIGHT,listOf(nezha,xiao,yang),emptyMap(),emptyMap(),1234)
            assertFalse(story.automaticallyTriggersAt(before));assertTrue(story.triggersAt(before))
            val a=StoryFollowup.begin(before,story);assertTrue(a.applied)
            assertEquals(before.copy(flags=mapOf(story.pendingFlag to true)),a.snapshot)
            assertFalse(story.triggersAt(before.copy(mapId=147)))
        }
    }
}
