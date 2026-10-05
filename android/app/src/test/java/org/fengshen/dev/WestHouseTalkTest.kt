package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class WestHouseTalkTest {
    private fun rule(map:Int)=OriginalNpcTalkDefinition(map,"rom.map.$map.flag.${if(map==41)1 else 2}",
        "rom.inventory.special.14.used","rom.special.14","rom.dialogue.${map+10}.${if(map==41)3 else 5}",
        "rom.dialogue.${map+10}.${if(map==41)4 else 7}").also{it.actionId=if(map==41)55 else 56}
    private fun state(map:Int)=SaveSnapshot("fixture",map,120,104,Key.UP,
        listOf(CharacterState("nezha",27,121000,321,401,81,99,80,93,86)),
        mapOf("rom.special.18" to 1),mapOf("unrelated" to true),money=4500,encounterSteps=17,
        interiorContext=InteriorContext(10,if(map==41)25 else 13,if(map==41)7 else 4))
    @Test fun all2048OriginalWitnessCasesPreservePartyItemsMoneyAndCaller(){
        val rows=javaClass.getResourceAsStream("/world-west-house-talk-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(2048,rows.size)
        for(line in rows){
            val r=line.split('\t').map(String::toInt);val def=rule(r[0])
            val flags=(0..7).associate{"rom.map.${r[0]}.flag.${1 shl it}" to (r[1]and(1 shl it)!=0)}+
                ("rom.inventory.special.14.used" to (r[2]and 128!=0))+("unrelated" to true)
            val original=state(r[0]);val count=r[2]and 127
            val before=original.copy(inventory=original.inventory+("rom.special.14" to count),flags=flags)
            val result=OriginalNpcTalk.begin(before,def);assertTrue(line,result.applied)
            assertEquals(line,"rom.dialogue.${r[0]+10}.${r[3]}",result.nextDialogue)
            val witnessed=if(r[0]==41)r[2]!=0 else r[2]and 128!=0
            assertEquals(line,if(witnessed)before.copy(flags=before.flags+(def.mapFlagId to true))else before,result.snapshot)
        }
    }
    @Test fun rejectsUnverifiedRulesOtherScenesAndInvalidInventoryWithoutSideEffects(){
        val before=state(42);val good=rule(42)
        for(bad in listOf(good.copy(itemId="rom.special.0"),good.copy(witnessFlagId="rom.ship.6812"),
            good.copy(mapFlagId="rom.map.42.flag.1"),good.copy(firstDialogue="rom.dialogue.52.6"))){
            bad.actionId=56;val result=OriginalNpcTalk.begin(before,bad)
            assertFalse(result.applied);assertEquals(before,result.snapshot)
        }
        assertFalse(OriginalNpcTalk.begin(state(41),good).applied)
        val invalid=before.copy(inventory=mapOf("rom.special.14" to 2))
        val result=OriginalNpcTalk.begin(invalid,good);assertFalse(result.applied);assertEquals(invalid,result.snapshot)
    }
}
