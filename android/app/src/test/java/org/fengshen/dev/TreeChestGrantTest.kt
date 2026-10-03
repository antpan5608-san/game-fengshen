package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Three real original tree grants; controlled CPU comparison, not App play. */
class TreeChestGrantTest {
    private val names=listOf("medicine","special","weapon","armor")
    @Test fun threeChestsMatchAllOriginalCapacityCasesAndRepeatDoesNotGrantAgain(){
        val rows=javaClass.getResourceAsStream("/world-tree-chest-original-cpu.tsv")!!.bufferedReader().use{it.readLines()}.drop(1)
        assertEquals(21,rows.size)
        for(line in rows){
            val r=line.split('\t');val category=r[2].toInt();val original=r[3].toInt()
            fun id(n:Int)="rom.${names[category]}.$n"
            val item=ItemDefinition(id(original),"原版宝箱物品",null,"ORIGINAL_CPU",names[category],original,maxCount=r[4].toInt())
            val treasure=TreasureDefinition(item.id,"rom.map.${r[0]}.flag.${r[9]}").also{it.categoryGrant=category}
            val inventory=if(r[10]=="-")emptyMap()else r[10].split(',').associate{pair->
                val c=pair.split(':');id(c[0].toInt()) to(c[1].toInt() and 127)}
            val before=SaveSnapshot("fixture",r[0].toInt(),120,104,Key.UP,emptyList(),inventory,mapOf("unrelated" to true),money=83,encounterSteps=17)
            val result=WorldItems.openTreasure(before,treasure,item)
            assertEquals(line,r[6]=="1",result.applied)
            assertEquals(line,r[7].toInt(),result.inventory[item.id]?:0)
            assertEquals(line,r[8].toInt() and r[9].toInt()!=0,result.flags[treasure.flagId]==true)
            assertEquals(true,result.flags["unrelated"])
            if(result.applied){
                val after=before.copy(inventory=result.inventory,flags=result.flags)
                val repeat=WorldItems.openTreasure(after,treasure,item)
                assertFalse(repeat.applied);assertEquals(after.inventory,repeat.inventory);assertEquals(after.flags,repeat.flags)
                assertEquals(before.characters,after.characters);assertEquals(before.money,after.money);assertEquals(before.encounterSteps,after.encounterSteps)
            }else{assertEquals(before.inventory,result.inventory);assertEquals(before.flags,result.flags)}
        }
    }
}
