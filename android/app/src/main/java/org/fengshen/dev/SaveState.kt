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
        val current=when(definition.slot){"rightHand"->e.rightHand;"body"->e.body;"feet"->e.feet;else->return null}
        if(current!=definition.originalId)return null
        val next=when(definition.slot){"rightHand"->e.copy(rightHand=-1);"body"->e.copy(body=-1);else->e.copy(feet=-1)}
        return character.copy(equipment=next) to (items+(definition.itemId to ((items[definition.itemId]?:0)+1)))
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
        if((inventory[item.id]?:0)==0 && inventory.count{it.value>0&&it.key in shop.items}>=16)return Result(money,inventory,"物品栏已满")
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
    val equipment:EquipmentState?=null) {
    fun json()=JSONObject().put("id",id).put("level",level).put("experience",experience).put("hp",hp)
        .put("maxHp",maxHp).put("mp",mp).put("strength",strength).put("stamina",stamina)
        .put("agility",agility).put("spirit",spirit).also{if(maxMp!=null)it.put("maxMp",maxMp);
            if(equipment!=null)it.put("equipment",equipment.json())}
    companion object {
        fun parse(o:JSONObject)=CharacterState(o.getString("id"),o.getInt("level"),o.getInt("experience"),
            o.getInt("hp"),o.getInt("maxHp"),o.getInt("mp"),o.getInt("strength"),o.getInt("stamina"),
                o.getInt("agility"),o.getInt("spirit"),if(o.has("maxMp"))o.getInt("maxMp") else null,
                o.optJSONObject("equipment")?.let{EquipmentState.parse(it)}).also {
            require(it.id.matches(Regex("[a-z0-9_-]{1,64}")) && it.level in 1..99 && it.experience in 0..0xffffff &&
                it.maxHp in 1..9999 && it.hp in 0..it.maxHp && it.mp in 0..9999 &&
                listOf(it.strength,it.stamina,it.agility,it.spirit).all { value->value in 0..9999 } &&
                (it.maxMp==null || it.maxMp in it.mp..9999))
        }
    }
}

data class SaveSnapshot(val contentVersion:String,val mapId:Int,val x:Int,val y:Int,val direction:Key,
    val characters:List<CharacterState>,val inventory:Map<String,Int> = emptyMap(),val flags:Map<String,Boolean> = emptyMap(),
    val money:Int=0,val encounterSteps:Int=0) {
    fun json():JSONObject {
        val items=JSONObject();inventory.toSortedMap().forEach{(id,count)->items.put(id,count)}
        val events=JSONObject();flags.toSortedMap().forEach{(id,value)->events.put(id,value)}
        return JSONObject().put("saveSchemaVersion",1).put("contentVersion",contentVersion)
            .put("mapId",mapId).put("x",x).put("y",y).put("direction",direction.name)
            .put("characters",JSONArray().also{a->characters.forEach{a.put(it.json())}})
            .put("inventory",items).put("flags",events).put("money",money).put("encounterSteps",encounterSteps)
    }
    fun validate(content:Content):Boolean {
        if(contentVersion !in setOf(content.scene.version,"opening-to-world-b1","opening-segment-001-c1","opening-segment-001-c2","opening-segment-001-c3","opening-segment-001-c4","opening-segment-001-c5","opening-segment-001-c6","opening-segment-001-c7","opening-segment-001-c8","opening-segment-001-c9","opening-segment-001-c10","opening-segment-001-c11") || direction !in listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT) ||
            x%16!=8 || y%16!=8 || characters.isEmpty() || characters.size>4 || inventory.size>256 || flags.size>1024 || money !in 0..9999999 || encounterSteps !in 0..255)return false
        val scene=content.scenes[mapId]?:return false
        return scene.check(x/16,y/16)==null
    }
    companion object {
        fun parse(text:String):SaveSnapshot {
            val o=JSONObject(text);require(o.getInt("saveSchemaVersion")==1)
            val chars=o.getJSONArray("characters");require(chars.length() in 1..4)
            val inventory=mutableMapOf<String,Int>();val items=o.getJSONObject("inventory")
            for(key in items.keys()){val count=items.getInt(key);require(key.length in 1..96&&count in 0..9999);inventory[key]=count}
            val flags=mutableMapOf<String,Boolean>();val events=o.getJSONObject("flags")
            for(key in events.keys()){require(key.length in 1..96);flags[key]=events.getBoolean(key)}
            return SaveSnapshot(o.getString("contentVersion"),o.getInt("mapId"),o.getInt("x"),o.getInt("y"),
                Key.valueOf(o.getString("direction")),(0 until chars.length()).map{CharacterState.parse(chars.getJSONObject(it))},
                inventory,flags,o.optInt("money",0),o.optInt("encounterSteps",0))
        }
    }
}
