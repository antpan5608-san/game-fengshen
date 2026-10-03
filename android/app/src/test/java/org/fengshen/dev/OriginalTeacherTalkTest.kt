package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Original room171 selectors/gift cases. Fixtures do not establish actual party progression. */
class OriginalTeacherTalkTest {
    @Test fun discipleRawSelectorAndAction11KeepActualPartyBranches(){
        val r=OriginalNpcTalkDefinition(171,"rom.map.171.flag.1","","","rom.dialogue.181.5","rom.dialogue.181.6").also{it.actionId=11}
        val rows=javaClass.getResourceAsStream("/world-room171-disciple-original-cpu.tsv")!!.bufferedReader().use{it.readLines()}.drop(1)
        assertEquals(1024,rows.size)
        for(line in rows){
            val c=line.split('\t').map(String::toInt)
            val before=snapshot(0,mapOf(r.mapFlagId to(c[0]and 1!=0),"unrelated" to true),c[1])
            val result=OriginalNpcTalk.begin(before,r)
            assertTrue(line,result.applied);assertEquals("rom.dialogue.181.${c[2]}",result.nextDialogue)
            assertEquals(c[3]and 1!=0,result.snapshot.flags[r.mapFlagId]==true)
            assertEquals(before.copy(flags=result.snapshot.flags),result.snapshot)
        }
    }
    @Test fun room171FootClassesMatchAllOriginalDirectionsWithoutOpeningWalls(){
        val rows=javaClass.getResourceAsStream("/world-room171-terrain-original-cpu.tsv")!!.bufferedReader().use{it.readLines()}.drop(1)
        assertEquals(36,rows.size)
        val keys=listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT)
        for(line in rows){
            val c=line.split('\t').map(String::toInt);val key=keys[c[2]-1]
            val collision=IntArray(16);collision[5]=c[0]
            val target=5+if(key==Key.UP)-4 else if(key==Key.DOWN)4 else if(key==Key.LEFT)-1 else 1
            collision[target]=c[1]
            val scene=Scene("fixture",4,4,IntArray(16),collision,collision.indices.filter{collision[it]!=1}.toSet(),0,0,
                mapId=171,walkableClasses=setOf(0,2))
            assertEquals(line,if(c[3]==0)MovementBlock.NONE else MovementBlock.PHYSICAL,scene.probeFrom(1,1,key))
            assertEquals(0,c[4])
        }
    }
    private val rule=OriginalNpcTalkDefinition(171,"rom.map.171.flag.2","rom.global.7c8.1","rom.special.19",
        "rom.dialogue.181.0","rom.dialogue.181.3").also{
        it.actionId=12;it.messageDialogues=listOf(0,1,2,3,7).associateWith{i->"rom.dialogue.181.$i"}
        it.completionWitnessFlagId="rom.global.7c7.128"
    }
    private val item=ItemDefinition(rule.itemId,"玉佩",null,"ORIGINAL_CPU","special",19,maxCount=1)
    private fun snapshot(count:Int,flags:Map<String,Boolean>,party:Int=2,inventory:Map<String,Int>?=null)=
        SaveSnapshot("fixture",171,120,88,Key.UP,(0 until party).map{
            CharacterState("fixture-$it",10,2000,80,100,10,40,30,20,10)},
            inventory?:if(count==0)emptyMap()else mapOf(item.id to count),flags,money=83,encounterSteps=7)
    @Test fun actualRawSelectorAndAction12MatchEveryOriginalBranch(){
        val rows=javaClass.getResourceAsStream("/world-teacher171-selector-original-cpu.tsv")!!.bufferedReader().use{it.readLines()}.drop(1)
        assertEquals(8192,rows.size)
        for(line in rows){
            val c=line.split('\t').map(String::toInt)
            val flags=mapOf(rule.mapFlagId to(c[0]and 2!=0),rule.witnessFlagId to(c[1]==1),rule.completionWitnessFlagId to(c[4]and 128!=0),"unrelated" to true)
            val before=snapshot(c[2],flags,c[3]);val result=OriginalNpcTalk.begin(before,rule,item)
            assertTrue(line,result.applied);assertEquals(line,rule.messageDialogues.getValue(c[5]),result.nextDialogue)
            assertEquals(line,c[6]and 2!=0,result.snapshot.flags[rule.mapFlagId]==true)
            assertEquals(before.characters,result.snapshot.characters);assertEquals(before.money,result.snapshot.money)
            assertEquals(before.encounterSteps,result.snapshot.encounterSteps);assertEquals(true,result.snapshot.flags["unrelated"])
            val expected=if(c[5]==1&&c[2]==0)before.inventory+(item.id to 1)else before.inventory
            assertEquals(line,expected,result.snapshot.inventory)
            assertFalse(result.snapshot.flags.containsKey("rom.npc.171.1"))
        }
    }
    @Test fun realGiftFailureAndSuccessUseSharedCapacityAndNoExtraClaimFlag(){
        val rows=javaClass.getResourceAsStream("/world-teacher171-gift-original-cpu.tsv")!!.bufferedReader().use{it.readLines()}.drop(1)
        assertEquals(6,rows.size)
        // Original gift-stage fixture intentionally invokes B510 with message1;
        // normal owned-item selector skips that stage, tested separately above.
        for(line in rows){
            val c=line.split('\t');val inventory=if(c[1]=="-")emptyMap()else c[1].split(',').associate{
                val p=it.split(':');"rom.special.${p[0]}" to(p[1].toInt()and 127)}
            val before=snapshot(inventory[item.id]?:0,mapOf(rule.witnessFlagId to true),inventory=inventory)
            val result=OriginalNpcTalk.begin(before,rule,item)
            assertTrue(result.applied);assertEquals(c[2].toInt(),result.snapshot.inventory[item.id]?:0)
            assertTrue(result.snapshot.flags[rule.mapFlagId]!=true)
            assertEquals(before.characters,result.snapshot.characters)
        }
    }
    @Test fun repeatedTalkUsesLatestOwnedStateAndNoUnconditionalGiftOrJoin(){
        val before=snapshot(0,emptyMap());val first=OriginalNpcTalk.begin(before,rule,item)
        assertEquals(before,first.snapshot);assertEquals(rule.messageDialogues[0],first.nextDialogue)
        val witnessed=before.copy(flags=mapOf(rule.witnessFlagId to true))
        val gift=OriginalNpcTalk.begin(witnessed,rule,item);assertEquals(1,gift.snapshot.inventory[item.id])
        val repeat=OriginalNpcTalk.begin(gift.snapshot,rule,item);assertEquals(gift.snapshot,repeat.snapshot)
        assertEquals(rule.messageDialogues[2],repeat.nextDialogue)
        val full=(0..16).filter{it!=19}.take(16).associate{"rom.special.$it" to 1}
        val blocked=OriginalNpcTalk.begin(witnessed.copy(inventory=full),rule,item)
        assertEquals(full,blocked.snapshot.inventory);assertEquals(rule.messageDialogues[1],blocked.nextDialogue)
        val retry=OriginalNpcTalk.begin(blocked.snapshot.copy(inventory=full-full.keys.first()),rule,item)
        assertEquals(1,retry.snapshot.inventory[item.id]);assertEquals(before.characters,retry.snapshot.characters)
    }
}
