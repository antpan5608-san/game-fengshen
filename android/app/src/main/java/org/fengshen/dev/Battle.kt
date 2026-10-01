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
    val iceBaseDamage:Int?=null,val loot:BattleLoot?=null)
data class BattleLoot(val itemId:String,val threshold:Int,val category:String)
data class PhysicalRules(val weaponHitThreshold:Map<Int,Int>,val multiplierThresholds:List<Int>) {
    init {require(multiplierThresholds.size==36&&multiplierThresholds.all{it in 0..255})}
    fun hits(weapon:Int,roll:Int)=(roll and 63)<weaponHitThreshold.getValue(weapon)
    fun multiplier(level:Int,roll:Int):Int {
        val bucket=((level-1)/5).coerceIn(0,11);var multiplier=1
        while(multiplier<4&&roll>=multiplierThresholds[bucket+12*(multiplier-1)])multiplier++
        return multiplier
    }
    fun damage(attack:Int,defense:Int,level:Int,roll:Int):Int =
        if(attack<defense)1 else ((attack-defense)*multiplier(level,roll)) and 65535
}
data class StoryBattleDefinition(val id:String,val npcId:String,val flagId:String,val group:EncounterGroup,
    val victoryDialogue:String)
data class GrowthRow(val level:Int,val threshold:Int,val hp:Int,val mp:Int,val strength:Int,
    val stamina:Int,val agility:Int,val spirit:Int,val runtimeVerified:Boolean)
data class BattleContent(val zoneMapId:Int,val zoneRects:List<EncounterRect>,val groups:List<EncounterGroup>,
    val enemies:Map<Int,EnemyDefinition>,val growth:List<GrowthRow>,val armorContribution:Int,
    val minimumSteps:Int,val forcedSteps:Int,val hitThreshold:Int,
    val enemyAgility:Map<Int,Int> = emptyMap(),val escapeEnabled:Boolean=false,val defeatResetEnabled:Boolean=false,
    val zones:List<EncounterZone> = emptyList(),val physicalRules:PhysicalRules?=null,
    val storyBattles:Map<String,StoryBattleDefinition> = emptyMap())

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
data class BattleActionStep(val text:String,val heroHp:Int,val enemyHp:Map<Int,Int>,
    val actorSlot:Int?=null,val targetSlot:Int?=null)
data class BattleTurn(val playerDamage:Int,val enemyDamage:Int,val enemyMisses:Int,val defeatedEnemyIds:List<Int>,
    val phase:BattlePhase,val actions:List<BattleActionStep> = emptyList())
data class BattleSettlement(val character:CharacterState,val money:Int,val experience:Int,val levels:List<Int>)

/** Scoped physical branch for the three opening enemies. No spell/item or invented special action is accepted. */
class OpeningBattle(val group:EncounterGroup,private val content:BattleContent,hero:CharacterState,
    private val weaponBonus:Int,private val equippedArmorBonus:Int?=null) {
    val enemies=group.members.sortedBy{it.slot}.map{m->
        val definition=content.enemies[m.enemyId]?:error("Missing enemy ${m.enemyId}")
        require(definition.behaviorByte==0 || (definition.id==137&&definition.behaviorByte==3&&definition.iceBaseDamage==8))
            {"Unimplemented enemy special behavior"}
        BattleEnemy(m.slot,definition,definition.hp)
    }
    var hero=hero;private set
    var phase=BattlePhase.TARGET;private set
    private var settled=false
    private fun frame(text:String,actor:Int?=null,target:Int?=null)=
        BattleActionStep(text,hero.hp,enemies.associate{it.slot to it.hp},actor,target)
    /** Byte-exact 9:8A49..8AAF for the enabled normal enemies. Carry at entry is 1.
     * Random sequence remains independent of NES $43; no fixed success probability. */
    fun escape(nextByte:()->Int):BattleTurn? {
        if(phase!=BattlePhase.TARGET || !content.escapeEnabled)return null
        if(content.physicalRules!=null)return originalRound(null,nextByte)
        val opponent=enemies.filter{it.hp>0}.sortedWith(compareByDescending<BattleEnemy>{
            content.enemyAgility.getValue(it.definition.id)}.thenBy{it.slot}).firstOrNull()?:return null
        val random=nextByte().also{require(it in 0..255)}
        val transformed=(((random shl 2)+2+(random ushr 7))+random+((random ushr 6) and 1)) and 255
        val threshold=(127+hero.agility-content.enemyAgility.getValue(opponent.definition.id)) and 255
        val steps=mutableListOf(frame("尝试逃跑"))
        if(transformed<threshold){phase=BattlePhase.ESCAPED;steps.add(frame("逃跑成功"))
            return BattleTurn(0,0,0,emptyList(),phase,steps)}
        steps.add(frame("逃跑失败"))
        val (damage,misses)=retaliate(nextByte,steps)
        return BattleTurn(0,damage,misses,emptyList(),phase,steps)
    }
    fun attack(slot:Int,nextByte:()->Int):BattleTurn? {
        if(phase!=BattlePhase.TARGET)return null
        val target=enemies.firstOrNull{it.slot==slot && it.hp>0}?:return null
        if(content.physicalRules!=null)return originalRound(target.slot,nextByte)
        val steps=mutableListOf(frame("攻击 ${target.definition.name}",target=slot))
        val damage=max(1,hero.strength+weaponBonus-target.definition.defense)
        val actualDamage=minOf(damage,target.hp);target.hp-=actualDamage
        val defeated=if(target.hp==0)listOf(target.definition.id) else emptyList()
        steps.add(frame("${target.definition.name} 受到 $actualDamage 点伤害",target=slot))
        if(target.hp==0)steps.add(frame("${target.definition.name} 被击倒",target=slot))
        if(enemies.all{it.hp==0}){
            phase=BattlePhase.VICTORY
            return BattleTurn(actualDamage,0,0,defeated,phase,steps)
        }
        val (total,misses)=retaliate(nextByte,steps)
        return BattleTurn(actualDamage,total,misses,defeated,phase,steps)
    }
    /** The restored physical path shares the observed accuracy/multiplier byte.
     * Stable descending agility retains the player before enemies on ties. */
    private fun originalRound(targetSlot:Int?,nextByte:()->Int):BattleTurn {
        val rules=content.physicalRules!!;val steps=mutableListOf<BattleActionStep>()
        val defeated=mutableListOf<Int>();var dealt=0;var received=0;var misses=0
        fun roll()=nextByte().also{require(it in 0..255)}
        val actors=(listOf(-1)+enemies.filter{it.hp>0}.map{it.slot}).sortedByDescending{slot->
            if(slot==-1)hero.agility else content.enemyAgility.getValue(enemies.first{it.slot==slot}.definition.id)}
        for(actor in actors){
            if(phase!=BattlePhase.TARGET)break
            if(actor==-1){
                if(targetSlot==null){
                    steps.add(frame("尝试逃跑"))
                    val opponent=enemies.filter{it.hp>0}.maxByOrNull{content.enemyAgility.getValue(it.definition.id)}!!
                    // 9:8A69: original enemy IDs >=136 always fail; the action is consumed.
                    val random=roll() // Original 8A49 reads the byte before the Boss-ID rejection.
                    val succeeds=if(opponent.definition.id>=136)false else {
                        val transformed=(((random shl 2)+2+(random ushr 7))+random+((random ushr 6) and 1)) and 255
                        transformed<((127+hero.agility-content.enemyAgility.getValue(opponent.definition.id)) and 255)
                    }
                    if(succeeds){phase=BattlePhase.ESCAPED;steps.add(frame("逃跑成功"))}
                    else steps.add(frame("逃跑失败"))
                    continue
                }
                val target=enemies.first{it.slot==targetSlot};if(target.hp<=0)continue
                steps.add(frame("攻击 ${target.definition.name}",target=target.slot))
                val random=roll()
                if(!rules.hits(hero.equipment?.rightHand?:-1,random)){
                    steps.add(frame("攻击未命中",target=target.slot));continue
                }
                val damage=rules.damage(hero.strength+weaponBonus,target.definition.defense,hero.level,random)
                val actual=minOf(damage,target.hp);target.hp-=actual;dealt+=actual
                steps.add(frame("${target.definition.name} 受到 $actual 点伤害",target=target.slot))
                if(target.hp==0){defeated.add(target.definition.id);steps.add(frame("${target.definition.name} 被击倒",target=target.slot))}
                if(enemies.all{it.hp==0})phase=BattlePhase.VICTORY
            }else{
                val enemy=enemies.first{it.slot==actor};if(enemy.hp<=0)continue
                val random=roll();val ice=enemy.definition.iceBaseDamage!=null&&(random and 127)<41
                steps.add(frame(if(ice)"${enemy.definition.name} 冰系攻击" else "${enemy.definition.name} 攻击",actor=actor))
                if(!ice&&random>=enemy.definition.hitByte){misses++;steps.add(frame("攻击未命中",actor=actor));continue}
                val armor=equippedArmorBonus ?: if(hero.equipment?.body==0)content.armorContribution else 0
                val damage=if(ice)enemy.definition.iceBaseDamage!! else max(1,enemy.definition.attack-armor-hero.stamina)
                val actual=minOf(damage,hero.hp);received+=actual;hero=hero.copy(hp=hero.hp-actual)
                steps.add(frame("受到 $actual 点伤害",actor=actor))
                if(hero.hp==0)phase=BattlePhase.DEFEAT
            }
        }
        return BattleTurn(dealt,received,misses,defeated,phase,steps)
    }
    private fun retaliate(nextByte:()->Int,steps:MutableList<BattleActionStep>):Pair<Int,Int>{
        var total=0;var misses=0
        for(enemy in enemies.filter{it.hp>0}){
            steps.add(frame("${enemy.definition.name} 攻击",actor=enemy.slot))
            val value=nextByte().also{require(it in 0..255)}
            if(value>=enemy.definition.hitByte){misses++;steps.add(frame("攻击未命中",actor=enemy.slot));continue}
            val armor=equippedArmorBonus ?: if(hero.equipment?.body==0)content.armorContribution else 0
            val hit=max(1,enemy.definition.attack-armor-hero.stamina)
            val applied=minOf(hit,hero.hp);total+=applied;hero=hero.copy(hp=hero.hp-applied)
            steps.add(frame("受到 $applied 点伤害",actor=enemy.slot))
            if(hero.hp==0){phase=BattlePhase.DEFEAT;break}
        }
        return total to misses
    }
    fun settle(currentMoney:Int):BattleSettlement? {
        if(phase!=BattlePhase.VICTORY || settled)return null
        val exp=enemies.sumOf{it.definition.experienceReward}
        val money=enemies.sumOf{it.definition.moneyReward}
        var grown=hero.copy(experience=(hero.experience+exp).coerceAtMost(0xffffff))
        val levels=mutableListOf<Int>()
        while(true){
            val row=content.growth.firstOrNull{it.level==grown.level+1 && grown.experience>=it.threshold}?:break
            grown=grown.copy(level=row.level,hp=(grown.hp+row.hp).coerceAtMost(grown.maxHp+row.hp),
                maxHp=grown.maxHp+row.hp,mp=grown.mp+row.mp,
                maxMp=grown.maxMp?.plus(row.mp),strength=grown.strength+row.strength,
                stamina=grown.stamina+row.stamina,agility=grown.agility+row.agility,spirit=grown.spirit+row.spirit)
            levels.add(row.level)
        }
        settled=true;hero=grown
        return BattleSettlement(grown,(currentMoney+money).coerceAtMost(9999999),exp,levels)
    }
}

/** Original acquisition is best-effort after victory: no deletion when quantity/category is full. */
object BattleAcquisition {
    data class Result(val inventory:Map<String,Int>,val acquired:List<String>,val skipped:List<String>)
    fun apply(inventory:Map<String,Int>,loot:List<BattleLoot>,categories:Map<String,String>,nextByte:()->Int):Result {
        val next=inventory.toMutableMap();val acquired=mutableListOf<String>();val skipped=mutableListOf<String>()
        for(item in loot){
            require(item.category in setOf("medicine","weapon")&&categories[item.itemId]==item.category&&item.threshold in 0..128)
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
        if(screen!=Screen.ACTING)return false
        elapsed+=ms
        if(elapsed<650)return false
        elapsed=0;index++;revision++
        if(index<steps.size){action=steps[index];return false}
        action=null;screen=if(outcome==BattlePhase.TARGET)Screen.COMMAND else Screen.RESULT
        return true
    }
}
