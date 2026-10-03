package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class PreservedArrivalDirectionTest {
    private fun scene(id:Int)=Scene("fixture",4,4,IntArray(16),IntArray(16),(0..15).toSet(),1,1,id,setOf(0))
    @Test fun preservedOriginalLoaderDirectionSurvivesAllFourLegalExitsAndRestore(){
        for((key,cell)in listOf(Key.UP to (1 to 0),Key.DOWN to (1 to 2),Key.LEFT to (0 to 1),Key.RIGHT to (2 to 1))){
            val maps=mapOf(25 to scene(25),95 to scene(95))
            val exit=MapExit(25,cell.first,cell.second,95,1,1).also{it.preserveArrivalDirection=true}
            val w=World(maps,listOf(exit),25);repeat(8){w.tick(key)}
            assertEquals(95,w.mapId);assertEquals(key,w.direction)
            val cold=World(maps,listOf(exit),25)
            assertTrue(cold.tryRestore(95,w.x,w.y,0,w.direction,null,0));assertEquals(key,cold.direction)
        }
    }
    @Test fun existingExplicitArrivalDirectionAndFailedTransitionStillKeepTheirContracts(){
        val maps=mapOf(25 to scene(25),95 to scene(95))
        val exit=MapExit(25,2,1,95,1,1,arrivalDirection=Key.DOWN)
        val w=World(maps,listOf(exit),25);repeat(8){w.tick(Key.RIGHT)}
        assertEquals(95,w.mapId);assertEquals(Key.DOWN,w.direction)
        val preserved=MapExit(25,2,1,95,1,1).also{it.preserveArrivalDirection=true}
        val blocked=World(maps,listOf(preserved),25);blocked.prepareTarget={false};repeat(8){blocked.tick(Key.RIGHT)}
        assertEquals(25,blocked.mapId);assertEquals(Key.RIGHT,blocked.direction)
    }
}
