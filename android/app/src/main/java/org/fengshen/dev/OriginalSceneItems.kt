package org.fengshen.dev

/** Scoped item0/event9 and item14/event23. Reuses the durable dialogue queue;
 * the original effects happen before its first message, completion afterwards.
 * No new party, teleport, money, movement cost or healing rule is inferred. */
data class OriginalSceneItemDefinition(val evidence:String,val originalItemId:Int) {
    val mapId get()=if(originalItemId==0)37 else 42
    val eventId get()=if(originalItemId==0)9 else 23
    val id get()="rom.event.$mapId.$eventId"
    val npcId get()=if(originalItemId==0)"rom.npc.37.yang-bed" else "rom.npc.42.0"
    val target get()=if(originalItemId==0)WorldObjectTarget(npcId,37,3,5,130,
        "rom.map.37.flag.128","rom.map.37.flag.128") else WorldObjectTarget(npcId,42,7,5,162,
        "rom.map.42.flag.128","rom.map.42.flag.128")
    val pendingFlag get()="$id.dialogue.pending"
    val continuation get()=StoryContinuation(if(originalItemId==0)listOf("rom.dialogue.47.4")
        else listOf("rom.dialogue.52.6","rom.dialogue.52.7"),null,null,setOf("rom.map.$mapId.flag.128"))
    fun verified()=originalItemId in setOf(0,14)&&evidence==OriginalSceneItems.EVIDENCE
    fun validPending(s:SaveSnapshot):Boolean {
        if(!verified())return false
        if(s.flags[pendingFlag]!=true)return true
        val stage=continuation.stage(id,s.flags)?:return false
        return s.mapId==mapId&&WorldItems.facesTarget(s,target)&&s.inventory.containsKey("rom.special.$originalItemId")&&
            s.inventory["rom.special.$originalItemId"]==0&&
            s.flags["rom.inventory.special.$originalItemId.used"]==true&&
            (if(originalItemId==0)s.characters.any{it.id=="yangjian"&&it.statusMask==0}&&
                s.flags["rom.npccontext.37.196"]==true else s.flags[OriginalSceneItems.SHIP_FLAG]==true)&&
            continuation.dialogueIds.indices.drop(stage).none{s.flags[continuation.stageKey(id,it)]==true}
    }
}

object OriginalSceneItems {
    const val EVIDENCE="game-data/provenance/world-west-scene-items.json"
    const val SHIP_FLAG="rom.global.6812.nonzero"
    fun unavailable(s:SaveSnapshot,item:ItemDefinition,target:WorldObjectTarget,inMapMenu:Boolean):String? {
        val r=item.worldUse?.sceneScript?:return "场景物品规则未接入"
        if(!r.verified()||item.id!="rom.special.${r.originalItemId}"||item.category!="special"||
            item.originalId!=r.originalItemId||item.maxCount!=1||target!=r.target||
            item.worldUse.targetSpriteId!=target.spriteId||item.worldUse.usedFlagId!="rom.inventory.special.${r.originalItemId}.used")
            return "场景物品规则未核验"
        if(!inMapMenu)return "只能在地图菜单使用"
        if((s.inventory[item.id]?:0)<=0)return "没有此物"
        if(s.inventory[item.id]!=1)return "物品数量异常，原状态已保留"
        if(s.flags[r.pendingFlag]==true)return "当前对话尚未结束"
        if(s.mapId!=r.mapId)return "当前场景不可用"
        if(!WorldItems.facesTarget(s,target))return "请站定并面向原版目标"
        if(r.originalItemId==0&&s.flags["rom.npccontext.37.196"]!=true)return "病房杨戬当前不在场"
        if(r.originalItemId==0&&s.characters.none{it.id=="yangjian"})return "杨戬记录缺失，原状态已保留"
        if(r.originalItemId==0&&s.characters.first{it.id=="yangjian"}.let{it.maxHp !in 1..9999||it.maxMp==null||it.maxMp !in 0..9999})
            return "杨戬上限数据异常，原状态已保留"
        return null
    }
    fun begin(s:SaveSnapshot,item:ItemDefinition,target:WorldObjectTarget,inMapMenu:Boolean):StoryFollowup.Result {
        unavailable(s,item,target,inMapMenu)?.let{return StoryFollowup.Result(s,null,false,it)}
        val r=item.worldUse!!.sceneScript!!
        val party=if(r.originalItemId==0)s.characters.map{if(it.id=="yangjian")
            it.copy(hp=it.maxHp,mp=it.maxMp!!,statusMask=0)else it}else s.characters
        // Qty128 in the cartridge means an empty count with a retained used row.
        // Retain the ID key; this does not create 128 usable inventory units.
        val stageFlags=r.continuation.dialogueIds.indices.map{r.continuation.stageKey(r.id,it)}
        val flags=((s.flags-stageFlags)+mapOf(
            item.worldUse.usedFlagId to true,r.pendingFlag to true)).let{
                if(r.originalItemId==14)it+(SHIP_FLAG to true)else it}
        return StoryFollowup.Result(s.copy(characters=party,inventory=s.inventory+(item.id to 0),flags=flags),
            r.continuation.dialogueIds.first(),true)
    }
    fun advance(s:SaveSnapshot,r:OriginalSceneItemDefinition,dialogue:String):StoryFollowup.Result {
        if(!r.validPending(s))return StoryFollowup.Result(s,null,false,"场景物品对话存档阶段不一致")
        return StoryFollowup.advance(s,r.id,r.pendingFlag,r.continuation,dialogue,emptyMap()){
            ((it+("rom.map.${r.mapId}.flag.128" to true))-r.pendingFlag).let{flags->
                if(r.originalItemId==0)flags+("rom.npccontext.37.196" to false)else flags}}
    }
}
