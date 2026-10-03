package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class ForestFootTest {
    @Test fun completeOriginalForestFootDispatchMatchesExistingWorldEdges(){
        val rows=javaClass.getResourceAsStream("/forest101-direction-original-cpu.tsv")!!.bufferedReader().use{it.readLines()}.drop(1)
        assertEquals(144,rows.size)
        val keys=listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT)
        for(line in rows){
            val r=line.split('\t').map(String::toInt);val key=keys[r[2]-1]
            val c=IntArray(9);c[4]=r[0]
            val dx=if(key==Key.LEFT)-1 else if(key==Key.RIGHT)1 else 0
            val dy=if(key==Key.UP)-1 else if(key==Key.DOWN)1 else 0
            c[(1+dy)*3+1+dx]=r[1]
            val allowed=setOf(0,3,7,8,9)
            // CPU matrix includes source-wall cases; the fixture spawn stays on
            // legal ground while probeFrom independently checks each source.
            val scene=Scene("fixture",3,3,IntArray(9),c,c.indices.filter{c[it]in allowed}.toSet(),0,0,
                mapId=101,walkableClasses=allowed,sourceEdges=mapOf(3 to setOf(Key.LEFT,Key.RIGHT)),
                targetEdges=mapOf(3 to setOf(Key.LEFT,Key.RIGHT),7 to setOf(Key.LEFT,Key.RIGHT)))
            assertEquals(line,if(r[3]==0)MovementBlock.NONE else MovementBlock.PHYSICAL,scene.probeFrom(1,1,key))
            assertEquals("Original call argument97 is the requested direction",r[2],r[5])
            assertEquals("Original occlusion is separate from collision",if(r[0]==7)255 else 0,r[4])
        }
    }
}
