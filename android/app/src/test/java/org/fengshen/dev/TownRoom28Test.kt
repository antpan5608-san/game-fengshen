package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class TownRoom28Test {
    @Test fun originalHiddenMedicineMatches28CpuCapacityAndPriorFlagCases(){
        val rows=javaClass.getResourceAsStream("/town-room28-hidden-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(28,rows.size)
        for(line in rows){
            val r=line.split('\t');val prior=r[1].toInt()
            val target="rom.medicine.0"
            val others=(1..16).map{"rom.medicine.$it"}
            val inventory=when(r[0]){
                "one","used"->mapOf(target to 1)
                "below_cap"->mapOf(target to 9)
                "at_cap"->mapOf(target to 10)
                "full_new"->others.associateWith{1}
                "full_existing"->others.take(15).associateWith{1}+(target to 1)
                else->emptyMap()
            }
            val flags=(0..7).associate{"rom.map.28.flag.${1 shl it}" to (prior and(1 shl it)!=0)}+("unrelated" to true)
            val before=SaveSnapshot("fixture",28,104,72,Key.RIGHT,
                listOf(CharacterState("nezha",1,0,20,20,0,0,10,10,10)),inventory,flags,
                money=321,encounterSteps=7,interiorContext=InteriorContext(0,12,23))
            val item=ItemDefinition(target,"药草",null,"ORIGINAL_CPU","medicine",0,maxCount=10)
            val treasure=TreasureDefinition(target,"rom.map.28.flag.1").also{it.categoryGrant=0}
            val result=WorldItems.openTreasure(before,treasure,item)
            assertEquals(line,r[3]=="1",result.applied)
            assertEquals(line,r[4].toInt(),result.inventory[target]?:0)
            assertEquals(line,r[5].toInt()and 1!=0,result.flags[treasure.flagId]==true)
            if(result.applied){
                val after=before.copy(inventory=result.inventory,flags=result.flags)
                val again=WorldItems.openTreasure(after,treasure,item)
                assertFalse(again.applied);assertEquals(after.inventory,again.inventory);assertEquals(after.flags,again.flags)
                assertEquals(before.copy(inventory=after.inventory,flags=after.flags),after)
            }else{assertEquals(before.inventory,result.inventory);assertEquals(before.flags,result.flags)}
        }
    }
    @Test fun grantPolicyIsLimitedToTheExactOriginalHiddenRecord(){
        assertTrue(WorldItems.categoryGrantEvidenceSupported(WorldItems.ROOM28_EVIDENCE,28,"rom.npc.28.1",0))
        assertFalse(WorldItems.categoryGrantEvidenceSupported(WorldItems.ROOM28_EVIDENCE,0,"rom.npc.28.1",0))
        assertFalse(WorldItems.categoryGrantEvidenceSupported(WorldItems.ROOM28_EVIDENCE,28,"rom.npc.28.0",0))
        assertFalse(WorldItems.categoryGrantEvidenceSupported(WorldItems.ROOM28_EVIDENCE,28,"rom.npc.28.1",1))
    }
}
