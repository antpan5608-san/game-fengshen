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
    var interactionDirection:Key?=null;internal set
    var clinicId:String?=null;internal set
    var originalTalk:OriginalNpcTalkDefinition?=null;internal set
    var worldItemTarget:WorldObjectTarget?=null;internal set
    var automaticStoryOnly:Boolean=false;internal set
    var removedFlagId:String?=null;internal set
    var visibleFlagId:String?=null;internal set
    var moneyTreasure:MoneyTreasureDefinition?=null;internal set
    var stateVariant:NpcStateVariant?=null;internal set
    var hiddenInvestigation:Boolean=false;internal set
}
data class NpcStateVariant(val flagId:String,val x:Int,val y:Int,val firstDialogue:String,val repeatDialogue:String) {
    var sprite:Bitmap?=null;internal set
    fun activeIn(flags:Map<String,Boolean>)=if(flagId=="rom.npccontext.145.215")
        OriginalNpcTalk.hasHuangJiamengContext(flags) else flags[flagId]==true
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
    val worldUse:WorldItemUseDefinition?=null) {
    // Body property preserves the published cross-APK constructor signature.
    var fieldProtectionUse:WorldFieldProtectionDefinition?=null;internal set
    var nightLightUse:WorldFieldProtectionDefinition?=null;internal set
    var battleBindingUse:BattleBindingUseDefinition?=null;internal set
}
data class BattleBindingUseDefinition(val evidence:String) {
    // Body properties preserve the existing cross-APK constructor ABI.
    var bindingMarker:Int=1;internal set
    var targetLabel:String="四恶人";internal set
}
data class AntidoteUseDefinition(val evidence:String)
data class HerbUseDefinition(val healHp:Int,val consumeAtFullHp:Boolean,val evidence:String)
data class EquipmentDefinition(val itemId:String,val originalId:Int,val slot:String,val attackBonus:Int,
    val allowedCharacters:Set<String>,val source:String,val defenseBonus:Int=0,val evasionValue:Int=0,
    val operationEnabled:Boolean=true)

data class ShopDefinition(val id:String,val mapId:Int,val npcId:String,val name:String,
    val items:List<String>,val sellItems:Set<String>,val buyPrompt:String="")
data class InnDefinition(val id:String,val mapId:Int,val npcId:String,val name:String,
    val price:Int,val blockedStatusMask:Int,val prompt:String,val evidence:String)
data class ClinicTreatment(val id:String,val name:String,val statusMask:Int,val price:Int)
data class ClinicDefinition(val id:String,val mapId:Int,val npcId:String,val name:String,
    val evidence:String,val deadMask:Int=32,val recoveredHp:Int=1,val recoveredStatus:Int=1,
    val feeDenominator:Int=100,val minimumFee:Int=1,val moneyLimit:Int=999999,
    val kind:String="REVIVAL",val treatments:List<ClinicTreatment> = emptyList())
data class ServiceBinding(val callerMapId:Int,val interiorMapId:Int,val npcId:String,
    val shopId:String?=null,val innId:String?=null) {
    var clinicId:String?=null;internal set
}
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
    var clinics:Map<String,ClinicDefinition> = emptyMap();internal set
    var ferries:Map<String,FerryDefinition> = emptyMap();internal set
    var ferrySprites:Map<String,Bitmap> = emptyMap();internal set
    var nightLightAtlas:(()->Bitmap)?=null;internal set
    fun atlasForState(mapId:Int,flags:Map<String,Boolean>):Bitmap =
        if(mapId==74&&flags[WorldItems.NIGHT_LIGHT_FLAG]==true)nightLightAtlas?.invoke()
            ?:error("Verified cave light atlas unavailable") else atlases.getValue(mapId)
    // One state-dependent scene view. Arrays and atlases stay in the existing
    // bounded loader; this never holds every visited map alive.
    var joinCharacters:Map<String,CharacterState> = emptyMap()
        internal set
    var sceneBarriers:List<SceneBarrier> = emptyList()
        internal set
    var mechanisms:List<SceneMechanism> = emptyList()
        internal set
    var sceneStories:Map<String,SceneStoryDefinition> = emptyMap()
        internal set
    private var stateScene:Scene?=null
    private var stateFlags:Map<String,Boolean>?=null
    fun worldItemTargets()=mapObjects.mapNotNull{it.itemTarget}+npcs.mapNotNull{it.worldItemTarget}
    fun yangJoin()=itemDefinitions[OriginalYangJoin.ITEM_ID]?.worldUse?.yangJoin
    fun npcsForState(mapId:Int,flags:Map<String,Boolean>)=npcs.filter{it.mapId==mapId}.map{npc->
        val v=npc.stateVariant
        if(v!=null&&v.activeIn(flags))npc.copy(x=v.x,y=v.y,sprite=v.sprite?:npc.sprite,
            firstDialogue=v.firstDialogue,repeatDialogue=v.repeatDialogue).also{n->
            n.scriptedActor=npc.scriptedActor;n.interactionDirection=npc.interactionDirection;n.clinicId=npc.clinicId
            n.originalTalk=npc.originalTalk;n.worldItemTarget=npc.worldItemTarget;n.automaticStoryOnly=npc.automaticStoryOnly
            n.removedFlagId=npc.removedFlagId;n.moneyTreasure=npc.moneyTreasure;n.stateVariant=npc.stateVariant
            n.visibleFlagId=npc.visibleFlagId
            n.hiddenInvestigation=npc.hiddenInvestigation
        }else npc
    }
    fun npcVisible(npc:StoryNpc,flags:Map<String,Boolean>)=
        (npc.visibleFlagId?.let{flags[it]==true}?:true)&&
        (npc.removedFlagId?.let{flags[it]!=true}?:npc.worldItemTarget?.let{flags[it.removedFlagId]!=true}?:true)
    // Original context196 actor130 has FF/FF messages and no talk action.
    // Keep its visible collision without inventing an empty conversation.
    fun npcInteractive(npc:StoryNpc)=!npc.scriptedActor&&!npc.automaticStoryOnly&&npc.id!="rom.npc.37.yang-bed"
    @Synchronized fun sceneForState(mapId:Int,flags:Map<String,Boolean>):Scene? {
        if(stateScene?.mapId==mapId&&stateFlags===flags)return stateScene
        val base=scenes[mapId]?:return null
        val removed=worldItemTargets().filter{it.mapId==mapId&&flags[it.removedFlagId]==true}
            .map{it.y*base.width+it.x}.toSet()+npcs.filter{it.mapId==mapId&&!npcVisible(it,flags)}
            .map{it.y*base.width+it.x}.toSet()
        var result=if(removed.isEmpty())base else base.copy(dynamicObjectCells=base.dynamicObjectCells-removed)
        for(npc in npcs.filter{it.mapId==mapId})npc.stateVariant?.takeIf{it.activeIn(flags)&&npcVisible(npc,flags)}?.let{v->
            result=result.copy(dynamicObjectCells=(result.dynamicObjectCells-(npc.y*base.width+npc.x))+(v.y*base.width+v.x))
        }
        for(barrier in sceneBarriers)result=barrier.apply(result,flags)
        for(mechanism in mechanisms)result=mechanism.apply(result,flags)
        result=OriginalFerry.sceneView(result,flags,ferries.values)
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
        val sceneBarriers=data.optJSONArray("sceneBarriers")?.let{a->(0 until a.length()).map{i->
            val b=a.getJSONObject(i);require(b.getString("evidence").isNotBlank())
            val cell=ints(b,"cell");require(cell.size==2)
            SceneBarrier(b.getString("id"),b.getInt("mapId"),cell[0],cell[1],b.getString("removedFlagId")).also{rule->
                val base=scenes.getValue(rule.mapId)
                require(rule.x<base.width&&rule.y<base.height&&rule.y*base.width+rule.x in base.dynamicObjectCells)
            }
        }}?:emptyList()
        require(sceneBarriers.map{it.id}.distinct().size==sceneBarriers.size)
        val exits=data.getJSONArray("exits").let{a->(0 until a.length()).map{i->
            val o=a.getJSONObject(i);val trigger=ints(o,"trigger");val spawn=ints(o,"spawn")
            require(trigger.size==2&&spawn.size==2&&o.getString("confidence")=="VERIFIED")
            val mode=o.optString("triggerMode","CELL");require(mode in setOf("CELL","EDGE","ACTOR_CONTACT"))
            val direction=if(mode=="EDGE")Key.valueOf(o.getString("direction")) else null
            require(direction==null || direction in setOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT))
            val arrival=Key.valueOf(o.optString("arrivalDirection","DOWN"))
            require(arrival in setOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT))
            MapExit(o.getInt("fromMapId"),trigger[0],trigger[1],o.getInt("toMapId"),spawn[0],spawn[1],direction,arrival,o.optBoolean("resetEncounterSteps",false),o.optBoolean("captureCaller",false),o.optBoolean("returnToCaller",false)).also{
                it.preserveArrivalDirection=o.optBoolean("preserveArrivalDirection",false)
                if(mode=="ACTOR_CONTACT"){
                    val actor=o.getInt("contactActorId")
                    require(o.getString("evidence")=="game-data/provenance/world-tree-contact.json"&&
                        it.fromMapId==16&&it.toMapId==107&&actor in setOf(231,232)&&
                        it.triggerX==170&&it.triggerY==if(actor==231)148 else 149)
                    require(it.spawnX==7&&it.spawnY==14&&it.preserveArrivalDirection)
                    require(sceneBarriers.any{b->b.id=="rom.barrier.16.$actor"&&b.x==it.triggerX&&b.y==it.triggerY})
                    it.contactActorId=actor
                }
            }
        }}
        for(exit in exits){
            val from=scenes[exit.fromMapId];val to=scenes[exit.toMapId]
            require(from!=null && to!=null && validExitPlacement(from,exit.triggerX,exit.triggerY,sceneBarriers) &&
                validExitPlacement(to,exit.spawnX,exit.spawnY,sceneBarriers)
            ) {"Invalid exit geometry ${exit.fromMapId}(${exit.triggerX},${exit.triggerY}) -> ${exit.toMapId}(${exit.spawnX},${exit.spawnY}) after reviewed removable objects"}
        }
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
                    TreasureDefinition(t.getString("itemId"),t.getString("flagId"),t.getInt("amount")).also{treasure->
                        if(t.has("categoryGrant")){
                            require(WorldItems.categoryGrantEvidenceSupported(t.getString("evidence"),mapId,n.getString("id"),t.getInt("categoryGrant")))
                            treasure.categoryGrant=t.getInt("categoryGrant").also{require(it in 0..3)}
                        }
                    }
                },n.optString("openedSprite").takeIf{it.isNotEmpty()}?.let{bitmap(it,16,16)}).also{npc->
                npc.scriptedActor=n.optBoolean("scriptedActor",false)
                npc.hiddenInvestigation=n.optBoolean("hiddenInvestigation",false)
                if(npc.hiddenInvestigation){
                    val queen=npc.id=="rom.npc.115.5"&&npc.mapId==115&&npc.x==41&&npc.y==7&&
                        npc.treasure?.flagId=="rom.map.115.flag.2"&&npc.treasure?.itemId=="rom.weapon.23"&&
                        npc.treasure.categoryGrant==2&&n.getString("queen117ResourceEvidence")==OriginalNpcTalk.HUANG_EVIDENCE
                    val old=((npc.id=="rom.npc.5.5"&&npc.mapId==5&&npc.x==15&&npc.y==7&&
                    npc.treasure?.flagId=="rom.map.5.flag.1")||
                    (npc.id=="rom.npc.6.3"&&npc.mapId==6&&npc.x==18&&npc.y==5&&npc.treasure?.flagId=="rom.map.6.flag.8"))&&
                    npc.treasure?.itemId=="rom.medicine.1"&&
                    npc.treasure.categoryGrant==0&&npc.firstDialogue.isEmpty()&&npc.repeatDialogue==null&&
                    npc.firstEffects.isEmpty()&&npc.openedSprite!=null
                    require((old||queen)&&npc.firstDialogue.isEmpty()&&npc.repeatDialogue==null&&npc.firstEffects.isEmpty()&&npc.openedSprite!=null)
                }
                npc.automaticStoryOnly=n.optBoolean("automaticStoryOnly",false)
                npc.removedFlagId=n.optString("removedFlagId").takeIf{it.isNotEmpty()}
                npc.visibleFlagId=n.optString("visibleFlagId").takeIf{it.isNotEmpty()}
                if(npc.visibleFlagId!=null)require(npc.id=="rom.npc.37.yang-bed"&&npc.mapId==37&&npc.x==3&&npc.y==5&&
                    npc.visibleFlagId=="rom.npccontext.37.196"&&npc.removedFlagId==null&&npc.firstDialogue.isEmpty()&&
                    npc.repeatDialogue==null&&npc.firstEffects.isEmpty()&&
                    n.getString("jiamengResourceEvidence")=="game-data/provenance/world-jiameng-actors.json")
                if(npc.automaticStoryOnly||npc.removedFlagId!=null){
                    val island=npc.mapId==76&&npc.id in (0..3).map{"rom.npc.76.$it"}&&
                        npc.removedFlagId=="rom.map.76.flag.128"&&
                        n.getString("automaticStoryEvidence")=="game-data/provenance/world-island-event7.json"
                    val cave=npc.mapId==87&&npc.id=="rom.npc.87.0"&&npc.x==5&&npc.y==4&&
                        npc.removedFlagId=="rom.map.87.flag.128"&&
                        n.getString("automaticStoryEvidence")=="game-data/provenance/world-cave87-state.json"
                    val queenEvidence=n.optString("automaticStoryEvidence")==OriginalNpcTalk.HUANG_EVIDENCE
                    val queen=queenEvidence&&npc.mapId==117&&npc.id=="rom.npc.117.1"&&npc.x==7&&npc.y==4&&
                        npc.removedFlagId=="rom.map.117.flag.128"&&npc.automaticStoryOnly
                    val huang=queenEvidence&&npc.mapId==117&&npc.id=="rom.npc.117.0"&&npc.x==7&&npc.y==3&&
                        npc.removedFlagId==OriginalNpcTalk.HUANG_COMPLETED_FLAG&&!npc.automaticStoryOnly
                    val women=queenEvidence&&npc.mapId==115&&npc.id in (0..4).map{"rom.npc.115.$it"}&&
                        npc.removedFlagId=="rom.npccontext.115.208"&&!npc.automaticStoryOnly
                    val jail=npc.mapId==116&&npc.id in (0..5).map{"rom.npc.116.$it"}&&!npc.automaticStoryOnly&&
                        npc.removedFlagId=="rom.npccontext.116.210"&&n.optString("automaticStoryEvidence")==OriginalNpcTalk.ROOM116_EVIDENCE
                    val jiameng=n.optString("automaticStoryEvidence")=="game-data/provenance/world-jiameng-actors.json"&&when(npc.mapId){
                        145->npc.id=="rom.npc.145.0"&&npc.x==4&&npc.y==10&&!npc.automaticStoryOnly&&npc.removedFlagId=="rom.npccontext.145.228"
                        146->npc.id=="rom.npc.146.0"&&npc.x==2&&npc.y==4&&!npc.automaticStoryOnly&&npc.removedFlagId=="rom.npccontext.146.216"
                        148->npc.id in (0..2).map{"rom.npc.148.$it"}&&npc.x==7+npc.id.substringAfterLast('.').toInt()&&npc.y==5&&
                            npc.automaticStoryOnly&&npc.removedFlagId=="rom.npccontext.148.217"
                        else->false}
                    require(((island||cave)&&npc.automaticStoryOnly||queen||huang||women||jail||jiameng)&&npc.firstEffects.isEmpty())
                }
                npc.clinicId=n.optString("clinicId").takeIf{it.isNotEmpty()}
                n.optJSONObject("moneyTreasure")?.let{t->
                    val island=npc.id=="rom.npc.76.6"&&npc.mapId==76&&npc.x==2&&npc.y==5&&
                        t.getString("flagId")=="rom.map.76.flag.4"&&t.getInt("amount")==100&&
                        t.getString("evidence")=="game-data/provenance/world-island-chests.json"
                    val cave=npc.id=="rom.npc.87.4"&&npc.mapId==87&&npc.x==8&&npc.y==4&&
                        t.getString("flagId")=="rom.map.87.flag.8"&&t.getInt("amount")==550&&
                        t.getString("evidence")=="game-data/provenance/world-cave87-chests.json"
                    val dark=npc.id=="rom.npc.74.4"&&npc.mapId==74&&npc.x==40&&npc.y==18&&
                        t.getString("flagId")=="rom.map.74.flag.32"&&t.getInt("amount")==120&&
                        t.getString("evidence")=="game-data/provenance/world-night8-chests.json"
                    require((island||cave||dark)&&t.getInt("moneyCap")==999999&&npc.openedSprite!=null&&
                        npc.treasure==null&&npc.firstEffects.isEmpty())
                    npc.moneyTreasure=MoneyTreasureDefinition(t.getString("flagId"),t.getInt("amount"),t.getInt("moneyCap"),t.getString("evidence"))
                }
                n.optJSONObject("worldItemTarget")?.let{t->
                    require(npc.id=="rom.npc.110.0"&&npc.mapId==110&&npc.x==6&&npc.y==6&&
                        t.getInt("spriteId")==130&&t.getString("removedFlagId")==OriginalYangJoin.CONTEXT_FLAG&&
                        t.getString("completionFlagId")=="rom.map.110.flag.128"&&t.getString("evidence")==OriginalYangJoin.EVIDENCE)
                    npc.worldItemTarget=WorldObjectTarget(npc.id,npc.mapId,npc.x,npc.y,t.getInt("spriteId"),
                        t.getString("removedFlagId"),t.getString("completionFlagId"))
                }

                n.optJSONObject("stateVariant")?.let{v->
                    val cell=ints(v,"cell")
                    val teacher163=npc.id=="rom.npc.163.0"&&npc.mapId==163&&npc.x==7&&npc.y==10&&
                        cell.contentEquals(intArrayOf(7,9))&&v.getString("flagId")==OriginalNpcTalk.TEACHER_CONTEXT_FLAG&&
                        v.getString("firstDialogue")=="rom.dialogue.173.1"&&v.getString("repeatDialogue")=="rom.dialogue.173.1"&&
                        v.getString("evidence")=="game-data/provenance/world-teacher163-gate.json"&&
                        npc.firstEffects.isEmpty()&&!n.has("originalTalk")
                    val teacher164=npc.id=="rom.npc.164.0"&&npc.mapId==164&&npc.x==7&&npc.y==9&&
                        cell.contentEquals(intArrayOf(7,10))&&v.getString("flagId")=="rom.npccontext.164.220"&&
                        v.getString("firstDialogue")=="rom.dialogue.174.0"&&v.getString("repeatDialogue")=="rom.dialogue.174.0"&&
                        v.getString("evidence")==OriginalNpcTalk.HUANG_EVIDENCE&&npc.firstEffects.isEmpty()&&!n.has("originalTalk")
                    val jail=npc.mapId==116&&v.getString("flagId")==OriginalNpcTalk.ROOM116_COMPLETED_FLAG&&
                        v.getString("evidence")==OriginalNpcTalk.ROOM116_EVIDENCE&&npc.firstEffects.isEmpty()&&when(npc.id){
                            "rom.npc.116.0"->cell.contentEquals(intArrayOf(6,3))&&v.getString("firstDialogue")=="rom.dialogue.126.8"&&v.getString("repeatDialogue")=="rom.dialogue.126.8"
                            "rom.npc.116.3"->cell.contentEquals(intArrayOf(16,5))&&v.getString("firstDialogue")=="rom.dialogue.126.3"&&v.getString("repeatDialogue")=="rom.dialogue.126.3"
                            else->false}
                    val jiameng=npc.id=="rom.npc.145.0"&&npc.mapId==145&&npc.x==4&&npc.y==10&&
                        cell.contentEquals(intArrayOf(4,10))&&v.getString("flagId")=="rom.npccontext.145.215"&&
                        npc.firstDialogue=="rom.dialogue.155.0"&&v.getString("firstDialogue")=="rom.dialogue.155.1"&&
                        v.getString("repeatDialogue")=="rom.dialogue.155.1"&&
                        v.optString("sprite")=="npc-jiameng-154.png"&&
                        v.getString("evidence")=="game-data/provenance/world-jiameng-state.json"&&npc.firstEffects.isEmpty()
                    require(teacher163||teacher164||jail||jiameng)
                    require(!v.has("sprite")||jiameng)
                    npc.stateVariant=NpcStateVariant(v.getString("flagId"),cell[0],cell[1],v.getString("firstDialogue"),v.getString("repeatDialogue")).also{
                        if(jiameng)it.sprite=bitmap(v.getString("sprite"),16,16)
                    }
                }
                n.optJSONObject("originalTalk")?.let{t->
                    val rule=OriginalNpcTalkDefinition(npc.mapId,t.getString("mapFlagId"),t.getString("witnessFlagId"),
                        t.getString("itemId"),npc.firstDialogue,npc.repeatDialogue?:error("Original talk needs its repeat message"))
                    rule.actionId=t.getInt("actionId");require(npc.firstEffects.isEmpty())
                    when(rule.actionId){
                        58->require(t.getString("evidence")=="game-data/provenance/world-jiameng-actors.json"&&
                            npc.mapId==37&&npc.id in listOf("rom.npc.37.0","rom.npc.37.1")&&
                            npc.x==4+2*npc.id.substringAfterLast('.').toInt()&&npc.y==4&&rule.witnessFlagId.isEmpty()&&rule.itemId.isEmpty()&&
                            rule.mapFlagId=="rom.map.37.flag.${1 shl npc.id.substringAfterLast('.').toInt()}"&&
                            rule.firstDialogue=="rom.dialogue.47.${2*npc.id.substringAfterLast('.').toInt()}"&&
                            rule.repeatDialogue=="rom.dialogue.47.${2*npc.id.substringAfterLast('.').toInt()+1}")
                        1->{
                            val gift=when(npc.mapId){163->9;164->8;else->error("Unknown original teacher")}
                            val proof=if(npc.mapId==163)"world-teacher163-binding" else "world-night8-resources"
                            require(t.getString("evidence")=="game-data/provenance/$proof.json"&&
                                npc.id=="rom.npc.${npc.mapId}.1"&&rule.mapFlagId=="rom.map.${npc.mapId}.flag.2"&&
                                rule.witnessFlagId.isEmpty()&&rule.itemId=="rom.special.$gift"&&
                                rule.firstDialogue=="rom.dialogue.${npc.mapId+10}.2"&&rule.repeatDialogue=="rom.dialogue.${npc.mapId+10}.3")
                        }
                        41->require(t.getString("evidence")==OriginalNpcTalk.ROOM116_EVIDENCE&&
                            npc.id=="rom.npc.116.0"&&npc.mapId==116&&npc.x==5&&npc.y==3&&
                            rule.mapFlagId==OriginalNpcTalk.ROOM116_ACTOR_FLAG&&rule.witnessFlagId.isEmpty()&&rule.itemId.isEmpty()&&
                            rule.firstDialogue=="rom.dialogue.126.6"&&rule.repeatDialogue=="rom.dialogue.126.8")
                        43->require(t.getString("evidence")==OriginalNpcTalk.HUANG_EVIDENCE&&
                            npc.id=="rom.npc.117.0"&&npc.mapId==117&&npc.x==7&&npc.y==3&&
                            rule.mapFlagId==OriginalNpcTalk.HUANG_COMPLETED_FLAG&&rule.witnessFlagId.isEmpty()&&
                            rule.itemId=="rom.special.18"&&rule.firstDialogue=="rom.dialogue.127.14"&&
                            rule.repeatDialogue==rule.firstDialogue&&npc.removedFlagId==OriginalNpcTalk.HUANG_COMPLETED_FLAG)
                        17->require(t.getString("evidence")=="game-data/provenance/world-tree107-talk.json"&&
                            npc.id=="rom.npc.110.0"&&npc.mapId==110&&rule.mapFlagId=="rom.map.110.flag.2"&&
                            rule.witnessFlagId=="rom.global.7c8.1"&&rule.itemId=="rom.special.19"&&
                            rule.firstDialogue=="rom.dialogue.120.0"&&rule.repeatDialogue=="rom.dialogue.120.1")
                        11->require(t.getString("evidence")=="game-data/provenance/world-room171-resources.json"&&
                            npc.id=="rom.npc.171.0"&&npc.mapId==171&&rule.mapFlagId=="rom.map.171.flag.1"&&
                            rule.witnessFlagId.isEmpty()&&rule.itemId.isEmpty()&&
                            rule.firstDialogue=="rom.dialogue.181.5"&&rule.repeatDialogue=="rom.dialogue.181.6")
                        12->{
                            require(t.getString("evidence")=="game-data/provenance/world-teacher171-talk.json"&&
                                npc.id=="rom.npc.171.1"&&npc.mapId==171&&rule.mapFlagId=="rom.map.171.flag.2"&&
                                rule.witnessFlagId=="rom.global.7c8.1"&&rule.itemId=="rom.special.19"&&
                                rule.firstDialogue=="rom.dialogue.181.0"&&rule.repeatDialogue=="rom.dialogue.181.3")
                            val messages=t.getJSONObject("messageDialogues")
                            rule.messageDialogues=messages.keys().asSequence().associate{k->k.toInt() to messages.getString(k)}
                            require(rule.messageDialogues==listOf(0,1,2,3,7).associateWith{i->"rom.dialogue.181.$i"})
                            rule.completionWitnessFlagId=t.getString("completionWitnessFlagId")
                            require(rule.completionWitnessFlagId=="rom.global.7c7.128")
                        }
                        31->{
                            require(t.getString("evidence")=="game-data/provenance/world-island-talk31.json"&&
                                npc.mapId==78&&npc.id in listOf("rom.npc.78.0","rom.npc.78.1")&&
                                rule.mapFlagId=="rom.map.78.flag.${1 shl npc.id.substringAfterLast('.').toInt()}"&&
                                rule.witnessFlagId=="rom.global.7c6.16"&&rule.itemId.isEmpty()&&
                                rule.firstDialogue=="rom.dialogue.88.${npc.id.substringAfterLast('.')}"&&rule.repeatDialogue=="rom.dialogue.88.2")
                            val messages=t.getJSONObject("messageDialogues")
                            rule.messageDialogues=messages.keys().asSequence().associate{k->k.toInt() to messages.getString(k)}
                            require(rule.messageDialogues==mapOf(0 to rule.firstDialogue,2 to rule.repeatDialogue))
                        }
                        50->{
                            require(t.getString("evidence")=="game-data/provenance/world-village4-resources.json"&&
                                npc.mapId==4&&npc.id in (1..7).map{"rom.npc.4.$it"}&&
                                rule.mapFlagId=="rom.map.4.flag.${1 shl (npc.id.substringAfterLast('.').toInt()-1)}"&&
                                rule.witnessFlagId=="rom.global.7c6.16"&&rule.itemId.isEmpty())
                            val messages=t.getJSONObject("messageDialogues")
                            rule.messageDialogues=messages.keys().asSequence().associate{k->k.toInt() to messages.getString(k)}
                            require(rule.messageDialogues.keys==setOf(0,1,2)&&rule.messageDialogues[0]==rule.firstDialogue&&
                                rule.messageDialogues[2]==rule.repeatDialogue&&
                                rule.messageDialogues.values.all{it.startsWith("rom.dialogue.14.")})
                        }
                        52->{
                            val index=npc.id.substringAfterLast('.').toInt()
                            val first=mapOf(0 to 2,1 to 5,2 to 9)[index]
                            require(t.getString("evidence")=="game-data/provenance/world-village-batch-resources.json"&&
                                npc.mapId==6&&npc.id=="rom.npc.6.$index"&&first!=null&&
                                rule.mapFlagId=="rom.map.6.flag.${1 shl index}"&&rule.witnessFlagId=="rom.global.7c6.64"&&rule.itemId.isEmpty()&&
                                rule.firstDialogue=="rom.dialogue.16.$first"&&rule.repeatDialogue=="rom.dialogue.16.${first+1}")
                            val messages=t.getJSONObject("messageDialogues")
                            rule.messageDialogues=messages.keys().asSequence().associate{k->k.toInt() to messages.getString(k)}
                            require(rule.messageDialogues==mapOf(0 to rule.firstDialogue,1 to rule.repeatDialogue,2 to rule.repeatDialogue))
                        }
                        else->error("Original actor action has no scoped implementation")
                    }
                    npc.originalTalk=rule
                }
                n.optString("interactionDirection").takeIf{it.isNotEmpty()}?.let{dir->
                    require(n.getString("interactionEvidence").isNotBlank())
                    val point=npc.interactionCell?:error("Missing original interaction point")
                    require(point.first in 0 until npcScene.width&&point.second in 0 until npcScene.height)
                    npc.interactionDirection=Key.valueOf(dir).also{require(it in setOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT))}
                }
                require(!npc.scriptedActor||(npc.shopId==null&&npc.innId==null&&npc.clinicId==null&&npc.treasure==null&&effects.isEmpty()))
            }
        }
        val mapObjects=data.optJSONArray("mapObjects")?.let{a->(0 until a.length()).map{i->
            val o=a.getJSONObject(i);val cell=ints(o,"cell");val mid=o.getInt("mapId")
            require(o.getString("interaction") in setOf("NOT_IMPLEMENTED","WORLD_ITEM_TARGET","FERRY_CONTACT")&&mid in scenes&&cell.size==2&&
                cell[0] in 0 until scenes.getValue(mid).width&&cell[1] in 0 until scenes.getValue(mid).height)
            MapObject(o.getString("id"),mid,cell[0],cell[1],bitmap(o.getString("sprite"),16,16),
                if(o.getString("interaction")=="WORLD_ITEM_TARGET")o.getJSONObject("itemTarget").let{t->
                    require(t.getInt("spriteId")==226&&t.getString("evidence").isNotBlank())
                    WorldObjectTarget(o.getString("id"),mid,cell[0],cell[1],t.getInt("spriteId"),
                        t.getString("removedFlagId"),t.getString("completionFlagId"))
                }else null)
        }}?:emptyList()
        require(npcs.map{it.id}.toSet().size==npcs.size && npcs.all{(it.treasure!=null||it.moneyTreasure!=null||it.firstDialogue in dialogues) && (it.repeatDialogue==null||it.repeatDialogue in dialogues)})
        require(npcs.all{it.originalTalk?.messageDialogues?.values?.all{id->id in dialogues}!=false})
        require(npcs.all{it.stateVariant?.let{v->v.firstDialogue in dialogues&&v.repeatDialogue in dialogues}!=false})
        data.optJSONArray("mapObjects")?.let{a->for(i in 0 until a.length()){
            val o=a.getJSONObject(i)
            if(o.getString("interaction")=="FERRY_CONTACT")require(o.getString("id")=="rom.object.4.0"&&
                o.getInt("mapId")==4&&ints(o,"cell").contentEquals(intArrayOf(10,3))&&o.getInt("spriteId")==149&&
                o.getString("ferryId")=="rom.ferry.45"&&data.has("ferries"))
        }}
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
                    val id=o.getString("id");val special=o.getString("category")=="special"&&o.getInt("maxCount")==1
                    require(special&&use.getBoolean("reusable"))
                    if(id==WorldItems.ID)require(o.getInt("originalId")==11&&use.getInt("targetSpriteId")==226&&use.getString("evidence").isNotBlank())
                    else require(id==OriginalYangJoin.ITEM_ID&&o.getInt("originalId")==19&&use.getInt("targetSpriteId")==130&&
                        use.getString("usedFlagId")==OriginalYangJoin.USED_FLAG&&use.getString("evidence")==OriginalYangJoin.EVIDENCE&&
                        !o.has("buyPrice")&&!o.has("sellPrice"))
                    WorldItemUseDefinition(use.getInt("targetSpriteId"),use.getString("usedFlagId")).also{
                        if(id==OriginalYangJoin.ITEM_ID)it.yangJoin=OriginalYangJoinDefinition(use.getString("evidence"))
                    }

                })
            o.optJSONObject("fieldProtectionUse")?.let{use->
                require(item.id==WorldItems.FIELD_PROTECTION_ID&&item.category=="special"&&item.originalId==12&&
                    item.maxCount==1&&item.buyPrice==null&&item.sellPrice==null&&item.worldUse==null&&
                    item.herbUse==null&&item.antidoteUse==null&&use.getInt("mapId")==67&&
                    use.getBoolean("reusable")&&use.getString("evidence")=="game-data/provenance/world-field67-item12.json")
                item.fieldProtectionUse=WorldFieldProtectionDefinition(use.getString("evidence"))
            }
            o.optJSONObject("nightLightUse")?.let{use->
                require(item.id==WorldItems.NIGHT_LIGHT_ID&&item.category=="special"&&item.originalId==8&&item.maxCount==1&&
                    use.getInt("mapId")==74&&use.getInt("paletteSelector")==32&&use.getBoolean("reusable")&&
                    use.getString("evidence")=="game-data/provenance/world-night8-resources.json")
                item.nightLightUse=WorldFieldProtectionDefinition(use.getString("evidence"))
            }
            o.optJSONObject("battleBindingUse")?.let{use->
                val profile=requireNotNull(originalBindingProfile(item.originalId))
                require(item.id=="rom.special.${profile.itemId}"&&item.category=="special")
                require(item.maxCount==1&&
                    item.buyPrice==null&&item.sellPrice==null&&item.worldUse==null&&item.herbUse==null&&
                    use.getBoolean("reusable")&&use.getBoolean("consumesAction")&&!use.getBoolean("chooseTarget")&&
                    use.getString("target")==profile.targetKey)
                require(use.getInt("bindingMarker")==profile.marker)
                require(use.getString("evidence")==profile.itemEvidence)
                item.battleBindingUse=BattleBindingUseDefinition(use.getString("evidence")).also{
                    it.bindingMarker=profile.marker;it.targetLabel=profile.targetLabel
                }
            }
            item.id to item
        }
        require(npcs.filter{it.treasure!=null}.all{npc->
            val t=npc.treasure!!;val item=itemDefinitions[t.itemId]
            npc.openedSprite!=null&&item!=null&&WorldItems.supportsTreasure(t,item)
        }){"宝箱物品定义不符合已接入的原版类别规则"}
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
        itemDefinitions[OriginalYangJoin.ITEM_ID]?.worldUse?.yangJoin?.let{rule->
            require(npcs.single{it.id==rule.npcId}.worldItemTarget!=null&&
                rule.continuation.dialogueIds.all{it in dialogues}&&extraCharacters.any{it.first.id=="yangjian"})
        }

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
                b.optString("shopId").takeIf{it.isNotEmpty()},b.optString("innId").takeIf{it.isNotEmpty()}).also{it.clinicId=b.optString("clinicId").takeIf{it.isNotEmpty()}}
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
        val clinicRows=data.optJSONArray("clinics")?.let{a->(0 until a.length()).map{i->
            val o=a.getJSONObject(i)
            require(o.getString("confidence")=="GAMEPLAY_VERIFIED")
            ClinicDefinition(o.getString("id"),o.getInt("mapId"),o.getString("npcId"),o.getString("name"),
                o.getString("evidence"),o.getInt("deadMask"),o.getInt("recoveredHp"),o.getInt("recoveredStatus"),
                o.getInt("feeDenominator"),o.getInt("minimumFee"),o.getInt("moneyLimit"),o.getString("kind"),
                o.getJSONArray("treatments").let{v->(0 until v.length()).map{j->val t=v.getJSONObject(j)
                    ClinicTreatment(t.getString("id"),t.getString("name"),t.getInt("statusMask"),t.getInt("price"))}})
        }}?:emptyList()
        require(clinicRows.map{it.id}.distinct().size==clinicRows.size)
        val clinics=clinicRows.associateBy{it.id}
        require(clinics.values.all{s->(ClinicRevival.valid(s)||ClinicCare.valid(s))&&s.mapId in scenes&&npcs.any{
            it.id==s.npcId&&it.mapId==s.mapId&&(it.clinicId==s.id||serviceBindings.any{b->
                b.npcId==it.id&&b.interiorMapId==it.mapId&&b.clinicId==s.id})}})
        require(serviceBindings.all{b->b.callerMapId in scenes&&b.interiorMapId in scenes&&
            npcs.any{it.id==b.npcId&&it.mapId==b.interiorMapId}&&listOf(b.shopId,b.innId,b.clinicId).count{it!=null}==1&&
            (b.shopId==null||shops[b.shopId]?.let{it.mapId==b.interiorMapId&&it.npcId==b.npcId}==true)&&
            (b.innId==null||inns[b.innId]?.let{it.mapId==b.interiorMapId&&it.npcId==b.npcId}==true)&&
            (b.clinicId==null||clinics[b.clinicId]?.let{it.mapId==b.interiorMapId&&it.npcId==b.npcId}==true)})
        require(npcs.all{(it.shopId==null||it.shopId in shops)&&(it.innId==null||it.innId in inns)&&
            (it.clinicId==null||it.clinicId in clinics)&&listOf(it.shopId,it.innId,it.clinicId).count{v->v!=null}<=1})
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
                            itemDefinitions.getValue(it.itemId).category==it.category)}}).also{enemy->
                    val protection=originalProtectionProfile(enemy.id)
                    if(protection!=null){
                        require(e.getInt("requiredBindingMarker")==protection.marker&&
                            e.getString("bindingEvidence")==protection.protectionEvidence)
                        enemy.requiredBindingMarker=protection.marker
                    }else require(!e.has("requiredBindingMarker")&&!e.has("bindingEvidence"))
                    if(e.has("specialBaseDamage")){
                        require(enemy.behaviorByte in setOf(1,2,4)&&enemy.iceBaseDamage==null&&
                            e.getInt("specialBaseDamage") in 0..65535&&e.getString("specialDamageEvidence").isNotBlank())
                        enemy.specialBaseDamage=e.getInt("specialBaseDamage")
                    }
                }
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
                growth.map{it.level}.distinct().size==growth.size&&growth.all(::validGrowthRow)&&
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
                        if(b.has("activationFlagId")){
                            require(boss.id=="rom.boss.158"&&boss.npcId=="rom.npc.145.0"&&
                                boss.flagId=="rom.map.145.flag.2"&&members==listOf(EncounterMember(3,158))&&
                                b.getString("activationFlagId")=="rom.npccontext.145.215"&&
                                b.getString("source")=="game-data/provenance/world-jiameng-state.json"&&boss.entryTrigger==null)
                            boss.activationFlagId=b.getString("activationFlagId")
                        }
                        b.optJSONArray("additionalEntryTriggers")?.let{a->
                            require(boss.id=="rom.boss.159"&&boss.npcId=="rom.npc.148.0"&&
                                boss.flagId=="rom.map.148.flag.128"&&boss.entryTrigger==StoryEntryTrigger(148,8,6)&&
                                members==listOf(EncounterMember(0,159),EncounterMember(3,160),EncounterMember(6,161))&&
                                b.getString("source")=="game-data/provenance/world-jiameng-state.json")
                            val points=(0 until a.length()).map{i->val t=a.getJSONObject(i)
                                StoryEntryTrigger(t.getInt("mapId"),t.getInt("x"),t.getInt("y"))}
                            require(points.size==5&&points.toSet()==setOf(StoryEntryTrigger(148,6,5),
                                StoryEntryTrigger(148,10,5),StoryEntryTrigger(148,7,6),
                                StoryEntryTrigger(148,8,6),StoryEntryTrigger(148,9,6)))
                            boss.additionalEntryTriggers=points.toSet()
                        }
                        b.optJSONArray("victoryCharacterChanges")?.let{a->
                            require(boss.id=="rom.boss.159"&&boss.npcId=="rom.npc.148.0"&&
                                boss.flagId=="rom.map.148.flag.128"&&a.length()==1&&
                                b.getString("source")=="game-data/provenance/world-jiameng-state.json")
                            val change=a.getJSONObject(0)
                            require(change.getString("characterId")=="yangjian"&&
                                change.getInt("statusAndMask")==255&&change.getInt("statusOrMask")==64&&
                                !change.getBoolean("restoreHp")&&!change.getBoolean("restoreMp"))
                            boss.victoryCharacterChanges=listOf(StoryCharacterChange("yangjian",statusOrMask=64))
                        }
                        b.optJSONObject("intro")?.let{v->
                            if(boss.id=="rom.boss.157"){
                                require(boss.npcId=="rom.npc.117.1"&&boss.entryTrigger==StoryEntryTrigger(117,7,5)&&
                                    v.getString("evidence")=="game-data/provenance/world-queen117-state.json"&&
                                    v.getInt("completedSteps")==0&&!v.getBoolean("accumulateEncounterSteps"))
                                val ids=v.getJSONArray("dialogueIds").let{d->(0 until d.length()).map{d.getString(it)}}
                                require(ids==listOf("rom.dialogue.127.13")&&ids.all{it in dialogues}&&
                                    ints(v,"destinationCell").contentEquals(intArrayOf(7,5)))
                                val destination=StoryDestination(117,7,5,Key.UP,0,null)
                                require(scenes[117]?.check(7,5)==null)
                                val flag="runtime.story.117.event14.intro.complete"
                                boss.intro=SceneStoryDefinition("rom.scene-story.117.queen-intro",boss.npcId,flag,
                                    boss.entryTrigger!!,StoryContinuation(ids,null,destination,setOf(flag)),
                                    StoryMovement(destination,0),emptyMap())
                            }else if(boss.id=="rom.boss.159"){
                                require(boss.npcId=="rom.npc.148.0"&&boss.additionalEntryTriggers.size==5&&
                                    v.getString("evidence")=="game-data/provenance/world-jiameng-state.json"&&
                                    v.getInt("completedSteps")==0&&!v.getBoolean("accumulateEncounterSteps")&&
                                    v.getBoolean("preserveOpeningPosition"))
                                val ids=v.getJSONArray("dialogueIds").let{d->(0 until d.length()).map{d.getString(it)}}
                                require(ids==(3..5).map{"rom.dialogue.148.$it"}&&ids.all{it in dialogues}&&
                                    ints(v,"destinationCell").contentEquals(intArrayOf(8,6)))
                                val flag="runtime.story.148.event1.intro.complete"
                                val destination=StoryDestination(148,8,6,null,null,null)
                                boss.intro=SceneStoryDefinition("rom.scene-story.148.three-generals-intro",boss.npcId,flag,
                                    boss.entryTrigger!!,StoryContinuation(ids,null,null,setOf(flag)),
                                    StoryMovement(destination,0),emptyMap()).also{
                                        it.additionalEntryTriggers=boss.additionalEntryTriggers;it.preserveOpeningPosition=true}
                            }else{
                            require(boss.id=="rom.boss.152"&&boss.npcId=="rom.npc.76.0"&&
                                boss.entryTrigger==StoryEntryTrigger(76,12,12)&&
                                v.getString("evidence")=="game-data/provenance/world-island-event7.json"&&
                                v.getBoolean("accumulateEncounterSteps")&&v.getInt("completedSteps")==4)
                            val ids=v.getJSONArray("dialogueIds").let{d->(0 until d.length()).map{d.getString(it)}}
                            require(ids==(3..6).map{"rom.dialogue.86.$it"}&&ids.all{it in dialogues})
                            val destination=StoryDestination(76,9,11,Key.UP,0,null)
                            require(ints(v,"destinationCell").contentEquals(intArrayOf(9,11))&&scenes[76]?.check(9,11)==null)
                            val movement=StoryMovement(destination,4).also{it.accumulateEncounterSteps=true}
                            val flag="runtime.story.76.event7.intro.complete"
                            boss.intro=SceneStoryDefinition("rom.scene-story.76.four-villains-intro",boss.npcId,flag,
                                boss.entryTrigger!!,StoryContinuation(ids,null,destination,setOf(flag)),movement,emptyMap())
                            }
                        }
                        b.optJSONObject("approach")?.let{v->
                            require(boss.id=="rom.boss.156"&&boss.npcId=="rom.npc.87.0"&&
                                boss.entryTrigger==StoryEntryTrigger(87,1,7)&&
                                v.getString("evidence")=="game-data/provenance/world-cave87-state.json"&&
                                v.getInt("completedSteps")==6&&v.getBoolean("accumulateEncounterSteps"))
                            val d=v.getJSONObject("destination")
                            require(d.getInt("mapId")==87&&d.getInt("x")==5&&d.getInt("y")==5&&
                                d.getString("direction")=="UP"&&d.getInt("terrainMode")==0)
                            boss.approach=StoryMovement(StoryDestination(87,5,5,Key.UP,0,null),6).also{it.accumulateEncounterSteps=true}
                        }
                        boss.finalizeWithoutDialogue=b.optBoolean("finalizeWithoutDialogue",false)
                        val firstJiameng=boss.id=="rom.boss.158"&&boss.npcId=="rom.npc.145.0"&&
                            boss.activationFlagId=="rom.npccontext.145.215"&&boss.flagId=="rom.map.145.flag.2"&&
                            members==listOf(EncounterMember(3,158))&&g.getInt("id")==0&&
                            b.optString("source")=="game-data/provenance/world-jiameng-state.json"
                        if(boss.finalizeWithoutDialogue)require(!b.optBoolean("commitAfterDialogue",false)&&(firstJiameng||
                            (boss.intro!=null&&
                            boss.flagId=="rom.map.76.flag.128"&&members==listOf(EncounterMember(0,152),EncounterMember(2,153),
                                EncounterMember(4,154),EncounterMember(6,155))&&g.getInt("id")==62&&
                            b.getString("victoryFlagEvidence")=="game-data/provenance/world-island-event7.json")))
                        b.optJSONArray("victoryFlags")?.let{fs->
                            require(b.getString("victoryFlagEvidence").isNotBlank())
                            boss.victoryFlags=(0 until fs.length()).map{fs.getString(it)}.toSet()
                            val jiamengFlags=when{
                                firstJiameng->setOf("rom.npccontext.145.228")
                                boss.id=="rom.boss.159"&&boss.npcId=="rom.npc.148.0"&&boss.additionalEntryTriggers.size==5&&
                                    b.optString("source")=="game-data/provenance/world-jiameng-state.json"->
                                    setOf("rom.map.145.flag.4","rom.map.145.flag.8","rom.npccontext.148.217","rom.npccontext.37.196")
                                else->emptySet()
                            }
                            require(boss.victoryFlags.size==fs.length()&&boss.victoryFlags.size in 1..16&&
                                (if(jiamengFlags.isNotEmpty())boss.victoryFlags==jiamengFlags else
                                    boss.victoryFlags.all{it.matches(Regex("rom\\.map\\.\\d+\\.flag\\.\\d+"))||
                                        (boss.finalizeWithoutDialogue&&it=="rom.global.7c6.16")}))
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
                            StoryContinuation(ids,joins,target,fs).also{chain->
                                if(c.has("departureCharacterId")||c.has("movementsBeforeDialogue")){
                                    require(boss.id=="rom.boss.156"&&boss.npcId=="rom.npc.87.0"&&boss.approach!=null&&
                                        c.getString("evidence")=="game-data/provenance/world-cave87-state.json"&&joins==null&&
                                        c.getString("departureCharacterId")=="xiaolongnv"&&
                                        ids==(11..17).map{"rom.dialogue.97.$it"}&&
                                        fs==setOf("rom.map.87.flag.128","rom.global.7bf.16")&&
                                        target==StoryDestination(87,4,6,Key.RIGHT,0,0))
                                    val moves=c.getJSONArray("movementsBeforeDialogue")
                                    require(moves.length()==2)
                                    val expected=mapOf(3 to (StoryDestination(87,1,7,Key.DOWN,0,null) to 6),
                                        5 to (StoryDestination(87,4,6,Key.RIGHT,0,null) to 4))
                                    val parsed=(0 until moves.length()).associate{i->
                                        val m=moves.getJSONObject(i);val index=m.getInt("index");val d=m.getJSONObject("destination")
                                        val destination=StoryDestination(d.getInt("mapId"),d.getInt("x"),d.getInt("y"),Key.valueOf(d.getString("direction")),d.getInt("terrainMode"),null)
                                        require(expected[index]==(destination to m.getInt("completedSteps"))&&m.getBoolean("accumulateEncounterSteps"))
                                        index to StoryMovement(destination,m.getInt("completedSteps")).also{it.accumulateEncounterSteps=true}
                                    }
                                    require(parsed.keys==expected.keys);chain.movementsBeforeDialogue=parsed
                                    chain.departureCharacterId="xiaolongnv"
                                }
                            }
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
                                values.all(::validGrowthRow)&&
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
        val sceneStories=data.optJSONArray("sceneStories")?.let{a->
            (0 until a.length()).map{i->
                val o=a.getJSONObject(i)
                val sceneEvidence=o.getString("evidence")
                val manual=o.optBoolean("manualActivation",false)
                require(sceneEvidence in setOf("game-data/provenance/world-rebirth-script.json",
                    "game-data/provenance/world-jiameng-state.json"))
                val entry=o.getJSONObject("entryTrigger")
                val trigger=StoryEntryTrigger(entry.getInt("mapId"),entry.getInt("x"),entry.getInt("y"))
                fun destination(t:JSONObject):StoryDestination {
                    val d=StoryDestination(t.getInt("mapId"),t.getInt("x"),t.getInt("y"),
                        if(t.has("direction"))Key.valueOf(t.getString("direction"))else null,
                        if(t.has("terrainMode"))t.getInt("terrainMode")else null,
                        if(t.has("encounterSteps"))t.getInt("encounterSteps")else null)
                    require(scenes[d.mapId]?.check(d.x,d.y,d.terrainMode?:0)==null)
                    require(d.direction==null||d.direction in setOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT))
                    require(d.encounterSteps==null||d.encounterSteps in 0..255)
                    return d
                }
                fun movement(m:JSONObject)=StoryMovement(destination(m.getJSONObject("destination")),m.getInt("completedSteps"))
                val c=o.getJSONObject("continuation")
                val ids=c.getJSONArray("dialogueIds").let{d->(0 until d.length()).map{d.getString(it)}}
                require(!c.has("joinCharacterId")&&ids.all{it in dialogues})
                val completion=c.getJSONArray("completionFlags").let{f->(0 until f.length()).map{f.getString(it)}.toSet()}
                val chain=StoryContinuation(ids,null,c.optJSONObject("destination")?.let(::destination),completion)
                val moves=o.getJSONArray("movementsBeforeDialogue").let{m->(0 until m.length()).map{j->
                    val v=m.getJSONObject(j);v.getInt("dialogueIndex") to movement(v)}}
                require(moves.map{it.first}.distinct().size==moves.size&&moves.all{it.first in 1 until ids.size&&it.second.destination.mapId==86})
                val story=SceneStoryDefinition(o.getString("id"),o.getString("npcId"),o.getString("flagId"),trigger,chain,
                    movement(o.getJSONObject("openingMovement")),moves.toMap()).also{it.manualActivation=manual}
                if(sceneEvidence=="game-data/provenance/world-rebirth-script.json"){
                require(!manual&&!c.has("characterChanges")&&story.id=="rom.scene-story.86.rebirth"&&story.npcId=="rom.npc.86.0"&&
                    story.flagId=="rom.map.86.flag.128"&&trigger.mapId==86&&story.openingMovement.destination.mapId==86&&
                    chain.destination?.mapId==16&&completion==setOf(story.flagId)&&
                    scenes[86]?.check(trigger.x,trigger.y)==null&&
                    npcs.any{it.id==story.npcId&&it.mapId==86&&it.scriptedActor&&it.firstEffects.isEmpty()})
                }else{
                    // Only this witnessed NPC return is admitted. Not a generic
                    // data-driven way to restore or create arbitrary party members.
                    require(manual&&story.id=="rom.scene-story.146.xiao-return"&&story.npcId=="rom.npc.146.0"&&
                        story.flagId=="rom.map.146.flag.4"&&trigger==StoryEntryTrigger(146,2,5)&&
                        chain.destination==null&&ids==listOf("rom.dialogue.156.2")&&moves.isEmpty()&&
                        completion==setOf(story.flagId,"rom.npccontext.146.216")&&
                        story.openingMovement.completedSteps==0&&story.openingMovement.destination.mapId==146&&
                        story.openingMovement.destination.x==2&&story.openingMovement.destination.y==5&&
                        scenes[146]?.check(trigger.x,trigger.y)==null&&"xiaolongnv" in knownCharacters&&
                        npcs.any{it.id==story.npcId&&it.mapId==146&&!it.scriptedActor&&it.x==2&&it.y==4&&
                            it.firstDialogue==ids.single()&&it.firstEffects.isEmpty()})
                    val changes=c.getJSONArray("characterChanges");require(changes.length()==1)
                    val change=changes.getJSONObject(0)
                    require(change.getString("characterId")=="xiaolongnv"&&change.getInt("statusAndMask")==0&&
                        change.getInt("statusOrMask")==0&&change.getBoolean("restoreHp")&&change.getBoolean("restoreMp"))
                    chain.characterChanges=listOf(StoryCharacterChange("xiaolongnv",0,0,true,true))
                }
                story
            }.also{s->require(s.map{it.id}.distinct().size==s.size&&s.map{it.npcId}.distinct().size==s.size)}
                .associateBy{it.npcId}
        }?:emptyMap()
        require(npcs.filter{it.scriptedActor}.all{npc->
            battle?.storyBattles?.get(npc.id)?.entryTrigger?.mapId==npc.mapId||
                sceneStories[npc.id]?.entryTrigger?.mapId==npc.mapId})
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
                content.clinics=clinics
                data.optJSONObject("nightLightAtlas")?.let{v->
                    require(v.getInt("mapId")==74&&v.getString("activeFlag")==WorldItems.NIGHT_LIGHT_FLAG&&
                        v.getString("evidence")=="game-data/provenance/world-night8-resources.json"&&74 in scenes)
                    val name=checkedName(v.getString("asset"));require(name=="tiles74-lit.png")
                    val resource=ResourceMap(listOf(name),1){bitmap(it,256,256)}
                    content.nightLightAtlas={resource.getValue(name)}
                }
                content.joinCharacters=extraCharacters.associate{it.first.id to it.first}
                content.sceneStories=sceneStories
                content.sceneBarriers=sceneBarriers
                data.optJSONArray("ferries")?.let{a->
                require(a.length()==2)
                val rules=(0 until a.length()).map{i->
                    val o=a.getJSONObject(i)
                    fun leg(v:JSONObject)=FerryLeg(v.getInt("mapId"),v.getInt("x"),v.getInt("y"),Key.valueOf(v.getString("direction")))
                    val legs=o.getJSONArray("legs")
                    FerryDefinition(o.getString("id"),o.getInt("eventId"),leg(o.getJSONObject("start")),
                        o.getInt("contactX"),o.getInt("contactY"),(0 until legs.length()).map{leg(legs.getJSONObject(it))},
                        o.getString("spriteAsset"),o.getString("evidence")).also{rule->
                        require(OriginalFerry.verified(rule))
                        require(rule.legs.all{p->val m=scenes[p.mapId];m!=null&&p.x in 0 until m.width&&p.y in 0 until m.height})
                    }
                }
                require(rules.map{it.eventId}.toSet()==setOf(45,46))
                require(mapObjects.any{it.id=="rom.object.4.0"&&it.x==10&&it.y==3})
                require(data.getJSONArray("mapObjects").let{objects->(0 until objects.length()).any{i->
                    val boat=objects.getJSONObject(i)
                    boat.getString("id")=="rom.object.4.0"&&boat.getString("interaction")=="FERRY_CONTACT"
                }})
                val world=scenes.getValue(16);require(world.collision[135*world.width+150]==25)
                content.ferries=rules.associateBy{it.id}
                content.ferrySprites=rules.associate{it.id to bitmap(it.spriteAsset,16,16)}
            }
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
