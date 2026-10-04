package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class VillageSixTalkTest {
    private fun rule(i:Int)=OriginalNpcTalkDefinition(6,"rom.map.6.flag.${1 shl i}","rom.global.7c6.64","",
        "rom.dialogue.16.${listOf(2,5,9)[i]}","rom.dialogue.16.${listOf(3,6,10)[i]}").apply {
        actionId=52;messageDialogues=mapOf(0 to firstDialogue,1 to repeatDialogue,2 to repeatDialogue)
    }
    private fun before(flags:Map<String,Boolean>)=SaveSnapshot("opening-segment-001-c47",6,360,456,Key.UP,
        listOf(CharacterState("nezha",13,3484,72,128,0,36,16,14,10),
            CharacterState("xiaolongnv",12,2063,80,92,44,22,14,26,43,maxMp=44,statusMask=64),
            CharacterState("yangjian",24,26000,495,495,54,96,60,28,69,maxMp=54)),
        mapOf(HerbUse.ID to 2,"rom.special.19" to 1),flags,money=1019)
    @Test fun all6144ActualCpuSelectorsPreservePartyEconomyAndUnrelatedFlags() {
        var count=0
        javaClass.getResourceAsStream("/world-village6-talk-original.tsv")!!.bufferedReader().readLines().drop(1).forEach {line->
            val c=line.split('\t').map(String::toInt);val r=rule(c[0])
            val flags=(0..7).associate{b->"rom.map.6.flag.${1 shl b}" to(c[1]and(1 shl b)!=0)}+
                mapOf(r.witnessFlagId to(c[2]and 64!=0),"unrelated" to true)
            val start=before(flags);val result=OriginalNpcTalk.begin(start,r)
            assertTrue(result.applied);assertEquals("rom.dialogue.16.${c[3]}",result.nextDialogue)
            for(b in 0..7)assertEquals(c[4]and(1 shl b)!=0,result.snapshot.flags["rom.map.6.flag.${1 shl b}"]==true)
            assertEquals(start.copy(flags=result.snapshot.flags),result.snapshot);count++
        };assertEquals(6144,count)
    }
    @Test fun talkingDoesNotRepairRouteOrGrantRewardAndRepeatedSpeechStaysStable() {
        val r=rule(2);val start=before(emptyMap())
        val first=OriginalNpcTalk.begin(start,r);assertTrue(first.applied);assertEquals(start,first.snapshot)
        assertEquals("rom.dialogue.16.9",first.nextDialogue)
        val witnessed=start.copy(flags=mapOf(r.witnessFlagId to true));val after=OriginalNpcTalk.begin(witnessed,r)
        assertEquals(witnessed.copy(flags=witnessed.flags+(r.mapFlagId to true)),after.snapshot)
        assertEquals("rom.dialogue.16.10",after.nextDialogue)
        assertEquals(after,OriginalNpcTalk.begin(after.snapshot,r))
        // Android JSONObject round-trip is checked by the real ContentTest,
        // not the unimplemented android.jar JVM stub.
    }
    @Test fun wrongSceneMaskWitnessAndItemNeverMutateState() {
        val r=rule(0);val start=before(emptyMap())
        assertFalse(OriginalNpcTalk.begin(start.copy(mapId=5),r).applied)
        for(bad in listOf(r.copy(mapFlagId="rom.map.6.flag.8"),r.copy(witnessFlagId="rom.global.7c6.16"),r.copy(itemId="rom.special.9"))) {
            bad.actionId=52;bad.messageDialogues=r.messageDialogues
            val result=OriginalNpcTalk.begin(start,bad);assertFalse(result.applied);assertEquals(start,result.snapshot)
        }
    }
    @Test fun hiddenPickupKeepsOriginalCapacitySuccessFlagAndRepeatedClaimRules() {
        val item=ItemDefinition("rom.medicine.1","参须",null,"GAMEPLAY_VERIFIED",category="medicine",originalId=1,maxCount=10)
        val treasure=TreasureDefinition(item.id,"rom.map.6.flag.8").also{it.categoryGrant=0}
        val lines=javaClass.getResourceAsStream("/world-village6-hidden-original.tsv")!!.bufferedReader().readLines().drop(1)
        for(line in lines.filter{!it.startsWith("select-")}) {
            val c=line.split('\t');val inventory=when(c[0]) {
                "empty"->emptyMap()
                "one","used"->mapOf(item.id to 1)
                "below_cap"->mapOf(item.id to 9)
                "at_cap"->mapOf(item.id to 10)
                "full_new"->(2..17).associate{"rom.medicine.$it" to 1}
                "full_existing"->(2..16).associate{"rom.medicine.$it" to 1}+mapOf(item.id to 1)
                else->error("Unknown original case")
            }
            val start=before(emptyMap()).copy(inventory=inventory);val result=WorldItems.openTreasure(start,treasure,item)
            assertEquals(c[4]=="1",result.applied);assertEquals(c[5].toInt(),result.inventory[item.id]?:0)
            assertEquals(c[6]=="8",result.flags[treasure.flagId]==true)
            if(result.applied) {
                val after=start.copy(inventory=result.inventory,flags=result.flags)
                val repeat=WorldItems.openTreasure(after,treasure,item)
                assertFalse(repeat.applied);assertEquals(after.inventory,repeat.inventory);assertEquals(after.flags,repeat.flags)
                assertEquals(start.copy(inventory=after.inventory,flags=after.flags),after)
            } else {assertEquals(start.inventory,result.inventory);assertEquals(start.flags,result.flags)}
        }
        val evidence="game-data/provenance/world-village-batch-resources.json"
        assertTrue(WorldItems.categoryGrantEvidenceSupported(evidence,6,"rom.npc.6.3",0))
        assertFalse(WorldItems.categoryGrantEvidenceSupported(evidence,5,"rom.npc.6.3",0))
        assertFalse(WorldItems.categoryGrantEvidenceSupported(evidence,6,"rom.npc.6.4",0))
        assertFalse(WorldItems.categoryGrantEvidenceSupported(evidence,6,"rom.npc.6.3",1))
    }
}
