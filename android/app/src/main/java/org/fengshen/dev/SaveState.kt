package org.fengshen.dev

import org.json.JSONArray
import org.json.JSONObject

data class EquipmentState(val rightHand:Int,val leftHand:Int,val body:Int,val feet:Int) {
    fun json()=JSONObject().put("rightHand",rightHand).put("leftHand",leftHand).put("body",body).put("feet",feet)
    companion object {
        fun parse(o:JSONObject)=EquipmentState(o.getInt("rightHand"),o.getInt("leftHand"),
            o.getInt("body"),o.getInt("feet")).also{e->
            require(listOf(e.rightHand,e.leftHand,e.body,e.feet).all{it in -1..255})
        }
    }
}

/** Only the ROM-observed opening knife cycle is enabled. -1 is the ROM empty-slot byte FF. */
object InventoryCapacity {
    fun category(id:String):String?=when {
        id==OpeningEquipment.KNIFE_ID||id.startsWith("rom.weapon.")->"weapon"
        id.startsWith("rom.armor.")->"armor"
        id.startsWith("rom.medicine.")->"medicine"
        id.startsWith("rom.special.")->"special"
        else->null
    }
    fun hasCategorySlot(items:Map<String,Int>,id:String,category:String)=
        (items[id]?:0)>0||items.count{it.value>0&&InventoryCapacity.category(it.key)==category}<16
}

object OpeningEquipment {
    const val KNIFE_ID="rom.item.0"
    fun equip(character:CharacterState,items:Map<String,Int>,definition:EquipmentDefinition):Pair<CharacterState,Map<String,Int>>? {
        val e=character.equipment?:return null
        if(!definition.operationEnabled || character.id !in definition.allowedCharacters || (items[definition.itemId]?:0)<=0)return null
        val empty=when(definition.slot){"rightHand"->e.rightHand==-1;"body"->e.body==-1;"feet"->e.feet==-1;else->false}
        if(!empty)return null // Explicitly remove first; unknown replacement/capacity side effects are not guessed.
        val next=when(definition.slot){"rightHand"->e.copy(rightHand=definition.originalId);"body"->e.copy(body=definition.originalId);"feet"->e.copy(feet=definition.originalId);else->return null}
        val remaining=items.getValue(definition.itemId)-1;val inventory=items.toMutableMap()
        if(remaining==0)inventory.remove(definition.itemId) else inventory[definition.itemId]=remaining
        return character.copy(equipment=next) to inventory
    }
    fun unequip(character:CharacterState,items:Map<String,Int>,definition:EquipmentDefinition):Pair<CharacterState,Map<String,Int>>? {
        val e=character.equipment?:return null
        if(!definition.operationEnabled || character.id !in definition.allowedCharacters || (items[definition.itemId]?:0)>=10)return null
        val category=if(definition.slot=="rightHand")"weapon" else "armor"
        if(!InventoryCapacity.hasCategorySlot(items,definition.itemId,category))return null
        val current=when(definition.slot){"rightHand"->e.rightHand;"body"->e.body;"feet"->e.feet;else->return null}
        if(current!=definition.originalId)return null
        val next=when(definition.slot){"rightHand"->e.copy(rightHand=-1);"body"->e.copy(body=-1);else->e.copy(feet=-1)}
        return character.copy(equipment=next) to (items+(definition.itemId to ((items[definition.itemId]?:0)+1)))
    }
    /** Equivalent to the existing legal remove-then-equip sequence, committed only as one result. */
    fun replace(character:CharacterState,items:Map<String,Int>,definition:EquipmentDefinition,
        definitions:Collection<EquipmentDefinition>):Pair<CharacterState,Map<String,Int>>? {
        val e=character.equipment?:return null
        val old=when(definition.slot){"rightHand"->e.rightHand;"body"->e.body;"feet"->e.feet;else->return null}
        if(old==-1)return equip(character,items,definition)
        if(old==definition.originalId)return null
        val prior=definitions.firstOrNull{it.slot==definition.slot&&it.originalId==old}?:return null
        val removed=unequip(character,items,prior)?:return null
        return equip(removed.first,removed.second,definition)
    }
    fun equipKnife(character:CharacterState,items:Map<String,Int>):Pair<CharacterState,Map<String,Int>>? {
        val equipment=character.equipment?:return null
        if(character.id!="nezha" || equipment.rightHand!=-1 || (items[KNIFE_ID]?:0)<=0)return null
        val count=items.getValue(KNIFE_ID)-1
        val updated=items.toMutableMap();if(count==0)updated.remove(KNIFE_ID) else updated[KNIFE_ID]=count
        return character.copy(equipment=equipment.copy(rightHand=0)) to updated
    }
    fun unequipKnife(character:CharacterState,items:Map<String,Int>):Pair<CharacterState,Map<String,Int>>? {
        val equipment=character.equipment?:return null
        if(character.id!="nezha" || equipment.rightHand!=0 || (items[KNIFE_ID]?:0)>=9999)return null
        return character.copy(equipment=equipment.copy(rightHand=-1)) to
            (items+(KNIFE_ID to ((items[KNIFE_ID]?:0)+1)))
    }
}

/** Atomic one-item transaction; the modal confirmation owns one call, rendering owns none. */
object TownTrade {
    data class Result(val money:Int,val inventory:Map<String,Int>,val error:String?=null)
    fun buy(money:Int,inventory:Map<String,Int>,shop:ShopDefinition,item:ItemDefinition):Result {
        val price=item.buyPrice
        if(item.id !in shop.items || price==null || price<0)return Result(money,inventory,"商品尚未开放")
        if(money<price)return Result(money,inventory,"银两不足")
        if((inventory[item.id]?:0)>=item.maxCount)return Result(money,inventory,"数量已满")
        if(!InventoryCapacity.hasCategorySlot(inventory,item.id,item.category))return Result(money,inventory,"物品栏已满")
        return Result(money-price,inventory+(item.id to ((inventory[item.id]?:0)+1)))
    }
    fun sell(money:Int,inventory:Map<String,Int>,shop:ShopDefinition,item:ItemDefinition):Result {
        val price=item.sellPrice;val count=inventory[item.id]?:0
        if(item.id !in shop.sellItems || price==null || price<0)return Result(money,inventory,"此物卖出规则尚未核验")
        if(count<=0)return Result(money,inventory,"没有此物")
        if(money+price>9999999)return Result(money,inventory,"银两已满")
        val next=inventory.toMutableMap();if(count==1)next.remove(item.id) else next[item.id]=count-1
        return Result(money+price,next)
    }
}

/** The shared original lodging command; price/policy belong to each evidenced service instance. */
object InnStay {
    data class Result(val money:Int,val characters:List<CharacterState>,val error:String?=null)
    fun eligible(character:CharacterState,inn:InnDefinition)=character.statusMask and inn.blockedStatusMask==0
    fun apply(money:Int,characters:List<CharacterState>,inn:InnDefinition):Result {
        if(inn.price !in 0..9999999 || inn.blockedStatusMask !in 0..255 || characters.isEmpty())
            return Result(money,characters,"住宿定义或队伍无效")
        if(money<inn.price)return Result(money,characters,"银两不足")
        return Result(money-inn.price,characters.map{hero->
            if(eligible(hero,inn))hero.copy(hp=hero.maxHp,mp=hero.maxMp?:hero.mp,statusMask=0) else hero
        })
    }
}

/** Scoped original map/menu herb command. Rendering never mutates these values. */
object HerbUse {
    const val ID="rom.medicine.0"
    data class Result(val characters:List<CharacterState>,val inventory:Map<String,Int>,val applied:Boolean)
    fun available(characters:List<CharacterState>,inventory:Map<String,Int>,targetId:String,
        item:ItemDefinition,inMapMenu:Boolean):Boolean {
        val rule=item.herbUse?:return false
        val index=characters.indexOfFirst{it.id==targetId}
        val count=inventory[ID]?:0
        if(!inMapMenu || item.id!=ID || index<0 || count<=0 || rule.healHp<=0)return false
        val hero=characters[index]
        return hero.hp>0 && hero.hp<=hero.maxHp && hero.maxHp>0 &&
            (hero.hp<hero.maxHp || rule.consumeAtFullHp)
    }
    fun apply(characters:List<CharacterState>,inventory:Map<String,Int>,targetId:String,
        item:ItemDefinition,inMapMenu:Boolean):Result {
        if(!available(characters,inventory,targetId,item,inMapMenu))return Result(characters,inventory,false)
        val rule=item.herbUse!!
        val index=characters.indexOfFirst{it.id==targetId}
        val count=inventory.getValue(ID)
        val hero=characters[index]
        val next=hero.copy(hp=minOf(hero.maxHp.toLong(),hero.hp.toLong()+rule.healHp).toInt())
        val items=inventory.toMutableMap()
        if(count==1)items.remove(ID) else items[ID]=count-1
        return Result(characters.toMutableList().also{it[index]=next},items,true)
    }
}

data class CharacterState(val id:String,val level:Int,val experience:Int,val hp:Int,val maxHp:Int,val mp:Int,
    val strength:Int,val stamina:Int,val agility:Int,val spirit:Int,val maxMp:Int?=null,
    val equipment:EquipmentState?=null,val statusMask:Int=0) {
    fun json()=JSONObject().put("id",id).put("level",level).put("experience",experience).put("hp",hp)
        .put("maxHp",maxHp).put("mp",mp).put("strength",strength).put("stamina",stamina)
        .put("agility",agility).put("spirit",spirit).also{if(maxMp!=null)it.put("maxMp",maxMp);
            if(equipment!=null)it.put("equipment",equipment.json());if(statusMask!=0)it.put("statusMask",statusMask)}
    companion object {
        fun parse(o:JSONObject)=CharacterState(o.getString("id"),o.getInt("level"),o.getInt("experience"),
            o.getInt("hp"),o.getInt("maxHp"),o.getInt("mp"),o.getInt("strength"),o.getInt("stamina"),
                o.getInt("agility"),o.getInt("spirit"),if(o.has("maxMp"))o.getInt("maxMp") else null,
                o.optJSONObject("equipment")?.let{EquipmentState.parse(it)},o.optInt("statusMask",0)).also {
            require(it.id.matches(Regex("[a-z0-9_-]{1,64}")) && it.level in 1..99 && it.experience in 0..0xffffff &&
                it.maxHp in 1..9999 && it.hp in 0..it.maxHp && it.mp in 0..9999 &&
                listOf(it.strength,it.stamina,it.agility,it.spirit).all { value->value in 0..9999 } &&
                (it.maxMp==null || it.maxMp in it.mp..9999) && it.statusMask in 0..255)
        }
    }
}

data class SaveSnapshot(val contentVersion:String,val mapId:Int,val x:Int,val y:Int,val direction:Key,
    val characters:List<CharacterState>,val inventory:Map<String,Int> = emptyMap(),val flags:Map<String,Boolean> = emptyMap(),
    val money:Int=0,val encounterSteps:Int=0,val interiorContext:InteriorContext?=null,val terrainMode:Int=0) {
    /** Published c11..c14 only exposed the three town0 stores (and inn in c14).
     * Infer that one evidenced legacy caller; new saves must carry their actual caller. */
    fun resolvedInteriorContext(content:Content):InteriorContext? {
        interiorContext?.let{return it}
        val knownLegacy=(mapId in 17..19&&contentVersion in (11..14).map{"opening-segment-001-c$it"})||
            (mapId==22&&contentVersion=="opening-segment-001-c14")
        if(!knownLegacy)return null
        val entry=content.exits.singleOrNull{it.captureCaller&&it.fromMapId==0&&it.toMapId==mapId}?:return null
        return InteriorContext(0,entry.triggerX,entry.triggerY)
    }
    fun json():JSONObject {
        val items=JSONObject();inventory.toSortedMap().forEach{(id,count)->items.put(id,count)}
        val events=JSONObject();flags.toSortedMap().forEach{(id,value)->events.put(id,value)}
        return JSONObject().put("saveSchemaVersion",1).put("contentVersion",contentVersion)
            .put("mapId",mapId).put("x",x).put("y",y).put("direction",direction.name)
            .put("characters",JSONArray().also{a->characters.forEach{a.put(it.json())}})
            .put("terrainMode",terrainMode).put("inventory",items).put("flags",events).put("money",money).put("encounterSteps",encounterSteps).also{json->
                interiorContext?.let{c->json.put("interiorContext",JSONObject().put("callerMapId",c.callerMapId)
                    .put("returnX",c.returnX).put("returnY",c.returnY))}
            }
    }
    fun validate(content:Content):Boolean {
        if(!compatibleContentVersion(contentVersion,content.scene.version) || direction !in listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT) ||
            x%16!=8 || y%16!=8 || characters.isEmpty() || characters.size>4 || inventory.size>256 || flags.size>1024 || money !in 0..9999999 || encounterSteps !in 0..255)return false
        if(content.sceneStories.values.any{!it.validPending(this)})return false
        val scene=content.sceneForState(mapId,flags)?:return false
        val resolved=resolvedInteriorContext(content)
        if(resolved==null&&content.exits.any{it.returnToCaller&&it.fromMapId==mapId})return false
        resolved?.let{c->
            if(content.exits.none{it.captureCaller&&it.fromMapId==c.callerMapId&&it.toMapId==mapId&&
                    it.triggerX==c.returnX&&it.triggerY==c.returnY})return false
            val caller=content.scenes[c.callerMapId]?:return false
            if(caller.check(c.returnX,c.returnY)!=null)return false
        }
        return scene.check(x/16,y/16,terrainMode)==null
    }
    companion object {
        /** Known schema1 content iterations through the original rebirth checkpoint.
         * This admits their version marker only; scene, actor, inventory, caller
         * and flag-dependent position checks remain mandatory below. */
        fun compatibleContentVersion(saved:String,current:String)=saved==current||saved=="opening-to-world-b1"||
            saved in (1..28).map{"opening-segment-001-c$it"}
        fun parse(text:String):SaveSnapshot {
            val o=JSONObject(text);require(o.getInt("saveSchemaVersion")==1)
            val chars=o.getJSONArray("characters");require(chars.length() in 1..4)
            val inventory=mutableMapOf<String,Int>();val items=o.getJSONObject("inventory")
            for(key in items.keys()){val count=items.getInt(key);require(key.length in 1..96&&count in 0..9999);inventory[key]=count}
            val flags=mutableMapOf<String,Boolean>();val events=o.getJSONObject("flags")
            for(key in events.keys()){require(key.length in 1..96);flags[key]=events.getBoolean(key)}
            return SaveSnapshot(o.getString("contentVersion"),o.getInt("mapId"),o.getInt("x"),o.getInt("y"),
                Key.valueOf(o.getString("direction")),(0 until chars.length()).map{CharacterState.parse(chars.getJSONObject(it))},
                inventory,flags,o.optInt("money",0),o.optInt("encounterSteps",0),
                o.optJSONObject("interiorContext")?.let{InteriorContext(it.getInt("callerMapId"),it.getInt("returnX"),it.getInt("returnY"))},o.optInt("terrainMode",0))
        }
    }
}
