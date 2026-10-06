package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class MobilePlayTest {
    private val hero=CharacterState("nezha",1,0,20,20,0,8,4,2,4,0,EquipmentState(0,-1,0,28))
    private val growth=listOf(12,27,80,166,292,468,702,1010,1387).mapIndexed{i,n->GrowthRow(i+2,n,3,0,2,1,1,0,false)}
    @Test fun firstLevelUsesZeroNotObservedExpThirteen(){
        val p=experienceProgress(hero.copy(experience=11),growth)
        assertEquals(0,p.lower);assertEquals(12,p.upper);assertEquals(11,p.earned);assertEquals(1,p.remaining)
        assertEquals(11f/12,p.fraction!!,0.00001f)
        assertEquals(ExperienceProgress.Status.INVALID,experienceProgress(hero.copy(experience=12),growth).status)
    }
    @Test fun justUpgradedUsesItsOwnIntervalNotCumulativeDivision(){
        val at=experienceProgress(hero.copy(level=2,experience=12),growth)
        assertEquals(0,at.earned);assertEquals(15,at.span);assertEquals(15,at.remaining)
        val after=experienceProgress(hero.copy(level=2,experience=13),growth)
        assertEquals(1f/15,after.fraction!!,0.00001f)
        assertEquals(ExperienceProgress.Status.INVALID,experienceProgress(hero.copy(level=2,experience=27),growth).status)
    }
    @Test fun missingNextLevelIsNotFakeMaxOrFull(){
        val last=experienceProgress(hero.copy(level=10,experience=1400),growth)
        assertEquals(ExperienceProgress.Status.MISSING,last.status);assertNull(last.fraction)
        assertEquals(ExperienceProgress.Status.MAX,experienceProgress(hero.copy(level=10,experience=1400),growth,knownMaxLevel=10).status)
        assertEquals(ExperienceProgress.Status.MISSING,experienceProgress(hero.copy(id="other"),growth).status)
    }
    @Test fun inconsistentOldSaveIsDiagnosedWithoutCorrection(){
        val old=hero.copy(level=3,experience=13);val before=old.copy()
        assertEquals(ExperienceProgress.Status.INVALID,experienceProgress(old,growth).status);assertEquals(before,old)
        assertEquals(ExperienceProgress.Status.INVALID,experienceProgress(hero,growth+growth.first()).status)
        assertEquals(ExperienceProgress.Status.INVALID,experienceProgress(hero,growth.map{it.copy(threshold=1)}).status)
    }
    @Test fun multiLevelRewardDerivesFromSameSettlementRows(){
        val e=EnemyDefinition(2,"existing",1,1,0,90,0,0,0)
        val c=BattleContent(16,emptyList(),emptyList(),mapOf(2 to e),growth,2,6,50,16)
        val b=OpeningBattle(EncounterGroup(1,listOf(EncounterMember(2,2))),c,hero,2)
        b.attack(2){error("dead enemy cannot act")};val r=b.settle(0)!!
        assertEquals(listOf(2,3,4),r.levels);val p=experienceProgress(r.character,growth)
        assertEquals(80,p.lower);assertEquals(10,p.earned);assertEquals(76,p.remaining);assertNull(b.settle(0))
    }
    private fun overlaps(a:Box,b:Box)=a.x<b.x+b.w&&a.x+a.w>b.x&&a.y<b.y+b.h&&a.y+a.h>b.y
    @Test fun battleTargetsAreIndependentAndFitAllFonts(){
        for(font in listOf(1f,1.3f,2f))for(n in listOf(1,3,7))for(partyCount in 1..4)for(window in listOf(Box(0f,0f,960f,540f),Box(90f,0f,2460f,1216f))){
            val dp=if(window.w>1000)3f else 1f;val l=battleTouchLayout(window,dp,font,n,partyCount)
            val targets=l.commands+l.enemies+listOf(l.status)
            targets.forEach{assertTrue(it.w>=48*dp);assertTrue(it.h>=48*dp);assertTrue(it.y+it.h<=window.y+window.h)}
            for(i in targets.indices)for(j in i+1 until targets.size)assertFalse(overlaps(targets[i],targets[j]))
            assertTrue(l.arena.h>0);assertTrue(l.arena.y+l.arena.h<=l.enemies.first().y)
            assertTrue(l.status.y>=window.y);assertTrue(l.arena.y+l.arena.h<=l.status.y)
        }
    }
    @Test fun actionSnapshotsDoNotRevealLaterEnemyOrHeroHp(){
        val enemies=listOf(EnemyDefinition(2,"same",20,10,2,1,1,255,0),EnemyDefinition(3,"same",20,10,2,1,1,255,0))
        val c=BattleContent(16,emptyList(),emptyList(),enemies.associateBy{it.id},growth,2,6,50,16)
        val b=OpeningBattle(EncounterGroup(1,listOf(EncounterMember(1,2),EncounterMember(4,3))),c,hero,2)
        var random=0;val t=b.attack(1){random++;0}!!
        assertEquals(20,t.actions.first().enemyHp[1]);assertEquals(20,t.actions.first().enemyHp[4]);assertEquals(20,t.actions.first().heroHp)
        val hit=t.actions.first{it.kind==BattleActionKind.DAMAGE&&it.targetSlot==1}
        assertEquals(20,hit.beforeEnemyHp);assertEquals(12,hit.enemyHp[1]);assertEquals(-8,hit.hpDelta)
        val p=BattlePresentation();p.tick(400);p.targets();assertTrue(p.present(t));val before=b.hero
        assertEquals(20,p.action!!.heroHp);repeat(200){p.tick(16)}
        assertEquals(before,b.hero);assertEquals(2,random);assertEquals(20,b.enemies[1].hp)
    }
    @Test fun presentationSpeedAndPauseDoNotResettleOrConsumeRandom(){
        val e=EnemyDefinition(2,"old",10,7,2,2,1,179,0)
        val c=BattleContent(16,emptyList(),emptyList(),mapOf(2 to e),growth,2,6,50,16)
        fun run(ticks:List<Long>):Triple<CharacterState,List<Int>,Int>{
            val b=OpeningBattle(EncounterGroup(1,listOf(EncounterMember(2,2))),c,hero,2);var calls=0
            val turn=b.attack(2){calls++;0}!!;val p=BattlePresentation();p.tick(400);p.targets();p.present(turn);assertFalse(p.present(turn))
            for(dt in ticks)p.tick(dt)
            return Triple(b.hero,b.enemies.map{it.hp},calls)
        }
        assertEquals(run(List(1000){16L}),run(listOf(0L,0L,0L,16000L)))
    }
}
