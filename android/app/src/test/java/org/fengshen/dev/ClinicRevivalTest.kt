package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Original menu experiments, isolated fixtures; not normal Android evidence. */
class ClinicRevivalTest {
    private val clinic=ClinicDefinition("rom.clinic.3.revival",20,"rom.npc.20.1","道士","world-clinic-rules")
    private fun hero(hp:Int=0,status:Int=32)=CharacterState("nezha",12,2000,hp,128,3,8,4,2,4,
        maxMp=44,statusMask=status)
    @Test fun originalPercentageMinimumMoneyCapAndStatusBoundaries(){
        val rows=javaClass.getResourceAsStream("/clinic-revival-original-menu.tsv")!!.bufferedReader().use{it.readLines()}.drop(1)
        assertEquals(15,rows.size)
        for(line in rows){
            val v=line.split('\t');if(v[0]=="cancel")continue
            val before=hero(v[3].toInt(),v[2].toInt()).copy(mp=v[7].toInt())
            val other=hero(29,0).copy(id="xiaolongnv",mp=9,maxHp=92)
            val party=listOf(before,other);val result=ClinicRevival.apply(v[1].toInt(),party,before.id,clinic)
            assertEquals(line,v[4].toInt(),result.money)
            assertEquals(line,v[5].toInt(),result.characters[0].statusMask)
            assertEquals(line,v[6].toInt(),result.characters[0].hp)
            assertEquals(line,v[8].toInt(),result.characters[0].mp)
            assertEquals(other,result.characters[1]);assertEquals(before,party[0])
            assertEquals(line,before.statusMask and 32!=0,result.applied)
        }
    }
    @Test fun secondActorAndRepeatedSubmissionPreservePartyAndNeverChargeTwice(){
        val party=listOf(hero(35,0),hero().copy(id="xiaolongnv"))
        val result=ClinicRevival.apply(887,party,"xiaolongnv",clinic)
        assertTrue(result.applied);assertEquals(879,result.money);assertEquals(8,result.fee)
        assertEquals(party[0],result.characters[0]);assertEquals(party[1].copy(hp=1,statusMask=1),result.characters[1])
        val again=ClinicRevival.apply(result.money,result.characters,"xiaolongnv",clinic)
        assertFalse(again.applied);assertEquals(result.money,again.money);assertEquals(result.characters,again.characters)
    }
    @Test fun missingTargetBadPolicyAndDuplicateActorDoNotModifyState(){
        val party=listOf(hero());assertFalse(ClinicRevival.apply(887,party,"missing",clinic).applied)
        for(rule in listOf(clinic.copy(deadMask=64),clinic.copy(recoveredHp=128),clinic.copy(recoveredStatus=0),
            clinic.copy(feeDenominator=10),clinic.copy(minimumFee=0),clinic.copy(moneyLimit=9999999),clinic.copy(evidence=""))){
            val result=ClinicRevival.apply(887,party,"nezha",rule)
            assertFalse(result.applied);assertEquals(887,result.money);assertEquals(party,result.characters)
        }
        assertFalse(ClinicRevival.apply(887,party+party,"nezha",clinic).applied)
        assertFalse(ClinicRevival.apply(-1,party,"nezha",clinic).applied)
    }
}
