package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

class Input01Test {
    private fun scene(blocked:Set<Pair<Int,Int>> = emptySet(), enabled:Set<Int>?=null,
        objects:Set<Int> = emptySet()):Scene {
        val collision=IntArray(25)
        blocked.forEach{(x,y)->collision[y*5+x]=1}
        val open=enabled ?: (0 until 25).filter{collision[it]==0}.toSet()
        return Scene("input-01",5,5,IntArray(25),collision,open,2,2,
            dynamicObjectCells=objects)
    }
    private fun ticks(world:World,intent:MoveIntent):Int {
        var count=1
        world.tickIntent(intent)
        while(world.remaining>0){world.tickIntent(null);count++;assertTrue("step stalled",count<128)}
        return count
    }
    private fun intentFromStick(dx:Float,dy:Float):MoveIntent {
        val input=InputState();val box=Box(0f,0f,200f,200f)
        assertTrue(input.startStick(1));input.moveStick(1,100f+dx,100f+dy,box,MovementTuning.DEFAULT_DEAD_ZONE)
        return input.movementIntent()!!
    }
    @Test fun t1OpenGroundUsesExactlyOneAxisAtFullSpeed() {
        for(intent in listOf(MoveIntent(Key.RIGHT,.8f,-.6f),MoveIntent(Key.UP,.6f,-.8f))){
            val world=World(scene());var oldX=world.x;var oldY=world.y
            repeat(8){
                world.tickIntent(intent)
                assertFalse(world.x!=oldX && world.y!=oldY)
                oldX=world.x;oldY=world.y
            }
            assertEquals(0,world.remaining)
            if(intent.primary==Key.RIGHT){assertEquals(56,world.x);assertEquals(40,world.y)}
            else {assertEquals(40,world.x);assertEquals(24,world.y)}
        }
    }
    @Test fun t2StickDistanceDoesNotChangeNormalOrWallSpeed(){
        val short=intentFromStick(24f,-32f);val full=intentFromStick(60f,-80f)
        assertEquals(short.primary,full.primary)
        assertEquals(short.ux,full.ux,.0001f);assertEquals(short.uy,full.uy,.0001f)
        assertEquals(8,ticks(World(scene()),short))
        assertEquals(8,ticks(World(scene()),full))
        val wall=scene(blocked=setOf(2 to 1))
        assertEquals(13,ticks(World(wall),short))
        assertEquals(13,ticks(World(wall),full))
    }
    @Test fun t3EightDegreeHysteresisAndHorizontalTie(){
        fun at(degrees:Double,previous:Key?):Key? {
            val radians=Math.toRadians(degrees)
            return joystickDirection(cos(radians).toFloat(),-sin(radians).toFloat(),1f,.15f,previous)
        }
        assertEquals(Key.RIGHT,at(45.0,null))
        assertEquals(Key.RIGHT,at(50.0,Key.RIGHT))
        assertEquals(Key.UP,at(54.0,Key.RIGHT))
        assertEquals(Key.UP,at(40.0,Key.UP))
        assertEquals(Key.RIGHT,at(36.0,Key.UP))
        assertNull(joystickDirection(.1f,0f,1f,.15f,null))
    }
    @Test fun t4HorizontalWallKeepsHorizontalComponent(){
        val wall=scene(blocked=setOf(2 to 1))
        val diagonal=(1f/sqrt(2f))
        val w45=World(wall)
        assertEquals(11,ticks(w45,MoveIntent(Key.UP,diagonal,-diagonal)))
        assertEquals(56,w45.x);assertEquals(40,w45.y);assertEquals(Key.RIGHT,w45.direction)
        val w60=World(wall)
        assertEquals(13,ticks(w60,MoveIntent(Key.UP,.6f,-.8f)))
        assertEquals(56,w60.x);assertEquals(40,w60.y)
        val primaryHorizontal=World(wall)
        assertEquals(10,ticks(primaryHorizontal,MoveIntent(Key.RIGHT,.8f,-.6f)))
        assertEquals(56,primaryHorizontal.x);assertEquals(40,primaryHorizontal.y)
    }
    @Test fun t5VerticalWallKeepsVerticalComponent(){
        val wall=scene(blocked=setOf(3 to 2))
        val world=World(wall)
        assertEquals(11,ticks(world,MoveIntent(Key.RIGHT,1f/sqrt(2f),-1f/sqrt(2f))))
        assertEquals(40,world.x);assertEquals(24,world.y);assertEquals(Key.UP,world.direction)
        val component=World(wall)
        assertEquals(13,ticks(component,MoveIntent(Key.RIGHT,.8f,-.6f)))
        assertEquals(24,component.y)
        val primaryVertical=World(wall)
        assertEquals(10,ticks(primaryVertical,MoveIntent(Key.UP,.6f,-.8f)))
        assertEquals(40,primaryVertical.x);assertEquals(24,primaryVertical.y)
    }
    @Test fun t6PureWallCornerLowComponentAndDevelopmentBoundaryDoNotSlide(){
        val wall=World(scene(blocked=setOf(2 to 1)))
        wall.tickIntent(MoveIntent.cardinal(Key.UP));assertEquals(40,wall.x);assertEquals(40,wall.y)
        wall.tickIntent(MoveIntent(Key.UP,.14f,-.99015f));assertEquals(40,wall.x);assertEquals(40,wall.y)
        val corner=World(scene(blocked=setOf(2 to 1,3 to 2)))
        corner.tickIntent(MoveIntent(Key.UP,.6f,-.8f));assertEquals(40,corner.x);assertEquals(40,corner.y)
        val limited=World(scene(enabled=setOf(12,13)))
        limited.tickIntent(MoveIntent(Key.UP,.6f,-.8f));assertEquals(40,limited.x);assertEquals(40,limited.y)
        assertTrue(limited.message.startsWith("开发边界"))
    }
    @Test fun t7ReleaseAndModalCancellationOnlyFinishCurrentStep(){
        val input=InputState();val box=Box(0f,0f,200f,200f)
        input.startStick(1);input.moveStick(1,160f,20f,box,.15f)
        input.set(2,Key.A);assertTrue(input.pressed(Key.A));assertNotNull(input.movementIntent())
        val world=World(scene(blocked=setOf(2 to 1)))
        world.tickIntent(input.movementIntent());assertTrue(world.remaining>0)
        input.clear() // menu/dialogue/background uses this same cancellation path
        assertNull(input.movementIntent());assertFalse(input.pressed(Key.A))
        world.finishStep();val stopped=world.x
        repeat(100){world.tickIntent(input.movementIntent())}
        assertEquals(56,stopped);assertEquals(stopped,world.x);assertEquals(0,world.remaining)
    }
    @Test fun t8DynamicObstacleCannotBeCrossedMidStepAndExitWinsOverSliding(){
        val objects=mutableSetOf<Int>()
        val world=World(scene(objects=objects))
        repeat(3){world.tickIntent(MoveIntent.cardinal(Key.RIGHT))}
        assertTrue(world.x>40)
        objects.add(2*5+3)
        world.tickIntent(null)
        assertEquals(40,world.x);assertEquals(40,world.y);assertEquals(0,world.remaining)
        val a=Scene("exit",2,2,IntArray(4),IntArray(4),setOf(0,1,2,3),0,0,1)
        val b=Scene("exit",1,1,IntArray(1),IntArray(1),setOf(0),0,0,2)
        val exiting=World(mapOf(1 to a,2 to b),listOf(MapExit(1,0,0,2,0,0,Key.LEFT)),1)
        exiting.tickIntent(MoveIntent(Key.LEFT,-.8f,.6f))
        assertEquals(2,exiting.mapId)
    }
}
