package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class TownBridgeTerrainTest {
    private val source=mapOf(2 to setOf(Key.LEFT),3 to setOf(Key.RIGHT),4 to setOf(Key.UP,Key.LEFT),
        5 to setOf(Key.DOWN,Key.LEFT),6 to setOf(Key.UP),7 to setOf(Key.DOWN),8 to setOf(Key.UP,Key.RIGHT),
        9 to setOf(Key.DOWN,Key.RIGHT),10 to setOf(Key.UP,Key.DOWN),11 to setOf(Key.LEFT,Key.RIGHT))
    private val target=mapOf(2 to setOf(Key.RIGHT),3 to setOf(Key.LEFT),4 to setOf(Key.DOWN,Key.RIGHT),
        5 to setOf(Key.UP,Key.RIGHT),6 to setOf(Key.DOWN),7 to setOf(Key.UP),8 to setOf(Key.DOWN,Key.LEFT),
        9 to setOf(Key.UP,Key.LEFT))
    @Test fun everyActualSourceAndTargetDispatchMatches576OriginalCpuCases() {
        var count=0
        javaClass.getResourceAsStream("/world-town-bridges-original-cpu.tsv")!!.bufferedReader().readLines().drop(1).forEach{line->
            val c=line.split('\t').map(String::toInt);val key=listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT)[c[2]-1]
            val tiles=IntArray(9);tiles[4]=c[0]
            val at=when(key){Key.UP->1;Key.DOWN->7;Key.LEFT->3;else->5};tiles[at]=c[1]
            // Source1 is only a dispatch fixture, never an enabled town wall.
            val enabled=(0..8).filter{tiles[it]!=1||it==4}.toSet()
            val s=Scene("original-town-matrix",3,3,IntArray(9),tiles,enabled,1,1,4,
                (0..11).filter{it!=1}.toSet(),transitionCells=setOf(4),sourceEdges=source,targetEdges=target)
            assertEquals("source=${c[0]} target=${c[1]} direction=$key",c[3]==1,s.probeFrom(1,1,key)!=MovementBlock.NONE);count++
        };assertEquals(576,count)
    }
}
