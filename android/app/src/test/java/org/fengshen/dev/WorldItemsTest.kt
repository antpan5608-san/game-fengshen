package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Representative boundaries from the 46 isolated original CPU cases in world-key-item provenance.
 * These are pure transaction tests, not Android input, persistence or normal original gameplay.
 */
class WorldItemsTest {
    private val item=ItemDefinition(WorldItems.ID,"定海珠",null,"ROM_CPU_VERIFIED",
        category="special",originalId=11,maxCount=1)
    private val treasure=TreasureDefinition(item.id,"rom.map.139.flag.2")
    private val rule=WorldItemUseDefinition(226,"rom.inventory.special.11.used")
    private val target=WorldObjectTarget("rom.object.25.0",25,47,40,226,
        "rom.map.25.flag.1","rom.map.25.flag.128")
    private fun state(x:Int=47,y:Int=41,direction:Key=Key.UP,inventory:Map<String,Int> = mapOf(item.id to 1),
        flags:Map<String,Boolean> = mapOf("rom.event.97.40.2" to true,"untouched.false" to false))=
        SaveSnapshot("fixture",25,x*16+8,y*16+8,direction,
            listOf(CharacterState("nezha",8,500,72,104,0,32,12,10,4,
                equipment=EquipmentState(3,-1,2,29),statusMask=2)),inventory,flags,
            money=303,encounterSteps=17,interiorContext=InteriorContext(1,15,21),terrainMode=1)
    private fun apply(before:SaveSnapshot,result:WorldItems.Result)=
        before.copy(inventory=result.inventory,flags=result.flags)
    private fun unchanged(before:SaveSnapshot,result:WorldItems.Result){
        assertFalse(result.applied);assertNotNull(result.error)
        assertEquals(before,apply(before,result))
    }

    @Test fun chestAddsExactlyOneThenMarksFlagWithoutMutatingInput(){
        val inventory=linkedMapOf("rom.medicine.0" to 3)
        val flags=linkedMapOf("previous" to true,treasure.flagId to false)
        val before=state(inventory=inventory,flags=flags)
        val result=WorldItems.openTreasure(before,treasure,item)
        assertTrue(result.applied);assertNull(result.error)
        assertEquals(inventory+(item.id to 1),result.inventory)
        assertEquals(flags+(treasure.flagId to true),result.flags)
        assertFalse(inventory.containsKey(item.id));assertEquals(false,flags[treasure.flagId])
        assertEquals(before.copy(inventory=inventory+(item.id to 1),flags=flags+(treasure.flagId to true)),apply(before,result))
    }
    @Test fun repeatedChestCannotRewardEvenWhenTheItemIsAbsent(){
        for(inventory in listOf(emptyMap(),mapOf(item.id to 1))){
            val before=state(inventory=inventory,flags=mapOf(treasure.flagId to true))
            unchanged(before,WorldItems.openTreasure(before,treasure,item))
        }
    }
    @Test fun fullSpecialCategoryFailsWithoutClaimingChestAndOneFreeSlotSucceeds(){
        val full=(0..16).filter{it!=11}.associate{"rom.special.$it" to 1}
        val before=state(inventory=full)
        unchanged(before,WorldItems.openTreasure(before,treasure,item))
        assertFalse(before.flags.containsKey(treasure.flagId))
        val oneFree=before.copy(inventory=full-full.keys.first())
        val result=WorldItems.openTreasure(oneFree,treasure,item)
        assertTrue(result.applied);assertEquals(16,result.inventory.size)
        assertEquals(true,result.flags[treasure.flagId])
    }
    @Test fun OtherCategoriesAndZeroCountSpecialEntriesDoNotConsumeSpecialSlots(){
        val inventory=(0..15).associate{"rom.weapon.$it" to 1}+
            (0..16).filter{it!=11}.associate{"rom.special.$it" to 0}+(item.id to 0)
        val result=WorldItems.openTreasure(state(inventory=inventory),treasure,item)
        assertTrue(result.applied);assertEquals(inventory+(item.id to 1),result.inventory)
    }
    @Test fun ownedKeyAndUsedOwnedKeyFailAcquisitionWithoutChangingTheChest(){
        for(used in listOf(false,true)){
            val before=state(flags=mapOf(rule.usedFlagId to used))
            unchanged(before,WorldItems.openTreasure(before,treasure,item))
            assertFalse(before.flags.containsKey(treasure.flagId))
        }
    }
    @Test fun unknownChestDefinitionsAndCorruptQuantityDoNotWriteAnything(){
        val before=state(inventory=emptyMap())
        for(bad in listOf(treasure.copy(itemId="rom.special.12"),treasure.copy(amount=0),
            treasure.copy(amount=2),treasure.copy(flagId=""),treasure.copy(flagId="x".repeat(97))))
            unchanged(before,WorldItems.openTreasure(before,bad,item))
        val corrupt=state(inventory=mapOf(item.id to -1))
        unchanged(corrupt,WorldItems.openTreasure(corrupt,treasure,item))
    }
    @Test fun onlyTheVerifiedStableIdCategoryOriginalIdAndCountLimitAreEnabled(){
        for(bad in listOf(item.copy(id="rom.special.12"),item.copy(category="medicine"),
            item.copy(originalId=12),item.copy(maxCount=10))){
            val before=state()
            unchanged(before,WorldItems.openTreasure(before,treasure,bad))
            assertFalse(WorldItems.available(before,bad,rule,target,true))
            unchanged(before,WorldItems.use(before,bad,rule,target,true))
        }
    }
    @Test fun originalFourFacingNeighboursUseWithoutConsumingTheKey(){
        for((position,direction) in listOf((47 to 41) to Key.UP,(47 to 39) to Key.DOWN,
            (48 to 40) to Key.LEFT,(46 to 40) to Key.RIGHT)){
            val before=state(position.first,position.second,direction)
            assertTrue(WorldItems.available(before,item,rule,target,true))
            val result=WorldItems.use(before,item,rule,target,true)
            assertTrue(result.applied);assertNull(result.error)
            assertEquals(before.inventory,result.inventory)
            assertEquals(before.flags+mapOf(rule.usedFlagId to true,target.removedFlagId to true,
                target.completionFlagId to true),result.flags)
            assertEquals(1,result.inventory[item.id])
            assertEquals(before,apply(before,result).copy(flags=before.flags))
            assertFalse(before.flags.containsKey(target.removedFlagId))
        }
    }
    @Test fun theOriginal28PositionFacingCasesRejectWrongFacingDistanceAndDiagonals(){
        for((x,y) in listOf(47 to 41,47 to 39,48 to 40,46 to 40,47 to 42,48 to 41,47 to 40)){
            for(direction in listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT)){
                val before=state(x,y,direction)
                val legal=when(direction){Key.UP->x==47&&y==41;Key.DOWN->x==47&&y==39
                    Key.LEFT->x==48&&y==40;else->x==46&&y==40}
                assertEquals("$x,$y,$direction",legal,WorldItems.available(before,item,rule,target,true))
                if(!legal)unchanged(before,WorldItems.use(before,item,rule,target,true))
            }
        }
    }
    @Test fun usedMarkerIsNotAConsumptionOrReuseLock(){
        val before=state(flags=mapOf(rule.usedFlagId to true,"other" to true))
        val result=WorldItems.use(before,item,rule,target,true)
        assertTrue(result.applied);assertEquals(1,result.inventory[item.id])
        assertEquals(true,result.flags[rule.usedFlagId]);assertEquals(true,result.flags["other"])
    }
    @Test fun aSecondCommandRechecksLatestRemovalAndDoesNothing(){
        val before=state();val first=WorldItems.use(before,item,rule,target,true)
        assertTrue(first.applied)
        val committed=apply(before,first)
        assertFalse(WorldItems.available(committed,item,rule,target,true))
        unchanged(committed,WorldItems.use(committed,item,rule,target,true))
    }
    @Test fun staleSelectionRechecksCurrentScenePositionDirectionInventoryAndFlags(){
        val before=state();assertTrue(WorldItems.available(before,item,rule,target,true))
        val changed=listOf(before.copy(mapId=139),before.copy(x=before.x+16),
            before.copy(direction=Key.DOWN),before.copy(inventory=emptyMap()),
            before.copy(flags=before.flags+(target.removedFlagId to true)))
        for(current in changed)unchanged(current,WorldItems.use(current,item,rule,target,true))
    }
    @Test fun noItemImpossibleQuantityAndOtherSceneHaveNoEffect(){
        for(inventory in listOf(emptyMap(),mapOf(item.id to 0),mapOf(item.id to -1),
            mapOf(item.id to 2),mapOf(item.id to 129))){
            val before=state(inventory=inventory)
            assertFalse(WorldItems.available(before,item,rule,target,true))
            unchanged(before,WorldItems.use(before,item,rule,target,true))
        }
        val before=state();assertFalse(WorldItems.available(before,item,rule,target,false))
        unchanged(before,WorldItems.use(before,item,rule,target,false))
    }
    @Test fun aMovingSnapshotOrNonDirectionalInputCannotApplyUse(){
        val before=state()
        for(current in listOf(before.copy(x=before.x+1),before.copy(y=before.y-1),
            before.copy(x=-8),before.copy(direction=Key.A),before.copy(direction=Key.MENU)))
            unchanged(current,WorldItems.use(current,item,rule,target,true))
    }
    @Test fun unknownTargetAndAliasedOrMissingFlagsFailClosed(){
        val before=state()
        for(bad in listOf(target.copy(spriteId=144),target.copy(id=""),target.copy(x=-1),
            target.copy(mapId=-1),target.copy(removedFlagId=""),
            target.copy(completionFlagId=target.removedFlagId),target.copy(removedFlagId=rule.usedFlagId)))
            unchanged(before,WorldItems.use(before,item,rule,bad,true))
        for(bad in listOf(rule.copy(targetSpriteId=144),rule.copy(usedFlagId="")))
            unchanged(before,WorldItems.use(before,item,bad,target,true))
    }
    @Test fun checkingAvailabilityAndCancellingBeforeSubmissionHaveNoEffects(){
        val inventory=linkedMapOf(item.id to 1);val flags=linkedMapOf("unrelated" to false)
        val before=state(inventory=inventory,flags=flags)
        repeat(10){assertTrue(WorldItems.available(before,item,rule,target,true))}
        // Cancelling means no use command is submitted; availability remains read-only.
        assertEquals(mapOf(item.id to 1),inventory);assertEquals(mapOf("unrelated" to false),flags)
    }
    @Test fun savedStateFieldsRemainIdenticalExceptForTheTwoExplicitTransactionOutputs(){
        val before=state()
        val result=WorldItems.use(before,item,rule,target,true)
        val committed=apply(before,result)
        assertEquals(before,committed.copy(inventory=before.inventory,flags=before.flags))
        assertEquals(before.characters,committed.characters);assertEquals(before.interiorContext,committed.interiorContext)
        assertEquals(before.terrainMode,committed.terrainMode)
        // Actual json/parse and process-cold-start belong to Android instrumentation,
        // where org.json is real; this JVM assertion does not claim serialization coverage.
    }
}
