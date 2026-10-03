package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class WorldActorContactTest {
    private fun world(actor:Int,key:Key,flag:Int=0,wall:Boolean=false):World {
        val delta=when(key){Key.UP->0 to -1;Key.DOWN->0 to 1;Key.LEFT->-1 to 0;else->1 to 0}
        val start=2-delta.first to 2-delta.second
        val cells=IntArray(25);if(wall)cells[12]=1
        val scene=Scene("fixture",5,5,IntArray(25),cells,cells.indices.filter{cells[it]==0}.toSet(),start.first,start.second,
            mapId=16,dynamicObjectCells=setOf(12))
        val target=Scene("fixture",3,3,IntArray(9),IntArray(9),(0..8).toSet(),1,1,mapId=107)
        val entry=MapExit(16,2,2,107,1,1).also{it.contactActorId=actor;it.preserveArrivalDirection=true}
        val mask=if(actor==231)2 else 4
        val barrier=SceneBarrier("rom.barrier.16.$actor",16,2,2,"rom.map.16.flag.$mask")
        return World(mapOf(16 to scene,107 to target),listOf(entry),16).also{w->
            w.sceneResolver={mid->if(mid==16)barrier.apply(scene,mapOf(barrier.removedFlagId to (flag and mask!=0)))else target}
        }
    }
    @Test fun supportedFootContactsMatchActualOriginalCpuFlagAndDirectionCases(){
        val rows=javaClass.getResourceAsStream("/world-tree-contact-original-cpu.tsv")!!.bufferedReader().use{it.readLines()}.drop(1)
        assertEquals(2384,rows.size)
        val keys=listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT);var checked=0
        for(line in rows){
            val r=line.split('\t').map(String::toInt)
            if(r[2]!=214||r[4]!=2)continue // Runtime currently implements foot, not boat/flying actors.
            val w=world(r[0],keys[r[3]-1],r[1]);val before=w.completedStepSeq
            w.tick(keys[r[3]-1]);assertEquals(line,r[5]==1,w.mapId==107)
            assertEquals(line,if(r[5]==1)1L else 0L,w.contactTransitionSeq)
            assertEquals(line,before,w.completedStepSeq)
            if(r[5]==1){assertNull(w.lastCompletedStep);assertEquals(0,w.remaining);assertEquals(keys[r[3]-1],w.direction)}
            checked++
        }
        assertTrue(checked>500)
    }
    @Test fun failedContactLoadLeavesOriginalPositionAndHasNoCompletedStep(){
        val w=world(232,Key.UP);val before=w.x to w.y;var failures=0
        w.prepareTarget={false};w.transitionObserver={_,_,success->assertFalse(success);failures++}
        w.tick(Key.UP)
        assertEquals(16,w.mapId);assertEquals(before,w.x to w.y);assertEquals(0,w.remaining)
        assertEquals(0L,w.completedStepSeq);assertEquals(0L,w.contactTransitionSeq);assertNull(w.lastContactExit)
        assertEquals(1,failures);assertTrue(w.message.contains("保留"))
        w.prepareTarget={true};w.tick(Key.UP);assertEquals(107,w.mapId);assertEquals(1L,w.contactTransitionSeq)
    }
    @Test fun contactDoesNotOpenPhysicalWallOrActAsOrdinaryCellExit(){
        val wall=world(232,Key.RIGHT,wall=true);wall.tick(Key.RIGHT)
        assertEquals(16,wall.mapId);assertEquals(0L,wall.contactTransitionSeq);assertEquals(0,wall.remaining)
        val removed=world(232,Key.RIGHT,flag=4);repeat(8){removed.tick(Key.RIGHT)}
        assertEquals(16,removed.mapId);assertEquals(0L,removed.contactTransitionSeq)
        assertEquals(1L,removed.completedStepSeq);assertFalse(removed.lastCompletedStep!!.transitioned)
    }
}
