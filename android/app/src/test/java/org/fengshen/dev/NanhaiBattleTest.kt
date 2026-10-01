package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Scoped target-ROM facts, verified with controlled original probes; never normal-play evidence.
 * 9:9B29/8528/ACEC accuracy/multiplier/subtract; 9:804C stable order; 9:8E41/AA08 Boss ice;
 * 9:8A69 ID>=136 escape failure; bank46 sea/palace gates and bank5 group count/selection.
 */
class NanhaiBattleTest {
    private val rules=PhysicalRules(mapOf(-1 to 64,0 to 54,1 to 54,2 to 51),listOf(
        242,230,179,128,76,51,25,12,12,9,7,2,
        255,255,255,243,229,217,153,88,37,24,24,12,
        0,0,0,255,255,255,255,241,165,100,75,24))
    private val boss=EnemyDefinition(137,"南海龍王",120,16,13,60,100,243,3,8)
    private val ordinary=EnemyDefinition(4,"鞭形蟲",11,10,4,3,3,192,0)
    private val bossGroup=EncounterGroup(0,listOf(EncounterMember(3,137)),97)
    private fun hero(agility:Int=9,strength:Int=20,weapon:Int=2,hp:Int=100)=
        CharacterState("nezha",1,0,hp,100,0,strength,4,agility,4,0,EquipmentState(weapon,-1,0,28))
    private fun content(enemies:Map<Int,EnemyDefinition> = mapOf(137 to boss),
        groups:List<EncounterGroup> = listOf(bossGroup),agilities:Map<Int,Int> = mapOf(137 to 8),
        zones:List<EncounterZone> = emptyList())=BattleContent(16,listOf(EncounterRect(193,128,216,151)),
        groups,enemies,emptyList(),2,6,50,16,agilities,true,true,zones,rules)
    private class Bytes(vararg values:Int) {
        private val queue=ArrayDeque(values.toList());var calls=0;private set
        fun next():Int {calls++;check(queue.isNotEmpty()){ "Unexpected extra RNG read $calls" };return queue.removeFirst()}
        fun assertExhausted(){assertTrue("Required RNG read was omitted",queue.isEmpty())}
    }

    @Test fun samePlayerByteControlsHitAndMultiplierWithoutAdditionalRead(){
        // 242&63=50<長劍 threshold51; same242 selects two-fold damage17*2=34.
        val bytes=Bytes(242,243);val battle=OpeningBattle(bossGroup,content(),hero(),10)
        val turn=battle.attack(3,bytes::next)!!
        assertEquals(34,turn.playerDamage);assertEquals(86,battle.enemies.single().hp)
        assertEquals(1,turn.enemyMisses);assertEquals(0,turn.enemyDamage)
        assertEquals(2,bytes.calls);bytes.assertExhausted()
    }
    @Test fun sameByteCanMissEvenWhenMultiplierAloneWouldBeFour(){
        val bytes=Bytes(255,243);val battle=OpeningBattle(bossGroup,content(),hero(),10)
        val turn=battle.attack(3,bytes::next)!!
        assertEquals(4,rules.multiplier(1,255));assertFalse(rules.hits(2,255))
        assertEquals(0,turn.playerDamage);assertEquals(120,battle.enemies.single().hp)
        assertTrue(turn.actions.any{it.text=="攻击未命中"&&it.targetSlot==3})
        assertEquals(2,bytes.calls);bytes.assertExhausted()
    }
    @Test fun exactSubtractionDistinguishesEqualityFromUnderflow(){
        for((strength,expected) in listOf(3 to 0,2 to 1)){
            val bytes=Bytes(0,243);val battle=OpeningBattle(bossGroup,content(),hero(strength=strength),10)
            assertEquals(expected,battle.attack(3,bytes::next)!!.playerDamage)
            assertEquals(120-expected,battle.enemies.single().hp);bytes.assertExhausted()
        }
        // Controlled original 255 multiplier fixture: zero remainszero, underflow staysone.
        assertEquals(0,rules.damage(13,13,1,255));assertEquals(1,rules.damage(12,13,1,255))
        assertEquals(17,rules.damage(30,13,1,241));assertEquals(34,rules.damage(30,13,1,242))
        assertEquals(68,rules.damage(30,13,1,255))
    }
    @Test fun multiplierBucketsUseRomLevelIndexAndCapTheObservedTableDomain(){
        assertEquals(1,rules.multiplier(5,240));assertEquals(2,rules.multiplier(6,240))
        assertEquals(rules.multiplier(56,25),rules.multiplier(99,25))
        assertTrue(rules.hits(-1,255));assertTrue(rules.hits(0,53));assertFalse(rules.hits(0,54))
        assertTrue(rules.hits(2,50));assertFalse(rules.hits(2,51))
    }
    @Test fun fasterBossActsBeforePlayerAndCanPreventPlayerActionOnDefeat(){
        val bytes=Bytes(41);val battle=OpeningBattle(bossGroup,content(),hero(agility=7,hp=1),10)
        val turn=battle.attack(3,bytes::next)!!
        assertEquals(3,turn.actions.first().actorSlot);assertEquals(BattlePhase.DEFEAT,turn.phase)
        assertEquals(1,turn.enemyDamage);assertEquals(0,turn.playerDamage)
        assertEquals(120,battle.enemies.single().hp);bytes.assertExhausted()
    }
    @Test fun equalAgilityRetainsPlayerBeforeBoss(){
        val bytes=Bytes(0,41);val turn=OpeningBattle(bossGroup,content(),hero(agility=8),10).attack(3,bytes::next)!!
        assertEquals("攻击 南海龍王",turn.actions.first().text)
        assertEquals(17,turn.playerDamage);assertEquals(10,turn.enemyDamage);bytes.assertExhausted()
    }
    @Test fun enemyTiesRetainOriginalStableSlotOrdering(){
        val group=EncounterGroup(0,listOf(EncounterMember(5,4),EncounterMember(2,4)))
        val bytes=Bytes(0,0,0);val c=content(mapOf(4 to ordinary),listOf(group),mapOf(4 to 9))
        val turn=OpeningBattle(group,c,hero(agility=8,strength=2,weapon=-1),0).attack(5,bytes::next)!!
        val attackSlots=turn.actions.filter{it.text=="鞭形蟲 攻击"}.map{it.actorSlot}
        assertEquals(listOf(2,5),attackSlots);assertEquals(8,turn.enemyDamage);bytes.assertExhausted()
    }
    @Test fun iceChoiceAndPhysicalAccuracyShareOneEnemyByte(){
        for((enemyByte,expectedDamage) in listOf(0 to 8,40 to 8,41 to 10,242 to 10,243 to 0,255 to 0)){
            val bytes=Bytes(0,enemyByte);val battle=OpeningBattle(bossGroup,content(),hero(),10)
            val turn=battle.attack(3,bytes::next)!!
            assertEquals("enemyByte=$enemyByte",expectedDamage,turn.enemyDamage)
            assertEquals(if(enemyByte>=243)1 else 0,turn.enemyMisses)
            assertEquals((enemyByte and 127)<41,turn.actions.any{it.text.contains("冰系攻击")})
            assertEquals(100-expectedDamage,battle.hero.hp);assertEquals(2,bytes.calls);bytes.assertExhausted()
        }
    }
    @Test fun bossEscapeAlwaysFailsButConsumesAnOrdinaryOrderedRound(){
        for(agility in listOf(7,8,9)){
            val bytes=Bytes(0,0);val battle=OpeningBattle(bossGroup,content(),hero(agility=agility),10)
            val turn=battle.escape(bytes::next)!!
            assertEquals(BattlePhase.TARGET,turn.phase);assertEquals(8,turn.enemyDamage)
            assertEquals(0,turn.playerDamage);assertEquals(120,battle.enemies.single().hp)
            assertTrue(turn.actions.any{it.text=="逃跑失败"})
            assertFalse(turn.actions.any{it.text=="逃跑成功"})
            assertEquals(if(agility<8)"南海龍王 冰系攻击" else "尝试逃跑",turn.actions.first().text)
            assertNull(battle.settle(100));assertEquals(2,bytes.calls);bytes.assertExhausted()
        }
    }
    @Test fun restoredSeaAndPalaceHighGatesUse245AndModuloMaskedByte(){
        val seaGroups=(0 until 12).map{EncounterGroup(it,listOf(EncounterMember(2,4)),1)}
        val palaceGroups=(0 until 13).map{EncounterGroup(it,listOf(EncounterMember(2,6)),2)}
        val sea=EncounterZone(25,listOf(EncounterRect(2,23,30,63),EncounterRect(31,35,63,63)),seaGroups,245,true)
        val palace=EncounterZone(97,emptyList(),palaceGroups,245,true)
        val c=content(zones=listOf(sea,palace));val encounter=OpeningEncounter(c,5)
        var called=false;assertNull(encounter.onCompletedStep(25,29,44){called=true;244});assertTrue(called)
        var bytes=Bytes(245,255);assertEquals(7,encounter.onCompletedStep(25,29,44,bytes::next)!!.id)
        assertEquals(0,encounter.steps);bytes.assertExhausted()
        encounter.restore(5);bytes=Bytes(255,255)
        assertEquals(5,encounter.onCompletedStep(97,15,4,bytes::next)!!.id);bytes.assertExhausted()
        encounter.restore(49);bytes=Bytes(63)
        assertEquals(7,encounter.onCompletedStep(25,29,44,bytes::next)!!.id)
        assertEquals(1,bytes.calls);bytes.assertExhausted()
    }
    @Test fun zoneCoordinatesAndMinimumCounterAvoidRandomReadsOutsideRealArea(){
        val sea=EncounterZone(25,listOf(EncounterRect(2,23,30,63)),listOf(bossGroup),245,true)
        val encounter=OpeningEncounter(content(zones=listOf(sea)))
        repeat(4){assertNull(encounter.onCompletedStep(25,29,44){error("Belowminimum mustnot sample")})}
        assertNull(encounter.onCompletedStep(25,2,44){error("Lowerboundary exclusive")})
        assertNull(encounter.onCompletedStep(25,29,64){error("Upperboundary exceeded")})
        assertNull(encounter.onCompletedStep(97,15,4){error("Wrongmap")})
        val bytes=Bytes(245,0);assertNotNull(encounter.onCompletedStep(25,30,63,bytes::next));bytes.assertExhausted()
    }

    // Acquisition fixtures mirror controlled original 2:A0EB/A190 probes. They do not grant gameplay items.
    @Test fun emptyInventoryAcquiresTheActualDragonKingWeaponWhenDropRollPasses(){
        val bytes=Bytes(49);val id="rom.weapon.2"
        val input=emptyMap<String,Int>()
        val result=BattleAcquisition.apply(input,listOf(BattleLoot(id,50,"weapon")),mapOf(id to "weapon"),bytes::next)
        assertEquals(mapOf(id to 1),result.inventory);assertEquals(listOf(id),result.acquired)
        assertTrue(result.skipped.isEmpty());assertTrue(input.isEmpty());bytes.assertExhausted()
    }
    @Test fun tenItemStackSkipsOnlyTheLootAndPreservesExistingEquipment(){
        val id="rom.weapon.2";val input=mapOf(id to 10,"rom.item.0" to 1,"rom.armor.0" to 1)
        val before=input.toMap();val bytes=Bytes(0)
        val result=BattleAcquisition.apply(input,listOf(BattleLoot(id,50,"weapon")),
            mapOf(id to "weapon","rom.item.0" to "weapon","rom.armor.0" to "armor"),bytes::next)
        assertEquals(before,result.inventory);assertEquals(before,input)
        assertTrue(result.acquired.isEmpty());assertEquals(listOf(id),result.skipped);bytes.assertExhausted()
    }
    @Test fun sixteenCategorySlotsRejectNewIdButStillAllowExistingStackIncrement(){
        val input=(3..18).associate{"rom.weapon.$it" to 1}
        val categories=(2..18).associate{"rom.weapon.$it" to "weapon"};val bytes=Bytes(0,0)
        val result=BattleAcquisition.apply(input,listOf(BattleLoot("rom.weapon.2",50,"weapon"),
            BattleLoot("rom.weapon.3",50,"weapon")),categories,bytes::next)
        assertEquals(listOf("rom.weapon.2"),result.skipped);assertEquals(listOf("rom.weapon.3"),result.acquired)
        assertFalse(result.inventory.containsKey("rom.weapon.2"));assertEquals(2,result.inventory["rom.weapon.3"])
        for(id in (4..18).map{"rom.weapon.$it"})assertEquals(1,result.inventory[id])
        assertEquals(16,result.inventory.count{it.value>0});assertEquals(1,input["rom.weapon.3"]);bytes.assertExhausted()
    }
    @Test fun otherCategoriesAndZeroQuantityEntriesDoNotOccupyWeaponSlots(){
        val input=(0..15).associate{"rom.medicine.$it" to 1}+
            (0..15).associate{"rom.armor.$it" to 1}+mapOf("rom.weapon.99" to 0)
        val categories=input.keys.associateWith{it.removePrefix("rom.").substringBefore('.')}+
            mapOf("rom.weapon.2" to "weapon")
        val bytes=Bytes(0);val result=BattleAcquisition.apply(input,
            listOf(BattleLoot("rom.weapon.2",50,"weapon")),categories,bytes::next)
        assertEquals(1,result.inventory["rom.weapon.2"]);assertTrue(result.skipped.isEmpty())
        for((id,n) in input)assertEquals(n,result.inventory[id]);assertFalse(input.containsKey("rom.weapon.2"))
        bytes.assertExhausted()
    }
    @Test fun lootUsesStrictThresholdAfterMaskingBitSeven(){
        for((roll,expected) in listOf(49 to 1,50 to 0,177 to 1,178 to 0,255 to 0)){
            val bytes=Bytes(roll);val id="rom.weapon.2"
            val result=BattleAcquisition.apply(emptyMap(),listOf(BattleLoot(id,50,"weapon")),mapOf(id to "weapon"),bytes::next)
            assertEquals("roll=$roll",expected,result.inventory[id]?:0)
            assertEquals(expected,result.acquired.size);assertTrue(result.skipped.isEmpty());bytes.assertExhausted()
        }
    }
    @Test fun consecutiveDropsObserveUpdatedCountsAndNeverDeleteOldEquipment(){
        val herb="rom.medicine.0";val sword="rom.weapon.2";val other="rom.medicine.7"
        val input=mapOf(herb to 9,sword to 9,"rom.item.0" to 1,"rom.armor.0" to 1)
        val categories=mapOf(herb to "medicine",sword to "weapon",other to "medicine",
            "rom.item.0" to "weapon","rom.armor.0" to "armor")
        val loot=listOf(BattleLoot(herb,10,"medicine"),BattleLoot(herb,10,"medicine"),
            BattleLoot(sword,50,"weapon"),BattleLoot(sword,50,"weapon"),BattleLoot(other,10,"medicine"))
        val bytes=Bytes(0,0,0,0,0);val result=BattleAcquisition.apply(input,loot,categories,bytes::next)
        assertEquals(10,result.inventory[herb]);assertEquals(10,result.inventory[sword]);assertEquals(1,result.inventory[other])
        assertEquals(listOf(herb,sword,other),result.acquired);assertEquals(listOf(herb,sword),result.skipped)
        assertEquals(1,result.inventory["rom.item.0"]);assertEquals(1,result.inventory["rom.armor.0"])
        assertEquals(9,input[herb]);assertEquals(9,input[sword]);assertFalse(input.containsKey(other));bytes.assertExhausted()
    }
    @Test fun consecutiveDistinctDropsRespectCategoryCapacityAfterEachAcceptedItem(){
        val input=(3..17).associate{"rom.weapon.$it" to 1}
        val categories=(2..18).associate{"rom.weapon.$it" to "weapon"};val bytes=Bytes(0,0)
        val result=BattleAcquisition.apply(input,listOf(BattleLoot("rom.weapon.2",50,"weapon"),
            BattleLoot("rom.weapon.18",50,"weapon")),categories,bytes::next)
        assertEquals(listOf("rom.weapon.2"),result.acquired);assertEquals(listOf("rom.weapon.18"),result.skipped)
        assertEquals(16,result.inventory.size);assertFalse(result.inventory.containsKey("rom.weapon.18"))
        for((id,n) in input)assertEquals(n,result.inventory[id]);bytes.assertExhausted()
    }
}
