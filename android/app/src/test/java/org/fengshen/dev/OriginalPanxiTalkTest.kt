package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class OriginalPanxiTalkTest {
    private val hero=CharacterState("nezha",32,59000,450,450,41,120,90,70,60,maxMp=41)
    private fun state()=SaveSnapshot("fixture",7,26*16+8,11*16+8,Key.UP,listOf(hero),
        mapOf(HerbUse.ID to 3),mapOf(OriginalJiangJoin.PANXI_THREE_FLAG to true),money=153,encounterSteps=14)
    private fun rule()=OriginalNpcTalkDefinition(7,"",OriginalJiangJoin.PANXI_FLAG,"","rom.dialogue.17.13","rom.dialogue.17.6").also{it.actionId=61}
    @Test fun talkWritesWitnessBeforeTextButNeverJoinsRewardsOrSetsAMapBit(){
        val s=state();val first=OriginalNpcTalk.begin(s,rule());assertTrue(first.applied)
        assertEquals("rom.dialogue.17.13",first.nextDialogue)
        assertTrue(first.snapshot.flags[OriginalJiangJoin.PANXI_FLAG]==true)
        assertEquals(s.characters,first.snapshot.characters);assertEquals(s.inventory,first.snapshot.inventory)
        assertEquals(s.money,first.snapshot.money);assertEquals(s.encounterSteps,first.snapshot.encounterSteps)
        assertTrue(OriginalJiangJoin.validPanxiPending(first.snapshot))
        val stale=OriginalJiangJoin.advancePanxi(first.snapshot,"rom.dialogue.17.6")
        assertFalse(stale.applied);assertEquals(first.snapshot,stale.snapshot)
        val second=OriginalJiangJoin.advancePanxi(first.snapshot,"rom.dialogue.17.13")
        assertTrue(second.applied);assertEquals("rom.dialogue.17.6",second.nextDialogue)
        assertTrue(OriginalJiangJoin.validPanxiPending(second.snapshot))
        val done=OriginalJiangJoin.advancePanxi(second.snapshot,"rom.dialogue.17.6")
        assertTrue(done.applied);assertNull(done.nextDialogue)
        assertEquals(s.copy(flags=s.flags+(OriginalJiangJoin.PANXI_FLAG to true)),done.snapshot)
        assertFalse(OriginalJiangJoin.advancePanxi(done.snapshot,"rom.dialogue.17.6").applied)
        assertEquals("rom.dialogue.17.13",OriginalNpcTalk.begin(done.snapshot,rule()).nextDialogue)
        assertFalse(OriginalNpcTalk.begin(s.copy(flags=mapOf(OriginalJiangJoin.PANXI_FOUR_FLAG to true)),rule()).applied)
    }
    @Test fun threeGuardSelectorsMatchAllExecutedOriginalCasesIncludingDifferentRepeatTexts(){
        val rows=javaClass.getResourceAsStream("/world-jiang-guard-selector-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(84,rows.size)
        for(row in rows){
            val v=row.split('\t').map{it.toInt()};val i=v[0];val mask=1 shl i
            val f="rom.dialogue.131.${listOf(5,10,11)[i]}";val repeat="rom.dialogue.131.${listOf(6,13,6)[i]}"
            val r=OriginalNpcTalkDefinition(121,"rom.map.121.flag.$mask",OriginalSceneItems.PLAGUE_FLAG,"",f,repeat).also{
                it.actionId=44;it.messageDialogues=mapOf(0 to f,1 to "rom.dialogue.131.${listOf(6,11,12)[i]}",2 to repeat)}
            val flags=(0..7).associate{"rom.map.121.flag.${1 shl it}" to (v[1] and (1 shl it)!=0)}+
                (OriginalSceneItems.PLAGUE_FLAG to (v[2]!=0))
            val s=state().copy(mapId=121,flags=flags);val result=OriginalNpcTalk.begin(s,r)
            assertTrue(row,result.applied);assertEquals(row,"rom.dialogue.131.${v[4]}",result.nextDialogue)
            assertEquals(row,s.copy(flags=s.flags+(r.mapFlagId to (v[5] and mask!=0))),result.snapshot)
        }
    }
}
