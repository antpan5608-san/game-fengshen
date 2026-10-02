package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class OriginalTerrainTest {
    // Isolated geometry follows the original class-8 stair/plane semantics;
    // this fixture is not a normal-play or full-map reachability claim.
    private fun palace(id:Int=96)=Scene("fixture",3,4,IntArray(12),
        intArrayOf(1,0,1,1,8,1,3,3,3,1,8,1),
        setOf(1,4,6,7,8,10),1,0,id,setOf(0,2,3,4,5,6,7,8),terrainProfile=OriginalTerrain.PALACE)
    private fun step(w:World,key:Key){repeat(8){w.tick(key)}}
    @Test fun stairsChangePlaneOnlyForACompletedLegalDirection(){
        val w=World(palace());step(w,Key.DOWN);assertEquals(0,w.terrainMode)
        step(w,Key.LEFT);assertEquals(1,w.x/16);assertEquals(1,w.y/16);assertEquals(0,w.terrainMode)
        step(w,Key.DOWN);assertEquals(1,w.terrainMode);assertEquals(2,w.y/16)
        step(w,Key.RIGHT);assertEquals(2,w.x/16);assertEquals(1,w.terrainMode)
        step(w,Key.LEFT);step(w,Key.UP);assertEquals(1,w.y/16);assertEquals(1,w.terrainMode)
        step(w,Key.UP);assertEquals(0,w.terrainMode);assertEquals(0,w.y/16)
    }
    @Test fun waterIsNotOrdinaryFloorAndRestoreUsesTheActualPlane(){
        val s=palace();assertNotNull(s.check(1,2));assertNull(s.check(1,2,1))
        val w=World(s);assertFalse(w.tryRestore(96,24,40,0,Key.DOWN,null,0))
        assertTrue(w.tryRestore(96,24,40,0,Key.DOWN,null,1))
        assertEquals(1,w.terrainMode);step(w,Key.UP);step(w,Key.UP);assertEquals(0,w.terrainMode)
        assertFalse(w.tryRestore(96,24,40,0,Key.DOWN,null,218));assertEquals(0,w.terrainMode)
    }
    @Test fun abortedStairMoveRollsBackItsPlane(){
        val s=palace();val w=World(s);step(w,Key.DOWN);w.tick(Key.DOWN)
        assertEquals(1,w.terrainMode);assertEquals(14,w.remaining)
        s.collision[7]=1;w.tick(null)
        assertEquals(0,w.terrainMode);assertEquals(1,w.y/16);assertEquals(0,w.remaining)
    }
    @Test fun failedTransitionCannotDiscardTerrainOrPosition(){
        val s=palace();val other=s.copy(mapId=95)
        val w=World(mapOf(96 to s,95 to other),listOf(MapExit(96,2,2,95,1,2)),96)
        step(w,Key.DOWN);step(w,Key.DOWN);w.prepareTarget={false};step(w,Key.RIGHT)
        assertEquals(96,w.mapId);assertEquals(2,w.x/16);assertEquals(2,w.y/16);assertEquals(1,w.terrainMode)
        assertFalse(w.lastCompletedStep!!.transitioned)
    }
    @Test fun originalSuppressedTerrainDoesNotBecomeAnExtraEncounter(){
        assertTrue(OriginalTerrain.step(4,2,0,Key.DOWN,0).suppressEncounter)
        assertTrue(OriginalTerrain.step(4,5,3,Key.LEFT,1).suppressEncounter)
        assertFalse(OriginalTerrain.step(4,0,0,Key.DOWN,0).suppressEncounter)
        assertEquals(MovementBlock.PHYSICAL,OriginalTerrain.step(4,7,0,Key.RIGHT,0).block)
        assertEquals(MovementBlock.DEVELOPMENT,OriginalTerrain.step(4,8,27,Key.DOWN,0).block)
        assertFalse(OriginalTerrain.supported(null,1))
    }

    @Test fun witnessedWestPalaceRouteAndReturnPreserveTheActualTerrainPlane(){
        // Ordered controller-only original route from world-terrain.json. Other cells
        // remain blocked; this checks the real 225-point corridor, not a map preview.
        val points = "15,29,0;15,28,0;15,27,0;15,26,2;15,25,2;15,24,0;15,23,0;15,22,0;14,22,0;13,22,0;12,22,0;11,22,0;11,21,0;10,21,0;10,20,0;9,20,0;9,19,0;8,19,0;8,18,0;7,18,0;7,17,0;6,17,0;5,17,0;4,17,0;3,17,0;2,17,0;2,18,8;2,19,3;2,20,3;2,21,3;2,22,3;2,23,3;3,23,3;3,24,3;3,25,3;3,26,3;3,27,3;3,28,3;3,29,3;2,29,3;1,29,3;0,29,3;0,28,3;0,27,3;0,26,3;0,25,3;0,24,3;0,23,3;0,22,3;0,21,3;0,20,3;0,19,3;0,18,3;0,17,3;0,16,3;0,15,3;0,14,3;0,13,3;0,12,3;0,11,3;0,10,3;0,9,3;0,8,3;0,7,3;0,6,3;0,5,3;0,4,3;0,3,3;0,2,3;0,1,3;0,0,3;1,0,3;2,0,3;3,0,3;4,0,3;5,0,3;6,0,3;7,0,3;8,0,3;9,0,3;10,0,3;11,0,3;12,0,3;13,0,3;14,0,3;15,0,3;16,0,3;17,0,3;18,0,3;19,0,3;20,0,3;21,0,3;22,0,3;23,0,3;24,0,3;25,0,3;26,0,3;27,0,3;28,0,3;29,0,3;30,0,3;31,0,3;31,1,3;31,2,3;31,3,3;31,4,3;31,5,3;31,6,3;31,7,3;31,8,3;31,9,3;31,10,3;31,11,3;31,12,3;31,13,3;31,14,3;31,15,3;31,16,3;31,17,3;31,18,3;31,19,3;31,20,3;31,21,3;31,22,3;31,23,3;31,24,3;31,25,3;31,26,3;31,27,3;31,28,3;31,29,3;30,29,3;29,29,3;28,29,3;28,28,3;28,27,3;28,26,3;28,25,3;28,24,3;28,23,3;28,22,3;28,21,3;27,21,3;26,21,3;25,21,3;24,21,3;23,21,3;22,21,3;21,21,3;20,21,3;19,21,3;18,21,3;17,21,3;16,21,3;15,21,3;14,21,3;13,21,3;12,21,3;12,20,3;12,19,3;11,19,3;11,18,3;10,18,3;10,17,3;10,16,3;9,16,3;8,16,3;7,16,3;7,15,3;6,15,3;5,15,3;4,15,3;4,14,3;4,13,3;4,12,3;4,11,3;4,10,3;4,9,3;3,9,3;2,9,3;2,8,8;2,7,0;2,6,0;2,5,0;3,5,0;3,4,0;4,4,0;5,4,0;6,4,0;7,4,0;8,4,0;9,4,0;10,4,0;11,4,0;12,4,0;13,4,0;14,4,0;15,4,0;16,4,0;17,4,0;18,4,0;19,4,0;20,4,0;21,4,0;22,4,0;23,4,0;23,5,0;23,6,0;23,7,0;23,8,0;23,9,0;23,10,0;23,11,0;23,12,0;22,12,0;21,12,0;20,12,0;19,12,0;19,11,0;19,10,0;18,10,0;18,9,0;17,9,0;16,9,0;15,9,0".split(';').map{it.split(',').map(String::toInt)}
        val collision=IntArray(32*30){1};points.forEach{collision[it[1]*32+it[0]]=it[2]}
        val enabled=points.map{it[1]*32+it[0]}.toSet()
        val scene=Scene("witnessed-route",32,30,IntArray(32*30),collision,enabled,15,29,96,
            setOf(0,2,3,4,5,6,7,8),terrainProfile=OriginalTerrain.PALACE)
        val world=World(scene)
        fun follow(route:List<List<Int>>){
            route.zipWithNext().forEach{(from,to)->
                val key=when{to[0]>from[0]->Key.RIGHT;to[0]<from[0]->Key.LEFT;to[1]>from[1]->Key.DOWN;else->Key.UP}
                step(world,key)
                assertEquals("route x $from to $to",to[0],world.x/16)
                assertEquals("route y $from to $to",to[1],world.y/16)
                assertNull(scene.check(world.x/16,world.y/16,world.terrainMode))
            }
        }
        follow(points);assertEquals(0,world.terrainMode);assertEquals(15,world.x/16);assertEquals(9,world.y/16)
        follow(points.reversed());assertEquals(0,world.terrainMode);assertEquals(29,world.y/16)
    }
}
