package org.fengshen.dev

/** Shared read-only exit queries. Execution/entry and completed-step costs stay in World. */
internal object MapMovementQuery {
    fun delta(key:Key)=when(key){
        Key.UP->0 to -1;Key.LEFT->-1 to 0;Key.DOWN->0 to 1;Key.RIGHT->1 to 0;else->0 to 0
    }
    fun edge(exits:List<MapExit>,map:Int,x:Int,y:Int,key:Key)=exits.firstOrNull{
        it.fromMapId==map&&it.triggerX==x&&it.triggerY==y&&it.edgeDirection==key
    }
    fun contact(scene:Scene,exits:List<MapExit>,map:Int,x:Int,y:Int,mode:Int,key:Key):MapExit? {
        if(mode!=0)return null
        val(dx,dy)=delta(key);val nx=x+dx;val ny=y+dy
        val exit=exits.firstOrNull{it.fromMapId==map&&it.contactActorId!=null&&it.triggerX==nx&&it.triggerY==ny}?:return null
        val cell=ny*scene.width+nx
        if(cell !in scene.dynamicObjectCells)return null
        // Preserve the original contact exemption: only this reviewed actor, never walls/other actors.
        return exit.takeIf{scene.copy(dynamicObjectCells=scene.dynamicObjectCells-cell)
            .probeFrom(x,y,key,mode)==MovementBlock.NONE}
    }
}

data class NavigationCell(val x:Int,val y:Int,val terrainMode:Int,val parkedBoat:Pair<Int,Int>?=null)
enum class NavigationExitKind { NONE, CELL, EDGE, CONTACT }
data class NavigationStep(val from:NavigationCell,val to:NavigationCell,val direction:Key,
    val exitKind:NavigationExitKind=NavigationExitKind.NONE)
enum class NavigationFailure { OUTSIDE_MAP, UNREACHABLE, CANCELLED, SEARCH_LIMIT }
data class NavigationPlan(val steps:List<NavigationStep>,val failure:NavigationFailure?=null) {
    val reachable get()=failure==null
}

/** Private defensive copies: a worker cannot observe later live collision/flag/vehicle changes.
 * This is navigation data only; no SaveSnapshot, RNG, loader, callbacks or entry operations.
 */
class MapNavigationSnapshot private constructor(private val scene:Scene,private val exits:List<MapExit>,
    val start:NavigationCell,val completedStepSeq:Long) {
    val mapId get()=scene.mapId
    val width get()=scene.width
    val height get()=scene.height
    fun contains(x:Int,y:Int)=x in 0 until width&&y in 0 until height
    /** Exact movement topology comparison, not a hash whose collision could accept stale work.
     * Parking is compared by each expected NavigationCell; other vehicle capabilities are topology.
     */
    internal fun sameTopology(other:MapNavigationSnapshot):Boolean {
        val a=scene;val b=other.scene
        return a.mapId==b.mapId&&a.version==b.version&&a.width==b.width&&a.height==b.height&&
            a.grid.contentEquals(b.grid)&&a.collision.contentEquals(b.collision)&&a.enabled==b.enabled&&
            a.walkableClasses==b.walkableClasses&&a.dynamicObjectCells==b.dynamicObjectCells&&
            a.transitionCells==b.transitionCells&&a.sourceEdges==b.sourceEdges&&a.targetEdges==b.targetEdges&&
            a.unavailableRegions==b.unavailableRegions&&a.terrainProfile==b.terrainProfile&&
            a.freeBoat?.copy(parked=null)==b.freeBoat?.copy(parked=null)&&exits.size==other.exits.size&&
            exits.zip(other.exits).all{(x,y)->x==y&&x.contactActorId==y.contactActorId&&
                x.preserveArrivalDirection==y.preserveArrivalDirection&&x.arrivalTerrainMode==y.arrivalTerrainMode}
    }
    internal fun step(from:NavigationCell,key:Key):NavigationStep? {
        if(!contains(from.x,from.y)||key !in DIRECTIONS)return null
        val currentScene=if(scene.freeBoat==null||scene.freeBoat?.parked==from.parkedBoat)scene else
            scene.copy().also{it.freeBoat=scene.freeBoat?.copy(parked=from.parkedBoat)}
        val(dx,dy)=MapMovementQuery.delta(key)
        val edge=MapMovementQuery.edge(exits,mapId,from.x,from.y,key)
        if(edge!=null)return NavigationStep(from,from.copy(x=from.x+dx,y=from.y+dy),key,NavigationExitKind.EDGE)
        val contact=MapMovementQuery.contact(currentScene,exits,mapId,from.x,from.y,from.terrainMode,key)
        if(contact!=null)return NavigationStep(from,from.copy(x=from.x+dx,y=from.y+dy),key,NavigationExitKind.CONTACT)
        if(currentScene.probeFrom(from.x,from.y,key,from.terrainMode)!=MovementBlock.NONE)return null
        val decision=currentScene.terrainDecision(from.x,from.y,key,from.terrainMode)
        var to=from.copy(x=from.x+dx,y=from.y+dy,terrainMode=decision.nextMode)
        val exit=exits.firstOrNull{it.fromMapId==mapId&&it.triggerX==to.x&&it.triggerY==to.y&&
            it.edgeDirection==null&&it.contactActorId==null}
        val projection=CompletedStep(mapId,to.x,to.y,exit!=null).also{
            it.originX=from.x;it.originY=from.y;it.fromTerrainMode=from.terrainMode;it.toTerrainMode=to.terrainMode
        }
        OriginalBoat.parkingCell(projection)?.let{to=to.copy(parkedBoat=it)}
        return NavigationStep(from,to,key,if(exit==null)NavigationExitKind.NONE else NavigationExitKind.CELL)
    }
    companion object {
        internal val DIRECTIONS=listOf(Key.UP,Key.LEFT,Key.DOWN,Key.RIGHT)
        internal fun freeze(scene:Scene,exits:List<MapExit>,start:NavigationCell,seq:Long):MapNavigationSnapshot {
            val frozen=scene.copy(grid=scene.grid.copyOf(),collision=scene.collision.copyOf(),
                enabled=scene.enabled.toSet(),walkableClasses=scene.walkableClasses.toSet(),
                dynamicObjectCells=scene.dynamicObjectCells.toSet(),transitionCells=scene.transitionCells.toSet(),
                sourceEdges=scene.sourceEdges.mapValues{it.value.toSet()},targetEdges=scene.targetEdges.mapValues{it.value.toSet()},
                unavailableRegions=scene.unavailableRegions.toList()).also{it.freeBoat=scene.freeBoat?.copy()}
            val copiedExits=exits.filter{it.fromMapId==scene.mapId}.map{exit->exit.copy().also{
                it.preserveArrivalDirection=exit.preserveArrivalDirection;it.contactActorId=exit.contactActorId
                it.arrivalTerrainMode=exit.arrivalTerrainMode
            }}
            return MapNavigationSnapshot(frozen,copiedExits,start.copy(parkedBoat=frozen.freeBoat?.parked),seq)
        }
    }
}

/** Fewest cardinal moves within this map, including the original terrain mode.
 * A non-target exit is never used to continue a same-map search, even if loading it would fail.
 * The caller supplies cancellation only; live state is never captured by the planner.
 */
object MapNavigationPlanner {
    private const val MAX_VISITED=196608
    fun plan(snapshot:MapNavigationSnapshot,targetX:Int,targetY:Int,cancelled:()->Boolean={false}):NavigationPlan =
        planToAny(snapshot,setOf(targetX to targetY),true,cancelled)
    internal fun planToAny(snapshot:MapNavigationSnapshot,targets:Set<Pair<Int,Int>>,allowGoalExit:Boolean,
        cancelled:()->Boolean={false}):NavigationPlan {
        if(cancelled())return NavigationPlan(emptyList(),NavigationFailure.CANCELLED)
        if(targets.isEmpty())return NavigationPlan(emptyList(),NavigationFailure.UNREACHABLE)
        val goals=targets.filter{snapshot.contains(it.first,it.second)}.toSet()
        if(goals.isEmpty())return NavigationPlan(emptyList(),NavigationFailure.OUTSIDE_MAP)
        val start=snapshot.start
        if((start.x to start.y) in goals)return NavigationPlan(emptyList())
        val visited=HashSet<NavigationCell>();visited.add(start)
        val previous=HashMap<NavigationCell,NavigationStep>()
        val queue=java.util.ArrayDeque<NavigationCell>();queue.add(start)
        while(queue.isNotEmpty()){
            if(cancelled())return NavigationPlan(emptyList(),NavigationFailure.CANCELLED)
            val from=queue.removeFirst()
            for(key in MapNavigationSnapshot.DIRECTIONS){
                val step=snapshot.step(from,key)?:continue
                val goal=(step.to.x to step.to.y) in goals
                if(step.exitKind!=NavigationExitKind.NONE&&(!goal||!allowGoalExit))continue
                if(!visited.add(step.to))continue
                if(visited.size>MAX_VISITED)return NavigationPlan(emptyList(),NavigationFailure.SEARCH_LIMIT)
                previous[step.to]=step
                if(goal){
                    val path=ArrayList<NavigationStep>();var cursor=step.to
                    while(cursor!=start){val link=previous.getValue(cursor);path.add(link);cursor=link.from}
                    path.reverse();return NavigationPlan(path.toList())
                }
                queue.add(step.to)
            }
        }
        return NavigationPlan(emptyList(),NavigationFailure.UNREACHABLE)
    }
}
