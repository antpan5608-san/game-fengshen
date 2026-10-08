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

/** Keep the existing advance/return clock, bounded by the arena and adjacent crop envelopes.
 * Only the active body's x changes. No rule, bitmap, cache or actor state is touched.
 */
fun battleVisualAttackBounds(bodies:List<Box>,actorIndex:Int,arena:Box,progress:Float):List<Box> {
    require(actorIndex in bodies.indices)
    val actor=bodies[actorIndex]
    var forward=(actor.x-arena.x).coerceAtLeast(0f)
    var backward=(arena.x+arena.w-actor.x-actor.w).coerceAtLeast(0f)
    for((i,other)in bodies.withIndex())if(i!=actorIndex&&
        other.y<actor.y+actor.h&&other.y+other.h>actor.y){
        if(other.x+other.w<=actor.x+.01f)forward=min(forward,(actor.x-other.x-other.w).coerceAtLeast(0f))
        if(other.x>=actor.x+actor.w-.01f)backward=min(backward,(other.x-actor.x-actor.w).coerceAtLeast(0f))
    }
    val shift=(battleAdvance(progress)*arena.w*.055f).coerceIn(-backward,forward)
    return bodies.mapIndexed{i,box->if(i==actorIndex)box.copy(x=box.x-shift) else box}
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
