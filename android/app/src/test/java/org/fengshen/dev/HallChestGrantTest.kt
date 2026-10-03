package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Original grant boundaries, isolated state proposals, not normal Android interaction. */
class HallChestGrantTest {
    private val names=listOf("medicine","special","weapon","armor")
    private fun id(category:Int,item:Int)=if(category==2&&item==0)OpeningEquipment.KNIFE_ID else "rom.${names[category]}.$item"
    private val hero=CharacterState("nezha",10,2000,50,100,0,30,20,14,8,equipment=EquipmentState(4,-1,3,30))
    @Test fun everyOriginalScopedChestQuantityAndFullCategoryCaseMatches(){
        val lines=javaClass.getResourceAsStream("/hell-chest-grants-cpu.tsv")!!.bufferedReader().use{it.readLines()}
            .filter{it.isNotBlank()&&!it.startsWith("#")};assertEquals(72,lines.size)
        for(line in lines){
            val r=line.split('\t');val category=r[2].toInt();val original=r[3].toInt();val itemId=id(category,original)
            val item=ItemDefinition(itemId,"原版物品",null,"ORIGINAL_CPU",names[category],original,maxCount=r[4].toInt())
            val treasure=TreasureDefinition(itemId,"rom.map.${r[0]}.flag.${r[7]}").also{it.categoryGrant=category}
            val inventory=if(r[11]=="-")emptyMap()else r[11].split(',').associate{pair->
                val v=pair.split(':');id(category,v[0].toInt()) to (v[1].toInt() and 127)}
            val before=SaveSnapshot("fixture",r[0].toInt(),40,56,Key.UP,listOf(hero),inventory,
                mapOf("untouched" to true,treasure.flagId to (r[6].toInt() and r[7].toInt()!=0)),money=808,encounterSteps=9)
            val result=WorldItems.openTreasure(before,treasure,item)
            assertEquals(line,r[8]=="1",result.applied);assertEquals(line,r[9].toInt(),result.inventory[itemId]?:0)
            assertEquals(line,r[10].toInt() and r[7].toInt()!=0,result.flags[treasure.flagId]==true)
            if(!result.applied){assertEquals(before.inventory,result.inventory);assertEquals(before.flags,result.flags)}
            else{
                assertEquals(before.inventory+(itemId to (before.inventory[itemId]?:0)+1),result.inventory)
                val committed=before.copy(inventory=result.inventory,flags=result.flags)
                val repeat=WorldItems.openTreasure(committed,treasure,item);assertFalse(repeat.applied)
                assertEquals(committed.inventory,repeat.inventory);assertEquals(committed.flags,repeat.flags)
                assertEquals(before.characters,committed.characters);assertEquals(before.money,committed.money)
                assertEquals(before.encounterSteps,committed.encounterSteps)
            }
        }
    }
    @Test fun itemUseAndUnknownGrantCapabilitiesAreNeverInferredFromTheName(){
        val item=ItemDefinition("rom.medicine.12","药草",null,"fixture","medicine",12,maxCount=10)
        val before=SaveSnapshot("fixture",61,40,56,Key.UP,listOf(hero),emptyMap(),emptyMap())
        val t=TreasureDefinition(item.id,"rom.map.61.flag.2")
        assertFalse(WorldItems.openTreasure(before,t,item).applied)
        for(c in listOf(-1,1,2,3,4)){
            t.categoryGrant=c;val result=WorldItems.openTreasure(before,t,item)
            assertFalse(result.applied);assertEquals(before.inventory,result.inventory);assertEquals(before.flags,result.flags)
        }
        t.categoryGrant=0;assertTrue(WorldItems.openTreasure(before,t,item).applied)
        assertFalse(WorldItems.openTreasure(before,t,item.copy(maxCount=99)).applied)
        assertFalse(WorldItems.openTreasure(before.copy(inventory=mapOf(item.id to -1)),t,item).applied)
    }
}
