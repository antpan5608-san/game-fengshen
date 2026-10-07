package org.fengshen.dev

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import org.json.JSONObject
import java.security.MessageDigest

/** Prepared by ContentLoader's existing worker. Canvas access never reads or decodes assets. */
class BattleVisualAssets(source:ContentSource,manifestBytes:ByteArray,
    actorIds:Set<String>,mapIds:Set<Int>,enemyIds:Set<Int>) {
    companion object {
        const val MANIFEST_SHA256="8dc53b77027055a2b9a2ec37a80e2aba3113e668bd350ab822c14c3d85794f30"
        const val CACHE_LIMIT=64*1024*1024
        private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(""){"%02x".format(it)}
    }
    private val manifest=JSONObject(String(manifestBytes,Charsets.UTF_8))
    private val files=manifest.getJSONObject("files")
    private val actors=manifest.getJSONObject("actors")
    private val enemies=manifest.getJSONObject("enemies")
    private val backgrounds=manifest.getJSONObject("backgrounds")
    private val maps=manifest.getJSONObject("mapBackgrounds")
    private val prepared:Map<String,Bitmap>
    val failedAssets:Set<String>
    val cachedBytes:Int
    val preparedCount:Int get()=prepared.size
    val id:String get()=manifest.getString("id")
    init {
        require(manifestBytes.size<=64*1024&&sha(manifestBytes)==MANIFEST_SHA256){"Visual manifest differs from reviewed assets"}
        require(manifest.getInt("schemaVersion")==1&&id=="battle-visual-02-r2")
        require(manifest.getInt("cacheBytes")==CACHE_LIMIT&&files.length()==16)
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
        // A fixed 16-image pack, not a full-world preload. Share the verified decoded
        // bitmaps with an immutable lookup; retain no source or cache lock for drawing.
        val names=files.keys().asSequence().toList().sorted()
        val cache=ResourceMap(names,names.size){name->decode(source,name)}
            .limitBytes(CACHE_LIMIT.toLong()){it.allocationByteCount.toLong()}
        val ready=linkedMapOf<String,Bitmap>();val failed=linkedSetOf<String>()
        for(name in names){
            try{ready[name]=cache.getValue(name)}catch(error:Exception){failed.add(name)}
        }
        val total=ready.values.sumOf{it.allocationByteCount.toLong()}
        require(total<=CACHE_LIMIT){"Prepared visual pack exceeds decoded budget"}
        prepared=ready.toMap();failedAssets=failed.toSet();cachedBytes=total.toInt()
    }
    private fun checkBounds(b:org.json.JSONArray,file:JSONObject){
        require(b.length()==4&&b.getInt(0)>=0&&b.getInt(1)>=0&&
            b.getInt(2)>b.getInt(0)&&b.getInt(3)>b.getInt(1)&&
            b.getInt(2)<=file.getInt("width")&&b.getInt(3)<=file.getInt("height"))
    }
    private fun decode(source:ContentSource,name:String):Bitmap {
        val row=files.getJSONObject(checkedName(name))
        val bytes=source.readVisual(name)?:error("Visual asset missing: $name")
        require(bytes.size==row.getInt("bytes")&&bytes.size<=4*1024*1024&&sha(bytes)==row.getString("sha256")){"Visual checksum mismatch: $name"}
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth==row.getInt("width")&&bounds.outHeight==row.getInt("height")&&bounds.outWidth in 1..2048&&bounds.outHeight in 1..2048)
        val options=BitmapFactory.Options().apply{inScaled=false;inPreferredConfig=Bitmap.Config.ARGB_8888
            inSampleSize=when(row.getString("kind")){"portrait"->4;"idle","attack","cast","enemy"->2;else->1}}
        val image=BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)?:error("Invalid visual image")
        require(image.allocationByteCount<=CACHE_LIMIT)
        return image
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

/** A crop belongs only to this draw; mutating its Rect cannot change the manifest. */
data class BattleVisualImage(val file:String,val bitmap:Bitmap,val crop:Rect)
