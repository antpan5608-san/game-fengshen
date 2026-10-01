package org.fengshen.dev

import android.test.InstrumentationTestCase
import android.content.Intent
import android.content.pm.PackageInstaller

@Suppress("DEPRECATION")
class ApkUpdateTest:IsolatedGameTestCase() {
    private val signer="5c460557b64daf1eda32c8019cc3610751f8d12af5a9aa412099db5bc8ef70d6"
    private val hash="a".repeat(64)
    private fun manifest(code:Int=6,url:String="https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/fengshen-remake.apk.bin?v=$code",
        name:String="org.fengshen.dev",sig:String=signer,size:Long=2500000)=
        """{"package":"$name","channel":"development","versionCode":$code,"versionName":"0.2.4","apkUrl":"$url","sha256":"$hash","sizeBytes":$size,"signerSha256":"$sig"}"""
    private fun rejects(text:String){
        try {parseApkRelease(text,"org.fengshen.dev",signer);fail("invalid update accepted")}
        catch(_:IllegalArgumentException){/* expected */}
    }
    fun testValidReleaseUsesOnlyFengshenObject(){
        val r=parseApkRelease(manifest(),"org.fengshen.dev",signer)
        assertEquals(6L,r.versionCode);assertEquals(2500000L,r.sizeBytes);assertEquals(signer,r.signerSha256)
    }
    fun testRejectsOtherPackageSignatureOrLocation(){
        rejects(manifest(name="org.other.app"))
        rejects(manifest(sig="b".repeat(64)))
        rejects(manifest(url="https://example.com/fengshen.apk"))
        rejects(manifest(url="https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/language/app.apk?v=6"))
    }
    fun testRejectsInvalidSizeVersionAndDigest(){
        rejects(manifest(size=0))
        rejects(manifest(size=201L*1024*1024))
        rejects(manifest(code=0))
        rejects(manifest().replace(hash,"broken"))
    }
    fun testMissingAndUnrecognizedCallbacksDoNotBecomeFailures(){
        assertEquals(InstallOutcome.IGNORE,installOutcome(19,18,null))
        assertEquals(InstallOutcome.IGNORE,installOutcome(19,18,-99))
        assertEquals(InstallOutcome.IGNORE,installOutcome(0,18,PackageInstaller.STATUS_FAILURE))
    }
    fun testInstalledVersionWinsOverLateFailedOrCancelledCallback(){
        for(status in listOf<Int?>(null,PackageInstaller.STATUS_FAILURE,PackageInstaller.STATUS_FAILURE_ABORTED))
            assertEquals(InstallOutcome.SUCCESS,installOutcome(18,18,status))
    }
    fun testCancellationWaitingAndRealFailureRemainDistinct(){
        assertEquals(InstallOutcome.CANCELLED,installOutcome(19,18,PackageInstaller.STATUS_FAILURE_ABORTED))
        assertEquals(InstallOutcome.WAITING,installOutcome(19,18,PackageInstaller.STATUS_PENDING_USER_ACTION))
        assertEquals(InstallOutcome.WAITING,installOutcome(19,18,PackageInstaller.STATUS_SUCCESS))
        for(status in listOf(PackageInstaller.STATUS_FAILURE,PackageInstaller.STATUS_FAILURE_STORAGE,PackageInstaller.STATUS_FAILURE_INVALID))
            assertEquals(InstallOutcome.FAILED,installOutcome(19,18,status))
    }
    fun testUnownedAndIncompleteReceiverCallbacksDoNotChangeSavedResult(){
        val c=instrumentation.targetContext;val p=c.getSharedPreferences("apk-update-state",0)
        val original=p.all
        try {
            p.edit().clear().putString("message","existing result").commit()
            val i=Intent(ApkInstallReceiver.ACTION).putExtra(PackageInstaller.EXTRA_STATUS,PackageInstaller.STATUS_FAILURE)
            assertEquals(InstallOutcome.IGNORE,ApkInstallState.receive(c,i))
            assertEquals("existing result",p.getString("message",null))
        }finally{
            val edit=p.edit().clear();original.forEach{(key,v)->when(v){is String->edit.putString(key,v);is Int->edit.putInt(key,v);is Long->edit.putLong(key,v);is Boolean->edit.putBoolean(key,v)}};edit.commit()
        }
    }
}
