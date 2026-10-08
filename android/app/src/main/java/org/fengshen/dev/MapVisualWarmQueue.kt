package org.fengshen.dev

import java.util.concurrent.RejectedExecutionException

/** UI-owned scheduling on the original content worker. Only cancellation crosses threads. */
internal class MapVisualWarmQueue(private val enqueue:(Runnable)->Unit,private val remove:(Runnable)->Unit) {
    private data class Scope(val actors:Set<String>,val map:Int)
    private class Ticket {@Volatile var cancelled=false;lateinit var runnable:Runnable}
    private var scope:Scope?=null
    private var current:Ticket?=null
    @Volatile private var closed=false
    fun request(request:BattleVisualRequest,work:(BattleVisualRequest,()->Boolean)->Unit):Boolean {
        require(request.enemyIds.isEmpty()&&!request.blackScene)
        if(closed)return false
        val copy=BattleVisualRequest(request.actorIds,emptyList(),request.mapId,false)
        val next=Scope(copy.actorIds,copy.mapId)
        if(scope==next)return false
        cancel()
        val ticket=Ticket()
        val keepGoing={ !ticket.cancelled&&!closed&&!Thread.currentThread().isInterrupted }
        ticket.runnable=Runnable {if(keepGoing())work(copy,keepGoing)}
        scope=next;current=ticket
        return try{enqueue(ticket.runnable);true}catch(_:RejectedExecutionException){cancel();false}
    }
    /** Remove a queued warm; a running decode may finish its current image only. */
    fun cancel(){current?.let{it.cancelled=true;remove(it.runnable)};current=null;scope=null}
    fun close(){closed=true;cancel()}
}

internal data class MapVisualWarmResult(val selectedFiles:Set<String>,val preparedFiles:Set<String>,
    val failedFiles:Set<String>,val stopped:Boolean,val retainedBytes:Long=0)

/** No bundle is installed. Failed cache reads remain retryable by the real battle request. */
internal fun warmVisualResources(names:Collection<String>,load:(String)->Unit,keepGoing:()->Boolean):MapVisualWarmResult {
    val selected=names.toSortedSet();val prepared=linkedSetOf<String>();val failed=linkedSetOf<String>()
    for(name in selected){
        if(!keepGoing())break
        try{load(name);prepared.add(name)}catch(_:Exception){failed.add(name)}
    }
    return MapVisualWarmResult(selected.toSet(),prepared.toSet(),failed.toSet(),!keepGoing())
}
