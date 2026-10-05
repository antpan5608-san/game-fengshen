package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class WestVillageTalkTest {
    private fun state(map:Int)=SaveSnapshot("fixture",map,24,40,Key.UP,
        listOf(CharacterState("nezha",7,1000,73,90,8,30,22,19,23)),
        mapOf("rom.special.18" to 1),mapOf("unrelated" to true),money=1000)
    private fun rule(map:Int,index:Int):OriginalNpcTalkDefinition {
        val first=if(map==9)12 else if(index==0)2 else 5
        val repeat=if(map==9)4 else if(index==0)11 else 12
        return OriginalNpcTalkDefinition(map,"rom.map.$map.flag.${if(index==0)1 else 8}",
            "rom.global.7c9.nonzero","","rom.dialogue.${map+10}.$first","rom.dialogue.${map+10}.$repeat")
            .also{it.actionId=if(map==9)54 else 53}
    }
    @Test fun all5376OriginalCasesOnlyChangeEvidencedMessageAndLocalBit(){
        val rows=javaClass.getResourceAsStream("/world-west-village-talk-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(5376,rows.size)
        for(line in rows){
            val r=line.split('\t').map(String::toInt)
            val mapFlags=(0..7).associate{"rom.map.${r[0]}.flag.${1 shl it}" to (r[4]and(1 shl it)!=0)}
            val global=(0..7).associate{"rom.global.7c9.${1 shl it}" to (r[5]and(1 shl it)!=0)}
            val before=state(r[0]).copy(flags=mapFlags+global+("unrelated" to true));val def=rule(r[0],r[1])
            val result=OriginalNpcTalk.begin(before,def);assertTrue(line,result.applied)
            assertEquals("rom.dialogue.${r[0]+10}.${r[6]}",result.nextDialogue)
            val expected=if(r[5]!=0)before.copy(flags=before.flags+(def.mapFlagId to true))else before
            assertEquals(line,expected,result.snapshot)
        }
    }
    @Test fun unknownRulesAndOtherScenesDoNotChangeItemsOrState(){
        val original=state(8);val good=rule(8,0)
        for(bad in listOf(good.copy(mapFlagId="rom.map.8.flag.2"),good.copy(itemId="rom.special.18"),
            good.copy(witnessFlagId="invented.condition"),good.copy(firstDialogue="rom.dialogue.18.3"))){
            bad.actionId=53;val result=OriginalNpcTalk.begin(original,bad)
            assertFalse(result.applied);assertEquals(original,result.snapshot)
        }
        assertFalse(OriginalNpcTalk.begin(state(7),good).applied)
    }
}
