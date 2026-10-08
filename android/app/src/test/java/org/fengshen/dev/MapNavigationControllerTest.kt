package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class MapNavigationControllerTest {
    private fun floor()=Scene("fixture",5,5,IntArray(25),IntArray(25),(0 until 25).toSet(),2,2,mapId=16)
    private class Harness(val world:World) {
        val workers=java.util.ArrayDeque<Runnable>()
        val deliveries=java.util.ArrayDeque<()->Unit>()
        val failures=mutableListOf<NavigationFailure>()
        var available=true
        val controller=MapNavigationController({workers.add(it)},{workers.remove(it);Unit},
            {deliveries.add(it)},{if(available)world.navigationSnapshot()else null},{failures.add(it)})
        fun direction()=controller.direction(world.navigationSnapshot()!!)
        fun plan(){assertNull(direction());workers.removeFirst().run();deliveries.removeFirst().invoke()}
        fun walk(key:Key){world.tick(key);while(world.remaining!=0)world.tick(null)}
    }

    @Test fun asynchronousPlanningAndDirectionDoNotMoveOrSettleWorld(){
        val h=Harness(World(floor()));val w=h.world
        var prepares=0;w.prepareTarget={prepares++;true}
        h.controller.request(16,0,0);h.plan()
        assertEquals(4,h.controller.remainingSteps.size)
        assertEquals(Key.UP,h.direction())
        assertEquals(4,h.controller.remainingSteps.size) // Includes the committed in-flight segment.
        assertEquals(40,w.x);assertEquals(40,w.y);assertEquals(0L,w.completedStepSeq);assertEquals(0,prepares)
        assertEquals(Key.DOWN,w.direction)
        h.walk(Key.UP);assertEquals(Key.UP,h.direction())
        h.walk(Key.UP);assertEquals(Key.LEFT,h.direction())
        h.walk(Key.LEFT);assertEquals(Key.LEFT,h.direction())
        h.walk(Key.LEFT);assertNull(h.direction())
        assertFalse(h.controller.active);assertEquals(4L,w.completedStepSeq)
        assertTrue(h.controller.remainingSteps.isEmpty());assertTrue(h.failures.isEmpty())
    }
    @Test fun cancellationAfterWorkerBeforeDeliveryCannotInstallOrResume(){
        val h=Harness(World(floor()));h.controller.request(16,0,0)
        assertNull(h.direction());h.workers.removeFirst().run()
        h.controller.cancel();h.deliveries.removeFirst().invoke()
        assertFalse(h.controller.active);assertNull(h.direction());assertTrue(h.controller.remainingSteps.isEmpty())
        assertEquals(0L,h.world.completedStepSeq)
    }
    @Test fun cancelledWorkAlreadyDequeuedNeverPostsAResult(){
        val h=Harness(World(floor()));h.controller.request(16,0,0);assertNull(h.direction())
        val dequeued=h.workers.removeFirst();h.controller.cancel();dequeued.run()
        assertTrue(h.deliveries.isEmpty());assertFalse(h.controller.active);assertEquals(0L,h.world.completedStepSeq)
    }
    @Test fun retargetRejectsOldDeliveryAndUsesNaturalCompletedTile(){
        val h=Harness(World(floor()));h.controller.request(16,0,0)
        assertNull(h.direction());h.workers.removeFirst().run()
        h.controller.request(16,4,2);h.deliveries.removeFirst().invoke();h.plan()
        assertEquals(Key.RIGHT,h.direction());h.world.tick(Key.RIGHT)
        assertNull(h.world.navigationSnapshot())
        h.controller.request(16,3,4) // No finishStep or direct coordinates on retarget.
        assertTrue(h.workers.isEmpty());assertTrue(h.world.remaining>0)
        while(h.world.remaining!=0)h.world.tick(null)
        h.plan();assertEquals(Key.DOWN,h.direction())
        assertEquals(1L,h.world.completedStepSeq)
    }
    @Test fun movedStartOrStepSequenceCannotInstallACompletedOldSearch(){
        val h=Harness(World(floor()));h.controller.request(16,0,0)
        assertNull(h.direction());h.workers.removeFirst().run();h.walk(Key.RIGHT)
        h.deliveries.removeFirst().invoke();assertTrue(h.controller.remainingSteps.isEmpty())
        h.plan();assertEquals(5,h.controller.remainingSteps.size)
        // Return to the exact old cell still does not validate an old completed-step sequence.
        val j=Harness(World(floor()));j.controller.request(16,0,0)
        assertNull(j.direction());j.workers.removeFirst().run();j.walk(Key.RIGHT);j.walk(Key.LEFT)
        j.deliveries.removeFirst().invoke();assertTrue(j.controller.remainingSteps.isEmpty())
        assertEquals(2L,j.world.completedStepSeq)
    }
    @Test fun changedCollisionBeforeDeliveryAndBetweenStepsReplansWithoutWalking(){
        var scene=floor();val h=Harness(World(scene));h.world.sceneResolver={scene}
        h.controller.request(16,2,0);assertNull(h.direction());h.workers.removeFirst().run()
        scene=scene.copy(dynamicObjectCells=setOf(7));h.deliveries.removeFirst().invoke()
        assertTrue(h.controller.remainingSteps.isEmpty());h.plan()
        assertEquals(Key.LEFT,h.direction());h.walk(Key.LEFT)
        scene=scene.copy(dynamicObjectCells=setOf(7,6))
        assertNull(h.direction());assertTrue(h.controller.remainingSteps.isEmpty())
        h.workers.removeFirst().run();h.deliveries.removeFirst().invoke()
        assertTrue(h.controller.remainingSteps.isNotEmpty());assertEquals(1L,h.world.completedStepSeq)
    }
    @Test fun terminalEntranceNeverContinuesEvenWhenEntryIsRejected(){
        val scene=floor();val other=scene.copy(mapId=17)
        val h=Harness(World(mapOf(16 to scene,17 to other),listOf(MapExit(16,2,1,17,2,2)),16))
        h.world.prepareTarget={false};h.controller.request(16,2,1);h.plan()
        assertEquals(Key.UP,h.direction());h.walk(Key.UP)
        assertFalse(h.world.lastCompletedStep!!.transitioned)
        assertNull(h.direction());assertFalse(h.controller.active);assertEquals(1L,h.world.completedStepSeq)
    }
    @Test fun unavailableOwnerAndMapChangeDiscardDelivery(){
        for(changedMap in listOf(false,true)){
            val scene=floor();val h=Harness(World(mapOf(16 to scene,17 to scene.copy(mapId=17)),emptyList(),16))
            h.controller.request(16,0,0);assertNull(h.direction());h.workers.removeFirst().run()
            if(changedMap)assertTrue(h.world.tryRestore(17,40,40,0,Key.DOWN))else h.available=false
            h.deliveries.removeFirst().invoke();assertFalse(h.controller.active)
        }
    }
    @Test fun invalidTargetsReportExactFailureAndSameCellStopsWithoutWork(){
        val h=Harness(World(floor().copy(dynamicObjectCells=setOf(7))))
        for((target,failure)in listOf((5 to 0) to NavigationFailure.OUTSIDE_MAP,(2 to 1) to NavigationFailure.UNREACHABLE)){
            h.controller.request(16,target.first,target.second);h.plan()
            assertFalse(h.controller.active);assertEquals(failure,h.failures.last())
        }
        h.controller.request(16,2,2);assertNull(h.direction());assertFalse(h.controller.active)
        assertTrue(h.workers.isEmpty());assertEquals(0L,h.world.completedStepSeq)
    }
    @Test fun rejectedExecutorAndMissingExecutionCannotProduceFurtherDirections(){
        val w=World(floor());val c=MapNavigationController({throw java.util.concurrent.RejectedExecutionException()},
            {},{it()},{w.navigationSnapshot()})
        c.request(16,0,0);assertNull(c.direction(w.navigationSnapshot()!!));assertFalse(c.active)
        val h=Harness(w);h.controller.request(16,0,0);h.plan();assertEquals(Key.UP,h.direction())
        assertNull(h.direction());assertFalse(h.controller.active);assertEquals(0L,w.completedStepSeq)
    }
    @Test fun topologyIncludesMutableExitFieldsAndBoatCapabilitiesButParkingIsInCell(){
        val scene=floor();val exit=MapExit(16,2,1,17,2,2)
        fun snap()=MapNavigationSnapshot.freeze(scene,listOf(exit),NavigationCell(2,2,0),0)
        val first=snap();exit.preserveArrivalDirection=true;assertFalse(first.sameTopology(snap()))
        val second=snap();exit.arrivalTerrainMode=219;assertFalse(second.sameTopology(snap()))
        val third=snap();exit.contactActorId=2;assertFalse(third.sameTopology(snap()))
        scene.freeBoat=FreeBoatState(false,1 to 1);val boat=snap()
        scene.freeBoat=FreeBoatState(false,3 to 3);assertTrue(boat.sameTopology(snap()));assertNotEquals(boat.start,snap().start)
        scene.freeBoat=FreeBoatState(true,3 to 3);assertFalse(boat.sameTopology(snap()))
    }
    @Test fun naturalBoatLandingParkingIsAcceptedThenFootStepContinuesOriginalPath(){
        val scene=floor().also{for(i in it.collision.indices)it.collision[i]=4;it.collision[12]=25;it.collision[17]=0}
            .copy(enabled=setOf(17),walkableClasses=setOf(0),spawnY=3)
        var flags=emptyMap<String,Boolean>();val w=World(scene);w.sceneResolver={OriginalBoat.sceneView(scene,flags)}
        assertTrue(w.tryRestore(16,40,24,0,Key.DOWN,null,219));val h=Harness(w)
        h.controller.request(16,2,3);h.plan();assertEquals(Key.DOWN,h.direction())
        h.walk(Key.DOWN);flags=OriginalBoat.flagsAfterStep(flags,w.lastCompletedStep!!)
        assertEquals(0,w.terrainMode);assertEquals(2 to 1,OriginalBoat.parked(flags))
        assertEquals(Key.DOWN,h.direction());h.walk(Key.DOWN);flags=OriginalBoat.flagsAfterStep(flags,w.lastCompletedStep!!)
        assertNull(h.direction());assertFalse(h.controller.active);assertEquals(2L,w.completedStepSeq)
    }
    @Test fun downBoundCellSurvivesCameraMovementButDragWrongPointerOrMapDoesNot(){
        val tap=MapNavigationTap(8,16,10f,10f,3 to 4,8f)
        assertEquals(3 to 4,tap.finish(8,16,12f,13f))
        assertNull(tap.finish(9,16,12f,13f));assertNull(tap.finish(8,17,12f,13f))
        tap.move(30f,30f);tap.move(10f,10f);assertNull(tap.finish(8,16,10f,10f))
    }
}
