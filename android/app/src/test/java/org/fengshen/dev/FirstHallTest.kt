package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class FirstHallTest {
    @Test fun allOriginalCpuPairsKeepGroundModeAndDirectionalBarrierRules(){
        val rows=javaClass.getResourceAsStream("/first-hall-terrain-cpu.tsv")!!.bufferedReader().use{it.readLines()}.filter{!it.startsWith("#")&&it.isNotBlank()}
        for(row in rows){
            val original=row.split('\t').map{it.toInt()};val key=listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT)[original[2]-1]
            val result=OriginalTerrain.step(OriginalTerrain.CAVE_GROUND,original[0],original[1],key,0)
            assertEquals(row,if(original[3]==1)MovementBlock.PHYSICAL else MovementBlock.NONE,result.block)
            assertEquals(original[4],result.nextMode);assertEquals(original[6]!=0,result.suppressEncounter)
            assertEquals(0,original[5])
        }
        assertEquals(576,rows.size)
    }
    private val barrier=SceneBarrier("rom.barrier.70.0",70,23,2,"rom.map.70.flag.4")
    private fun scene():Scene = Scene("test",32,15,IntArray(480),IntArray(480),(0 until 480).toSet(),1,13,70,
        dynamicObjectCells=setOf(2*32+23,4*32+28,5*32+2))
    @Test fun onlyOriginalGateActorDisappearsAndArraysExitsAndOtherActorsStay(){
        val base=scene()
        assertSame(base,barrier.apply(base,emptyMap()))
        assertSame(base,barrier.apply(base,mapOf("rom.map.70.flag.2" to true)))
        val after=barrier.apply(base,mapOf("rom.map.70.flag.4" to true))
        assertEquals(setOf(4*32+28,5*32+2),after.dynamicObjectCells)
        assertSame(base.grid,after.grid);assertSame(base.collision,after.collision)
        assertEquals(MovementBlock.PHYSICAL,base.blockType(23,2));assertNull(after.check(23,2))
        assertEquals(setOf(2*32+23,4*32+28,5*32+2),base.dynamicObjectCells)
    }
    @Test fun battleCommitKeepsVictoryDoorFlagAndDurableDialogueThroughReload(){
        val boss=StoryBattleDefinition("rom.boss.142","rom.npc.70.1","rom.map.70.flag.2",
            EncounterGroup(159,listOf(EncounterMember(3,142))),"rom.dialogue.80.2").also{it.victoryFlags=setOf("rom.map.70.flag.4")}
        val before=mapOf("other" to true);assertFalse(boss.alreadyWon(before))
        val won=boss.rewardFlags(before)
        assertTrue(won.getValue(boss.flagId));assertTrue(won.getValue(barrier.removedFlagId));assertTrue(won.getValue(boss.pendingFlag))
        assertTrue(boss.alreadyWon(won));assertEquals(won,boss.rewardFlags(won))
        assertEquals("rom.dialogue.80.2",boss.pendingDialogue(won))
        val done=boss.completeDialogue(won)
        assertTrue(done.getValue(barrier.removedFlagId));assertTrue(done.getValue("other"));assertFalse(done.containsKey(boss.pendingFlag))
        assertEquals(done,boss.completeDialogue(done));assertFalse(boss.triggersAt(70,28,6,done))
    }
}
