package org.fengshen.dev

/** Original actor action17. Proposal only; the UI commits/save-rolls back once.
 * The witness flag is written before the original message, not on page closure.
 * No reward, party join or generic NPC-seen flag is inferred.
 */
data class OriginalNpcTalkDefinition(val mapId:Int,val mapFlagId:String,val witnessFlagId:String,
    val itemId:String,val firstDialogue:String,val repeatDialogue:String) {
    // Existing constructor stays stable. Original teacher branches are explicit
    // message selectors, never an automatic multi-page reward chain.
    var actionId:Int=17;internal set
    var messageDialogues:Map<Int,String> = emptyMap();internal set
    var completionWitnessFlagId:String="";internal set
}
object OriginalNpcTalk {
    fun begin(before:SaveSnapshot,rule:OriginalNpcTalkDefinition):StoryFollowup.Result=begin(before,rule,null)
    fun begin(before:SaveSnapshot,rule:OriginalNpcTalkDefinition,item:ItemDefinition?):StoryFollowup.Result {
        fun reject(message:String)=StoryFollowup.Result(before,null,false,message)
        if(before.mapId!=rule.mapId)return reject("当前场景已变化")
        if(rule.actionId==11){
            if(rule.mapId!=171||rule.mapFlagId!="rom.map.171.flag.1"||
                rule.firstDialogue!="rom.dialogue.181.5"||rule.repeatDialogue!="rom.dialogue.181.6"||
                before.characters.size !in 1..4)return reject("道童对话规则未核验")
            val repeat=before.flags[rule.mapFlagId]==true||before.characters.size==4
            val next=if(before.characters.size==4)before.copy(flags=before.flags+(rule.mapFlagId to true))else before
            return StoryFollowup.Result(next,if(repeat)rule.repeatDialogue else rule.firstDialogue,true)
        }
        if(rule.actionId==12)return teacher(before,rule,item)
        if(rule.actionId==50)return villageFour(before,rule)
        if(rule.actionId!=17)return reject("当前对话规则未接入")
        val count=before.inventory[rule.itemId]?:0
        if(count !in 0..1)return reject("信物数量异常")
        if(before.flags[rule.mapFlagId]==true)
            return StoryFollowup.Result(before,rule.repeatDialogue,true)
        val flags=before.flags+(rule.witnessFlagId to true)+
            (if(count==1)mapOf(rule.mapFlagId to true)else emptyMap())
        return StoryFollowup.Result(before.copy(flags=flags),
            if(count==1)rule.repeatDialogue else rule.firstDialogue,true)
    }

    private fun villageFour(before:SaveSnapshot,rule:OriginalNpcTalkDefinition):StoryFollowup.Result {
        val masks=(0..6).map{1 shl it}
        val mask=rule.mapFlagId.removePrefix("rom.map.4.flag.").toIntOrNull()
        if(rule.mapId!=4||mask !in masks||rule.witnessFlagId!="rom.global.7c6.16"||rule.itemId.isNotEmpty()||
            rule.messageDialogues.keys!=setOf(0,1,2)||rule.messageDialogues[0]!=rule.firstDialogue||
            rule.messageDialogues[2]!=rule.repeatDialogue)
            return StoryFollowup.Result(before,null,false,"当前村民对话规则未核验")
        // The raw first/repeat selector skips action50 after this local bit.
        // Action50 writes it only when the actual global witness is already set.
        val message=when {
            before.flags[rule.mapFlagId]==true->2
            before.flags[rule.witnessFlagId]==true->1
            else->0
        }
        val next=if(message==1)before.copy(flags=before.flags+(rule.mapFlagId to true))else before
        return StoryFollowup.Result(next,rule.messageDialogues.getValue(message),true)
    }

    private fun teacher(before:SaveSnapshot,rule:OriginalNpcTalkDefinition,item:ItemDefinition?):StoryFollowup.Result {
        fun reject(message:String)=StoryFollowup.Result(before,null,false,message)
        if(rule.mapId!=171||rule.mapFlagId!="rom.map.171.flag.2"||rule.witnessFlagId!="rom.global.7c8.1"||
            rule.itemId!="rom.special.19"||rule.completionWitnessFlagId!="rom.global.7c7.128"||
            rule.messageDialogues.keys!=setOf(0,1,2,3,7)||before.characters.size !in 1..4)
            return reject("师父对话规则或队伍未核验")
        val count=before.inventory[rule.itemId]?:0
        if(count !in 0..1)return reject("信物数量异常")
        var message=if(before.flags[rule.mapFlagId]==true)3 else 0
        var next=before
        if(before.flags[rule.mapFlagId]!=true&&before.flags[rule.witnessFlagId]==true){
            message=1
            if(count==1){
                message=if(before.characters.size<3)2 else if(before.characters.size<4)3 else 7
                if(before.characters.size==4&&before.flags[rule.completionWitnessFlagId]==true){
                    message=3;next=before.copy(flags=before.flags+(rule.mapFlagId to true))
                }
            }else{
                if(item?.id!=rule.itemId||item.category!="special"||item.originalId!=19||item.maxCount!=1)
                    return reject("玉佩取得定义未接入")
                // Same original 16-row category capacity used by chests/trades.
                // The actual gift precedes text; failure neither invents a gift
                // nor writes a claim flag. Future talk rechecks current inventory.
                if(InventoryCapacity.hasCategorySlot(before.inventory,item.id,item.category))
                    next=before.copy(inventory=before.inventory+(item.id to 1))
            }
        }
        return StoryFollowup.Result(next,rule.messageDialogues.getValue(message),true)
    }
}
