package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Actual CPU selector/grant rows; these are isolated fixtures, not Android normal play. */
class WestVillagePickupsTest {
    private val categories=listOf("medicine","special","weapon","armor")
    private fun state(map:Int)=SaveSnapshot("fixture",map,120,104,Key.UP,
        listOf(CharacterState("nezha",7,1000,73,90,8,30,22,19,23)),emptyMap(),mapOf("unrelated" to true),money=83,encounterSteps=17)
    @Test fun sevenHiddenPickupsMatch94OriginalRowsAndKeepOtherState(){
        val rows=javaClass.getResourceAsStream("/world-west-village-pickups-original.tsv")!!.bufferedReader().use{it.readLines()}.drop(1)
        assertEquals(94,rows.size)
        for(line in rows){
            val r=line.split('\t');val map=r[0].toInt();val cat=r[2].toInt();val original=r[3].toInt();val mask=r[4].toInt();val kind=r[5]
            fun flags(bits:Int)=(0..7).associate{"rom.map.$map.flag.${1 shl it}" to (bits and(1 shl it)!=0)}+("unrelated" to true)
            if(cat==4){
                if(!kind.startsWith("money-"))continue
                val values=kind.split('-');val before=state(map).copy(money=values[1].toInt(),flags=flags(values[2].toInt()))
                val result=WorldItems.openMoneyTreasure(before,MoneyTreasureDefinition("rom.map.$map.flag.$mask",1,999999,
                    "game-data/provenance/world-village-batch-resources.json"))
                assertEquals(line,r[7]=="1",result.applied);assertEquals(line,r[8].toInt(),result.snapshot.money)
                assertEquals(before.copy(money=r[8].toInt(),flags=flags(r[9].toInt())),result.snapshot)
                continue
            }
            fun id(n:Int)=if(cat==2&&n==0)OpeningEquipment.KNIFE_ID else "rom.${categories[cat]}.$n"
            val max=if(cat==1)1 else 10;val target=id(original)
            val item=ItemDefinition(target,"原版隐藏物品",null,"ORIGINAL_CPU",categories[cat],original,maxCount=max)
            val treasure=TreasureDefinition(target,"rom.map.$map.flag.$mask").also{it.categoryGrant=cat}
            val others=(0..255).filter{it!=original}.take(16)
            val inventory=when(kind){
                "empty"->emptyMap();"one","used"->mapOf(target to 1);"below_cap"->if(max==1)emptyMap()else mapOf(target to max-1)
                "at_cap"->mapOf(target to max);"full_new"->others.associate{id(it) to 1}
                "full_existing"->others.take(15).associate{id(it) to 1}+(target to 1)
                else->emptyMap()
            }
            val prior=if(kind.startsWith("select-"))kind.substringAfter('-').toInt()else 0
            val before=state(map).copy(inventory=inventory,flags=flags(prior));val result=WorldItems.openTreasure(before,treasure,item)
            if(kind.startsWith("select-")){
                assertEquals(line,r[6]=="1",result.applied)
            }else{
                assertEquals(line,r[7]=="1",result.applied);assertEquals(line,r[8].toInt(),result.inventory[target]?:0)
                assertEquals(line,r[9].toInt()and mask!=0,result.flags[treasure.flagId]==true)
            }
            if(result.applied){
                val after=before.copy(inventory=result.inventory,flags=result.flags)
                assertFalse(WorldItems.openTreasure(after,treasure,item).applied)
                assertEquals(before.characters,after.characters);assertEquals(before.money,after.money);assertEquals(before.encounterSteps,after.encounterSteps)
            }else{assertEquals(before.inventory,result.inventory);assertEquals(before.flags,result.flags)}
        }
    }
    @Test fun grantScopesAndMoneyDoNotInventUsesOrChangeAnotherMap(){
        assertTrue(WorldItems.categoryGrantEvidenceSupported("game-data/provenance/world-village-batch-resources.json",9,"rom.npc.9.3",1))
        assertFalse(WorldItems.categoryGrantEvidenceSupported("game-data/provenance/world-village-batch-resources.json",8,"rom.npc.9.3",1))
        val before=state(8);val result=WorldItems.openMoneyTreasure(before,MoneyTreasureDefinition("rom.map.9.flag.2",1,999999,
            "game-data/provenance/world-village-batch-resources.json"))
        assertFalse(result.applied);assertEquals(before,result.snapshot)
    }
}
