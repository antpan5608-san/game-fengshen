package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Isolated boundary inputs; normal cartridge play remains separately indexed in world-two-party.json. */
class PartyBattleTest {
    @Test fun growthValidationUsesOriginalFieldWidthsForEveryActor(){
        // Target ROM actor1 level59 row DD6A83... contains HP bytes 00 01.
        val actual=GrowthRow(59,992795,256,8,28,15,9,5,false)
        assertTrue(validGrowthRow(actual))
        assertTrue(validGrowthRow(actual.copy(hp=65535)))
        assertFalse(validGrowthRow(actual.copy(hp=65536)))
        assertFalse(validGrowthRow(actual.copy(hp=-1)))
        for(bad in listOf(actual.copy(mp=256),actual.copy(strength=256),actual.copy(stamina=256),
            actual.copy(agility=256),actual.copy(spirit=256),actual.copy(threshold=0)))assertFalse(validGrowthRow(bad))
    }
    private val hero=CharacterState("nezha",12,2000,50,100,0,20,10,20,10,0,EquipmentState(0,-1,0,28))
    private val girl=CharacterState("xiaolongnv",12,2000,92,92,44,22,14,26,43,maxMp=44,equipment=EquipmentState(19,-1,11,38),statusMask=0)
    private val item=ItemDefinition(HerbUse.ID,"藥草",null,"verified herb rules",category="medicine",originalId=0,
        herbUse=HerbUseDefinition(50,true,"world-two-party"))
    private val physical=PhysicalRules(mapOf(-1 to 64,0 to 64,19 to 64),List(36){255})
    private fun make(enemyHp:Int=38,enemyAgility:Int=1,party:List<CharacterState> = listOf(hero,girl),slots:List<Int> = listOf(3),ice:Int?=null):OpeningBattle {
        val enemy=EnemyDefinition(18,"原名未核",enemyHp,25,0,39,13,255,if(ice==null)0 else 3,ice)
        val group=EncounterGroup(1,slots.map{EncounterMember(it,18)})
        val c=BattleContent(23,emptyList(),listOf(group),mapOf(18 to enemy),listOf(GrowthRow(13,2010,8,0,2,1,1,0,false)),0,6,50,16,
            enemyAgility=mapOf(18 to enemyAgility),escapeEnabled=true,physicalRules=physical).also{
            it.characterPhysicalRules=mapOf("xiaolongnv" to physical)
            it.characterGrowth=mapOf("xiaolongnv" to listOf(GrowthRow(13,2525,6,4,1,1,2,2,false)))
        }
        return OpeningBattle(group,c,party.first(),0,0).also{it.configureParty(party,mapOf("nezha" to 0,"xiaolongnv" to 1),
            mapOf("nezha" to 0,"xiaolongnv" to 20),mapOf("nezha" to 0,"xiaolongnv" to 20))}
    }
    @Test fun thirdActorCollectsOnceUsesOwnInitialContributionAndReceivesOwnXpShare(){
        val yang=CharacterState("yangjian",24,26000,495,495,54,96,60,28,69,maxMp=54,equipment=EquipmentState(33,33,18,29))
        val enemy=EnemyDefinition(18,"fixture",50,25,0,39,13,255,0)
        val group=EncounterGroup(1,listOf(EncounterMember(3,18)))
        val thresholds=javaClass.getResourceAsStream("/world-yang-multiplier-original.tsv")!!.bufferedReader().readLines().single().split("\t").map{it.toInt()}
        val own=PhysicalRules(physical.weaponHitThreshold+(33 to 64),thresholds)
        val c=BattleContent(23,emptyList(),listOf(group),mapOf(18 to enemy),listOf(GrowthRow(13,2010,8,0,2,1,1,0,false)),0,6,50,16,
            enemyAgility=mapOf(18 to 1),escapeEnabled=true,physicalRules=physical).also{
            it.characterPhysicalRules=mapOf("xiaolongnv" to physical,"yangjian" to own)
            it.characterGrowth=mapOf("xiaolongnv" to listOf(GrowthRow(13,2525,6,4,1,1,2,2,false)),
                "yangjian" to listOf(GrowthRow(25,29899,56,3,9,4,2,2,false)))
        }
        val b=OpeningBattle(group,c,hero,0,0).also{it.configureParty(listOf(hero,girl,yang),
            mapOf("nezha" to 0,"xiaolongnv" to 1,"yangjian" to 2),mapOf("nezha" to 0,"xiaolongnv" to 20,"yangjian" to 58),
            mapOf("nezha" to 0,"xiaolongnv" to 20,"yangjian" to 50))}
        assertNull(b.attack(3){error("first command cannot act")})
        assertNull(b.attack(3){error("second command cannot act")});assertEquals("yangjian",b.inputHero!!.id)
        var rolls=0;val turn=b.attack(3){rolls++;0}!!
        assertEquals(BattlePhase.VICTORY,turn.phase);assertEquals(1,rolls)
        assertEquals("yangjian",turn.actions.first().actorId)
        val reward=b.settle(100)!!;assertEquals(113,reward.money)
        assertEquals(mapOf("nezha" to 13,"xiaolongnv" to 13,"yangjian" to 13),reward.experienceByCharacter)
        assertEquals(26013,reward.characters[2].experience);assertEquals(24,reward.characters[2].level)
        assertNull(b.settle(reward.money))
    }
    @Test fun firstCommandQueuesWithoutHpRandomOrRewardsThenFasterActorWins(){
        val b=make();var rolls=0
        assertEquals("nezha",b.inputHero!!.id)
        assertNull(b.attack(3){rolls++;0});assertEquals(0,rolls);assertEquals(38,b.enemies.single().hp)
        assertEquals("xiaolongnv",b.inputHero!!.id);assertEquals(1,b.inputRevision)
        val t=b.attack(3){rolls++;0}!!
        assertEquals(BattlePhase.VICTORY,t.phase);assertEquals(1,rolls)
        assertEquals("xiaolongnv",t.actions.first().actorId)
        val reward=b.settle(100)!!
        assertEquals(113,reward.money);assertEquals(mapOf("nezha" to 19,"xiaolongnv" to 19),reward.experienceByCharacter)
        assertEquals(13,reward.characters[0].level);assertEquals(12,reward.characters[1].level)
        assertEquals(2019,reward.characters[1].experience);assertNull(b.settle(reward.money))
    }
    @Test fun invalidTargetDoesNotSubmitAndDeadEnemyRetargetUsesStableSlots(){
        val b=make(slots=listOf(1,5));var rolls=0
        assertNull(b.attack(0){error("invalid input reads RNG")});assertEquals(0,b.inputRevision)
        assertNull(b.attack(5){rolls++;0})
        val t=b.attack(5){rolls++;0}!!
        assertEquals(listOf(5,1),t.actions.filter{it.kind==BattleActionKind.ATTACK&&it.actorSlot==null}.map{it.targetSlot})
        assertEquals(0,b.enemies.first{it.slot==5}.hp);assertEquals(18,b.enemies.first{it.slot==1}.hp)
        assertEquals(3,rolls)
    }
    @Test fun enemySelectsIndependentActorUsingSameHitByteAndSnapshotsKeepOtherHp(){
        val b=make(enemyHp=500,enemyAgility=30);var rolls=0
        b.attack(3){error("first input must not act")}
        val t=b.attack(3){rolls++;4}!!
        assertEquals(3,rolls)
        val hit=t.actions.first{it.kind==BattleActionKind.DAMAGE&&it.actorSlot==3}
        assertEquals("xiaolongnv",hit.targetId);assertEquals(91,hit.partyHp["xiaolongnv"])
        assertEquals(50,hit.partyHp["nezha"])
        assertEquals(92,t.actions.first().partyHp["xiaolongnv"])
    }
    @Test fun herbTargetIsIndependentFromActingActorAndReservedBeforeSecondInput(){
        val b=make(enemyHp=500);var rolls=0
        assertNull(b.useHerb("xiaolongnv",1,item){error("queued herb must not resolve")})
        assertEquals(1,b.herbsConsumed);assertEquals(92,b.party[1].hp)
        assertNull(b.useHerb("nezha",1,item){error("reserved inventory cannot be spent again")})
        assertEquals("xiaolongnv",b.inputHero!!.id)
        val t=b.attack(3){rolls++;4}!!
        val heal=t.actions.single{it.kind==BattleActionKind.HEAL}
        assertEquals("nezha",heal.actorId);assertEquals("xiaolongnv",heal.targetId);assertEquals(0,heal.hpDelta)
        assertEquals(2,rolls);assertTrue(b.inventoryAfterBattle(mapOf(HerbUse.ID to 1)).isEmpty())
    }
    @Test fun fallenFirstActorDoesNotBlockSecondInputAndReceivesNoShare(){
        val dead=hero.copy(hp=0,statusMask=OriginalStatus.DEAD)
        val b=make(party=listOf(dead,girl));assertEquals("xiaolongnv",b.inputHero!!.id)
        assertEquals(BattlePhase.VICTORY,b.attack(3){0}!!.phase)
        val reward=b.settle(10)!!
        assertEquals(0,reward.experienceByCharacter["nezha"]);assertEquals(39,reward.experienceByCharacter["xiaolongnv"])
        assertEquals(dead,reward.characters.first())
    }
    @Test fun oneActorDyingDoesNotEndBattleWhileSecondRemains(){
        val b=make(enemyHp=500,enemyAgility=30,party=listOf(hero.copy(hp=1),girl))
        b.attack(3){error("first input")}
        val t=b.attack(3){0}!!
        assertEquals(BattlePhase.TARGET,t.phase);assertEquals(0,b.hero.hp)
        assertEquals(OriginalStatus.DEAD,b.hero.statusMask);assertEquals("xiaolongnv",b.inputHero!!.id)
        assertFalse(t.actions.any{it.actorId=="nezha"&&it.kind==BattleActionKind.ATTACK})
    }
    @Test fun oneIceDecisionAppliesSequentialSnapshotsToBothLivingTargets(){
        val b=make(enemyHp=500,enemyAgility=30,party=listOf(hero.copy(hp=5),girl.copy(statusMask=4)),ice=8)
        b.attack(3){error("first input")};var rolls=0
        val t=b.attack(3){rolls++;0}!!
        val hits=t.actions.filter{it.actorSlot==3&&it.kind==BattleActionKind.DAMAGE}
        assertEquals(2,hits.size);assertEquals(5,-hits[0].hpDelta);assertEquals(16,-hits[1].hpDelta)
        assertEquals(92,hits[0].partyHp["xiaolongnv"]);assertEquals(76,hits[1].partyHp["xiaolongnv"])
        assertEquals(BattlePhase.TARGET,t.phase);assertEquals(2,rolls) // one ice decision, surviving actor physical action
        assertEquals("xiaolongnv",b.inputHero!!.id)
    }
    @Test fun allTargetIceCompletesBothDamagesBeforeTotalDefeat(){
        val b=make(enemyHp=500,enemyAgility=30,party=listOf(hero.copy(hp=5),girl.copy(hp=6)),ice=8)
        b.attack(3){error("first input")};var rolls=0
        val t=b.attack(3){rolls++;0}!!
        assertEquals(BattlePhase.DEFEAT,t.phase);assertEquals(1,rolls);assertTrue(b.party.all{it.hp==0})
        assertEquals(2,t.actions.count{it.kind==BattleActionKind.DAMAGE});assertNull(b.settle(10))
    }
    @Test fun forcedEscapeFailureRetriesAtActorsExistingLaterSchedulerSlot(){
        val b=make(enemyHp=500);b.escape{error("first command")}
        val bytes=ArrayDeque(listOf(0,40,0));val turn=b.escape{bytes.removeFirst()}!!
        assertTrue(bytes.isEmpty());assertEquals(BattlePhase.ESCAPED,turn.phase)
        assertEquals(listOf("xiaolongnv","nezha","nezha"),turn.actions.filter{it.kind==BattleActionKind.ESCAPE}.map{it.actorId})
        assertFalse(turn.actions.any{it.actorSlot!=null});assertEquals(listOf(hero,girl),b.party)
    }
    @Test fun partialEscapeDoesNotProtectActorAndNextRoundCollectsBothCommands(){
        val b=make(enemyHp=500);b.escape{error("first command")}
        val bytes=ArrayDeque(listOf(0,40,40,4));val turn=b.escape{bytes.removeFirst()}!!
        assertTrue(bytes.isEmpty());assertEquals(BattlePhase.TARGET,turn.phase)
        assertEquals("xiaolongnv",turn.actions.first{it.kind==BattleActionKind.DAMAGE}.targetId)
        assertEquals(91,b.party[1].hp);assertEquals("nezha",b.inputHero!!.id)
        assertNull(b.attack(3){error("new round first input")});assertEquals("xiaolongnv",b.inputHero!!.id)
    }
    @Test fun forcedEscapeReplacesConfirmedHerbWithoutRefundOrFreeHealing(){
        val b=make(enemyHp=500);b.useHerb("nezha",1,item){error("first command")}
        var draws=0;val turn=b.escape{draws++;0}!!
        assertEquals(BattlePhase.ESCAPED,turn.phase);assertEquals(2,draws)
        assertEquals(1,b.herbsConsumed);assertEquals(hero.hp,b.hero.hp)
        assertFalse(turn.actions.any{it.kind==BattleActionKind.HEAL})
        assertTrue(b.inventoryAfterBattle(mapOf(HerbUse.ID to 1)).isEmpty())
    }
    @Test fun battleHerbConfirmedForDeadTeammateConsumesWithoutRevivalUnlikeMapUse(){
        val dead=hero.copy(hp=0,statusMask=OriginalStatus.DEAD);val b=make(enemyHp=500,party=listOf(dead,girl))
        assertTrue(b.herbAvailable("nezha",1,item));var draws=0
        val t=b.useHerb("nezha",1,item){draws++;255}!!
        assertEquals(1,draws);assertEquals(1,b.herbsConsumed);assertEquals(dead,b.hero)
        assertFalse(t.actions.any{it.kind==BattleActionKind.HEAL});assertEquals(BattlePhase.TARGET,t.phase)
        assertEquals("xiaolongnv",b.inputHero!!.id)
    }
    @Test fun twoActorStatusSpaceAtAllSupportedFontScalesDoesNotOverlapCommands(){
        for(font in listOf(1f,1.3f,2f)){
            val l=battleTouchLayout(Box(0f,0f,900f,430f),1f,font,2,2)
            assertTrue(l.status.h>=48*font+18);assertTrue(l.status.y+l.status.h<l.commands.first().y)
            assertTrue(l.enemies.all{it.y+it.h<l.status.y&&it.h>=48f})
            assertTrue(l.arena.h>1f)
        }
    }
}
