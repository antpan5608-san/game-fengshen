package org.fengshen.dev

import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.AudioAttributes
import android.os.SystemClock
import android.test.InstrumentationTestCase
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import android.content.Intent
import android.view.ViewGroup

@Suppress("DEPRECATION")
class AudioBattle02Test:IsolatedGameTestCase(){
    private fun <T:Any> main(block:()->T):T {var v:T?=null;instrumentation.runOnMainSync{v=block()};return v!!}
    private fun waitFor(block:()->Boolean):Boolean {repeat(80){if(main(block))return true;SystemClock.sleep(100)};return false}
    fun testActivityPlayersReleaseAndResumeRoundTwo(){
        val c=instrumentation.targetContext;val players=mutableListOf<GameAudio>()
        repeat(6){
            val activity=instrumentation.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            var view:GameView?=null
            repeat(800){if(view==null){main{view=activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as? GameView;true};SystemClock.sleep(50)}}
            assertNotNull(view);val game=view!!;players.add(game.audio)
            main{game.audio.settings(music=true,sfx=true,mv=.45f);game.audio.foreground(true);true}
            assertTrue(waitFor{game.audio.isPlaying})
            for(scene in listOf("battle","victory")){
                main{game.audio.scene(16,scene);true};assertTrue(waitFor{game.audio.isPlaying})
                main{instrumentation.callActivityOnPause(activity);true};assertTrue(waitFor{!game.audio.isPlaying})
                main{instrumentation.callActivityOnResume(activity);true};assertTrue(waitFor{game.audio.isPlaying})
            }
            main{activity.finish();true};instrumentation.waitForIdleSync();SystemClock.sleep(150)
            // Late callbacks/scene calls must not revive the released instance.
            main{game.audio.foreground(true);game.audio.scene(0);game.audio.close();true}
        }
        val errors=players.sumOf{it.errorCount}
        File(c.getExternalFilesDir(null),"battle02-audio-round2.json").writeText(JSONObject()
            .put("activityCycles",6).put("mapBattleVictorySwitches",12).put("backgroundResumePairs",12)
            .put("errors",errors).put("rootCause","UNCONFIRMED; historical timeout did not reproduce").toString())
        assertEquals(players.joinToString("; "){it.lastErrorCause},0,errors)
    }
    fun testBoundedLifecycleAndFocusStress(){
        val c=instrumentation.targetContext
        val activity=instrumentation.startActivitySync(Intent(c,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var view:GameView?=null
        repeat(800){if(view==null){main{view=activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as? GameView;true};SystemClock.sleep(50)}}
        assertNotNull("Foreground Activity required for Android 15 audio focus",view)
        main{view!!.audio.foreground(false);true}
        val content=ContentLoader.load(AssetSource(c.assets),audioCache=File(c.cacheDir,"battle02-audio-test"))
        val rows=JSONArray();var errors=0;var soundFailures=0
        val prefs=c.getSharedPreferences("operation-a-ui",0)
        val music=prefs.getBoolean("musicEnabled",true);val sfx=prefs.getBoolean("effectsEnabled",true);val volume=prefs.getFloat("musicVolume",.45f)
        try {
            repeat(12){cycle->
                val audio=main{GameAudio(c,content.audio).also{it.settings(music=true,sfx=true,mv=.45f);it.scene(114);it.foreground(true)}}
                var sounding=waitFor{audio.isPlaying}
                for(map in listOf(16,0,114)){
                    main{audio.scene(map);true};sounding=waitFor{audio.isPlaying}&&sounding
                }
                main{audio.scene(16,"battle");true};sounding=waitFor{audio.isPlaying}&&sounding
                repeat(4){
                    main{audio.foreground(false);true};assertTrue(waitFor{!audio.isPlaying})
                    main{audio.foreground(true);true};sounding=waitFor{audio.isPlaying}&&sounding
                }
                val manager=c.getSystemService(AudioManager::class.java)
                val focus=AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)
                    .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).build())
                    .setOnAudioFocusChangeListener{}.build()
                val granted=main{manager.requestAudioFocus(focus)}==AudioManager.AUDIOFOCUS_REQUEST_GRANTED
                assertTrue("Competing focus request not granted",granted)
                assertTrue("Player ignored focus loss",waitFor{!audio.isPlaying})
                main{manager.abandonAudioFocusRequest(focus);true};sounding=waitFor{audio.isPlaying}&&sounding
                main{audio.scene(16,"victory");audio.scene(16);true};sounding=waitFor{audio.isPlaying}&&sounding
                val pos=main{audio.positionMs};SystemClock.sleep(300)
                sounding=main{audio.positionMs>pos}&&sounding
                main{audio.foreground(false);audio.close();audio.close();audio.foreground(true);audio.scene(0);true}
                SystemClock.sleep(100)
                val row=JSONObject().put("cycle",cycle).put("audiblePlaybackStateAndPosition",sounding)
                    .put("errors",audio.errorCount).put("cause",audio.lastErrorCause).put("timeoutOperation",audio.lastTimeoutOperation)
                rows.put(row);errors+=audio.errorCount;if(!sounding)soundFailures++
            }
        }finally{main{prefs.edit().putBoolean("musicEnabled",music).putBoolean("effectsEnabled",sfx).putFloat("musicVolume",volume).commit();activity.finish();true}}
        File(c.getExternalFilesDir(null),"battle02-audio-stress.json").writeText(JSONObject().put("cycles",rows).put("errors",errors).put("soundFailures",soundFailures).toString())
        assertEquals("Normal audio lifecycle produced errors; cause/operation retained",0,errors)
        assertEquals("Audio did not resume/play after scene/background/focus",0,soundFailures)
    }
}
