package org.fengshen.dev

import kotlin.math.min

/** The existing Canvas crop fit, shared with the next verified key poses. */
fun battleVisualBodyBounds(field:Box,sourceWidth:Int,sourceHeight:Int):Box {
    require(sourceWidth>0&&sourceHeight>0&&field.w.isFinite()&&field.h.isFinite()&&
        field.x.isFinite()&&field.y.isFinite()&&field.w>0&&field.h>0)
    val scale=min(field.w/sourceWidth,field.h/sourceHeight)
    val w=sourceWidth*scale;val h=sourceHeight*scale
    return Box(field.x+(field.w-w)/2,field.y+field.h-h,w,h)
}

enum class BattleVisualPose { IDLE, ATTACK, CAST }

/** Only an already executed, identified spell may select a cast pose.
 * A herb HEAL, an enemy special, or an old unidentified frame stays idle.
 * No image loading, rule lookup, RNG or mutation belongs in this projection.
 */
fun battleVisualPose(action:BattleActionStep?,actorId:String):BattleVisualPose {
    if(action==null||action.actorSlot!=null||action.actorId!=actorId)return BattleVisualPose.IDLE
    return when {
        action.kind==BattleActionKind.ATTACK&&action.abilityId==null->BattleVisualPose.ATTACK
        action.abilityId in setOf(OriginalBattleMagic.HEAL,OriginalBattleMagic.ANTIDOTE)&&
            action.kind in setOf(BattleActionKind.SPECIAL,BattleActionKind.HEAL,BattleActionKind.STATUS)->BattleVisualPose.CAST
        else->BattleVisualPose.IDLE
    }
}

/** Project only identified original support steps onto their actual party target.
 * Unknown/legacy steps, enemy specials and item effects retain their existing feedback.
 * Failed effects may still show the original preparation/status feedback, never a new heal.
 */
fun battleVisualSupportTarget(action:BattleActionStep?,partyIds:Collection<String>):String? {
    if(action==null||action.actorSlot!=null||action.targetSlot!=null||
        action.actorId !in partyIds||action.targetId !in partyIds)return null
    if(action.abilityId !in setOf(OriginalBattleMagic.HEAL,OriginalBattleMagic.ANTIDOTE)||
        action.kind !in setOf(BattleActionKind.SPECIAL,BattleActionKind.HEAL,BattleActionKind.STATUS))return null
    return action.targetId
}
