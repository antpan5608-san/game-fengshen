package org.fengshen.dev
import org.junit.Assert.*
import org.junit.Test
class WorldTreasureScopeTest {
    @Test fun hiddenGrantMatchesOriginalCapacityAndFlagCases(){
        val item=ItemDefinition("rom.medicine.1","参须",null,"GAMEPLAY_VERIFIED",category="medicine",originalId=1,maxCount=10)
        val treasure=TreasureDefinition(item.id,"rom.map.5.flag.1").also{it.categoryGrant=0}
        val matrix=javaClass.getResourceAsStream("/world-village5-hidden-original.tsv")!!.bufferedReader().readLines().drop(1)
        for(line in matrix.filter{!it.startsWith("select-")}){
            val row=line.split('\t');val name=row[0]
            val inventory=when(name){
                "empty"->emptyMap()
                "one","used"->mapOf(item.id to 1)
                "below_cap"->mapOf(item.id to 9)
                "at_cap"->mapOf(item.id to 10)
                "full_new"->(2..17).associate{"rom.medicine.$it" to 1}
                "full_existing"->(2..16).associate{"rom.medicine.$it" to 1}+mapOf(item.id to 1)
                else->error("Unrecognized original case")
            }
            val before=SaveSnapshot("fixture",5,248,136,Key.UP,
                listOf(CharacterState("nezha",1,0,20,20,0,8,4,2,4)),inventory,mapOf("previous" to true),money=123)
            val result=WorldItems.openTreasure(before,treasure,item)
            assertEquals(name,row[4]=="1",result.applied)
            assertEquals(name,row[5].toInt(),result.inventory[item.id]?:0)
            assertEquals(name,row[6]=="1",result.flags[treasure.flagId]==true)
            if(result.applied){
                val after=before.copy(inventory=result.inventory,flags=result.flags)
                val repeated=WorldItems.openTreasure(after,treasure,item)
                assertFalse(name,repeated.applied);assertEquals(after.inventory,repeated.inventory);assertEquals(after.flags,repeated.flags)
            }else{assertEquals(before.inventory,result.inventory);assertEquals(before.flags,result.flags)}
            assertEquals(true,result.flags["previous"])
        }
    }
    @Test fun newOriginalScopesLoadOnlyTheirActualActorAndCategory(){
        val forest="game-data/provenance/world-five-dragon-chests.json"
        for((slot,category)in listOf(0 to 2,1 to 3,2 to 0)){
            assertTrue(WorldItems.categoryGrantEvidenceSupported(forest,99,"rom.npc.99.$slot",category))
            assertFalse(WorldItems.categoryGrantEvidenceSupported(forest,99,"rom.npc.99.$slot",(category+1)%4))
        }
        assertFalse(WorldItems.categoryGrantEvidenceSupported(forest,100,"rom.npc.99.0",2))
        assertFalse(WorldItems.categoryGrantEvidenceSupported(forest,99,"rom.npc.99.3",0))
        val hidden="game-data/provenance/world-village5-hidden.json"
        assertTrue(WorldItems.categoryGrantEvidenceSupported(hidden,5,"rom.npc.5.5",0))
        assertFalse(WorldItems.categoryGrantEvidenceSupported(hidden,5,"rom.npc.5.4",0))
        assertFalse(WorldItems.categoryGrantEvidenceSupported(hidden,5,"rom.npc.5.5",1))
        assertFalse(WorldItems.categoryGrantEvidenceSupported("unknown",5,"rom.npc.5.5",0))
    }
}
