package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Native CPU tables plus isolated durable event checks; not Android play. */
class OriginalJiangJoinTest {
    private val rule=OriginalJiangJoinDefinition(OriginalJiangJoin.EVIDENCE)
    private val hero=CharacterState("nezha",32,59000,450,450,41,120,90,70,60,maxMp=41)
    private val girl=hero.copy(id="xiaolongnv")
    private val yang=hero.copy(id="yangjian")
    private val jiang=CharacterState("jiangziya",38,190000,1608,1608,151,235,109,63,124,
        maxMp=151,equipment=EquipmentState(44,-1,24,28))
    private fun before()=SaveSnapshot("fixture",121,23*16+8,13*16+8,Key.UP,listOf(hero,girl,yang),
        mapOf(HerbUse.ID to 7,"rom.special.1" to 0),mapOf(OriginalSceneItems.PLAGUE_FLAG to true,
            OriginalJiangJoin.PANXI_FLAG to true,"old" to true),money=8342,encounterSteps=113)
    @Test fun kingMessageAndMapBitMatchAllScopedOriginalCpuCases(){
        val rows=javaClass.getResourceAsStream("/world-jiang-king-selector-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(256,rows.size)
        for(row in rows){
            val v=row.split('\t').map{it.toInt()};val flags=mapOf(
                OriginalJiangJoin.KING_FLAG to (v[0] and 16!=0),
                OriginalSceneItems.PLAGUE_FLAG to (v[1]!=0),OriginalJiangJoin.PANXI_FLAG to (v[2]!=0),"old" to true)
            // Unreachable party1/2 invitation branch is deliberately rejected
            // instead of overwriting the native fourth slot in a malformed save.
            val s=before().copy(flags=flags,characters=(listOf(hero,girl,yang,jiang)).take(v[3]))
            val result=OriginalJiangJoin.begin(s,rule)
            if(v[3]<3&&v[4]==16){assertFalse(result.applied);assertEquals(s,result.snapshot);continue}
            assertTrue(row,result.applied);assertEquals(row,"rom.dialogue.131.${v[4]}",result.nextDialogue)
            assertEquals(row,v[5] and 16!=0,result.snapshot.flags[OriginalJiangJoin.KING_FLAG]==true)
            assertEquals(s.characters,result.snapshot.characters);assertEquals(s.inventory,result.snapshot.inventory)
            assertEquals(s.money,result.snapshot.money);assertEquals(s.encounterSteps,result.snapshot.encounterSteps)
        }
    }
    @Test fun invitationJoinsOnlyAtTheNativeBoundaryAndFullScriptCompletesOnce(){
        val s=before();var current=OriginalJiangJoin.begin(s,rule)
        assertTrue(current.applied);assertEquals(s.characters,current.snapshot.characters)
        assertEquals(s.mapId,current.snapshot.mapId);assertTrue(rule.validPending(current.snapshot))
        val invite=current.snapshot
        val wrong=OriginalJiangJoin.advance(invite,rule,"rom.dialogue.17.7",jiang)
        assertFalse(wrong.applied);assertEquals(invite,wrong.snapshot)
        current=OriginalJiangJoin.advance(invite,rule,"rom.dialogue.131.16",jiang)
        assertTrue(current.applied);assertEquals(s.characters+jiang,current.snapshot.characters)
        assertEquals(7,current.snapshot.mapId);assertEquals(23*16+8,current.snapshot.x);assertEquals(7*16+8,current.snapshot.y)
        assertEquals(Key.UP,current.snapshot.direction);assertEquals(0,current.snapshot.encounterSteps)
        assertEquals(s.money,current.snapshot.money);assertEquals(s.inventory,current.snapshot.inventory)
        val joined=current.snapshot
        assertFalse(OriginalJiangJoin.advance(joined,rule,"rom.dialogue.131.16",jiang).applied)
        for(message in (listOf(12)+(7..11)).map{"rom.dialogue.17.$it"}){
            assertTrue(rule.validPending(current.snapshot));assertEquals(message,current.nextDialogue)
            current=OriginalJiangJoin.advance(current.snapshot,rule,message,jiang);assertTrue(current.applied)
        }
        assertNull(current.nextDialogue);assertTrue(current.snapshot.flags["rom.map.7.flag.128"]==true)
        assertTrue(current.snapshot.flags[rule.pendingFlag]!=true)
        assertEquals(joined.characters,current.snapshot.characters);assertEquals(s.money,current.snapshot.money)
        assertEquals(s.inventory,current.snapshot.inventory)
        assertFalse(OriginalJiangJoin.advance(current.snapshot,rule,"rom.dialogue.17.11",jiang).applied)
        val repeat=OriginalJiangJoin.begin(current.snapshot.copy(mapId=121),rule)
        assertEquals("rom.dialogue.131.17",repeat.nextDialogue);assertEquals(current.snapshot.characters,repeat.snapshot.characters)
    }
    @Test fun missingWrongOrStaleInitializationAndCorruptPendingKeepTheOriginalSave(){
        val started=OriginalJiangJoin.begin(before(),rule).snapshot
        for(template in listOf(null,jiang.copy(level=37),jiang.copy(strength=109),jiang.copy(stamina=235),
            jiang.copy(experience=190001),jiang.copy(equipment=EquipmentState(44,44,24,28)))){
            val r=OriginalJiangJoin.advance(started,rule,"rom.dialogue.131.16",template)
            assertFalse(r.applied);assertEquals(started,r.snapshot)
        }
        assertFalse(rule.validPending(started.copy(mapId=7)))
        assertFalse(rule.validPending(started.copy(characters=listOf(hero,girl))))
        assertFalse(rule.validPending(started.copy(flags=started.flags-OriginalJiangJoin.PANXI_FLAG)))
        assertFalse(rule.validPending(started.copy(flags=started.flags+(rule.continuation.stageKey(rule.id,3) to true))))
        val joined=OriginalJiangJoin.advance(started,rule,"rom.dialogue.131.16",jiang).snapshot
        assertFalse(rule.validPending(joined.copy(x=joined.x+16)))
        assertFalse(rule.validPending(joined.copy(characters=joined.characters.dropLast(1))))
        assertFalse(rule.validPending(joined.copy(flags=joined.flags-OriginalJiangJoin.PANXI_FOUR_FLAG)))
    }
}
