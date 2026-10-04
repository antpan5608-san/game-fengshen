package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Original controlled CPU expectations; does not prove App play or a costume timer. */
class Room116TalkTest {
    private val rule=OriginalNpcTalkDefinition(116,OriginalNpcTalk.ROOM116_ACTOR_FLAG,"","",
        "rom.dialogue.126.6","rom.dialogue.126.8").also{it.actionId=41}
    private val before=SaveSnapshot("fixture",116,88,72,Key.UP,
        listOf(CharacterState("nezha",20,8000,53,100,0,90,38,60,11,statusMask=2)),
        mapOf("rom.special.13" to 1,HerbUse.ID to 2),mapOf("unrelated" to true),money=3000,encounterSteps=17)
    @Test fun firstBeforeTextAndDurableSecondPageDoNotChangePartyInventoryOrPlayer(){
        val first=OriginalNpcTalk.begin(before,rule);assertTrue(first.applied)
        assertEquals(rule.firstDialogue,first.nextDialogue);assertTrue(first.snapshot.flags[rule.mapFlagId]==true)
        assertTrue(OriginalNpcTalk.validRoom116Pending(first.snapshot))
        assertEquals(first.snapshot,OriginalNpcTalk.begin(first.snapshot,rule).snapshot)
        val second=OriginalNpcTalk.advanceRoom116(first.snapshot,rule,rule.firstDialogue)
        assertTrue(second.applied);assertEquals(rule.repeatDialogue,second.nextDialogue)
        assertEquals(second.snapshot,OriginalNpcTalk.begin(second.snapshot,rule).snapshot)
        assertFalse(OriginalNpcTalk.advanceRoom116(second.snapshot,rule,rule.firstDialogue).applied)
        val final=OriginalNpcTalk.advanceRoom116(second.snapshot,rule,rule.repeatDialogue)
        assertTrue(final.applied);assertNull(final.nextDialogue)
        assertTrue(final.snapshot.flags[OriginalNpcTalk.ROOM116_COMPLETED_FLAG]==true)
        assertTrue(final.snapshot.flags["rom.npccontext.116.209"]==true)
        assertFalse(OriginalNpcTalk.ROOM116_PENDING_FLAG in final.snapshot.flags)
        assertFalse(OriginalNpcTalk.ROOM116_SECOND_FLAG in final.snapshot.flags)
        assertEquals(before.copy(flags=final.snapshot.flags),final.snapshot)
        assertEquals(final.snapshot,OriginalNpcTalk.begin(final.snapshot,rule).snapshot)
        assertEquals(rule.repeatDialogue,OriginalNpcTalk.begin(final.snapshot,rule).nextDialogue)
    }
    @Test fun allOriginalActorSelectorsAndEvent15CompletionBitsMatch(){
        val selector=javaClass.getResourceAsStream("/room116-actor41-selector-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(256,selector.size)
        for(row in selector){val r=row.split('\t').map(String::toInt)
            val input=before.copy(flags=before.flags+(rule.mapFlagId to(r[0]and 1!=0)))
            val result=OriginalNpcTalk.begin(input,rule);assertTrue(result.applied)
            assertEquals("rom.dialogue.126.${r[1]}",result.nextDialogue)
            assertEquals(r[3]and 1!=0,result.snapshot.flags[rule.mapFlagId]==true)
            assertEquals(r[2]!=0,result.snapshot.flags[OriginalNpcTalk.ROOM116_PENDING_FLAG]==true)
        }
        val end=javaClass.getResourceAsStream("/room116-event15-finish-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(512,end.size)
        for(row in end){val r=row.split('\t').map(String::toInt);if(r[0]and 128!=0)continue
            val flags=(0..7).associate{val bit=1 shl it;"rom.map.116.flag.$bit" to((r[0]or 1)and bit!=0)}+
                mapOf("rom.global.7c6.64" to(r[1]and 64!=0),OriginalNpcTalk.ROOM116_PENDING_FLAG to true,OriginalNpcTalk.ROOM116_SECOND_FLAG to true)
            val result=OriginalNpcTalk.advanceRoom116(before.copy(flags=flags),rule,rule.repeatDialogue);assertTrue(result.applied)
            for(i in 0..7){val bit=1 shl i;assertEquals((r[2]or 1)and bit!=0,result.snapshot.flags["rom.map.116.flag.$bit"]==true)}
            assertEquals(r[3]and 64!=0,result.snapshot.flags["rom.global.7c6.64"]==true)
        }
    }
    @Test fun staleOrInvalidPendingCannotMoveOrConsume(){
        val pending=OriginalNpcTalk.begin(before,rule).snapshot
        assertFalse(OriginalNpcTalk.begin(before.copy(x=120),rule).applied)
        assertFalse(OriginalNpcTalk.begin(before.copy(mapId=117),rule).applied)
        assertFalse(OriginalNpcTalk.validRoom116Pending(pending.copy(mapId=117)))
        assertFalse(OriginalNpcTalk.validRoom116Pending(pending.copy(flags=pending.flags+(OriginalNpcTalk.ROOM116_ACTOR_FLAG to false))))
        assertFalse(OriginalNpcTalk.validRoom116Pending(before.copy(flags=mapOf(OriginalNpcTalk.ROOM116_SECOND_FLAG to true))))
        assertFalse(OriginalNpcTalk.advanceRoom116(pending,rule,"rom.dialogue.126.8").applied)
        assertEquals(pending,OriginalNpcTalk.advanceRoom116(pending,rule,"rom.dialogue.126.8").snapshot)
    }
}
