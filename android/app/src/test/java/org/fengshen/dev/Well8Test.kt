package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class Well8Test {
    private val rule=OriginalSceneItemDefinition(OriginalSceneItems.WELL_EVIDENCE,1)
    private fun item()=ItemDefinition("rom.special.1","丹藥",null,"fixture","special",1,maxCount=1,
        worldUse=WorldItemUseDefinition(0,"rom.inventory.special.1.used").also{it.sceneScript=rule})
    private fun state()=SaveSnapshot("fixture",8,13*16+8,26*16+8,Key.UP,
        listOf(CharacterState("nezha",29,123456,298,430,23,120,91,87,80)),
        mapOf("rom.special.1" to 1,"rom.special.11" to 0),mapOf("unrelated" to true,
            "rom.inventory.special.11.used" to true,"rom.npccontext.38.123" to true),money=9021,encounterSteps=55)
    @Test fun nativeCoordinatePredicateDoesNotNeedFacingNpcOrQuestFlags(){
        val rows=javaClass.getResourceAsStream("/world-well8-dispatch-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(472,rows.size)
        for(line in rows){
            val v=line.split('\t').map(String::toInt)
            val before=state().copy(mapId=v[0],x=v[1]*16+8,y=v[2]*16+8,
                direction=listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT)[v[3]-1])
            val result=OriginalSceneItems.begin(before,item(),rule.target,true)
            // Native coordinates accept other maps too; this scoped adapter only
            // exposes the evidenced village8 event, explicitly reporting scope.
            assertEquals(line,v[0]==8&&v[5]==1,result.applied)
            if(!result.applied)assertEquals(before,result.snapshot)
            else{
                assertEquals(before.characters,result.snapshot.characters)
                assertEquals(before.money,result.snapshot.money);assertEquals(before.encounterSteps,result.snapshot.encounterSteps)
                assertTrue(result.snapshot.flags[OriginalSceneItems.PLAGUE_FLAG]==true)
            }
        }
        assertNull(OriginalSceneItems.unavailable(state(),item(),rule.target,true))
    }
    @Test fun fiveOriginalMessagesCommitOnceAndKeepAllPartyEconomyAndPosition(){
        val before=state();val begin=OriginalSceneItems.begin(before,item(),rule.target,true)
        assertTrue(begin.applied);assertEquals("rom.dialogue.18.14",begin.nextDialogue)
        assertEquals(0,begin.snapshot.inventory[item().id]);assertTrue(rule.validPending(begin.snapshot))
        assertTrue(begin.snapshot.flags[OriginalSceneItems.WELL_CONTEXT]==true)
        assertFalse(begin.snapshot.flags.containsKey("rom.npccontext.38.123"))
        assertFalse(begin.snapshot.flags["rom.map.8.flag.128"]==true)
        assertFalse(OriginalSceneItems.begin(begin.snapshot,item(),rule.target,true).applied)
        var s=begin.snapshot
        for((index,message)in rule.continuation.dialogueIds.withIndex()){
            val wrong=OriginalSceneItems.advance(s,rule,"rom.dialogue.99.0");assertFalse(wrong.applied);assertEquals(s,wrong.snapshot)
            val done=OriginalSceneItems.advance(s,rule,message);assertTrue(done.applied);s=done.snapshot
            assertEquals(before.characters,s.characters);assertEquals(before.money,s.money)
            assertEquals(before.encounterSteps,s.encounterSteps);assertEquals(before.mapId,s.mapId)
            assertEquals(before.x,s.x);assertEquals(before.y,s.y)
            if(index<4){assertEquals(rule.continuation.dialogueIds[index+1],done.nextDialogue);assertTrue(rule.validPending(s))
                assertFalse(OriginalSceneItems.advance(s,rule,message).applied)}
            else assertNull(done.nextDialogue)
        }
        assertTrue(s.flags["rom.map.8.flag.128"]==true);assertFalse(s.flags[rule.pendingFlag]==true)
        assertTrue(s.flags[OriginalSceneItems.PLAGUE_FLAG]==true);assertEquals(0,s.inventory[item().id])
        assertFalse(OriginalSceneItems.advance(s,rule,"rom.dialogue.18.18").applied)
        assertFalse(OriginalSceneItems.begin(s,item(),rule.target,true).applied)
        // No synthetic once-only lock beyond real current inventory.
        assertTrue(OriginalSceneItems.begin(s.copy(inventory=s.inventory+(item().id to 1)),item(),rule.target,true).applied)
    }
    @Test fun cancelInvalidInventoryLocationOrPendingPreservesCompleteState(){
        val initial=state()
        for(s in listOf(initial.copy(x=initial.x+1),initial.copy(y=initial.y+16),initial.copy(mapId=89),
            initial.copy(inventory=initial.inventory-item().id),initial.copy(inventory=initial.inventory+(item().id to 2)))){
            val result=OriginalSceneItems.begin(s,item(),rule.target,true);assertFalse(result.applied);assertEquals(s,result.snapshot)
        }
        assertFalse(OriginalSceneItems.begin(initial,item(),rule.target,false).applied)
        assertFalse(OriginalSceneItems.begin(initial,item(),rule.target.copy(spriteId=130),true).applied)
        assertFalse(OriginalSceneItemDefinition(OriginalSceneItems.EVIDENCE,1).verified())
        val s=OriginalSceneItems.begin(initial,item(),rule.target,true).snapshot
        for(bad in listOf(s.copy(flags=s.flags-OriginalSceneItems.PLAGUE_FLAG),s.copy(flags=s.flags-OriginalSceneItems.WELL_CONTEXT),
            s.copy(inventory=s.inventory+(item().id to 1)),s.copy(flags=s.flags+(rule.continuation.stageKey(rule.id,3)to true))))
            assertFalse(rule.validPending(bad))
    }
}
