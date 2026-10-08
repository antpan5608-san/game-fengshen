package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class NavigationObjectTest {
    private fun floor()=Scene("fixture",7,7,IntArray(49),IntArray(49),(0 until 49).toSet(),0,3,mapId=16)
    private fun snap(scene:Scene)=World(scene).navigationSnapshot()!!
    private fun plan(scene:Scene,obj:NavigationObject)=MapNavigationPlanner.planToAny(snap(scene),obj.cells(),false)

    @Test fun ordinaryObjectUsesOriginalAdjacencyAndFacingOnly(){
        val obj=NavigationObject("npc",16,3,3)
        assertEquals(setOf(3 to 2,2 to 3,3 to 4,4 to 3),obj.cells())
        assertEquals(Key.DOWN,obj.facingAt(3,2));assertEquals(Key.RIGHT,obj.facingAt(2,3))
        assertEquals(Key.UP,obj.facingAt(3,4));assertEquals(Key.LEFT,obj.facingAt(4,3))
        assertNull(obj.facingAt(3,3));assertNull(obj.facingAt(2,2));assertFalse(obj.standsAt(3,3))
    }
    @Test fun explicitInteractionCellAndDirectionDoNotInventNearbyAlternatives(){
        val obj=NavigationObject("point",16,3,3,1 to 3,Key.UP)
        assertEquals(setOf(1 to 3),obj.cells());assertEquals(Key.UP,obj.facingAt(1,3))
        assertNull(obj.facingAt(2,3));assertFalse(obj.standsAt(2,3))
        assertTrue(obj.copy(interactionDirection=null).cells().isEmpty())
        assertEquals(Key.UP,obj.copy(service=true).facingAt(1,3))
    }
    @Test fun oneBfsChoosesFewestStepsAcrossLegalPosesRatherThanClosestByCoordinates(){
        val obj=NavigationObject("npc",16,3,3)
        val blocked=setOf(3*7+1,3*7+2)
        val scene=floor().copy(dynamicObjectCells=blocked+24)
        val r=plan(scene,obj);assertTrue(r.reachable);assertEquals(4,r.steps.size)
        assertEquals(listOf(Key.UP,Key.RIGHT,Key.RIGHT,Key.RIGHT),r.steps.map{it.direction})
        assertEquals(3 to 2,r.steps.last().to.let{it.x to it.y})
        assertEquals(Key.DOWN,obj.facingAt(r.steps.last().to.x,r.steps.last().to.y))
    }
    @Test fun blockedDedicatedPoseDoesNotSnapToAnotherObjectNeighbor(){
        val obj=NavigationObject("point",16,3,3,2 to 3,Key.RIGHT)
        assertEquals(NavigationFailure.UNREACHABLE,plan(floor().copy(dynamicObjectCells=setOf(23)),obj).failure)
    }
    @Test fun objectPoseCannotBorrowTerminalEntranceAsAStandingInteractionCell(){
        val scene=floor();val obj=NavigationObject("point",16,2,3,1 to 3,Key.RIGHT)
        val w=World(mapOf(16 to scene,17 to scene.copy(mapId=17)),listOf(MapExit(16,1,3,17,0,3)),16)
        assertEquals(NavigationFailure.UNREACHABLE,MapNavigationPlanner.planToAny(w.navigationSnapshot()!!,obj.cells(),false).failure)
        assertEquals(NavigationExitKind.CELL,MapNavigationPlanner.plan(w.navigationSnapshot()!!,1,3).steps.single().exitKind)
    }
    @Test fun alreadyStandingPoseNeedsZeroStepsAndMissingPoseIsUnreachable(){
        val obj=NavigationObject("npc",16,1,3);val scene=floor()
        assertTrue(plan(scene,obj).steps.isEmpty());assertEquals(Key.RIGHT,obj.facingAt(0,3))
        assertEquals(NavigationFailure.UNREACHABLE,MapNavigationPlanner.planToAny(snap(scene),emptySet(),false).failure)
        assertEquals(NavigationFailure.OUTSIDE_MAP,MapNavigationPlanner.planToAny(snap(scene),setOf(-1 to 0),false).failure)
    }
    private class Harness {
        val w=World(Scene("fixture",5,5,IntArray(25),IntArray(25),(0 until 25).toSet(),0,2,mapId=16))
        val workers=java.util.ArrayDeque<Runnable>();val deliveries=java.util.ArrayDeque<()->Unit>()
        var objectValue:NavigationObject?=NavigationObject("same-id",16,3,2)
        val arrivals=mutableListOf<Pair<NavigationObject,Key>>()
        val failures=mutableListOf<NavigationFailure>()
        val c=MapNavigationController({workers.add(it)},{workers.remove(it);Unit},{deliveries.add(it)},
            {w.navigationSnapshot()},{failures.add(it)},{id->objectValue?.takeIf{it.id==id}},{obj,key->arrivals.add(obj to key)})
        fun direction()=c.direction(w.navigationSnapshot()!!)
        fun plan(){assertNull(direction());workers.removeFirst().run();deliveries.removeFirst().invoke()}
        fun walk(key:Key){w.tick(key);while(w.remaining!=0)w.tick(null)}
    }
    @Test fun arrivalIsReportedOnceAfterNaturalStepsWithoutActionOrExtraStep(){
        val h=Harness();h.c.requestObject(h.objectValue!!);h.plan()
        assertEquals(2 to 2,h.c.target);assertEquals(Key.RIGHT,h.direction());h.walk(Key.RIGHT)
        assertEquals(Key.RIGHT,h.direction());h.walk(Key.RIGHT);assertNull(h.direction())
        assertEquals(listOf(h.objectValue!! to Key.RIGHT),h.arrivals);assertFalse(h.c.active)
        assertNull(h.direction());assertEquals(1,h.arrivals.size);assertEquals(2L,h.w.completedStepSeq)
    }
    @Test fun objectMovingBeforeDeliveryAndBetweenStepsCannotInstallItsOldRoute(){
        val h=Harness();h.c.requestObject(h.objectValue!!);assertNull(h.direction());h.workers.removeFirst().run()
        h.objectValue=h.objectValue!!.copy(x=4,y=4);h.deliveries.removeFirst().invoke()
        assertTrue(h.c.remainingSteps.isEmpty());h.plan();assertEquals(Key.DOWN,h.direction());h.walk(Key.DOWN)
        h.objectValue=h.objectValue!!.copy(x=0,y=4)
        assertNull(h.direction());assertEquals(listOf(h.objectValue!! to Key.DOWN),h.arrivals)
        assertEquals(1L,h.w.completedStepSeq)
    }
    @Test fun disappearedObjectStopsAtCompletedTileWithoutArrivalOrFurtherWork(){
        val h=Harness();h.c.requestObject(h.objectValue!!);h.plan()
        val key=h.direction()!!;h.walk(key);h.objectValue=null
        assertNull(h.direction());assertFalse(h.c.active);assertTrue(h.arrivals.isEmpty())
        assertEquals(listOf(NavigationFailure.UNREACHABLE),h.failures);assertEquals(1L,h.w.completedStepSeq)
    }
    @Test fun cancellationBeforeSamePositionRequestPreventsOldArrivalCallback(){
        val h=Harness();h.c.requestObject(h.objectValue!!);assertNull(h.direction());h.workers.removeFirst().run()
        h.c.cancel();h.deliveries.removeFirst().invoke();assertTrue(h.arrivals.isEmpty());assertFalse(h.c.active)
        h.objectValue=NavigationObject("same-id",16,1,2);h.c.requestObject(h.objectValue!!)
        assertNull(h.direction());assertEquals(listOf(h.objectValue!! to Key.RIGHT),h.arrivals)
        assertTrue(h.workers.isEmpty());assertEquals(0L,h.w.completedStepSeq)
    }
}
