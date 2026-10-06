package org.fengshen.dev

import kotlin.math.*

/** Read-only UI projection. Never a second party, inventory, or settlement. */
data class BattlePartyView(val id:String,val name:String,val level:Int,val hp:Int,val maxHp:Int,
    val mp:Int,val maxMp:Int?,val status:Int,val active:Boolean)

fun battlePartyView(hero:CharacterState,name:String,action:BattleActionStep?,inputHeroId:String?,
    firstHeroId:String):BattlePartyView {
    val hp=action?.partyHp?.get(hero.id)
        ?:if(hero.id==firstHeroId)action?.heroHp?:hero.hp else hero.hp
    val status=action?.partyStatus?.get(hero.id)
        ?:if(hero.id==firstHeroId)action?.heroStatusMask?:hero.statusMask else hero.statusMask
    return BattlePartyView(hero.id,name,hero.level,hp,hero.maxHp,hero.mp,hero.maxMp,status,
        if(action==null)inputHeroId==hero.id else action.actorId==hero.id)
}

/** One scene geometry for painting and hit testing; legacy constructors remain intact. */
data class BattleSceneLayout(val touch:BattleTouchLayout,val prompt:Box,val enemyField:Box,
    val allyField:Box,val allySprites:List<Box>,val partyCards:List<Box>,val resultFooter:Box,
    val compact:Boolean)

fun battleSceneLayout(safe:Box,dp:Float,fontScale:Float,enemyCount:Int,partyCount:Int):BattleSceneLayout? {
    require(dp>0&&fontScale>0&&enemyCount in 1..8&&partyCount in 1..4)
    val gap=4*dp
    val commandH=max(48f,15*fontScale+16)*dp
    // Name/status on the first line; current HP/MP numbers and gauges on the
    // second. Full maxima and attributes remain in the separate detail layer.
    val cardH=max(52f,26*fontScale+18)*dp
    val partyRows=if(partyCount>2)2 else 1
    val cardColumns=if(partyCount==1)1 else 2
    val statusH=partyRows*cardH+(partyRows-1)*gap
    val promptH=max(28f,13*fontScale+8)*dp
    // Respect the actual inset-safe height, which is shorter than the screenshot.
    // Compress only whitespace; preserve two-column cards and system font sizes.
    val minimumArena=max(48f,if(partyCount>2)76f else 48f)*dp
    val pad=min(8*dp,max(2*dp,(safe.h-commandH-statusH-promptH-minimumArena)/5))
    val f=Box(safe.x+pad,safe.y+pad,safe.w-2*pad,safe.h-2*pad)
    if(f.w<560*dp||f.h<280*dp)return null
    val commands=(0..4).map{i->Box(f.x+i*(f.w+pad)/5,f.y+f.h-commandH,(f.w-4*pad)/5,commandH)}
    val status=Box(f.x,commands[0].y-pad-statusH,f.w,statusH)
    val cardW=(f.w-(cardColumns-1)*gap)/cardColumns
    val cards=(0 until partyCount).map{i->Box(f.x+(i%cardColumns)*(cardW+gap),
        status.y+(i/cardColumns)*(cardH+gap),cardW,cardH)}
    val prompt=Box(f.x,f.y,f.w,promptH)
    val arena=Box(f.x,prompt.y+prompt.h+pad,f.w,status.y-pad-prompt.y-prompt.h-pad)
    if(arena.h<minimumArena-.01f)return null
    val enemyW=(arena.w-pad)*.6f
    val enemyField=Box(arena.x,arena.y,enemyW,arena.h)
    val allies=Box(enemyField.x+enemyField.w+pad,arena.y,arena.w-enemyField.w-pad,arena.h)
    val maxRows=max(1,floor((arena.h+gap)/(48*dp+gap)).toInt())
    val columns=max(1,ceil(enemyCount.toFloat()/maxRows).toInt())
    val rows=ceil(enemyCount.toFloat()/columns).toInt()
    val cellW=(enemyField.w-(columns-1)*gap)/columns
    val cellH=(enemyField.h-(rows-1)*gap)/rows
    if(cellW<48*dp||cellH<48*dp)return null
    val enemies=(0 until enemyCount).map{i->Box(enemyField.x+(i%columns)*(cellW+gap),
        enemyField.y+(i/columns)*(cellH+gap),cellW,cellH)}
    val allyColumns=if(partyCount==1)1 else 2
    val allyRows=ceil(partyCount.toFloat()/allyColumns).toInt()
    val aw=(allies.w-(allyColumns-1)*gap)/allyColumns
    val ah=(allies.h-(allyRows-1)*gap)/allyRows
    val sprites=(0 until partyCount).map{i->Box(allies.x+(i%allyColumns)*(aw+gap),
        allies.y+(i/allyColumns)*(ah+gap),aw,ah)}
    val footer=Box(f.x,commands[0].y,f.w,commandH)
    val close=Box(f.x+f.w-max(80f,40*fontScale)*dp,f.y,max(80f,40*fontScale)*dp,commandH)
    val result=Box(f.x,f.y,f.w,footer.y-pad-f.y)
    val controls=BattleTouchLayout(f,arena,status,commands,enemies,commands.last(),close,result,13*dp*fontScale)
    return BattleSceneLayout(controls,prompt,enemyField,allies,sprites,cards,footer,rows==1&&enemyCount>3)
}

/** The compact row keeps a whole instance number above the native graphic. */
data class BattleEnemySceneLayout(val graphic:Box,val label:Box,val gauge:Box)
fun battleEnemySceneLayout(cell:Box,dp:Float,fontScale:Float,compact:Boolean,single:Boolean):BattleEnemySceneLayout {
    val labelH=max(26f,16*fontScale)*dp
    if(compact){
        val label=Box(cell.x+4*dp,cell.y+2*dp,cell.w-8*dp,labelH)
        val graphic=Box(cell.x+4*dp,label.y+label.h+2*dp,cell.w-8*dp,
            max(1f,cell.y+cell.h-10*dp-(label.y+label.h+2*dp)))
        return BattleEnemySceneLayout(graphic,label,Box(cell.x+4*dp,cell.y+cell.h-6*dp,cell.w-8*dp,4*dp))
    }
    val graphic=if(single)Box(cell.x+4*dp,cell.y+4*dp,cell.w-8*dp,max(1f,cell.h-38*dp))
        else Box(cell.x+4*dp,cell.y+4*dp,max(1f,min(cell.w*.35f,48*dp)),cell.h-8*dp)
    val x=if(single)cell.x+6*dp else cell.x+min(cell.w*.35f,48*dp)+10*dp
    val y=if(single)cell.y+cell.h-32*dp else cell.y+4*dp
    val width=max(1f,cell.x+cell.w-6*dp-x)
    return BattleEnemySceneLayout(graphic,Box(x,y,width,labelH),Box(x,cell.y+cell.h-8*dp,width,4*dp))
}
