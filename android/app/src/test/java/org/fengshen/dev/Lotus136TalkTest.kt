package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class Lotus136TalkTest {
    private val item=ItemDefinition("rom.special.0","雪蓮",null,"ORIGINAL_CPU","special",0,maxCount=1)
    private fun rule()=OriginalNpcTalkDefinition(136,"rom.map.136.flag.1","",item.id,
        "rom.dialogue.146.1","rom.dialogue.146.3").also{it.actionId=47;it.messageDialogues=mapOf(
        1 to "rom.dialogue.146.1",2 to "rom.dialogue.146.2",3 to "rom.dialogue.146.3")}
    private fun state()=SaveSnapshot("fixture",136,120,104,Key.UP,listOf(
        CharacterState("nezha",28,123456,370,490,71,120,115,99,108),
        CharacterState("yangjian",28,120000,77,451,16,121,87,94,91,statusMask=64)),
        mapOf("rom.special.18" to 1),mapOf("unrelated" to true),money=9123,encounterSteps=23)
    @Test fun all7168OriginalSelectorCasesAndGiftPreservePartyEconomyAndOtherFlags(){
        val rows=javaClass.getResourceAsStream("/world-lotus136-selector-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(7168,rows.size)
        for(line in rows){
            val r=line.split('\t').map(String::toInt);val original=state();val flags=(0..7).associate{
                "rom.map.136.flag.${1 shl it}" to (r[0]and(1 shl it)!=0)}+("unrelated" to true)+
                ("rom.inventory.special.0.used" to (r[2]and 128!=0))
            val before=original.copy(characters=original.characters.map{if(it.id=="yangjian")it.copy(statusMask=r[1])else it},
                flags=flags,inventory=original.inventory+(item.id to (r[2]and 127)))
            val result=OriginalNpcTalk.begin(before,rule(),item);assertTrue(line,result.applied)
            assertEquals(line,"rom.dialogue.146.${r[3]}",result.nextDialogue)
            assertEquals(before.characters,result.snapshot.characters);assertEquals(before.money,result.snapshot.money)
            assertEquals(before.encounterSteps,result.snapshot.encounterSteps)
            val healthyFirst=r[0]and 1==0&&r[1]and 64==0
            val gift=r[3]==1
            val expected=before.copy(flags=(if(healthyFirst)flags+("rom.map.136.flag.1" to true)else flags).let{
                if(gift)it-"rom.inventory.special.0.used"else it},inventory=if(gift)before.inventory+(item.id to 1)else before.inventory)
            assertEquals(line,expected,result.snapshot)
        }
    }
    @Test fun fullInventoryGiftRetriesOnlyAfterCapacityChangesAndNeverCures(){
        val original=state();val full=original.copy(inventory=(1..16).associate{"rom.special.$it" to 1})
        val first=OriginalNpcTalk.begin(full,rule(),item)
        assertTrue(first.applied);assertEquals("rom.dialogue.146.1",first.nextDialogue);assertEquals(full,first.snapshot)
        val room=full.copy(inventory=full.inventory-"rom.special.16")
        val gift=OriginalNpcTalk.begin(room,rule(),item);assertEquals(1,gift.snapshot.inventory[item.id])
        assertEquals(64,gift.snapshot.characters.last().statusMask)
        assertFalse(gift.snapshot.flags["rom.map.136.flag.1"]==true)
        val repeat=OriginalNpcTalk.begin(gift.snapshot,rule(),item)
        assertEquals("rom.dialogue.146.2",repeat.nextDialogue);assertEquals(gift.snapshot,repeat.snapshot)
    }
    @Test fun wrongMapDefinitionAndInvalidCountRejectWithoutChanges(){
        val original=state()
        assertFalse(OriginalNpcTalk.begin(original.copy(mapId=37),rule(),item).applied)
        assertFalse(OriginalNpcTalk.begin(original,rule(),item.copy(originalId=1)).applied)
        val bad=original.copy(inventory=original.inventory+(item.id to 2));val result=OriginalNpcTalk.begin(bad,rule(),item)
        assertFalse(result.applied);assertEquals(bad,result.snapshot)
    }
    @Test fun nativeSingleEmptyPaddleRowIsReusedAndAmbiguousLegacyRowsArePreserved(){
        val before=state().copy(inventory=state().inventory+("rom.special.14" to 0),
            flags=state().flags+("rom.inventory.special.14.used" to true))
        val result=OriginalNpcTalk.begin(before,rule(),item);assertTrue(result.applied)
        assertEquals(1,result.snapshot.inventory[item.id]);assertFalse(result.snapshot.inventory.containsKey("rom.special.14"))
        assertFalse(result.snapshot.flags["rom.inventory.special.14.used"]==true)
        assertEquals(before.characters,result.snapshot.characters);assertEquals(before.money,result.snapshot.money)
        val uncertain=before.copy(inventory=before.inventory+(item.id to 0),flags=before.flags+("rom.inventory.special.0.used" to true))
        val rejected=OriginalNpcTalk.begin(uncertain,rule(),item);assertFalse(rejected.applied);assertEquals(uncertain,rejected.snapshot)
    }
}
