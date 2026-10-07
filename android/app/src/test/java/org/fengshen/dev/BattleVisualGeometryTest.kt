package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class BattleVisualGeometryTest {
    @Test fun selectedIdleAttackCastAndEnemyCropsKeepAspectAndFeetWithinTheSameSlots(){
        // Actual alpha bounds from the retained incoming manifest, not App acceptance.
        val crops=listOf(859 to 1256,896 to 898,886 to 1317,873 to 1210,633 to 1019,1065 to 1308)
        for((safe,dp)in listOf(Box(0f,136f,960f,404f) to 1f,
            Box(0f,0f,2640f,936f) to 3f,Box(90f,0f,2460f,1216f) to 3f))
            for(font in listOf(1f,1.3f,2f))for(count in 1..4){
                val scene=battleSceneLayout(safe,dp,font,6,count,true)!!
                for(slot in scene.allySprites)for((w,h)in crops){
                    val body=battleVisualBodyBounds(slot,w,h)
                    assertTrue(body.x>=slot.x-.01f&&body.y>=slot.y-.01f)
                    assertTrue(body.x+body.w<=slot.x+slot.w+.01f)
                    assertEquals(slot.y+slot.h,body.y+body.h,.01f)
                    assertEquals(w.toFloat()/h,body.w/body.h,.001f)
                }
            }
    }
    @Test fun missingOrNonFiniteGeometryCannotEnterCanvas(){
        for((box,w,h)in listOf(Triple(Box(0f,0f,100f,100f),0,10),
            Triple(Box(0f,0f,100f,100f),10,-1),Triple(Box(0f,0f,0f,10f),1,1),
            Triple(Box(Float.NaN,0f,10f,10f),1,1),Triple(Box(0f,0f,Float.POSITIVE_INFINITY,10f),1,1))){
            try{battleVisualBodyBounds(box,w,h);fail("Invalid geometry")}
            catch(expected:IllegalArgumentException){}
        }
    }
    @Test fun actualIdentitiesSeparateCastFromHerbEnemySpecialAndOldFramesWithoutChangingSnapshots(){
        val action=BattleActionStep("display text is not an identity",58,mapOf(3 to 100),kind=BattleActionKind.HEAL)
            .apply{actorId="xiaolongnv";targetId="nezha";partyHp=mapOf("nezha" to 58);partyMp=mapOf("xiaolongnv" to 44)}
        val hp=action.partyHp;val mp=action.partyMp;val enemies=action.enemyHp
        for(id in listOf(OriginalBattleMagic.HEAL,OriginalBattleMagic.ANTIDOTE)){
            action.abilityId=id
            assertEquals(BattleVisualPose.CAST,battleVisualPose(action,"xiaolongnv"))
            assertEquals(BattleVisualPose.IDLE,battleVisualPose(action,"nezha"))
            assertSame(hp,action.partyHp);assertSame(mp,action.partyMp);assertSame(enemies,action.enemyHp)
        }
        for(id in listOf(HerbUse.ID,"unknown",null)){
            action.abilityId=id;assertEquals(BattleVisualPose.IDLE,battleVisualPose(action,"xiaolongnv"))
        }
        val enemy=BattleActionStep("提神术",58,emptyMap(),actorSlot=3,kind=BattleActionKind.SPECIAL)
            .apply{actorId="xiaolongnv";abilityId=OriginalBattleMagic.HEAL}
        assertEquals(BattleVisualPose.IDLE,battleVisualPose(enemy,"xiaolongnv"))
        assertEquals(BattleVisualPose.IDLE,battleVisualPose(null,"nezha"))
    }
    @Test fun anAttackPoseBelongsOnlyToItsExecutingActorAndDebitTextIsIdle(){
        val attack=BattleActionStep("攻击",20,emptyMap(),kind=BattleActionKind.ATTACK).apply{actorId="nezha"}
        assertEquals(BattleVisualPose.ATTACK,battleVisualPose(attack,"nezha"))
        assertEquals(BattleVisualPose.IDLE,battleVisualPose(attack,"xiaolongnv"))
        val debit=BattleActionStep("MP −3",58,emptyMap()).apply{actorId="xiaolongnv";abilityId=OriginalBattleMagic.HEAL}
        assertEquals(BattleVisualPose.IDLE,battleVisualPose(debit,"xiaolongnv"))
    }
    @Test fun supportFeedbackUsesOnlyTheActualPartyTargetAndDoesNotMutateStepState(){
        val ids=listOf("nezha","xiaolongnv","yangjian","jiangziya")
        for(ability in listOf(OriginalBattleMagic.HEAL,OriginalBattleMagic.ANTIDOTE))
            for(kind in listOf(BattleActionKind.SPECIAL,BattleActionKind.HEAL,BattleActionKind.STATUS)){
                val action=BattleActionStep("Not parsed as a spell",5,mapOf(3 to 100),kind=kind)
                    .apply{actorId="xiaolongnv";targetId="nezha";abilityId=ability
                        partyHp=mapOf("nezha" to 5);partyMp=mapOf("xiaolongnv" to 44);partyStatus=mapOf("nezha" to 2)}
                val hp=action.partyHp;val mp=action.partyMp;val status=action.partyStatus
                repeat(50){assertEquals("nezha",battleVisualSupportTarget(action,ids))}
                assertSame(hp,action.partyHp);assertSame(mp,action.partyMp);assertSame(status,action.partyStatus)
                assertEquals(5,action.heroHp);assertEquals(0,action.hpDelta)
                // The projection does not infer a successful heal or revive from the target's HP.
                action.partyHp=mapOf("nezha" to 0);assertEquals("nezha",battleVisualSupportTarget(action,ids))
            }
    }
    @Test fun unsupportedEnemyItemMissingTargetsAndDebitFramesKeepLegacyFeedback(){
        val ids=listOf("nezha","xiaolongnv")
        fun step(kind:BattleActionKind=BattleActionKind.SPECIAL,actorSlot:Int?=null,targetSlot:Int?=null,
            actor:String?="xiaolongnv",target:String?="nezha",ability:String?=OriginalBattleMagic.HEAL)=
            BattleActionStep("提神术",5,emptyMap(),actorSlot,targetSlot,kind).apply{
                actorId=actor;targetId=target;abilityId=ability}
        val rejected=listOf(step(actorSlot=3),step(targetSlot=3),step(actor=null),step(target=null),
            step(actor="unknown"),step(target="unknown"),step(ability=null),step(ability="unknown"),
            step(ability=HerbUse.ID),step(BattleActionKind.TEXT),step(BattleActionKind.ATTACK),
            step(BattleActionKind.ICE),step(BattleActionKind.DAMAGE),step(BattleActionKind.MISS))
        for(action in rejected)assertNull(battleVisualSupportTarget(action,ids))
        assertNull(battleVisualSupportTarget(null,ids));assertNull(battleVisualSupportTarget(step(),emptyList()))
    }
}
