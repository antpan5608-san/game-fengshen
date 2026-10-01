package org.fengshen.dev

import android.app.Activity
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicBoolean

private const val RELEASE_BASE = "https://kubernetes-fleetpilot.oss-cn-beijing.aliyuncs.com/artifacts/fengshen-remake/app/"
private const val RELEASE_MANIFEST = RELEASE_BASE + "version.json"
private const val RELEASE_APK = RELEASE_BASE + "fengshen-remake.apk.bin"
private const val MAX_APK_BYTES = 200L * 1024 * 1024

data class ApkRelease(val packageName:String,val versionCode:Long,val versionName:String,val apkUrl:String,
    val sha256:String,val sizeBytes:Long,val signerSha256:String)

internal enum class InstallOutcome { WAITING, SUCCESS, CANCELLED, FAILED, IGNORE }
internal fun installOutcome(target:Long,installed:Long,status:Int?):InstallOutcome = when {
    target<=0 -> InstallOutcome.IGNORE
    installed>=target -> InstallOutcome.SUCCESS
    status==null -> InstallOutcome.IGNORE
    status==PackageInstaller.STATUS_PENDING_USER_ACTION || status==PackageInstaller.STATUS_SUCCESS -> InstallOutcome.WAITING
    status==PackageInstaller.STATUS_FAILURE_ABORTED -> InstallOutcome.CANCELLED
    status>0 -> InstallOutcome.FAILED
    // A future platform status is not automatically a failure.
    else -> InstallOutcome.IGNORE
}

/** Installer bookkeeping only; player state remains in the existing save system. */
internal object ApkInstallState {
    private fun prefs(c:Context)=c.getSharedPreferences("apk-update-state",0)
    fun begin(c:Context,session:Int,target:Long,test:Boolean=false){check(prefs(c).edit().putInt("session",session).putLong("target",target).putBoolean("test",test).remove("missingSince").remove("message").commit())}
    fun active(c:Context)=prefs(c).getInt("session",-1)>=0
    private fun installed(c:Context)=c.packageManager.getPackageInfo(c.packageName,0).longVersionCode
    fun record(c:Context,stage:String,code:String="",severity:String="INFO",status:Int?=null){
        val p=prefs(c)
        val d=JSONObject().put("stage",stage).put("targetVersion",p.getLong("target",0))
            .put("installedVersion",installed(c)).put("installSessionID",p.getInt("session",-1))
        if(status!=null)d.put("status",status)
        Diagnostics.record("apk_update",severity,d,code,test=p.getBoolean("test",false))
    }
    private fun complete(c:Context,outcome:InstallOutcome,status:Int?){
        val message=when(outcome){
            InstallOutcome.SUCCESS -> "应用已更新至 ${c.packageManager.getPackageInfo(c.packageName,0).versionName}。"
            InstallOutcome.CANCELLED -> "已取消安装，当前游戏和存档保持不变。可再次检查应用更新。"
            else -> when(status){
                PackageInstaller.STATUS_FAILURE_BLOCKED -> "系统阻止安装，请检查本应用的安装权限或设备安全设置。"
                PackageInstaller.STATUS_FAILURE_CONFLICT -> "安装包与当前应用冲突，请重新检查更新。"
                PackageInstaller.STATUS_FAILURE_INVALID -> "系统未通过安装包验证，请重新下载更新。"
                PackageInstaller.STATUS_FAILURE_INCOMPATIBLE -> "系统提示安装包不兼容。"
                PackageInstaller.STATUS_FAILURE_STORAGE -> "存储空间不足，请腾出空间后重试。"
                else -> "系统未完成安装（状态 ${status?:"未知"}），请再次检查更新。"
            }
        }
        record(c,outcome.name.lowercase(),if(outcome==InstallOutcome.FAILED)"installer_status_$status" else "",if(outcome==InstallOutcome.FAILED)"ERROR" else "INFO",status)
        prefs(c).edit().remove("session").remove("target").remove("test").remove("missingSince").putString("message",message).commit()
    }
    fun reconcile(c:Context){
        val target=prefs(c).getLong("target",0)
        if(target>0 && installed(c)>=target)complete(c,InstallOutcome.SUCCESS,null)
        else if(target>0 && c.packageManager.packageInstaller.getSessionInfo(prefs(c).getInt("session",-1))==null){
            // Returning from the OS dialog can precede its final broadcast. Give that
            // callback time to classify cancellation/failure before recovering a lost session.
            val now=System.currentTimeMillis();val since=prefs(c).getLong("missingSince",0)
            if(since==0L){prefs(c).edit().putLong("missingSince",now).commit();return}
            if(now-since<2000)return
            record(c,"session_missing","unfinished_install","WARN")
            prefs(c).edit().remove("session").remove("target").remove("test").remove("missingSince").putString("message","上次安装未完成，可再次检查应用更新。当前游戏和存档保持不变。").commit()
        }
    }
    fun consumeMessage(c:Context):String?{
        val p=prefs(c);val text=p.getString("message",null)
        if(text!=null)p.edit().remove("message").commit()
        return text
    }
    fun receive(c:Context,intent:Intent):InstallOutcome{
        val target=prefs(c).getLong("target",0)
        if(target>0 && installed(c)>=target){complete(c,InstallOutcome.SUCCESS,null);return InstallOutcome.SUCCESS}
        val p=prefs(c);val session=p.getInt("session",-1)
        if(session<0||intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1)!=session)return InstallOutcome.IGNORE
        val status=if(intent.hasExtra(PackageInstaller.EXTRA_STATUS))intent.getIntExtra(PackageInstaller.EXTRA_STATUS,Int.MIN_VALUE) else null
        val outcome=installOutcome(p.getLong("target",0),installed(c),status)
        if(outcome==InstallOutcome.CANCELLED||outcome==InstallOutcome.FAILED)complete(c,outcome,status)
        else record(c,if(outcome==InstallOutcome.WAITING)"awaiting_system" else "incomplete_callback","",if(outcome==InstallOutcome.IGNORE)"WARN" else "INFO",status)
        return outcome
    }
    fun failedToStart(c:Context,session:Int){
        if(prefs(c).getInt("session",-1)==session)prefs(c).edit().remove("session").remove("target").remove("test").commit()
    }
}

fun parseApkRelease(text:String,expectedPackage:String,installedSigner:String):ApkRelease {
    val o=JSONObject(text)
    val result=ApkRelease(o.getString("package"),o.getLong("versionCode"),o.getString("versionName"),
        o.getString("apkUrl"),o.getString("sha256").lowercase(),o.getLong("sizeBytes"),o.getString("signerSha256").lowercase())
    require(result.packageName==expectedPackage && o.getString("channel")=="development") {"更新包名或发布通道不符"}
    require(result.versionCode>0 && result.versionName.isNotBlank()) {"更新版本无效"}
    require(result.apkUrl=="$RELEASE_APK?v=${result.versionCode}") {"更新地址不属于本应用"}
    require(result.sha256.matches(Regex("[0-9a-f]{64}")) && result.sizeBytes in 1..MAX_APK_BYTES) {"更新校验信息无效"}
    require(result.signerSha256==installedSigner.lowercase()) {"更新签名与当前安装版本不同"}
    return result
}

private fun ByteArray.sha256()=MessageDigest.getInstance("SHA-256").digest(this).joinToString(""){"%02x".format(it)}

class ApkUpdater(private val activity:Activity) {
    private val busy=AtomicBoolean(false)
    private val installing=AtomicBoolean(false)
    @Volatile private var cancelled=false
    @Volatile private var transport:HttpURLConnection?=null
    private val permissionPrefs=activity.getSharedPreferences("apk-update-state",0)

    fun close(){cancelled=true;transport?.disconnect()}

    fun check(){
        if(!busy.compareAndSet(false,true))return
        cancelled=false
        val progress=AlertDialog.Builder(activity).setTitle("检查应用更新").setMessage("正在读取版本信息…")
            .setNegativeButton("取消"){_,_->close()}.setCancelable(false).create()
        progress.show()
        Thread({
            try {
                val signer=installedSigner()
                ApkInstallState.record(activity,"check")
                val release=parseApkRelease(readSmallUrl("$RELEASE_MANIFEST?check=${System.currentTimeMillis()}"),activity.packageName,signer)
                val installed=activity.packageManager.getPackageInfo(activity.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
                val current=installed.longVersionCode
                if(release.versionCode<=current){
                    showResult(progress,"已是最新版本","当前版本 ${installed.versionName}（$current）。")
                    return@Thread
                }
                activity.runOnUiThread {if(!cancelled&&!activity.isDestroyed)progress.setMessage("正在下载 ${release.versionName}…")}
                val apk=downloadAndVerify(release)
                if(cancelled)return@Thread
                activity.runOnUiThread {
                    if(activity.isDestroyed)return@runOnUiThread
                    progress.dismiss()
                    AlertDialog.Builder(activity).setTitle("发现新版本 ${release.versionName}")
                        .setMessage("APK 已在应用内下载并完成 SHA-256、包名、版本和签名校验。继续后由 Android 系统确认安装。")
                        .setNegativeButton("稍后",null).setPositiveButton("安装"){_,_->install(apk)}.show()
                }
            } catch(e:Exception){if(!cancelled){Diagnostics.record("apk_update","ERROR",JSONObject().put("stage","download_or_verify"),e.javaClass.simpleName,e.stackTrace.take(8).joinToString("\n"));showResult(progress,"检查更新未完成",e.message?:"网络或安装包校验失败")}}
            finally {activity.runOnUiThread{progress.dismiss();busy.set(false)}}
        },"fengshen-apk-update").start()
    }

    fun resumeAfterPermission(){
        ApkInstallState.reconcile(activity)
        ApkInstallState.consumeMessage(activity)?.let{Toast.makeText(activity,it,Toast.LENGTH_LONG).show()}
        activity.window.decorView.postDelayed({if(!activity.isDestroyed){ApkInstallState.reconcile(activity);ApkInstallState.consumeMessage(activity)?.let{Toast.makeText(activity,it,Toast.LENGTH_LONG).show()}}},2100)
        val filename=permissionPrefs.getString("permissionFile",null)?:return
        if(!filename.matches(Regex("verified-[0-9]+\\.apk"))){permissionPrefs.edit().remove("permissionFile").commit();return}
        val apk=File(File(activity.cacheDir,"apk-update"),filename)
        if(activity.packageManager.canRequestPackageInstalls()){
            permissionPrefs.edit().remove("permissionFile").commit()
            if(apk.isFile)install(apk) else AlertDialog.Builder(activity).setTitle("更新包已失效").setMessage("请再次检查应用更新。").setPositiveButton("知道了",null).show()
        }
    }

    private fun showResult(progress:AlertDialog,title:String,message:String){
        activity.runOnUiThread {
            if(activity.isDestroyed||cancelled)return@runOnUiThread
            progress.dismiss()
            AlertDialog.Builder(activity).setTitle(title).setMessage(message).setPositiveButton("知道了",null).show()
        }
    }

    private fun connection(url:String):HttpURLConnection {
        val conn=(URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout=10_000;readTimeout=20_000;instanceFollowRedirects=false;useCaches=false
            setRequestProperty("Cache-Control","no-cache")
        }
        transport=conn
        try {if(cancelled)error("下载已取消");if(conn.responseCode!=200)error("更新服务 HTTP ${conn.responseCode}");return conn}
        catch(e:Exception){conn.disconnect();throw e}
    }
    private fun readSmallUrl(url:String):String {
        val conn=connection(url)
        try {
            val bytes=conn.inputStream.use{it.readNBytes(16_385)}
            require(bytes.size<=16_384){"版本信息过大"}
            return bytes.toString(Charsets.UTF_8)
        } finally {conn.disconnect()}
    }
    private fun installedSigner():String {
        val info=activity.packageManager.getPackageInfo(activity.packageName,PackageManager.GET_SIGNING_CERTIFICATES)
        val cert=info.signingInfo?.apkContentsSigners?.singleOrNull()?:error("无法读取当前应用签名")
        return cert.toByteArray().sha256()
    }
    private fun downloadAndVerify(release:ApkRelease):File {
        val directory=File(activity.cacheDir,"apk-update").apply{mkdirs()}
        val partial=File(directory,"download.part")
        val apk=File(directory,"verified-${release.versionCode}.apk")
        partial.delete()
        val digest=MessageDigest.getInstance("SHA-256")
        var length=0L
        try {
            val conn=connection(release.apkUrl)
            try {
                conn.inputStream.use { input -> FileOutputStream(partial).use { output ->
                    val buffer=ByteArray(64*1024)
                    while(true){
                        if(cancelled)throw IllegalStateException("下载已取消")
                        val count=input.read(buffer)
                        if(count<0)break
                        length+=count
                        require(length<=release.sizeBytes){"APK 长度超出元数据"}
                        digest.update(buffer,0,count);output.write(buffer,0,count)
                    }
                    output.fd.sync()
                }}
            } finally {conn.disconnect()}
            require(length==release.sizeBytes && digest.digest().joinToString(""){"%02x".format(it)}==release.sha256){"APK SHA-256 或大小不符"}
            Files.move(partial.toPath(),apk.toPath(),StandardCopyOption.REPLACE_EXISTING)
            val info=activity.packageManager.getPackageArchiveInfo(apk.absolutePath,PackageManager.GET_SIGNING_CERTIFICATES)
                ?:error("APK 无法解析")
            val archiveSigner=info.signingInfo?.apkContentsSigners?.singleOrNull()?.toByteArray()?.sha256()
            require(info.packageName==activity.packageName && info.longVersionCode==release.versionCode && archiveSigner==release.signerSha256){"APK 包名、版本或签名不符"}
            return apk
        } catch(e:Exception){partial.delete();apk.delete();throw e}
    }
    private fun install(apk:File){
        if(ApkInstallState.active(activity)){
            ApkInstallState.reconcile(activity)
            if(ApkInstallState.active(activity)){Toast.makeText(activity,"已有安装请求，请完成系统确认后再试。",Toast.LENGTH_LONG).show();return}
        }
        if(!activity.packageManager.canRequestPackageInstalls()){
            AlertDialog.Builder(activity).setTitle("允许本应用安装更新")
                .setMessage("请在系统设置中允许封神榜安装应用；返回后会继续安装已校验的 APK。")
                .setNegativeButton("稍后"){_,_->permissionPrefs.edit().remove("permissionFile").commit()}
                .setPositiveButton("打开设置"){_,_->
                    permissionPrefs.edit().putString("permissionFile",apk.name).commit()
                    activity.startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,Uri.parse("package:${activity.packageName}")))
                }.show()
            return
        }
        if(!installing.compareAndSet(false,true))return
        Thread({
            var sessionId=-1
            try {
                val archive=activity.packageManager.getPackageArchiveInfo(apk.absolutePath,PackageManager.GET_SIGNING_CERTIFICATES)?:error("APK 无法解析，请重新检查更新")
                require(archive.packageName==activity.packageName && archive.signingInfo?.apkContentsSigners?.singleOrNull()?.toByteArray()?.sha256()==installedSigner()){ "APK 包名或签名不符" }
                val installed=activity.packageManager.getPackageInfo(activity.packageName,0).longVersionCode
                if(archive.longVersionCode<=installed){activity.runOnUiThread{if(!activity.isDestroyed)Toast.makeText(activity,"该版本已安装，无需重复更新。",Toast.LENGTH_LONG).show()};return@Thread}
                val installer=activity.packageManager.packageInstaller
                val params=PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(activity.packageName);setSize(apk.length())
                    if(Build.VERSION.SDK_INT>=31)setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
                sessionId=installer.createSession(params)
                installer.openSession(sessionId).use{session->
                    apk.inputStream().use{input->session.openWrite("base.apk",0,apk.length()).use{output->
                        input.copyTo(output);session.fsync(output)
                    }}
                    val callback=Intent(activity,ApkInstallReceiver::class.java).setAction(ApkInstallReceiver.ACTION)
                        .setData(Uri.parse("fengshen-update://session/$sessionId"))
                    val sender=PendingIntent.getBroadcast(activity,sessionId,callback,PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE).intentSender
                    ApkInstallState.begin(activity,sessionId,archive.longVersionCode)
                    ApkInstallState.record(activity,"commit")
                    session.commit(sender)
                }
            } catch(e:Exception){
                if(sessionId>=0)runCatching{activity.packageManager.packageInstaller.abandonSession(sessionId)}
                ApkInstallState.record(activity,"start_failed",e.javaClass.simpleName,"ERROR")
                ApkInstallState.failedToStart(activity,sessionId)
                activity.runOnUiThread {if(activity.isDestroyed)return@runOnUiThread;AlertDialog.Builder(activity).setTitle("安装未开始").setMessage(e.message?:"系统拒绝安装请求")
                    .setPositiveButton("知道了",null).show()}
            } finally {installing.set(false)}
        },"fengshen-apk-install").start()
    }
}

class ApkInstallReceiver:BroadcastReceiver(){
    override fun onReceive(context:Context,intent:Intent){
        if(intent.action==Intent.ACTION_MY_PACKAGE_REPLACED){ApkInstallState.reconcile(context);return}
        if(intent.action!=ACTION)return
        val outcome=ApkInstallState.receive(context,intent)
        if(outcome==InstallOutcome.WAITING && intent.getIntExtra(PackageInstaller.EXTRA_STATUS,Int.MIN_VALUE)==PackageInstaller.STATUS_PENDING_USER_ACTION){
            val confirmation=if(Build.VERSION.SDK_INT>=33)intent.getParcelableExtra(Intent.EXTRA_INTENT,Intent::class.java)
                else @Suppress("DEPRECATION") intent.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
            if(confirmation!=null)runCatching{context.startActivity(confirmation.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))}
                .onFailure{ApkInstallState.record(context,"confirmation_unavailable",it.javaClass.simpleName,"ERROR");Toast.makeText(context,"系统安装确认未打开，请返回应用重试。",Toast.LENGTH_LONG).show();runCatching{context.packageManager.packageInstaller.abandonSession(intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1))}}
            else {ApkInstallState.record(context,"confirmation_missing","missing_confirmation","ERROR");Toast.makeText(context,"系统未提供安装确认界面，请重新检查更新。",Toast.LENGTH_LONG).show();runCatching{context.packageManager.packageInstaller.abandonSession(intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,-1))}}
        } else if(outcome==InstallOutcome.CANCELLED||outcome==InstallOutcome.FAILED){
            ApkInstallState.consumeMessage(context)?.let{Toast.makeText(context,it,Toast.LENGTH_LONG).show()}
        }
    }
    companion object {const val ACTION="org.fengshen.dev.APK_INSTALL_STATUS"}
}
