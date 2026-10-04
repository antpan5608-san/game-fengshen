package org.fengshen.dev

data class TreasureDefinition(val itemId:String,val flagId:String,val amount:Int=1) {
    // Keep the cross-APK constructor ABI; the loader accepts this only with the
    // original scoped category-grant evidence, not a name-based item effect.
    var categoryGrant:Int?=null;internal set
}
data class MoneyTreasureDefinition(val flagId:String,val amount:Int,val moneyCap:Int,val evidence:String)
data class WorldObjectTarget(val id:String,val mapId:Int,val x:Int,val y:Int,val spriteId:Int,
    val removedFlagId:String,val completionFlagId:String)
data class WorldItemUseDefinition(val targetSpriteId:Int,val usedFlagId:String) {
    var yangJoin:OriginalYangJoinDefinition?=null;internal set
}
data class WorldFieldProtectionDefinition(val evidence:String)

/** Scoped original category-1/item-11 transactions. Evidence: world-key-item.json.
 * Results are proposals only: the caller owns gesture validation, commit and save rollback.
 * Original quantity bit 7 is a flag here, never an extra 128 inventory units.
 */
object WorldItems {
    fun categoryGrantEvidenceSupported(evidence:String,mapId:Int,npcId:String,category:Int):Boolean {
        if(category !in 0..3)return false
        return when(evidence){
            "game-data/provenance/world-hell-chest-grants.json",
            "game-data/provenance/world-tree107-chests.json",
            "game-data/provenance/world-island-chests.json"->true
            "game-data/provenance/world-five-dragon-chests.json"->mapId==99&&
                mapOf("rom.npc.99.0" to 2,"rom.npc.99.1" to 3,"rom.npc.99.2" to 0)[npcId]==category
            "game-data/provenance/world-cave87-chests.json"->mapId==87&&
                mapOf("rom.npc.87.1" to 0,"rom.npc.87.2" to 0,"rom.npc.87.3" to 0,
                    "rom.npc.87.5" to 2,"rom.npc.87.6" to 0,"rom.npc.87.7" to 2)[npcId]==category
            "game-data/provenance/world-village5-hidden.json"->mapId==5&&npcId=="rom.npc.5.5"&&category==0
            else->false
        }
    }
    const val ID="rom.special.11"
    const val FIELD_PROTECTION_ID="rom.special.12"
    const val FIELD_PENDING_FLAG="runtime.field67.protection.pending"
    const val FIELD_ACTIVE_FLAG="runtime.field67.protection.active"
    const val FIELD_USED_FLAG="rom.inventory.special.12.used"
    private const val TARGET_SPRITE=226
    data class Result(val inventory:Map<String,Int>,val flags:Map<String,Boolean>,
        val applied:Boolean,val error:String?=null)
    data class MoneyResult(val snapshot:SaveSnapshot,val applied:Boolean,val error:String?=null)
    fun openMoneyTreasure(snapshot:SaveSnapshot,treasure:MoneyTreasureDefinition):MoneyResult {
        fun reject(reason:String)=MoneyResult(snapshot,false,reason)
        val island=treasure.flagId=="rom.map.76.flag.4"&&treasure.amount==100&&
            treasure.evidence=="game-data/provenance/world-island-chests.json"
        val cave=treasure.flagId=="rom.map.87.flag.8"&&treasure.amount==550&&
            treasure.evidence=="game-data/provenance/world-cave87-chests.json"
        if((!island&&!cave)||treasure.moneyCap!=999999)return reject("钱箱规则尚未核验")
        if(snapshot.mapId!=if(island)76 else 87)return reject("当前场景不可用")
        if(snapshot.flags[treasure.flagId]==true)return reject("已经取过了")
        if(snapshot.money !in 0..treasure.moneyCap)return reject("当前银两超出原版钱箱可核范围，原状态已保留")
        return MoneyResult(snapshot.copy(money=minOf(treasure.moneyCap,snapshot.money+treasure.amount),
            flags=snapshot.flags+(treasure.flagId to true)),true)
    }

    private fun supported(item:ItemDefinition)=item.id==ID&&item.category=="special"&&
        item.originalId==11&&item.maxCount==1
    private fun validId(id:String)=id.isNotBlank()&&id.length<=96
    private fun reject(snapshot:SaveSnapshot,error:String)=Result(snapshot.inventory,snapshot.flags,false,error)

    /** Original map67/category1/id12: no character/object target, quantity1 is
     * retained and its used marker set. Selection is not execution. */
    fun fieldProtectionUnavailable(snapshot:SaveSnapshot,item:ItemDefinition,inMapMenu:Boolean):String? {
        if(item.id!=FIELD_PROTECTION_ID||item.category!="special"||item.originalId!=12||item.maxCount!=1||
            item.fieldProtectionUse?.evidence!="game-data/provenance/world-field67-item12.json")return "物品使用规则尚未核验"
        if(!inMapMenu)return "只能在地图菜单使用"
        val count=snapshot.inventory[item.id]?:0
        if(count<=0)return "没有此物"
        if(count!=1)return "物品数量异常"
        if(snapshot.mapId!=67)return "原版仅在第七殿使用"
        return null
    }
    fun useFieldProtection(snapshot:SaveSnapshot,item:ItemDefinition,inMapMenu:Boolean):Result {
        fieldProtectionUnavailable(snapshot,item,inMapMenu)?.let{return reject(snapshot,it)}
        return Result(snapshot.inventory,snapshot.flags+mapOf(FIELD_PENDING_FLAG to true,FIELD_USED_FLAG to true),true)
    }
    /** BA85..BA92 promotes pending at a SOURCE-map67 completed step; original
     * 858E clears pending on map reconstruction, not the already active bit.
     * New-game/defeat reset uses the existing whole-state reset. */
    fun fieldFlagsAfterStep(flags:Map<String,Boolean>,step:CompletedStep):Map<String,Boolean> {
        var next=flags
        if(step.mapId==67&&flags[FIELD_PENDING_FLAG]==true)next=next+(FIELD_ACTIVE_FLAG to true)
        if(step.transitioned)next=next-FIELD_PENDING_FLAG
        return next
    }

    /** Original inventory-grant stage only. The caller must first complete the original
     * encounter/dialogue dispatch; map 139's chest is guarded by a story battle.
     * This transaction does not establish that walking up to a chest permits its reward.
     */
    /** Loader and transaction share one scoped identity/capacity policy. */
    fun supportsTreasure(treasure:TreasureDefinition,item:ItemDefinition):Boolean {
        val category=treasure.categoryGrant
        val categories=listOf("medicine","special","weapon","armor")
        val expectedId=if(category==2&&item.originalId==0)OpeningEquipment.KNIFE_ID else "rom.${item.category}.${item.originalId}"
        val ordinary=category!=null&&category in categories.indices&&item.category==categories[category]&&
            item.originalId in 0..255&&item.id==expectedId&&item.maxCount==(if(category==1)1 else 10)
        return (if(category==null)supported(item)else ordinary)&&treasure.itemId==item.id&&
            treasure.amount==1&&validId(treasure.flagId)
    }
    fun openTreasure(snapshot:SaveSnapshot,treasure:TreasureDefinition,item:ItemDefinition):Result {
        if(!supportsTreasure(treasure,item))return reject(snapshot,"宝箱物品规则尚未核验")
        if(snapshot.flags[treasure.flagId]==true)return reject(snapshot,"已经取过了")
        val count=snapshot.inventory[item.id]?:0
        if(count<0)return reject(snapshot,"物品数量异常")
        if(count>=item.maxCount)return reject(snapshot,"物品数量已满")
        if(!InventoryCapacity.hasCategorySlot(snapshot.inventory,item.id,item.category))
            return reject(snapshot,"此类物品栏已满")
        return Result(snapshot.inventory+(item.id to count+1),snapshot.flags+(treasure.flagId to true),true)
    }

    fun facesTarget(snapshot:SaveSnapshot,target:WorldObjectTarget):Boolean {
        val delta=when(snapshot.direction){Key.UP->0 to -1;Key.DOWN->0 to 1;Key.LEFT->-1 to 0;Key.RIGHT->1 to 0;else->return false}
        return snapshot.x>=0&&snapshot.y>=0&&snapshot.x%16==8&&snapshot.y%16==8&&
            snapshot.x.toLong()+delta.first*16L==target.x.toLong()*16+8&&
            snapshot.y.toLong()+delta.second*16L==target.y.toLong()*16+8
    }

    private fun unavailable(snapshot:SaveSnapshot,item:ItemDefinition,rule:WorldItemUseDefinition,
        target:WorldObjectTarget,inMapMenu:Boolean):String? {
        if(rule.yangJoin!=null)return OriginalYangJoin.unavailable(snapshot,item,target,inMapMenu)
        val flagIds=listOf(rule.usedFlagId,target.removedFlagId,target.completionFlagId)
        if(!supported(item)||rule.targetSpriteId!=TARGET_SPRITE||target.spriteId!=TARGET_SPRITE||
            !validId(target.id)||flagIds.any{!validId(it)}||flagIds.toSet().size!=3||
            target.mapId<0||target.x<0||target.y<0)
            return "物品使用规则尚未核验"
        if(!inMapMenu)return "只能在地图菜单使用"
        val count=snapshot.inventory[item.id]?:0
        if(count<=0)return "没有此物"
        if(count!=1)return "物品数量异常"
        if(snapshot.flags[target.removedFlagId]==true)return "此处已处理"
        if(snapshot.mapId!=target.mapId)return "当前场景不可用"
        // Save coordinates are pixel centers; target coordinates are map cells.
        // Do not use interactionTarget's single-neighbour facing fallback here.
        if(!facesTarget(snapshot,target))return "请站定并面向目标"
        return null
    }

    fun available(snapshot:SaveSnapshot,item:ItemDefinition,rule:WorldItemUseDefinition,
        target:WorldObjectTarget,inMapMenu:Boolean)=unavailable(snapshot,item,rule,target,inMapMenu)==null

    fun use(snapshot:SaveSnapshot,item:ItemDefinition,rule:WorldItemUseDefinition,
        target:WorldObjectTarget,inMapMenu:Boolean):Result {
        unavailable(snapshot,item,rule,target,inMapMenu)?.let{return reject(snapshot,it)}
        if(rule.yangJoin!=null)return reject(snapshot,"入队须通过统一剧情事务提交")
        // This reusable item keeps its quantity, including when its used bit was already set.
        return Result(snapshot.inventory,snapshot.flags+mapOf(rule.usedFlagId to true,
            target.removedFlagId to true,target.completionFlagId to true),true)
    }
}
