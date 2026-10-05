package org.fengshen.dev

import kotlin.math.max

/** ROM map-16 zone 0. Coordinates are world metatile coordinates, not camera pixels. */
data class EncounterRect(val left:Int,val top:Int,val right:Int,val bottom:Int) {
    fun contains(x:Int,y:Int)=x>left && x<=right && y>top && y<=bottom
}
data class EncounterMember(val slot:Int,val enemyId:Int)
data class EncounterGroup(val id:Int,val members:List<EncounterMember>,val zoneId:Int=0)
data class EncounterZone(val mapId:Int,val rectangles:List<EncounterRect>,val groups:List<EncounterGroup>,
    val randomThreshold:Int,val highGate:Boolean=false) {
    fun contains(map:Int,x:Int,y:Int)=map==mapId&&(rectangles.isEmpty()||rectangles.any{it.contains(x,y)})
}
data class EnemyDefinition(val id:Int,val name:String,val hp:Int,val attack:Int,val defense:Int,
    val experienceReward:Int,val moneyReward:Int,val hitByte:Int,val behaviorByte:Int,
    val iceBaseDamage:Int?=null,val loot:BattleLoot?=null) {
    // Behavior1 name is not transcribed; keep it distinct from verified ice.
    var specialBaseDamage:Int?=null;internal set
    var requiredBindingMarker:Int=0;internal set
}
/** Original 9:AC32 marker comparison. A victory flag is never an immunity bypass. */
fun originalBoundTargetDamage(requiredMarker:Int,battleMarker:Int,computed:Int):Int {
    require(requiredMarker in 0..5&&battleMarker in 0..255&&computed in 0..65535)
    return if(requiredMarker!=0&&battleMarker!=requiredMarker)0 else computed
}
internal data class OriginalBindingProfile(val itemId:Int,val marker:Int,val originalTargets:Set<Int>,
    val enabledGroups:Set<Set<Int>>,val itemEvidence:String,val protectionEvidence:String,
    val targetKey:String,val targetLabel:String)
/** One scoped registry shared by content validation and command eligibility.
 * Enabled groups remain narrower than the original effect's full target domain. */
internal fun originalBindingProfile(itemId:Int):OriginalBindingProfile?=when(itemId){
    9->OriginalBindingProfile(9,1,(152..155).toSet(),setOf((152..155).toSet()),
        "game-data/provenance/world-teacher163-binding.json","game-data/provenance/world-island-binding.json",
        "four-villains-current-battle","四恶人")
    13->OriginalBindingProfile(13,2,setOf(157,174),setOf(setOf(157)),
        "game-data/provenance/world-queen117-state.json","game-data/provenance/world-queen117-state.json",
        "queen-current-battle","女王")
    18->OriginalBindingProfile(18,5,(158..161).toSet(),setOf(setOf(158),setOf(159,160,161)),
        "game-data/provenance/world-jiameng-binding.json","game-data/provenance/world-jiameng-binding.json",
        "four-generals-current-battle","魔家四将")
    else->null
}
internal fun originalProtectionProfile(enemyId:Int):OriginalBindingProfile?=
    listOf(9,13,18).mapNotNull(::originalBindingProfile).firstOrNull{p->
        p.enabledGroups.any{enemyId in it}}
/** Original special9/13/18 dispatcher. The marker is local to this battle;
 * matching a name, owning the item or a past victory never applies the effect. */
fun originalSpecialBindingMarker(itemId:Int,targetEnemyId:Int,before:Int):Int {
    require(itemId in 0..255&&targetEnemyId in 0..255&&before in 0..255)
    val profile=originalBindingProfile(itemId)?:return before
    return if(targetEnemyId in profile.originalTargets)profile.marker else before
}
data class BattleLoot(val itemId:String,val threshold:Int,val category:String)
data class PhysicalRules(val weaponHitThreshold:Map<Int,Int>,val multiplierThresholds:List<Int>) {
    init {require(weaponHitThreshold.isNotEmpty()&&weaponHitThreshold.all{(id,n)->id in -1..255&&n in 0..64}&&
        multiplierThresholds.size==36&&multiplierThresholds.all{it in 0..255})}
    fun hits(weapon:Int,roll:Int)=(roll and 63)<weaponHitThreshold.getValue(weapon)
    fun multiplier(level:Int,roll:Int):Int {
        val bucket=((level-1)/5).coerceIn(0,11);var multiplier=1
        while(multiplier<4&&roll>=multiplierThresholds[bucket+12*(multiplier-1)])multiplier++
        return multiplier
    }
    fun damage(attack:Int,defense:Int,level:Int,roll:Int):Int =
        if(attack<defense)1 else ((attack-defense)*multiplier(level,roll)) and 65535
}
data class StoryEntryTrigger(val mapId:Int,val x:Int,val y:Int)
data class StoryDestination(val mapId:Int,val x:Int,val y:Int,val direction:Key?,val terrainMode:Int?,
    val encounterSteps:Int?)
/** Existing-member scene effects: never create, reorder or replace a saved actor.
 * Content validation must bind each definition to its original script evidence. */
data class StoryCharacterChange(val characterId:String,val statusAndMask:Int=255,val statusOrMask:Int=0,
    val restoreHp:Boolean=false,val restoreMp:Boolean=false) {
    init {require(characterId.matches(Regex("[a-z0-9_-]{1,64}"))&&statusAndMask in 0..255&&statusOrMask in 0..255)}
}
fun applyStoryCharacterChanges(before:List<CharacterState>,changes:List<StoryCharacterChange>):List<CharacterState>? {
    if(before.map{it.id}.distinct().size!=before.size||changes.map{it.characterId}.distinct().size!=changes.size)return null
    if(changes.any{change->before.none{it.id==change.characterId}})return null
    val definitions=changes.associateBy{it.characterId}
    val result=mutableListOf<CharacterState>()
    for(actor in before){
        val change=definitions[actor.id]
        if(change==null){result.add(actor);continue}
        if(actor.statusMask !in 0..255||change.restoreHp&&actor.maxHp !in 1..9999||
            change.restoreMp&&(actor.maxMp==null||actor.maxMp !in 0..9999))return null
        result.add(actor.copy(statusMask=(actor.statusMask and change.statusAndMask)or change.statusOrMask,
            hp=if(change.restoreHp)actor.maxHp else actor.hp,mp=if(change.restoreMp)actor.maxMp!! else actor.mp))
    }
    return result
}
data class StoryContinuation(val dialogueIds:List<String>,val joinCharacterId:String?,
    val destination:StoryDestination?,val completionFlags:Set<String>) {
    // Optional scoped event6 fields keep the existing constructor ABI.
    var departureCharacterId:String?=null;internal set
    var movementsBeforeDialogue:Map<Int,StoryMovement> = emptyMap();internal set
    var characterChanges:List<StoryCharacterChange> = emptyList();internal set
    init {
        require(dialogueIds.isNotEmpty()&&dialogueIds.size<=64&&dialogueIds.all{it.isNotBlank()})
        require(dialogueIds.distinct().size==dialogueIds.size)
        require(completionFlags.all{it.isNotBlank()&&it.length<=96})
        destination?.let{require(it.mapId in 0..255&&it.x>=0&&it.y>=0&&(it.terrainMode==null||it.terrainMode in 0..255)&&
            (it.direction==null||it.direction in setOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT))&&(it.encounterSteps==null||it.encounterSteps in 0..255))}
    }
    fun stageKey(storyId:String,index:Int)="runtime.story.$storyId.continuation.$index"
    fun stage(storyId:String,flags:Map<String,Boolean>)=dialogueIds.indices.firstOrNull{flags[stageKey(storyId,it)]!=true}
}

/** Original scene scripts use the same durable dialogue continuation as Boss followup.
 * Cutscene movement proposals retain the witnessed completed-step poison costs. */
data class StoryMovement(val destination:StoryDestination,val completedSteps:Int) {
    init {require(completedSteps in 0..32)}
    var accumulateEncounterSteps:Boolean=false;internal set
}
data class SceneStoryDefinition(val id:String,val npcId:String,val flagId:String,
    val entryTrigger:StoryEntryTrigger,val continuation:StoryContinuation,
    val openingMovement:StoryMovement,val movementsBeforeDialogue:Map<Int,StoryMovement>) {
    // NPC interaction is validated by the map UI; approaching is not a command.
    var manualActivation:Boolean=false;internal set
    var additionalEntryTriggers:Set<StoryEntryTrigger> = emptySet();internal set
    var preserveOpeningPosition:Boolean=false;internal set
    val pendingFlag get()=flagId+".dialogue.pending"
    fun triggersAt(snapshot:SaveSnapshot)=snapshot.mapId==entryTrigger.mapId&&
        (manualActivation||(snapshot.x/16==entryTrigger.x&&snapshot.y/16==entryTrigger.y)||
            StoryEntryTrigger(snapshot.mapId,snapshot.x/16,snapshot.y/16) in additionalEntryTriggers)&&
        snapshot.flags[flagId]!=true&&snapshot.flags[pendingFlag]!=true
    fun automaticallyTriggersAt(snapshot:SaveSnapshot)=!manualActivation&&triggersAt(snapshot)
    fun pendingDialogue(flags:Map<String,Boolean>)=continuation.stage(id,flags)?.let{continuation.dialogueIds[it]}
    fun validPending(snapshot:SaveSnapshot):Boolean {
        if(snapshot.flags[pendingFlag]!=true)return true
        if(snapshot.mapId!=entryTrigger.mapId||snapshot.flags[flagId]==true)return false
        val stage=continuation.stage(id,snapshot.flags)?:return false
        return continuation.dialogueIds.indices.drop(stage).all{snapshot.flags[continuation.stageKey(id,it)]!=true}
    }
}

/** One durable dialogue-step proposal; rendering never moves the party or grants actors. */
object StoryFollowup {
    data class Result(val snapshot:SaveSnapshot,val nextDialogue:String?,val applied:Boolean,val error:String?=null)
    fun advance(before:SaveSnapshot,story:StoryBattleDefinition,currentDialogue:String,
        templates:Map<String,CharacterState>):Result {
        val chain=story.continuation?:return Result(before,null,false,"当前剧情没有后续阶段")
        if(!story.validScopedContinuation(before))return Result(before,null,false,"剧情存档阶段不一致")
        val result=advance(before,story.id,story.pendingFlag,chain,currentDialogue,templates,story::completeDialogue)
        if(!result.applied||result.nextDialogue==null)return result
        val index=chain.stage(story.id,result.snapshot.flags)?:return result
        return chain.movementsBeforeDialogue[index]?.let{result.copy(snapshot=move(result.snapshot,it))}?:result
    }
    fun approachBattle(before:SaveSnapshot,story:StoryBattleDefinition):Result {
        val movement=story.approach?:return Result(before,null,false,"剧情没有前行阶段")
        if(before.flags[story.approachFlag]==true||story.alreadyWon(before.flags)||
            story.entryTrigger!=StoryEntryTrigger(before.mapId,before.x/16,before.y/16))
            return Result(before,null,false,"剧情前行状态已变化")
        return Result(move(before.copy(flags=before.flags+(story.approachFlag to true)),movement),null,true)
    }
    fun begin(before:SaveSnapshot,story:SceneStoryDefinition):Result {
        if(!story.triggersAt(before)||story.continuation.dialogueIds.indices.any{
                before.flags[story.continuation.stageKey(story.id,it)]==true})
            return Result(before,null,false,"场景剧情状态已变化")
        val pending=before.copy(flags=before.flags+(story.pendingFlag to true))
        if(story.manualActivation||story.preserveOpeningPosition){
            require(story.openingMovement.completedSteps==0&&story.movementsBeforeDialogue.isEmpty())
            return Result(pending,story.continuation.dialogueIds.first(),true)
        }
        return Result(move(pending,story.openingMovement),story.continuation.dialogueIds.first(),true)
    }
    fun advance(before:SaveSnapshot,story:SceneStoryDefinition,currentDialogue:String):Result {
        if(!story.validPending(before))return Result(before,null,false,"场景剧情存档状态不一致")
        val result=advance(before,story.id,story.pendingFlag,story.continuation,currentDialogue,emptyMap()){
            (it+(story.flagId to true))-story.pendingFlag}
        if(!result.applied||result.nextDialogue==null)return result
        val index=story.continuation.stage(story.id,result.snapshot.flags)?:return result
        return story.movementsBeforeDialogue[index]?.let{result.copy(snapshot=move(result.snapshot,it))}?:result
    }
    fun advance(before:SaveSnapshot,rule:OriginalYangJoinDefinition,currentDialogue:String):Result {
        if(!rule.validPending(before))return Result(before,null,false,"入队对话存档状态不一致")
        return advance(before,rule.id,rule.pendingFlag,rule.continuation,currentDialogue,emptyMap()){
            (it+("rom.map.110.flag.128" to true))-rule.pendingFlag}
    }
    private fun move(before:SaveSnapshot,movement:StoryMovement):SaveSnapshot {
        // Original witnessed cutscenes on these maps; no cross-map shortcut.
        require(movement.destination.mapId==before.mapId)
        if(movement.completedSteps==0)require(before.x/16==movement.destination.x&&
            before.y/16==movement.destination.y) // NPC-only scripts cannot teleport the player.
        else require(before.mapId in setOf(86,76,87)) // Positive movement still needs its scoped original trace.
        if(before.mapId==117)require(movement.completedSteps==0)
        var party=before.characters
        repeat(movement.completedSteps){party=OriginalStatus.step(party,before.mapId)}
        val d=movement.destination
        return before.copy(x=d.x*16+8,y=d.y*16+8,direction=d.direction?:before.direction,
            characters=party,terrainMode=d.terrainMode?:before.terrainMode,
            encounterSteps=if(movement.accumulateEncounterSteps)(before.encounterSteps+movement.completedSteps) and 255
                else d.encounterSteps?:before.encounterSteps)
    }
    internal fun advance(before:SaveSnapshot,storyId:String,pendingFlag:String,chain:StoryContinuation,
        currentDialogue:String,templates:Map<String,CharacterState>,complete:(Map<String,Boolean>)->Map<String,Boolean>):Result {
        fun reject(reason:String)=Result(before,null,false,reason)
        if(before.flags[pendingFlag]!=true)return reject("剧情状态已变化")
        val index=chain.stage(storyId,before.flags)?:return reject("后续剧情已经完成")
        if(chain.dialogueIds[index]!=currentDialogue)return reject("对话阶段已变化")
        val progressed=before.flags+(chain.stageKey(storyId,index) to true)
        if(index+1<chain.dialogueIds.size)
            return Result(before.copy(flags=progressed),chain.dialogueIds[index+1],true)
        val characters=before.characters.toMutableList()
        chain.joinCharacterId?.let{id->
            val actor=templates[id]?:return reject("入队角色数据未接入")
            if(characters.any{it.id==id}||characters.size>=4)return reject("当前队伍与入队剧情不一致")
            characters.add(actor)
        }
        chain.departureCharacterId?.let{id->
            val index=characters.indexOfFirst{it.id==id}
            if(index<0)return reject("离队角色记录缺失")
            // Original CE91..CE98 retains all actor data, ORs bit40 only.
            characters[index]=characters[index].copy(statusMask=characters[index].statusMask or 64)
        }
        val changed=applyStoryCharacterChanges(characters,chain.characterChanges)
            ?:return reject("剧情角色状态与已核变化不一致，原存档已保留")
        val completed=complete(progressed)+chain.completionFlags.associateWith{true}
        val next=before.copy(characters=changed,flags=completed)
        val destination=chain.destination?:return Result(next,null,true)
        return Result(next.copy(mapId=destination.mapId,x=destination.x*16+8,y=destination.y*16+8,
            direction=destination.direction?:before.direction,terrainMode=destination.terrainMode?:before.terrainMode,interiorContext=null,
            encounterSteps=destination.encounterSteps?:before.encounterSteps),null,true)
    }
}

data class StoryBattleDefinition(val id:String,val npcId:String,val flagId:String,val group:EncounterGroup,
    val victoryDialogue:String) {
    // Keep the existing constructor ABI for cross-APK instrumentation. Set only by ContentLoader.
    var entryTrigger:StoryEntryTrigger?=null;internal set
    var commitAfterDialogue:Boolean=false;internal set
    var continuation:StoryContinuation?=null;internal set
    var victoryFlags:Set<String> = emptySet();internal set
    var intro:SceneStoryDefinition?=null;internal set
    var approach:StoryMovement?=null;internal set
    val approachFlag get()="runtime.story.$id.approach.complete"
    var finalizeWithoutDialogue:Boolean=false;internal set
    var activationFlagId:String?=null;internal set
    var additionalEntryTriggers:Set<StoryEntryTrigger> = emptySet();internal set
    var victoryCharacterChanges:List<StoryCharacterChange> = emptyList();internal set
    fun charactersOnVictory(before:List<CharacterState>):List<CharacterState>? {
        // Original event1 writes the reserved Yang slot even when no saved actor
        // exists. Preserve the actual roster; never invent a template or gate.
        val changes=if(id=="rom.boss.159"&&npcId=="rom.npc.148.0"&&
            victoryCharacterChanges==listOf(StoryCharacterChange("yangjian",statusOrMask=64)))
            victoryCharacterChanges.filter{change->before.any{it.id==change.characterId}}
            else victoryCharacterChanges
        return applyStoryCharacterChanges(before,changes)
    }
    fun activeIn(flags:Map<String,Boolean>)=activationFlagId?.let{flag->
        if(flag=="rom.npccontext.145.215")OriginalNpcTalk.hasHuangJiamengContext(flags) else flags[flag]==true
    }?:true
    val pendingFlag get()=flagId+".dialogue.pending"
    fun pendingDialogue(flags:Map<String,Boolean>):String=continuation?.let{c->c.stage(id,flags)?.let{c.dialogueIds[it]}}?:victoryDialogue
    fun alreadyWon(flags:Map<String,Boolean>)=flags[flagId]==true||flags[pendingFlag]==true
    fun triggersAt(mapId:Int,x:Int,y:Int,flags:Map<String,Boolean>)=
        activeIn(flags)&&!alreadyWon(flags)&&(entryTrigger==StoryEntryTrigger(mapId,x,y)||
            StoryEntryTrigger(mapId,x,y) in additionalEntryTriggers||intro?.let{i->
            flags[i.flagId]==true&&i.continuation.destination?.let{it.mapId==mapId&&it.x==x&&it.y==y}==true}==true||
            approach?.destination?.let{flags[approachFlag]==true&&it.mapId==mapId&&it.x==x&&it.y==y}==true)
    fun validScopedContinuation(snapshot:SaveSnapshot):Boolean {
        if(approach==null)return true // Existing story definitions retain their policy.
        if(snapshot.flags[approachFlag]==true&&!alreadyWon(snapshot.flags)){
            val d=approach!!.destination
            if(snapshot.mapId!=d.mapId||snapshot.x/16!=d.x||snapshot.y/16!=d.y)return false
        }
        if(snapshot.flags[pendingFlag]!=true)return true
        val chain=continuation?:return false
        if(snapshot.flags[flagId]==true||snapshot.mapId!=entryTrigger?.mapId)return false
        val index=chain.stage(id,snapshot.flags)?:return false
        if(chain.dialogueIds.indices.drop(index).any{snapshot.flags[chain.stageKey(id,it)]==true})return false
        val expected=chain.movementsBeforeDialogue.filterKeys{it<=index}.maxByOrNull{it.key}?.value?.destination
            ?:approach!!.destination
        return snapshot.x/16==expected.x&&snapshot.y/16==expected.y&&
            chain.departureCharacterId?.let{actor->snapshot.characters.any{it.id==actor}}!=false
    }
    fun rewardFlags(flags:Map<String,Boolean>):Map<String,Boolean> {
        val next=(if(commitAfterDialogue)flags else flags+(flagId to true))+victoryFlags.associateWith{true}+
            (if(finalizeWithoutDialogue)emptyMap()else mapOf(pendingFlag to true))
        return if(id=="rom.boss.152"&&flagId=="rom.map.76.flag.128"&&"rom.global.7c6.16" in victoryFlags)
            OriginalNpcTalk.flagsAfterIslandVictory(next)else next
    }
    fun completeDialogue(flags:Map<String,Boolean>):Map<String,Boolean> =
        if(alreadyWon(flags))((flags+(flagId to true))-pendingFlag)-approachFlag else flags
}
/** Structural/implemented-behavior checks; witnessed opening sizes belong in golden tests. */
fun validEncounterGroup(group:EncounterGroup,enemies:Map<Int,EnemyDefinition>):Boolean =
    group.id in 0..255&&group.members.size in 1..7&&group.members.map{it.slot}.distinct().size==group.members.size&&
        group.members.all{it.slot in 0..6&&enemies[it.enemyId]?.let(OriginalStatus::enemySupported)==true}
data class GrowthRow(val level:Int,val threshold:Int,val hp:Int,val mp:Int,val strength:Int,
    val stamina:Int,val agility:Int,val spirit:Int,val runtimeVerified:Boolean)
/** Seven-byte original growth row: HP is uint16, the remaining deltas uint8. */
fun validGrowthRow(row:GrowthRow):Boolean = row.level in 2..99 && row.threshold in 1..0xffffff &&
    row.hp in 0..65535 && listOf(row.mp,row.strength,row.stamina,row.agility,row.spirit).all{it in 0..255}

data class BattleContent(val zoneMapId:Int,val zoneRects:List<EncounterRect>,val groups:List<EncounterGroup>,
    val enemies:Map<Int,EnemyDefinition>,val growth:List<GrowthRow>,val armorContribution:Int,
    val minimumSteps:Int,val forcedSteps:Int,val hitThreshold:Int,
    val enemyAgility:Map<Int,Int> = emptyMap(),val escapeEnabled:Boolean=false,val defeatResetEnabled:Boolean=false,
    val zones:List<EncounterZone> = emptyList(),val physicalRules:PhysicalRules?=null,
    val storyBattles:Map<String,StoryBattleDefinition> = emptyMap(),val knownMaxLevel:Int?=null) {
    // Constructor remains ABI-compatible with published APK instrumentation.
    var characterPhysicalRules:Map<String,PhysicalRules> = emptyMap();internal set
    fun physicalFor(owner:String)=if(owner=="nezha")physicalRules else characterPhysicalRules[owner]
    var characterGrowth:Map<String,List<GrowthRow>> = emptyMap();internal set
    var characterLevelLimits:Map<String,Int> = emptyMap();internal set
    fun growthFor(owner:String)=if(owner=="nezha")growth else characterGrowth[owner]?:emptyList()
    fun maxLevelFor(owner:String)=if(owner=="nezha")knownMaxLevel else characterLevelLimits[owner]
}


/** The ROM increments $5B on a completed metatile movement and tests $43 at a tile-aligned checkpoint.
 * Android samples an independent byte, so the random sequence is explicitly not NES-equivalent. */
class OpeningEncounter(private val content:BattleContent,initialSteps:Int=0) {
    var steps=initialSteps.coerceIn(0,255);private set
    fun onCompletedStep(mapId:Int,x:Int,y:Int,nextByte:()->Int):EncounterGroup? {
        steps=(steps+1).coerceAtMost(255)
        val zone=content.zones.firstOrNull{it.contains(mapId,x,y)}
            ?: if(mapId==content.zoneMapId&&content.zoneRects.any{it.contains(x,y)})
                EncounterZone(content.zoneMapId,content.zoneRects,content.groups,content.hitThreshold) else return null
        if(steps<content.minimumSteps)return null
        if(steps<content.forcedSteps){
            val roll=nextByte().also{require(it in 0..255)}
            if(if(zone.highGate)roll<zone.randomThreshold else roll>=zone.randomThreshold)return null
        }
        val groupByte=nextByte().also{require(it in 0..255)}
        steps=0
        return zone.groups[(groupByte and 31)%zone.groups.size]
    }
    fun restore(value:Int){require(value in 0..255);steps=value}
}

data class BattleEnemy(val slot:Int,val definition:EnemyDefinition,var hp:Int)
enum class BattlePhase { TARGET, VICTORY, DEFEAT, ESCAPED }
enum class BattleActionKind { TEXT, ATTACK, ICE, DAMAGE, MISS, DEATH, ESCAPE, ESCAPED, ESCAPE_FAILED, HEAL, STATUS, SPECIAL }
data class BattleActionStep(val text:String,val heroHp:Int,val enemyHp:Map<Int,Int>,
    val actorSlot:Int?=null,val targetSlot:Int?=null,val kind:BattleActionKind=BattleActionKind.TEXT,
    val hpDelta:Int=0,val beforeHeroHp:Int=heroHp,val beforeEnemyHp:Int?=null,val heroStatusMask:Int=0) {
    var actorId:String?=null;internal set
    var targetId:String?=null;internal set
    var partyHp:Map<String,Int> = emptyMap();internal set
    var partyStatus:Map<String,Int> = emptyMap();internal set
}
data class BattleTurn(val playerDamage:Int,val enemyDamage:Int,val enemyMisses:Int,val defeatedEnemyIds:List<Int>,
    val phase:BattlePhase,val actions:List<BattleActionStep> = emptyList())
data class BattleSettlement(val character:CharacterState,val money:Int,val experience:Int,val levels:List<Int>) {
    var characters:List<CharacterState> = listOf(character);internal set
    var levelsByCharacter:Map<String,List<Int>> = mapOf(character.id to levels);internal set
    var experienceByCharacter:Map<String,Int> = mapOf(character.id to experience);internal set
}

/** Existing physical/escape branch plus the fingerprint-verified single-character herb action. */
class OpeningBattle(val group:EncounterGroup,private val content:BattleContent,hero:CharacterState,
    private val weaponBonus:Int,private val equippedArmorBonus:Int?=null) {
    val enemies=group.members.sortedBy{it.slot}.map{m->
        val definition=content.enemies[m.enemyId]?:error("Missing enemy ${m.enemyId}")
        require(OriginalStatus.enemySupported(definition)&&(definition.behaviorByte !in setOf(1,2,4,6,7,8,9)||content.physicalRules!=null))
            {"Unimplemented enemy special behavior"}
        BattleEnemy(m.slot,definition,definition.hp)
    }
    private var partyStates=listOf(hero)
    private var rosterStates=listOf(hero)
    var hero:CharacterState
        get()=partyStates.first()
        private set(value){partyStates=partyStates.toMutableList().also{it[0]=value}}
    val party:List<CharacterState> get()=partyStates
    private var originalIndices=mapOf(hero.id to 0)
    private var weaponBonuses=mapOf(hero.id to weaponBonus)
    private var armorBonuses:Map<String,Int> = equippedArmorBonus?.let{mapOf(hero.id to it)}?:emptyMap()
    private var configured=false
    // Original6948 is local to the current battle and resets on exit.
    private var bindingMarker=0
    private enum class CommandKind { ATTACK, HERB, BINDING, ESCAPE, ESCAPED }
    private data class QueuedCommand(val kind:CommandKind,val targetSlot:Int?=null,val targetId:String?=null,
        val bindingName:String="",val bindingTarget:String="",val bindingOriginalId:Int=-1)
    private val commands=linkedMapOf<String,QueuedCommand>()
    var inputRevision=0;private set
    private fun originalActors()=partyStates.mapIndexed{slot,p->OriginalPartyRules.Actor(originalIndices.getValue(p.id),slot,p.hp,p.statusMask,p.agility)}
    private fun originalEnemies()=enemies.map{OriginalPartyRules.Enemy(it.slot,it.hp,content.enemyAgility.getValue(it.definition.id))}
    val inputHero:CharacterState? get()=partyStates.firstOrNull{p->p.id !in commands&&
        OriginalPartyRules.collectsCommand(originalActors().first{it.originalActorIndex==originalIndices.getValue(p.id)})}
    fun configureParty(characters:List<CharacterState>,indices:Map<String,Int>,weapons:Map<String,Int>,armors:Map<String,Int>){
        require(!configured&&phase==BattlePhase.TARGET&&commands.isEmpty()&&characters.size in 1..3&&characters.first()==hero)
        require(characters.map{it.id}.distinct().size==characters.size&&characters.all{it.id in indices&&it.id in weapons&&it.id in armors})
        require(characters.map{indices.getValue(it.id)}.toSet().size==characters.size&&characters.all{indices.getValue(it.id) in 0..2})
        require(characters.size==1||characters.all{content.physicalFor(it.id)!=null&&content.growthFor(it.id).isNotEmpty()})
        val active=OriginalPartyRules.battleCharacters(characters)
        require(active.isNotEmpty()&&active.first()==hero){"Current controlled character is unavailable"}
        rosterStates=characters.toList();partyStates=active;originalIndices=indices.toMap();weaponBonuses=weapons.toMap();armorBonuses=armors.toMap();configured=true
    }
    private fun setCharacter(id:String,character:CharacterState){
        val index=partyStates.indexOfFirst{it.id==id};check(index>=0&&character.id==id)
        partyStates=partyStates.toMutableList().also{it[index]=character}
    }
    private fun submit(command:QueuedCommand,nextByte:()->Int):BattleTurn? {
        val actor=inputHero?:return null
        commands[actor.id]=command;inputRevision++
        if(inputHero!=null)return null // Collect every real actor before resolving the round.
        return originalRound(nextByte)
    }
    var phase=BattlePhase.TARGET;private set
    /** Original B68E→BA18 advances directly to stage5 when every living actor
     * has a no-input state08/10. Called only at a ready input boundary. */
    fun continueSkippedCommands(nextByte:()->Int):BattleTurn? {
        if(phase!=BattlePhase.TARGET||content.physicalRules==null||commands.isNotEmpty()||inputHero!=null)return null
        val living=partyStates.filter{it.hp>0}
        if(living.isEmpty()||living.any{it.statusMask and (OriginalStatus.STATUS_BIT8 or OriginalStatus.STATUS_BIT16)==0})return null
        return originalRound(nextByte)
    }
    private var settled=false
    // Pending battle effects share the existing pre-battle save checkpoint. No second inventory is persisted.
    var herbsConsumed=0;private set
    fun bindingAvailable(count:Int,item:ItemDefinition,alreadyUsed:Boolean=false):Boolean {
        if(phase!=BattlePhase.TARGET||content.physicalRules==null||(inputHero?.hp?:0)<=0||count!=1||alreadyUsed||
            item.category!="special"||item.maxCount!=1)return false
        val use=item.battleBindingUse?:return false
        val profile=originalBindingProfile(item.originalId)?:return false
        val ids=enemies.map{it.definition.id}.toSet()
        return item.id=="rom.special.${profile.itemId}"&&use.bindingMarker==profile.marker&&
            use.evidence==profile.itemEvidence&&ids in profile.enabledGroups&&ids.size==enemies.size&&
            enemies.all{it.definition.requiredBindingMarker==profile.marker}
    }
    fun useBinding(count:Int,item:ItemDefinition,alreadyUsed:Boolean,nextByte:()->Int):BattleTurn? {
        if(!bindingAvailable(count,item,alreadyUsed))return null
        // Original BE6E->BE9D collects the special directly, no target screen,
        // quantity decrement or used bit write. Its ordinary scheduler slot
        // executes 8935; faster enemy actions are not skipped or refunded.
        val use=item.battleBindingUse!!
        return submit(QueuedCommand(CommandKind.BINDING,
            bindingName=item.name,bindingTarget=use.targetLabel,bindingOriginalId=item.originalId),nextByte)
    }
    fun herbAvailable(targetId:String,count:Int,item:ItemDefinition):Boolean =
        phase==BattlePhase.TARGET && content.physicalRules!=null && (inputHero?.hp?:0)>0 &&
        partyStates.any{it.id==targetId&&it.hp>=0&&it.hp<=it.maxHp&&it.maxHp>0} && count>herbsConsumed &&
        item.id==HerbUse.ID && item.herbUse?.healHp==50 && item.herbUse.consumeAtFullHp
    fun useHerb(targetId:String,count:Int,item:ItemDefinition,nextByte:()->Int):BattleTurn? {
        if(!herbAvailable(targetId,count,item))return null
        // 9:BB9F..BBC9 consumes at confirmation, BEFORE ordered actions. Faster lethal enemies do not refund.
        herbsConsumed++
        return submit(QueuedCommand(CommandKind.HERB,targetId=targetId),nextByte)
    }
    fun inventoryAfterBattle(inventory:Map<String,Int>):Map<String,Int> {
        if(herbsConsumed==0)return inventory
        val count=inventory[HerbUse.ID]?:0;check(count>=herbsConsumed){"Pending battle item count changed"}
        return inventory.toMutableMap().also{if(count==herbsConsumed)it.remove(HerbUse.ID) else it[HerbUse.ID]=count-herbsConsumed}
    }
    /** Original 9:9282 battle-exit cleanup clears08 only for this supported
     * normal status domain; status04, poison02 and death are not cured by leaving battle. */
    fun charactersAfterBattle():List<CharacterState> {
        check(phase in setOf(BattlePhase.VICTORY,BattlePhase.ESCAPED))
        return OriginalPartyRules.restoreBattleCharacters(rosterStates,partyStates)
    }
    private fun frame(text:String,actor:Int?=null,target:Int?=null,kind:BattleActionKind=BattleActionKind.TEXT,
        delta:Int=0,beforeHero:Int=hero.hp,beforeEnemy:Int?=null,actorId:String?=null,targetId:String?=null)=
        BattleActionStep(text,hero.hp,enemies.associate{it.slot to it.hp},actor,target,kind,delta,beforeHero,beforeEnemy,hero.statusMask).also{
            it.actorId=actorId;it.targetId=targetId;it.partyHp=partyStates.associate{p->p.id to p.hp};it.partyStatus=partyStates.associate{p->p.id to p.statusMask}
        }
    /** Byte-exact 9:8A49..8AAF for the enabled normal enemies. Carry at entry is 1.
     * Random sequence remains independent of NES $43; no fixed success probability. */
    fun escape(nextByte:()->Int):BattleTurn? {
        if(phase!=BattlePhase.TARGET || !content.escapeEnabled)return null
        if(content.physicalRules!=null)return submit(QueuedCommand(CommandKind.ESCAPE),nextByte)
        val opponent=enemies.filter{it.hp>0}.sortedWith(compareByDescending<BattleEnemy>{
            content.enemyAgility.getValue(it.definition.id)}.thenBy{it.slot}).firstOrNull()?:return null
        val random=nextByte().also{require(it in 0..255)}
        val transformed=(((random shl 2)+2+(random ushr 7))+random+((random ushr 6) and 1)) and 255
        val threshold=(127+hero.agility-content.enemyAgility.getValue(opponent.definition.id)) and 255
        val steps=mutableListOf(frame("尝试逃跑",kind=BattleActionKind.ESCAPE))
        if(transformed<threshold){phase=BattlePhase.ESCAPED;steps.add(frame("逃跑成功",kind=BattleActionKind.ESCAPED))
            return BattleTurn(0,0,0,emptyList(),phase,steps)}
        steps.add(frame("逃跑失败",kind=BattleActionKind.ESCAPE_FAILED))
        val (damage,misses)=retaliate(nextByte,steps)
        return BattleTurn(0,damage,misses,emptyList(),phase,steps)
    }
    fun attack(slot:Int,nextByte:()->Int):BattleTurn? {
        if(phase!=BattlePhase.TARGET)return null
        val target=enemies.firstOrNull{it.slot==slot && it.hp>0}?:return null
        if(content.physicalRules!=null)return submit(QueuedCommand(CommandKind.ATTACK,target.slot),nextByte)
        val steps=mutableListOf(frame("攻击 ${target.definition.name}",target=slot,kind=BattleActionKind.ATTACK))
        val damage=max(1,hero.strength+weaponBonus-target.definition.defense)
        val actualDamage=minOf(damage,target.hp);target.hp-=actualDamage
        val defeated=if(target.hp==0)listOf(target.definition.id) else emptyList()
        steps.add(frame("${target.definition.name} 受到 $actualDamage 点伤害",target=slot,kind=BattleActionKind.DAMAGE,delta=-actualDamage,beforeEnemy=target.hp+actualDamage))
        if(target.hp==0)steps.add(frame("${target.definition.name} 被击倒",target=slot,kind=BattleActionKind.DEATH))
        if(enemies.all{it.hp==0}){
            phase=BattlePhase.VICTORY
            return BattleTurn(actualDamage,0,0,defeated,phase,steps)
        }
        val (total,misses)=retaliate(nextByte,steps)
        return BattleTurn(actualDamage,total,misses,defeated,phase,steps)
    }
    /** One original ordered scheduler; both old single-actor and joined-party input use it. */
    private fun originalRound(nextByte:()->Int):BattleTurn {
        val steps=mutableListOf<BattleActionStep>();val defeated=mutableListOf<Int>()
        var dealt=0;var received=0;var misses=0;var lastActionByte:Int?=null
        fun roll()=nextByte().also{require(it in 0..255);lastActionByte=it}
        fun finishEnemyAction(){if(OriginalPartyRules.defeated(originalActors()))phase=BattlePhase.DEFEAT}
        val actors=OriginalPartyRules.actionOrder(originalActors(),originalEnemies())
        for(actor in actors){
            if(phase!=BattlePhase.TARGET)break
            if(actor<0x80){
                val player=partyStates.first{originalIndices.getValue(it.id)==actor}
                if(!OriginalPartyRules.canAct(originalActors().first{it.originalActorIndex==actor}))continue
                val command=commands[player.id]?:continue
                if(command.kind==CommandKind.ESCAPED)continue
                if(command.kind==CommandKind.BINDING){
                    val target=enemies.firstOrNull{it.hp>0}?:continue
                    bindingMarker=originalSpecialBindingMarker(command.bindingOriginalId,target.definition.id,bindingMarker)
                    val feedback=if(command.bindingOriginalId==18)"${command.bindingName} · ${command.bindingTarget}保护标记生效 · 数量保留"
                        else "${command.bindingName}困住${command.bindingTarget} · 数量保留"
                    steps.add(frame(feedback,kind=BattleActionKind.SPECIAL,actorId=player.id))
                    continue
                }
                if(command.kind==CommandKind.HERB){
                    val target=partyStates.firstOrNull{it.id==command.targetId}?:continue
                    if(target.statusMask and OriginalStatus.DEAD!=0){
                        steps.add(frame("药草不能复活目标 · 已消耗1份",kind=BattleActionKind.TEXT,actorId=player.id,targetId=target.id));continue
                    }
                    val before=target.hp
                    val updated=target.copy(hp=minOf(target.maxHp.toLong(),before.toLong()+50).toInt())
                    setCharacter(target.id,updated)
                    steps.add(frame("药草 · 恢复 ${updated.hp-before} HP",kind=BattleActionKind.HEAL,
                        delta=updated.hp-before,beforeHero=if(target.id==hero.id)before else hero.hp,actorId=player.id,targetId=target.id))
                    continue
                }
                if(command.kind==CommandKind.ESCAPE){
                    var escaper=player
                    while(true){
                        steps.add(frame("尝试逃跑",kind=BattleActionKind.ESCAPE,actorId=escaper.id))
                        val opponent=enemies.filter{it.hp>0}.maxByOrNull{content.enemyAgility.getValue(it.definition.id)}!!
                        val random=roll()
                        val succeeds=if(opponent.definition.id>=136)false else {
                            val transformed=(((random shl 2)+2+(random ushr 7))+random+((random ushr 6) and 1)) and 255
                            transformed<((127+escaper.agility-content.enemyAgility.getValue(opponent.definition.id)) and 255)
                        }
                        if(!succeeds){steps.add(frame("逃跑失败",kind=BattleActionKind.ESCAPE_FAILED,actorId=escaper.id));break}
                        commands[escaper.id]=QueuedCommand(CommandKind.ESCAPED)
                        steps.add(frame("逃跑成功",kind=BattleActionKind.ESCAPED,actorId=escaper.id))
                        val next=partyStates.firstOrNull{commands[it.id]?.kind!=CommandKind.ESCAPED&&it.statusMask<0x10}
                        if(next==null){phase=BattlePhase.ESCAPED;break}
                        // 9:8B14 rewrites THIS scheduler slot, not the later slot of the forced actor.
                        // A failure can therefore be retried at that actor's existing later slot.
                        // Already confirmed items stay spent; the original chain does not refund them.
                        commands[next.id]=QueuedCommand(CommandKind.ESCAPE);escaper=next
                    }
                    continue
                }
                val slot=OriginalPartyRules.retargetEnemy(command.targetSlot!!,originalEnemies(),actors)?:continue
                val target=enemies.first{it.slot==slot};val rules=content.physicalFor(player.id)?:error("Missing actor physical rules")
                steps.add(frame("攻击 ${target.definition.name}",target=slot,kind=BattleActionKind.ATTACK,actorId=player.id))
                val random=roll()
                if(!rules.hits(player.equipment?.rightHand?:-1,random)){
                    steps.add(frame("攻击未命中",target=slot,kind=BattleActionKind.MISS,actorId=player.id));continue
                }
                val computed=rules.damage(player.strength+weaponBonuses.getValue(player.id),target.definition.defense,player.level,random)
                val damage=originalBoundTargetDamage(target.definition.requiredBindingMarker,bindingMarker,
                    OriginalStatus.outgoingPhysicalDamage(computed,player.statusMask))
                val actual=minOf(damage,target.hp);target.hp-=actual;dealt+=actual
                steps.add(frame("${target.definition.name} 受到 $actual 点伤害",target=slot,kind=BattleActionKind.DAMAGE,
                    delta=-actual,beforeEnemy=target.hp+actual,actorId=player.id))
                if(target.hp==0){defeated.add(target.definition.id);steps.add(frame("${target.definition.name} 被击倒",target=slot,kind=BattleActionKind.DEATH,actorId=player.id))}
                if(enemies.all{it.hp==0})phase=BattlePhase.VICTORY
            }else{
                val enemy=enemies.first{it.slot==(actor and 7)};if(enemy.hp<=0)continue
                val random=roll()
                val targetIndex=OriginalPartyRules.enemyTarget(originalActors(),random)
                if(targetIndex==null){phase=BattlePhase.DEFEAT;break}
                val target=partyStates.first{originalIndices.getValue(it.id)==targetIndex}
                val slot=enemy.slot
                if(enemy.definition.behaviorByte==6&&(random and 127)<41){
                    steps.add(frame("${enemy.definition.name} 异常10攻击（原名未核）",actor=slot,kind=BattleActionKind.ATTACK,targetId=target.id))
                    if(OriginalStatus.status16Hits(random)){
                        val updated=OriginalStatus.applyStatus16(target);setCharacter(target.id,updated)
                        steps.add(frame(if(updated.statusMask!=target.statusMask)"异常 10" else "异常状态保持",actor=slot,kind=BattleActionKind.STATUS,targetId=target.id))
                    }else{misses++;steps.add(frame("攻击未命中",actor=slot,kind=BattleActionKind.MISS,targetId=target.id))}
                    finishEnemyAction()
                    continue
                }
                if(enemy.definition.behaviorByte==7&&OriginalStatus.choosesPoison(random)){
                    steps.add(frame("${enemy.definition.name} 毒系攻击",actor=slot,kind=BattleActionKind.ATTACK,targetId=target.id))
                    val updated=OriginalStatus.poison(target);setCharacter(target.id,updated)
                    steps.add(frame(if(updated.statusMask!=target.statusMask)"中毒" else "异常状态保持",actor=slot,kind=BattleActionKind.STATUS,targetId=target.id));finishEnemyAction();continue
                }
                if(enemy.definition.behaviorByte==9&&OriginalStatus.choosesStatus4(random)){
                    steps.add(frame("${enemy.definition.name} 异常状态攻击",actor=slot,kind=BattleActionKind.ATTACK,targetId=target.id))
                    val updated=OriginalStatus.applyStatus4(target);setCharacter(target.id,updated)
                    steps.add(frame(if(updated.statusMask!=target.statusMask)"异常 04" else "异常状态保持",actor=slot,kind=BattleActionKind.STATUS,targetId=target.id));finishEnemyAction();continue
                }
                if(enemy.definition.behaviorByte==8&&OriginalStatus.choosesStatus4(random)){
                    steps.add(frame("${enemy.definition.name} 异常状态攻击",actor=slot,kind=BattleActionKind.ATTACK,targetId=target.id))
                    val updated=OriginalStatus.applyStatus8(target);setCharacter(target.id,updated)
                    steps.add(frame(if(updated.statusMask!=target.statusMask)"异常 08" else "异常状态保持",actor=slot,kind=BattleActionKind.STATUS,targetId=target.id));finishEnemyAction();continue
                }
                val ice=enemy.definition.iceBaseDamage!=null&&(random and 127)<41
                val special=enemy.definition.behaviorByte in setOf(1,2,4)&&enemy.definition.specialBaseDamage!=null&&OriginalStatus.choosesSpecial1(random)
                val targets=if(ice||special&&enemy.definition.behaviorByte==1)partyStates.filter{it.hp>0} else listOf(target)
                steps.add(frame(when{ice->"${enemy.definition.name} 冰系攻击";special->"${enemy.definition.name} 特殊攻击${enemy.definition.behaviorByte}（原名未核）";else->"${enemy.definition.name} 攻击"},actor=slot,
                    kind=when{ice->BattleActionKind.ICE;special->BattleActionKind.SPECIAL;else->BattleActionKind.ATTACK},targetId=if(targets.size==1)target.id else null))
                if(!ice&&!special&&random>=enemy.definition.hitByte){misses++;steps.add(frame("攻击未命中",actor=slot,kind=BattleActionKind.MISS,targetId=target.id));finishEnemyAction();continue}
                // Original behavior1/3 iterates present living party slots using this one AI byte.
                // A fallen first target does not skip the second; defeat is checked after the whole action.
                for(victim in targets){
                    val armor=armorBonuses[victim.id]?:if(victim.equipment?.body==0)content.armorContribution else 0
                    val computed=when{ice->enemy.definition.iceBaseDamage!!;special->enemy.definition.specialBaseDamage!!;else->enemy.definition.attack-armor-victim.stamina}
                    val damage=OriginalStatus.incomingDamage(computed,victim.statusMask);val actual=minOf(damage,victim.hp)
                    received+=actual;setCharacter(victim.id,victim.copy(hp=victim.hp-actual,statusMask=if(victim.hp==actual)OriginalStatus.DEAD else victim.statusMask))
                    steps.add(frame("受到 $actual 点伤害",actor=slot,kind=BattleActionKind.DAMAGE,delta=-actual,
                        beforeHero=if(victim.id==hero.id)victim.hp else hero.hp,targetId=victim.id))
                }
                finishEnemyAction()
            }
        }
        if(phase==BattlePhase.TARGET&&lastActionByte!=null){
            val recovery=OriginalPartyRules.recoverStatuses(originalActors(),lastActionByte!!)
            for(player in partyStates){
                val status=recovery.statusByActorIndex.getValue(originalIndices.getValue(player.id))
                if(status!=player.statusMask){setCharacter(player.id,player.copy(statusMask=status));steps.add(frame("异常状态解除",kind=BattleActionKind.STATUS,targetId=player.id))}
            }
        }
        commands.clear();inputRevision++
        return BattleTurn(dealt,received,misses,defeated,phase,steps)
    }
    private fun retaliate(nextByte:()->Int,steps:MutableList<BattleActionStep>):Pair<Int,Int>{
        var total=0;var misses=0
        for(enemy in enemies.filter{it.hp>0}){
            steps.add(frame("${enemy.definition.name} 攻击",actor=enemy.slot,kind=BattleActionKind.ATTACK))
            val value=nextByte().also{require(it in 0..255)}
            if(value>=enemy.definition.hitByte){misses++;steps.add(frame("攻击未命中",actor=enemy.slot,kind=BattleActionKind.MISS));continue}
            val armor=equippedArmorBonus ?: if(hero.equipment?.body==0)content.armorContribution else 0
            val hit=max(1,enemy.definition.attack-armor-hero.stamina)
            val applied=minOf(hit,hero.hp);total+=applied;hero=hero.copy(hp=hero.hp-applied)
            steps.add(frame("受到 $applied 点伤害",actor=enemy.slot,kind=BattleActionKind.DAMAGE,delta=-applied,beforeHero=hero.hp+applied))
            if(hero.hp==0){phase=BattlePhase.DEFEAT;break}
        }
        return total to misses
    }
    fun settle(currentMoney:Int):BattleSettlement? {
        if(phase!=BattlePhase.VICTORY || settled)return null
        val exp=enemies.sumOf{it.definition.experienceReward}
        val money=enemies.sumOf{it.definition.moneyReward}
        val shares=OriginalPartyRules.experienceShares(enemies.map{it.definition.experienceReward},originalActors())
        val levels=mutableMapOf<String,List<Int>>();val gained=mutableMapOf<String,Int>()
        partyStates=partyStates.map{it.copy(statusMask=it.statusMask and 0xf7)}.map{player->
            val amount=shares[originalIndices.getValue(player.id)]?:0;gained[player.id]=amount
            var grown=player.copy(experience=(player.experience+amount).coerceAtMost(0xffffff))
            val actorLevels=mutableListOf<Int>()
            if(player.hp>0)while(true){
                val row=content.growthFor(player.id).firstOrNull{it.level==grown.level+1&&grown.experience>=it.threshold}?:break
                grown=grown.copy(level=row.level,hp=(grown.hp+row.hp).coerceAtMost(grown.maxHp+row.hp),maxHp=grown.maxHp+row.hp,
                    mp=grown.mp+row.mp,maxMp=grown.maxMp?.plus(row.mp),strength=grown.strength+row.strength,
                    stamina=grown.stamina+row.stamina,agility=grown.agility+row.agility,spirit=grown.spirit+row.spirit)
                actorLevels.add(row.level)
            }
            levels[player.id]=actorLevels;grown
        }
        settled=true
        return BattleSettlement(hero,(currentMoney+money).coerceAtMost(9999999),exp,levels.getValue(hero.id)).also{
            it.characters=charactersAfterBattle();it.levelsByCharacter=levels;it.experienceByCharacter=gained
        }
    }

}

/** Original acquisition is best-effort after victory: no deletion when quantity/category is full. */
object BattleAcquisition {
    data class Result(val inventory:Map<String,Int>,val acquired:List<String>,val skipped:List<String>)
    fun apply(inventory:Map<String,Int>,loot:List<BattleLoot>,categories:Map<String,String>,nextByte:()->Int):Result {
        val next=inventory.toMutableMap();val acquired=mutableListOf<String>();val skipped=mutableListOf<String>()
        for(item in loot){
            require(item.category in setOf("medicine","weapon","armor")&&categories[item.itemId]==item.category&&item.threshold in 0..128)
            val byte=nextByte().also{require(it in 0..255)}
            if((byte and 127)>=item.threshold)continue
            val count=next[item.itemId]?:0
            val distinct=next.count{(id,n)->n>0&&(categories[id]?:id.removePrefix("rom.").substringBefore('.'))==item.category}
            if(count>=10||(count==0&&distinct>=16)){skipped.add(item.itemId);continue}
            next[item.itemId]=count+1;acquired.add(item.itemId)
        }
        return Result(next,acquired,skipped)
    }
}

/** Presentation consumes immutable computed steps; it has no RNG, damage or save access. */
class BattlePresentation {
    enum class Screen { ENTRY, COMMAND, TARGET, ACTING, RESULT }
    var screen=Screen.ENTRY;private set
    var command=0;private set
    var action:BattleActionStep?=null;private set
    var revision=0;private set
    private var steps:List<BattleActionStep> = emptyList()
    private var index=0
    private var elapsed=0L
    val elapsedMs get()=elapsed
    var resultElapsedMs=0L;private set
    val actionDurationMs get()=duration(action)
    private fun duration(step:BattleActionStep?)=when(step?.kind){
        BattleActionKind.ATTACK->450L;BattleActionKind.ICE,BattleActionKind.SPECIAL->650L;BattleActionKind.DAMAGE,BattleActionKind.HEAL->500L
        BattleActionKind.MISS->350L;BattleActionKind.DEATH->320L;BattleActionKind.ESCAPE->450L
        BattleActionKind.ESCAPED,BattleActionKind.ESCAPE_FAILED->400L;else->450L}
    fun invalidateInput(){revision++}
    private var outcome=BattlePhase.TARGET
    fun selectCommand(value:Int){if(screen==Screen.COMMAND&&value in 0..4)command=value}
    fun targets(){if(screen==Screen.COMMAND){screen=Screen.TARGET;revision++}}
    fun back(){if(screen==Screen.TARGET){screen=Screen.COMMAND;revision++}}
    fun present(turn:BattleTurn):Boolean {
        if(screen !in listOf(Screen.COMMAND,Screen.TARGET)||turn.actions.isEmpty())return false
        steps=turn.actions;index=0;action=steps[0];outcome=turn.phase;elapsed=0
        screen=Screen.ACTING;revision++;return true
    }
    /** Tick only while foreground/focused. One completed queue produces one result transition. */
    fun tick(ms:Long):Boolean {
        require(ms>=0)
        if(screen==Screen.ENTRY){elapsed+=ms;if(elapsed>=400){elapsed=0;screen=Screen.COMMAND;revision++};return false}
        if(screen==Screen.RESULT){resultElapsedMs+=ms;return false}
        if(screen!=Screen.ACTING)return false
        elapsed+=ms
        while(elapsed>=duration(action)){
            elapsed-=duration(action);index++;revision++
            if(index<steps.size){action=steps[index];continue}
            action=null;screen=if(outcome==BattlePhase.TARGET)Screen.COMMAND else Screen.RESULT
            command=0;elapsed=0;resultElapsedMs=0
            return true
        }
        return false
    }
}
