package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Controlled original CPU parity only, not an Android route or player save. */
class JiamengZone29Test {
    @Test fun originalState08PriorityAtAllFourSlotsPreservesHpAndOtherTargets(){
        val rows=javaClass.getResourceAsStream("/jiameng-zone29-state08-original.tsv")!!
            .bufferedReader().readLines().drop(1)
        assertEquals(1024,rows.size)
        for(line in rows){
            val r=line.split('\t').map(String::toInt)
            val party=(0..3).map{slot->CharacterState("fixture-$slot",1,0,369,400,0,8,4,2,4,
                statusMask=if(slot==r[0])r[1]else 2)}
            val next=party.mapIndexed{slot,actor->if(slot==r[0])OriginalStatus.applyStatus8(actor)else actor}
            assertEquals(line,party[r[0]].copy(statusMask=r[2]),next[r[0]])
            assertEquals(r[3],next[r[0]].hp);assertEquals(1,r[4])
            party.indices.filter{it!=r[0]}.forEach{assertEquals(party[it],next[it])}
        }
    }
}
