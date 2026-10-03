package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Original CPU flag outputs; isolated fixtures do not establish normal Boss victory. */
class HallBatchFlagsTest {
    private val rows=javaClass.getResourceAsStream("/hell-hall-flags-cpu.tsv")!!.bufferedReader().use{it.readLines()}
        .filter{it.isNotBlank()&&!it.startsWith("#")}.map{it.split('\t').map(String::toInt)}
    private val gateMasks=mapOf(61 to 4,62 to 8,63 to 8,64 to 8,65 to 8,66 to 16,67 to 16,68 to 2)
    private fun flags(mid:Int,raw:Int)=((0..7).map{1 shl it}.filter{raw and it!=0}.associate{"rom.map.$mid.flag.$it" to true})+
        mapOf("unrelated" to true,"rom.map.70.flag.4" to true)
    @Test fun eachCommittedMapFlagAndGateMatchesEveryOriginalByteIndependently(){
        assertEquals(4096,rows.size)
        for(r in rows){
            val mid=r[0];val arg=if(mid==64)2 else 1;val mask=gateMasks.getValue(mid)
            val id=144+mid-61
            val boss=StoryBattleDefinition("rom.boss.$id","rom.npc.$mid.${if(mid==64)2 else 1}","rom.map.$mid.flag.$arg",
                EncounterGroup(161+mid-61,listOf(EncounterMember(3,id))),"verified-repeat-${r[5]}")
                .also{it.victoryFlags=setOf("rom.map.$mid.flag.$mask")}
            val before=flags(mid,r[1]);val after=if(r[2]==1)boss.rewardFlags(before)else before
            val expected=flags(mid,r[3]);assertEquals(r.toString(),expected,after-boss.pendingFlag)
            assertTrue(after["unrelated"]==true);assertTrue(after["rom.map.70.flag.4"]==true)
            val base=Scene("fixture",32,30,IntArray(960),IntArray(960),(0 until 960).toSet(),1,1,mid,dynamicObjectCells=setOf(33,65))
            val barrier=SceneBarrier("gate.$mid",mid,1,1,"rom.map.$mid.flag.$mask")
            assertEquals(r[4]==1,barrier.apply(base,after).dynamicObjectCells.contains(33))
            assertTrue(barrier.apply(base,after).dynamicObjectCells.contains(65));assertTrue(base.dynamicObjectCells.contains(33))
            if(r[2]==1){
                assertTrue(boss.alreadyWon(after));assertEquals(after,boss.rewardFlags(after))
                val done=boss.completeDialogue(after);assertTrue(done[boss.pendingFlag]!=true)
                assertEquals(done,boss.completeDialogue(done));assertEquals(expected,done)
            }
        }
    }
}
