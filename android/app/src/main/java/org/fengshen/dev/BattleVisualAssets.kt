package org.fengshen.dev

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Rect
import org.json.JSONObject
import java.security.MessageDigest

/** Lazy, verified presentation resources. No gameplay state or model-generated rules. */
class BattleVisualAssets(private val source:ContentSource,manifestBytes:ByteArray,
    actorIds:Set<String>,mapIds:Set<Int>) {
    companion object {
        const val MANIFEST_SHA256="4480914b805e4b5c99feeaf9d207227a60bca4accf3379fd151c13ccbaac571c"
        const val CACHE_LIMIT=64*1024*1024
        private fun sha(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(""){"%02x".format(it)}
    }
    private val manifest=JSONObject(String(manifestBytes,Charsets.UTF_8))
    private val files=manifest.getJSONObject("files")
    private val actors=manifest.getJSONObject("actors")
    private val backgrounds=manifest.getJSONObject("backgrounds")
    private val maps=manifest.getJSONObject("mapBackgrounds")
    private val cache=ResourceMap(files.keys().asSequence().toList(),files.length()){decode(it)}
        .limitBytes(CACHE_LIMIT.toLong()){it.allocationByteCount.toLong()}
    val cachedBytes:Int get()=cache.cachedBytes().toInt()
    val id:String get()=manifest.getString("id")
    init {
        require(manifestBytes.size<=64*1024&&sha(manifestBytes)==MANIFEST_SHA256){"Visual manifest differs from reviewed assets"}
        require(manifest.getInt("schemaVersion")==1&&id=="battle-visual-02-r1")
        require(manifest.getInt("cacheBytes")==CACHE_LIMIT&&files.length()==12)
        require(actors.keys().asSequence().toSet()==setOf("nezha","xiaolongnv","yangjian","jiangziya"))
        for(actor in actors.keys()){
            require(actor in actorIds)
            val row=actors.getJSONObject(actor)
            for(kind in listOf("portrait","idle"))require(files.getJSONObject(checkedName(row.getString(kind))).getString("kind")==kind)
            val bounds=row.getJSONArray("bounds");val file=files.getJSONObject(row.getString("idle"))
            require(bounds.length()==4&&bounds.getInt(0)>=0&&bounds.getInt(1)>=0&&
                bounds.getInt(2)>bounds.getInt(0)&&bounds.getInt(3)>bounds.getInt(1)&&
                bounds.getInt(2)<=file.getInt("width")&&bounds.getInt(3)<=file.getInt("height"))
        }
        require(backgrounds.keys().asSequence().toSet()==setOf("grass","coast","undersea","cave"))
        for(key in backgrounds.keys())require(files.getJSONObject(checkedName(backgrounds.getString(key))).getString("kind")=="background")
        for(key in maps.keys())require(key.toInt() in mapIds&&backgrounds.has(maps.getString(key)))
    }
    private fun image(name:String):Bitmap=cache.getValue(name)
    private fun decode(name:String):Bitmap {
        val row=files.getJSONObject(checkedName(name))
        val bytes=source.readVisual(name)?:error("Visual asset missing: $name")
        require(bytes.size==row.getInt("bytes")&&bytes.size<=4*1024*1024&&sha(bytes)==row.getString("sha256")){"Visual checksum mismatch: $name"}
        val bounds=BitmapFactory.Options().apply{inJustDecodeBounds=true}
        BitmapFactory.decodeByteArray(bytes,0,bytes.size,bounds)
        require(bounds.outWidth==row.getInt("width")&&bounds.outHeight==row.getInt("height")&&bounds.outWidth in 1..2048&&bounds.outHeight in 1..2048)
        val options=BitmapFactory.Options().apply{inScaled=false;inPreferredConfig=Bitmap.Config.ARGB_8888
            inSampleSize=when(row.getString("kind")){"portrait"->4;"idle"->2;else->1}}
        val image=BitmapFactory.decodeByteArray(bytes,0,bytes.size,options)?:error("Invalid visual image")
        require(image.allocationByteCount<=CACHE_LIMIT)
        // ResourceMap accounts decoded bytes without recycling live Canvas bitmaps.
        return image
    }
    fun portrait(actor:String):Bitmap?=actors.optJSONObject(actor)?.let{image(it.getString("portrait"))}
    fun idle(actor:String):Bitmap?=actors.optJSONObject(actor)?.let{image(it.getString("idle"))}
    fun idleBounds(actor:String,image:Bitmap):Rect {
        val row=actors.getJSONObject(actor);val b=row.getJSONArray("bounds");val file=files.getJSONObject(row.getString("idle"))
        val sx=image.width.toFloat()/file.getInt("width");val sy=image.height.toFloat()/file.getInt("height")
        return Rect((b.getInt(0)*sx).toInt(),(b.getInt(1)*sy).toInt(),
            (b.getInt(2)*sx).toInt().coerceAtMost(image.width),(b.getInt(3)*sy).toInt().coerceAtMost(image.height))
    }
    fun background(mapId:Int,blackScene:Boolean):Bitmap? {
        val key=if(blackScene)"cave" else maps.optString(mapId.toString()).ifBlank{return null}
        return image(backgrounds.getString(key))
    }
}
