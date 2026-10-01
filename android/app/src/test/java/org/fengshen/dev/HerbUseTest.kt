package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class HerbUseTest {
    private val herb=ItemDefinition(HerbUse.ID,"藥草",null,"GAMEPLAY_VERIFIED",category="medicine",
        herbUse=HerbUseDefinition(50,true,"game-data/provenance/town02-herb.json"))
    private fun hero(hp:Int=5)=CharacterState("nezha",1,0,hp,100,0,8,4,2,4)
    private fun use(hp:Int,count:Int=3,target:String="nezha",map:Boolean=true)=
        HerbUse.apply(listOf(hero(hp)),mapOf(HerbUse.ID to count),target,herb,map)
    @Test fun originalInjuredEffectAndOneConsumption(){
        val before=hero();val items=mapOf(HerbUse.ID to 3)
        val result=HerbUse.apply(listOf(before),items,before.id,herb,true)
        assertTrue(result.applied);assertEquals(55,result.characters.single().hp)
        assertEquals(2,result.inventory[HerbUse.ID]);assertEquals(5,before.hp);assertEquals(3,items[HerbUse.ID])
        assertEquals(before.copy(hp=55),result.characters.single())
    }
    @Test fun nearMaximumClamps(){assertEquals(100,use(95).characters.single().hp)}
    @Test fun originalFullHpStillConsumes(){
        val result=use(100);assertTrue(result.applied);assertEquals(100,result.characters.single().hp)
        assertEquals(2,result.inventory[HerbUse.ID])
    }
    @Test fun finalItemRemoved(){assertFalse(use(5,1).inventory.containsKey(HerbUse.ID))}
    @Test fun emptyInventoryAndDeadTargetHaveNoEffect(){
        assertFalse(use(5,0).applied);val dead=use(0);assertFalse(dead.applied)
        assertEquals(0,dead.characters.single().hp);assertEquals(3,dead.inventory[HerbUse.ID])
    }
    @Test fun absentTargetAndBattleRejected(){assertFalse(use(5,target="enemy").applied);assertFalse(use(5,map=false).applied)}
    @Test fun otherMedicineAndMissingPolicyRejected(){
        for(item in listOf(herb.copy(id="rom.medicine.6"),herb.copy(herbUse=null))){
            assertFalse(HerbUse.apply(listOf(hero()),mapOf(HerbUse.ID to 3),"nezha",item,true).applied)
        }
    }
}
