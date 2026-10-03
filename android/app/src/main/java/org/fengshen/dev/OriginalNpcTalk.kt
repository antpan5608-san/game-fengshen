package org.fengshen.dev

/** Original actor action17. Proposal only; the UI commits/save-rolls back once.
 * The witness flag is written before the original message, not on page closure.
 * No reward, party join or generic NPC-seen flag is inferred.
 */
data class OriginalNpcTalkDefinition(val mapId:Int,val mapFlagId:String,val witnessFlagId:String,
    val itemId:String,val firstDialogue:String,val repeatDialogue:String)
object OriginalNpcTalk {
    fun begin(before:SaveSnapshot,rule:OriginalNpcTalkDefinition):StoryFollowup.Result {
        fun reject(message:String)=StoryFollowup.Result(before,null,false,message)
        if(before.mapId!=rule.mapId)return reject("当前场景已变化")
        val count=before.inventory[rule.itemId]?:0
        if(count !in 0..1)return reject("信物数量异常")
        if(before.flags[rule.mapFlagId]==true)
            return StoryFollowup.Result(before,rule.repeatDialogue,true)
        val flags=before.flags+(rule.witnessFlagId to true)+
            (if(count==1)mapOf(rule.mapFlagId to true)else emptyMap())
        return StoryFollowup.Result(before.copy(flags=flags),
            if(count==1)rule.repeatDialogue else rule.firstDialogue,true)
    }
}
