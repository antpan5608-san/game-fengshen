package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class Sages89Test {
    private val item=ItemDefinition("rom.special.1","丹藥",null,"fixture",category="special",originalId=1,maxCount=1)
    private fun rule()=OriginalNpcTalkDefinition(89,"rom.map.89.flag.1","",item.id,"rom.dialogue.99.0","rom.dialogue.99.1").also{it.actionId=1}
    private fun state(items:Map<String,Int>,flags:Map<String,Boolean>)=SaveSnapshot("fixture",89,136,104,Key.UP,
        emptyList(),items,flags,money=8812,encounterSteps=91)
    @Test fun all256NativeFirstRepeatCasesPreservePartyMoneyAndRepeatDoesNotRegrant(){
        val rows=javaClass.getResourceAsStream("/world-sages89-selector-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(256,rows.size)
        for(line in rows){val c=line.split('\t').map(String::toInt)
            val before=state(emptyMap(),mapOf(rule().mapFlagId to (c[0]and 1!=0),"other" to true))
            val r=OriginalNpcTalk.begin(before,rule(),item);assertTrue(r.applied)
            assertEquals("rom.dialogue.99.${c[1]}",r.nextDialogue);assertEquals(c[2]and 1!=0,r.snapshot.flags[rule().mapFlagId]==true)
            assertEquals(before.money,r.snapshot.money);assertEquals(before.encounterSteps,r.snapshot.encounterSteps)
            assertEquals(before.characters,r.snapshot.characters);assertEquals(true,r.snapshot.flags["other"])
            assertEquals(if(c[1]==0)1 else null,r.snapshot.inventory[item.id])
            val again=OriginalNpcTalk.begin(r.snapshot,rule(),item);assertTrue(again.applied);assertEquals(r.snapshot,again.snapshot)
            assertEquals("rom.dialogue.99.1",again.nextDialogue)
        }
    }
    @Test fun fourteenNativeGiftBoundariesRetainFullClaimAndReplaceOnlyKnownEmptySnowRow(){
        val rows=javaClass.getResourceAsStream("/world-sages89-gift-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(14,rows.size)
        for(line in rows){val c=line.split('\t');val msg=c[0].toInt();val name=c[1]
            val inventory=when(name){"empty"->emptyMap();"existing","used-positive"->mapOf(item.id to 1)
                "used-zero"->mapOf(item.id to 0);"used-snow"->mapOf("rom.special.0" to 0,"rom.special.11" to 1)
                "full"->(2..17).associate{"rom.special.$it" to 1};"one-free"->(2..16).associate{"rom.special.$it" to 1};else->error(name)}
            val flags=mapOf(rule().mapFlagId to (msg==1))+when(name){
                "used-positive","used-zero"->mapOf("rom.inventory.special.1.used" to true)
                "used-snow"->mapOf("rom.inventory.special.0.used" to true,"rom.inventory.special.11.used" to true)
                else->emptyMap()}
            val before=state(inventory,flags);val r=OriginalNpcTalk.begin(before,rule(),item);assertTrue(line,r.applied)
            assertEquals(c[2].toInt()and 127,r.snapshot.inventory[item.id]?:0)
            assertEquals(c[2].toInt()and 128!=0,r.snapshot.flags["rom.inventory.special.1.used"]==true)
            assertEquals(before.money,r.snapshot.money);assertEquals(before.characters,r.snapshot.characters)
            if(msg==1)assertEquals(before,r.snapshot)
            if(msg==0&&name=="used-snow"){
                assertFalse(r.snapshot.inventory.containsKey("rom.special.0"));assertFalse(r.snapshot.flags.containsKey("rom.inventory.special.0.used"))
                assertEquals(1,r.snapshot.inventory["rom.special.11"]);assertEquals(true,r.snapshot.flags["rom.inventory.special.11.used"])
                assertEquals(171,OriginalMapArrival(171,172,"rom.special.0",4,OriginalMapArrival.EVIDENCE).resolve(171,3,r.snapshot.inventory,r.snapshot.flags))
            }
            if(name=="full"&&msg==0){assertEquals(inventory,r.snapshot.inventory);assertEquals(true,r.snapshot.flags[rule().mapFlagId])}
        }
    }
    @Test fun actual128NativeTerrainRowsReuseSharedDirectionAndPlaneHandler(){
        val rows=javaClass.getResourceAsStream("/world-sages89-terrain-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(128,rows.size)
        for(line in rows){val c=line.split('\t').map(String::toInt);val direction=when(c[4]){1->Key.UP;2->Key.DOWN;3->Key.LEFT;4->Key.RIGHT;else->error(line)}
            val r=OriginalTerrain.step(3,c[2],c[3],direction,c[1])
            assertEquals(line,c[5]!=0,r.block!=MovementBlock.NONE);assertEquals(line,c[6],r.nextMode);assertFalse(r.suppressEncounter)
        }
    }
    @Test fun wrongSceneUnknownEmptySlotOrderAndWrongGiftDoNotMutate(){
        for(before in listOf(state(emptyMap(),emptyMap()).copy(mapId=172),
            state(mapOf("rom.special.0" to 0,"rom.special.14" to 0),mapOf("rom.inventory.special.0.used" to true,"rom.inventory.special.14.used" to true)),
            state(mapOf(item.id to 2),emptyMap()))){
            val r=OriginalNpcTalk.begin(before,rule(),item);assertFalse(r.applied);assertEquals(before,r.snapshot)
        }
        val before=state(emptyMap(),emptyMap());val r=OriginalNpcTalk.begin(before,rule(),item.copy(originalId=0));assertFalse(r.applied);assertEquals(before,r.snapshot)
    }
}
