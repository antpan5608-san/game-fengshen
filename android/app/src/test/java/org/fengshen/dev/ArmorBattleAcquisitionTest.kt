package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Shared original category3 acquisition, not permission to equip/sell an unlisted item.
 * Golden boundary evidence: world-armor-acquisition.json, 2:A0DF/A190 and 9:9118.
 */
class ArmorBattleAcquisitionTest {
    private val skin="rom.armor.2"
    private val dress="rom.armor.11"
    private fun acquire(input:Map<String,Int>,id:String=dress):BattleAcquisition.Result =
        BattleAcquisition.apply(input,listOf(BattleLoot(id,50,"armor")),mapOf(id to "armor")){0}

    @Test fun enemy17AndEastBossDropsKeepTheirOriginalStrictMaskedThresholds() {
        for((id,threshold) in listOf(skin to 10,dress to 50))for(byte in 0..255) {
            var calls=0
            val result=BattleAcquisition.apply(emptyMap(),listOf(BattleLoot(id,threshold,"armor")),mapOf(id to "armor")){
                calls++;byte
            }
            val expected=(byte and 127)<threshold
            assertEquals("$id byte=$byte",if(expected)mapOf(id to 1) else emptyMap<String,Int>(),result.inventory)
            assertEquals(if(expected)listOf(id) else emptyList<String>(),result.acquired)
            assertTrue(result.skipped.isEmpty())
            assertEquals(1,calls)
        }
    }
    @Test fun armorUsesTenCountLimitWithoutChangingInputOrOtherCategories() {
        for(count in 0..10) {
            val input=mapOf(dress to count,"rom.medicine.0" to 8,"rom.weapon.3" to 1)
            val before=input.toMap();val result=acquire(input)
            assertEquals(minOf(10,count+1),result.inventory[dress])
            assertEquals(if(count<10)listOf(dress) else emptyList<String>(),result.acquired)
            assertEquals(if(count==10)listOf(dress) else emptyList<String>(),result.skipped)
            assertEquals(8,result.inventory["rom.medicine.0"]);assertEquals(1,result.inventory["rom.weapon.3"])
            assertEquals(before,input)
        }
    }
    @Test fun sixteenPositiveArmorKindsBlockOnlyANewKind() {
        for(size in 0..16) {
            val input=(20 until 20+size).associate{"rom.armor.$it" to 1}
            val result=acquire(input)
            assertEquals(if(size<16)1 else 0,result.inventory[dress]?:0)
            assertEquals(if(size==16)listOf(dress) else emptyList<String>(),result.skipped)
            for((id,count) in input)assertEquals(count,result.inventory[id])
            assertFalse(input.containsKey(dress))
        }
        for(count in listOf(1,9,10)) {
            val input=(20..34).associate{"rom.armor.$it" to 1}+(dress to count)
            val result=acquire(input)
            assertEquals(minOf(10,count+1),result.inventory[dress])
            assertEquals(16,result.inventory.count{it.value>0})
            assertEquals(if(count==10)listOf(dress) else emptyList<String>(),result.skipped)
        }
    }
    @Test fun unrelatedCategoriesAndZeroEntriesDoNotOccupyAnArmorSlot() {
        val input=(0..15).associate{"rom.medicine.$it" to 10}+
            (0..15).associate{"rom.weapon.$it" to 10}+
            (20..35).associate{"rom.armor.$it" to 0}
        val result=acquire(input)
        assertEquals(1,result.inventory[dress]);assertEquals(listOf(dress),result.acquired)
        for((id,count) in input)assertEquals(count,result.inventory[id])
        assertTrue(result.skipped.isEmpty())
    }
    @Test fun consecutiveArmorDropsObserveThePreviousSuccessfulGrant() {
        val input=mapOf(skin to 9,dress to 9)
        val loot=listOf(skin,skin,dress,dress).map{BattleLoot(it,50,"armor")}
        var calls=0
        val result=BattleAcquisition.apply(input,loot,mapOf(skin to "armor",dress to "armor")){calls++;0}
        assertEquals(mapOf(skin to 10,dress to 10),result.inventory)
        assertEquals(listOf(skin,dress),result.acquired);assertEquals(listOf(skin,dress),result.skipped)
        assertEquals(4,calls);assertEquals(mapOf(skin to 9,dress to 9),input)
        val nearlyFull=(20..34).associate{"rom.armor.$it" to 1}
        val second=BattleAcquisition.apply(nearlyFull,listOf(BattleLoot(skin,10,"armor"),BattleLoot(dress,50,"armor")),
            mapOf(skin to "armor",dress to "armor")){0}
        assertEquals(listOf(skin),second.acquired);assertEquals(listOf(dress),second.skipped)
        assertEquals(16,second.inventory.count{it.value>0});assertFalse(second.inventory.containsKey(dress))
    }
    @Test fun armorSupportDoesNotAdmitUnknownCategoriesOrMismatchedDefinitions() {
        for((loot,categories) in listOf(
            BattleLoot("rom.special.11",50,"special") to mapOf("rom.special.11" to "special"),
            BattleLoot(dress,50,"armor") to mapOf(dress to "weapon"),
            BattleLoot(dress,129,"armor") to mapOf(dress to "armor"))) {
            var calls=0
            assertThrows(IllegalArgumentException::class.java){BattleAcquisition.apply(emptyMap(),listOf(loot),categories){calls++;0}}
            assertEquals(0,calls)
        }
    }
}
