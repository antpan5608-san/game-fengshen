package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Isolated scheduler fixtures; native effect expectations are in the committed CPU tables. */
class BattleMagicQueueTest {
    private val n=CharacterState("nezha",1,0,5,200,0,8,4,2,4,maxMp=0)
    private val x=CharacterState("xiaolongnv",12,2000,92,92,44,22,14,26,43,maxMp=44)
    private val y=x.copy(id="yangjian",level=24,agility=28,mp=54,maxMp=54)
    private val j=x.copy(id="jiangziya",level=38,agility=63,mp=151,maxMp=151)
    private fun make(party:List<CharacterState> = listOf(n,x,y,j),enemyAgility:Int=1,enabled:Boolean=true):OpeningBattle {
        val enemy=EnemyDefinition(18,"fixture",500,25,0,0,0,255,0)
        val group=EncounterGroup(1,listOf(EncounterMember(3,18)))
        val physical=PhysicalRules(mapOf(-1 to 64),List(36){255})
        val growth=listOf(GrowthRow(2,10,1,1,1,1,1,1,false))
        val rules=BattleContent(23,emptyList(),listOf(group),mapOf(18 to enemy),growth,0,6,50,16,
            enemyAgility=mapOf(18 to enemyAgility),physicalRules=physical).also{
                it.originalMagicEnabled=enabled
                it.characterPhysicalRules=mapOf(x.id to physical,y.id to physical,j.id to physical)
                it.characterGrowth=mapOf(x.id to growth,y.id to growth,j.id to growth)
            }
        return OpeningBattle(group,rules,party.first(),0,0).also{b->
            b.configureParty(party,mapOf(n.id to 0,x.id to 1,y.id to 2,j.id to 3),
                party.associate{it.id to 0},party.associate{it.id to 0})
        }
    }
    private fun finish(b:OpeningBattle,random:Int=255):BattleTurn {
        var result:BattleTurn?=null
        repeat(4){
            if(result==null&&b.inputHero!=null)result=b.attack(3){random}
        }
        assertNotNull("Four-party command collection must produce exactly one turn",result)
        return result!!
    }

    @Test fun fourRealCommandsQueueWithoutCostThenOrderedEffectAndDebit(){
        val b=make();val before=b.party
        assertNull(b.attack(3){error("First actor cannot run")})
        assertNull(b.useMagic(OriginalBattleMagic.HEAL,n.id){error("Queued magic cannot sample RNG")})
        assertEquals(before,b.party);assertEquals(2,b.inputRevision);assertEquals(y.id,b.inputHero!!.id)
        val turn=finish(b)
        val magic=turn.actions.filter{it.actorId==x.id}
        assertEquals(listOf(44,44,41),magic.map{it.partyMp.getValue(x.id)})
        assertEquals(listOf(5,58,58),magic.map{it.partyHp.getValue(n.id)})
        assertTrue(magic.all{it.abilityId==OriginalBattleMagic.HEAL})
        assertEquals(listOf(BattleVisualPose.CAST,BattleVisualPose.CAST,BattleVisualPose.IDLE),
            magic.map{battleVisualPose(it,x.id)})
        assertTrue(turn.actions.filter{it.actorId!=x.id}.all{it.abilityId==null})
        assertEquals(listOf(j.id,y.id,x.id,n.id),turn.actions.mapNotNull{it.actorId}.distinct())
        assertEquals(41,b.party.single{it.id==x.id}.mp)
        assertEquals(58,b.hero.hp)
        for(frame in magic){
            val view=battlePartyView(b.party.single{it.id==x.id},"小龙女",frame,null,n.id)
            assertEquals(frame.partyMp.getValue(x.id),view.mp)
        }
    }
    @Test fun selfHealingSurvivesLateDebit(){
        val b=make(listOf(n,x.copy(hp=5),y,j))
        b.attack(3){error("No early RNG")};b.useMagic(OriginalBattleMagic.HEAL,x.id){error("No early RNG")}
        val turn=finish(b);val caster=b.party.single{it.id==x.id}
        assertEquals(58,caster.hp);assertEquals(41,caster.mp)
        assertEquals(58,turn.actions.last{it.actorId==x.id}.partyHp.getValue(x.id))
    }
    @Test fun initialDeadTargetIsSelectableButNotRevivedAndPays(){
        val b=make(listOf(n.copy(hp=0,statusMask=32),x,y,j))
        assertEquals(x.id,b.inputHero!!.id);assertNull(b.magicReason(OriginalBattleMagic.HEAL,n.id))
        b.useMagic(OriginalBattleMagic.HEAL,n.id){error("Still two commands missing")}
        val turn=finish(b)
        assertEquals(0,b.hero.hp);assertEquals(32,b.hero.statusMask)
        assertEquals(41,b.party.single{it.id==x.id}.mp)
        assertFalse(turn.actions.any{it.actorId==x.id&&it.kind==BattleActionKind.HEAL})
    }
    @Test fun fullTargetAndHealthyCurePayWhileLowHpPostStateMatchesNative(){
        for((spell,target) in listOf(OriginalBattleMagic.HEAL to n.copy(hp=200),
            OriginalBattleMagic.ANTIDOTE to n,OriginalBattleMagic.ANTIDOTE to n.copy(statusMask=2))){
            val b=make(listOf(target,x,y,j));b.attack(3){error("No early RNG")}
            b.useMagic(spell,n.id){error("No early RNG")};val turn=finish(b)
            assertEquals(target.hp,b.hero.hp);assertEquals(41,b.party.single{it.id==x.id}.mp)
            assertEquals(if(target.hp==5)1 else 0,b.hero.statusMask)
            if(target.statusMask==2){
                assertEquals(listOf(2,0,0,1),turn.actions.filter{it.actorId==x.id}.map{it.partyStatus.getValue(n.id)})
            }
            assertTrue(turn.actions.filter{it.actorId==x.id}.all{it.abilityId==spell})
        }
    }
    @Test fun fasterEnemyKillsTargetButSpellStillDebits(){
        val b=make(enemyAgility=100);b.attack(3){error("No early RNG")}
        b.useMagic(OriginalBattleMagic.HEAL,n.id){error("No early RNG")}
        val turn=finish(b,0)
        assertEquals(0,b.hero.hp);assertEquals(32,b.hero.statusMask)
        assertEquals(41,b.party.single{it.id==x.id}.mp)
        assertTrue(turn.actions.first().actorSlot!=null)
    }
    @Test fun fasterEnemyKillsCasterSoNoEffectOrDebit(){
        val b=make(listOf(n.copy(hp=200),x.copy(hp=5),y,j),enemyAgility=100)
        b.attack(3){error("No early RNG")};b.useMagic(OriginalBattleMagic.HEAL,n.id){error("No early RNG")}
        val turn=finish(b,4);val caster=b.party.single{it.id==x.id}
        assertEquals(0,caster.hp);assertEquals(44,caster.mp)
        assertFalse(turn.actions.any{it.actorId==x.id})
    }
    @Test fun initialInsufficientMpAndUnsupportedCommandsDoNotAdvance(){
        val b=make(listOf(n,x.copy(mp=2),y,j));val initial=b.party
        assertNull(b.useMagic(OriginalBattleMagic.HEAL,n.id){error("Wrong actor cannot run")})
        assertEquals(0,b.inputRevision);b.attack(3){error("Only first command")}
        for((spell,target) in listOf(OriginalBattleMagic.HEAL to n.id,"unknown" to n.id,OriginalBattleMagic.HEAL to "absent")){
            assertNotNull(b.magicReason(spell,target));assertNull(b.useMagic(spell,target){error("Invalid input cannot run")})
        }
        assertEquals(1,b.inputRevision);assertEquals(initial,b.party)
        val disabled=make(enabled=false);disabled.attack(3){error("No early RNG")}
        assertTrue(disabled.learnedMagic().isEmpty());assertNotNull(disabled.magicReason(OriginalBattleMagic.HEAL,n.id))
    }
    @Test fun oldActionFramesRetainMpFallback(){
        val old=BattleActionStep("historical ABI",5,emptyMap())
        assertNull(old.abilityId)
        assertEquals(44,battlePartyView(x,"小龙女",old,null,n.id).mp)
    }
}
