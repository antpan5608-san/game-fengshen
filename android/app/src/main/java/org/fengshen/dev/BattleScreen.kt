package org.fengshen.dev

import kotlin.math.*

/** Preparation, advance, impact hold and return; reads only the presentation clock. */
fun battleAdvance(progress:Float):Float {
    require(progress.isFinite())
    val p=progress.coerceIn(0f,1f)
    return when{p<.18f->-.08f*p/.18f;p<.42f->-.08f+(p-.18f)/.24f*1.08f
        p<.65f->1f;else->(1f-p)/.35f}
}

/** Read-only UI projection. Never a second party, inventory, or settlement. */
data class BattlePartyView(val id:String,val name:String,val level:Int,val hp:Int,val maxHp:Int,
    val mp:Int,val maxMp:Int?,val status:Int,val active:Boolean)

fun battlePartyView(hero:CharacterState,name:String,action:BattleActionStep?,inputHeroId:String?,
    firstHeroId:String):BattlePartyView {
    val hp=action?.partyHp?.get(hero.id)
        ?:if(hero.id==firstHeroId)action?.heroHp?:hero.hp else hero.hp
    val status=action?.partyStatus?.get(hero.id)
        ?:if(hero.id==firstHeroId)action?.heroStatusMask?:hero.statusMask else hero.statusMask
    val mp=action?.partyMp?.get(hero.id)?:hero.mp
    return BattlePartyView(hero.id,name,hero.level,hp,hero.maxHp,mp,hero.maxMp,status,
        if(action==null)inputHeroId==hero.id else action.actorId==hero.id)
}

/** One scene geometry for painting and hit testing; legacy constructors remain intact. */
data class BattleSceneLayout(val touch:BattleTouchLayout,val prompt:Box,val enemyField:Box,
    val allyField:Box,val allySprites:List<Box>,val partyCards:List<Box>,val resultFooter:Box,
    val compact:Boolean)

fun battleSceneLayout(safe:Box,dp:Float,fontScale:Float,enemyCount:Int,partyCount:Int):BattleSceneLayout? {
    return battleSceneLayout(safe,dp,fontScale,enemyCount,partyCount,false)
}
fun battleSceneLayout(safe:Box,dp:Float,fontScale:Float,enemyCount:Int,partyCount:Int,illustrated:Boolean):BattleSceneLayout? {
    require(dp>0&&fontScale>0&&enemyCount in 1..8&&partyCount in 1..4)
    val gap=4*dp
    val commandH=max(48f,15*fontScale+16)*dp
    // Name/status on the first line; current HP/MP numbers and gauges on the
    // second. Full maxima and attributes remain in the separate detail layer.
    val cardH=max(if(illustrated)56f else 52f,26*fontScale+18)*dp
    val singleRow=illustrated&&safe.w>=800*dp&&fontScale<=1.3f
    val partyRows=if(partyCount>2&&!singleRow)2 else 1
    val cardColumns=if(singleRow)partyCount else if(partyCount==1)1 else 2
    val statusH=partyRows*cardH+(partyRows-1)*gap
    val promptH=max(28f,13*fontScale+8)*dp
    // Respect the actual inset-safe height, which is shorter than the screenshot.
    // Compress only whitespace; preserve two-column cards and system font sizes.
    val minimumArena=max(48f,if(partyCount>2)76f else 48f)*dp
    val pad=min((if(illustrated)4 else 8)*dp,max(2*dp,(safe.h-commandH-statusH-promptH-minimumArena)/5))
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
    val columns=if(illustrated&&enemyCount>3)enemyCount else max(1,ceil(enemyCount.toFloat()/maxRows).toInt())
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
    val sprites=if(illustrated)(0 until partyCount).map{i->
        // Separate crop envelopes along the same grounded diagonal. Cards and slots stay intact.
        val t=if(partyCount==1).5f else i.toFloat()/(partyCount-1)
        val side=allies.w*.04f;val bodyGap=min(8*dp,allies.w*.02f)
        val slotW=(allies.w-2*side-(partyCount-1)*bodyGap)/partyCount
        // Large-font two-row cards can leave a short arena. Use its vertical
        // whitespace without changing the slots, cards, targets or solo pose.
        val short=partyCount>1&&allies.h<96*dp
        val h=allies.h*(if(short).80f+.14f*t else .62f+.18f*t)
        val w=min(if(partyCount==1)allies.w*.48f else slotW,h*.76f)
        val x=if(partyCount==1)allies.x+allies.w*.27f
            else allies.x+side+i*(slotW+bodyGap)+(slotW-w)/2
        val foot=allies.y+allies.h*(if(short).84f+.15f*t else .70f+.27f*t)
        Box(x,foot-h,w,h)
    } else (0 until partyCount).map{i->Box(allies.x+(i%allyColumns)*(aw+gap),
        allies.y+(i/allyColumns)*(ah+gap),aw,ah)}
    val footer=Box(f.x,commands[0].y,f.w,commandH)
    val close=Box(f.x+f.w-max(80f,40*fontScale)*dp,f.y,max(80f,40*fontScale)*dp,commandH)
    val result=Box(f.x,f.y,f.w,footer.y-pad-f.y)
    val controls=BattleTouchLayout(f,arena,status,commands,enemies,commands.last(),close,result,13*dp*fontScale)
    return BattleSceneLayout(controls,prompt,enemyField,allies,sprites,cards,footer,rows==1&&enemyCount>3)
}

/** The compact row keeps a whole instance number above the native graphic. */
data class BattleEnemySceneLayout(val graphic:Box,val label:Box,val gauge:Box)
/** Read-only feedback measured at the exact font used by Canvas. */
data class BattleEnemyFeedback(val parts:BattleEnemySceneLayout,val text:String,
    val textWidth:Float,val textHeight:Float,val adapted:Boolean,
    val baselineGauge:Box,val compactGauge:Boolean)

/** Only the crowded row's gauge follows the actual sprite; labels and hit cells stay intact. */
fun battleCompactEnemyGauge(parts:BattleEnemySceneLayout,dp:Float,compact:Boolean,single:Boolean):BattleEnemySceneLayout {
    require(dp.isFinite()&&dp>0)
    if(!compact||single)return parts
    val sprite=parts.graphic;val gauge=parts.gauge
    require(listOf(sprite.x,sprite.w,gauge.x,gauge.w).all{it.isFinite()}&&sprite.w>0&&gauge.w>0)
    val width=min(gauge.w,max(24*dp,sprite.w+8*dp))
    val x=(sprite.x+sprite.w/2-width/2).coerceIn(gauge.x,gauge.x+(gauge.w-width))
    return parts.copy(gauge=gauge.copy(x=x,w=width))
}

/** Preserve the original sprite and hit cell; fit feedback below it or in the side whitespace. */
fun battleEnemyFeedbackLayout(cell:Box,sprite:Box,dp:Float,textWidth:Float,textHeight:Float):BattleEnemySceneLayout? {
    require(listOf(cell.x,cell.y,cell.w,cell.h,sprite.x,sprite.y,sprite.w,sprite.h,dp,textWidth,textHeight).all{it.isFinite()})
    require(dp>0&&textWidth>=0&&textHeight>0&&cell.w>0&&cell.h>0&&sprite.w>0&&sprite.h>0)
    require(sprite.x>=cell.x-.01f&&sprite.y>=cell.y-.01f&&
        sprite.x+sprite.w<=cell.x+cell.w+.01f&&sprite.y+sprite.h<=cell.y+cell.h+.01f)
    val labelH=ceil(textHeight)+2*dp;val gap=2*dp;val gaugeH=4*dp
    val blockH=labelH+gap+gaugeH;val desired=max(sprite.w+8*dp,textWidth+8*dp)
    var y=sprite.y+sprite.h+gap
    val width:Float;val x:Float
    if(y+blockH<=cell.y+cell.h-4*dp){
        width=min(desired,cell.w-8*dp)
        if(width<=0)return null
        x=(sprite.x+sprite.w/2-width/2).coerceIn(cell.x+4*dp,cell.x+cell.w-4*dp-width)
    }else{
        val right=cell.x+cell.w-4*dp-(sprite.x+sprite.w+8*dp)
        val left=sprite.x-8*dp-(cell.x+4*dp)
        val space=max(right,left)
        if(space<24*dp||blockH>cell.h-8*dp)return null
        width=min(desired,space)
        x=if(right>=left)sprite.x+sprite.w+8*dp else sprite.x-8*dp-width
        y=(sprite.y+sprite.h/2-blockH/2).coerceIn(cell.y+4*dp,cell.y+cell.h-4*dp-blockH)
    }
    return BattleEnemySceneLayout(sprite,Box(x,y,width,labelH),Box(x,y+labelH+gap,width,gaugeH))
}

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

/** Wide four-party medicine targets share one row, leaving the effect readable. */
data class BattleMedicineSceneLayout(val modal:TouchModalLayout,val targets:List<Box>)
fun battleMedicineSceneLayout(safe:Box,dp:Float,fontScale:Float,partyCount:Int):BattleMedicineSceneLayout? {
    require(dp>0&&fontScale>0&&partyCount in 1..4)
    val l=touchModalLayout(safe,dp,fontScale,0,0,false)
    if(partyCount<3||l.frame.w<800*dp)return null
    val pad=8*dp
    val header=max(l.close.h+2*pad,(15*fontScale*1.25f+16)*dp)
    val cardH=max(52f,26*fontScale+18)*dp
    val cardW=(l.frame.w-(partyCount+1)*pad)/partyCount
    val targets=(0 until partyCount).map{i->Box(l.frame.x+pad+i*(cardW+pad),l.frame.y+header,cardW,cardH)}
    val y=l.frame.y+header+cardH+pad
    val detailH=l.primary.y-pad-y
    // Target HP/max is visible in the cards; the two core effect/cost lines
    // must be visible together. Other conditions keep the existing scrolling.
    if(detailH<(14*fontScale*1.25f+4)*2*dp)return null
    return BattleMedicineSceneLayout(l.copy(list=l.list.copy(y=y,h=l.frame.y+l.frame.h-pad-y),
        detail=l.detail.copy(y=y,h=detailH)),targets)
}
