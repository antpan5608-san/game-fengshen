package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Original CPU parity, not normal Android play. */
class JiamengRoomTalkTest {
    private fun rule(mask:Int)=OriginalNpcTalkDefinition(37,"rom.map.37.flag.$mask","","",
        "rom.dialogue.47.${if(mask==1)0 else 2}","rom.dialogue.47.${if(mask==1)1 else 3}").also{it.actionId=58}
    private fun snapshot(status:Int,flags:Map<String,Boolean>)=SaveSnapshot("fixture",37,4,5,Key.UP,
        listOf(CharacterState("yangjian",23,10000,92,100,5,30,12,6,9,statusMask=status)),
        mapOf("rom.medicine.0" to 2),flags,123)
    @Test fun bothOriginalActorsAllMapFlagsAndIllnessBranchesMatch() {
        val rows=javaClass.getResourceAsStream("/jiameng-room37-talk58-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(2560,rows.size)
        for(line in rows){
            val r=line.split('\t').map(String::toInt);val flags=(0..7).associate{"rom.map.37.flag.${1 shl it}" to (r[2]and(1 shl it)!=0)}+("unrelated" to true)
            val before=snapshot(r[3],flags);val result=OriginalNpcTalk.begin(before,rule(r[1]))
            assertTrue(line,result.applied);assertEquals("rom.dialogue.47.${r[4]}",result.nextDialogue)
            val expected=flags+("rom.map.37.flag.${r[1]}" to (r[5]and r[1]!=0))
            assertEquals(line,expected,result.snapshot.flags)
            assertEquals(before.copy(flags=expected),result.snapshot)
        }
    }
    @Test fun repeatPreservesStateAbsentYangDoesNotCreateActorAndWrongRulesReject() {
        val sick=snapshot(64,emptyMap());val r=rule(1)
        val first=OriginalNpcTalk.begin(sick,r);assertEquals(sick,first.snapshot)
        assertEquals(first,OriginalNpcTalk.begin(first.snapshot,r))
        val absent=sick.copy(characters=emptyList());val healthy=OriginalNpcTalk.begin(absent,r)
        assertEquals(emptyList<CharacterState>(),healthy.snapshot.characters);assertTrue(healthy.snapshot.flags[r.mapFlagId]==true)
        assertEquals(healthy.snapshot,OriginalNpcTalk.begin(healthy.snapshot,r).snapshot)
        for(bad in listOf(r.copy(mapId=36),r.copy(itemId="rom.special.18"),r.copy(firstDialogue="rom.dialogue.47.2"))){
            bad.actionId=58;val result=OriginalNpcTalk.begin(sick,bad);assertFalse(result.applied);assertEquals(sick,result.snapshot)
        }
        assertFalse(OriginalNpcTalk.begin(sick.copy(mapId=16),r).applied)
    }
}
