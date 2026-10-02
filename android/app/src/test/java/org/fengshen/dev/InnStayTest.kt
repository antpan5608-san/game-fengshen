package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Expected results follow the ten original-ROM experiments in world-full01 provenance. */
class InnStayTest {
    private val inn=InnDefinition("rom.inn.0",22,"rom.npc.22.0","客栈",4,0x72,"","world-full01")
    private fun hero(hp:Int=5,status:Int=0)=CharacterState("nezha",1,0,hp,100,1,8,4,2,4,
        maxMp=10,statusMask=status)
    @Test fun fixedPartyPriceAndExcludedMemberRemainIntact(){
        val party=listOf(hero(),hero(0,32).copy(id="second"))
        val result=InnStay.apply(315,party,inn)
        assertNull(result.error);assertEquals(311,result.money)
        assertEquals(party[0].copy(hp=100,mp=10),result.characters[0])
        assertEquals(party[1],result.characters[1]);assertEquals(5,party[0].hp)
    }
    @Test fun eachOriginalBlockedFlagPreservesHpMpAndStatus(){
        for(flag in listOf(2,16,32,64,0x72)){
            val h=hero(status=flag);val result=InnStay.apply(315,listOf(h),inn)
            assertEquals(311,result.money);assertEquals(h,result.characters.single())
        }
    }
    @Test fun OriginalEligibleStatusClearsAndFullPartyStillPays(){
        assertEquals(hero().copy(hp=100,mp=10),InnStay.apply(315,listOf(hero(status=4)),inn).characters.single())
        val full=hero(100).copy(mp=10)
        val result=InnStay.apply(315,listOf(full),inn)
        assertEquals(311,result.money);assertEquals(full,result.characters.single())
    }
    @Test fun insufficientMoneyNeverChangesAnyMember(){
        val party=listOf(hero(),hero(status=4));val result=InnStay.apply(3,party,inn)
        assertNotNull(result.error);assertEquals(3,result.money);assertEquals(party,result.characters)
        assertEquals(0,InnStay.apply(4,party,inn).money)
    }
    @Test fun invalidServiceAndEmptyPartyAreRejectedWithoutCharge(){
        for(service in listOf(inn.copy(price=-1),inn.copy(blockedStatusMask=256))){
            val result=InnStay.apply(315,listOf(hero()),service)
            assertNotNull(result.error);assertEquals(315,result.money);assertEquals(listOf(hero()),result.characters)
        }
        val empty=InnStay.apply(315,emptyList(),inn)
        assertNotNull(empty.error);assertEquals(315,empty.money)
    }
}
