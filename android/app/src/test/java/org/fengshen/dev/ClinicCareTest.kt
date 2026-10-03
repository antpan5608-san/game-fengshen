package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Commit snapshots of the unchanged original menu, before generic menu cleanup. */
class ClinicCareTest {
    private val care=ClinicDefinition("rom.clinic.3.care",20,"rom.npc.20.0","大夫","world-clinic-rules",
        kind="TREATMENT",treatments=listOf(ClinicTreatment("poison","中毒",2,2),ClinicTreatment("confusion","錯亂",4,3)))
    private fun hero(hp:Int=35,status:Int=2)=CharacterState("nezha",12,2000,hp,128,3,8,4,2,4,
        maxMp=44,statusMask=status)
    @Test fun originalStatusMoneyAndSingleBitCommitVectors(){
        val rows=javaClass.getResourceAsStream("/clinic-care-original-menu.tsv")!!.bufferedReader().use{it.readLines()}.drop(1)
        assertEquals(27,rows.size)
        var executed=0
        for(line in rows){
            val v=line.split('\t');if(v[10]=="0")continue // Original B-cancel; actual Android cancel is tested separately.
            val before=hero(v[3].toInt(),v[2].toInt()).copy(mp=v[8].toInt())
            val other=hero(29,4).copy(id="xiaolongnv",mp=9,maxHp=92)
            val treatment=care.treatments[v[1].toInt()]
            val result=ClinicCare.apply(v[4].toInt(),listOf(before,other),"nezha",care,treatment.id)
            assertEquals(line,v[7].toInt(),result.money)
            assertEquals(line,before.copy(statusMask=v[5].toInt(),hp=v[6].toInt(),mp=v[9].toInt()),result.characters[0])
            assertEquals(line,other,result.characters[1])
            assertEquals(line,before.statusMask and treatment.statusMask!=0&&v[4].toInt()>=treatment.price,result.applied)
            executed++
        }
        assertEquals(25,executed)
    }
    @Test fun chosenTargetLatestStatusAndRepeatedCommitCannotChargeAgain(){
        val party=listOf(hero(),hero(29,36).copy(id="xiaolongnv"))
        val result=ClinicCare.apply(887,party,"xiaolongnv",care,"confusion")
        assertTrue(result.applied);assertEquals(884,result.money)
        assertEquals(party[0],result.characters[0]);assertEquals(party[1].copy(statusMask=32),result.characters[1])
        val again=ClinicCare.apply(result.money,result.characters,"xiaolongnv",care,"confusion")
        assertFalse(again.applied);assertEquals(result.money,again.money);assertEquals(result.characters,again.characters)
        assertFalse(ClinicCare.apply(887,party,"missing",care,"poison").applied)
        assertFalse(ClinicCare.apply(887,party,"nezha",care,"missing").applied)
    }
    @Test fun wrongPriceMaskKindOrDuplicatePartyRefusedBeforeAnyCommit(){
        val party=listOf(hero())
        for(rule in listOf(care.copy(kind="REVIVAL"),care.copy(treatments=care.treatments.map{it.copy(price=1)}),
            care.copy(treatments=care.treatments.map{it.copy(statusMask=255)}),care.copy(moneyLimit=9999999))){
            val result=ClinicCare.apply(887,party,"nezha",rule,"poison")
            assertFalse(result.applied);assertEquals(887,result.money);assertEquals(party,result.characters)
        }
        assertFalse(ClinicCare.apply(887,party+party,"nezha",care,"poison").applied)
    }
}
