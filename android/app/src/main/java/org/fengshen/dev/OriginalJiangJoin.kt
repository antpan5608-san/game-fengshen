package org.fengshen.dev

/** Scoped original action45/event21, using the existing durable dialogue queue.
 * The fourth slot is initialized after the invitation, before the Panxi script.
 * No reward, inventory change or player walking cost is inferred from NPC motion.
 */
data class OriginalJiangJoinDefinition(val evidence:String) {
    val id="rom.event.7.21"
    val pendingFlag="$id.dialogue.pending"
    val kingNpcId="rom.npc.121.3"
    val continuation=StoryContinuation(listOf("rom.dialogue.131.16","rom.dialogue.17.12")+(7..11).map{"rom.dialogue.17.$it"},
        null,null,setOf("rom.map.7.flag.128"))
    fun verified()=evidence==OriginalJiangJoin.EVIDENCE
    fun validPending(s:SaveSnapshot):Boolean {
        if(!verified())return false
        if(s.flags[pendingFlag]!=true)return true
        val stage=continuation.stage(id,s.flags)?:return false
        if(s.flags[OriginalJiangJoin.KING_FLAG]!=true||
            s.flags[OriginalJiangJoin.PANXI_FLAG]!=true||!OriginalJiangJoin.cured(s.flags)||
            continuation.dialogueIds.indices.drop(stage).any{s.flags[continuation.stageKey(id,it)]==true})return false
        return if(stage==0)s.mapId==121&&s.characters.map{it.id}==OriginalJiangJoin.PRIOR_PARTY
        else s.mapId==7&&s.x==23*16+8&&s.y==7*16+8&&s.characters.map{it.id}==OriginalJiangJoin.FULL_PARTY&&
            s.flags[OriginalJiangJoin.PANXI_FOUR_FLAG]==true&&s.flags["rom.map.7.flag.128"]!=true
    }
}

object OriginalJiangJoin {
    const val EVIDENCE="game-data/provenance/world-jiang-invitation.json"
    const val KING_FLAG="rom.map.121.flag.16"
    const val PANXI_FLAG="rom.global.7fd.nonzero"
    const val PANXI_THREE_FLAG="rom.npccontext.7.191"
    const val PANXI_FOUR_FLAG="rom.npccontext.7.192"
    val PRIOR_PARTY=listOf("nezha","xiaolongnv","yangjian")
    val FULL_PARTY=PRIOR_PARTY+"jiangziya"
    const val PANXI_PENDING="runtime.story.7.panxi.dialogue.pending"
    private const val PANXI_ID="rom.event.7.panxi61"
    private val panxiContinuation=StoryContinuation(listOf("rom.dialogue.17.13","rom.dialogue.17.6"),null,null,emptySet())
    fun validPanxiPending(s:SaveSnapshot):Boolean {
        if(s.flags[PANXI_PENDING]!=true)return true
        val stage=panxiContinuation.stage(PANXI_ID,s.flags)?:return false
        return s.mapId==7&&s.characters.size in 1..3&&s.flags[PANXI_FLAG]==true&&
            s.flags[PANXI_THREE_FLAG]==true&&s.flags[PANXI_FOUR_FLAG]!=true&&
            panxiContinuation.dialogueIds.indices.drop(stage).none{s.flags[panxiContinuation.stageKey(PANXI_ID,it)]==true}
    }
    fun panxiDialogue(s:SaveSnapshot)=panxiContinuation.stage(PANXI_ID,s.flags)?.let{panxiContinuation.dialogueIds[it]}
    fun beginPanxi(s:SaveSnapshot,rule:OriginalNpcTalkDefinition):StoryFollowup.Result {
        if(rule.actionId!=61||rule.mapId!=7||rule.firstDialogue!="rom.dialogue.17.13"||
            rule.repeatDialogue!="rom.dialogue.17.6"||rule.mapFlagId.isNotEmpty()||rule.itemId.isNotEmpty()||
            rule.witnessFlagId!=PANXI_FLAG||s.mapId!=7||s.characters.size !in 1..3||
            s.flags[PANXI_THREE_FLAG]!=true||s.flags[PANXI_FOUR_FLAG]==true||!validPanxiPending(s))
            return StoryFollowup.Result(s,null,false,"磻溪交谈对象或阶段已变化")
        if(s.flags[PANXI_PENDING]==true)return StoryFollowup.Result(s,panxiDialogue(s),true)
        // CF10 writes the witness before text13. D6EC then displays text6;
        // this happens on every talk, with no map claim bit or party change.
        val clean=s.flags-panxiContinuation.stageKey(PANXI_ID,0)-panxiContinuation.stageKey(PANXI_ID,1)
        return StoryFollowup.Result(s.copy(flags=clean+mapOf(PANXI_FLAG to true,PANXI_PENDING to true)),
            panxiContinuation.dialogueIds.first(),true)
    }
    fun advancePanxi(s:SaveSnapshot,dialogue:String):StoryFollowup.Result {
        if(!validPanxiPending(s))return StoryFollowup.Result(s,null,false,"磻溪交谈阶段已变化")
        return StoryFollowup.advance(s,PANXI_ID,PANXI_PENDING,panxiContinuation,dialogue,emptyMap()){
            it-PANXI_PENDING-panxiContinuation.stageKey(PANXI_ID,0)-panxiContinuation.stageKey(PANXI_ID,1)}
    }
    internal fun cured(flags:Map<String,Boolean>)=flags[OriginalSceneItems.PLAGUE_FLAG]==true||
        (0..7).any{flags["rom.global.7c9.${1 shl it}"]==true}
    fun begin(before:SaveSnapshot,rule:OriginalJiangJoinDefinition):StoryFollowup.Result {
        fun reject(message:String)=StoryFollowup.Result(before,null,false,message)
        if(!rule.verified()||before.mapId!=121)return reject("邀贤场景或规则未核验")
        if(before.flags[rule.pendingFlag]==true){
            if(!rule.validPending(before))return reject("邀贤存档阶段不一致")
            val stage=rule.continuation.stage(rule.id,before.flags)?:return reject("邀贤已经结束")
            return StoryFollowup.Result(before,rule.continuation.dialogueIds[stage],true)
        }
        if(before.flags[KING_FLAG]==true||!cured(before.flags))return StoryFollowup.Result(before,
            if(before.flags[KING_FLAG]==true)"rom.dialogue.131.17"else"rom.dialogue.131.14",true)
        if(before.characters.size==4)return StoryFollowup.Result(before.copy(flags=before.flags+(KING_FLAG to true)),
            "rom.dialogue.131.17",true)
        if(before.flags[PANXI_FLAG]!=true)return StoryFollowup.Result(before,"rom.dialogue.131.18",true)
        // Native code writes slot3/party4 unconditionally. Never replace an
        // unexpected saved actor to imitate that raw initializer.
        if(before.characters.map{it.id}!=PRIOR_PARTY)return reject("当前队伍与已核邀贤路线不一致，原状态已保留")
        if(rule.continuation.dialogueIds.indices.any{before.flags[rule.continuation.stageKey(rule.id,it)]==true})
            return reject("邀贤阶段记录不一致，原状态已保留")
        return StoryFollowup.Result(before.copy(flags=before.flags+mapOf(KING_FLAG to true,rule.pendingFlag to true)),
            rule.continuation.dialogueIds.first(),true)
    }
    fun advance(before:SaveSnapshot,rule:OriginalJiangJoinDefinition,currentDialogue:String,
        template:CharacterState?):StoryFollowup.Result {
        if(!rule.validPending(before))return StoryFollowup.Result(before,null,false,"邀贤存档阶段不一致")
        val stage=rule.continuation.stage(rule.id,before.flags)
        if(stage==0&&(template?.id!="jiangziya"||template.level!=38||template.experience!=190000||
            template.hp!=1608||template.maxHp!=1608||template.mp!=151||template.maxMp!=151||
            template.strength!=235||template.stamina!=109||template.agility!=63||template.spirit!=124||
            template.statusMask!=0||template.equipment!=EquipmentState(44,-1,24,28)))
            return StoryFollowup.Result(before,null,false,"姜子牙原版初始化尚未接入")
        val result=StoryFollowup.advance(before,rule.id,rule.pendingFlag,rule.continuation,currentDialogue,emptyMap()){
            it-rule.pendingFlag}
        if(!result.applied||stage!=0)return result
        return result.copy(snapshot=result.snapshot.copy(mapId=7,x=23*16+8,y=7*16+8,direction=Key.UP,
            terrainMode=0,interiorContext=null,encounterSteps=0,characters=before.characters+requireNotNull(template),
            // This script enters map7 just like a map load. Persist both
            // reconstructed context bits now so reloading remains idempotent.
            flags=OriginalNpcTalk.flagsAfterMapLoad(7,4,result.snapshot.flags)))
    }
}
