package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class WestHousePickupsTest {
    @Test fun twoOriginalHouseGrantsMatch22CpuCapacityAndRepeatCases(){
        val rows=javaClass.getResourceAsStream("/world-west-house-pickups-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(22,rows.size)
        for(line in rows){
            val r=line.split('\t');val cat=r[2].toInt();val original=r[3].toInt();val mask=r[4].toInt();val kind=r[5]
            val category=if(cat==2)"weapon" else "medicine"
            fun id(n:Int)=if(cat==2&&n==0)OpeningEquipment.KNIFE_ID else "rom.$category.$n"
            val target=id(original);val others=(0..255).filter{it!=original}.take(16)
            val inventory=when(kind){
                "one","used"->mapOf(target to 1);"below_cap"->mapOf(target to 9);"at_cap"->mapOf(target to 10)
                "full_new"->others.associate{id(it)to 1};"full_existing"->others.take(15).associate{id(it)to 1}+(target to 1)
                else->emptyMap()
            }
            val prior=if(kind.startsWith("select-"))kind.substringAfter('-').toInt() else 0
            val flags=(0..7).associate{"rom.map.42.flag.${1 shl it}" to (prior and(1 shl it)!=0)}+("unrelated" to true)
            val before=SaveSnapshot("fixture",42,120,200,Key.UP,listOf(CharacterState("nezha",27,100000,199,401,81,99,80,93,86)),
                inventory,flags,money=4321,encounterSteps=17,interiorContext=InteriorContext(10,13,4))
            val item=ItemDefinition(target,"原版隐藏物品",null,"ORIGINAL_CPU",category,original,maxCount=10)
            val treasure=TreasureDefinition(target,"rom.map.42.flag.$mask").also{it.categoryGrant=cat}
            val result=WorldItems.openTreasure(before,treasure,item)
            assertTrue(WorldItems.categoryGrantEvidenceSupported("game-data/provenance/world-west-houses-resources.json",42,"rom.npc.42.${r[1]}",cat))
            if(kind.startsWith("select-"))assertEquals(line,r[6]=="1",result.applied)
            else{assertEquals(line,r[7]=="1",result.applied);assertEquals(line,r[8].toInt(),result.inventory[target]?:0)}
            if(result.applied){
                val after=before.copy(inventory=result.inventory,flags=result.flags)
                assertFalse(WorldItems.openTreasure(after,treasure,item).applied)
                assertEquals(before.copy(inventory=result.inventory,flags=result.flags),after)
            }else{assertEquals(before.inventory,result.inventory);assertEquals(before.flags,result.flags)}
        }
    }
    @Test fun unrelatedRoomCategoryAndIdentityAreRejectedByLoaderPolicy(){
        val path="game-data/provenance/world-west-houses-resources.json"
        assertFalse(WorldItems.categoryGrantEvidenceSupported(path,41,"rom.npc.42.1",2))
        assertFalse(WorldItems.categoryGrantEvidenceSupported(path,42,"rom.npc.42.1",0))
        assertFalse(WorldItems.categoryGrantEvidenceSupported(path,42,"rom.npc.42.0",2))
    }
}
