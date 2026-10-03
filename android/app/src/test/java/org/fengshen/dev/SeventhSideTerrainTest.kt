package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class SeventhSideTerrainTest {
    /** Independent actual CPU parameters; not normal player traversal evidence. */
    @Test fun allOriginalSideRoomTerrainPairsKeepCollisionModeAndEncounterSemantics(){
        val rows=javaClass.getResourceAsStream("/seventh-side-ground-cpu.tsv")!!.bufferedReader().use{it.readLines()}
            .map{it.split('\t').map(String::toInt)}
        assertEquals(864,rows.size);assertEquals(setOf(69,158,159),rows.map{it[0]}.toSet())
        val keys=listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT)
        for(r in rows){
            val actual=OriginalTerrain.step(3,r[2],r[3],keys[r[4]-1],r[1])
            assertEquals(r.toString(),if(r[5]==1)MovementBlock.PHYSICAL else MovementBlock.NONE,actual.block)
            assertEquals(r.toString(),r[6],actual.nextMode);assertFalse(actual.suppressEncounter)
        }
    }
}
