package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class ContinentBridgeTest {
    private val sourceEdges=mapOf(15 to setOf(Key.LEFT,Key.RIGHT),16 to setOf(Key.UP,Key.DOWN))
    private fun scene(source:Int,target:Int,key:Key):Scene {
        val collision=IntArray(9){0};collision[4]=source
        val dx=if(key==Key.LEFT)-1 else if(key==Key.RIGHT)1 else 0
        val dy=if(key==Key.UP)-1 else if(key==Key.DOWN)1 else 0
        collision[(1+dy)*3+1+dx]=target
        return Scene("fixture",3,3,IntArray(9),collision,collision.indices.filter{collision[it]in setOf(0,2,15,16)}.toSet(),1,1,
            mapId=16,walkableClasses=setOf(0,2,15,16),sourceEdges=sourceEdges)
    }
    @Test fun allCapturedFootBridgeDirectionsMatchOriginalCpuDecisions(){
        val rows=javaClass.getResourceAsStream("/continent-bridge-original-cpu.tsv")!!.bufferedReader().use{it.readLines()}.drop(1)
        assertEquals(144,rows.size)
        val keys=listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT)
        for(line in rows){
            val r=line.split('\t').map(String::toInt);val key=keys[r[2]-1]
            assertEquals(line,if(r[3]==0)MovementBlock.NONE else MovementBlock.PHYSICAL,scene(r[0],r[1],key).probeFrom(1,1,key))
            assertEquals("Foot proof cannot grant transport",0,r[5])
        }
    }
    @Test fun bridgeEntryIsNotConfusedWithSourceDepartureRestriction(){
        assertEquals(MovementBlock.NONE,scene(0,16,Key.UP).probeFrom(1,1,Key.UP))
        assertEquals(MovementBlock.PHYSICAL,scene(16,0,Key.UP).probeFrom(1,1,Key.UP))
        assertEquals(MovementBlock.NONE,scene(16,16,Key.LEFT).probeFrom(1,1,Key.LEFT))
        assertEquals(MovementBlock.PHYSICAL,scene(15,0,Key.LEFT).probeFrom(1,1,Key.LEFT))
        assertEquals(MovementBlock.PHYSICAL,scene(16,1,Key.LEFT).probeFrom(1,1,Key.LEFT))
    }
}
