package org.fengshen.dev

import android.app.Application
import android.app.ActivityManager
import android.app.ApplicationExitInfo
import android.content.Context
import android.os.Build
import android.os.SystemClock
import androidx.work.*
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import java.util.zip.GZIPOutputStream
import java.io.ByteArrayOutputStream

class GameApplication:Application(){override fun onCreate(){super.onCreate();Diagnostics.init(this)}}
class DiagnosticTestException:RuntimeException("controlled diagnostic test")
class DiagnosticUploadWorker(context:Context,params:WorkerParameters):Worker(context,params){override fun doWork():Result=try{if(Diagnostics.upload())Result.success()else Result.retry()}catch(_:Exception){Result.retry()}}

/** Bounded application-only queue. Never receives save bodies, dialogue text or HTTP headers. */
object Diagnostics {
    const val LOCAL_LIMIT=10*1024*1024L
    const val BATCH_LIMIT=256*1024
    const val BASE="https://fleetpilots.com/fengshen-api/v1/diagnostics/"
    private lateinit var app:Context
    private val io=Executors.newSingleThreadExecutor()
    private val lock=Any()
    private val uploadLock=Any()
    @Volatile private var connection:HttpURLConnection?=null
    private val sequence=AtomicLong()
    private val lastSchedule=AtomicLong()
    private val session=UUID.randomUUID().toString()
    @Volatile var contentVersion=""
    @Volatile var contentHash=""
    @Volatile internal var testSession=false
    private val prefs get()=app.getSharedPreferences("diagnostics",0)
    private val root get()=File(app.filesDir,"diagnostics").apply{mkdirs()}
    val enabled get()=prefs.getBoolean("enabled",true)
    val installationID get()=prefs.getString("installationID",null)?:UUID.randomUUID().toString().also{prefs.edit().putString("installationID",it).commit()}
    val emulator get()=Build.FINGERPRINT.contains("generic")||Build.FINGERPRINT.contains("emulator")||Build.MODEL.contains("sdk_gphone")||Build.HARDWARE in listOf("ranchu","goldfish")
    fun init(context:Context){
        app=context.applicationContext
        val previous=Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread,error->
            if(error is DiagnosticTestException)prefs.edit().putLong("testCrashAt",System.currentTimeMillis()).commit()
            runCatching{if(enabled)synchronized(lock){write(make("uncaught_exception","FATAL",JSONObject(),error.javaClass.simpleName,error.stackTrace.take(12).joinToString("\n"),error is DiagnosticTestException))}}
            previous?.uncaughtException(thread,error)?:run{android.os.Process.killProcess(android.os.Process.myPid());kotlin.system.exitProcess(10)}
        }
        record("app_start");io.execute{runCatching{historicalExits()}}
    }
    fun setEnabled(value:Boolean){
        prefs.edit().putBoolean("enabled",value).commit()
        if(!value){connection?.disconnect();WorkManager.getInstance(app).cancelUniqueWork("fengshen-diagnostics");io.execute{synchronized(lock){root.listFiles()?.forEach{it.delete()};prefs.edit().putLong("dropped",0).commit()}}}else {lastSchedule.set(0);schedule()}
    }
    fun safeText(text:String,max:Int=2048)=text.replace(Regex("(?i)(https?://\\S+|Bearer\\s+\\S+|(?:password|token|secret|authorization|access.?key)\\s*[=:]\\s*\\S+)"),"[redacted]").take(max)
    private fun make(type:String,severity:String,details:JSONObject,code:String,stack:String,test:Boolean):JSONObject {
        val allowed=setOf("mapId","x","y","fromMapId","groupId","battleID","settlementID","experience","money","success","verificationMs","parseMs","atlasMs","audioMs","firstFrameMs","reason","exitTimestamp","trackID","assetID","stackAvailable","stage","targetVersion","installedVersion","installSessionID","status","audioInstance","audioLifecycle","audioOperations","audioThread","audioLooper","audioPlayerLooper","audioForeground","audioClosed","audioCause","timeoutOperation")
        val safe=JSONObject();details.keys().forEach{k->if(k in allowed){val v=details.get(k);if(v is String)safe.put(k,safeText(v,if(k in setOf("audioOperations","audioCause"))512 else 100)) else if(v is Number||v is Boolean)safe.put(k,v)}}
        return JSONObject().put("schemaVersion",1).put("eventID",UUID.randomUUID().toString()).put("batchID","").put("sessionID",session)
            .put("versionCode",BuildConfig.VERSION_CODE).put("versionName",BuildConfig.VERSION_NAME).put("buildID",BuildConfig.BUILD_ID)
            .put("contentVersion",contentVersion).put("contentHash",contentHash).put("clientTime",java.time.Instant.now().toString())
            .put("monotonicMs",SystemClock.elapsedRealtime()).put("sequence",sequence.incrementAndGet()).put("receivedAt","")
            .put("type",type).put("severity",severity).put("code",safeText(code,80)).put("stack",safeText(stack)).put("test",test)
            .put("device",JSONObject().put("model",Build.MODEL.take(80)).put("api",Build.VERSION.SDK_INT).put("android",Build.VERSION.RELEASE.take(30)).put("emulator",emulator)).put("details",safe)
    }
    fun record(type:String,severity:String="INFO",details:JSONObject=JSONObject(),code:String="",stack:String="",test:Boolean=false):String {
        val event=make(type,severity,details,code,stack,test||testSession)
        if(enabled)io.execute{runCatching{synchronized(lock){if(enabled)write(event)};if(severity in listOf("ERROR","FATAL")||(root.listFiles()?.size?:0)>=16)schedule()}}
        return event.getString("eventID")
    }
    private fun write(event:JSONObject){
        val destination=File(root,event.getString("eventID")+".json");val temp=File(root,destination.name+".pending")
        temp.outputStream().use{stream->stream.write(event.toString().toByteArray());stream.fd.sync()};check(temp.renameTo(destination))
        // Only abandoned queue writes; every live writer holds this same lock.
        root.listFiles()?.filter{it.extension=="pending"}?.forEach{it.delete()}
        val limit=prefs.getLong("limitBytes",LOCAL_LIMIT).coerceIn(1024*1024,LOCAL_LIMIT)
        val files=root.listFiles()?.filter{it.name!="batch.json"&&it.extension=="json"}?.sortedBy{it.lastModified()}?:emptyList()
        var total=root.listFiles()?.sumOf{it.length()}?:0;if(total<=limit)return
        // Usually one oldest INFO event must be removed. Do not parse the entire
        // full queue on each append merely to place its critical events last.
        var dropped=0L
        for(critical in listOf(false,true)){
            for(f in files){if(total<=limit)break
                if(!f.exists())continue
                val isCritical=runCatching{JSONObject(f.readText()).getString("severity") in listOf("ERROR","FATAL")}.getOrDefault(false)
                if(isCritical!=critical)continue
                val size=f.length();if(f.delete()){total-=size;dropped++}
            }
            if(total<=limit)break
        }
        prefs.edit().putLong("dropped",prefs.getLong("dropped",0)+dropped).commit()
    }
    fun schedule(){if(!enabled)return
        val now=SystemClock.elapsedRealtime();val previous=lastSchedule.get()
        if(now-previous<30000||!lastSchedule.compareAndSet(previous,now))return
        runCatching{val work=OneTimeWorkRequest.Builder(DiagnosticUploadWorker::class.java)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL,30,TimeUnit.SECONDS).build()
        WorkManager.getInstance(app).enqueueUniqueWork("fengshen-diagnostics",ExistingWorkPolicy.KEEP,work)}}
    private fun historicalExits(){
        val previous=prefs.getLong("exitWatermark",0);var newest=previous
        app.getSystemService(ActivityManager::class.java).getHistoricalProcessExitReasons(null,0,10).filter{it.timestamp>previous}.sortedBy{it.timestamp}.forEach{e->
            val critical=e.reason in listOf(ApplicationExitInfo.REASON_CRASH,ApplicationExitInfo.REASON_CRASH_NATIVE,ApplicationExitInfo.REASON_ANR)
            // This is an observation at this version, not a crash attributed to it.
            // Android exit info does not expose occurrence APK version; no stack is claimed.
            val controlled=kotlin.math.abs(e.timestamp-prefs.getLong("testCrashAt",0))<5000
            record("historical_process_exit",if(critical)"WARN" else "INFO",JSONObject().put("reason",e.reason).put("exitTimestamp",e.timestamp).put("stackAvailable",false),"occurrence_apk_version_unknown",test=controlled)
            newest=maxOf(newest,e.timestamp)
        };prefs.edit().putLong("exitWatermark",newest).commit()
    }
    private fun call(path:String,token:String?,raw:ByteArray,gzip:Boolean=false):Pair<Int,String>{
        val c=URL(BASE+path).openConnection() as HttpURLConnection
        connection=c
        c.requestMethod="POST";c.connectTimeout=10000;c.readTimeout=15000;c.instanceFollowRedirects=false;c.doOutput=true
        c.setRequestProperty("Content-Type","application/json");if(token!=null)c.setRequestProperty("Authorization","Bearer $token");if(gzip)c.setRequestProperty("Content-Encoding","gzip")
        try{if(!enabled)return 0 to "";c.outputStream.use{it.write(raw)};val status=c.responseCode;val body=(if(status in 200..299)c.inputStream else c.errorStream)?.use{it.readNBytes(32768).toString(Charsets.UTF_8)}?:"";return status to body}finally{c.disconnect();connection=null}
    }
    fun upload():Boolean=synchronized(uploadLock){uploadLocked()}
    private fun uploadLocked():Boolean {
        if(!enabled)return true
        val store=CloudTokenStore(app,"diagnostic-write-token")
        val cloud=CloudTokenStore(app).load()?.token
        var auth=if(prefs.getBoolean("sessionRejected",false))store.load()?.token else cloud?:store.load()?.token
        if(auth==null){val(status,text)=call("installations",null,JSONObject().put("installationID",installationID).toString().toByteArray());if(status!=201)return false;auth=JSONObject(text).getString("writeToken");store.save(CloudSession("diagnostics-write-only",auth))}
        for(ignored in 0..15){
            if(!enabled)return true
            val batch=synchronized(lock){
                val pending=File(root,"batch.json")
                val existing=if(pending.exists())runCatching{JSONObject(pending.readText())}.getOrElse{pending.delete();null}else null
                if(existing!=null)existing else {
                    val files=root.listFiles()?.filter{it.extension=="json"&&it.name!="batch.json"}?.sortedBy{it.lastModified()}?:emptyList()
                    val a=JSONArray();var bytes=256;var version=-1
                    for(f in files){val text=f.readText();val e=runCatching{JSONObject(text).also{it.getInt("versionCode");it.getString("eventID")}}.getOrElse{
                            f.delete();prefs.edit().putLong("dropped",prefs.getLong("dropped",0)+1).commit();null}?:continue
                        if(version==-1)version=e.getInt("versionCode");if(e.getInt("versionCode")!=version)continue
                        // Keep worst-case compressed bytes under the existing 64 KiB gateway limit.
                        if(a.length()>=128||bytes+text.toByteArray().size>60*1024)break;a.put(e);bytes+=text.toByteArray().size}
                    if(a.length()==0)null else JSONObject().put("batchID",UUID.randomUUID().toString()).put("events",a).put("dropped",prefs.getLong("dropped",0)).also{value->
                        val temp=File(root,"batch.pending");temp.outputStream().use{it.write(value.toString().toByteArray());it.fd.sync()};check(temp.renameTo(pending))}
                }
            }?:return true
            val data=batch.toString().toByteArray();val zipped=ByteArrayOutputStream().also{buffer->GZIPOutputStream(buffer).use{it.write(data)}}.toByteArray()
            if(zipped.size>64*1024)return false // Existing nginx limit applies to compressed bytes.
            if(!enabled)return true
            val(status,text)=call("batches",auth,zipped,true)
            if(status==401){if(auth==cloud)prefs.edit().putBoolean("sessionRejected",true).commit()else store.clear();return false}
            if(status !in 200..299&&!(status==410&&JSONObject(text).optString("error")=="expired_apk_version"))return false
            synchronized(lock){val a=batch.getJSONArray("events");for(i in 0 until a.length())File(root,a.getJSONObject(i).getString("eventID")+".json").delete();File(root,"batch.json").delete()
                prefs.edit().putLong("dropped",(prefs.getLong("dropped",0)-batch.optLong("dropped")).coerceAtLeast(0)).commit()}
        };return false
    }
    fun flushForTest(){io.submit{}.get(60,TimeUnit.SECONDS)}
}
