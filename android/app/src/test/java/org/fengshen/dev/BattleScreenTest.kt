package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class BattleScreenTest {
    private fun overlaps(a:Box,b:Box)=a.x<b.x+b.w&&a.x+a.w>b.x&&a.y<b.y+b.h&&a.y+a.h>b.y
    private fun inside(a:Box,b:Box)=a.x>=b.x&&a.y>=b.y&&a.x+a.w<=b.x+b.w+.01f&&a.y+a.h<=b.y+b.h+.01f
    @Test fun allNativePartyEnemyCountsAndLargeFontsHaveVisibleIndependentTargets(){
        val windows=listOf(Pair(Box(0f,136f,960f,404f),1f),Pair(Box(90f,0f,2460f,1216f),3f),
            Pair(Box(0f,0f,660f,318f),1f))
        for((safe,dp)in windows)for(font in listOf(1f,1.3f,2f))for(party in 1..4)for(enemy in 1..6){
            val s=battleSceneLayout(safe,dp,font,enemy,party)!!
            assertEquals(party,s.partyCards.size);assertEquals(party,s.allySprites.size)
            assertEquals(enemy,s.touch.enemies.size)
            val hit=s.touch.commands+s.touch.enemies+s.partyCards
            for(b in hit){assertTrue(inside(b,safe));assertTrue(b.w>=48*dp&&b.h>=48*dp)}
            for(i in hit.indices)for(j in i+1 until hit.size)assertFalse(overlaps(hit[i],hit[j]))
            assertFalse(overlaps(s.enemyField,s.allyField));assertTrue(inside(s.touch.result,safe))
            assertFalse(overlaps(s.touch.result,s.resultFooter))
        }
    }
    @Test fun unsupportedSmallWindowIsExplicitlyDeferredInsteadOfCrashingTheApp(){
        assertNull(battleSceneLayout(Box(0f,0f,400f,240f),1f,2f,6,4))
        assertNull(battleSceneLayout(Box(0f,0f,660f,280f),1f,2f,6,4))
    }
    @Test fun currentActionPartyProjectionCannotShowLaterSettlementHp(){
        val hero=CharacterState("jiangziya",38,190000,1000,1608,151,235,109,63,124,151)
        val before=hero.copy()
        val action=BattleActionStep("current action",20,emptyMap()).apply{
            actorId="jiangziya";partyHp=mapOf("jiangziya" to 1600);partyStatus=mapOf("jiangziya" to 8)
        }
        val view=battlePartyView(hero,"姜子牙",action,null,"nezha")
        assertEquals(1600,view.hp);assertEquals(8,view.status);assertTrue(view.active)
        assertEquals(151,view.mp);assertEquals(before,hero)
        val waiting=battlePartyView(hero,"姜子牙",null,"yangjian","nezha")
        assertEquals(1000,waiting.hp);assertFalse(waiting.active)
    }
}
