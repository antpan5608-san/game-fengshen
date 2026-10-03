package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class ContinentBarrierTest {
    @Test fun allOriginalLoadTimeFlagStatesPreserveIndependentCollisionActors(){
        val rows=javaClass.getResourceAsStream("/continent-actor-filter-original-cpu.tsv")!!.bufferedReader().use{it.readLines()}.drop(1)
        assertEquals(1536,rows.size)
        val scene=Scene("fixture",4,3,IntArray(12),IntArray(12),(0..11).toSet(),0,0,mapId=16,
            walkableClasses=setOf(0),dynamicObjectCells=(1..6).toSet())
        for(line in rows){
            val r=line.split('\t').map(String::toInt);val cell=r[0]+1
            val rule=SceneBarrier("rom.barrier.16.${r[1]}",16,cell%4,cell/4,"rom.map.16.flag.${r[2]}")
            val flags=listOf(1,2,4,8,16,32,64,128).associate{"rom.map.16.flag.$it" to (r[3]and it!=0)}
            val result=rule.apply(scene,flags)
            assertEquals(line,r[4]==1,cell in result.dynamicObjectCells)
            assertEquals(line,scene.dynamicObjectCells-setOf(cell),result.dynamicObjectCells-setOf(cell))
            assertArrayEquals(scene.collision,result.collision);assertArrayEquals(scene.grid,result.grid)
            assertEquals(scene.enabled,result.enabled)
            assertEquals(line,scene,rule.apply(scene.copy(mapId=0),flags).copy(mapId=16))
        }
    }
}
