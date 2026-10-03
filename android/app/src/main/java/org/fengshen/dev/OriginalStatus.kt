package org.fengshen.dev

/** Target ROM 9:A0D2..A129 and 0:BA30..BA7F, checked with isolated original runs. */
object OriginalStatus {
    const val POISON=2
    // Original behavior9 writes this exact state; its Chinese name is not yet verified.
    const val STATUS_BIT4=4
    const val DEAD=32
    fun label(mask:Int)=when(mask){0->"正常";POISON->"中毒";DEAD->"死亡";else->"异常 %02X".format(mask)}
    fun enemySupported(enemy:EnemyDefinition)=enemy.behaviorByte==0 ||
        (enemy.behaviorByte==3&&enemy.iceBaseDamage!=null&&enemy.iceBaseDamage in 0..65535) ||
        enemy.behaviorByte in setOf(7,9) // Original shared AI dispatch is keyed by behavior, not enemy ID.
    fun choosesPoison(random:Int):Boolean {
        require(random in 0..255)
        return (random and 127)<41 && ((random ushr 1) and 63)<25
    }
    fun poison(hero:CharacterState):CharacterState =
        if(hero.statusMask in setOf(0,POISON,8,4))hero.copy(statusMask=POISON) else hero
    /** 9:8DE9/A0D2; selection and the secondary hit gate share one byte. */
    fun choosesStatus4(random:Int):Boolean {
        require(random in 0..255)
        return (random and 127)<41 && ((random ushr 1) and 63)<25
    }
    fun applyStatus4(hero:CharacterState):CharacterState =
        if(hero.hp>0 && hero.statusMask in setOf(0,STATUS_BIT4))hero.copy(statusMask=STATUS_BIT4) else hero
    /** 9:AA08/AA82..AB52: physical and enemy ice share the nonzero status gate.
     * Pass computed damage BEFORE its minimum-one floor, not already-clamped damage.
     * This only computes the amount; the existing action applies HP/death once. */
    fun incomingDamage(computedDamage:Int,statusMask:Int):Int {
        require(computedDamage<=0xffff && statusMask in 0..255)
        return if(computedDamage<=0)1 else if(statusMask and STATUS_BIT4!=0)(computedDamage shl 1) and 0xffff else computedDamage
    }
    /** 9:AD7E..AD99: pass the already-computed successful physical damage, after
     * defense/minimum/multiplier/wrap. The original 8-bit byte-sum guard is not
     * equivalent to a minimum-one clamp. Spell damage bypasses this branch.
     * No RNG, HP mutation, or status recovery occurs here. */
    fun outgoingPhysicalDamage(computedDamage:Int,statusMask:Int):Int {
        require(computedDamage in 0..0xffff && statusMask in 0..255)
        val byteSum=((computedDamage and 255)+(computedDamage ushr 8)) and 255
        return if(statusMask and STATUS_BIT4!=0 && byteSum!=1)computedDamage ushr 1 else computedDamage
    }
    data class Status4Recovery(val character:CharacterState,val randomAfter:Int,val rotated:Boolean)
    /** Original single living character at 9:A69F completed-round boundary.
     * CMP #11 supplies carry1 to ROR; this proposal neither draws RNG nor mutates state.
     * No status8 capability, multi-character carry chain, or battle-exit cure is implied. */
    fun recoverStatus4AtRoundEnd(hero:CharacterState,random:Int):Status4Recovery {
        require(random in 0..255)
        if(hero.hp==0 || hero.statusMask and STATUS_BIT4==0)return Status4Recovery(hero,random,false)
        val nextRandom=(random ushr 1) or 128
        return Status4Recovery(if(nextRandom and 1==0)hero.copy(statusMask=0) else hero,nextRandom,true)
    }
    fun step(characters:List<CharacterState>):List<CharacterState> = characters.map{hero->
        if(hero.statusMask and POISON==0 || hero.hp==0)hero else {
            val hp=hero.hp-1;hero.copy(hp=hp,statusMask=if(hp==0)DEAD else hero.statusMask)
        }
    }
    fun allDisabled(characters:List<CharacterState>)=characters.isNotEmpty()&&characters.all{it.statusMask and 0x60!=0}
}

/** Map medicine #6: original confirm consumes once; curing bit 2 invokes consume again (9750..9777). */
object AntidoteUse {
    const val ID="rom.medicine.6"
    fun available(characters:List<CharacterState>,inventory:Map<String,Int>,targetId:String,
        item:ItemDefinition,inMapMenu:Boolean)=inMapMenu&&item.id==ID&&item.antidoteUse!=null&&
        (inventory[ID]?:0)>0&&characters.any{it.id==targetId}
    fun apply(characters:List<CharacterState>,inventory:Map<String,Int>,targetId:String,
        item:ItemDefinition,inMapMenu:Boolean):HerbUse.Result {
        if(!available(characters,inventory,targetId,item,inMapMenu))return HerbUse.Result(characters,inventory,false)
        val target=characters.indexOfFirst{it.id==targetId};val hero=characters[target]
        val cures=hero.statusMask and OriginalStatus.POISON!=0
        val count=inventory.getValue(ID);val used=minOf(count,if(cures)2 else 1)
        val items=inventory.toMutableMap();if(count==used)items.remove(ID) else items[ID]=count-used
        val next=characters.toMutableList();if(cures)next[target]=hero.copy(statusMask=hero.statusMask and 253)
        return HerbUse.Result(next,items,true)
    }
}

/** One facade submits the existing map commands; battle items retain their separate ordered rules. */
object MapItemUse {
    fun supported(item:ItemDefinition)=item.herbUse!=null||item.antidoteUse!=null
    fun available(characters:List<CharacterState>,inventory:Map<String,Int>,target:String,item:ItemDefinition,mapMenu:Boolean)=
        if(item.antidoteUse!=null)AntidoteUse.available(characters,inventory,target,item,mapMenu)
        else HerbUse.available(characters,inventory,target,item,mapMenu)
    fun apply(characters:List<CharacterState>,inventory:Map<String,Int>,target:String,item:ItemDefinition,mapMenu:Boolean)=
        if(item.antidoteUse!=null)AntidoteUse.apply(characters,inventory,target,item,mapMenu)
        else HerbUse.apply(characters,inventory,target,item,mapMenu)
}
