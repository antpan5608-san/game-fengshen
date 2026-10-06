package org.fengshen.dev

import kotlin.math.*

/** Derived display only: thresholds are the SAME cumulative rows used by settle(). */
data class ExperienceProgress(val status:Status,val level:Int,val cumulative:Int,val lower:Int?=null,
    val upper:Int?=null,val earned:Int?=null,val span:Int?=null,val remaining:Int?=null,val reason:String="") {
    enum class Status { PROGRESS, MISSING, INVALID, MAX }
    val fraction:Float? get()=if(status==Status.PROGRESS)earned!!.toFloat()/span!! else null
    val summary:String get()=when(status){Status.PROGRESS->"本级 $earned/$span · 距下级 $remaining"
        Status.MISSING->"下一等级数据未接入";Status.INVALID->"经验状态不一致：$reason";Status.MAX->"MAX"}
}
fun experienceProgress(hero:CharacterState,growth:List<GrowthRow>,ownerId:String="nezha",knownMaxLevel:Int?=null):ExperienceProgress {
    fun result(status:ExperienceProgress.Status,reason:String="")=ExperienceProgress(status,hero.level,hero.experience,reason=reason)
    if(hero.id!=ownerId)return result(ExperienceProgress.Status.MISSING,"当前角色成长数据未接入")
    if(hero.level<1||hero.experience<0)return result(ExperienceProgress.Status.INVALID,"等级或EXP非法")
    val rows=growth.sortedBy{it.level}
    if(rows.map{it.level}.distinct().size!=rows.size||rows.any{it.threshold<0}||rows.zipWithNext().any{it.first.threshold>=it.second.threshold})
        return result(ExperienceProgress.Status.INVALID,"成长门槛异常")
    val lower=if(hero.level==1)0 else rows.firstOrNull{it.level==hero.level}?.threshold
        ?:return result(ExperienceProgress.Status.MISSING,"当前等级门槛未接入")
    if(hero.experience<lower)return result(ExperienceProgress.Status.INVALID,"累计EXP低于本级门槛")
    if(knownMaxLevel!=null&&hero.level==knownMaxLevel)return result(ExperienceProgress.Status.MAX)
    val upper=rows.firstOrNull{it.level==hero.level+1}?.threshold
        ?:return result(ExperienceProgress.Status.MISSING)
    if(upper<=lower||hero.experience>=upper)return result(ExperienceProgress.Status.INVALID,"等级与累计EXP门槛不符")
    return ExperienceProgress(ExperienceProgress.Status.PROGRESS,hero.level,hero.experience,lower,upper,
        hero.experience-lower,upper-lower,upper-hero.experience)
}

/** Scoped Canvas geometry, shared by drawing and hit testing. No map button dependency. */
data class BattleTouchLayout(val frame:Box,val arena:Box,val status:Box,val commands:List<Box>,
    val enemies:List<Box>,val info:Box,val closeInfo:Box,val result:Box,val fontSize:Float)
fun battleTouchLayout(safe:Box,dp:Float,fontScale:Float,count:Int):BattleTouchLayout =
    battleTouchLayout(safe,dp,fontScale,count,1)
fun battleTouchLayout(safe:Box,dp:Float,fontScale:Float,count:Int,partyCount:Int):BattleTouchLayout {
    require(partyCount in 1..4)
    val pad=8*dp;val f=Box(safe.x+pad,safe.y+pad,safe.w-2*pad,safe.h-2*pad)
    val commandH=max(48f,18f*fontScale+20)*dp
    val statusH=max(64f,16f*(partyCount+1)*fontScale+18)*dp
    val commands=(0..4).map{Box(f.x+it*(f.w+pad)/5,f.y+f.h-commandH,(f.w-4*pad)/5,commandH)}
    val status=Box(f.x,commands.first().y-pad-statusH,f.w,statusH)
    val enemyH=max(48f,20f*fontScale+16)*dp
    val rows=max(1,ceil(count/4f).toInt());val perRow=min(4,max(1,count))
    val enemyTop=status.y-pad-rows*(enemyH+pad)
    val enemies=(0 until count).map{i->Box(f.x+(i%perRow)*(f.w+pad)/perRow,
        enemyTop+(i/perRow)*(enemyH+pad),(f.w-(perRow-1)*pad)/perRow,enemyH)}
    val arena=Box(f.x,f.y,f.w,max(1f,enemyTop-pad-f.y))
    val info=commands.last();val close=Box(f.x+f.w-max(80f,40*fontScale)*dp,f.y,max(80f,40*fontScale)*dp,commandH)
    return BattleTouchLayout(f,arena,status,commands,enemies,info,close,
        Box(f.x,f.y,f.w,status.y-f.y),13*dp*fontScale)
}

data class BattleTouchCommand(val battleId:String,val revision:Int,val kind:String,val slot:Int?=null,val itemId:String?=null,val targetId:String?=null)

/** Same target boxes for item drawing and input; first two retain their geometry. */
fun battlePartyTargetHeader(dp:Float,fontScale:Float,partyCount:Int):Float {
    require(partyCount in 1..4)
    if(partyCount==1)return 0f
    val rows=(partyCount+1)/2;val height=max(48f,12f*fontScale*2.5f+16f)*dp
    return rows*height+(rows-1)*8*dp
}
fun battlePartyTargetBoxes(frame:Box,listTop:Float,dp:Float,fontScale:Float,partyCount:Int):List<Box> {
    val header=battlePartyTargetHeader(dp,fontScale,partyCount)
    if(partyCount==1)return emptyList()
    val gap=8*dp;val h=max(48f,12f*fontScale*2.5f+16f)*dp;val w=(frame.w-3*gap)/2
    return (0 until partyCount).map{i->Box(frame.x+gap+(i%2)*(w+gap),
        listTop-header-gap+(i/2)*(h+gap),w,h)}
}

data class BattleTouchGesture(val pointer:Int,val x:Float,val y:Float,val command:BattleTouchCommand,var cancelled:Boolean=false,var lastY:Float=y)
