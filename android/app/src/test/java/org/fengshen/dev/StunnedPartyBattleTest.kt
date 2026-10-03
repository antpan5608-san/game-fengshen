package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Original behavior8 CPU boundaries are indexed in world-status-bit8.json.
 * These isolated fixtures do not claim a normal Android route has run. */
class StunnedPartyBattleTest {
    private val hero=CharacterState("nezha",13,3131,100,100,0,30,10,20,10,statusMask=8,equipment=EquipmentState(0,-1,0,28))
    private val girl=hero.copy(id="xiaolongnv",equipment=EquipmentState(19,-1,11,38))
    private val physical=PhysicalRules(mapOf(-1 to 64,0 to 64,19 to 64),List(36){255})
    private fun battle(party:List<CharacterState>,behavior:Int=0):OpeningBattle {
        val e=EnemyDefinition(29,"原名暂定",500,25,0,120,25,243,behavior)
        val group=EncounterGroup(0,listOf(EncounterMember(3,29)))
        val rules=BattleContent(23,emptyList(),listOf(group),mapOf(29 to e),listOf(GrowthRow(14,3959,1,1,1,1,1,1,false)),0,6,50,16,
            enemyAgility=mapOf(29 to 22),escapeEnabled=true,physicalRules=physical).also{
            it.characterPhysicalRules=mapOf("xiaolongnv" to physical)
            it.characterGrowth=mapOf("xiaolongnv" to listOf(GrowthRow(13,3131,1,1,1,1,1,1,false)))
        }
        return OpeningBattle(group,rules,party.first(),0,0).also{
            it.configureParty(party,mapOf("nezha" to 0,"xiaolongnv" to 1),mapOf("nezha" to 0,"xiaolongnv" to 0),mapOf("nezha" to 0,"xiaolongnv" to 0))
        }
    }
    @Test fun originalPriorityOnlyReplacesNormal04And08WithoutChangingHp(){
        for(mask in listOf(0,1,2,4,8,16,32,64,6)){
            val p=hero.copy(statusMask=mask);val applied=OriginalStatus.applyStatus8(p)
            assertEquals(if(mask in setOf(0,4,8))8 else mask,applied.statusMask)
            assertEquals(p,applied.copy(statusMask=mask))
        }
        val dead=hero.copy(hp=0,statusMask=32);assertEquals(dead,OriginalStatus.applyStatus8(dead))
    }
    @Test fun wholly08PartyRunsEnemyAndSequentialRecoveryWithoutInventedPlayerCommand(){
        val b=battle(listOf(hero,girl));assertNull(b.inputHero);var rolls=0
        val turn=b.continueSkippedCommands{rolls++;0}!!
        assertEquals(1,rolls);assertEquals(0,turn.playerDamage);assertEquals(15,turn.enemyDamage)
        assertFalse(turn.actions.any{it.actorId!=null&&it.kind==BattleActionKind.ATTACK})
        assertEquals(listOf(0,0),b.party.map{it.statusMask});assertEquals("nezha",b.inputHero!!.id)
        assertNull(b.continueSkippedCommands{error("Eligible input must not advance another round")})
        assertEquals(1,rolls)
    }
    @Test fun ongoing08WaitsForNextDisplayedBoundaryAndUsesOneBytePerActualEnemyAction(){
        val b=battle(listOf(hero,girl));var rolls=0
        val first=b.continueSkippedCommands{rolls++;2}!!
        assertEquals(listOf(8,0),b.party.map{it.statusMask}) // sequential rotate2→129→192
        assertEquals("xiaolongnv",b.inputHero!!.id);assertEquals(1,rolls)
        assertNull(b.continueSkippedCommands{error("One recovered actor now needs a real command")})
        assertEquals(BattlePhase.TARGET,first.phase)
    }
    @Test fun loneLivingStunnedActorContinuesButNoSurvivorCannotGainTurnOrReward(){
        val dead=hero.copy(hp=0,statusMask=32)
        val b=battle(listOf(dead,girl));assertNotNull(b.continueSkippedCommands{0})
        assertEquals(dead,b.party.first());assertEquals("xiaolongnv",b.inputHero!!.id)
        val allDead=battle(listOf(dead,girl.copy(hp=0,statusMask=32)))
        assertNull(allDead.continueSkippedCommands{error("No living actor must not consume randomness")})
        assertNull(allDead.settle(50))
    }
    @Test fun behavior8UsesIndependentOriginalTargetAndSkipsActorStunnedBeforeItsAction(){
        val b=battle(listOf(hero.copy(statusMask=0,agility=1),girl.copy(statusMask=0,agility=1)),8)
        assertNull(b.attack(3){error("First actor only queues")});var rolls=0
        val turn=b.attack(3){rolls++;4}!!
        val applied=turn.actions.first{it.kind==BattleActionKind.STATUS&&it.targetId=="xiaolongnv"}
        assertEquals(8,applied.partyStatus["xiaolongnv"]);assertEquals(0,applied.partyStatus["nezha"])
        assertFalse(turn.actions.any{it.actorId=="xiaolongnv"&&it.kind==BattleActionKind.ATTACK})
        assertEquals(2,rolls);assertEquals(100,b.party[1].hp)
    }
    @Test fun escapeCleanupClears08AndKeepsOtherOriginalStatusesAndInventory(){
        val b=battle(listOf(hero,girl.copy(statusMask=2)));assertEquals("xiaolongnv",b.inputHero!!.id)
        assertEquals(BattlePhase.ESCAPED,b.escape{0}!!.phase)
        assertEquals(listOf(0,2),b.charactersAfterBattle().map{it.statusMask})
        // Enemy agility22 precedes the actors20: leaving battle must preserve
        // that real damage, not heal it during08 cleanup.
        assertEquals(listOf(85,100),b.charactersAfterBattle().map{it.hp})
        assertEquals(b.party.map{it.hp},b.charactersAfterBattle().map{it.hp});assertNull(b.settle(10))
        assertEquals(mapOf(HerbUse.ID to 2),b.inventoryAfterBattle(mapOf(HerbUse.ID to 2)))
    }
}
