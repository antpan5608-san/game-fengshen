package org.fengshen.dev

import android.app.PendingIntent
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.SystemClock
import android.test.InstrumentationTestCase
import java.io.File
import org.json.JSONObject

/** Uses the real uploader/installer APIs. Confirmation is driven externally by the AVD QA script. */
@Suppress("DEPRECATION")
// This driver must also run against the previous installed APK, whose Diagnostics
// class predates the new testSession field. Its host verifies unchanged user saves.
class UpdateFlowTest:InstrumentationTestCase(){
    private fun launch():MainActivity = instrumentation.startActivitySync(
        Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
    private fun findText(text:String):Boolean {
        fun visit(n:android.view.accessibility.AccessibilityNodeInfo?):Boolean {
            if(n==null)return false
            return n.text?.toString()?.contains(text)==true || (0 until n.childCount).any{visit(n.getChild(it))}
        }
        return visit(instrumentation.uiAutomation.rootInActiveWindow)
    }
    fun testCheckPublicReleaseIsAlreadyInstalled(){
        val a=launch();val before=a.packageManager.packageInstaller.mySessions.map{it.sessionId}.toSet()
        instrumentation.runOnMainSync{a.checkForUpdates()}
        var found=false
        for(i in 0..600){if(findText("已是最新版本")){found=true;break};SystemClock.sleep(100)}
        assertTrue("Current public release check failed; inspect the visible result",found)
        assertEquals(before,a.packageManager.packageInstaller.mySessions.map{it.sessionId}.toSet())
        instrumentation.runOnMainSync{a.finish()}
    }
    fun testSameVersionDoesNotCreateInstallSession(){
        val a=launch();val code=a.packageManager.getPackageInfo(a.packageName,0).longVersionCode
        val apk=File(File(a.cacheDir,"apk-update").apply{mkdirs()},"verified-$code.apk")
        File(a.applicationInfo.sourceDir).copyTo(apk,overwrite=true)
        val before=a.packageManager.packageInstaller.mySessions.map{it.sessionId}.toSet()
        instrumentation.runOnMainSync{ApkUpdater::class.java.getDeclaredMethod("install",File::class.java).apply{isAccessible=true}.invoke(ApkUpdater(a),apk)}
        SystemClock.sleep(2500)
        assertEquals(before,a.packageManager.packageInstaller.mySessions.map{it.sessionId}.toSet())
        assertFalse(ApkInstallState.active(a));instrumentation.runOnMainSync{a.finish()}
    }
    fun testPrepareRealSystemCancellationProbe(){
        val a=launch();val c=instrumentation.targetContext
        val installer=a.packageManager.packageInstaller
        val info=a.packageManager.getPackageInfo(a.packageName,0)
        val apk=File(a.applicationInfo.sourceDir)
        val params=PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply{
            setAppPackageName(a.packageName);setSize(apk.length());setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
        }
        val id=installer.createSession(params)
        // A same-byte reinstall fixture is deliberately cancelled. The future target
        // keeps installed-version reconciliation from bypassing the pending-user-action
        // branch. This is test=true, not a published APK or a success/upgrade claim.
        ApkInstallState.begin(c,id,info.longVersionCode+1,test=true)
        installer.openSession(id).use{s->
            apk.inputStream().use{input->s.openWrite("base.apk",0,apk.length()).use{out->input.copyTo(out);s.fsync(out)}}
            val i=Intent(c,ApkInstallReceiver::class.java).setAction(ApkInstallReceiver.ACTION).setData(Uri.parse("fengshen-update://session/$id"))
            s.commit(PendingIntent.getBroadcast(c,id,i,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE).intentSender)
        }
        var ready=false
        for(i in 0..200){if(findText("update")||findText("Update")||findText("更新")){ready=true;break};SystemClock.sleep(100)}
        assertTrue("Real PackageInstaller confirmation did not become visible",ready)
        File(c.getExternalFilesDir(null),"installer-cancel-probe.json").writeText(JSONObject().put("sessionID",id).put("installedVersion",info.longVersionCode).put("test",true).toString())
        // Leave the system confirmation open for the external UI test to press Cancel.
    }
    fun testOpenPublicUpdateOnPreviousVersion(){
        val a=launch()
        instrumentation.uiAutomation.serviceInfo=instrumentation.uiAutomation.serviceInfo.apply{
            flags=flags or android.accessibilityservice.AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
        }
        fun positiveBounds():org.json.JSONArray {
            val n=instrumentation.uiAutomation.rootInActiveWindow.findAccessibilityNodeInfosByViewId("android:id/button1").first()
            val r=android.graphics.Rect();n.getBoundsInScreen(r)
            return org.json.JSONArray(listOf(r.left,r.top,r.right,r.bottom))
        }
        instrumentation.runOnMainSync{a.checkForUpdates()}
        var ready=false
        for(i in 0..900){if(findText("发现新版本")){ready=true;break};SystemClock.sleep(100)}
        assertTrue("Real public download/verification did not offer installation",ready)
        File(instrumentation.targetContext.getExternalFilesDir(null),"installer-download-probe.json")
            .writeText(JSONObject().put("installedVersion",a.packageManager.getPackageInfo(a.packageName,0).longVersionCode).put("downloadDialogVisible",true).put("buttonBounds",positiveBounds()).toString())
        var confirmation=false
        for(i in 0..300){
            val root=instrumentation.uiAutomation.rootInActiveWindow
            if(root?.packageName?.toString()!=a.packageName && (findText("update")||findText("Update")||findText("更新"))){confirmation=true;break}
            SystemClock.sleep(100)
        }
        assertTrue("System did not request upgrade confirmation",confirmation)
        File(instrumentation.targetContext.getExternalFilesDir(null),"installer-system-probe.json")
            .writeText(JSONObject().put("systemConfirmationVisible",true).put("buttonBounds",positiveBounds()).toString())
        // App install and system confirmation are driven externally. No adb install for the upgrade.
        // Keep the Activity alive until the externally confirmed package replacement
        // terminates this instrumentation process. Completion is checked by the driver.
        SystemClock.sleep(150000)
        fail("External App upgrade did not replace the test process within the deadline")
    }
}
