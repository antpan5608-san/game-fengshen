package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Controlled fixtures + original CPU tables; not normal Android progression. */
class QueenHuangTest {
    private val rule=OriginalNpcTalkDefinition(117,OriginalNpcTalk.HUANG_COMPLETED_FLAG,"","rom.special.18",
        "rom.dialogue.127.14","rom.dialogue.127.14").also{it.actionId=43}
    private val item=ItemDefinition(rule.itemId,"攢心釘",null,"ORIGINAL_CPU","special",18,maxCount=1)
    private val before=SaveSnapshot("fixture",117,120,72,Key.UP,
        listOf(CharacterState("nezha",20,12000,100,100,0,80,40,60,10,statusMask=2)),
        mapOf("rom.special.13" to 1,HerbUse.ID to 3),mapOf("rom.map.117.flag.128" to true,"unrelated" to true),3000,17)
    @Test fun giftPrecedesTextAndPendingRestartNeverGrantsTwice(){
        val first=OriginalNpcTalk.begin(before,rule,item)
        assertTrue(first.applied);assertEquals(rule.firstDialogue,first.nextDialogue)
        assertEquals(1,first.snapshot.inventory[item.id]);assertTrue(first.snapshot.flags[OriginalNpcTalk.HUANG_PENDING_FLAG]==true)
        assertFalse(first.snapshot.flags[rule.mapFlagId]==true);assertTrue(OriginalNpcTalk.validHuangPending(first.snapshot))
        val resumed=OriginalNpcTalk.begin(first.snapshot,rule,item)
        assertTrue(resumed.applied);assertEquals(first.snapshot,resumed.snapshot)
        assertEquals(before.characters,first.snapshot.characters);assertEquals(before.money,first.snapshot.money)
        assertEquals(before.x,first.snapshot.x);assertEquals(before.y,first.snapshot.y);assertEquals(17,first.snapshot.encounterSteps)
        // Even a restored pending snapshot with no inventory slot stays pending;
        // page closure never reruns a failed original gift.
        val pendingEmpty=first.snapshot.copy(inventory=before.inventory)
        assertEquals(pendingEmpty,OriginalNpcTalk.begin(pendingEmpty,rule,item).snapshot)
        val close=OriginalNpcTalk.finishHuang(first.snapshot,rule,rule.firstDialogue)
        assertTrue(close.applied);assertNull(close.nextDialogue);assertTrue(close.snapshot.flags[rule.mapFlagId]==true)
        assertTrue(close.snapshot.flags["rom.global.7fe.128"]==true)
        assertTrue(close.snapshot.flags["rom.npccontext.117.211"]==true)
        assertTrue(close.snapshot.flags["rom.npccontext.121.215"]==true)
        assertFalse(close.snapshot.flags[OriginalNpcTalk.HUANG_PENDING_FLAG]==true)
        assertEquals(first.snapshot.inventory,close.snapshot.inventory)
        assertFalse(OriginalNpcTalk.finishHuang(close.snapshot,rule,rule.firstDialogue).applied)
        assertFalse(OriginalNpcTalk.begin(close.snapshot,rule,item).applied)
    }
    @Test fun capacityExistingAndUsedMatchOriginalGrantFailureWithoutRetryReward(){
        val rows=javaClass.getResourceAsStream("/queen117-huang-gift-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(5,rows.size)
        for(row in rows){val r=row.split('\t')
            val inv=when(r[0]){
                "empty"->emptyMap()
                "existing","used"->mapOf(item.id to 1)
                "full"->(0..15).associate{"rom.special.$it" to 1}
                "full-existing"->(0..14).associate{"rom.special.$it" to 1}+(item.id to 1)
                else->error("Unknown original fixture")
            }
            val s=before.copy(inventory=inv,flags=before.flags+("runtime.item.special.18.used" to(r[0]=="used")))
            val result=OriginalNpcTalk.begin(s,rule,item);assertTrue(result.applied)
            assertEquals(row,r[1].toInt()and 127,result.snapshot.inventory[item.id]?:0)
            assertEquals(s.flags["runtime.item.special.18.used"],result.snapshot.flags["runtime.item.special.18.used"])
            val closed=OriginalNpcTalk.finishHuang(result.snapshot,rule,rule.firstDialogue)
            assertTrue(closed.applied);assertEquals(result.snapshot.inventory,closed.snapshot.inventory)
            assertFalse(OriginalNpcTalk.begin(closed.snapshot,rule,item).applied)
        }
    }
    @Test fun actorSelectorAndEvent16CompletionKeepOriginalBits(){
        val selector=javaClass.getResourceAsStream("/queen117-huang-selector-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(256,selector.size)
        for(row in selector){val r=row.split('\t').map(String::toInt)
            val s=before.copy(flags=before.flags+(rule.mapFlagId to(r[0]and 2!=0)))
            val result=OriginalNpcTalk.begin(s,rule,item)
            assertEquals(r[0]and 2==0,result.applied)
            if(result.applied){assertEquals("rom.dialogue.127.${r[1]}",result.nextDialogue);assertEquals(43,r[2])}
            assertEquals(s.flags[rule.mapFlagId],result.snapshot.flags[rule.mapFlagId])
        }
        val completion=javaClass.getResourceAsStream("/queen117-huang-event16-finish-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(1024,completion.size)
        val bits=listOf(1,2,4,8,16,32,64,128)
        for(row in completion){val r=row.split('\t').map(String::toInt)
            if(r[0]and 2!=0)continue
            val flags=bits.associate{"rom.map.117.flag.$it" to(r[0]and it!=0)}+
                bits.associate{"rom.global.7fe.$it" to(r[1]and it!=0)}+(OriginalNpcTalk.HUANG_PENDING_FLAG to true)
            val result=OriginalNpcTalk.finishHuang(before.copy(flags=flags),rule,rule.firstDialogue)
            assertTrue(result.applied)
            for(bit in bits){assertEquals((r[2]or 2)and bit!=0,result.snapshot.flags["rom.map.117.flag.$bit"]==true)
                assertEquals(r[3]and bit!=0,result.snapshot.flags["rom.global.7fe.$bit"]==true)}
        }
    }
    @Test fun wrongPositionDefinitionAndStalePageCannotSubmit(){
        val pending=OriginalNpcTalk.begin(before,rule,item).snapshot
        assertFalse(OriginalNpcTalk.finishHuang(pending,rule,"rom.dialogue.127.15").applied)
        assertFalse(OriginalNpcTalk.begin(before.copy(mapId=16),rule,item).applied)
        assertFalse(OriginalNpcTalk.begin(before.copy(x=88),rule,item).applied)
        assertFalse(OriginalNpcTalk.begin(before,rule,item.copy(originalId=19)).applied)
        assertFalse(OriginalNpcTalk.validHuangPending(pending.copy(mapId=16)))
        assertFalse(OriginalNpcTalk.validHuangPending(pending.copy(flags=pending.flags+(rule.mapFlagId to true))))
        assertEquals(before,OriginalNpcTalk.begin(before,rule,item.copy(originalId=19)).snapshot)
    }
}
