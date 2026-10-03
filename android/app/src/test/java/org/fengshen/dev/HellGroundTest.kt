package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Actual map23 ground classes, compared with the scoped 484 original CPU cases. */
class HellGroundTest {
    private val classes=listOf(0,1,4,8,10,11,13,14,19,20,21)
    private val keys=listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT)
    @Test fun actualGroundClassMatrixMatchesOriginalDirections(){
        val denied=mapOf(4 to setOf(Key.UP,Key.LEFT),8 to setOf(Key.UP,Key.RIGHT),
            10 to setOf(Key.UP,Key.DOWN),11 to setOf(Key.LEFT,Key.RIGHT))
        for(source in classes)for(target in classes)for(key in keys){
            val result=OriginalTerrain.step(OriginalTerrain.CAVE_GROUND,source,target,key,0)
            val blocked=target in setOf(1,14)||key in (denied[source]?:emptySet())
            assertEquals("$source/$target/$key",if(blocked)MovementBlock.PHYSICAL else MovementBlock.NONE,result.block)
            assertEquals(0,result.nextMode);assertEquals(source==19,result.suppressEncounter)
        }
    }
    @Test fun horizontalBridgePermitsLongitudinalMovementAndRejectsSideDeparture(){
        val grid=IntArray(9);val collision=intArrayOf(0,0,0,0,10,0,0,0,0)
        val scene=Scene("fixture",3,3,grid,collision,(0..8).toSet(),1,1,23,classes.toSet()-setOf(1,14),terrainProfile=3)
        assertEquals(MovementBlock.NONE,scene.probeFrom(1,1,Key.LEFT))
        assertEquals(MovementBlock.NONE,scene.probeFrom(1,1,Key.RIGHT))
        assertEquals(MovementBlock.PHYSICAL,scene.probeFrom(1,1,Key.UP))
        assertEquals(MovementBlock.PHYSICAL,scene.probeFrom(1,1,Key.DOWN))
        assertEquals(MovementBlock.NONE,scene.probeFrom(0,1,Key.RIGHT))
    }
    @Test fun unverifiedUpperPlaneOrOtherClassesAreNotMadeWalkable(){
        assertFalse(OriginalTerrain.supported(3,1))
        assertFalse(OriginalTerrain.standing(3,14,0))
        assertFalse(OriginalTerrain.standing(3,1,0))
        assertFalse(OriginalTerrain.standing(3,23,0))
        assertEquals(MovementBlock.DEVELOPMENT,OriginalTerrain.step(3,0,23,Key.UP,0).block)
    }
}
