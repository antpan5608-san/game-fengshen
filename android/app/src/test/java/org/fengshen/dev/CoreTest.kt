package org.fengshen.dev

import org.junit.Test
import org.junit.Assert.*

class CoreTest {
    private fun scene():Scene=Scene("test",4,3,IntArray(12),intArrayOf(1,1,1,1,1,0,0,1,1,0,0,1),setOf(5,6,9),1,1)
    @Test fun collisionIsSeparateFromDevelopmentLimit(){val s=scene();assertTrue(s.check(0,1)!!.startsWith("原版"));assertTrue(s.check(2,2)!!.startsWith("开发"));assertNull(s.check(2,1))}
    @Test fun targetLoadFailureDoesNotCommitTransitionOrRestore(){
        fun room(id:Int)=Scene("test",3,1,IntArray(3),IntArray(3),setOf(0,1,2),0,0,id)
        val world=World(mapOf(1 to room(1),2 to room(2)),listOf(MapExit(1,1,0,2,2,0)),1)
        var failed=0;world.prepareTarget={it!=2};world.transitionObserver={_,_,ok->if(!ok)failed++}
        repeat(8){world.tick(Key.RIGHT)}
        assertEquals(1,world.mapId);assertEquals(24,world.x);assertEquals(0,world.remaining)
        assertEquals(false,world.lastCompletedStep!!.transitioned);assertEquals(1,failed)
        assertFalse(world.tryRestore(2,40,8));assertEquals(1,world.mapId);assertEquals(24,world.x)
        world.prepareTarget={true};assertTrue(world.tryRestore(2,40,8));assertEquals(2,world.mapId)
    }
    @Test fun lazySceneHashFailureRemainsDiagnosableAndOriginIsKept(){
        val first=Scene("test",3,1,IntArray(3),IntArray(3),setOf(0,1,2),0,0,1)
        val maps=ResourceMap(listOf(1,2),2){if(it==1)first else error("Content checksum mismatch")}
        val world=World(maps,listOf(MapExit(1,1,0,2,0,0)),1)
        repeat(8){world.tick(Key.RIGHT)}
        assertEquals(1,world.mapId);assertEquals(24,world.x)
        assertEquals("Content checksum mismatch",world.transitionFailure!!.message)
    }
    @Test fun invalidSpawnAndMaskRejected(){try{scene().copy(enabled=setOf(0));fail()}catch(_:IllegalArgumentException){};try{scene().copy(grid=IntArray(2));fail()}catch(_:IllegalArgumentException){}}
    @Test fun coordinateAndCamera(){val w=World(scene());assertEquals(24,w.x);assertEquals(24,w.y);assertEquals(0f,w.camera().x,.001f);repeat(8){w.tick(Key.RIGHT)};assertEquals(40,w.x)}
    @Test fun releaseFinishesOneLegalTileWithoutStartingAnother(){val w=World(scene());w.tick(Key.RIGHT);repeat(100){w.tick(null)};assertEquals(40,w.x);assertEquals(0,w.remaining);w.tick(Key.RIGHT);assertEquals(40,w.x);assertTrue(w.message.startsWith("原版"))}
    @Test fun inputTracksMultiplePointersAndSlide(){val i=InputState();i.set(1,Key.UP);i.set(2,Key.A);assertEquals(Key.UP,i.direction());assertTrue(i.pressed(Key.A));i.set(1,Key.LEFT);assertFalse(i.pressed(Key.UP));i.release(2);assertFalse(i.pressed(Key.A));i.set(1,null);assertNull(i.direction())}
    @Test fun cancellationClearsAllPointers(){val i=InputState();i.set(1,Key.UP);i.set(2,Key.A);i.clear();assertNull(i.direction());assertFalse(i.pressed(Key.A))}
    @Test fun safeLayoutAndAspectAcrossPhoneSizes(){
        for((w,h)in listOf(2344 to 1080,1600 to 720,1280 to 800))for(mode in DisplayMode.entries){
            val l=layout(w,h,2.5f,SafeInsets(70,0,45,35),mode,ControlConfig())
            if(mode==DisplayMode.ORIGINAL)assertEquals(256f/240,l.game.w/l.game.h,.0001f)
            else {assertEquals(w.toFloat(),l.game.w,.001f);assertEquals(h.toFloat(),l.game.h,.001f)}
            for(b in listOf(l.stick)+l.buttons.values){assertTrue(b.x>=70);assertTrue(b.y>=0);assertTrue(b.x+b.w<=w-45+.01f);assertTrue(b.y+b.h<=h-35+.01f)}
            assertTrue(l.stick.x+l.stick.w<l.buttons.getValue(Key.A).x)
        }
    }
    @Test fun fullScreenCameraAndWorldToScreen(){
        val l=layout(2344,1080,3f,SafeInsets(130,60,130,0),DisplayMode.FULL,ControlConfig())
        assertEquals(0f,l.game.x,.001f);assertEquals(2344f,l.game.w,.001f)
        assertEquals(512f,l.viewWidth,.01f);assertTrue(l.viewHeight<480f)
        val cam=World(scene()).camera(l.viewWidth,l.viewHeight)
        val pos=l.worldToScreen(24f,24f,cam);assertEquals(l.game.x+(24f-cam.x)*l.scale,pos.first,.001f)
        val world=World(scene());world.restore(40,24)
        assertTrue(world.camera(32f,32f).x>=0f)
        assertEquals(0f,world.camera(512f,480f).y,.001f)
    }
    @Test fun joystickDeadZoneHysteresisAndMultiTouch(){
        val i=InputState();val b=Box(100f,100f,200f,200f)
        assertTrue(i.startStick(1));i.moveStick(1,205f,200f,b,.24f);assertNull(i.direction())
        i.moveStick(1,270f,180f,b,.24f);assertEquals(Key.RIGHT,i.direction())
        i.set(2,Key.A);assertTrue(i.pressed(Key.A));assertEquals(Key.RIGHT,i.direction())
        i.moveStick(1,245f,150f,b,.24f);assertEquals(Key.RIGHT,i.direction()) // near diagonal stays stable
        i.moveStick(1,205f,115f,b,.24f);assertEquals(Key.UP,i.direction())
        i.release(2);assertFalse(i.pressed(Key.A));i.release(1);assertNull(i.direction())
    }
    @Test fun clockIndependentOfRenderFrequency(){
        fun run(hz:Int):Int{val c=FixedClock();var count=0;for(i in 0..hz*10)c.advance(1_000_000_000L+i*10_000_000_000L/(hz*10)){count++};return count}
        assertEquals(run(60),run(90));assertEquals(run(60),run(120));assertTrue(run(60) in 599..600)
    }
    @Test fun clockResetDoesNotCatchUpBackground(){val c=FixedClock();var n=0;c.advance(100){n++};c.reset();c.advance(10_000_000_000){n++};assertEquals(0,n)}
    @Test fun partialMovementLifecycleCompletesLegalStep(){val w=World(scene());w.tick(Key.RIGHT);val restored=World(scene());restored.restore(w.x,w.y,w.remaining,w.direction);repeat(60){restored.tick(null)};assertEquals(40,restored.x);assertEquals(0,restored.remaining);assertNull(restored.scene.check(restored.x/16,restored.y/16))}
    @Test fun adjacentNpcSelectionNeverReachesDiagonalOrDistantCells(){
        val list=listOf(NpcCell("up",2,1),NpcCell("diagonal",3,3),NpcCell("far",2,0))
        assertEquals(listOf("up"),adjacentNpcs(2,2,list).map{it.id})
        assertEquals("up",interactionTarget(2,2,Key.DOWN,list)?.id)
        assertEquals(Key.UP,facingToward(2,2,list[0]))
        assertNull(facingToward(2,2,list[1]))
    }
    @Test fun multipleAdjacentNpcsRequireFacingOrDirectTap(){
        val list=listOf(NpcCell("up",2,1),NpcCell("right",3,2))
        assertEquals("up",interactionTarget(2,2,Key.UP,list)?.id)
        assertEquals("right",interactionTarget(2,2,Key.RIGHT,list)?.id)
        assertNull(interactionTarget(2,2,Key.DOWN,list))
    }
    @Test fun pendingStepTargetsItsLegalDestinationAndFacingDoesNotMove(){
        val w=World(scene());w.tick(Key.RIGHT)
        assertEquals(2 to 1,w.destinationCell());assertEquals(14,w.remaining)
        w.finishStep();w.face(Key.UP)
        assertEquals(40,w.x);assertEquals(24,w.y);assertEquals(Key.UP,w.direction)
    }
    @Test fun largeSecondMapCameraMathStaysBounded(){
        val s=Scene("map16-geometry",256,181,IntArray(256*181),IntArray(256*181),setOf(0),0,0)
        val l=layout(2344,1080,3f,SafeInsets(),DisplayMode.FULL,ControlConfig(),4096,2896)
        assertTrue(l.viewWidth<=512.001f);assertTrue(l.viewHeight<=240.001f);assertTrue(l.scale>4f)
        val camera=World(s).camera(l.viewWidth,l.viewHeight)
        assertTrue(camera.x>=0&&camera.y>=0);assertTrue(camera.x+camera.viewWidth<=4096.001f)
        assertTrue(camera.y+camera.viewHeight<=2896.001f)
    }
    @Test fun invalidExitDestinationPreservesCurrentMapAndPosition(){
        val from=Scene("test",2,1,IntArray(2),IntArray(2),setOf(0,1),0,0,1)
        val to=Scene("test",2,1,IntArray(2),intArrayOf(1,0),setOf(1),1,0,2)
        val w=World(mapOf(1 to from,2 to to),listOf(MapExit(1,1,0,2,0,0)),1)
        repeat(8){w.tick(Key.RIGHT)}
        assertEquals(1,w.mapId);assertEquals(24,w.x);assertEquals(8,w.y)
        assertTrue(w.message.contains("目标地图或落点不可用"))
    }
    @Test fun edgeExitRequiresItsDirectionAndCannotImmediatelyBounce(){
        val a=Scene("test",2,1,IntArray(2),IntArray(2),setOf(0,1),0,0,1)
        val b=Scene("test",2,1,IntArray(2),IntArray(2),setOf(0,1),1,0,2)
        val w=World(mapOf(1 to a,2 to b),listOf(MapExit(1,0,0,2,1,0,Key.LEFT)),1)
        w.tick(Key.UP);assertEquals(1,w.mapId)
        w.tick(Key.LEFT);assertEquals(2,w.mapId);assertEquals(24,w.x)
        w.tick(null);assertEquals(2,w.mapId)
    }
}
