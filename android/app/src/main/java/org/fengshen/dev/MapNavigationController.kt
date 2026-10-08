package org.fengshen.dev

/** UI-thread-owned temporary navigation. Workers receive only a frozen snapshot and epoch.
 * Delivery and next-direction observation belong to the UI thread. No clock, save, RNG or costs.
 */
internal class MapNavigationController(
    private val enqueue:(Runnable)->Unit,
    private val remove:(Runnable)->Unit,
    private val deliver:(()->Unit)->Unit,
    private val observe:()->MapNavigationSnapshot?,
    private val failed:(NavigationFailure)->Unit={}
) {
    @Volatile private var epoch=0L
    private var job:Runnable?=null
    private var scope:MapNavigationSnapshot?=null
    private var route:List<NavigationStep> = emptyList()
    private var nextIndex=0
    private var inFlight:Pair<NavigationStep,Long>?=null
    private var map:Int?=null
    var target:Pair<Int,Int>?=null;private set
    val active get()=target!=null
    val remainingSteps get()=route.subList(nextIndex,route.size)

    fun cancel(){
        epoch++;job?.let(remove);job=null;scope=null;route=emptyList();nextIndex=0;inFlight=null;map=null;target=null
    }
    /** Retarget may occur halfway through a real step: wait for its natural boundary. */
    fun request(mapId:Int,x:Int,y:Int){cancel();map=mapId;target=x to y}

    fun direction(current:MapNavigationSnapshot):Key? {
        val goal=target?:return null
        if(current.mapId!=map){cancel();return null}
        inFlight?.let{(step,seq)->
            // A terminal entrance is never continued, even when its loader rejected entry.
            if(step.exitKind!=NavigationExitKind.NONE){cancel();return null}
            if(current.completedStepSeq!=seq+1||current.start!=step.to){cancel();return null}
            inFlight=null
            nextIndex++
        }
        if(current.start.x==goal.first&&current.start.y==goal.second){cancel();return null}
        if(job!=null)return null
        val old=scope
        if(old==null||!old.sameTopology(current)||route.getOrNull(nextIndex)?.from!=current.start){
            route=emptyList();nextIndex=0;scope=null;submit(current,goal);return null
        }
        val step=route[nextIndex]
        if(current.step(current.start,step.direction)!=step){route=emptyList();nextIndex=0;scope=null;submit(current,goal);return null}
        inFlight=step to current.completedStepSeq
        return step.direction
    }
    private fun submit(snapshot:MapNavigationSnapshot,goal:Pair<Int,Int>){
        val ticket=epoch
        val work=Runnable {
            val result=MapNavigationPlanner.plan(snapshot,goal.first,goal.second){epoch!=ticket}
            if(epoch!=ticket)return@Runnable
            deliver {
                if(epoch!=ticket)return@deliver
                job=null
                val current=observe()
                if(current==null||current.mapId!=map){cancel();return@deliver}
                // Changed start/step/topology cannot install. A later boundary submits fresh work.
                if(current.start!=snapshot.start||current.completedStepSeq!=snapshot.completedStepSeq||
                    !snapshot.sameTopology(current))return@deliver
                if(!result.reachable){cancel();if(result.failure!=NavigationFailure.CANCELLED)failed(result.failure!!);return@deliver}
                scope=snapshot;route=result.steps.toList();nextIndex=0
            }
        }
        job=work
        try{enqueue(work)}catch(_:java.util.concurrent.RejectedExecutionException){cancel()}
    }
}

/** A single DOWN-bound world cell. Camera movement does not reinterpret it on UP. */
internal class MapNavigationTap(val pointer:Int,val mapId:Int,val x:Float,val y:Float,
    val cell:Pair<Int,Int>,private val slop:Float) {
    var cancelled=false;private set
    fun move(px:Float,py:Float){if(kotlin.math.hypot(px-x,py-y)>slop)cancelled=true}
    fun finish(id:Int,map:Int,px:Float,py:Float):Pair<Int,Int>? {
        move(px,py)
        return cell.takeIf{!cancelled&&id==pointer&&map==mapId}
    }
}
