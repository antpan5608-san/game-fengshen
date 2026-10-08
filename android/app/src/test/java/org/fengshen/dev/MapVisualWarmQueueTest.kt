package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.RejectedExecutionException

class MapVisualWarmQueueTest {
    private class Worker {
        val pending=mutableListOf<Runnable>()
        val queue=MapVisualWarmQueue({pending.add(it)},{pending.remove(it);Unit})
        fun runNext(){pending.removeAt(0).run()}
    }
    private fun scope(map:Int=16,actors:List<String> =listOf("nezha"))=BattleVisualRequest(actors,emptyList(),map,false)
    @Test fun repeatedPostedFramesDeduplicateEvenAfterCompletion(){
        val w=Worker();var calls=0
        assertTrue(w.queue.request(scope()){_,_->calls++})
        repeat(200){assertFalse(w.queue.request(scope()){_,_->fail()})}
        assertEquals(1,w.pending.size);w.runNext();assertEquals(1,calls)
        assertFalse(w.queue.request(scope()){_,_->fail()});assertTrue(w.pending.isEmpty())
    }
    @Test fun latestScopeCoalescesWithoutRemovingStartupOrBattle(){
        val w=Worker();val events=mutableListOf<String>()
        w.pending.add(Runnable{events.add("startup")})
        w.queue.request(scope()){_,_->fail("Old pending map decoded")}
        w.pending.add(Runnable{events.add("battle")})
        repeat(50){map->w.queue.request(scope(map)){r,_->events.add("map${r.mapId}")}}
        assertEquals(3,w.pending.size);repeat(3){w.runNext()}
        assertEquals(listOf("startup","battle","map49"),events)
    }
    @Test fun cancellationBeforeBattleAndLifecycleAllowsSameMapRetry(){
        val w=Worker();var calls=0
        val old=scope()
        w.queue.request(old){_,_->fail()};val removed=w.pending.single()
        w.queue.cancel();removed.run();assertTrue(w.pending.isEmpty())
        w.pending.add(Runnable{calls++});assertTrue(w.queue.request(old){_,_->calls++})
        repeat(2){w.runNext()};assertEquals(2,calls)
        w.queue.close();assertFalse(w.queue.request(old){_,_->fail()})
    }
    @Test fun runningImageMayFinishButRemainingOldScopeNeverLoads(){
        val w=Worker();val reads=mutableListOf<String>();var oldResult:MapVisualWarmResult?=null
        w.queue.request(scope()){_,alive->oldResult=warmVisualResources(listOf("a","b","c"),{name->
            reads.add(name);if(name=="a")w.queue.request(scope(17)){_,_->reads.add("new")}
        },alive)}
        w.runNext();assertEquals(listOf("a"),reads)
        assertEquals(setOf("a"),oldResult!!.preparedFiles);assertTrue(oldResult!!.stopped)
        assertEquals(1,w.pending.size);w.runNext();assertEquals(listOf("a","new"),reads)
    }
    @Test fun completedOldTaskCannotAcceptAfterCancellation(){
        val w=Worker();var accept:(()->Boolean)?=null
        w.queue.request(scope()){_,alive->accept=alive};w.runNext();assertTrue(accept!!())
        w.queue.cancel();assertFalse(accept!!());assertTrue(w.queue.request(scope()){_,_->})
    }
    @Test fun actorIdentitiesSnapshotAndOrderDoNotCauseDuplicateJobs(){
        val w=Worker();val actors=mutableListOf("nezha","xiaolongnv");var actual:Set<String>?=null
        w.queue.request(BattleVisualRequest(actors,emptyList(),16,false)){r,_->actual=r.actorIds}
        actors.clear();assertFalse(w.queue.request(scope(actors=listOf("xiaolongnv","nezha"))){_,_->fail()})
        w.runNext();assertEquals(setOf("nezha","xiaolongnv"),actual)
        assertTrue(w.queue.request(scope(actors=listOf("nezha"))){_,_->})
    }
    @Test fun closedExecutorRejectsWarmWithoutPoisoningRetry(){
        var rejected=true;var runnable:Runnable?=null
        val q=MapVisualWarmQueue({if(rejected)throw RejectedExecutionException();runnable=it},{})
        assertFalse(q.request(scope()){_,_->fail()});rejected=false
        assertTrue(q.request(scope()){_,_->});runnable!!.run()
    }
    @Test fun enemyAndBlackSceneCannotEnterMapWarm(){
        val w=Worker()
        for(request in listOf(BattleVisualRequest(listOf("nezha"),listOf(1),16,false),
            BattleVisualRequest(listOf("nezha"),emptyList(),16,true))){
            try{w.queue.request(request){_,_->fail()};fail()}catch(_:IllegalArgumentException){}
        }
        assertTrue(w.pending.isEmpty())
    }
    @Test fun partialFailureKeepsExistingBudgetAndRealBattleCanRetry(){
        var failing=true;val reads=mutableListOf<String>()
        val cache=ResourceMap(listOf("a","b","c"),3){name->
            reads.add(name);if(name=="b"&&failing)error("Hash mismatch");ByteArray(40)
        }.limitBytes(64){it.size.toLong()}
        val result=warmVisualResources(listOf("c","b","a"),{cache.getValue(it);Unit},{true})
        assertEquals(listOf("a","b","c"),reads);assertEquals(setOf("b"),result.failedFiles)
        assertEquals(setOf("a","c"),result.preparedFiles);assertFalse(result.stopped)
        assertEquals(40L,cache.cachedBytes());assertEquals(setOf("c"),cache.cachedKeys())
        failing=false;cache.getValue("b");assertEquals(40L,cache.cachedBytes())
        assertEquals(setOf("b"),cache.cachedKeys());assertEquals(setOf("a","b","c"),cache.keys)
    }
    @Test fun exactCurrentSelectionDoesNotReadUnknownActorsEnemiesOrFutureMaps(){
        val selection=BattleVisualSelection(mapOf("nezha" to listOf("portrait","idle","attack")),
            mapOf(1 to "enemy"),mapOf("grass" to "grass","cave" to "cave"),mapOf(16 to "grass"))
        val reads=mutableListOf<String>()
        warmVisualResources(selection.battle(scope(actors=listOf("nezha","unknown"))),{reads.add(it)},{true})
        assertEquals(listOf("attack","grass","idle","portrait"),reads)
        reads.clear();val result=warmVisualResources(selection.battle(scope(999,listOf("unknown"))),{reads.add(it)},{true})
        assertTrue(reads.isEmpty());assertTrue(result.selectedFiles.isEmpty());assertFalse(result.stopped)
    }
}
