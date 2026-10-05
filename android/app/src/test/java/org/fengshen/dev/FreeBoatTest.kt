package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class FreeBoatTest {
    private val keys=listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT)
    @Test fun boatWaterAndPortsMatchOriginalCpu() {
        val water=javaClass.getResourceAsStream("/world-west-boat-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(5400,water.size)
        for(row in water){val v=row.split('\t').map{it.toInt()};val d=FreeBoatState(v[0]!=0).step(v[1],v[2],keys[v[3]-1],219,10,10)!!
            assertEquals(row,v[4]!=0,d.block!=MovementBlock.NONE);assertEquals(row,v[5],d.nextMode)
            assertFalse(d.suppressEncounter)
        }
        val ports=javaClass.getResourceAsStream("/world-west-boat-ports-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(112,ports.size)
        for(row in ports){val v=row.split('\t').map{it.toInt()}
            if(v[0]==219){val d=FreeBoatState(false).step(v[1],v[2],keys[v[3]-1],219,10,10)!!
                assertEquals(row,v[4]!=0,d.block!=MovementBlock.NONE);assertEquals(row,v[5],d.nextMode)
            }else assertNull(FreeBoatState(false).step(v[1],v[2],keys[v[3]-1],0,10,10))
        }
    }
    private fun scene()=Scene("boat-test",5,5,IntArray(25),IntArray(25){4}.also{it[2*5+2]=25;it[3*5+2]=0},
        setOf(3*5+2),2,3,16)
    @Test fun landingAndReboardingUseNormalCompletedStepsWithoutFreePoisonOrWaterWalking() {
        val base=scene();var flags=emptyMap<String,Boolean>()
        val world=World(mapOf(16 to base),emptyList(),16)
        world.sceneResolver={OriginalBoat.sceneView(base,flags)}
        assertTrue(world.tryRestore(16,2*16+8,16+8,0,Key.DOWN,null,219))
        world.tick(Key.DOWN);world.finishStep()
        assertEquals(0,world.terrainMode);assertEquals(1L,world.completedStepSeq)
        val step=world.lastCompletedStep!!
        assertEquals(219,step.fromTerrainMode);assertEquals(0,step.toTerrainMode)
        flags=OriginalBoat.flagsAfterStep(flags,step)
        assertEquals(2 to 1,OriginalBoat.parked(flags))
        assertEquals(Key.DOWN,OriginalBoat.parkedDirection(flags))
        assertFalse(step.suppressEncounter)
        val poisoned=CharacterState("nezha",1,0,100,100,0,0,10,10,10,10,statusMask=2)
        assertEquals(99,OriginalStatus.step(listOf(poisoned),step.mapId).single().hp)
        assertNotNull(world.scene.check(1,2,0)) // Other water stays blocked on foot.
        world.tick(Key.UP);world.finishStep()
        assertEquals(219,world.terrainMode);assertEquals(2L,world.completedStepSeq)
        assertEquals(2 to 1,OriginalBoat.parked(flags)) // Original retained parked coordinate witness.
    }
    @Test fun parkedCoordinatesRoundTripAndUnrelatedFlagsRemainUnchanged() {
        val original=mapOf("rom.map.42.flag.128" to true,OriginalSceneItems.SHIP_FLAG to true)
        for(x in listOf(0,24,35,255))for(y in listOf(0,44,71,255)){
            val f=OriginalBoat.park(original,x,y);assertEquals(x to y,OriginalBoat.parked(f))
            assertTrue(f.getValue("rom.map.42.flag.128"));assertTrue(f.getValue(OriginalSceneItems.SHIP_FLAG))
        }
        assertNull(OriginalBoat.parked(emptyMap()))
    }
    @Test fun fubingTransfersPayStatusOnceResetEncounterAndPreserveAllOtherPlayerState() {
        val hero=CharacterState("nezha",1,7,100,100,0,10,10,10,10,0,statusMask=2)
        val before=SaveSnapshot("boat-test",10,9*16+8,3*16+8,Key.DOWN,listOf(hero,hero.copy(id="dragon",statusMask=0)),mapOf("rom.special.14" to 0),
            mapOf("kept" to true),1234,27)
        val r=OriginalBoat.transfer(before,Key.UP)
        assertTrue(r.applied);assertEquals(16,r.snapshot.mapId);assertEquals(219,r.snapshot.terrainMode)
        assertEquals(68*16+8,r.snapshot.x);assertEquals(88*16+8,r.snapshot.y)
        assertEquals(0,r.snapshot.encounterSteps);assertEquals(99,r.snapshot.characters.first().hp)
        assertEquals(before.inventory,r.snapshot.inventory);assertEquals(before.money,r.snapshot.money);assertEquals(before.flags,r.snapshot.flags)
        assertFalse(OriginalBoat.transfer(r.snapshot,Key.UP).applied)
        val back=OriginalBoat.transfer(r.snapshot,Key.LEFT)
        assertTrue(back.applied);assertEquals(10,back.snapshot.mapId);assertEquals(0,back.snapshot.terrainMode)
        assertEquals(before.x,back.snapshot.x);assertEquals(before.y,back.snapshot.y)
        assertEquals(98,back.snapshot.characters.first().hp);assertEquals(0,back.snapshot.encounterSteps)
        assertEquals(before.money,back.snapshot.money);assertEquals(before.inventory,back.snapshot.inventory)
        assertFalse(OriginalBoat.transfer(back.snapshot,Key.LEFT).applied)
        val dying=before.copy(characters=listOf(hero.copy(hp=1)))
        val dead=OriginalBoat.transfer(dying,Key.UP)
        assertEquals(16,dead.snapshot.mapId);assertEquals(219,dead.snapshot.terrainMode)
        assertEquals(9*16+8,dead.snapshot.x);assertEquals(2*16+8,dead.snapshot.y)
        assertEquals(28,dead.snapshot.encounterSteps);assertTrue(OriginalBoat.validFailure(dead.snapshot))
        assertEquals(0,dead.snapshot.characters.single().hp);assertEquals(32,dead.snapshot.characters.single().statusMask)
        assertFalse(OriginalBoat.transfer(dead.snapshot,Key.LEFT).applied)
        assertFalse(OriginalBoat.validFailure(dead.snapshot.copy(characters=listOf(hero))))
        assertFalse(OriginalBoat.validFailure(dead.snapshot.copy(x=68*16+8)))
    }
    @Test fun boatDoesNotBypassUnknownEncounterRegionWallsOrOtherActors() {
        val base=scene().copy(unavailableRegions=listOf(EncounterRect(0,0,1,1)))
        val view=OriginalBoat.sceneView(base,emptyMap())
        assertEquals(MovementBlock.DEVELOPMENT,view.blockType(1,1,219))
        val occupied=OriginalBoat.sceneView(scene().copy(dynamicObjectCells=setOf(1)),emptyMap())
        assertEquals(MovementBlock.PHYSICAL,occupied.blockType(1,0,219))
        assertEquals(MovementBlock.DEVELOPMENT,scene().blockType(1,0,219)) // Unenabled data has no boat capability.
        assertEquals(MovementBlock.PHYSICAL,FreeBoatState(false).step(4,5,Key.UP,219,0,0)!!.block)
        assertEquals(MovementBlock.NONE,FreeBoatState(true).step(4,5,Key.UP,219,0,0)!!.block)
        assertEquals(MovementBlock.DEVELOPMENT,FreeBoatState(true).step(4,27,Key.UP,219,0,0)!!.block)
        val waterExit=scene().copy(enabled=scene().enabled+1,transitionCells=setOf(1))
        assertEquals(MovementBlock.PHYSICAL,OriginalBoat.sceneView(waterExit,emptyMap()).blockType(1,0,0))
        assertEquals(MovementBlock.NONE,OriginalBoat.sceneView(waterExit,emptyMap(),1 to 0).blockType(1,0,0))
        assertEquals(MovementBlock.PHYSICAL,OriginalBoat.sceneView(waterExit,emptyMap(),1 to 0).blockType(0,0,0))
    }
    @Test fun independentLotusExitModesDoNotParkOrResetThePlayerPosition() {
        val sea=scene().copy(collision=scene().collision.copyOf().also{it[12]=4})
        val room=Scene("boat-test",3,3,IntArray(9),IntArray(9), (0..8).toSet(),1,2,136)
        val inward=MapExit(16,2,2,136,1,2).also{it.arrivalTerrainMode=0}
        val outward=MapExit(136,1,2,16,2,2).also{it.arrivalTerrainMode=219}
        val world=World(mapOf(16 to sea,136 to room),listOf(inward,outward),16)
        world.sceneResolver={id->if(id==16)OriginalBoat.sceneView(sea,emptyMap())else room}
        assertTrue(world.tryRestore(16,2*16+8,16+8,0,Key.DOWN,null,219))
        world.tick(Key.DOWN);world.finishStep();assertEquals(136,world.mapId);assertEquals(0,world.terrainMode)
        assertTrue(world.lastCompletedStep!!.transitioned)
        assertEquals(emptyMap<String,Boolean>(),OriginalBoat.flagsAfterStep(emptyMap(),world.lastCompletedStep!!))
        world.tick(Key.UP);world.finishStep();world.tick(Key.DOWN);world.finishStep()
        assertEquals(16,world.mapId);assertEquals(2*16+8,world.x);assertEquals(2*16+8,world.y);assertEquals(219,world.terrainMode)
    }
    @Test fun originalIncompleteAllDeadEndpointIsDefeatOnlyNeverWalkableTerrain() {
        val base=Scene("boat-test",16,16,IntArray(256),IntArray(256), (0..255).toSet(),1,1,16)
        val flags=mapOf(OriginalBoat.FAILED_BOARD to true,"runtime.field-defeat.pending" to true)
        val world=World(OriginalBoat.sceneView(base,flags))
        assertTrue(world.tryRestore(16,9*16+8,2*16+8,0,Key.UP,null,219))
        world.tick(Key.UP);world.finishStep();assertEquals(9*16+8,world.x);assertEquals(2*16+8,world.y)
        assertEquals(0L,world.completedStepSeq)
        assertEquals(MovementBlock.DEVELOPMENT,world.scene.blockType(9,3,219))
        assertEquals(MovementBlock.DEVELOPMENT,world.scene.blockType(9,2,0))
        val room=base.copy(mapId=10)
        val back=OriginalBoat.sceneView(room,mapOf(OriginalBoat.FAILED_RETURN to true))
        assertNull(back.check(9,3,148));assertNotNull(back.check(9,2,148))
    }
}
