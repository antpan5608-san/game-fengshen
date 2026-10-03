package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Independent original CPU outputs; no normal Android route claim. */
class HallBatchTerrainTest {
    private fun rows(name:String)=javaClass.getResourceAsStream("/$name")!!.bufferedReader().use{it.readLines()}
        .filter{it.isNotBlank()&&!it.startsWith("#")}.map{it.split('\t').map(String::toInt)}
    @Test fun bothGroundPlanesMatchEveryActualScopedOriginalClassPair(){
        val data=rows("hell-hall-ground-modes-cpu.tsv");assertEquals(6840,data.size)
        val keys=listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT)
        for(r in data){
            val decision=OriginalTerrain.step(3,r[2],r[3],keys[r[4]-1],r[1])
            assertEquals(r.toString(),if(r[5]==1)MovementBlock.PHYSICAL else MovementBlock.NONE,decision.block)
            assertEquals(r.toString(),r[6],decision.nextMode)
            assertFalse(r.toString(),decision.suppressEncounter)
        }
    }
    private fun scene()=Scene("fixture",3,4,IntArray(12),intArrayOf(1,0,1,1,23,1,1,14,17,1,0,1),
        setOf(1,4,7,8,10),1,0,63,setOf(0,23,14,17),terrainProfile=3)
    private fun step(w:World,key:Key){repeat(8){w.tick(key)}}
    @Test fun upperStairPlaneCanRestoreAndAbortedMoveKeepsPriorCheckpoint(){
        val s=scene();val w=World(s);step(w,Key.DOWN);assertEquals(0,w.terrainMode)
        step(w,Key.DOWN);assertEquals(1,w.terrainMode);assertEquals(2,w.y/16)
        step(w,Key.RIGHT);assertEquals(2,w.x/16);assertEquals(1,w.terrainMode)
        step(w,Key.LEFT);step(w,Key.UP);assertEquals(1,w.terrainMode)
        step(w,Key.UP);assertEquals(0,w.terrainMode)
        assertFalse(w.tryRestore(63,24,40,0,Key.DOWN,null,0))
        assertTrue(w.tryRestore(63,24,40,0,Key.DOWN,null,1));assertEquals(1,w.terrainMode)
        val fresh=World(scene());step(fresh,Key.DOWN);fresh.tick(Key.DOWN);assertEquals(1,fresh.terrainMode)
        fresh.scene.collision[7]=1;fresh.tick(null);assertEquals(0,fresh.terrainMode);assertEquals(1,fresh.y/16)
        assertFalse(OriginalTerrain.standing(3,20,1));assertFalse(OriginalTerrain.supported(3,2))
        assertEquals(MovementBlock.DEVELOPMENT,OriginalTerrain.step(3,20,14,Key.DOWN,1).block)
    }
}
