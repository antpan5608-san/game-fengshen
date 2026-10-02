package org.fengshen.dev

/** Target ROM 9:A0D2..A129 and 0:BA30..BA7F, checked with isolated original runs. */
object OriginalStatus {
    const val POISON=2
    const val DEAD=32
    fun label(mask:Int)=when(mask){0->"正常";POISON->"中毒";DEAD->"死亡";else->"异常 %02X".format(mask)}
    fun enemySupported(enemy:EnemyDefinition)=enemy.behaviorByte==0 ||
        (enemy.id==137&&enemy.behaviorByte==3&&enemy.iceBaseDamage==8) ||
        enemy.behaviorByte==7 // Original shared AI dispatch is keyed by behavior, not enemy ID.
    fun choosesPoison(random:Int):Boolean {
        require(random in 0..255)
        return (random and 127)<41 && ((random ushr 1) and 63)<25
    }
    fun poison(hero:CharacterState):CharacterState =
        if(hero.statusMask in setOf(0,POISON,8,4))hero.copy(statusMask=POISON) else hero
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
