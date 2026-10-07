package org.fengshen.dev

/** Field row0 only. Battle commands, cures and travel remain separate unverified capabilities. */
object OriginalFieldMagic {
    const val SPELL_ID="rom.magic.field.1.0"
    const val EVIDENCE="game-data/provenance/expected/original-magic/original-magic-report.json"
    const val COST=3
    const val NAME="提神术"
    data class Result(val characters:List<CharacterState>,val applied:Boolean,val error:String?=null)

    fun learned(hero:CharacterState,actorIndex:Int)=actorIndex==1&&hero.level in 1..80
    fun statusAllows(mask:Int)=mask in 0..255&&mask and 0xf0==0

    fun unavailable(characters:List<CharacterState>,indices:Map<String,Int>,casterId:String,
        targetId:String,spellId:String,inMapMenu:Boolean):String? {
        if(!inMapMenu)return "仅支持地图/菜单使用"
        if(spellId!=SPELL_ID)return "法术效果尚未开放"
        if(characters.size !in 2..4||characters.map{it.id}.distinct().size!=characters.size||
            characters.any{indices[it.id] !in 0..3}||
            characters.map{indices.getValue(it.id)}.distinct().size!=characters.size)return "队伍规则尚未核实"
        val caster=characters.firstOrNull{it.id==casterId}?:return "施法者不在队伍中"
        // Original field menu8FBD refuses the first slot, before its table0 alias.
        if(characters.first().id==casterId||!learned(caster,indices.getValue(casterId)))return "当前角色不能使用此法术"
        if(!statusAllows(caster.statusMask))return "施法者当前状态不能施法"
        if(caster.mp<COST)return "法力不够，无法使用"
        val target=characters.firstOrNull{it.id==targetId}?:return "请选择队内目标"
        if(!statusAllows(target.statusMask))return "目标当前状态不能接受治疗"
        return null
    }

    /** 2:91DA increments the search once without looping: offset0 if the first actor is1, otherwise1.
     * The level array is indexed by original actor identity, independently of party order.
     * Consequently a controlled Xiao-first roster reads Nezha's stored level.
     * Native normal N/X fixture5->58 and the independently executed CPU matrix are the oracle.
     */
    internal fun healedHp(characters:List<CharacterState>,indices:Map<String,Int>,target:Int,fieldIndex:Int):Int {
        require(fieldIndex in setOf(0,4)&&target in characters.indices)
        val formulaActor=if(indices.getValue(characters.first().id)==1)0 else 1
        val storedLevel=characters.first{indices.getValue(it.id)==formulaActor}.level-1
        val amount=if(fieldIndex==0)3*storedLevel+20 else 10*storedLevel+300
        return minOf(characters[target].maxHp,characters[target].hp+amount)
    }

    /** Side-effect-free proposal. The caller must save both HP and MP atomically before displaying success. */
    fun apply(characters:List<CharacterState>,indices:Map<String,Int>,casterId:String,
        targetId:String,spellId:String,inMapMenu:Boolean):Result {
        unavailable(characters,indices,casterId,targetId,spellId,inMapMenu)?.let{return Result(characters,false,it)}
        val target=characters.indexOfFirst{it.id==targetId}
        val hp=healedHp(characters,indices,target,0)
        val next=characters.mapIndexed{i,hero->hero.copy(
            hp=if(i==target)hp else hero.hp,mp=if(hero.id==casterId)hero.mp-COST else hero.mp)}
        return Result(next,true)
    }
}
