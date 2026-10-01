package org.fengshen.dev

import android.content.Intent
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.ViewGroup
import java.io.File
import org.json.JSONObject

@Suppress("DEPRECATION")
class AudioDiagnosticsTest:IsolatedGameTestCase(){
    private fun launch():Pair<MainActivity,GameView>{
        val a=instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var v:GameView?=null
        repeat(800){if(v==null){instrumentation.runOnMainSync{v=(a.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as? GameView)?.takeIf{it.width>0}};SystemClock.sleep(50)}}
        assertNotNull(v);return a to v!!
    }
    private fun <T:Any> onMain(block:()->T):T {var result:T?=null;instrumentation.runOnMainSync{result=block()};return result!!}
    fun testAudioPackageAndOldC7SaveCompatibility(){
        val c=instrumentation.targetContext
        val content=ContentLoader.load(AssetSource(c.assets),audioCache=File(c.cacheDir,"audio-test"))
        assertNotNull(content.audio);assertEquals(setOf(114,16,0),content.audio!!.maps.keys)
        assertEquals(4,content.audio!!.tracks.size)
        assertEquals(content.audio!!.events["battle"],content.audio!!.events["victory"])
        assertTrue(content.audio!!.effects.isEmpty()) // Unverified effects stay absent, no placeholder tones.
        val old=SaveSnapshot("opening-segment-001-c7",114,8*16+8,21*16+8,Key.DOWN,listOf(content.initialPlayer),mapOf(OpeningEquipment.KNIFE_ID to 1),mapOf("rom.npc.114.2" to true))
        assertTrue(SaveSnapshot.parse(old.json().toString()).validate(content))
    }
    fun testPlayerLoopSameTrackPauseAndSettings(){
        val(a,v)=launch();val audio=v.audio
        instrumentation.runOnMainSync{audio.settings(music=true,sfx=true,mv=.45f);audio.scene(114);audio.foreground(true)}
        for(i in 0..150){if(onMain{audio.isPlaying})break;SystemClock.sleep(100)}
        assertTrue("BGM decoder/focus did not start",onMain{audio.isPlaying})
        val initial=onMain{audio.prepareCount}
        val before=onMain{audio.positionMs}
        instrumentation.runOnMainSync{repeat(10){audio.scene(114)};GameView::class.java.getDeclaredMethod("openMenu").apply{isAccessible=true}.invoke(v)}
        SystemClock.sleep(600);assertEquals(initial,onMain{audio.prepareCount});assertTrue(onMain{audio.positionMs}>before)
        instrumentation.runOnMainSync{audio.scene(16);audio.scene(16,"battle")}
        SystemClock.sleep(500);val battleCount=onMain{audio.prepareCount}
        SystemClock.sleep(20500);assertTrue(onMain{audio.isPlaying});assertTrue(onMain{audio.positionMs}<20636L)
        instrumentation.runOnMainSync{audio.scene(16,"victory")};assertEquals(battleCount,onMain{audio.prepareCount})
        instrumentation.runOnMainSync{audio.foreground(false)};val paused=onMain{audio.positionMs}
        SystemClock.sleep(600);assertFalse(onMain{audio.isPlaying});assertTrue(kotlin.math.abs(onMain{audio.positionMs}-paused)<100)
        instrumentation.runOnMainSync{audio.foreground(true)};SystemClock.sleep(500);assertTrue(onMain{audio.isPlaying})
        instrumentation.runOnMainSync{audio.settings(music=false,sfx=false)};SystemClock.sleep(100);assertFalse(onMain{audio.isPlaying})
        instrumentation.runOnMainSync{audio.settings(music=true,sfx=true);GameView::class.java.getDeclaredMethod("closeMenu").apply{isAccessible=true}.invoke(v);a.finish()}
    }
    fun testDiagnosticsRedactionAndSwitchClearsOnlyQueue(){
        val(activity,_)=launch()
        val c=instrumentation.targetContext
        Diagnostics.setEnabled(false);Diagnostics.flushForTest()
        val prefs=c.getSharedPreferences("opening-local-save",0);val saved=prefs.getString("saveJson",null)
        assertEquals("[redacted] [redacted]",Diagnostics.safeText("Bearer secret-value password=secret-value"))
        val id=Diagnostics.record("controlled_probe","ERROR",code="token=secret-value",test=true)
        Diagnostics.flushForTest();assertFalse(File(c.filesDir,"diagnostics/$id.json").exists())
        assertEquals(saved,prefs.getString("saveJson",null));Diagnostics.setEnabled(true);instrumentation.runOnMainSync{activity.finish()}
    }
    fun testBoundedQueueKeepsCriticalAndOffClears(){
        val resolver=instrumentation.targetContext.contentResolver
        val wifi=android.provider.Settings.Global.getInt(resolver,"wifi_on",1)>0
        val data=android.provider.Settings.Global.getInt(resolver,"mobile_data",1)>0
        fun shell(command:String){instrumentation.uiAutomation.executeShellCommand(command).use{fd->java.io.FileInputStream(fd.fileDescriptor).use{it.readBytes()}}}
        // This is a local queue eviction test. An online worker can legitimately
        // consume it (including 410 for an unpublished APK), invalidating the fixture.
        shell("svc wifi disable");shell("svc data disable");SystemClock.sleep(1000)
        try {
        val(activity,_)=launch()
        instrumentation.runOnMainSync{activity.finish()};instrumentation.waitForIdleSync()
        val c=instrumentation.targetContext
        Diagnostics.setEnabled(false);Diagnostics.flushForTest();Diagnostics.setEnabled(true)
        val p=c.getSharedPreferences("diagnostics",0);p.edit().putLong("limitBytes",1024*1024).commit()
        val fatal=Diagnostics.record("queue_capacity_probe","FATAL",stack="x".repeat(2000),test=true)
        repeat(450){Diagnostics.record("queue_capacity_probe","INFO",stack="x".repeat(2000),test=true)}
        Diagnostics.flushForTest()
        val root=File(c.filesDir,"diagnostics")
        assertTrue(root.listFiles()!!.sumOf{it.length()}<=1024*1024)
        assertTrue(File(root,"$fatal.json").exists());assertTrue(p.getLong("dropped",0)>0)
        Diagnostics.setEnabled(false);Diagnostics.flushForTest();assertTrue(root.listFiles()!!.isEmpty())
        p.edit().remove("limitBytes").commit();Diagnostics.setEnabled(true);instrumentation.runOnMainSync{activity.finish()}
        } finally {if(wifi)shell("svc wifi enable");if(data)shell("svc data enable")}
    }
    // Used while adb disables network; after process restart the same eventID must arrive remotely.
    fun testOfflineAppEvent(){
        val(a,v)=launch();assertNotNull(v.audio.currentTrack)
        val id=Diagnostics.record("offline_recovery_probe",details=JSONObject().put("mapId",v.world.mapId),test=true)
        Diagnostics.flushForTest()
        File(instrumentation.targetContext.getExternalFilesDir(null),"diagnostic-offline-event.txt").writeText(id)
        assertTrue(File(instrumentation.targetContext.filesDir,"diagnostics/$id.json").exists())
        instrumentation.runOnMainSync{v.persistState();a.finish()}
    }
    // Actual App HTTPS uploader; a test marker is not a fabricated curl batch.
    fun testAppUploadAndCaptureIdentity(){
        val(a,v)=launch();val id=Diagnostics.record("app_upload_probe",details=JSONObject().put("mapId",v.world.mapId),test=true)
        Diagnostics.flushForTest();val ok=Diagnostics.upload()
        assertTrue("App HTTPS upload failed",ok)
        File(instrumentation.targetContext.getExternalFilesDir(null),"diagnostic-upload-event.txt").writeText(id)
        assertFalse(File(instrumentation.targetContext.filesDir,"diagnostics/$id.json").exists())
        instrumentation.runOnMainSync{a.finish()}
    }
}

/** Run separately: the expected process death must not turn the ordinary suite into a pass. */
@Suppress("DEPRECATION")
class ControlledCrashTest:InstrumentationTestCase(){
    fun testUncaughtChain(){
        instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        SystemClock.sleep(10000)
        Diagnostics.flushForTest()
        android.os.Handler(android.os.Looper.getMainLooper()).post{throw DiagnosticTestException()}
        SystemClock.sleep(3000)
        fail("Uncaught exception was swallowed instead of terminating the process")
    }
}
