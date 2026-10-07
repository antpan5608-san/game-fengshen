package org.fengshen.dev

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import org.json.JSONObject
import java.security.MessageDigest

/** Immutable prepared lookup. It retains neither a source nor the worker's cache. */
class BattleVisualAssets internal constructor(private val definition:BattleVisualDefinition,
    private val prepared:Map<String,Bitmap>,val failedAssets:Set<String>) {
    // Keep the original full-pack constructor for explicit decoder evidence and old callers.
    constructor(source:ContentSource,manifestBytes:ByteArray,actorIds:Set<String>,
        mapIds:Set<Int>,enemyIds:Set<Int>):this(
        BattleVisualPreparer(source,manifestBytes,actorIds,mapIds,enemyIds).prepareAll())
    private constructor(copy:BattleVisualAssets):this(copy.definition,copy.prepared,copy.failedAssets)
    companion object {
        const val MANIFEST_SHA256="8dc53b77027055a2b9a2ec37a80e2aba3113e668bd350ab822c14c3d85794f30"
        const val CACHE_LIMIT=64*1024*1024
    }
    private val files get()=definition.files
    private val actors get()=definition.actors
    private val enemies get()=definition.enemies
    private val backgrounds get()=definition.backgrounds
    private val maps get()=definition.maps
    /** Bytes retained by this bundle, not the cache, decoder peak or process footprint. */
    val cachedBytes:Int=prepared.values.sumOf{it.allocationByteCount.toLong()}.also{
        require(it<=CACHE_LIMIT){"Prepared visual pack exceeds decoded budget"}
    }.toInt()
    val preparedCount:Int get()=prepared.size
    val preparedFiles:Set<String> get()=prepared.keys.toSet()
    val id:String get()=definition.id
    /** Ordinary UI keeps its four startup portraits while the battle lookup changes. */
    fun withPortraits(portraits:BattleVisualAssets):BattleVisualAssets {
        require(id==portraits.id)
        val ready=portraits.prepared.filterKeys{it in definition.portraits}+prepared
        val failed=(failedAssets+portraits.failedAssets.filter{it in definition.portraits})-ready.keys
        return BattleVisualAssets(definition,ready.toMap(),failed.toSet())
    }
    fun portrait(actor:String):Bitmap?=actors.optJSONObject(actor)?.let{prepared[it.getString("portrait")]}
    fun idle(actor:String):Bitmap?=actors.optJSONObject(actor)?.let{prepared[it.getString("idle")]}
    private fun bounds(name:String,image:Bitmap,b:org.json.JSONArray):Rect {
        val file=files.getJSONObject(name)
        val sx=image.width.toFloat()/file.getInt("width");val sy=image.height.toFloat()/file.getInt("height")
        return Rect((b.getInt(0)*sx).toInt(),(b.getInt(1)*sy).toInt(),
            (b.getInt(2)*sx).toInt().coerceAtMost(image.width),(b.getInt(3)*sy).toInt().coerceAtMost(image.height))
    }
    fun idleBounds(actor:String,image:Bitmap):Rect {
        val row=actors.getJSONObject(actor)
        return bounds(row.getString("idle"),image,row.getJSONArray("bounds"))
    }
    fun body(actor:String,pose:BattleVisualPose):BattleVisualImage? {
        val row=actors.optJSONObject(actor)?:return null
        val key=when(pose){BattleVisualPose.ATTACK->"attack";BattleVisualPose.CAST->"cast";else->"idle"}
        val selected=row.optString(key).takeIf{it in prepared}?:row.getString("idle")
        val image=prepared[selected]?:return null
        val b=if(selected==row.getString("idle"))row.getJSONArray("bounds")else files.getJSONObject(selected).getJSONArray("bounds")
        return BattleVisualImage(selected,image,bounds(selected,image,b))
    }
    fun enemy(id:Int):BattleVisualImage? {
        val name=enemies.optString(id.toString()).takeIf{it in prepared}?:return null
        val image=prepared.getValue(name)
        return BattleVisualImage(name,image,bounds(name,image,files.getJSONObject(name).getJSONArray("bounds")))
    }
    fun background(mapId:Int,blackScene:Boolean):Bitmap? {
        val key=if(blackScene)"cave" else maps.optString(mapId.toString()).ifBlank{return null}
        return prepared[backgrounds.getString(key)]
    }
}

/** Worker-owned, reusable bounded preparation. Drawing receives only a completed bundle. */
class BattleVisualPreparer(private val source:ContentSource,manifestBytes:ByteArray,
    actorIds:Set<String>,mapIds:Set<Int>,enemyIds:Set<Int>) {
    private val definition=BattleVisualDefinition(manifestBytes,actorIds,mapIds,enemyIds)
    private val cache=ResourceMap(definition.names,definition.names.size){name->definition.decode(source,name)}
        .limitBytes(BattleVisualAssets.CACHE_LIMIT.toLong()){it.allocationByteCount.toLong()}
    fun preparePortraits():BattleVisualAssets=prepare(definition.portraits)
    fun prepareBattle(request:BattleVisualRequest):BattleVisualAssets=prepare(definition.selection.battle(request))
    fun prepareAll():BattleVisualAssets=prepare(definition.names)
    /** Worker diagnostics only; this synchronized query is never needed by Canvas. */
    fun retainedCacheBytes():Long=cache.cachedBytes()
    private fun prepare(names:Collection<String>):BattleVisualAssets {
        val ready=linkedMapOf<String,Bitmap>();val failed=linkedSetOf<String>()
        for(name in names.sorted()){
            try{ready[name]=cache.getValue(name)}catch(error:Exception){failed.add(name)}
        }
        return BattleVisualAssets(definition,ready.toMap(),failed.toSet())
    }
}

internal class BattleVisualDefinition(manifestBytes:ByteArray,
    actorIds:Set<String>,mapIds:Set<Int>,enemyIds:Set<Int>) {
    private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString(""){"%02x".format(it)}
    private val manifest=JSONObject(String(manifestBytes,Charsets.UTF_8))
    val files:JSONObject=manifest.getJSONObject("files")
    val actors:JSONObject=manifest.getJSONObject("actors")
    val enemies:JSONObject=manifest.getJSONObject("enemies")
    val backgrounds:JSONObject=manifest.getJSONObject("backgrounds")
    val maps:JSONObject=manifest.getJSONObject("mapBackgrounds")
    val names:List<String>
    val portraits:Set<String>
    val selection:BattleVisualSelection
    val id:String get()=manifest.getString("id")
    init {
        require(manifestBytes.size<=64*1024&&sha(manifestBytes)==BattleVisualAssets.MANIFEST_SHA256){"Visual manifest differs from reviewed assets"}
        require(manifest.getInt("schemaVersion")==1&&id=="battle-visual-02-r2")
        require(manifest.getInt("cacheBytes")==BattleVisualAssets.CACHE_LIMIT&&files.length()==16)
        require(actors.keys().asSequence().toSet()==setOf("nezha","xiaolongnv","yangjian","jiangziya"))
        for(actor in actors.keys()){
            require(actor in actorIds)
            val row=actors.getJSONObject(actor)
            for(kind in listOf("portrait","idle","attack","cast"))if(row.has(kind))
                require(files.getJSONObject(checkedName(row.getString(kind))).getString("kind")==kind)
            checkBounds(row.getJSONArray("bounds"),files.getJSONObject(row.getString("idle")))
        }
        require(enemies.keys().asSequence().toSet()==setOf("1","137"))
        for(key in enemies.keys())require(key.toInt() in enemyIds&&
            files.getJSONObject(checkedName(enemies.getString(key))).getString("kind")=="enemy")
        require(backgrounds.keys().asSequence().toSet()==setOf("grass","coast","undersea","cave"))
        for(key in backgrounds.keys())require(files.getJSONObject(checkedName(backgrounds.getString(key))).getString("kind")=="background")
        for(key in maps.keys())require(key.toInt() in mapIds&&backgrounds.has(maps.getString(key)))
        for(name in files.keys()){
            val row=files.getJSONObject(checkedName(name))
            if(row.getString("kind") in setOf("attack","cast","enemy"))checkBounds(row.getJSONArray("bounds"),row)
        }
        names=files.keys().asSequence().toList().sorted()
        portraits=actors.keys().asSequence().map{actors.getJSONObject(it).getString("portrait")}.toSet()
        selection=BattleVisualSelection(actors.keys().asSequence().associateWith{actor->
            val row=actors.getJSONObject(actor)
            listOf("portrait","idle","attack","cast").filter{row.has(it)}.map{row.getString(it)}
        },enemies.keys().asSequence().associate{it.toInt() to enemies.getString(it)},
            backgrounds.keys().asSequence().associateWith{backgrounds.getString(it)},
            maps.keys().asSequence().associate{it.toInt() to maps.getString(it)})
    }
    private fun checkBounds(b:org.json.JSONArray,file:JSONObject){
        require(b.length()==4&&b.getInt(0)>=0&&b.getInt(1)>=0&&
            b.getInt(2)>b.getInt(0)&&b.getInt(3)>b.getInt(1)&&
            b.getInt(2)<=file.getInt("width")&&b.getInt(3)<=file.getInt("height"))
    }
    fun decode(source:ContentSource,name:String):Bitmap {
        val row=files.getJSONObject(checkedName(name))
        val bytes=source.readVisual(name)?:error("Visual asset missing: $name")
        require(bytes.size==row.getInt("bytes")&&bytes.size<=4*1024*1024&&sha(bytes)==row.getString("sha256")){"Visual checksum mismatch: $name"}
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth==row.getInt("width")&&bounds.outHeight==row.getInt("height")&&bounds.outWidth in 1..2048&&bounds.outHeight in 1..2048)
        val options=BitmapFactory.Options().apply{inScaled=false;inPreferredConfig=Bitmap.Config.ARGB_8888
            inSampleSize=when(row.getString("kind")){"portrait"->4;"idle","attack","cast","enemy"->2;else->1}}
        val image=BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)?:error("Invalid visual image")
        require(image.allocationByteCount<=BattleVisualAssets.CACHE_LIMIT)
        return image
    }
}

/** A crop belongs only to this draw; mutating its Rect cannot change the manifest. */
data class BattleVisualImage(val file:String,val bitmap:Bitmap,val crop:Rect)
