package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class OriginalNpcTalkTest {
    private val rule=OriginalNpcTalkDefinition(110,"rom.map.110.flag.2","rom.global.7c8.1",
        "rom.special.19","rom.dialogue.120.0","rom.dialogue.120.1")
    private fun snapshot(count:Int,flags:Map<String,Boolean>)=SaveSnapshot("opening-segment-001-c36",110,120,104,Key.LEFT,
        emptyList(),if(count==0)emptyMap()else mapOf(rule.itemId to count),flags)
    @Test fun allOriginalAction17CpuCasesKeepPartyInventoryAndOnlyRealFlags() {
        val input=javaClass.getResourceAsStream("/world-yang-talk-original-cpu.tsv")!!
        var checked=0
        input.bufferedReader().readLines().drop(1).forEach{line->
            val c=line.split('\t').map(String::toInt)
            val originalFlags=mapOf(rule.mapFlagId to(c[0]and 2!=0),rule.witnessFlagId to(c[2]==1),"unrelated" to true)
            val before=snapshot(c[1],originalFlags);val result=OriginalNpcTalk.begin(before,rule)
            assertTrue(result.applied);assertEquals(if(c[3]==0)rule.firstDialogue else rule.repeatDialogue,result.nextDialogue)
            assertEquals(c[4]and 2!=0,result.snapshot.flags[rule.mapFlagId]==true)
            assertEquals(c[5]==1,result.snapshot.flags[rule.witnessFlagId]==true)
            assertEquals(before.inventory,result.snapshot.inventory);assertEquals(before.characters,result.snapshot.characters)
            assertEquals(before.money,result.snapshot.money);assertEquals(true,result.snapshot.flags["unrelated"])
            assertFalse(result.snapshot.flags.containsKey("rom.npc.110.0"));checked++
        };assertEquals(1024,checked)
    }
    @Test fun noInventoryGrantNoJoinAndIndependentRepeatedTalk() {
        val before=snapshot(0,emptyMap());val first=OriginalNpcTalk.begin(before,rule)
        assertEquals(rule.firstDialogue,first.nextDialogue);assertTrue(first.snapshot.flags[rule.witnessFlagId]==true)
        assertFalse(first.snapshot.flags[rule.mapFlagId]==true)
        val again=OriginalNpcTalk.begin(first.snapshot,rule)
        assertEquals(first.snapshot,again.snapshot);assertEquals(first.nextDialogue,again.nextDialogue)
        assertEquals(before.inventory,again.snapshot.inventory);assertEquals(before.characters,again.snapshot.characters)
    }
    @Test fun staleSceneAndInvalidQuantityRejectWithoutMutation() {
        for(before in listOf(snapshot(0,emptyMap()).copy(mapId=107),snapshot(-1,emptyMap()),snapshot(2,emptyMap()))) {
            val result=OriginalNpcTalk.begin(before,rule)
            assertFalse(result.applied);assertEquals(before,result.snapshot);assertNull(result.nextDialogue)
        }
    }
}
