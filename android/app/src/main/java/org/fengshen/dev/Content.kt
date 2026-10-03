package org.fengshen.dev

import android.content.res.AssetManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import android.os.SystemClock

/** Bundled and future validated cache directories use this same reader. No networking in A. */
interface ContentSource { fun read(name: String): ByteArray }
fun checkedName(name: String): String { require(name.matches(Regex("[a-zA-Z0-9._-]+")) && !name.startsWith("."));return name }
class AssetSource(private val assets: AssetManager): ContentSource {
    override fun read(name: String)=assets.open("development/"+checkedName(name)).use { it.readBytes() }
}
class DirectorySource(private val root: File): ContentSource {
    override fun read(name: String)=File(root,checkedName(name)).readBytes()
}
data class StoryEffect(val type:String,val id:String?,val amount:Int,val source:String)
data class StoryNpc(val id:String,val x:Int,val y:Int,val sprite:Bitmap,val firstDialogue:String,
    val repeatDialogue:String?,val firstEffects:List<StoryEffect>,val source:String,val mapId:Int=114,
    val shopId:String?=null,val interactionCell:Pair<Int,Int>?=null,val innId:String?=null,
    val treasure:TreasureDefinition?=null,val openedSprite:Bitmap?=null) {
    // Original script-spawned actors are only present in that story interaction.
    // Keep the existing constructor for instrumentation against the reviewed APK.
    var scriptedActor:Boolean=false;internal set
}
data class MapObject(val id:String,val mapId:Int,val x:Int,val y:Int,val sprite:Bitmap,
    val itemTarget:WorldObjectTarget?=null)
data class StoryText(val id:String,val text:String,val source:String)
data class CharacterDefinition(val id:String,val name:String,val portraitAsset:String,val portrait:Bitmap,
    val source:String,val equipmentSlots:List<String>?=null,val skillRefs:List<String>?=null) {
    var originalActorIndex:Int=0;internal set
}
data class ItemDefinition(val id:String,val name:String,val description:String?,val source:String,
    val category:String="weapon",val originalId:Int=0,val buyPrice:Int?=null,val sellPrice:Int?=null,
    val maxCount:Int=10,val preview:Bitmap?=null,val herbUse:HerbUseDefinition?=null,val antidoteUse:AntidoteUseDefinition?=null,
    val worldUse:WorldItemUseDefinition?=null)
data class AntidoteUseDefinition(val evidence:String)
data class HerbUseDefinition(val healHp:Int,val consumeAtFullHp:Boolean,val evidence:String)
data class EquipmentDefinition(val itemId:String,val originalId:Int,val slot:String,val attackBonus:Int,
    val allowedCharacters:Set<String>,val source:String,val defenseBonus:Int=0,val evasionValue:Int=0,
    val operationEnabled:Boolean=true)

data class ShopDefinition(val id:String,val mapId:Int,val npcId:String,val name:String,
    val items:List<String>,val sellItems:Set<String>,val buyPrompt:String="")
data class InnDefinition(val id:String,val mapId:Int,val npcId:String,val name:String,
    val price:Int,val blockedStatusMask:Int,val prompt:String,val evidence:String)
data class ServiceBinding(val callerMapId:Int,val interiorMapId:Int,val npcId:String,
    val shopId:String?=null,val innId:String?=null)
data class Content(val scene: Scene,val atlas: Bitmap,val sprites: Map<Key,Bitmap>,
    val scenes:Map<Int,Scene> = mapOf(scene.mapId to scene),val atlases:Map<Int,Bitmap> = mapOf(scene.mapId to atlas),
    val exits:List<MapExit> = emptyList(),val initialPlayer:CharacterState,
    val initialMoney:Int=0,val intro:StoryText?=null,val npcs:List<StoryNpc> = emptyList(),
    val dialogues:Map<String,StoryText> = emptyMap(),val itemNames:Map<String,String> = emptyMap(),
    val playerNames:Map<String,String> = emptyMap(),
    val characterDefinitions:Map<String,CharacterDefinition> = emptyMap(),
    val itemDefinitions:Map<String,ItemDefinition> = emptyMap(),
    val equipmentDefinitions:Map<String,EquipmentDefinition> = emptyMap(),
    val battle:BattleContent?=null,val audio:AudioContent?=null,
    val enemyGraphics:Map<Int,Bitmap> = emptyMap(),val battleHorizon:Bitmap?=null,val battleHero:Bitmap?=null,
    val shops:Map<String,ShopDefinition> = emptyMap(),val mapObjects:List<MapObject> = emptyList(),
    val battleHorizons:Map<Int,Bitmap> = emptyMap(),val blackBattleEnemyIds:Set<Int> = emptySet(),
    val enemyOrigins:Map<Int,Pair<Int,Int>> = emptyMap(),val inns:Map<String,InnDefinition> = emptyMap(),
    val serviceBindings:List<ServiceBinding> = emptyList()) {
    // One state-dependent scene view. Arrays and atlases stay in the existing
    // bounded loader; this never holds every visited map alive.
    var joinCharacters:Map<String,CharacterState> = emptyMap()
        internal set
    var mechanisms:List<SceneMechanism> = emptyList()
        internal set
    private var stateScene:Scene?=null
    private var stateFlags:Map<String,Boolean>?=null
    @Synchronized fun sceneForState(mapId:Int,flags:Map<String,Boolean>):Scene? {
        if(stateScene?.mapId==mapId&&stateFlags===flags)return stateScene
        val base=scenes[mapId]?:return null
        val removed=mapObjects.mapNotNull{it.itemTarget}.filter{it.mapId==mapId&&flags[it.removedFlagId]==true}
            .map{it.y*base.width+it.x}.toSet()
        var result=if(removed.isEmpty())base else base.copy(dynamicObjectCells=base.dynamicObjectCells-removed)
        for(mechanism in mechanisms)result=mechanism.apply(result,flags)
        stateScene=result;stateFlags=flags;return result
    }
}
object ContentLoader {
    fun load(source: ContentSource,timing:(JSONObject)->Unit={},audioCache:File?=null): Content {
        val started=SystemClock.elapsedRealtime();var verificationMs=0L;var atlasMs=0L
        val manifestBytes=source.read("manifest.json")
        val manifest=JSONObject(String(manifestBytes,Charsets.UTF_8))
        require(manifest.getInt("schemaVersion")==1 && manifest.getString("channel")=="development")
        val hashes=manifest.getJSONObject("files")
        fun read(name: String): ByteArray {
            val start=SystemClock.elapsedRealtime()
            val b=source.read(checkedName(name));require(b.size<=2_000_000)
            val hash=MessageDigest.getInstance("SHA-256").digest(b).joinToString(""){"%02x".format(it)}
            require(hash==hashes.getString(name)){"内容校验失败: $name"};verificationMs+=SystemClock.elapsedRealtime()-start;return b
        }
        fun ints(data:JSONObject,name:String)=data.getJSONArray(name).let { a->IntArray(a.length()){a.getInt(it)} }
        fun edges(data:JSONObject,name:String):Map<Int,Set<Key>> {
            val o=data.optJSONObject(name)?:return emptyMap()
            return o.keys().asSequence().associate{k->k.toInt() to o.getJSONArray(k).let{a->
                (0 until a.length()).map{Key.valueOf(a.getString(it))}.toSet()}}
        }
        fun scene(name:String,expectedId:Int):Pair<JSONObject,Scene>{
            val data=JSONObject(String(read(name),Charsets.UTF_8))
            require(data.getInt("schemaVersion")==1 && data.getInt("originalMapId")==expectedId)
            require(data.getInt("tileSize")==16 && data.getInt("logicalWidth")==256 && data.getInt("logicalHeight")==240)
            require(data.getString("channel")=="development" && data.getString("version")==manifest.getString("version"))
            val spawn=ints(data,"spawn");require(spawn.size==2)
            val result=Scene(data.getString("version"),data.getInt("width"),data.getInt("height"),
                ints(data,"grid"),ints(data,"collision"),ints(data,"enabledCells").toSet(),spawn[0],spawn[1],expectedId,
                if(data.has("walkableClasses"))ints(data,"walkableClasses").toSet() else setOf(0),
                if(data.has("dynamicObjectCells"))ints(data,"dynamicObjectCells").toSet() else emptySet(),
                if(data.has("transitionCells"))ints(data,"transitionCells").toSet() else emptySet(),
                edges(data,"sourceEdges"),edges(data,"targetEdges"),
                data.optJSONArray("unavailableRegions")?.let{a->(0 until a.length()).map{i->
                    val r=a.getJSONArray(i);require(r.length()==4)
                    EncounterRect(r.getInt(0),r.getInt(1),r.getInt(2),r.getInt(3))}}?:emptyList(),data.optJSONObject("terrain")?.let{t->
                    require(t.getString("evidence").isNotBlank())
                    t.getInt("tileset").also{require(it in setOf(OriginalTerrain.PALACE,OriginalTerrain.CAVE_GROUND))}
                })
            return data to result
        }
        val (data,opening)=scene("scene.json",114)
        val mapFiles=if(data.has("maps"))data.getJSONArray("maps").let{a->
            (0 until a.length()).map{i->a.getJSONObject(i)}.map{o->
                Triple(o.getInt("id"),checkedName(o.getString("scene")),checkedName(o.getString("atlas")))}
        } else listOf(Triple(114,"scene.json","tiles.png"),Triple(16,"scene16.json","tiles16.png"))
        // ROM dispatch IDs are bytes; cardinality is a format bound, not the last task's sample count.
        require(mapFiles.size in 1..256 && mapFiles.all{it.first in 0..255} && mapFiles.map{it.first}.toSet().size==mapFiles.size &&
            mapFiles.any{it.first==114 && it.second=="scene.json"})
        val sceneFiles=mapFiles.associate{it.first to it.second}
        val scenes=ResourceMap(sceneFiles.keys,8){id->if(id==114)opening else scene(sceneFiles.getValue(id),id).second}
        val initial=data.getJSONObject("initialPlayer")
        require(initial.getJSONObject("source").getString("confidence")=="HIGH")
        val initialName=initial.optString("name").ifBlank{initial.getString("id")}
        require(initialName.isNotBlank() && initialName.length<=32)
        val initialPlayer=CharacterState.parse(initial)
        val exits=data.getJSONArray("exits").let{a->(0 until a.length()).map{i->
            val o=a.getJSONObject(i);val trigger=ints(o,"trigger");val spawn=ints(o,"spawn")
            require(trigger.size==2&&spawn.size==2&&o.getString("confidence")=="VERIFIED")
            val mode=o.optString("triggerMode","CELL");require(mode in setOf("CELL","EDGE"))
            val direction=if(mode=="EDGE")Key.valueOf(o.getString("direction")) else null
            require(direction==null || direction in setOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT))
            val arrival=Key.valueOf(o.optString("arrivalDirection","DOWN"))
            require(arrival in setOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT))
            MapExit(o.getInt("fromMapId"),trigger[0],trigger[1],o.getInt("toMapId"),spawn[0],spawn[1],direction,arrival,o.optBoolean("resetEncounterSteps",false),o.optBoolean("captureCaller",false),o.optBoolean("returnToCaller",false)).also{
                it.preserveArrivalDirection=o.optBoolean("preserveArrivalDirection",false)
            }
        }}
        require(exits.all{exit->
            val from=scenes[exit.fromMapId];val to=scenes[exit.toMapId]
            from!=null && to!=null && from.check(exit.triggerX,exit.triggerY)==null && to.check(exit.spawnX,exit.spawnY)==null
        })
        fun bitmap(name: String,w: Int,h: Int): Bitmap {
            val b=read(name);val bitmapStart=SystemClock.elapsedRealtime();val opts=BitmapFactory.Options().apply{inScaled=false}
            val image=BitmapFactory.decodeByteArray(b,0,b.size,opts)?:error("Invalid image")
            require(image.width==w&&image.height==h);atlasMs+=SystemClock.elapsedRealtime()-bitmapStart;return image
        }
        val atlasFiles=mapFiles.associate{it.first to it.third}
        val atlasResources=ResourceMap(atlasFiles.values.distinct(),8){name->bitmap(name,256,256)}
        val atlases=object:AbstractMap<Int,Bitmap>(){
            override val keys:Set<Int> get()=atlasFiles.keys
            override val size get()=atlasFiles.size
            override fun containsKey(key:Int)=key in atlasFiles
            override fun get(key:Int)=atlasFiles[key]?.let{atlasResources.getValue(it)}
            override val entries get()=atlasFiles.keys.map{id->java.util.AbstractMap.SimpleImmutableEntry(id,getValue(id))}.toSet()
        }
        val atlas114=atlases.getValue(114)
        val introData=data.optJSONObject("intro")
        val intro=introData?.let{StoryText(it.getString("id"),it.getString("text"),
            it.getJSONObject("source").getString("confidence"))}
        val dialogueArray=data.getJSONArray("dialogues")
        val dialogues=(0 until dialogueArray.length()).map{dialogueArray.getJSONObject(it)}.associate{d->
            val record=StoryText(d.getString("id"),d.getString("text"),d.getJSONObject("source").getString("confidence"))
            record.id to record
        }
        val npcArray=data.getJSONArray("npcs")
        val npcs=(0 until npcArray.length()).map{i->
            val n=npcArray.getJSONObject(i);val cell=ints(n,"cell")
            val mapId=n.optInt("mapId",114);val npcScene=scenes.getValue(mapId)
            require(cell.size==2 && cell[0] in 0 until npcScene.width && cell[1] in 0 until npcScene.height)
            val effects=n.getJSONArray("firstEffects").let{a->(0 until a.length()).map{j->
                val e=a.getJSONObject(j);val confidence=e.getString("confidence")
                require((confidence=="PROVISIONAL_REFERENCE" && !e.getBoolean("originalVerified")) ||
                    (confidence=="GAMEPLAY_VERIFIED" && e.getBoolean("originalVerified") && e.has("evidence")))
                StoryEffect(e.getString("type"),e.optString("id").takeIf{it.isNotEmpty()},e.getInt("amount"),e.getString("source"))
                    .also{require(it.type in setOf("money","item") && it.amount in 1..9999 && (it.type!="item"||it.id!=null))}
            }}
            StoryNpc(n.getString("id"),cell[0],cell[1],bitmap(n.getString("sprite"),16,16),
                n.optString("firstDialogue"),n.optString("repeatDialogue").takeIf{it.isNotEmpty()&&it!="null"},
                effects,n.getJSONObject("source").getString("confidence"),mapId,
                n.optString("shopId").takeIf{it.isNotEmpty()},
                n.optJSONArray("interactionCell")?.let{it.getInt(0) to it.getInt(1)},
                n.optString("innId").takeIf{it.isNotEmpty()},
                n.optJSONObject("treasure")?.let{t->
                    require(t.getString("evidence").isNotBlank()&&t.getInt("amount")==1)
                    TreasureDefinition(t.getString("itemId"),t.getString("flagId"),t.getInt("amount"))
                },n.optString("openedSprite").takeIf{it.isNotEmpty()}?.let{bitmap(it,16,16)}).also{npc->
                npc.scriptedActor=n.optBoolean("scriptedActor",false)
                require(!npc.scriptedActor||(npc.shopId==null&&npc.innId==null&&npc.treasure==null&&effects.isEmpty()))
            }
        }
        val mapObjects=data.optJSONArray("mapObjects")?.let{a->(0 until a.length()).map{i->
            val o=a.getJSONObject(i);val cell=ints(o,"cell");val mid=o.getInt("mapId")
            require(o.getString("interaction") in setOf("NOT_IMPLEMENTED","WORLD_ITEM_TARGET")&&mid in scenes&&cell.size==2&&
                cell[0] in 0 until scenes.getValue(mid).width&&cell[1] in 0 until scenes.getValue(mid).height)
            MapObject(o.getString("id"),mid,cell[0],cell[1],bitmap(o.getString("sprite"),16,16),
                if(o.getString("interaction")=="WORLD_ITEM_TARGET")o.getJSONObject("itemTarget").let{t->
                    require(t.getInt("spriteId")==226&&t.getString("evidence").isNotBlank())
                    WorldObjectTarget(o.getString("id"),mid,cell[0],cell[1],t.getInt("spriteId"),
                        t.getString("removedFlagId"),t.getString("completionFlagId"))
                }else null)
        }}?:emptyList()
        require(npcs.map{it.id}.toSet().size==npcs.size && npcs.all{(it.treasure!=null||it.firstDialogue in dialogues) && (it.repeatDialogue==null||it.repeatDialogue in dialogues)})
        val itemArray=data.getJSONArray("items")
        require((0 until itemArray.length()).map{itemArray.getJSONObject(it).getString("id")}.distinct().size==itemArray.length()){"重复的稳定物品ID"}
        val itemDefinitions=(0 until itemArray.length()).associate{i->
            val o=itemArray.getJSONObject(i);val source=o.getJSONObject("source").getString("confidence")
            require(source in setOf("PROVISIONAL_REFERENCE","GAMEPLAY_VERIFIED")){"不支持的物品来源状态: ${o.getString("id")}: $source"}
            val preview=o.optJSONObject("preview")
            val item=ItemDefinition(o.getString("id"),o.getString("name"),o.optString("description").takeIf{it.isNotBlank()},source,
                o.optString("category","weapon"),o.optInt("originalId",0),
                if(o.has("buyPrice"))o.getInt("buyPrice") else null,
                if(o.has("sellPrice"))o.getInt("sellPrice") else null,o.optInt("maxCount",10),
                preview?.let{bitmap(it.getString("asset"),it.getInt("width"),it.getInt("height"))},
                o.optJSONObject("herbUse")?.let{use->
                    require(o.getString("id")=="rom.medicine.0" && o.getString("category")=="medicine" &&
                        o.getInt("originalId")==0 && use.getBoolean("mapMenu") &&
                        use.getString("target")=="living-party-member" && use.getInt("healHp")==50 &&
                        use.getBoolean("consumeAtFullHp"))
                    HerbUseDefinition(use.getInt("healHp"),use.getBoolean("consumeAtFullHp"),use.getString("evidence"))
                },o.optJSONObject("antidoteUse")?.let{use->
                    require(o.getString("id")==AntidoteUse.ID&&o.getString("category")=="medicine"&&o.getInt("originalId")==6&&
                        use.getBoolean("mapMenu")&&use.getInt("cureStatusMask")==2&&use.getInt("confirmationConsumption")==1&&
                        use.getInt("extraConsumptionWhenCured")==1&&use.getString("evidence").isNotBlank())
                    AntidoteUseDefinition(use.getString("evidence"))
                },o.optJSONObject("worldUse")?.let{use->
                    require(o.getString("id")=="rom.special.11"&&o.getString("category")=="special"&&
                        o.getInt("originalId")==11&&o.getInt("maxCount")==1&&use.getInt("targetSpriteId")==226&&
                        use.getBoolean("reusable")&&use.getString("evidence").isNotBlank())
                    WorldItemUseDefinition(use.getInt("targetSpriteId"),use.getString("usedFlagId"))
                })
            item.id to item
        }
        require(npcs.filter{it.treasure!=null}.all{npc->
            val t=npc.treasure!!;val item=itemDefinitions[t.itemId]
            npc.openedSprite!=null&&t.flagId.isNotBlank()&&item?.category=="special"&&item.originalId==11&&item.maxCount==1
        })
        val extraCharacters=data.optJSONArray("additionalCharacters")?.let{a->(0 until a.length()).map{i->
            val c=a.getJSONObject(i);val state=CharacterState.parse(c.getJSONObject("initialState"))
            require(state.id!=initialPlayer.id&&c.getString("evidence").isNotBlank())
            require(c.getInt("originalActorIndex") in 1..3)
            val name=c.getString("name");require(name.isNotBlank()&&name.length<=32)
            val asset=checkedName(c.getString("portraitAsset"))
            state to CharacterDefinition(state.id,name,asset,bitmap(asset,16,16),c.getString("confidence"),
                if(state.equipment!=null)listOf("rightHand","leftHand","body","feet") else null).also{
                    it.originalActorIndex=c.getInt("originalActorIndex")
                }
        }}?:emptyList()
        require(extraCharacters.map{it.first.id}.distinct().size==extraCharacters.size&&extraCharacters.size<=3&&
            extraCharacters.map{it.second.originalActorIndex}.distinct().size==extraCharacters.size)
        val knownCharacters=extraCharacters.map{it.first.id}.toSet()+initialPlayer.id
        val equipmentDefinitions=(0 until itemArray.length()).mapNotNull{i->
            val o=itemArray.getJSONObject(i);val e=o.optJSONObject("equipment")?:return@mapNotNull null
            require(o.getJSONObject("source").getString("confidence") in setOf("GAMEPLAY_VERIFIED","PROVISIONAL_REFERENCE"))
            EquipmentDefinition(o.getString("id"),e.getInt("originalId"),e.getString("slot"),
                e.getInt("attackBonus"),e.getJSONArray("allowedCharacters").let{a->
                    (0 until a.length()).map{a.getString(it)}.toSet()},e.getString("evidence"),
                e.optInt("defenseBonus",0),e.optInt("evasionValue",0),e.optBoolean("operationEnabled",true))
        }.associateBy{it.itemId}
        require(equipmentDefinitions.values.all{it.originalId in 0..255 && it.slot in setOf("rightHand","body","feet") &&
            it.attackBonus>=0 && it.defenseBonus>=0 && it.allowedCharacters.isNotEmpty()&&it.allowedCharacters.all{owner->owner in knownCharacters}})
        val shops=data.optJSONArray("shops")?.let{a->(0 until a.length()).map{i->
            val o=a.getJSONObject(i)
            fun refs(n:String)=o.getJSONArray(n).let{v->(0 until v.length()).map{v.getString(it)}}
            require(o.getJSONObject("source").getString("confidence") in setOf("GAMEPLAY_VERIFIED","ORIGINAL_ROM_STATIC"))
            ShopDefinition(o.getString("id"),o.getInt("mapId"),o.getString("npcId"),o.getString("name"),refs("items"),refs("sellItems").toSet(),o.optString("buyPrompt"))
        }.associateBy{it.id}}?:emptyMap()
        val serviceBindings=data.optJSONArray("serviceBindings")?.let{a->(0 until a.length()).map{i->
            val b=a.getJSONObject(i)
            ServiceBinding(b.getInt("callerMapId"),b.getInt("interiorMapId"),b.getString("npcId"),
                b.optString("shopId").takeIf{it.isNotEmpty()},b.optString("innId").takeIf{it.isNotEmpty()})
        }}?:emptyList()
        require(serviceBindings.map{Triple(it.callerMapId,it.interiorMapId,it.npcId)}.distinct().size==serviceBindings.size)
        require(shops.values.all{s->s.mapId in scenes && npcs.any{it.id==s.npcId&&it.mapId==s.mapId&&
                (it.shopId==s.id||serviceBindings.any{b->b.npcId==it.id&&b.interiorMapId==it.mapId&&b.shopId==s.id})} &&
            s.items.all{itemDefinitions[it]?.buyPrice!=null} && s.sellItems.all{itemDefinitions[it]?.sellPrice!=null}})
        val innRows=data.optJSONArray("inns")?.let{a->(0 until a.length()).map{i->
            val o=a.getJSONObject(i)
            require(o.getString("confidence") in setOf("GAMEPLAY_VERIFIED","VERIFIED","ORIGINAL_ROM_STATIC"))
            InnDefinition(o.getString("id"),o.getInt("mapId"),o.getString("npcId"),o.getString("name"),
                o.getInt("price"),o.getInt("blockedStatusMask"),o.getString("prompt"),o.getString("evidence"))
        }}?:emptyList()
        require(innRows.map{it.id}.toSet().size==innRows.size)
        val inns=innRows.associateBy{it.id}
        require(inns.values.all{s->s.mapId in scenes && s.price in 0..9999999 && s.blockedStatusMask in 0..255 &&
            s.evidence.isNotBlank() && npcs.any{it.id==s.npcId&&it.mapId==s.mapId&&
                (it.innId==s.id||serviceBindings.any{b->b.npcId==it.id&&b.interiorMapId==it.mapId&&b.innId==s.id})}})
        require(serviceBindings.all{b->b.callerMapId in scenes&&b.interiorMapId in scenes&&
            npcs.any{it.id==b.npcId&&it.mapId==b.interiorMapId}&&((b.shopId!=null) xor (b.innId!=null))&&
            (b.shopId==null||shops[b.shopId]?.let{it.mapId==b.interiorMapId&&it.npcId==b.npcId}==true)&&
            (b.innId==null||inns[b.innId]?.let{it.mapId==b.interiorMapId&&it.npcId==b.npcId}==true)})
        require(npcs.all{(it.shopId==null||it.shopId in shops)&&(it.innId==null||it.innId in inns)&&
            !(it.shopId!=null&&it.innId!=null)})
        val sprites=mapOf(Key.UP to bitmap("player-up.png",16,16),
            Key.DOWN to bitmap("player-down.png",16,16),Key.LEFT to bitmap("player-left.png",16,16),Key.RIGHT to bitmap("player-right.png",16,16))
        val portraitAsset=initial.optString("portraitAsset","player-down.png")
        require(portraitAsset=="player-down.png" && (initial.has("portraitSource") || !initial.has("portraitAsset")))
        val definition=CharacterDefinition(initialPlayer.id,initialName,portraitAsset,sprites.getValue(Key.DOWN),
            initial.getJSONObject("source").getString("confidence"),
            if(initialPlayer.equipment!=null)listOf("rightHand","leftHand","body","feet") else null)
        val battle=if(hashes.has("combat.json")){
            val o=JSONObject(String(read("combat.json"),Charsets.UTF_8))
            require(o.getInt("schemaVersion")==1 && o.getString("version")==manifest.getString("version"))
            val zone=o.getJSONObject("zone");require(zone.getInt("mapId") in scenes && zone.getInt("id") in 0..255)
            val rects=zone.getJSONArray("rectangles").let{array->(0 until array.length()).map{i->
                val a=array.getJSONArray(i);require(a.length()==4)
                EncounterRect(a.getInt(0),a.getInt(1),a.getInt(2),a.getInt(3))
            }}
            val enemyRows=o.getJSONArray("enemies").let{array->(0 until array.length()).map{i->
                val e=array.getJSONObject(i)
                EnemyDefinition(e.getInt("id"),e.getString("name"),e.getInt("hp"),e.getInt("attack"),
                    e.getInt("defense"),e.getInt("experienceReward"),e.getInt("moneyReward"),
                    e.getInt("hitByte"),e.getInt("behaviorByte"),
                    e.optInt("iceBaseDamage").takeIf{e.has("iceBaseDamage")},
                    e.optJSONObject("loot")?.let{l->BattleLoot(l.getString("itemId"),l.getInt("threshold"),l.getString("category"))
                        .also{require(it.itemId in itemDefinitions&&it.threshold in 0..128&&
                            itemDefinitions.getValue(it.itemId).category==it.category)}})
            }}
            require(enemyRows.size in 1..256&&enemyRows.map{it.id}.distinct().size==enemyRows.size&&enemyRows.all{
                it.id in 0..255&&it.name.isNotBlank()&&it.hp in 1..65535&&it.attack in 0..65535&&it.defense in 0..65535&&
                    it.experienceReward in 0..65535&&it.moneyReward in 0..65535})
            val enemies=enemyRows.associateBy{it.id}
            val groups=o.getJSONArray("groups").let{array->(0 until array.length()).map{i->
                val g=array.getJSONObject(i);val members=g.getJSONArray("entities").let{a->
                    (0 until a.length()).map{j->val e=a.getJSONObject(j);EncounterMember(e.getInt("slot"),e.getInt("enemyId"))}}
                EncounterGroup(g.getInt("id"),members)
            }}
            val growth=o.getJSONArray("nezhaGrowth").let{array->(0 until array.length()).map{i->
                val g=array.getJSONObject(i);GrowthRow(g.getInt("level"),g.getInt("threshold"),
                    g.getInt("hp"),g.getInt("mp"),g.getInt("strength"),g.getInt("stamina"),
                    g.getInt("agility"),g.getInt("spirit"),g.getBoolean("runtimeVerified"))
            }}
            val gate=o.getJSONObject("gate")
            require(rects.isNotEmpty() && enemies.isNotEmpty() && groups.size in 1..32 &&
                groups.indices.all{groups[it].id==it} && groups.all{validEncounterGroup(it,enemies)} &&
                enemies.values.all{it.hp>0 && it.hitByte in 0..255 && OriginalStatus.enemySupported(it)} &&
                growth.map{it.level}.distinct().size==growth.size&&growth.all{it.level in 2..99&&it.threshold in 1..0xffffff}&&
                growth.zipWithNext().all{it.first.threshold<it.second.threshold})
            fun parseZones(name:String):List<EncounterZone> = o.optJSONArray(name)?.let{a->(0 until a.length()).map{i->
                    val z=a.getJSONObject(i);val zr=z.getJSONArray("rectangles").let{rs->(0 until rs.length()).map{j->
                        val r=rs.getJSONArray(j);require(r.length()==4)
                        EncounterRect(r.getInt(0),r.getInt(1),r.getInt(2),r.getInt(3))}}
                    val zg=z.getJSONArray("groups").let{gs->(0 until gs.length()).map{j->
                        val g=gs.getJSONObject(j);val entities=g.getJSONArray("entities").let{es->(0 until es.length()).map{k->
                            val e=es.getJSONObject(k);EncounterMember(e.getInt("slot"),e.getInt("enemyId"))}}
                        require(validEncounterGroup(EncounterGroup(g.getInt("id"),entities),enemies))
                        EncounterGroup(g.getInt("id"),entities,z.getInt("id"))}}
                    require(z.getInt("mapId") in scenes&&zg.size in 1..32&&zg.indices.all{zg[it].id==it}&&z.getInt("randomThreshold") in 0..255)
                    EncounterZone(z.getInt("mapId"),zr,zg,z.getInt("randomThreshold"),z.optString("randomGate")=="HIGH")
                }}?:emptyList()
            val parsedZones=parseZones("zones")+parseZones("fallbackZones")
            val enemyAgility=o.optJSONObject("escape")?.getJSONObject("enemyAgility")?.let{a->
                a.keys().asSequence().associate{it.toInt() to a.getInt(it)}}?:emptyMap()
            if(o.has("physicalRules")||o.optJSONObject("escape")?.optBoolean("enabled")==true)
                require(enemyAgility.keys.containsAll(enemies.keys)&&enemyAgility.all{(id,value)->id in enemies&&value in 0..255})
            BattleContent(zone.getInt("mapId"),rects,groups,enemies,growth,
                o.getInt("initialArmorContribution"),gate.getInt("stepCounterMin"),
                gate.getInt("stepCounterForced"),gate.getInt("randomByteThreshold"),
                enemyAgility,
                o.optJSONObject("escape")?.optBoolean("enabled")==true,
                o.optJSONObject("defeat")?.optBoolean("enabled")==true,
                parsedZones,
                o.optJSONObject("physicalRules")?.let{p->
                    require(p.getBoolean("sameRandomByte"))
                    val h=p.getJSONObject("weaponHitThreshold");val t=ints(p,"multiplierThresholds").toList()
                    PhysicalRules(h.keys().asSequence().associate{it.toInt() to h.getInt(it)},t)
                },
                o.optJSONArray("bosses")?.let{a->(0 until a.length()).map{i->
                    val b=a.getJSONObject(i);val g=b.getJSONObject("group");val es=g.getJSONArray("entities")
                    val members=(0 until es.length()).map{j->val m=es.getJSONObject(j);EncounterMember(m.getInt("slot"),m.getInt("enemyId"))}
                    StoryBattleDefinition(b.getString("id"),b.getString("npcId"),b.getString("flagId"),
                        EncounterGroup(g.getInt("id"),members),b.getString("victoryDialogue")).also{boss->
                        boss.entryTrigger=b.optJSONObject("entryTrigger")?.let{t->
                            require(t.getString("evidence").isNotBlank())
                            val mid=t.getInt("mapId");val x=t.getInt("x");val y=t.getInt("y")
                            require(mid in scenes&&x in 0 until scenes.getValue(mid).width&&y in 0 until scenes.getValue(mid).height)
                            StoryEntryTrigger(mid,x,y)
                        }
                        boss.commitAfterDialogue=b.optBoolean("commitAfterDialogue",false)
                        boss.continuation=b.optJSONObject("continuation")?.let{c->
                            require(c.getString("evidence").isNotBlank())
                            val ids=c.getJSONArray("dialogueIds").let{v->(0 until v.length()).map{v.getString(it)}}
                            val joins=c.optString("joinCharacterId").takeIf{it.isNotEmpty()}
                            require(ids.firstOrNull()==boss.victoryDialogue&&ids.all{it in dialogues}&&
                                (joins==null||extraCharacters.any{it.first.id==joins}))
                            val target=c.optJSONObject("destination")?.let{t->
                                val d=StoryDestination(t.getInt("mapId"),t.getInt("x"),t.getInt("y"),if(t.has("direction"))Key.valueOf(t.getString("direction")) else null,
                                    if(t.has("terrainMode"))t.getInt("terrainMode") else null,if(t.has("encounterSteps"))t.getInt("encounterSteps") else null)
                                require(scenes[d.mapId]?.check(d.x,d.y,d.terrainMode?:0)==null&&d.mapId in scenes)
                                d
                            }
                            val fs=c.getJSONArray("completionFlags").let{v->(0 until v.length()).map{v.getString(it)}.toSet()}
                            StoryContinuation(ids,joins,target,fs)
                        }
                        require(!boss.commitAfterDialogue||boss.entryTrigger!=null)
                        require(boss.id.matches(Regex("rom\\.boss\\.\\d+"))&&(boss.flagId.matches(Regex("rom\\.event\\.\\d+\\.\\d+\\.\\d+"))||boss.flagId.matches(Regex("rom\\.map\\.\\d+\\.flag\\.\\d+")))&&
                            boss.victoryDialogue in dialogues&&validEncounterGroup(boss.group,enemies)&&
                            npcs.any{it.id==boss.npcId}&&b.getString("source").isNotBlank())}
                }.associateBy{it.npcId}}?:emptyMap(),
                o.optJSONObject("growthLimit")?.let{limit->
                    require(limit.getString("owner")=="nezha"&&limit.getString("confidence")=="ORIGINAL_ROM_STATIC"&&
                        limit.getString("evidence").isNotBlank())
                    limit.getInt("level").also{level->require(level in 2..99&&growth.lastOrNull()?.level==level)}
                }).also{rules->
                    o.optJSONObject("physicalRules")?.optJSONObject("characterMultiplierThresholds")?.let{tables->
                        val base=rules.physicalRules?:error("Missing common physical rules")
                        rules.characterPhysicalRules=tables.keys().asSequence().associate{owner->
                            require(owner in knownCharacters&&owner!=initialPlayer.id)
                            owner to PhysicalRules(base.weaponHitThreshold,ints(tables,owner).toList())
                        }
                    }
                    o.optJSONArray("characterGrowth")?.let{a->
                        val seen=mutableSetOf<String>();val rows=mutableMapOf<String,List<GrowthRow>>();val limits=mutableMapOf<String,Int>()
                        for(i in 0 until a.length()){
                            val g=a.getJSONObject(i);val owner=g.getString("owner")
                            require(owner in knownCharacters&&owner!=initialPlayer.id&&seen.add(owner)&&g.getString("evidence").isNotBlank())
                            val table=g.getJSONArray("rows")
                            val values=(0 until table.length()).map{j->
                                val r=table.getJSONObject(j)
                                GrowthRow(r.getInt("level"),r.getInt("threshold"),r.getInt("hp"),r.getInt("mp"),
                                    r.getInt("strength"),r.getInt("stamina"),r.getInt("agility"),r.getInt("spirit"),r.getBoolean("runtimeVerified"))}
                            require(values.isNotEmpty()&&values.map{it.level}.distinct().size==values.size&&
                                values.all{it.level in 2..99&&it.threshold in 1..0xffffff&&
                                    listOf(it.hp,it.mp,it.strength,it.stamina,it.agility,it.spirit).all{v->v in 0..255}}&&
                                values.zipWithNext().all{it.second.level==it.first.level+1&&it.second.threshold>it.first.threshold})
                            rows[owner]=values
                            if(g.has("knownMaxLevel")){
                                require(g.getString("limitEvidence").isNotBlank())
                                limits[owner]=g.getInt("knownMaxLevel").also{require(it==values.last().level)}
                            }
                        }
                        rules.characterGrowth=rows;rules.characterLevelLimits=limits
                    }
                }
        }else null
        require(npcs.filter{it.scriptedActor}.all{npc->
            battle?.storyBattles?.get(npc.id)?.entryTrigger?.mapId==npc.mapId})
        val battleHorizons=mutableMapOf<Int,Bitmap>();val blackBattleEnemyIds=mutableSetOf<Int>()
        val enemyGraphics=mutableMapOf<Int,Bitmap>();val enemyOrigins=mutableMapOf<Int,Pair<Int,Int>>()
        var battleHorizon:Bitmap?=null;var battleHero:Bitmap?=null
        if(battle!=null){
            val presentation=JSONObject(String(read("combat.json"),Charsets.UTF_8)).optJSONObject("presentation")
            if(presentation!=null){
                val assets=presentation.getJSONArray("graphics")
                for(i in 0 until assets.length()){
                    val a=assets.getJSONObject(i);val bytes=read(a.getString("asset"))
                    val image=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:error("Invalid enemy graphic")
                    require(image.width==a.getInt("width")&&image.height==a.getInt("height"))
                    val enemyId=a.getInt("enemyId");enemyGraphics[enemyId]=image
                    a.optJSONArray("origin")?.let{origin->
                        require(origin.length()==2);val x=origin.getInt(0);val y=origin.getInt(1)
                        require(x>=0&&y>=0&&x+image.width<=256&&y+image.height<=148){"Enemy origin overlaps battle controls"}
                        enemyOrigins[enemyId]=x to y
                    }
                }
                require(enemyGraphics.keys==battle.enemies.keys)
                val bytes=read(presentation.getString("horizon"))
                battleHorizon=BitmapFactory.decodeByteArray(bytes,0,bytes.size)?:error("Invalid battle horizon")
                presentation.optJSONArray("horizons")?.let{a->for(i in 0 until a.length()){
                    val h=a.getJSONObject(i);val mid=h.getInt("mapId");require(mid in scenes)
                    battleHorizons[mid]=bitmap(h.getString("asset"),256,32)
                }}
                presentation.optJSONArray("blackBackgroundEnemyIds")?.let{a->for(i in 0 until a.length()){
                    val id=a.getInt(i);require(id in battle.enemies);blackBattleEnemyIds.add(id)
                }}
                if(presentation.has("hero")){
                    val heroBytes=read(presentation.getString("hero"))
                    battleHero=BitmapFactory.decodeByteArray(heroBytes,0,heroBytes.size)?:error("Invalid battle actor")
                }
            }
        }
        Diagnostics.contentVersion=manifest.getString("version")
        Diagnostics.contentHash=MessageDigest.getInstance("SHA-256").digest(manifestBytes).joinToString(""){"%02x".format(it)}
        val parseMs=(SystemClock.elapsedRealtime()-started-verificationMs-atlasMs).coerceAtLeast(0)
        val audioStart=SystemClock.elapsedRealtime()
        val nonAudioVerificationMs=verificationMs
        val audio=if(audioCache==null||!hashes.has("audio.json"))null else runCatching{
            val o=JSONObject(String(read("audio.json"),Charsets.UTF_8));require(o.getInt("schemaVersion")==1)
            audioCache.mkdirs();val tracks=mutableMapOf<String,AudioTrack>();val effects=mutableMapOf<String,File>()
            val a=o.getJSONArray("assets")
            for(i in 0 until a.length()){
                val asset=a.getJSONObject(i);val id=asset.getString("id");val name=checkedName(asset.getString("file"))
                runCatching{
                    val bytes=read(name);val file=File(audioCache,hashes.getString(name)+"-"+name)
                    if(!file.exists()||!file.readBytes().contentEquals(bytes)){val pending=File(audioCache,file.name+".pending");pending.writeBytes(bytes);check(pending.renameTo(file))}
                    if(asset.getString("kind")=="BGM"){val start=asset.getLong("loopStartMs");val end=asset.getLong("loopEndMs");require(start>=0&&end>start)
                        tracks[id]=AudioTrack(id,file,start,end,asset.getBoolean("loop"))}else effects[id]=file
                    Diagnostics.record("audio_load",details=JSONObject().put("success",true).put("assetID",id))
                }.onFailure{Diagnostics.record("audio_load","WARN",JSONObject().put("success",false).put("assetID",id),"missing_or_corrupt_audio")}
            }
            fun refs(name:String):Map<String,String>{val refs=o.getJSONObject(name);return refs.keys().asSequence().associateWith{refs.getString(it)}}
            AudioContent(tracks,refs("maps").mapKeys{it.key.toInt()},refs("events"),effects)
        }.onFailure{Diagnostics.record("audio_load","WARN",code="invalid_audio_manifest")}.getOrNull()
        timing(JSONObject().put("verificationMs",nonAudioVerificationMs).put("atlasMs",atlasMs).put("parseMs",parseMs).put("audioMs",SystemClock.elapsedRealtime()-audioStart))
        return Content(opening,atlas114,sprites,
            scenes,atlases,exits,initialPlayer,
            data.getInt("initialMoney"),intro,npcs,dialogues,itemDefinitions.mapValues{it.value.name},
            mapOf(initialPlayer.id to initialName)+extraCharacters.associate{it.first.id to it.second.name},
            mapOf(definition.id to definition)+extraCharacters.associate{it.first.id to it.second},itemDefinitions,equipmentDefinitions,battle,audio,
            enemyGraphics,battleHorizon,battleHero,shops,mapObjects,battleHorizons,blackBattleEnemyIds,enemyOrigins,inns,serviceBindings).also{content->
                content.joinCharacters=extraCharacters.associate{it.first.id to it.first}
                data.optJSONArray("mechanisms")?.let{a->
                    content.mechanisms=(0 until a.length()).map{i->
                        val o=a.getJSONObject(i);val cells=o.getJSONArray("changes")
                        require(o.getString("evidence").isNotBlank())
                        val mechanism=SceneMechanism(o.getString("id"),o.getInt("mapId"),o.getInt("x"),o.getInt("y"),
                            o.getString("sessionFlag"),(0 until cells.length()).map{j->
                                val c=cells.getJSONObject(j)
                                SceneCellChange(c.getInt("x"),c.getInt("y"),c.getInt("fromTile"),c.getInt("toTile"),
                                    c.getInt("fromCollision"),c.getInt("toCollision"))})
                        val base=scenes.getValue(mechanism.mapId)
                        require(base.check(mechanism.x,mechanism.y)==null)
                        mechanism.apply(base,mapOf(mechanism.sessionFlag to true)) // Reject mismatched geometry before runtime.
                        mechanism
                    }
                    require(content.mechanisms.map{it.id}.distinct().size==content.mechanisms.size)
                    require(content.mechanisms.map{it.sessionFlag}.distinct().size==content.mechanisms.size)
                }
            }
    }
}
