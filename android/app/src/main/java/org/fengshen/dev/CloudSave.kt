package org.fengshen.dev

import android.app.AlertDialog
import android.text.InputType
import android.widget.EditText
import android.widget.LinearLayout
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.Proxy
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

private const val CLOUD_BASE="https://fleetpilots.com/fengshen-api/v1/"
data class CloudSession(val username:String,val token:String)
data class RemoteSave(val revision:Long,val snapshot:SaveSnapshot)
class CloudHttpError(val status:Int,detail:String):Exception(detail)

/** Only transports player state. It never participates in movement, collision or content loading. */
class CloudSaveClient(private val proxy:Proxy?=null) {
    private fun call(method:String,path:String,token:String?=null,body:JSONObject?=null):Pair<Int,String> {
        val url=URL(CLOUD_BASE+path)
        val connection=((if(proxy==null)url.openConnection() else url.openConnection(proxy)) as HttpURLConnection).apply {
            requestMethod=method;connectTimeout=10_000;readTimeout=15_000;instanceFollowRedirects=false
            setRequestProperty("Accept","application/json")
            if(token!=null)setRequestProperty("Authorization","Bearer $token")
            if(body!=null){doOutput=true;setRequestProperty("Content-Type","application/json; charset=utf-8")}
        }
        try {
            if(body!=null)connection.outputStream.use{it.write(body.toString().toByteArray(Charsets.UTF_8))}
            val status=connection.responseCode
            val stream=if(status in 200..299)connection.inputStream else connection.errorStream
            val response=stream?.use{it.readNBytes(65_537)}?:byteArrayOf()
            require(response.size<=65_536){"云端响应过大"}
            return status to response.toString(Charsets.UTF_8)
        } finally {connection.disconnect()}
    }
    fun login(username:String,password:String):CloudSession {
        val (status,text)=call("POST","session",body=JSONObject().put("username",username).put("password",password))
        if(status!=200)throw CloudHttpError(status,"登录失败（HTTP $status）")
        val o=JSONObject(text);val token=o.getString("token");require(token.length in 40..128)
        return CloudSession(o.getString("username"),token)
    }
    fun logout(session:CloudSession){
        val (status,_)=call("DELETE","session",session.token)
        if(status !in 200..299 && status!=401)throw CloudHttpError(status,"退出失败（HTTP $status）")
    }
    fun fetch(session:CloudSession):RemoteSave? {
        val (status,text)=call("GET","save/main",session.token)
        if(status==404)return null
        if(status!=200)throw CloudHttpError(status,"读取云存档失败（HTTP $status）")
        val o=JSONObject(text)
        return RemoteSave(o.getLong("revision"),SaveSnapshot.parse(o.getJSONObject("snapshot").toString()))
    }
    fun upload(session:CloudSession,baseRevision:Long,snapshot:SaveSnapshot):RemoteSave {
        val body=JSONObject().put("baseRevision",baseRevision).put("snapshot",snapshot.json())
        val (status,text)=call("PUT","save/main",session.token,body)
        if(status==409)throw CloudHttpError(status,"云端已有更新，已暂停上传；重新登录后选择保留哪个进度")
        if(status!=200)throw CloudHttpError(status,"上传云存档失败（HTTP $status）")
        val o=JSONObject(text)
        return RemoteSave(o.getLong("revision"),SaveSnapshot.parse(o.getJSONObject("snapshot").toString()))
    }
}

class CloudController(private val activity:MainActivity,private val view:()->GameView?) {
    private val client=CloudSaveClient()
    private val tokens=CloudTokenStore(activity)
    private val worker=Executors.newSingleThreadExecutor()
    private val uploading=AtomicBoolean(false)
    @Volatile private var session:CloudSession?=null
    @Volatile private var revision=0L
    @Volatile private var pending:SaveSnapshot?=null
    @Volatile private var synced:SaveSnapshot?=null
    @Volatile private var conflict=false
    @Volatile var status="未登录 · 本地可继续游玩";private set
    fun close(){worker.shutdownNow()}

    fun resume(){
        val remembered=tokens.load()?:return
        status="正在读取云端进度…"
        worker.execute{
            try{val remote=client.fetch(remembered);activity.runOnUiThread{if(!activity.isDestroyed)onLogin(remembered,remote)}}
            catch(e:Exception){activity.runOnUiThread{
                if(activity.isDestroyed)return@runOnUiThread
                if(e is CloudHttpError&&e.status==401){tokens.clear();status="登录已过期，请重新登录"}
                else status="云端暂不可用 · 本地进度保留"
            }}
        }
    }

    fun open(){
        val current=session
        if(current!=null){
            AlertDialog.Builder(activity).setTitle("封神云存档").setMessage("账号 ${current.username}\n$status\n云端仅保存已成功同步的进度。")
                .setPositiveButton("立即同步"){_,_->view()?.let{queue(it.currentSnapshot())}}
                .setNeutralButton("退出账号"){_,_->
                    session=null;pending=null;synced=null;conflict=false;tokens.clear();status="已退出 · 本地进度保留"
                    worker.execute{runCatching{client.logout(current)}}
                }
                .setNegativeButton("关闭",null).show()
            return
        }
        loginDialog()
    }

    private fun loginDialog(){
        val username=EditText(activity).apply{hint="封神账号";setSingleLine();setText(activity.getSharedPreferences("cloud-ui",0).getString("username",""))}
        val password=EditText(activity).apply{hint="密码";setSingleLine();inputType=InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD}
        val form=LinearLayout(activity).apply{
            orientation=LinearLayout.VERTICAL
            val pad=(20*activity.resources.displayMetrics.density).toInt();setPadding(pad,pad,pad,pad)
            addView(username);addView(password)
        }
        AlertDialog.Builder(activity).setTitle("登录封神云存档").setMessage("登录后读取 PostgreSQL 中的个人进度；离线仍可使用本地进度。")
            .setView(form).setNegativeButton("稍后",null).setPositiveButton("登录"){_,_->
                val name=username.text.toString().trim();val secret=password.text.toString();password.text.clear()
                if(name.isBlank()||secret.isBlank()){show("请输入账号和密码");return@setPositiveButton}
                status="正在登录并读取云端…"
                worker.execute{
                    try {
                        val login=client.login(name,secret)
                        val remote=client.fetch(login)
                        activity.runOnUiThread{if(!activity.isDestroyed)onLogin(login,remote)}
                    } catch(e:Exception){activity.runOnUiThread{if(!activity.isDestroyed){status="登录或读取失败，云端未覆盖本地";show(e.message?:"网络不可用")}}}
                }
            }.show()
    }

    private fun onLogin(login:CloudSession,remote:RemoteSave?){
        val game=view()?:return
        activity.getSharedPreferences("cloud-ui",0).edit().putString("username",login.username).apply()
        val local=game.currentSnapshot()
        if(remote==null){session=login;tokens.save(login);revision=0;synced=null;conflict=false;status="首次同步中";queue(local);return}
        if(!remote.snapshot.validate(game.content)){
            status="云存档内容版本与当前 APK 不兼容，未覆盖本地";show(status);return
        }
        val remoteCurrent=remote.snapshot.copy(contentVersion=game.content.scene.version)
        if(!game.hasMeaningfulLocalSave()||local==remoteCurrent){
            session=login;tokens.save(login);revision=remote.revision;synced=remote.snapshot;conflict=false
            if(local!=remoteCurrent)game.restoreSnapshot(remote.snapshot)
            else if(local!=remote.snapshot)queue(local)
            status="已从云端恢复 · 修订 ${remote.revision}"
            show(status);return
        }
        AlertDialog.Builder(activity).setTitle("发现两份不同的进度")
            .setMessage("云端：地图 ${remote.snapshot.mapId}，格 ${remote.snapshot.x/16},${remote.snapshot.y/16}（修订 ${remote.revision}）\n"+
                "本机：地图 ${local.mapId}，格 ${local.x/16},${local.y/16}\n请选择保留哪份；不会自动覆盖。")
            .setPositiveButton("使用云端"){_,_->
                session=login;tokens.save(login);revision=remote.revision;synced=remote.snapshot;conflict=false
                game.backupBeforeCloudRestore();game.restoreSnapshot(remote.snapshot)
                status="已从云端恢复 · 修订 ${remote.revision}"
            }.setNeutralButton("使用本机"){_,_->
                session=login;tokens.save(login);revision=remote.revision;synced=remote.snapshot;conflict=false
                status="正在上传本机进度";queue(local)
            }.setNegativeButton("暂不处理",null).show()
    }

    fun onLocalSaved(snapshot:SaveSnapshot){if(session!=null&&snapshot!=synced&&!conflict)queue(snapshot)}
    private fun queue(snapshot:SaveSnapshot){
        if(session==null)return
        pending=snapshot
        if(!uploading.compareAndSet(false,true))return
        worker.execute{
            var failed=false
            try {
                while(true){
                    val next=pending?:break;pending=null
                    val account=session?:break
                    try {
                        val saved=client.upload(account,revision,next)
                        revision=saved.revision;synced=next
                        activity.runOnUiThread{status="已同步云端 · 修订 ${saved.revision}"}
                    } catch(e:Exception){
                        failed=true
                        if(e is CloudHttpError&&e.status==409)conflict=true
                        activity.runOnUiThread{status=if(conflict)"云端冲突 · 已停止上传，需重新登录选择进度" else "离线待同步 · ${e.message}"}
                        break
                    }
                }
            } finally {
                uploading.set(false)
                val latest=pending
                if(latest!=null&&!failed&&!conflict&&session!=null)queue(latest)
            }
        }
    }
    private fun show(message:String){AlertDialog.Builder(activity).setTitle("封神云存档").setMessage(message).setPositiveButton("知道了",null).show()}
}
