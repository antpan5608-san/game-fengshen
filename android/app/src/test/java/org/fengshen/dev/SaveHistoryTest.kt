package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class SaveHistoryTest {
    private fun state(n:Int)=SaveSnapshot("opening-segment-001-c52",16,8+n*16,24,Key.DOWN,
        listOf(CharacterState("nezha",4,99,50,100,3,20,12,8,9,equipment=EquipmentState(0,-1,1,28))),
        mapOf("rom.medicine.0" to n+1),mapOf("original.reward" to (n%2==0)),money=n*10,
        encounterSteps=n,interiorContext=InteriorContext(0,6,19),terrainMode=0)

    @Test fun manualAndAutoAndPreRestoreShareNewestTwentyOnly(){
        var entries=emptyList<SaveHistoryEntry>()
        repeat(21){n->entries=SaveHistory.append(entries,state(n),SaveHistoryEntry.Kind.entries[n%3],100L-n,"save-$n")}
        assertEquals(20,entries.size);assertEquals("save-20",entries.first().id);assertEquals("save-1",entries.last().id)
        assertEquals(listOf(20,19,18),entries.take(3).map{it.snapshot.encounterSteps})
        assertEquals(3,entries.map{it.kind}.distinct().size)
    }
    @Test fun restoreKeepsCompleteTargetAndCreatesRecoverableCurrentStateWithoutMutation(){
        val a=state(1);val b=state(2)
        val history=SaveHistory.append(emptyList(),a,SaveHistoryEntry.Kind.MANUAL,100,"A")
        val proposal=SaveHistory.prepareRestore(history,"A",b,200){true}!!
        assertEquals(a,proposal.target);assertEquals(b,proposal.entries.first().snapshot)
        assertEquals(SaveHistoryEntry.Kind.BEFORE_RESTORE,proposal.entries.first().kind)
        assertEquals(history,listOf(proposal.entries.last()))
        val undo=SaveHistory.prepareRestore(proposal.entries,proposal.entries.first().id,a,300){true}!!
        assertEquals(b,undo.target);assertEquals(a,undo.entries.first().snapshot)
    }
    @Test fun unavailableOrInvalidTargetsCannotProduceRestoreOrEvictGoodHistory(){
        val good=state(1);val invalid=state(2).copy(mapId=999)
        val history=SaveHistory.append(emptyList(),invalid,SaveHistoryEntry.Kind.MANUAL,1,"bad")
        assertNull(SaveHistory.prepareRestore(history,"bad",good,2){it.mapId!=999})
        assertNull(SaveHistory.prepareRestore(history,"missing",good,2){true})
        assertNull(SaveHistory.prepareRestore(history,"bad",invalid,2){it.mapId!=999})
        assertEquals(invalid,history.single().snapshot)
    }
    @Test fun autoSkipsUnchangedSnapshotButManualIsAlwaysRecorded(){
        val a=state(1);val manual=SaveHistory.append(emptyList(),a,SaveHistoryEntry.Kind.MANUAL,1,"A")
        assertEquals(manual,SaveHistory.append(manual,a,SaveHistoryEntry.Kind.AUTO,2,"skip"))
        val again=SaveHistory.append(manual,a,SaveHistoryEntry.Kind.MANUAL,3,"B")
        assertEquals(2,again.size)
        assertEquals(3,SaveHistory.append(again,state(2),SaveHistoryEntry.Kind.AUTO,4,"C").size)
    }
    @Test fun rejectedOrFailedRestoreCannotPublishTargetAndUndoIsInSameCommit(){
        val a=state(1);val b=state(2)
        val history=SaveHistory.append(emptyList(),a,SaveHistoryEntry.Kind.MANUAL,1,"A")
        val proposal=SaveHistory.prepareRestore(history,"A",b,2){true}!!
        var current=b;var active=b;var durable=history;var writes=0
        val rejected=SaveHistory.applyRestore(proposal,b,{false},{current}){_,_->writes++;true}
        assertEquals(SaveHistory.RestoreStatus.REJECTED,rejected);assertEquals(0,writes)
        val failed=SaveHistory.applyRestore(proposal,b,{current=it;true},{current}){_,_->writes++;false}
        assertEquals(SaveHistory.RestoreStatus.WRITE_FAILED,failed)
        assertEquals(b,current);assertEquals(b,active);assertEquals(history,durable)
        val result=SaveHistory.applyRestore(proposal,b,{current=it;true},{current}){s,h->
            assertEquals(b,h.first().snapshot);assertEquals(SaveHistoryEntry.Kind.BEFORE_RESTORE,h.first().kind)
            active=s;durable=h;true
        }
        assertEquals(SaveHistory.RestoreStatus.SAVED,result);assertEquals(a,active);assertEquals(b,durable.first().snapshot)
    }
    @Test fun failedMemoryRollbackIsReportedRatherThanClaimingSaveSuccess(){
        val before=state(2);val proposal=SaveHistory.RestoreProposal(state(1),emptyList());var applies=0
        val result=SaveHistory.applyRestore(proposal,before,{applies++;applies==1},{proposal.target}){_,_->false}
        assertEquals(SaveHistory.RestoreStatus.ROLLBACK_FAILED,result);assertEquals(2,applies)
    }
    @Test fun fiveMinuteFakeClockDefersUnsafeTransactionAndNeverCatchesUpBackground(){
        val clock=AutoSaveHistoryClock();var saves=0
        fun tick(t:Long,front:Boolean=true,safe:Boolean=true)=clock.tick(t,front,safe){saves++;true}
        assertFalse(tick(0));assertFalse(tick(299999));assertEquals(0,saves)
        assertFalse(tick(300000,safe=false));assertFalse(tick(500000,safe=false))
        assertTrue(tick(500001));assertEquals(1,saves)
        tick(500010,front=false);assertFalse(tick(2000000));assertEquals(1,saves)
        assertFalse(tick(2299990));assertTrue(tick(2300000));assertEquals(2,saves)
    }
    @Test fun lifecyclePauseAndFailedWriteRetainDueAttemptWithoutRepeatedFrameWrites(){
        val clock=AutoSaveHistoryClock();var attempts=0
        clock.tick(0,true,true){true};clock.tick(100000,true,true){true};clock.pause()
        assertFalse(clock.tick(1000000,true,true){true})
        assertFalse(clock.tick(1199999,true,true){true})
        assertFalse(clock.tick(1200000,true,true){attempts++;false});assertEquals(1,attempts)
        assertFalse(clock.tick(1200001,true,true){attempts++;false});assertEquals(1,attempts)
        assertTrue(clock.tick(1201000,true,true){attempts++;true});assertEquals(2,attempts)
        assertFalse(clock.tick(1201001,true,true){attempts++;true})
    }
}
