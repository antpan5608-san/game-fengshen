package org.fengshen.dev

import kotlin.math.*

enum class Key { UP, DOWN, LEFT, RIGHT, A, B, START, MENU }
enum class DisplayMode { FULL, ORIGINAL, INTEGER }
object MovementTuning {
    const val DEFAULT_DEAD_ZONE = .15f
    const val DIRECTION_HOLD_DEGREES = 8.0
    const val MIN_WALL_COMPONENT = .15f
    val switchRatio = tan(Math.toRadians(45.0 + DIRECTION_HOLD_DEGREES)).toFloat()
}
data class MoveIntent(val primary:Key,val ux:Float,val uy:Float) {
    companion object {
        fun cardinal(key:Key)=when(key){
            Key.LEFT->MoveIntent(key,-1f,0f);Key.RIGHT->MoveIntent(key,1f,0f)
            Key.UP->MoveIntent(key,0f,-1f);Key.DOWN->MoveIntent(key,0f,1f)
            else->null
        }
    }
}

data class NpcCell(val id:String,val x:Int,val y:Int)
fun adjacentNpcs(playerX:Int,playerY:Int,npcs:List<NpcCell>)=
    npcs.filter { abs(it.x-playerX)+abs(it.y-playerY)==1 }
fun interactionTarget(playerX:Int,playerY:Int,facing:Key,npcs:List<NpcCell>):NpcCell? {
    val nearby=adjacentNpcs(playerX,playerY,npcs)
    val (dx,dy)=when(facing){Key.UP->0 to -1;Key.DOWN->0 to 1;Key.LEFT->-1 to 0;Key.RIGHT->1 to 0;else->0 to 0}
    return nearby.firstOrNull { it.x==playerX+dx && it.y==playerY+dy }
        ?: nearby.singleOrNull()
}
fun facingToward(playerX:Int,playerY:Int,npc:NpcCell):Key? = when {
    npc.x==playerX-1&&npc.y==playerY->Key.LEFT
    npc.x==playerX+1&&npc.y==playerY->Key.RIGHT
    npc.y==playerY-1&&npc.x==playerX->Key.UP
    npc.y==playerY+1&&npc.x==playerX->Key.DOWN
    else->null
}

fun joystickDirection(dx:Float,dy:Float,radius:Float,deadZone:Float,previous:Key?):Key? {
    val length=hypot(dx,dy)
    if(radius<=0f || length<=radius*deadZone.coerceIn(.05f,.8f))return null
    val ax=abs(dx/length);val ay=abs(dy/length)
    val horizontal=when {
        previous==Key.LEFT || previous==Key.RIGHT->ay<=ax*MovementTuning.switchRatio
        previous==Key.UP || previous==Key.DOWN->ax>ay*MovementTuning.switchRatio
        else->ax>=ay
    }
    return if(horizontal){if(dx<0)Key.LEFT else Key.RIGHT}else{if(dy<0)Key.UP else Key.DOWN}
}
class InputState {
    private val fingers = linkedMapOf<Int, Key>()
    private var stickId:Int?=null
    var stickDirection:Key?=null;private set
    var stickX=0f;private set
    var stickY=0f;private set
    fun set(id: Int, key: Key?) { if (key == null) fingers.remove(id) else fingers[id] = key }
    fun keyFor(id:Int)=fingers[id]
    fun startStick(id:Int):Boolean {if(stickId!=null)return false;stickId=id;fingers.remove(id);return true}
    fun ownsStick(id:Int)=stickId==id
    fun moveStick(id:Int,x:Float,y:Float,box:Box,deadZone:Float) {
        if(stickId!=id)return
        val radius=box.w/2;val dx=x-(box.x+radius);val dy=y-(box.y+radius)
        val length=hypot(dx,dy);val fraction=if(length>radius)radius/length else 1f
        stickX=dx*fraction;stickY=dy*fraction
        stickDirection=joystickDirection(dx,dy,radius,deadZone,stickDirection)
    }
    fun release(id: Int) {
        fingers.remove(id)
        if(stickId==id){stickId=null;stickDirection=null;stickX=0f;stickY=0f}
    }
    fun clear() {fingers.clear();stickId=null;stickDirection=null;stickX=0f;stickY=0f}
    fun pressed(key: Key) = fingers.containsValue(key)
    fun direction(): Key? = stickDirection ?: fingers.values.lastOrNull { it in listOf(Key.UP, Key.DOWN, Key.LEFT, Key.RIGHT) }
    fun movementIntent():MoveIntent? {
        val primary=stickDirection
        if(primary!=null){
            val length=hypot(stickX,stickY)
            if(length>0f)return MoveIntent(primary,stickX/length,stickY/length)
        }
        return direction()?.let(MoveIntent::cardinal)
    }
}
data class Box(val x: Float, val y: Float, val w: Float, val h: Float) {
    fun contains(px: Float, py: Float) = px >= x && py >= y && px < x+w && py < y+h
}
data class SafeInsets(val left: Int=0, val top: Int=0, val right: Int=0, val bottom: Int=0)
data class ControlConfig(val stickX:Float=.13f,val stickY:Float=.79f,val stickSize:Float=.9f,
    val mainX:Float=.87f,val mainY:Float=.78f,val mainSize:Float=.95f,
    val secondaryX:Float=.75f,val secondaryY:Float=.70f,val secondarySize:Float=.70f,
    val menuX:Float=.95f,val menuY:Float=.06f,val menuSize:Float=.65f,
    val opacity:Float=.56f,val deadZone:Float=MovementTuning.DEFAULT_DEAD_ZONE)
data class Camera(val x:Float,val y:Float,val viewWidth:Float,val viewHeight:Float)
data class ScreenLayout(val game:Box,val safe:Box,val stick:Box,val buttons:Map<Key,Box>,
    val scale:Float,val viewWidth:Float,val viewHeight:Float,val mode:DisplayMode) {
    fun hitButton(x:Float,y:Float)=buttons.entries.firstOrNull { it.value.contains(x,y) }?.key
    fun worldToScreen(x:Float,y:Float,camera:Camera)=Pair(game.x+(x-camera.x)*scale,game.y+(y-camera.y)*scale)
}
fun layout(w:Int,h:Int,density:Float,insets:SafeInsets,mode:DisplayMode,c:ControlConfig,
    worldWidth:Int=512,worldHeight:Int=480):ScreenLayout {
    val fw=max(1,w).toFloat();val fh=max(1,h).toFloat()
    val left=insets.left.coerceIn(0,w/3).toFloat();val right=insets.right.coerceIn(0,w/3).toFloat()
    val top=insets.top.coerceIn(0,h/3).toFloat();val bottom=insets.bottom.coerceIn(0,h/3).toFloat()
    val safe=Box(left,top,max(1f,fw-left-right),max(1f,fh-top-bottom))
    val scale=when(mode){
        DisplayMode.ORIGINAL->min(fw/256f,fh/240f).coerceAtLeast(.01f)
        // A large world must scroll; never shrink the entire second map into one phone frame.
        DisplayMode.FULL->max(fw/min(worldWidth,512),fh/min(worldHeight,240)).coerceAtLeast(.01f)
        DisplayMode.INTEGER->ceil(max(fw/min(worldWidth,512),fh/min(worldHeight,240))).coerceAtLeast(1f)
    }
    val game=if(mode==DisplayMode.ORIGINAL)Box((fw-256*scale)/2,(fh-240*scale)/2,256*scale,240*scale)
        else Box(0f,0f,fw,fh)
    fun control(x:Float,y:Float,size:Float):Box {
        val diameter=size.coerceAtLeast(24*density).coerceAtMost(min(safe.w,safe.h))
        val px=(safe.x+safe.w*x.coerceIn(0f,1f)-diameter/2).coerceIn(safe.x,safe.x+safe.w-diameter)
        val py=(safe.y+safe.h*y.coerceIn(0f,1f)-diameter/2).coerceIn(safe.y,safe.y+safe.h-diameter)
        return Box(px,py,diameter,diameter)
    }
    val stick=control(c.stickX,c.stickY,min(safe.h*.28f,safe.w*.17f)*c.stickSize)
    val main=min(safe.h*.19f,safe.w*.12f)*c.mainSize
    val buttons=mapOf(Key.A to control(c.mainX,c.mainY,main),
        Key.B to control(c.secondaryX,c.secondaryY,main*c.secondarySize),
        Key.MENU to control(c.menuX,c.menuY,min(safe.h*.07f,58*density)*c.menuSize))
    return ScreenLayout(game,safe,stick,buttons,scale,game.w/scale,game.h/scale,mode)
}
data class Scene(val version: String,val width: Int,val height: Int,val grid: IntArray,val collision: IntArray,
    val enabled: Set<Int>,val spawnX: Int,val spawnY: Int,val mapId:Int=114,
    val walkableClasses:Set<Int> = setOf(0),val dynamicObjectCells:Set<Int> = emptySet(),
    val transitionCells:Set<Int> = emptySet(),val sourceEdges:Map<Int,Set<Key>> = emptyMap(),
    val targetEdges:Map<Int,Set<Key>> = emptyMap()) {
    init {
        require(width in 1..256 && height in 1..256 && grid.size==width*height && collision.size==grid.size)
        require(grid.all { it in 0..255 } && collision.all { it in 0..255 })
        require(transitionCells.all {it in grid.indices})
        require(enabled.isNotEmpty() && enabled.all { it in grid.indices && (collision[it] in walkableClasses || it in transitionCells) })
        require(dynamicObjectCells.all {it in grid.indices})
        require(spawnX in 0 until width && spawnY in 0 until height && spawnY*width+spawnX in enabled)
    }
    fun blockType(x:Int,y:Int):MovementBlock {
        if(x !in 0 until width || y !in 0 until height)return MovementBlock.DEVELOPMENT
        val i=y*width+x
        if(collision[i] !in walkableClasses && i !in transitionCells)
            return if(collision[i] in setOf(1,3,4,5,7))MovementBlock.PHYSICAL else MovementBlock.DEVELOPMENT
        if(i in dynamicObjectCells)return MovementBlock.PHYSICAL
        if(i !in enabled)return MovementBlock.DEVELOPMENT
        return MovementBlock.NONE
    }
    fun probeFrom(x:Int,y:Int,key:Key):MovementBlock {
        val dx=if(key==Key.RIGHT)1 else if(key==Key.LEFT)-1 else 0
        val dy=if(key==Key.DOWN)1 else if(key==Key.UP)-1 else 0
        val nx=x+dx;val ny=y+dy
        val base=blockType(nx,ny)
        if(base!=MovementBlock.NONE)return base
        if(key in (sourceEdges[collision[y*width+x]]?:emptySet()) ||
            key in (targetEdges[collision[ny*width+nx]]?:emptySet()))return MovementBlock.PHYSICAL
        return MovementBlock.NONE
    }
    fun check(x: Int,y: Int): String? {
        if(x !in 0 until width || y !in 0 until height)return "开发边界 · 尚未开放"
        val i=y*width+x
        if(collision[i] !in walkableClasses && i !in transitionCells)return if(collision[i] in setOf(1,3,4,5,7))"原版碰撞 · 阻挡" else "开发边界 · 碰撞类别未开放"
        if(i in dynamicObjectCells)return "开发边界 · 动态对象未接入"
        if(i !in enabled)return "开发边界 · 尚未开放"
        return null
    }
}
enum class MovementBlock { NONE, PHYSICAL, DEVELOPMENT }
data class MapExit(val fromMapId:Int,val triggerX:Int,val triggerY:Int,val toMapId:Int,val spawnX:Int,val spawnY:Int,
    val edgeDirection:Key?=null,val arrivalDirection:Key=Key.DOWN)
data class CompletedStep(val mapId:Int,val x:Int,val y:Int,val transitioned:Boolean)
class World(private val scenes:Map<Int,Scene>,private val exits:List<MapExit>,private val initialMapId:Int) {
    var transitionObserver:((Int,Int,Boolean)->Unit)?=null
    constructor(scene:Scene):this(mapOf(scene.mapId to scene),emptyList(),scene.mapId)
    init {require(initialMapId in scenes && exits.all{it.fromMapId in scenes && it.toMapId in scenes})}
    var mapId=initialMapId;private set
    val scene get()=scenes.getValue(mapId)
    var x=scene.spawnX*16+8; private set
    var y=scene.spawnY*16+8; private set
    var direction=Key.DOWN; private set
    var message=""; private set
    var remaining=0; private set
    private var stepScale=1f
    private var movementCredit=0f
    private var stepOriginX=x
    private var stepOriginY=y
    var completedStepSeq=0L;private set
    var lastCompletedStep:CompletedStep?=null;private set
    fun destinationCell():Pair<Int,Int> {
        val dx=when(direction){Key.LEFT->-remaining;Key.RIGHT->remaining;else->0}
        val dy=when(direction){Key.UP->-remaining;Key.DOWN->remaining;else->0}
        return (x+dx)/16 to (y+dy)/16
    }
    fun face(key:Key){if(remaining==0 && key in listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT))direction=key}
    fun reset() {mapId=initialMapId;x=scene.spawnX*16+8;y=scene.spawnY*16+8;remaining=0;direction=Key.DOWN;stepScale=1f;movementCredit=0f;stepOriginX=x;stepOriginY=y;message="";lastCompletedStep=null }
    fun restore(px: Int,py: Int,pending: Int=0,facing: Key=Key.DOWN) {
        restore(mapId,px,py,pending,facing)
    }
    fun restore(targetMapId:Int,px:Int,py:Int,pending:Int=0,facing:Key=Key.DOWN){
        val target=scenes[targetMapId]?:return
        if(target.check(px/16,py/16)==null && pending in 0..16 && facing in listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT)){
            mapId=targetMapId;x=px;y=py;remaining=pending;direction=facing;stepScale=1f;movementCredit=0f;lastCompletedStep=null
            val moved=16-pending
            stepOriginX=px-when(facing){Key.LEFT->-moved;Key.RIGHT->moved;else->0}
            stepOriginY=py-when(facing){Key.UP->-moved;Key.DOWN->moved;else->0}
        }
    }
    private fun enter(exit:MapExit){
        val target=scenes[exit.toMapId]
        if(target==null || target.check(exit.spawnX,exit.spawnY)!=null){
            message="开发边界 · 目标地图或落点不可用"
            transitionObserver?.invoke(mapId,exit.toMapId,false)
            return
        }
        mapId=exit.toMapId;x=exit.spawnX*16+8;y=exit.spawnY*16+8;direction=exit.arrivalDirection;remaining=0;stepScale=1f;movementCredit=0f;stepOriginX=x;stepOriginY=y;message=""
    }
    private fun delta(key:Key)=when(key){Key.LEFT->-1 to 0;Key.RIGHT->1 to 0;Key.UP->0 to -1;Key.DOWN->0 to 1;else->0 to 0}
    private fun edgeExit(key:Key)=exits.firstOrNull{it.fromMapId==mapId && it.triggerX==x/16 &&
        it.triggerY==y/16 && it.edgeDirection==key}
    private fun probe(key:Key):MovementBlock {
        if(edgeExit(key)!=null)return MovementBlock.NONE
        val (dx,dy)=delta(key)
        return scene.probeFrom(x/16,y/16,key)
    }
    private fun secondary(intent:MoveIntent):Pair<Key,Float>? {
        val key=if(intent.primary==Key.LEFT || intent.primary==Key.RIGHT){
            if(intent.uy<0)Key.UP else Key.DOWN
        }else if(intent.primary==Key.UP || intent.primary==Key.DOWN){
            if(intent.ux<0)Key.LEFT else Key.RIGHT
        }else return null
        val component=if(key==Key.LEFT || key==Key.RIGHT)abs(intent.ux) else abs(intent.uy)
        return if(component>=MovementTuning.MIN_WALL_COMPONENT)key to component else null
    }
    fun tick(key: Key?)=tickIntent(key?.let(MoveIntent::cardinal))
    fun tickIntent(intent:MoveIntent?) {
        if(remaining==0) {
            if(intent==null)return
            val primary=intent.primary
            if(primary !in listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT))return
            val primaryBlock=probe(primary)
            val other=secondary(intent)
            val otherBlock=other?.let{probe(it.first)}
            val primaryComponent=if(primary==Key.LEFT || primary==Key.RIGHT)abs(intent.ux) else abs(intent.uy)
            val chosen=when {
                primaryBlock==MovementBlock.NONE && otherBlock==MovementBlock.PHYSICAL->primary to primaryComponent
                primaryBlock==MovementBlock.NONE->primary to 1f
                primaryBlock==MovementBlock.PHYSICAL && other!=null && otherBlock==MovementBlock.NONE->other
                else->{
                    val (dx,dy)=delta(primary)
                    message=scene.check(x/16+dx,y/16+dy) ?: if(primaryBlock==MovementBlock.PHYSICAL)"原版碰撞 · 阻挡" else "开发边界 · 入口不可用"
                    return
                }
            }
            direction=chosen.first
            val edge=edgeExit(direction)
            if(edge!=null){enter(edge);return}
            val (dx,dy)=delta(direction)
            val blocked=scene.check(x/16+dx,y/16+dy)
            if(blocked!=null){message=blocked;return}
            remaining=16;stepScale=chosen.second;movementCredit=0f;stepOriginX=x;stepOriginY=y;message=""
        }
        val (dx,dy)=delta(direction)
        val blocked=scene.check(stepOriginX/16+dx,stepOriginY/16+dy)
        if(blocked!=null){x=stepOriginX;y=stepOriginY;remaining=0;stepScale=1f;movementCredit=0f;message=blocked;return}
        movementCredit+=2f*stepScale
        var step=min(movementCredit.toInt(),remaining)
        movementCredit-=step
        // Integer world pixels and fixed 60 Hz ticks can only approximate the ideal
        // fractional duration. Finish on the nearest tick, rather than always late.
        if(remaining-step>0 && remaining-step-movementCredit<=stepScale){
            step=remaining;movementCredit=0f
        }
        when(direction){Key.UP->y-=step;Key.DOWN->y+=step;Key.LEFT->x-=step;Key.RIGHT->x+=step;else->Unit}
        remaining-=step
        if(remaining==0){
            stepScale=1f;movementCredit=0f;stepOriginX=x;stepOriginY=y
            val exit=exits.firstOrNull{it.fromMapId==mapId&&it.triggerX==x/16&&it.triggerY==y/16&&it.edgeDirection==null}
            completedStepSeq++
            lastCompletedStep=CompletedStep(mapId,x/16,y/16,exit!=null)
            if(exit!=null)enter(exit)
        }
    }
    fun finishStep(){while(remaining>0)tick(null)}
    fun camera(viewWidth:Float=256f,viewHeight:Float=240f):Camera {
        val vw=viewWidth.coerceAtLeast(1f);val vh=viewHeight.coerceAtLeast(1f)
        return Camera((x-vw*.5f).coerceIn(0f,max(0f,scene.width*16f-vw)),
            (y-vh*.46f).coerceIn(0f,max(0f,scene.height*16f-vh)),vw,vh)
    }
}
class FixedClock {
    private var last=0L;private var accumulated=0L
    fun reset(){last=0;accumulated=0}
    fun advance(now: Long,step: ()->Unit) {
        if(last==0L){last=now;return}
        accumulated+=(now-last).coerceIn(0L,100_000_000L);last=now
        while(accumulated>=16_666_667L){step();accumulated-=16_666_667L}
    }
}
