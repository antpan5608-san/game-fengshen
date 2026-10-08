package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class MapNavigationTest {
    private fun floor(width:Int=5,height:Int=5,spawnX:Int=2,spawnY:Int=2)=Scene("fixture",width,height,
        IntArray(width*height),IntArray(width*height),(0 until width*height).toSet(),spawnX,spawnY,mapId=16)
    private fun plan(w:World,x:Int,y:Int)=MapNavigationPlanner.plan(w.navigationSnapshot()!!,x,y)
    private fun walk(w:World,key:Key){w.tick(key);repeat(8){w.tick(null)}}

    @Test fun shortestEqualLengthRouteUsesUpLeftDownRightAndDoesNotMoveWorld(){
        val w=World(floor());var callbacks=0
        w.prepareTarget={callbacks++;true};w.transitionObserver={_,_,_->callbacks++}
        val snapshot=w.navigationSnapshot()!!
        val r=MapNavigationPlanner.plan(snapshot,0,0)
        assertTrue(r.reachable);assertEquals(listOf(Key.UP,Key.UP,Key.LEFT,Key.LEFT),r.steps.map{it.direction})
        assertEquals(40,w.x);assertEquals(40,w.y);assertEquals(0,w.remaining)
        assertEquals(0L,w.completedStepSeq);assertEquals(0L,w.contactTransitionSeq);assertEquals(0,callbacks)
        assertEquals(Key.DOWN,w.direction);assertEquals("",w.message)
    }
    @Test fun routeDetoursAroundWallsAndDynamicActorsRatherThanSnappingTarget(){
        val s=floor().copy(dynamicObjectCells=setOf(7))
        val w=World(s);val r=plan(w,2,0)
        assertTrue(r.reachable);assertEquals(4,r.steps.size);assertEquals(Key.LEFT,r.steps.first().direction)
        assertFalse(plan(w,2,1).reachable)
        s.collision[11]=1
        val changed=s.copy(enabled=s.enabled-11);w.sceneResolver={changed}
        val after=plan(w,2,0);assertEquals(Key.RIGHT,after.steps.first().direction)
        assertEquals(NavigationFailure.OUTSIDE_MAP,plan(w,5,0).failure)
        assertEquals(NavigationFailure.OUTSIDE_MAP,plan(w,-1,0).failure)
    }
    @Test fun snapshotsFreezeArraysSetsDirectionMapsAndExitBodyProperties(){
        val enabled=(0 until 25).toMutableSet();val objects=mutableSetOf(11)
        val edgeKeys=mutableSetOf(Key.RIGHT);val edges=mutableMapOf(0 to edgeKeys)
        val s=floor().copy(enabled=enabled,dynamicObjectCells=objects,sourceEdges=edges)
        val target=floor().copy(mapId=17)
        val exit=MapExit(16,1,2,17,2,2).also{it.contactActorId=232}
        val w=World(mapOf(16 to s,17 to target),listOf(exit),16);val snap=w.navigationSnapshot()!!
        val before=MapNavigationPlanner.plan(snap,1,2)
        assertEquals(NavigationExitKind.CONTACT,before.steps.single().exitKind)
        objects.clear();enabled.remove(11);edgeKeys.clear();edges.clear();exit.contactActorId=null
        assertEquals(before,MapNavigationPlanner.plan(snap,1,2))
    }
    @Test fun snapshotsDoNotObserveLaterLiveCollisionChanges(){
        val enabled=(0 until 25).toMutableSet();val objects=mutableSetOf<Int>()
        val blocked=mutableSetOf(Key.RIGHT);val edges=mutableMapOf(2 to blocked)
        val s=floor().also{it.collision[12]=2}.copy(enabled=enabled,dynamicObjectCells=objects,sourceEdges=edges,walkableClasses=setOf(0,2))
        val w=World(s);val old=w.navigationSnapshot()!!
        val first=MapNavigationPlanner.plan(old,3,2);assertTrue(first.reachable);assertEquals(3,first.steps.size)
        s.collision[7]=1;s.grid[7]=200;enabled.remove(7);objects.add(11);blocked.clear();edges.clear()
        assertEquals(first,MapNavigationPlanner.plan(old,3,2))
        assertEquals(1,plan(w,3,2).steps.size)
    }
    @Test fun sameMapSearchCannotPassThroughANonTargetCellExit(){
        val s=floor(3,1,0,0);val other=floor(3,1,0,0).copy(mapId=17)
        val w=World(mapOf(16 to s,17 to other),listOf(MapExit(16,1,0,17,0,0)),16)
        var prepared=0;w.prepareTarget={prepared++;false}
        assertEquals(NavigationFailure.UNREACHABLE,plan(w,2,0).failure)
        val entrance=plan(w,1,0);assertEquals(1,entrance.steps.size)
        assertEquals(NavigationExitKind.CELL,entrance.steps.single().exitKind);assertEquals(0,prepared)
        walk(w,Key.RIGHT);assertEquals(1L,w.completedStepSeq);assertEquals(1,prepared);assertEquals(16,w.mapId)
        assertFalse(w.lastCompletedStep!!.transitioned)
    }
    @Test fun actorContactAndRemovedActorMatchOriginalZeroCompletedStepRule(){
        val s=floor().copy(dynamicObjectCells=setOf(7));val other=floor().copy(mapId=17)
        val exit=MapExit(16,2,1,17,2,2).also{it.contactActorId=232;it.preserveArrivalDirection=true}
        val w=World(mapOf(16 to s,17 to other),listOf(exit),16)
        val old=w.navigationSnapshot()!!;assertEquals(NavigationExitKind.CONTACT,plan(w,2,1).steps.single().exitKind)
        exit.contactActorId=null
        assertEquals(NavigationExitKind.CONTACT,MapNavigationPlanner.plan(old,2,1).steps.single().exitKind)
        exit.contactActorId=232;w.tick(Key.UP)
        assertEquals(17,w.mapId);assertEquals(0L,w.completedStepSeq);assertEquals(1L,w.contactTransitionSeq)
        val removed=World(mapOf(16 to s.copy(dynamicObjectCells=emptySet()),17 to other),listOf(exit),16)
        assertEquals(NavigationExitKind.NONE,plan(removed,2,1).steps.single().exitKind)
        walk(removed,Key.UP);assertEquals(16,removed.mapId);assertEquals(1L,removed.completedStepSeq)
    }
    @Test fun sharedQueriesKeepWallsAndUnavailableRegionsForContact(){
        val other=floor().copy(mapId=17);val exit=MapExit(16,2,1,17,2,2).also{it.contactActorId=232}
        for(s in listOf(floor().also{it.collision[7]=1}.copy(enabled=(0 until 25).toSet()-7,dynamicObjectCells=setOf(7)),
            floor().copy(dynamicObjectCells=setOf(7),unavailableRegions=listOf(EncounterRect(1,0,2,1))))){
            val w=World(mapOf(16 to s,17 to other),listOf(exit),16)
            assertFalse(plan(w,2,1).reachable);w.tick(Key.UP);assertEquals(16,w.mapId);assertEquals(0,w.remaining)
        }
    }
    @Test fun edgeQueryMatchesOriginalImmediateEntryWithoutACompletedStep(){
        val s=floor(2,1,0,0);val other=s.copy(mapId=17)
        val exit=MapExit(16,0,0,17,1,0,edgeDirection=Key.LEFT)
        val w=World(mapOf(16 to s,17 to other),listOf(exit),16)
        val snap=w.navigationSnapshot()!!;val step=snap.step(snap.start,Key.LEFT)!!
        assertEquals(NavigationExitKind.EDGE,step.exitKind);assertEquals(-1,step.to.x)
        assertEquals(NavigationFailure.OUTSIDE_MAP,plan(w,-1,0).failure)
        w.tick(Key.LEFT);assertEquals(17,w.mapId);assertEquals(1,w.x/16)
        assertEquals(0L,w.completedStepSeq);assertEquals(0L,w.contactTransitionSeq)
    }
    @Test fun palaceRouteIncludesPlaneAndMatchesNaturalWorldSteps(){
        val s=Scene("fixture",3,4,IntArray(12),intArrayOf(1,0,1,1,8,1,3,3,3,1,8,1),
            setOf(1,4,6,7,8,10),1,0,96,setOf(0,2,3,4,5,6,7,8),terrainProfile=OriginalTerrain.PALACE)
        val w=World(s);val r=plan(w,2,2)
        assertTrue(r.reachable);assertEquals(listOf(Key.DOWN,Key.DOWN,Key.RIGHT),r.steps.map{it.direction})
        assertEquals(listOf(0,1,1),r.steps.map{it.to.terrainMode})
        for(step in r.steps){walk(w,step.direction);assertEquals(step.to.x,w.x/16);assertEquals(step.to.y,w.y/16);assertEquals(step.to.terrainMode,w.terrainMode)}
        assertEquals(3L,w.completedStepSeq)
    }
    @Test fun boatLandingUpdatesOnlySearchParkingThenReboardingUsesThatExactWaterCell(){
        val s=floor().also{for(i in it.collision.indices)it.collision[i]=4;it.collision[12]=25;it.collision[17]=0}
            .copy(enabled=setOf(17),walkableClasses=setOf(0),spawnY=3)
        var flags=emptyMap<String,Boolean>();val w=World(s);w.sceneResolver={OriginalBoat.sceneView(s,flags)}
        assertTrue(w.tryRestore(16,40,24,0,Key.DOWN,null,219))
        val snap=w.navigationSnapshot()!!;val land=snap.step(snap.start,Key.DOWN)!!
        assertEquals(0,land.to.terrainMode);assertEquals(2 to 1,land.to.parkedBoat)
        assertNull(OriginalBoat.parked(flags));assertEquals(219,w.terrainMode)
        val board=snap.step(land.to,Key.UP)!!;assertEquals(219,board.to.terrainMode)
        assertNull(snap.step(land.to,Key.LEFT)) // Other water is still blocked for the foot actor.
        walk(w,Key.DOWN);flags=OriginalBoat.flagsAfterStep(flags,w.lastCompletedStep!!)
        assertEquals(land.to.parkedBoat,OriginalBoat.parked(flags));walk(w,Key.UP)
        assertEquals(board.to.terrainMode,w.terrainMode);assertEquals(2L,w.completedStepSeq)
    }
    @Test fun snapshotIsNotAvailableHalfwayThroughNaturalStepAndCancellationNeverReturnsPartialRoute(){
        val w=World(floor());assertTrue(plan(w,2,2).steps.isEmpty())
        val snap=w.navigationSnapshot()!!;var checks=0
        val r=MapNavigationPlanner.plan(snap,4,4){++checks>=3}
        assertEquals(NavigationFailure.CANCELLED,r.failure);assertTrue(r.steps.isEmpty());assertEquals(40,w.x)
        w.tick(Key.UP);assertNull(w.navigationSnapshot());repeat(8){w.tick(null)}
        assertNotNull(w.navigationSnapshot());assertEquals(1L,w.completedStepSeq)
    }
    @Test fun ordinaryDirectionalQueriesAgreeWithActualWorldAcrossSourceTargetAndKeyMatrix(){
        val keys=listOf(Key.UP,Key.LEFT,Key.DOWN,Key.RIGHT)
        for(source in 0..2)for(target in 0..2)for(key in keys){
            if(source==1)continue
            val(dx,dy)=MapMovementQuery.delta(key);val c=IntArray(9);c[4]=source;c[(1+dy)*3+1+dx]=target
            val s=Scene("fixture",3,3,IntArray(9),c,(0..8).filter{c[it]!=1}.toSet(),1,1,16,
                walkableClasses=setOf(0,2),sourceEdges=mapOf(2 to setOf(Key.LEFT)),targetEdges=mapOf(2 to setOf(Key.UP)))
            val w=World(s);val snap=w.navigationSnapshot()!!;val query=snap.step(snap.start,key)
            walk(w,key);assertEquals("$source/$target/$key",query!=null,w.completedStepSeq==1L)
            if(query!=null){assertEquals(query.to.x,w.x/16);assertEquals(query.to.y,w.y/16);assertEquals(query.to.terrainMode,w.terrainMode)}
        }
    }
}
