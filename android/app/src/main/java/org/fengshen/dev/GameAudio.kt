package org.fengshen.dev

import android.content.Context
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.exoplayer.ExoPlayer
import org.json.JSONObject
import java.io.File
import java.util.concurrent.atomic.AtomicLong
import androidx.media3.exoplayer.ExoTimeoutException

data class AudioTrack(val id:String,val file:File,val loopStartMs:Long,val loopEndMs:Long,val loop:Boolean)
data class AudioContent(val tracks:Map<String,AudioTrack>,val maps:Map<Int,String>,val events:Map<String,String>,val effects:Map<String,File>)

/** One focus owner controls both players. No state changes are driven by drawing frames. */
class GameAudio(context:Context,private val data:AudioContent?) {
    companion object {private val instances=AtomicLong()}
    private val instance=instances.incrementAndGet()
    private var lifecycle=0L
    private val operations=ArrayDeque<String>()
    private fun operation(name:String){
        check(Looper.myLooper()==player.applicationLooper){"Audio operation $name must use player Looper"}
        lifecycle++;operations.addLast("$lifecycle:$name");while(operations.size>6)operations.removeFirst()
    }
    private val handler=Handler(Looper.getMainLooper())
    private val prefs=context.getSharedPreferences("operation-a-ui",0)
    private val manager=context.getSystemService(AudioManager::class.java)
    private val attrs=android.media.AudioAttributes.Builder().setUsage(android.media.AudioAttributes.USAGE_GAME).setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val player=ExoPlayer.Builder(context).setLooper(Looper.getMainLooper()).build().apply{
        setAudioAttributes(androidx.media3.common.AudioAttributes.Builder().setUsage(androidx.media3.common.C.USAGE_GAME).setContentType(androidx.media3.common.C.AUDIO_CONTENT_TYPE_MUSIC).build(),false)
    }
    private val pool=SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
    private val effects=mutableMapOf<String,Int>()
    private val ready=mutableSetOf<Int>()
    private val streams=mutableListOf<Int>()
    private val request=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN).setAudioAttributes(attrs)
        .setOnAudioFocusChangeListener({change->
            if(closed)return@setOnAudioFocusChangeListener
            operation("focus$change")
            focused=change==AudioManager.AUDIOFOCUS_GAIN
            if(change==AudioManager.AUDIOFOCUS_LOSS){abandoned=true;focused=false}
            applyPlayback()
        },handler).build()
    private var focused=false
    private var foreground=false
    private var abandoned=false
    private var closed=false
    private var currentID:String?=null
    var prepareCount=0;private set
    val currentTrack get()=currentID
    var errorCount=0;private set
    var lastErrorCause="";private set
    var lastTimeoutOperation:Int?=null;private set
    val isPlaying get()=player.isPlaying
    val positionMs get()=player.currentPosition
    val musicEnabled get()=prefs.getBoolean("musicEnabled",true)
    val effectsEnabled get()=prefs.getBoolean("effectsEnabled",true)
    val musicVolume get()=prefs.getFloat("musicVolume",.45f).coerceIn(0f,1f)
    val effectsVolume get()=prefs.getFloat("effectsVolume",.6f).coerceIn(0f,1f)
    init {
        player.addListener(object:Player.Listener{
            override fun onPlayerError(error:PlaybackException){
                errorCount++
                val causes=generateSequence<Throwable>(error){it.cause}.take(6).toList()
                lastErrorCause=causes.joinToString(" <- "){it.javaClass.simpleName+":"+(it.message?:"")}
                lastTimeoutOperation=causes.filterIsInstance<ExoTimeoutException>().firstOrNull()?.timeoutOperation
                val details=JSONObject().put("trackID",currentID?:"none").put("audioInstance",instance).put("audioLifecycle",lifecycle)
                    .put("audioOperations",operations.joinToString(",")).put("audioThread",Thread.currentThread().name)
                    .put("audioLooper",Looper.myLooper()?.thread?.name?:"none").put("audioPlayerLooper",player.applicationLooper.thread.name)
                    .put("audioForeground",foreground).put("audioClosed",closed).put("audioCause",lastErrorCause)
                lastTimeoutOperation?.let{details.put("timeoutOperation",it)}
                Diagnostics.record("audio_play_error","ERROR",details,error.errorCodeName,
                    causes.joinToString("\n"){it.javaClass.name+": "+(it.message?:"")+"\n"+it.stackTrace.take(4).joinToString("\n")})
            }
            override fun onMediaItemTransition(item:MediaItem?,reason:Int){if(!closed&&item?.mediaId?.endsWith(":loop")==true)player.repeatMode=Player.REPEAT_MODE_ONE}
        })
        pool.setOnLoadCompleteListener{_,sample,status->if(status==0)ready.add(sample)else Diagnostics.record("audio_play_error","WARN",code="soundpool_decode_failed")}
        data?.effects?.forEach{(id,file)->runCatching{effects[id]=pool.load(file.absolutePath,1)}.onFailure{Diagnostics.record("audio_play_error","WARN",JSONObject().put("assetID",id),"soundpool_load_failed")}}
    }
    fun scene(mapId:Int,event:String?=null){val id=if(event==null)data?.maps?.get(mapId) else data?.events?.get(event);select(id)}
    private fun select(id:String?){
        if(closed||id==currentID)return
        operation("prepare")
        currentID=id;val track=data?.tracks?.get(id)
        if(track==null){player.stop();return}
        fun item(start:Long,end:Long,suffix:String)=MediaItem.Builder().setUri(track.file.toURI().toString()).setMediaId(track.id+suffix)
            .setClippingConfiguration(MediaItem.ClippingConfiguration.Builder().setStartPositionMs(start).setEndPositionMs(end).build()).build()
        player.repeatMode=if(track.loop&&track.loopStartMs==0L)Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        val list=if(track.loop&&track.loopStartMs>0)listOf(item(0,track.loopStartMs,":intro"),item(track.loopStartMs,track.loopEndMs,":loop"))else listOf(item(0,track.loopEndMs,if(track.loop)":loop" else ":once"))
        player.setMediaItems(list);player.prepare();prepareCount++;applyPlayback()
    }
    fun foreground(value:Boolean){
        if(closed)return
        operation(if(value)"play" else "pause")
        foreground=value
        if(!value){player.pause();stopEffects();manager.abandonAudioFocusRequest(request);focused=false;abandoned=false}
        else if(!focused&&!abandoned&&(musicEnabled||effectsEnabled)){focused=manager.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED}
        applyPlayback()
    }
    private fun applyPlayback(){
        if(closed)return
        player.volume=musicVolume
        player.playWhenReady=foreground&&focused&&musicEnabled&&currentID!=null
        if(!foreground||!focused||!effectsEnabled)stopEffects()
    }
    private fun stopEffects(){streams.forEach{pool.stop(it)};streams.clear()}
    fun effect(event:String){
        if(!foreground||!focused||!effectsEnabled||closed)return
        val id=data?.events?.get(event)?:return;val sound=effects[id]?:return
        if(sound in ready){val stream=pool.play(sound,effectsVolume,effectsVolume,1,0,1f);if(stream==0)Diagnostics.record("audio_play_error","WARN",JSONObject().put("assetID",id),"soundpool_play_failed")else{streams.add(stream);if(streams.size>32)streams.removeAt(0)}}
    }
    fun settings(music:Boolean=musicEnabled,sfx:Boolean=effectsEnabled,mv:Float=musicVolume,sv:Float=effectsVolume){
        if(closed)return
        operation("settings")
        prefs.edit().putBoolean("musicEnabled",music).putBoolean("effectsEnabled",sfx).putFloat("musicVolume",mv.coerceIn(0f,1f)).putFloat("effectsVolume",sv.coerceIn(0f,1f)).apply()
        if(!music&&!sfx){manager.abandonAudioFocusRequest(request);focused=false}else if(foreground&&!focused&&!abandoned){focused=manager.requestAudioFocus(request)==AudioManager.AUDIOFOCUS_REQUEST_GRANTED};applyPlayback()
    }
    fun close(){if(closed)return;operation("release");closed=true;player.release();pool.release();manager.abandonAudioFocusRequest(request)}
}
