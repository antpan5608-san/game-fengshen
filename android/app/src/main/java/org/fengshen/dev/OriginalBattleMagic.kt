package org.fengshen.dev

/** Original 9:9E84/A026, 9435, A8AE and A752; scoped to Xiao's first two battle rows.
 * Selection, effect, debit and post-HP state are separate native phases.
 * Field magic has different predicates and addressing, and does not use this adapter.
 */
data class OriginalBattleSpell(val id:String,val name:String,val row:Int,val learnedLevel:Int,val cost:Int=3)

object OriginalBattleMagic {
    const val HEAL="rom.magic.battle.1.0"
    const val ANTIDOTE="rom.magic.battle.1.1"
    val spells=listOf(OriginalBattleSpell(HEAL,"提神术",0,1),OriginalBattleSpell(ANTIDOTE,"解毒术",1,10))
    fun learned(hero:CharacterState,originalActorIndex:Int)=
        if(originalActorIndex==1&&hero.level in 1..80)spells.filter{hero.level>=it.learnedLevel}else emptyList()

    /** Battle command availability/caster action status is owned by OriginalPartyRules. */
    fun selectionReason(caster:CharacterState,originalActorIndex:Int,spellId:String,target:CharacterState?):String? {
        val spell=learned(caster,originalActorIndex).firstOrNull{it.id==spellId}?:return "该战斗法术尚不可用"
        if(caster.mp !in 0..9999)return "MP状态不一致"
        if(caster.mp<spell.cost)return "MP不足"
        if(target==null)return "请选择队友"
        if(target.maxHp !in 1..9999||target.hp !in 0..target.maxHp||target.statusMask !in 0..255)return "目标状态不一致"
        // Initial dead/state64 targets can be selected in native battle; no fieldF0 gate here.
        return null
    }

    data class Effect(val target:CharacterState,val hpDelta:Int,val applied:Boolean)
    fun effect(caster:CharacterState,target:CharacterState,spell:OriginalBattleSpell):Effect {
        require(spell in spells&&caster.level in 1..80)
        return when(spell.id){
            HEAL->if(target.statusMask and OriginalStatus.DEAD!=0)Effect(target,0,false)else {
                // Battle fixed actor identity lookup, irrespective of party order.
                val hp=minOf(target.maxHp.toLong(),target.hp.toLong()+3L*(caster.level-1)+20).toInt()
                Effect(target.copy(hp=hp),hp-target.hp,true)
            }
            ANTIDOTE->if(target.statusMask==OriginalStatus.POISON)Effect(target.copy(statusMask=0),0,true)else Effect(target,0,false)
            else->error("Unsupported verified spell")
        }
    }

    /** Native late debit clamps underflow; target refusal/full HP do not refund. */
    fun debit(caster:CharacterState,spell:OriginalBattleSpell):CharacterState {
        require(spell in spells&&caster.mp in 0..9999)
        return caster.copy(mp=maxOf(0,caster.mp-spell.cost))
    }

    /** 9:A752. State1's native Chinese name is not promoted as verified. */
    fun afterHp(target:CharacterState):CharacterState {
        val mask=when{
            target.hp==0->OriginalStatus.DEAD
            target.statusMask>=16->target.statusMask
            target.hp<target.maxHp/4->if(target.statusMask==0)1 else target.statusMask
            target.statusMask==1->0
            else->target.statusMask
        }
        return if(mask==target.statusMask)target else target.copy(statusMask=mask)
    }
}
