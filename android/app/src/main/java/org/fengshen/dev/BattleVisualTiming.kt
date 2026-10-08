package org.fengshen.dev

/** Request-local monotonic observations. No state, asset source, cache or battle identity. */
data class BattleVisualTiming(val submittedMs:Long,val startedMs:Long,val completedMs:Long,
    val receivedMs:Long,val firstPostedMs:Long?=null) {
    companion object {const val MODEL="CURRENT_BATTLE_MONOTONIC_TO_POST_V1"}
    init {
        require(submittedMs>=0&&startedMs>=submittedMs&&completedMs>=startedMs&&receivedMs>=completedMs)
        require(firstPostedMs==null||firstPostedMs>=receivedMs)
    }
    /** Repeated frames preserve the first successfully posted observation. */
    fun posted(now:Long):BattleVisualTiming=if(firstPostedMs!=null)this else copy(firstPostedMs=now)
    fun durations():Map<String,Long> = linkedMapOf(
        "queueMs" to startedMs-submittedMs,"prepareMs" to completedMs-startedMs,
        "deliveryMs" to receivedMs-completedMs,"readyMs" to receivedMs-submittedMs).also{rows->
        firstPostedMs?.let{rows["postDelayMs"]=it-receivedMs;rows["firstPostedMs"]=it-submittedMs}
    }
}
