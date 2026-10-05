package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class OriginalMapArrivalTest {
    private fun rule()=OriginalMapArrival(171,172,"rom.special.0",4,OriginalMapArrival.EVIDENCE)
    @Test fun all256OriginalHeaderChoicesRequireCurrentUsedSnowRowAndRealPartyCount(){
        val lines=javaClass.getResourceAsStream("/world-master172-arrival-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(256,lines.size)
        for(line in lines){val r=line.split('\t').map(String::toInt)
            val id="rom.special.${r[2]}";val inventory=mapOf(id to (r[3]and 127))
            val flags=mapOf("rom.inventory.special.${r[2]}.used" to (r[3]and 128!=0),"untouched" to true)
            assertEquals(line,r[5],rule().resolve(r[0],r[1],inventory,flags))
            assertEquals(mapOf(id to (r[3]and 127)),inventory)
            assertEquals(true,flags["untouched"])
        }
        assertEquals(171,rule().resolve(171,3,emptyMap(),mapOf("rom.inventory.special.0.used" to true)))
        assertEquals(171,rule().resolve(171,3,mapOf("rom.special.0" to 1),emptyMap()))
        assertEquals(171,rule().resolve(171,0,mapOf("rom.special.0" to 0),mapOf("rom.inventory.special.0.used" to true)))
        assertEquals(171,rule().copy(actualMapId=173).resolve(171,3,mapOf("rom.special.0" to 0),mapOf("rom.inventory.special.0.used" to true)))
    }
    @Test fun newEntryLoadsResolvedSceneOnceAndSavedExplicitMapIsPreserved(){
        fun room(id:Int)=Scene("fixture",3,3,IntArray(9),IntArray(9),(0..8).toSet(),1,1,id)
        val scenes=listOf(101,171,172).associateWith(::room)
        val exit=MapExit(101,1,0,171,1,1)
        val back=MapExit(172,1,2,101,1,0)
        val world=World(scenes,listOf(exit,back),101)
        val selected=mutableListOf<Int>();val prepared=mutableListOf<Int>()
        world.arrivalResolver={target->selected.add(target);rule().resolve(target,3,mapOf("rom.special.0" to 0),mapOf("rom.inventory.special.0.used" to true))}
        world.prepareTarget={id->prepared.add(id);true}
        world.tick(Key.UP);world.finishStep()
        assertEquals(172,world.mapId);assertEquals(listOf(171),selected);assertEquals(listOf(172),prepared)
        assertEquals(24,world.x);assertEquals(24,world.y)
        assertTrue(world.tryRestore(171,24,24));assertEquals(171,world.mapId)
        assertEquals(listOf(171),selected) // restore must not rerun an arrival choice
        assertTrue(world.tryRestore(172,24,24));world.tick(Key.DOWN);world.finishStep()
        assertEquals(101,world.mapId)
        val failed=World(scenes,listOf(exit),101);failed.arrivalResolver={172};failed.prepareTarget={false}
        failed.tick(Key.UP);failed.finishStep()
        assertEquals(101,failed.mapId);assertTrue(failed.message.contains("保留"))
    }
}
