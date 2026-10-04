package org.fengshen.dev
import org.junit.Assert.*
import org.junit.Test

/** Isolated durable-event boundaries, not a normal acquisition/route recording. */
class OriginalYangJoinTest {
    private val rule=OriginalYangJoinDefinition(OriginalYangJoin.EVIDENCE)
    private val item=ItemDefinition(OriginalYangJoin.ITEM_ID,"玉佩",null,"original","special",19,maxCount=1,
        worldUse=WorldItemUseDefinition(130,OriginalYangJoin.USED_FLAG).also{it.yangJoin=rule})
    private val target=WorldObjectTarget(rule.npcId,110,6,6,130,OriginalYangJoin.CONTEXT_FLAG,"rom.map.110.flag.128")
    private val hero=CharacterState("nezha",25,31000,100,160,0,45,40,26,30,maxMp=20,equipment=EquipmentState(7,-1,4,29))
    private val girl=hero.copy(id="xiaolongnv",mp=30,maxMp=40,equipment=EquipmentState(19,-1,11,38))
    private val yang=CharacterState("yangjian",24,26000,495,495,54,96,60,28,69,maxMp=54,equipment=EquipmentState(33,33,18,29))
    private fun before()=SaveSnapshot("fixture",110,7*16+8,6*16+8,Key.LEFT,listOf(hero,girl),
        mapOf(item.id to 1,HerbUse.ID to 7),mapOf("old" to true),money=83,encounterSteps=17)
    @Test fun completedJoinedSaveUsesRealContextAndRetainedItemUseFlag(){
        val base=before().copy(characters=listOf(hero,girl,yang),
            flags=mapOf("rom.map.110.flag.128" to true,OriginalYangJoin.CONTEXT_FLAG to true,OriginalYangJoin.USED_FLAG to true))
        assertTrue(rule.validPending(base))
        assertFalse(rule.validPending(base.copy(flags=base.flags-OriginalYangJoin.USED_FLAG)))
        assertFalse(rule.validPending(base.copy(flags=(base.flags-OriginalYangJoin.CONTEXT_FLAG)+("rom.original.npc.context.207" to true))))
        assertTrue(rule.validPending(base.copy(flags=base.flags+("unrelated" to true))))
    }
    @Test fun firstSubmitJoinsBeforeFirstTextRetainsQuantityAndOnlyOriginalEventEffects(){
        val s=before();assertTrue(WorldItems.available(s,item,item.worldUse!!,target,true))
        val result=OriginalYangJoin.begin(s,item,target,yang,true)
        assertTrue(result.applied);assertEquals("rom.dialogue.120.2",result.nextDialogue)
        val next=result.snapshot
        assertEquals(s.characters+yang,next.characters);assertEquals(s.inventory,next.inventory);assertEquals(s.money,next.money)
        assertEquals(s.encounterSteps,next.encounterSteps);assertEquals(s.x,next.x);assertEquals(s.y,next.y);assertEquals(s.direction,next.direction)
        assertEquals(true,next.flags[OriginalYangJoin.USED_FLAG]);assertEquals(true,next.flags[OriginalYangJoin.CONTEXT_FLAG])
        assertEquals(true,next.flags["rom.global.7c8.1"]);assertEquals(true,next.flags["rom.map.110.flag.2"])
        assertTrue(next.flags["rom.map.110.flag.128"]!=true);assertTrue(rule.validPending(next))
        assertEquals(58,OriginalYangJoin.initialHandContribution(next,yang))
        assertFalse(WorldItems.use(s,item,item.worldUse!!,target,true).applied) // full state transaction is mandatory
        assertFalse(OriginalYangJoin.begin(next,item,target,yang,true).applied)
    }
    @Test fun completionUsesSharedContinuationOnceAndPreservesNonzeroEncounterCounter(){
        val s=before();val first=OriginalYangJoin.begin(s,item,target,yang,true).snapshot
        val page=StoryFollowup.advance(first,rule,"rom.dialogue.120.2")
        assertEquals("rom.dialogue.120.3",page.nextDialogue);assertTrue(rule.validPending(page.snapshot))
        assertFalse(StoryFollowup.advance(page.snapshot,rule,"rom.dialogue.120.2").applied)
        val last=StoryFollowup.advance(page.snapshot,rule,"rom.dialogue.120.3")
        assertTrue(last.applied);assertNull(last.nextDialogue);assertEquals(true,last.snapshot.flags["rom.map.110.flag.128"])
        assertTrue(last.snapshot.flags[rule.pendingFlag]!=true);assertEquals(first.characters,last.snapshot.characters)
        assertEquals(first.inventory,last.snapshot.inventory);assertEquals(17,last.snapshot.encounterSteps)
        assertFalse(StoryFollowup.advance(last.snapshot,rule,"rom.dialogue.120.3").applied)
        assertFalse(OriginalYangJoin.begin(last.snapshot,item,target,yang,true).applied)
    }
    @Test fun cancellationWrongTargetNoInventoryAndUnexpectedPartyNeverMutate(){
        val s=before()
        for(bad in listOf(s.copy(direction=Key.RIGHT),s.copy(x=s.x+1),s.copy(mapId=109),s.copy(inventory=emptyMap()),
            s.copy(inventory=s.inventory+(item.id to 2)),s.copy(characters=listOf(hero)),s.copy(characters=s.characters+yang))){
            val r=OriginalYangJoin.begin(bad,item,target,yang,true);assertFalse(r.applied);assertEquals(bad,r.snapshot)
        }
        for(bad in listOf(target.copy(spriteId=129),target.copy(id="wrong"),target.copy(x=5))){
            val r=OriginalYangJoin.begin(s,item,bad,yang,true);assertFalse(r.applied);assertEquals(s,r.snapshot)
        }
        assertEquals(s,OriginalYangJoin.begin(s,item,target,yang,false).snapshot)
        assertFalse(OriginalYangJoin.begin(s,item,target,yang.copy(strength=28,agility=96),true).applied)
        assertEquals(s, before()) // merely selecting/cancelling has no business effect
    }
    @Test fun corruptPendingCannotSkipJoinOrReawardOrBorrowAnotherActorContribution(){
        val joined=OriginalYangJoin.begin(before(),item,target,yang,true).snapshot
        assertFalse(rule.validPending(joined.copy(characters=listOf(hero,girl))))
        assertFalse(rule.validPending(joined.copy(inventory=emptyMap())))
        assertFalse(rule.validPending(joined.copy(mapId=109)))
        assertFalse(rule.validPending(joined.copy(flags=joined.flags-rule.pendingFlag,characters=listOf(hero,girl))))
        assertFalse(rule.validPending(joined.copy(flags=joined.flags-(OriginalYangJoin.CONTEXT_FLAG))))
        assertNull(OriginalYangJoin.initialHandContribution(joined,hero))
        assertNull(OriginalYangJoin.initialHandContribution(joined,yang.copy(equipment=EquipmentState(34,-1,18,29))))
    }
}
