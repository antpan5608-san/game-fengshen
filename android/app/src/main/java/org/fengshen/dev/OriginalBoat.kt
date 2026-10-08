package org.fengshen.dev

/** Original free ship DB, separate from the already supported scripted ferry DA.
 * The scene attaches this capability only when its source/data is enabled.
 * Snapshot flags retain the original parked water cell; no second inventory/save.
 */
data class BoatFailureCell(val x:Int,val y:Int,val mode:Int)
data class FreeBoatState(val paddleUsed:Boolean,val parked:Pair<Int,Int>?=null,val failure:BoatFailureCell?=null,
    val scriptedFootCell:Pair<Int,Int>?=null) {
    fun standing(collision:Int,mode:Int)=when(mode){
        219->collision in setOf(4,5,14,15,16,17)
        0->collision in setOf(25,26)
        else->false
    }
    fun step(source:Int,target:Int,key:Key,mode:Int,targetX:Int,targetY:Int):TerrainDecision? {
        if(failure!=null)return TerrainDecision(MovementBlock.DEVELOPMENT,mode) // Defeat-only resume, never a walkable shortcut.
        if(mode==0){
            if(parked==(targetX to targetY))
                return TerrainDecision(MovementBlock.NONE,219)
            return null // All other foot movement keeps the existing source/target edges.
        }
        if(mode!=219)return TerrainDecision(MovementBlock.DEVELOPMENT,mode)
        if(source !in 0..26||target !in 0..26)return TerrainDecision(MovementBlock.DEVELOPMENT,mode)
        if(target==25||target==26){
            val vertical=key==Key.UP||key==Key.DOWN
            return if(vertical==(target==25))TerrainDecision(MovementBlock.NONE,0)
                else TerrainDecision(MovementBlock.PHYSICAL,mode)
        }
        val allowed=target in setOf(4,15,16)||(paddleUsed&&target in setOf(5,14,17))
        return TerrainDecision(if(allowed)MovementBlock.NONE else MovementBlock.PHYSICAL,mode)
    }
}

object OriginalBoat {
    const val MODE=219
    const val EVIDENCE="game-data/provenance/world-west-free-boat.json"
    const val PARKED_FLAG="rom.transport.boat219.parked"
    const val FAILED_BOARD="rom.transport.boat219.failed-board"
    const val FAILED_RETURN="rom.transport.boat219.failed-return"
    private fun coordinate(prefix:String,flags:Map<String,Boolean>)=(0..7).sumOf{if(flags["$prefix.$it"]==true)1 shl it else 0}
    data class Result(val snapshot:SaveSnapshot,val applied:Boolean=false)
    fun contact(before:SaveSnapshot,key:Key):Boolean =
        before.interiorContext==null&&
        (before.mapId==10&&before.terrainMode==0&&before.x==9*16+8&&before.y==3*16+8&&key==Key.UP||
         before.mapId==16&&before.terrainMode==MODE&&before.x==68*16+8&&before.y==88*16+8&&key==Key.LEFT)
    /** Native boarding/return each pays one status step, then map load resets
     * the encounter counter. No extra random byte or inventory change. */
    fun transfer(before:SaveSnapshot,key:Key):Result {
        if(!contact(before,key)||OriginalStatus.allDisabled(before.characters))return Result(before)
        val outbound=before.mapId==10
        val party=OriginalStatus.step(before.characters,before.mapId)
        if(OriginalStatus.allDisabled(party)){
            // Original status failure stops the map-load script after its first
            // step. Keep that exact transient endpoint as a defeat-only save.
            val failureFlags=if(outbound)before.flags else before.flags+(PARKED_FLAG to false)
            val failed=failureFlags+((if(outbound)FAILED_BOARD else FAILED_RETURN) to true)+("runtime.field-defeat.pending" to true)
            return Result(before.copy(mapId=if(outbound)16 else 10,x=9*16+8,y=(if(outbound)2 else 3)*16+8,
                direction=key,terrainMode=if(outbound)MODE else 148,flags=failed,characters=party,
                encounterSteps=(before.encounterSteps+1)and 255),true)
        }
        val f=if(outbound)before.flags else before.flags+(PARKED_FLAG to false)
        return Result(before.copy(mapId=if(outbound)16 else 10,x=(if(outbound)68 else 9)*16+8,
            y=(if(outbound)88 else 3)*16+8,direction=key,terrainMode=if(outbound)MODE else 0,
            flags=f,characters=party,encounterSteps=0),true)
    }
    fun validFailure(before:SaveSnapshot):Boolean {
        val board=before.flags[FAILED_BOARD]==true;val back=before.flags[FAILED_RETURN]==true
        if(!board&&!back)return true
        if(board==back||!OriginalStatus.allDisabled(before.characters)||before.flags["runtime.field-defeat.pending"]!=true||before.interiorContext!=null)return false
        return before.x==9*16+8&&before.y==(if(board)2 else 3)*16+8&&before.mapId==(if(board)16 else 10)&&
            before.terrainMode==(if(board)MODE else 148)&&before.direction==(if(board)Key.UP else Key.LEFT)
    }
    fun validSnapshot(before:SaveSnapshot,scene:Scene?):Boolean {
        if(!validFailure(before))return false
        if(before.terrainMode==MODE&&(before.mapId!=16||scene?.freeBoat==null))return false
        val p=parked(before.flags)?:return true
        if(parkedDirection(before.flags)==null)return false
        if(scene==null||scene.mapId!=16)return false
        return p.first in 0 until scene.width&&p.second in 0 until scene.height&&
            scene.collision[p.second*scene.width+p.first] in setOf(4,5,14,15,16,17)
    }
    fun parked(flags:Map<String,Boolean>):Pair<Int,Int>? =
        if(flags[PARKED_FLAG]==true)coordinate("rom.transport.boat219.x",flags) to coordinate("rom.transport.boat219.y",flags) else null
    fun parkedDirection(flags:Map<String,Boolean>):Key?=listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT)
        .singleOrNull{flags["rom.transport.boat219.face.${it.name}"]==true}
    fun park(flags:Map<String,Boolean>,x:Int,y:Int,direction:Key=Key.DOWN):Map<String,Boolean> {
        require(x in 0..255&&y in 0..255&&direction in listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT))
        val result=flags.toMutableMap();result[PARKED_FLAG]=true
        for(i in 0..7){result["rom.transport.boat219.x.$i"]=x and (1 shl i)!=0;result["rom.transport.boat219.y.$i"]=y and (1 shl i)!=0}
        for(key in listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT))result["rom.transport.boat219.face.${key.name}"]=key==direction
        return result
    }
    fun sceneView(base:Scene,flags:Map<String,Boolean>,scriptedFootCell:Pair<Int,Int>?=null):Scene {
        if(base.mapId==10&&flags[FAILED_RETURN]==true)return base.copy().also{
            it.freeBoat=FreeBoatState(false,failure=BoatFailureCell(9,3,148))
        }
        if(base.mapId!=16)return base
        // Original foot dispatch admits both real port classes. This is a terrain
        // class capability, never a path-coordinate whitelist or water walking.
        val ports=base.collision.indices.filter{base.collision[it] in setOf(25,26)}.toSet()
        return base.copy(enabled=base.enabled+ports,transitionCells=base.transitionCells+ports).also{
            it.freeBoat=FreeBoatState(flags[OriginalSceneItems.SHIP_FLAG]==true,parked(flags),
                if(flags[FAILED_BOARD]==true)BoatFailureCell(9,2,MODE)else null,scriptedFootCell)
        }
    }
    internal fun parkingCell(step:CompletedStep):Pair<Int,Int>?=
        if(step.mapId==16&&!step.transitioned&&step.fromTerrainMode==MODE&&step.toTerrainMode==0)
            step.originX to step.originY else null
    fun flagsAfterStep(flags:Map<String,Boolean>,step:CompletedStep):Map<String,Boolean> =
        parkingCell(step)?.let{park(flags,it.first,it.second,step.direction)}?:flags
}
