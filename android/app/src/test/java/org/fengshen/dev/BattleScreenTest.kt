package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class BattleScreenTest {
    @Test fun shortIllustratedPartyUsesVerticalSpaceWithoutMovingControlsOrSolo(){
        var shortCases=0;var originalCases=0
        for((safe,dp)in listOf(Box(0f,136f,960f,404f) to 1f,Box(0f,0f,2640f,936f) to 3f))
            for(font in listOf(1f,1.3f,2f))for(count in 1..4)for(enemies in listOf(1,6)){
                val scene=battleSceneLayout(safe,dp,font,enemies,count,true)!!;val ally=scene.allyField
                for((i,frame)in scene.allySprites.withIndex()){
                    assertTrue(inside(frame,ally));val t=if(count==1).5f else i.toFloat()/(count-1)
                    if(count>1&&ally.h<96*dp){
                        assertTrue("Use at least 80% of the short arena",frame.h>=ally.h*.8f-.01f)
                        assertTrue("Leave room above each body",frame.y>=ally.y+ally.h*.039f)
                        assertTrue("Keep feet above the status cards",frame.y+frame.h<ally.y+ally.h)
                        shortCases++
                    }else{
                        // Published v96 standing geometry stays exactly the same outside the scope.
                        assertEquals(ally.h*(.62f+.18f*t),frame.h,0f)
                        assertEquals(ally.y+ally.h*(.70f+.27f*t),frame.y+frame.h,.01f)
                        originalCases++
                    }
                }
                val other=battleSceneLayout(safe,dp,font,if(enemies==1)6 else 1,count,true)!!
                assertEquals(scene.allySprites,other.allySprites);assertEquals(scene.partyCards,other.partyCards)
                assertEquals(scene.touch.commands,other.touch.commands)
                assertEquals(scene.allyField,other.allyField)
            }
        assertTrue(shortCases>0&&originalCases>0)
    }
    @Test fun shortIllustratedActualPoseCropsStaySeparateAtNinetyNineClockSamples(){
        val crops=listOf(853 to 1250,881 to 1311,942 to 1338,922 to 1309,896 to 898,873 to 1210)
        var shortScenes=0
        for((safe,dp)in listOf(Box(0f,136f,960f,404f) to 1f,Box(0f,0f,2640f,936f) to 3f))
            for(count in 3..4){
                val scene=battleSceneLayout(safe,dp,2f,6,count,true)!!
                // The taller desktop window remains a normal arena, covered above.
                if(scene.allyField.h>=96*dp)continue
                shortScenes++
                for(crop in crops){
                    val bases=scene.allySprites.map{battleVisualBodyBounds(it,crop.first,crop.second)}
                    for(actor in bases.indices)for(sample in 0..98){
                        val drawn=battleVisualAttackBounds(bases,actor,scene.touch.arena,sample/98f)
                        for(i in bases.indices){assertTrue(inside(drawn[i],scene.touch.arena))
                            assertEquals(bases[i].y,drawn[i].y,0f);assertEquals(bases[i].h,drawn[i].h,0f)
                            if(i!=actor)assertEquals(bases[i],drawn[i])}
                        for(i in drawn.indices)for(j in i+1 until drawn.size)assertFalse(overlaps(drawn[i],drawn[j]))
                        if(sample==0||sample==98)assertEquals(bases,drawn)
                    }
                }
            }
        assertTrue("Exercise actual short arenas",shortScenes>0)
    }
    @Test fun singleEnemyFeedbackPreservesCropsAndTargetsWithSeparateMeasuredLabels(){
        for((safe,dp)in listOf(Box(0f,136f,960f,404f) to 1f,Box(0f,0f,2640f,936f) to 3f))
            for(font in listOf(1f,1.3f,2f))for(party in 1..4){
                val scene=battleSceneLayout(safe,dp,font,1,party,true)!!;val cell=scene.touch.enemies.single()
                val target=battleEnemySceneLayout(cell,dp,font,false,true).graphic
                for((cw,ch)in listOf(633 to 1019,1065 to 1308,32 to 40)){
                    val scale=kotlin.math.min(target.w/cw,target.h/ch)
                    val sprite=Box(target.x+(target.w-cw*scale)/2,target.y+(target.h-ch*scale)/2,cw*scale,ch*scale)
                    for(width in listOf(12f,56f,144f,220f))for(lineFactor in listOf(1.05f,1.25f,1.5f)){
                        val textHeight=12*font*dp*lineFactor
                        val parts=battleEnemyFeedbackLayout(cell,sprite,dp,width*dp,textHeight)!!
                        assertEquals(sprite,parts.graphic);assertTrue(cell.w>=48*dp&&cell.h>=48*dp)
                        assertTrue(parts.label.h>=textHeight);assertTrue(parts.gauge.w==parts.label.w)
                        for(b in listOf(parts.graphic,parts.label,parts.gauge))assertTrue(inside(b,cell))
                        assertFalse(overlaps(parts.label,parts.gauge))
                        for(shift in listOf(-3f,0f,3f)){
                            val moving=sprite.copy(x=sprite.x+shift*dp)
                            assertFalse(overlaps(moving,parts.label));assertFalse(overlaps(moving,parts.gauge))
                        }
                    }
                }
            }
    }
    @Test fun shortFeedbackUsesSpriteSpaceAndDefersImpossibleMeasuredText(){
        val cell=Box(0f,0f,520f,76f);val sprite=Box(244f,4f,32f,38f)
        val parts=battleEnemyFeedbackLayout(cell,sprite,1f,96f,28f)!!
        assertEquals(sprite,parts.graphic);assertTrue(parts.label.x>=sprite.x+sprite.w+8f)
        assertTrue(parts.label.w<cell.w/2);assertTrue(parts.label.w>=96f)
        assertNull(battleEnemyFeedbackLayout(cell,sprite,1f,96f,100f))
        for(value in listOf(Float.NaN,Float.POSITIVE_INFINITY,-1f)){
            try{battleEnemyFeedbackLayout(cell,sprite,1f,value,28f);fail("Invalid text measurement")}
            catch(expected:IllegalArgumentException){}
        }
    }
    @Test fun illustratedPartyCropsStaySeparateThroughEachActorsAdvanceAndReturn(){
        val crops=listOf(853 to 1250,881 to 1311,942 to 1338,922 to 1309,896 to 898,873 to 1210)
        for((safe,dp)in listOf(Box(0f,136f,960f,404f) to 1f,Box(0f,0f,2640f,936f) to 3f))
            for(font in listOf(1f,1.3f,2f))for(count in 1..4){
                val scene=battleSceneLayout(safe,dp,font,1,count,true)!!
                for(crop in crops){
                    val bodies=scene.allySprites.map{battleVisualBodyBounds(it,crop.first,crop.second)}
                    for(actor in bodies.indices)for(progress in listOf(0f,.05f,.18f,.2f,.42f,.5f,.65f,.85f,1f)){
                        val drawn=battleVisualAttackBounds(bodies,actor,scene.touch.arena,progress)
                        for(i in drawn.indices){assertTrue(inside(drawn[i],scene.touch.arena))
                            assertEquals(bodies[i].y+bodies[i].h,drawn[i].y+drawn[i].h,.01f)
                            if(i!=actor)assertEquals(bodies[i],drawn[i])}
                        for(i in drawn.indices)for(j in i+1 until drawn.size)assertFalse(overlaps(drawn[i],drawn[j]))
                        if(progress==0f||progress==1f)assertEquals(bodies,drawn)
                    }
                }
            }
    }
    @Test fun attackBoundsKeepSingleActorsOriginalAdvanceAndRejectInvalidClock(){
        val arena=Box(0f,0f,900f,300f);val body=Box(600f,100f,100f,150f);val bodies=listOf(body)
        assertEquals(body.x-900f*.055f,battleVisualAttackBounds(bodies,0,arena,.5f)[0].x,.001f)
        assertEquals(bodies,listOf(body));try{battleVisualAttackBounds(bodies,0,arena,Float.NaN);fail("Invalid clock")}
        catch(expected:IllegalArgumentException){}
        try{battleVisualAttackBounds(bodies,1,arena,.5f);fail("Missing actor")}
        catch(expected:IllegalArgumentException){}
    }
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
