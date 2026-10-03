package org.fengshen.dev

/** Thin adapter for original category1/item19 event29. The shared durable
 * continuation, state commit and save rollback own execution; draw owns none.
 */
data class OriginalYangJoinDefinition(val evidence:String) {
    val id="rom.event.110.29"
    val npcId="rom.npc.110.0"
    val pendingFlag="$id.dialogue.pending"
    val continuation=StoryContinuation(listOf("rom.dialogue.120.2","rom.dialogue.120.3"),null,null,
        setOf("rom.map.110.flag.128"))
    fun validPending(s:SaveSnapshot):Boolean {
        val joined=s.characters.count{it.id=="yangjian"}
        val context=s.flags[OriginalYangJoin.CONTEXT_FLAG]==true
        if(joined>1||context!=(joined==1))return false
        if(context&&(s.flags[OriginalYangJoin.USED_FLAG]!=true||(s.inventory[OriginalYangJoin.ITEM_ID]?:0)!=1))return false
        if(s.flags[pendingFlag]!=true)return true
        val stage=continuation.stage(id,s.flags)?:return false
        return s.mapId==110&&s.characters.map{it.id}==listOf("nezha","xiaolongnv","yangjian")&&
            s.flags[OriginalYangJoin.CONTEXT_FLAG]==true&&s.flags[OriginalYangJoin.USED_FLAG]==true&&
            (s.inventory[OriginalYangJoin.ITEM_ID]?:0)==1&&s.flags["rom.map.110.flag.128"]!=true&&
            continuation.dialogueIds.indices.drop(stage).all{s.flags[continuation.stageKey(id,it)]!=true}
    }
}
object OriginalYangJoin {
    const val ITEM_ID="rom.special.19"
    const val USED_FLAG="rom.inventory.special.19.used"
    const val CONTEXT_FLAG="rom.npccontext.110.207"
    const val EVIDENCE="game-data/provenance/world-yang-join.json"
    fun unavailable(s:SaveSnapshot,item:ItemDefinition,target:WorldObjectTarget,inMapMenu:Boolean):String? {
        val rule=item.worldUse?.yangJoin
        if(rule?.evidence!=EVIDENCE||item.worldUse?.targetSpriteId!=130||item.worldUse?.usedFlagId!=USED_FLAG||item.id!=ITEM_ID||item.category!="special"||item.originalId!=19||item.maxCount!=1||
            target!=WorldObjectTarget(rule.npcId,110,6,6,130,CONTEXT_FLAG,"rom.map.110.flag.128"))return "信物使用规则尚未核验"
        if(!inMapMenu)return "只能在地图菜单使用"
        val count=s.inventory[item.id]?:0
        if(count<=0)return "没有此物"
        if(count!=1)return "信物数量异常"
        if(s.flags[rule.pendingFlag]==true)return "当前对话尚未结束"
        if(s.flags[CONTEXT_FLAG]==true||s.flags["rom.map.110.flag.128"]==true||s.characters.any{it.id=="yangjian"})return "杨戬已入队"
        if(s.mapId!=110)return "当前场景不可用"
        if(!WorldItems.facesTarget(s,target))return "请站定并面向杨戬"
        // The current original route joins Xiao before reaching this continent.
        // The cartridge initializer writes slot2/party3 unconditionally; never
        // overwrite an unexpected existing actor to imitate that raw write.
        if(s.characters.map{it.id}!=listOf("nezha","xiaolongnv"))return "当前队伍与已核入队路线不一致"
        return null
    }
    fun begin(s:SaveSnapshot,item:ItemDefinition,target:WorldObjectTarget,template:CharacterState?,inMapMenu:Boolean):StoryFollowup.Result {
        unavailable(s,item,target,inMapMenu)?.let{return StoryFollowup.Result(s,null,false,it)}
        if(template?.id!="yangjian"||template.level!=24||template.experience!=26000||template.strength!=96||
            template.agility!=28||template.hp!=495||template.maxHp!=495||template.mp!=54||template.maxMp!=54||
            template.stamina!=60||template.spirit!=69||template.statusMask!=0||
            template.equipment!=EquipmentState(33,33,18,29))return StoryFollowup.Result(s,null,false,"杨戬入队数据尚未核验")
        val rule=item.worldUse!!.yangJoin!!
        val next=s.copy(characters=s.characters+template,flags=s.flags+mapOf(USED_FLAG to true,CONTEXT_FLAG to true,
            "rom.global.7c8.1" to true,"rom.map.110.flag.2" to true,rule.pendingFlag to true))
        return StoryFollowup.Result(next,rule.continuation.dialogueIds.first(),true)
    }
    /** Original initializer hardcodes58 despite item33 catalog70. Paired
     * operations remain disabled, so this exact initial occupancy is preserved.
     * No second persisted stat/cache is introduced. */
    fun initialHandContribution(s:SaveSnapshot,hero:CharacterState):Int? =
        if(hero.id=="yangjian"&&hero.equipment?.let{it.rightHand==33&&it.leftHand==33}==true&&
            s.flags[CONTEXT_FLAG]==true&&s.flags[USED_FLAG]==true)58 else null
}
