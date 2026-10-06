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
    const val ROOM116_PENDING_FLAG="runtime.story.116.actor41.dialogue.pending"
    const val ROOM116_SECOND_FLAG="runtime.story.116.actor41.dialogue8"
    const val ROOM116_EVIDENCE="game-data/provenance/world-room116-state.json"
    const val ROOM116_ACTOR_FLAG="rom.map.116.flag.1"
    const val ROOM116_COMPLETED_FLAG="rom.map.116.flag.128"
    const val HUANG_PENDING_FLAG="runtime.story.117.huang.gift.dialogue.pending"
    const val HUANG_COMPLETED_FLAG="rom.map.117.flag.2"
    const val HUANG_EVIDENCE="game-data/provenance/world-queen117-state.json"
    const val TEACHER_CONTEXT_FLAG="rom.npccontext.163.219"
    /** Actual 0:A664 map reconstruction, not a new conversation prerequisite. */
    fun flagsAfterMapLoad(mapId:Int,partyCount:Int,flags:Map<String,Boolean>):Map<String,Boolean> {
        if(mapId==7)return (flags-OriginalJiangJoin.PANXI_THREE_FLAG-OriginalJiangJoin.PANXI_FOUR_FLAG)+mapOf(
            OriginalJiangJoin.PANXI_THREE_FLAG to (partyCount<4),OriginalJiangJoin.PANXI_FOUR_FLAG to (partyCount>=4))
        if(mapId!=79)return flags
        return flags+(TEACHER_CONTEXT_FLAG to (partyCount>=3&&flags["rom.global.7c6.16"]!=true))
    }
    fun flagsAfterIslandVictory(flags:Map<String,Boolean>)=flags+(TEACHER_CONTEXT_FLAG to false)
    fun begin(before:SaveSnapshot,rule:OriginalNpcTalkDefinition):StoryFollowup.Result=begin(before,rule,null)
    fun begin(before:SaveSnapshot,rule:OriginalNpcTalkDefinition,item:ItemDefinition?):StoryFollowup.Result {
        fun reject(message:String)=StoryFollowup.Result(before,null,false,message)
        if(before.mapId!=rule.mapId)return reject("当前场景已变化")
        if(rule.actionId==45)return OriginalJiangJoin.begin(before,OriginalJiangJoinDefinition(OriginalJiangJoin.EVIDENCE))
        if(rule.actionId==61)return OriginalJiangJoin.beginPanxi(before,rule)
        if(rule.actionId==41)return room116(before,rule)
        if(rule.actionId==43)return huang117(before,rule,item)
        if(rule.actionId==1)return teacher163(before,rule,item)
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
        if(rule.actionId==52)return villageSix(before,rule)
        if(rule.actionId==31)return islandResidents(before,rule)
        if(rule.actionId==58)return jiamengRoom(before,rule)
        if(rule.actionId in listOf(44,53,54))return westernVillageWitness(before,rule)
        if(rule.actionId in listOf(55,56))return westernHouseWitness(before,rule)
        if(rule.actionId==47)return lotus136(before,rule,item)
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

    /** CD75 and original B562/B60E: no cure or fee is inferred from gift text.
     * The completed healthy-first bit is checked by the original selector before
     * the illness handler. Sick gift attempts do not set a one-time claim bit. */
    private fun lotus136(before:SaveSnapshot,rule:OriginalNpcTalkDefinition,item:ItemDefinition?):StoryFollowup.Result {
        fun reject(message:String)=StoryFollowup.Result(before,null,false,message)
        if(rule.mapId!=136||rule.mapFlagId!="rom.map.136.flag.1"||rule.witnessFlagId.isNotEmpty()||
            rule.itemId!="rom.special.0"||rule.firstDialogue!="rom.dialogue.146.1"||
            rule.repeatDialogue!="rom.dialogue.146.3"||rule.messageDialogues!=mapOf(
                1 to "rom.dialogue.146.1",2 to "rom.dialogue.146.2",3 to "rom.dialogue.146.3"))
            return reject("百草仙子对白与赠物规则未核验")
        val count=before.inventory[rule.itemId]?:0
        if(count !in 0..1)return reject("雪莲数量异常，原状态已保留")
        if(before.flags[rule.mapFlagId]==true)return StoryFollowup.Result(before,rule.repeatDialogue,true)
        val sick=(before.characters.find{it.id=="yangjian"}?.statusMask?:0)and 64!=0
        if(!sick)return StoryFollowup.Result(before.copy(flags=before.flags+(rule.mapFlagId to true)),rule.repeatDialogue,true)
        if(count==1)return StoryFollowup.Result(before,rule.messageDialogues.getValue(2),true)
        if(item?.id!=rule.itemId||item.category!="special"||item.originalId!=0||item.maxCount!=1)
            return reject("雪莲取得定义未接入")
        // Existing category capacity remains authoritative. Actual full rows
        // retain the original dialogue but cannot invent a gift or claim bit.
        if(!InventoryCapacity.hasCategorySlot(before.inventory,item.id,item.category))
            return StoryFollowup.Result(before,rule.firstDialogue,true)
        // In the currently implemented route the only newly consumed special
        // row is the paddle. Native B481/A0EB reuses that empty used row. Do not
        // keep a stale paddle witness after its ID has become the lotus.
        // Legacy snapshots did not retain arbitrary slot order: multiple empty
        // used rows cannot be silently ordered by a Kotlin/JSON map.
        val emptyUsed=before.inventory.filter{(id,n)->n==0&&InventoryCapacity.category(id)=="special"&&
            before.flags["rom.inventory.special.${id.substringAfterLast('.')}.used"]==true}.keys
        if(emptyUsed.size>1||emptyUsed.any{it !in setOf(item.id,"rom.special.14")})
            return reject("旧存档的原版空物品格顺序未接入，原状态已保留")
        val replaced=emptyUsed.singleOrNull()?.takeIf{it!=item.id}
        val next=before.copy(inventory=(if(replaced==null)before.inventory else before.inventory-replaced)+(item.id to 1),
            flags=(if(replaced==null)before.flags else before.flags-"rom.inventory.special.${replaced.substringAfterLast('.')}.used")-
                "rom.inventory.special.0.used")
        return StoryFollowup.Result(next,rule.firstDialogue,true)
    }

    /** CE9D/CECF only inspect owned/used paddle and select original dialogue.
     * These callers do not grant a paddle, equip a boat or move the player. */
    private fun westernHouseWitness(before:SaveSnapshot,rule:OriginalNpcTalkDefinition):StoryFollowup.Result {
        val expected=when(rule.mapId){41->Triple(55,1,3);42->Triple(56,2,5);else->null}
        val repeat=if(rule.mapId==41)4 else 7
        if(expected==null||rule.actionId!=expected.first||rule.mapFlagId!="rom.map.${rule.mapId}.flag.${expected.second}"||
            rule.itemId!="rom.special.14"||rule.witnessFlagId!="rom.inventory.special.14.used"||
            rule.firstDialogue!="rom.dialogue.${rule.mapId+10}.${expected.third}"||
            rule.repeatDialogue!="rom.dialogue.${rule.mapId+10}.$repeat")
            return StoryFollowup.Result(before,null,false,"当前室内物品条件对白未核验")
        val count=before.inventory[rule.itemId]?:0
        if(count !in 0..1)return StoryFollowup.Result(before,null,false,"神木槳数量异常，原状态已保留")
        // Original D18C finds the item ID in its inventory row as well as the
        // quantity used bit. A historical flag alone is not current possession;
        // the native lotus gift can reuse an empty used-paddle row for item0.
        val used=before.inventory.containsKey(rule.itemId)&&before.flags[rule.witnessFlagId]==true
        val witnessed=if(rule.actionId==55)count==1||used else used
        val next=if(witnessed)before.copy(flags=before.flags+(rule.mapFlagId to true))else before
        return StoryFollowup.Result(next,if(witnessed||before.flags[rule.mapFlagId]==true)rule.repeatDialogue else rule.firstDialogue,true)
    }

    /** Shared original CE4A/CE75 selectors. A nonzero global byte changes only
     * the message and actor map bit; it does not cure, grant, charge or join. */
    private fun westernVillageWitness(before:SaveSnapshot,rule:OriginalNpcTalkDefinition):StoryFollowup.Result {
        val expected=when(rule.mapId to rule.mapFlagId){
            8 to "rom.map.8.flag.1"->Triple(53,"rom.dialogue.18.2","rom.dialogue.18.11")
            8 to "rom.map.8.flag.8"->Triple(53,"rom.dialogue.18.5","rom.dialogue.18.12")
            9 to "rom.map.9.flag.1"->Triple(54,"rom.dialogue.19.12","rom.dialogue.19.4")
            121 to "rom.map.121.flag.1"->Triple(44,"rom.dialogue.131.5","rom.dialogue.131.6")
            121 to "rom.map.121.flag.2"->Triple(44,"rom.dialogue.131.10","rom.dialogue.131.13")
            121 to "rom.map.121.flag.4"->Triple(44,"rom.dialogue.131.11","rom.dialogue.131.6")
            else->null
        }
        if(expected==null||rule.actionId!=expected.first||rule.firstDialogue!=expected.second||
            rule.repeatDialogue!=expected.third||rule.witnessFlagId!="rom.global.7c9.nonzero"||rule.itemId.isNotEmpty())
            return StoryFollowup.Result(before,null,false,"当前村民条件对白未核验")
        val witnessed=before.flags[rule.witnessFlagId]==true||(0..7).any{
            before.flags["rom.global.7c9.${1 shl it}"]==true
        }
        if(rule.actionId==44){
            val first=rule.firstDialogue.substringAfterLast('.').toInt()
            if(rule.messageDialogues!=mapOf(0 to rule.firstDialogue,1 to "rom.dialogue.131.${first+1}",2 to rule.repeatDialogue))
                return StoryFollowup.Result(before,null,false,"守卫原版对白选择未核验")
            val seen=before.flags[rule.mapFlagId]==true
            val next=if(!seen&&witnessed)before.copy(flags=before.flags+(rule.mapFlagId to true))else before
            return StoryFollowup.Result(next,if(seen)rule.repeatDialogue else if(witnessed)rule.messageDialogues.getValue(1)else rule.firstDialogue,true)
        }
        val repeat=before.flags[rule.mapFlagId]==true||witnessed
        val next=if(witnessed)before.copy(flags=before.flags+(rule.mapFlagId to true))else before
        return StoryFollowup.Result(next,if(repeat)rule.repeatDialogue else rule.firstDialogue,true)
    }

    /** Original 10:CEF8: illness keeps the first message/bit clear. Healthy
     * first talk selects first+1 and records only this actor's bit. No cure. */
    private fun jiamengRoom(before:SaveSnapshot,rule:OriginalNpcTalkDefinition):StoryFollowup.Result {
        val first=when(rule.mapFlagId){"rom.map.37.flag.1"->0;"rom.map.37.flag.2"->2;else->null}
        if(rule.mapId!=37||first==null||rule.witnessFlagId.isNotEmpty()||rule.itemId.isNotEmpty()||
            rule.firstDialogue!="rom.dialogue.47.$first"||rule.repeatDialogue!="rom.dialogue.47.${first+1}")
            return StoryFollowup.Result(before,null,false,"当前室内对白规则未核验")
        val repeat=before.flags[rule.mapFlagId]==true
        // The original reserved Yang slot reads zero before admission. Do not
        // spawn a character or infer a new prerequisite from this read.
        val sick=(before.characters.find{it.id=="yangjian"}?.statusMask?:0) and 64 != 0
        val next=if(!repeat&&!sick)before.copy(flags=before.flags+(rule.mapFlagId to true))else before
        return StoryFollowup.Result(next,if(repeat||!sick)rule.repeatDialogue else rule.firstDialogue,true)
    }
    private fun isRoom116Rule(rule:OriginalNpcTalkDefinition)=rule.actionId==41&&rule.mapId==116&&
        rule.mapFlagId==ROOM116_ACTOR_FLAG&&rule.witnessFlagId.isEmpty()&&rule.itemId.isEmpty()&&
        rule.firstDialogue=="rom.dialogue.126.6"&&rule.repeatDialogue=="rom.dialogue.126.8"
    fun validRoom116Pending(before:SaveSnapshot):Boolean {
        if(before.flags[ROOM116_PENDING_FLAG]!=true)return before.flags[ROOM116_SECOND_FLAG]!=true
        return before.mapId==116&&before.flags[ROOM116_ACTOR_FLAG]==true&&
            kotlin.math.abs(before.x/16-5)+kotlin.math.abs(before.y/16-3)==1
    }
    fun room116PendingDialogue(flags:Map<String,Boolean>)=if(flags[ROOM116_SECOND_FLAG]==true)"rom.dialogue.126.8"else"rom.dialogue.126.6"
    private fun room116(before:SaveSnapshot,rule:OriginalNpcTalkDefinition):StoryFollowup.Result {
        fun reject(message:String)=StoryFollowup.Result(before,null,false,message)
        if(!isRoom116Rule(rule)||!validRoom116Pending(before))return reject("地牢对白规则或阶段未核验")
        if(before.flags[ROOM116_PENDING_FLAG]==true)return StoryFollowup.Result(before,room116PendingDialogue(before.flags),true)
        if(before.flags[ROOM116_ACTOR_FLAG]==true)return StoryFollowup.Result(before,rule.repeatDialogue,true)
        if(kotlin.math.abs(before.x/16-5)+kotlin.math.abs(before.y/16-3)!=1)return reject("当前交谈位置已变化")
        // Original action41/CD1A sets actor bit BEFORE text6, then schedules
        // event15/script19. This contains text8 and NPC movement, no gift or player step.
        return StoryFollowup.Result(before.copy(flags=before.flags+(ROOM116_ACTOR_FLAG to true)+(ROOM116_PENDING_FLAG to true)),rule.firstDialogue,true)
    }
    fun advanceRoom116(before:SaveSnapshot,rule:OriginalNpcTalkDefinition,currentDialogue:String):StoryFollowup.Result {
        if(!isRoom116Rule(rule)||before.flags[ROOM116_PENDING_FLAG]!=true||!validRoom116Pending(before)||
            currentDialogue!=room116PendingDialogue(before.flags))return StoryFollowup.Result(before,null,false,"地牢对白阶段已变化")
        if(before.flags[ROOM116_SECOND_FLAG]!=true)return StoryFollowup.Result(before.copy(flags=before.flags+
            (ROOM116_SECOND_FLAG to true)+("rom.npccontext.116.209" to true)),rule.repeatDialogue,true)
        return StoryFollowup.Result(before.copy(flags=(before.flags-ROOM116_PENDING_FLAG-ROOM116_SECOND_FLAG)+
            (ROOM116_COMPLETED_FLAG to true)),null,true)
    }

    private fun isHuangRule(rule:OriginalNpcTalkDefinition)=rule.actionId==43&&rule.mapId==117&&
        rule.mapFlagId==HUANG_COMPLETED_FLAG&&rule.witnessFlagId.isEmpty()&&rule.itemId=="rom.special.18"&&
        rule.firstDialogue=="rom.dialogue.127.14"&&rule.repeatDialogue==rule.firstDialogue
    /** Runtime pending records an already submitted gift, not a second original
     * claim bit. It survives process death while the original text is visible. */
    fun validHuangPending(before:SaveSnapshot):Boolean {
        if(before.flags[HUANG_PENDING_FLAG]!=true)return true
        return before.mapId==117&&before.flags[HUANG_COMPLETED_FLAG]!=true&&
            kotlin.math.abs(before.x/16-7)+kotlin.math.abs(before.y/16-3)==1&&
            (before.inventory["rom.special.18"]?:0) in 0..1
    }
    private fun huang117(before:SaveSnapshot,rule:OriginalNpcTalkDefinition,item:ItemDefinition?):StoryFollowup.Result {
        fun reject(message:String)=StoryFollowup.Result(before,null,false,message)
        if(!isHuangRule(rule)||!validHuangPending(before))return reject("黄天化赠物规则或状态未核验")
        if(before.flags[rule.mapFlagId]==true)return reject("黄天化已离开")
        if(kotlin.math.abs(before.x/16-7)+kotlin.math.abs(before.y/16-3)!=1)return reject("当前交谈位置已变化")
        if(before.flags[HUANG_PENDING_FLAG]==true)return StoryFollowup.Result(before,rule.firstDialogue,true)
        val count=before.inventory[rule.itemId]?:0
        if(count !in 0..1||item?.id!=rule.itemId||item.originalId!=18||item.category!="special"||item.maxCount!=1)
            return reject("攢心釘取得定义或数量未核验")
        // Actual B54D->B60E grants BEFORE message14. Existing/used/full
        // inventory fails the grant without inventing quantity or a retry gift.
        val inventory=if(count==0&&InventoryCapacity.hasCategorySlot(before.inventory,item.id,item.category))
            before.inventory+(item.id to 1)else before.inventory
        return StoryFollowup.Result(before.copy(inventory=inventory,flags=before.flags+(HUANG_PENDING_FLAG to true)),rule.firstDialogue,true)
    }
    fun finishHuang(before:SaveSnapshot,rule:OriginalNpcTalkDefinition,currentDialogue:String):StoryFollowup.Result {
        if(!isHuangRule(rule)||currentDialogue!=rule.firstDialogue||before.flags[HUANG_PENDING_FLAG]!=true||!validHuangPending(before))
            return StoryFollowup.Result(before,null,false,"黄天化对白阶段已变化")
        // Original closes text, writes actor bit2, runs NPC-only script20,
        // switches contexts117/145, then event16 finishes via global7FE128.
        // Original 0:D664 map145 -> $7E6; map121 -> $7D0. The old map121
        // namespace was a provenance interpretation error, not a second scene.
        // No player step, extra reward or Queen completion is inferred here.
        val flags=before.flags+(HUANG_PENDING_FLAG to false)+(HUANG_COMPLETED_FLAG to true)+
            mapOf("rom.npccontext.117.231" to false,"rom.npccontext.117.211" to true,
                "rom.npccontext.145.215" to true,"rom.global.7fe.128" to true)
        return StoryFollowup.Result(before.copy(flags=flags),null,true)
    }

    /** Read-only compatibility for saves written before the corrected RAM/map
     * mapping. An explicit current false state must never revive the old actor. */
    fun hasHuangJiamengContext(flags:Map<String,Boolean>):Boolean {
        val current="rom.npccontext.145.215"
        if(current in flags)return flags[current]==true
        return flags["rom.npccontext.121.215"]==true&&flags[HUANG_COMPLETED_FLAG]==true&&
            flags["rom.global.7fe.128"]==true
    }

    private fun teacher163(before:SaveSnapshot,rule:OriginalNpcTalkDefinition,item:ItemDefinition?):StoryFollowup.Result {
        fun reject(message:String)=StoryFollowup.Result(before,null,false,message)
        val gift=when(rule.mapId){163->9;164->8;89->1;else->return reject("师父赠予规则未核验")}
        val group=rule.mapId+10
        val mask=if(rule.mapId==89)1 else 2
        val first=if(rule.mapId==89)0 else 2
        val repeat=if(rule.mapId==89)1 else 3
        if(rule.mapFlagId!="rom.map.${rule.mapId}.flag.$mask"||rule.witnessFlagId.isNotEmpty()||
            rule.itemId!="rom.special.$gift"||rule.firstDialogue!="rom.dialogue.$group.$first"||rule.repeatDialogue!="rom.dialogue.$group.$repeat")
            return reject("师父赠予规则未核验")
        val count=before.inventory[rule.itemId]?:0
        if(count !in 0..1)return reject("秘宝数量异常")
        if(before.flags[rule.mapFlagId]==true)return StoryFollowup.Result(before,rule.repeatDialogue,true)
        if(item?.id!=rule.itemId||item.originalId!=gift||item.category!="special"||item.maxCount!=1)
            return reject("秘宝取得定义未接入")
        // Actual action1 writes the flag BEFORE B481's gift. A full category
        // keeps that flag and the original text; it cannot invent a retry gift.
        if(rule.mapId==89&&count==0&&InventoryCapacity.hasCategorySlot(before.inventory,item.id,item.category)){
            // Original A0EB reuses the current empty USED row. This segment has
            // a retained snow row; do not keep its old ID as a future map witness.
            val emptyUsed=before.inventory.filter{(id,n)->n==0&&InventoryCapacity.category(id)=="special"&&
                before.flags["rom.inventory.special.${id.substringAfterLast('.')}.used"]==true}.keys
            if(emptyUsed.size>1||emptyUsed.any{it !in setOf("rom.special.0",item.id)})
                return reject("旧存档的原版空物品格顺序未接入，原状态已保留")
            val replaced=emptyUsed.singleOrNull()
            val inventory=(if(replaced==null)before.inventory else before.inventory-replaced)+(item.id to 1)
            val flags=(if(replaced==null)before.flags else before.flags-
                "rom.inventory.special.${replaced.substringAfterLast('.')}.used")-
                "rom.inventory.special.1.used"+(rule.mapFlagId to true)
            return StoryFollowup.Result(before.copy(inventory=inventory,flags=flags),rule.firstDialogue,true)
        }
        val next=before.copy(flags=before.flags+(rule.mapFlagId to true),
            inventory=if(count==0&&InventoryCapacity.hasCategorySlot(before.inventory,item.id,item.category))
                before.inventory+(item.id to 1)else before.inventory)
        return StoryFollowup.Result(next,rule.firstDialogue,true)
    }

    private fun islandResidents(before:SaveSnapshot,rule:OriginalNpcTalkDefinition):StoryFollowup.Result {
        val mask=rule.mapFlagId.removePrefix("rom.map.78.flag.").toIntOrNull()
        if(rule.mapId!=78||mask !in listOf(1,2)||rule.witnessFlagId!="rom.global.7c6.16"||
            rule.itemId.isNotEmpty()||rule.firstDialogue!="rom.dialogue.88.${if(mask==1)0 else 1}"||
            rule.repeatDialogue!="rom.dialogue.88.2"||rule.messageDialogues!=mapOf(0 to rule.firstDialogue,2 to rule.repeatDialogue))
            return StoryFollowup.Result(before,null,false,"当前岛内居民对白规则未核验")
        val repeated=before.flags[rule.mapFlagId]==true
        val witnessed=before.flags[rule.witnessFlagId]==true
        val next=if(!repeated&&witnessed)before.copy(flags=before.flags+(rule.mapFlagId to true))else before
        return StoryFollowup.Result(next,if(repeated||witnessed)rule.repeatDialogue else rule.firstDialogue,true)
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

    /** Actual 10:CE32 selects first+1 and writes this actor's bit only after
     * global7C6 bit64. Raw first/repeat selection runs before this action.
     * It does not grant an item, move the party, or make talking a route lock.
     */
    private fun villageSix(before:SaveSnapshot,rule:OriginalNpcTalkDefinition):StoryFollowup.Result {
        val mask=rule.mapFlagId.removePrefix("rom.map.6.flag.").toIntOrNull()
        val first=mapOf(1 to 2,2 to 5,4 to 9)[mask]
        if(rule.mapId!=6||first==null||rule.witnessFlagId!="rom.global.7c6.64"||rule.itemId.isNotEmpty()||
            rule.firstDialogue!="rom.dialogue.16.$first"||rule.repeatDialogue!="rom.dialogue.16.${first+1}"||
            rule.messageDialogues!=mapOf(0 to rule.firstDialogue,1 to rule.repeatDialogue,2 to rule.repeatDialogue))
            return StoryFollowup.Result(before,null,false,"当前村民对白规则未核验")
        val repeat=before.flags[rule.mapFlagId]==true
        val witnessed=before.flags[rule.witnessFlagId]==true
        val next=if(!repeat&&witnessed)before.copy(flags=before.flags+(rule.mapFlagId to true))else before
        return StoryFollowup.Result(next,if(repeat||witnessed)rule.repeatDialogue else rule.firstDialogue,true)
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
