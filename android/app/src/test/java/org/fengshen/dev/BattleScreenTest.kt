package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class BattleScreenTest {
    @Test fun illustratedSceneKeepsAllTargetsCardsAndGroundedActorsInsideSafeArea(){
        for((safe,dp)in listOf(Box(0f,136f,960f,404f) to 1f,Box(0f,0f,2640f,936f) to 3f))
            for(font in listOf(1f,1.3f,2f))for(party in 1..4)for(enemies in 1..6){
                val s=battleSceneLayout(safe,dp,font,enemies,party,true)!!
                val hit=s.touch.commands+s.touch.enemies+s.partyCards
                for(b in hit){assertTrue(inside(b,safe));assertTrue(b.w>=48*dp&&b.h>=48*dp)}
                for(i in hit.indices)for(j in i+1 until hit.size)assertFalse(overlaps(hit[i],hit[j]))
                for(b in s.allySprites)assertTrue(inside(b,s.allyField))
                for(i in 1 until s.allySprites.size){assertTrue(s.allySprites[i].x>s.allySprites[i-1].x)
                    assertTrue(s.allySprites[i].y+s.allySprites[i].h>s.allySprites[i-1].y+s.allySprites[i-1].h)}
                if(font<=1.3f)assertTrue(s.touch.arena.h>=safe.h*.5f)
                if(enemies>3)assertTrue(s.compact)
            }
    }
    @Test fun attackMotionReturnsToOriginAndCannotChangeAStoredAction(){
        val step=BattleActionStep("attack",53,mapOf(0 to 10),kind=BattleActionKind.ATTACK)
        val before=step.copy();assertEquals(0f,battleAdvance(0f),.001f);assertEquals(1f,battleAdvance(.5f),.001f)
        assertEquals(0f,battleAdvance(1f),.001f);assertEquals(0f,battleAdvance(2f),.001f)
        assertEquals(before,step);try{battleAdvance(Float.NaN);fail("Invalid clock") }catch(expected:IllegalArgumentException){}
    }
    private fun overlaps(a:Box,b:Box)=a.x<b.x+b.w&&a.x+a.w>b.x&&a.y<b.y+b.h&&a.y+a.h>b.y
    private fun inside(a:Box,b:Box)=a.x>=b.x&&a.y>=b.y&&a.x+a.w<=b.x+b.w+.01f&&a.y+a.h<=b.y+b.h+.01f
    @Test fun allNativePartyEnemyCountsAndLargeFontsHaveVisibleIndependentTargets(){
        val windows=listOf(Pair(Box(0f,136f,960f,404f),1f),Pair(Box(90f,0f,2460f,1216f),3f),
            Pair(Box(0f,0f,660f,318f),1f),Pair(Box(0f,0f,2640f,936f),3f))
        for((safe,dp)in windows)for(font in listOf(1f,1.3f,2f))for(party in 1..4)for(enemy in 1..6){
            val s=battleSceneLayout(safe,dp,font,enemy,party)!!
            assertEquals(party,s.partyCards.size);assertEquals(party,s.allySprites.size)
            assertEquals(enemy,s.touch.enemies.size)
            val hit=s.touch.commands+s.touch.enemies+s.partyCards
            for(b in hit){assertTrue(inside(b,safe));assertTrue(b.w>=48*dp&&b.h>=48*dp)}
            for(i in hit.indices)for(j in i+1 until hit.size)assertFalse(overlaps(hit[i],hit[j]))
            assertFalse(overlaps(s.enemyField,s.allyField));assertTrue(inside(s.touch.result,safe))
            assertFalse(overlaps(s.touch.result,s.resultFooter))
            if(s.compact)for(cell in s.touch.enemies){
                val parts=battleEnemySceneLayout(cell,dp,font,true,false)
                for(b in listOf(parts.graphic,parts.label,parts.gauge))assertTrue(inside(b,cell))
                assertFalse(overlaps(parts.graphic,parts.label));assertFalse(overlaps(parts.graphic,parts.gauge))
                assertFalse(overlaps(parts.label,parts.gauge))
                assertTrue(parts.label.w>=40*dp)
            }
        }
    }
    @Test fun unsupportedSmallWindowIsExplicitlyDeferredInsteadOfCrashingTheApp(){
        assertNull(battleSceneLayout(Box(0f,0f,400f,240f),1f,2f,6,4))
        assertNull(battleSceneLayout(Box(0f,0f,660f,280f),1f,2f,6,4))
    }
    @Test fun wideMedicineKeepsEveryTargetAndCoreEffectsAboveTheConfirmAction(){
        for((safe,dp)in listOf(Box(0f,136f,960f,404f) to 1f,Box(0f,0f,2640f,936f) to 3f))
            for(font in listOf(1f,1.3f,2f))for(party in 3..4){
                val scene=battleMedicineSceneLayout(safe,dp,font,party)!!;val l=scene.modal
                assertEquals(party,scene.targets.size)
                assertTrue(l.detail.h>=(14*font*1.25f+4)*2*dp)
                val boxes=scene.targets+listOf(l.close,l.list,l.detail,l.primary)
                for(b in boxes)assertTrue(inside(b,safe))
                for(target in scene.targets){assertTrue(target.w>=48*dp&&target.h>=48*dp)
                    assertTrue(target.y+target.h<=l.list.y)}
                for(i in boxes.indices)for(j in i+1 until boxes.size)assertFalse(overlaps(boxes[i],boxes[j]))
            }
        assertNull(battleMedicineSceneLayout(Box(0f,0f,660f,318f),1f,2f,4))
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
