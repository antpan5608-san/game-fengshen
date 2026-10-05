package org.fengshen.dev

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** RPG snapshots only. Active save, migration backups and CI checkpoints are not history entries. */
data class SaveHistoryEntry(val id:String,val kind:Kind,val timeMillis:Long,val snapshot:SaveSnapshot) {
    enum class Kind(val label:String) { MANUAL("手动"), AUTO("自动"), BEFORE_RESTORE("回档前") }
}

object SaveHistory {
    const val KEY="saveHistoryV1"
    const val LIMIT=20
    private fun hash(text:String)=java.security.MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8)).joinToString(""){"%02x".format(it)}
    data class RestoreProposal(val target:SaveSnapshot,val entries:List<SaveHistoryEntry>)
    enum class RestoreStatus { SAVED, REJECTED, WRITE_FAILED, ROLLBACK_FAILED }

    fun prepareRestore(entries:List<SaveHistoryEntry>,id:String,current:SaveSnapshot,timeMillis:Long,
        valid:(SaveSnapshot)->Boolean):RestoreProposal? {
        val target=entries.singleOrNull{it.id==id}?.snapshot?:return null
        if(!valid(current)||!valid(target))return null
        return RestoreProposal(target,append(entries,current,SaveHistoryEntry.Kind.BEFORE_RESTORE,timeMillis))
    }
    fun applyRestore(proposal:RestoreProposal,before:SaveSnapshot,apply:(SaveSnapshot)->Boolean,
        read:()->SaveSnapshot,persist:(SaveSnapshot,List<SaveHistoryEntry>)->Boolean):RestoreStatus {
        if(!apply(proposal.target))return RestoreStatus.REJECTED
        if(persist(read(),proposal.entries))return RestoreStatus.SAVED
        return if(apply(before))RestoreStatus.WRITE_FAILED else RestoreStatus.ROLLBACK_FAILED
    }

    fun append(entries:List<SaveHistoryEntry>,snapshot:SaveSnapshot,kind:SaveHistoryEntry.Kind,
        timeMillis:Long,id:String=UUID.randomUUID().toString()):List<SaveHistoryEntry> {
        require(entries.size<=LIMIT && entries.map{it.id}.distinct().size==entries.size)
        require(timeMillis>=0 && id.matches(Regex("[a-zA-Z0-9-]{1,64}")) && entries.none{it.id==id})
        if(kind==SaveHistoryEntry.Kind.AUTO && entries.firstOrNull()?.snapshot==snapshot)return entries
        // Insertion order stays newest-first even if the wall clock changes.
        return (listOf(SaveHistoryEntry(id,kind,timeMillis,snapshot))+entries).take(LIMIT)
    }

    fun encode(entries:List<SaveHistoryEntry>):String {
        require(entries.size<=LIMIT)
        return JSONObject().put("schemaVersion",1).put("entries",JSONArray().also{a->entries.forEach{e->
            val encoded=e.snapshot.json().toString()
            a.put(JSONObject().put("id",e.id).put("kind",e.kind.name).put("timeMillis",e.timeMillis)
                .put("snapshotJson",encoded).put("snapshotSha256",hash(encoded)))
        }}).toString()
    }

    fun parse(text:String?):List<SaveHistoryEntry> {
        if(text==null)return emptyList()
        require(text.length<=4*1024*1024)
        val root=JSONObject(text);require(root.getInt("schemaVersion")==1)
        val array=root.getJSONArray("entries");require(array.length()<=LIMIT)
        val entries=(0 until array.length()).map{i->
            val e=array.getJSONObject(i)
            val encoded=e.getString("snapshotJson")
            require(encoded.length<=192*1024 && hash(encoded)==e.getString("snapshotSha256"))
            SaveHistoryEntry(e.getString("id"),SaveHistoryEntry.Kind.valueOf(e.getString("kind")),
                e.getLong("timeMillis"),SaveSnapshot.parse(encoded)).also{
                require(it.id.matches(Regex("[a-zA-Z0-9-]{1,64}"))&&it.timeMillis>=0)
            }
        }
        require(entries.map{it.id}.distinct().size==entries.size)
        return entries
    }
}

/** Foreground monotonic time; unsafe transactions defer one attempt, never produce catch-up records. */
class AutoSaveHistoryClock {
    companion object { const val INTERVAL_MS=5*60*1000L }
    private var last:Long?=null
    private var elapsed=0L
    private var retryAt=0L
    fun pause(){last=null}
    fun saved(){elapsed=0;retryAt=0}
    fun tick(now:Long,foreground:Boolean,safe:Boolean,save:()->Boolean):Boolean {
        if(!foreground){pause();return false}
        val previous=last;last=now
        if(previous!=null)elapsed=(elapsed+(now-previous).coerceAtLeast(0)).coerceAtMost(INTERVAL_MS)
        if(elapsed<INTERVAL_MS||!safe||now<retryAt)return false
        val success=save()
        if(success)saved() else retryAt=now+1000
        return success
    }
}
