package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class SceneMechanismTest {
    private val flag="runtime.session.map95.mechanism0"
    private val changes=(17..19).flatMap{y->(12..14).map{x->SceneCellChange(x,y,if(y==17)112 else 113,102,1,0)}}
    private val rule=SceneMechanism("rom.mechanism.95.0",95,12,21,flag,changes)
    private fun scene():Scene {
        val grid=IntArray(32*30);val collision=IntArray(grid.size)
        for(c in changes){grid[c.y*32+c.x]=c.fromTile;collision[c.y*32+c.x]=1}
        return Scene("test",32,30,grid,collision,collision.indices.filter{collision[it]==0}.toSet(),12,21,95)
    }
    @Test fun triggerRequiresExactCellAtRestAndOneSessionOnly(){
        assertTrue(rule.triggered(95,12,21,true,emptyMap()))
        assertFalse(rule.triggered(95,12,21,false,emptyMap()))
        assertFalse(rule.triggered(94,12,21,true,emptyMap()))
        assertFalse(rule.triggered(95,12,20,true,emptyMap()))
        assertFalse(rule.triggered(95,13,21,true,emptyMap()))
        assertFalse(rule.triggered(95,12,21,true,mapOf(flag to true)))
    }
    @Test fun changesOnlyOriginalNineCellsWithoutMutatingCachedSource(){
        val base=scene();val updated=rule.apply(base,mapOf(flag to true))
        val indexes=changes.map{it.y*32+it.x}.toSet()
        for(i in base.grid.indices){
            if(i in indexes){
                assertEquals(102,updated.grid[i]);assertEquals(0,updated.collision[i]);assertTrue(i in updated.enabled)
                assertEquals(1,base.collision[i]);assertFalse(i in base.enabled)
                assertEquals(MovementBlock.NONE,updated.blockType(i%32,i/32))
            }else{assertEquals(base.grid[i],updated.grid[i]);assertEquals(base.collision[i],updated.collision[i])}
        }
        assertEquals(base.spawnX,updated.spawnX);assertEquals(base.spawnY,updated.spawnY)
        assertSame(base,rule.apply(base,emptyMap()))
        assertSame(base,rule.apply(base,mapOf("rom.map.95.flag.128" to true)))
    }
    @Test fun snapshotProposalPreservesEconomyAndOriginalStoryFlags(){
        val hero=CharacterState("nezha",1,0,10,10,0,1,1,1,1)
        val before=SaveSnapshot("test",95,12*16+8,21*16+8,Key.DOWN,listOf(hero),mapOf("rom.special.11" to 1),mapOf("original" to true),20)
        val after=before.copy(flags=before.flags+(flag to true))
        val restored=after.copy(flags=after.flags.toMap()) // JSON is exercised in Android ContentTest.
        assertEquals(after,restored)
        assertEquals(before,restored.copy(flags=before.flags))
        assertFalse(rule.triggered(restored.mapId,restored.x/16,restored.y/16,true,restored.flags))
        assertEquals(102,rule.apply(scene(),restored.flags).grid[17*32+12])
        assertEquals(setOf("original",flag),restored.flags.keys)
    }
    @Test fun rejectsDifferentMapGeometryAndDuplicateCells(){
        val changed=scene().let{it.copy(grid=it.grid.copyOf().also{g->g[17*32+12]=99})}
        try{rule.apply(changed,mapOf(flag to true));fail("Wrong original cells must reject")}catch(_:IllegalArgumentException){}
        try{rule.copy(changes=changes+changes.first());fail("Duplicate cells must reject")}catch(_:IllegalArgumentException){}
        try{rule.copy(sessionFlag="rom.map.95.flag.128");fail("Session state is not original story flag")}catch(_:IllegalArgumentException){}
    }
}
