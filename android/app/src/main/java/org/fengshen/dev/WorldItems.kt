package org.fengshen.dev

data class TreasureDefinition(val itemId:String,val flagId:String,val amount:Int=1) {
    // Keep the cross-APK constructor ABI; the loader accepts this only with the
    // original scoped category-grant evidence, not a name-based item effect.
    var categoryGrant:Int?=null;internal set
}
data class WorldObjectTarget(val id:String,val mapId:Int,val x:Int,val y:Int,val spriteId:Int,
    val removedFlagId:String,val completionFlagId:String)
data class WorldItemUseDefinition(val targetSpriteId:Int,val usedFlagId:String)

/** Scoped original category-1/item-11 transactions. Evidence: world-key-item.json.
 * Results are proposals only: the caller owns gesture validation, commit and save rollback.
 * Original quantity bit 7 is a flag here, never an extra 128 inventory units.
 */
object WorldItems {
    const val ID="rom.special.11"
    private const val TARGET_SPRITE=226
    data class Result(val inventory:Map<String,Int>,val flags:Map<String,Boolean>,
        val applied:Boolean,val error:String?=null)

    private fun supported(item:ItemDefinition)=item.id==ID&&item.category=="special"&&
        item.originalId==11&&item.maxCount==1
    private fun validId(id:String)=id.isNotBlank()&&id.length<=96
    private fun reject(snapshot:SaveSnapshot,error:String)=Result(snapshot.inventory,snapshot.flags,false,error)

    /** Original inventory-grant stage only. The caller must first complete the original
     * encounter/dialogue dispatch; map 139's chest is guarded by a story battle.
     * This transaction does not establish that walking up to a chest permits its reward.
     */
    fun openTreasure(snapshot:SaveSnapshot,treasure:TreasureDefinition,item:ItemDefinition):Result {
        val category=treasure.categoryGrant
        val categories=listOf("medicine","special","weapon","armor")
        val expectedId=if(category==2&&item.originalId==0)OpeningEquipment.KNIFE_ID else "rom.${item.category}.${item.originalId}"
        val ordinary=category!=null&&category in categories.indices&&item.category==categories[category]&&
            item.originalId in 0..255&&item.id==expectedId&&item.maxCount==(if(category==1)1 else 10)
        if(!(if(category==null)supported(item)else ordinary)||treasure.itemId!=item.id||treasure.amount!=1||!validId(treasure.flagId))
            return reject(snapshot,"宝箱物品规则尚未核验")
        if(snapshot.flags[treasure.flagId]==true)return reject(snapshot,"已经取过了")
        val count=snapshot.inventory[item.id]?:0
        if(count<0)return reject(snapshot,"物品数量异常")
        if(count>=item.maxCount)return reject(snapshot,"物品数量已满")
        if(!InventoryCapacity.hasCategorySlot(snapshot.inventory,item.id,item.category))
            return reject(snapshot,"此类物品栏已满")
        return Result(snapshot.inventory+(item.id to count+1),snapshot.flags+(treasure.flagId to true),true)
    }

    private fun unavailable(snapshot:SaveSnapshot,item:ItemDefinition,rule:WorldItemUseDefinition,
        target:WorldObjectTarget,inMapMenu:Boolean):String? {
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
        val delta=when(snapshot.direction){
            Key.UP->0 to -1;Key.DOWN->0 to 1;Key.LEFT->-1 to 0;Key.RIGHT->1 to 0
            else->return "请站定并面向目标"
        }
        if(snapshot.x<0||snapshot.y<0||snapshot.x%16!=8||snapshot.y%16!=8||
            snapshot.x.toLong()+delta.first*16L!=target.x.toLong()*16+8||
            snapshot.y.toLong()+delta.second*16L!=target.y.toLong()*16+8)
            return "请站定并面向目标"
        return null
    }

    fun available(snapshot:SaveSnapshot,item:ItemDefinition,rule:WorldItemUseDefinition,
        target:WorldObjectTarget,inMapMenu:Boolean)=unavailable(snapshot,item,rule,target,inMapMenu)==null

    fun use(snapshot:SaveSnapshot,item:ItemDefinition,rule:WorldItemUseDefinition,
        target:WorldObjectTarget,inMapMenu:Boolean):Result {
        unavailable(snapshot,item,rule,target,inMapMenu)?.let{return reject(snapshot,it)}
        // This reusable item keeps its quantity, including when its used bit was already set.
        return Result(snapshot.inventory,snapshot.flags+mapOf(rule.usedFlagId to true,
            target.removedFlagId to true,target.completionFlagId to true),true)
    }
}
