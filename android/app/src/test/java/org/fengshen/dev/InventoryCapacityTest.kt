package org.fengshen.dev

import org.junit.Test
import org.junit.Assert.*

class InventoryCapacityTest {
    private val shop=ShopDefinition("medicine",19,"npc","店铺",listOf("rom.medicine.0"),setOf("rom.medicine.0"))
    private val herb=ItemDefinition("rom.medicine.0","药草",null,"GAMEPLAY_VERIFIED",category="medicine",buyPrice=15)
    @Test fun fullCategoryIncludesGoodsFromOtherStores(){
        val bag=(1..16).associate{"rom.medicine.$it" to 1}
        val r=TownTrade.buy(100,bag,shop,herb)
        assertEquals("物品栏已满",r.error);assertEquals(100,r.money);assertEquals(bag,r.inventory)
        val next=TownTrade.buy(100,bag-"rom.medicine.16",shop,herb)
        assertNull(next.error);assertEquals(85,next.money);assertEquals(1,next.inventory[herb.id])
    }
    @Test fun existingStackAndOtherCategoriesDoNotOccupyNewCategorySlots(){
        val bag=(0..15).associate{"rom.medicine.$it" to 1}+(0..15).associate{"rom.weapon.$it" to 1}
        assertNull(TownTrade.buy(100,bag,shop,herb).error)
        assertEquals(2,TownTrade.buy(100,bag,shop,herb).inventory[herb.id])
        assertEquals("weapon",InventoryCapacity.category(OpeningEquipment.KNIFE_ID))
    }
    @Test fun fullBagCannotLoseEquipmentOnRemovalOrReplacement(){
        val hero=CharacterState("nezha",1,0,20,20,0,8,4,5,3,equipment=EquipmentState(0,-1,-1,-1))
        val knife=EquipmentDefinition(OpeningEquipment.KNIFE_ID,0,"rightHand",2,setOf("nezha"),"GAMEPLAY_VERIFIED")
        val hand=EquipmentDefinition("rom.weapon.1",1,"rightHand",5,setOf("nezha"),"GAMEPLAY_VERIFIED")
        val bag=(1..16).associate{"rom.weapon.$it" to 1}
        assertNull(OpeningEquipment.unequip(hero,bag,knife))
        assertNull(OpeningEquipment.replace(hero,bag,hand,listOf(knife,hand)))
        assertEquals(0,hero.equipment!!.rightHand);assertEquals(16,bag.size)
    }
}
