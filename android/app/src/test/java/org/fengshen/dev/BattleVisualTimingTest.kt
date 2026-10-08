package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class BattleVisualTimingTest {
    @Test fun distinguishesQueuePreparationAndUiDelivery(){
        val t=BattleVisualTiming(100,120,180,190)
        assertEquals(mapOf("queueMs" to 20L,"prepareMs" to 60L,"deliveryMs" to 10L,"readyMs" to 90L),t.durations())
        assertNull(t.firstPostedMs)
    }
    @Test fun recordsActualPostSeparatelyFromReadyAndOnlyOnce(){
        val pending=BattleVisualTiming(100,120,180,190);val posted=pending.posted(220)
        assertNull(pending.firstPostedMs);assertEquals(30L,posted.durations()["postDelayMs"])
        assertEquals(120L,posted.durations()["firstPostedMs"]);assertSame(posted,posted.posted(500))
    }
    @Test fun sameClockTickIsValidAndDoesNotInventLatency(){
        assertTrue(BattleVisualTiming(1,1,1,1).posted(1).durations().values.all{it==0L})
    }
    @Test fun rejectsNegativeAndNonMonotonicStages(){
        for(row in listOf(listOf(-1L,0L,0L,0L),listOf(2L,1L,3L,4L),listOf(1L,3L,2L,4L),listOf(1L,2L,4L,3L))){
            try{BattleVisualTiming(row[0],row[1],row[2],row[3]);fail("Invalid clock ordering accepted")}catch(_:IllegalArgumentException){}
        }
    }
    @Test fun rejectsPostBeforeUiDelivery(){
        try{BattleVisualTiming(1,2,3,4).posted(3);fail("Post precedes receipt")}catch(_:IllegalArgumentException){}
    }
    @Test fun largeMonotonicUptimeDoesNotOverflowOrMixRequests(){
        val a=BattleVisualTiming(Long.MAX_VALUE-100,Long.MAX_VALUE-90,Long.MAX_VALUE-20,Long.MAX_VALUE-10).posted(Long.MAX_VALUE)
        assertEquals(100L,a.durations()["firstPostedMs"])
        assertEquals(0L,BattleVisualTiming(0,0,0,0).posted(0).durations()["firstPostedMs"])
        assertEquals(70L,a.durations()["prepareMs"])
    }
}
