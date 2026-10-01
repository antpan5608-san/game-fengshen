package org.fengshen.dev

import kotlin.math.max

/** ROM map-16 zone 0. Coordinates are world metatile coordinates, not camera pixels. */
data class EncounterRect(val left:Int,val top:Int,val right:Int,val bottom:Int) {
    fun contains(x:Int,y:Int)=x>left && x<=right && y>top && y<=bottom
}
data class EncounterMember(val slot:Int,val enemyId:Int)
data class EncounterGroup(val id:Int,val members:List<EncounterMember>)
data class EnemyDefinition(val id:Int,val name:String,val hp:Int,val attack:Int,val defense:Int,
    val experienceReward:Int,val moneyReward:Int,val hitByte:Int,val behaviorByte:Int)
data class GrowthRow(val level:Int,val threshold:Int,val hp:Int,val mp:Int,val strength:Int,
    val stamina:Int,val agility:Int,val spirit:Int,val runtimeVerified:Boolean)
data class BattleContent(val zoneMapId:Int,val zoneRects:List<EncounterRect>,val groups:List<EncounterGroup>,
    val enemies:Map<Int,EnemyDefinition>,val growth:List<GrowthRow>,val armorContribution:Int,
    val minimumSteps:Int,val forcedSteps:Int,val hitThreshold:Int,
    val enemyAgility:Map<Int,Int> = emptyMap(),val escapeEnabled:Boolean=false,val defeatResetEnabled:Boolean=false)

/** The ROM increments $5B on a completed metatile movement and tests $43 at a tile-aligned checkpoint.
 * Android samples an independent byte, so the random sequence is explicitly not NES-equivalent. */
class OpeningEncounter(private val content:BattleContent,initialSteps:Int=0) {
    var steps=initialSteps.coerceIn(0,255);private set
    fun onCompletedStep(mapId:Int,x:Int,y:Int,nextByte:()->Int):EncounterGroup? {
        steps=(steps+1).coerceAtMost(255)
        if(mapId!=content.zoneMapId || content.zoneRects.none{it.contains(x,y)})return null
        if(steps<content.minimumSteps)return null
        if(steps<content.forcedSteps && nextByte().let{require(it in 0..255);it}>=content.hitThreshold)return null
        val groupByte=nextByte().also{require(it in 0..255)}
        steps=0
        return content.groups[(groupByte and 31)%content.groups.size]
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
        require(definition.behaviorByte==0) {"Unimplemented enemy special behavior"}
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
