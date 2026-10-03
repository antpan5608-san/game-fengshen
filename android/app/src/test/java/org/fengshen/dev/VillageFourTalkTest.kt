package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class VillageFourTalkTest {
    private val firsts=listOf(0,2,6,8,17,22,20)
    private fun rule(i:Int)=OriginalNpcTalkDefinition(4,"rom.map.4.flag.${1 shl i}","rom.global.7c6.16","",
        "rom.dialogue.14.${firsts[i]}","rom.dialogue.14.${if(i==6)14 else firsts[i]+1}").apply {
        actionId=50;messageDialogues=mapOf(0 to firstDialogue,1 to "rom.dialogue.14.${firsts[i]+1}",2 to repeatDialogue)
    }
    private fun before(flags:Map<String,Boolean>)=SaveSnapshot("opening-segment-001-c40",4,248,440,Key.LEFT,
        listOf(CharacterState("nezha",13,3484,72,128,0,36,16,14,10),
            CharacterState("xiaolongnv",12,2063,80,92,44,22,14,26,43,maxMp=44),
            CharacterState("yangjian",24,26000,495,495,54,96,60,28,69,maxMp=54)),
        mapOf(HerbUse.ID to 2,"rom.special.19" to 1),flags,money=1019)
    @Test fun all3584OriginalSelectorCasesPreserveEconomyPartyAndOtherLocalFlags() {
        var count=0
        javaClass.getResourceAsStream("/world-village4-talk-original-cpu.tsv")!!.bufferedReader().readLines().drop(1).forEach { line->
            val c=line.split('\t').map(String::toInt);val i=Integer.numberOfTrailingZeros(c[0]);val r=rule(i)
            val flags=(0..7).associate{b->"rom.map.4.flag.${1 shl b}" to (c[1]and (1 shl b)!=0)}+
                mapOf(r.witnessFlagId to(c[2]==16),"unrelated" to true)
            val start=before(flags);val result=OriginalNpcTalk.begin(start,r)
            assertTrue(result.applied);assertEquals("rom.dialogue.14.${c[3]}",result.nextDialogue)
            for(b in 0..7)assertEquals(c[4]and (1 shl b)!=0,result.snapshot.flags["rom.map.4.flag.${1 shl b}"]==true)
            assertEquals(start.copy(flags=result.snapshot.flags),result.snapshot)
            assertEquals(start.flags[r.witnessFlagId],result.snapshot.flags[r.witnessFlagId]);count++
        };assertEquals(3584,count)
    }
    @Test fun noNpcSeenBeforeActualWitnessAndRepeatIsNotAlwaysFirstPlusOne() {
        val r=rule(6);val start=before(emptyMap())
        val first=OriginalNpcTalk.begin(start,r);assertEquals(start,first.snapshot)
        assertEquals("rom.dialogue.14.20",first.nextDialogue)
        val witnessed=start.copy(flags=mapOf(r.witnessFlagId to true));val win=OriginalNpcTalk.begin(witnessed,r)
        assertEquals("rom.dialogue.14.21",win.nextDialogue);assertTrue(win.snapshot.flags[r.mapFlagId]==true)
        val again=OriginalNpcTalk.begin(win.snapshot,r);assertEquals("rom.dialogue.14.14",again.nextDialogue)
        assertEquals(win.snapshot,again.snapshot)
    }
    @Test fun staleSceneOrUnsupportedRuleNeverChangesTheSave() {
        val r=rule(0);val start=before(emptyMap())
        assertFalse(OriginalNpcTalk.begin(start.copy(mapId=16),r).applied)
        for(bad in listOf(r.copy(itemId="rom.special.19"),r.copy(mapFlagId="rom.map.4.flag.128"))) {
            bad.actionId=50;bad.messageDialogues=r.messageDialogues
            val result=OriginalNpcTalk.begin(start,bad);assertFalse(result.applied);assertEquals(start,result.snapshot)
        }
    }
    @Test fun original90InnPreservesTwoDeadOwnersAndRestoresOnlyEligibleThirdOwner() {
        val s=before(emptyMap());val party=s.characters.mapIndexed{i,h->if(i<2)h.copy(hp=0,statusMask=32)else h.copy(hp=1,mp=0,statusMask=1)}
        val inn=InnDefinition("rom.inn.4",22,"rom.npc.22.0","客栈",90,114,"","world-village4")
        val result=InnStay.apply(1019,party,inn)
        assertNull(result.error);assertEquals(929,result.money);assertEquals(party.take(2),result.characters.take(2))
        assertEquals(party[2].copy(hp=495,mp=54,statusMask=0),result.characters[2])
        assertEquals(89,InnStay.apply(89,party,inn).money)
        assertEquals(party,InnStay.apply(89,party,inn).characters)
    }
}
