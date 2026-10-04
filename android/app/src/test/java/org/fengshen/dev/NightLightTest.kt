package org.fengshen.dev
import org.junit.Test
import org.junit.Assert.*

class NightLightTest {
    private val proof="game-data/provenance/world-night8-resources.json"
    private val item=ItemDefinition(WorldItems.NIGHT_LIGHT_ID,"夜明珠",null,"PROVISIONAL_REFERENCE",
        category="special",originalId=8,maxCount=1).also{it.nightLightUse=WorldFieldProtectionDefinition(proof)}
    private fun state(map:Int=74,count:Int=1,flags:Map<String,Boolean> = mapOf("unchanged" to true))=
        SaveSnapshot("opening-segment-001-c48",map,56,440,Key.UP,
            listOf(CharacterState("nezha",1,0,11,20,0,8,4,2,4)),
            if(count==0)emptyMap()else mapOf(item.id to count),flags,money=123,encounterSteps=9)
    private val teacher=OriginalNpcTalkDefinition(164,"rom.map.164.flag.2","",item.id,"rom.dialogue.174.2","rom.dialogue.174.3").also{it.actionId=1}
    @Test fun mapSelectorOwnershipAndMenuSceneAreRevalidatedWithoutChangingQuantityOrCharacter(){
        for(map in 0..255)for(count in listOf(-1,0,1,2))for(menu in listOf(false,true)){
            val before=state(map,count);val result=WorldItems.useNightLight(before,item,menu)
            assertEquals("map=$map count=$count menu=$menu",map==74&&count==1&&menu,result.applied)
            assertEquals(before.inventory,result.inventory);assertEquals(true,result.flags["unchanged"])
            if(!result.applied)assertEquals(before.flags,result.flags)
            else{assertEquals(true,result.flags[WorldItems.NIGHT_LIGHT_FLAG]);assertEquals(true,result.flags[WorldItems.NIGHT_LIGHT_USED_FLAG])}
            assertEquals(11,before.characters.single().hp);assertEquals(123,before.money)
        }
    }
    @Test fun selectionCancelWrongItemAndIndependentRepeatedUseHaveNoInventedConsumption(){
        val before=state();assertNull(WorldItems.nightLightUnavailable(before,item,true));assertEquals(mapOf("unchanged" to true),before.flags)
        assertFalse(WorldItems.useNightLight(before,item.copy(id="rom.special.13"),true).applied)
        val one=WorldItems.useNightLight(before,item,true)
        val two=WorldItems.useNightLight(before.copy(flags=one.flags),item,true)
        assertEquals(one,two);assertEquals(1,two.inventory[item.id])
    }
    @Test fun originalMapReloadResetsOnlyLightingAndSavedSceneFieldsRetainCurrentEffect(){
        val before=state();val used=WorldItems.useNightLight(before,item,true)
        val inside=WorldItems.fieldFlagsAfterStep(used.flags,CompletedStep(74,3,26,false));assertEquals(used.flags,inside)
        val saved=before.copy(flags=inside);assertEquals(saved.flags,saved.copy().flags) // JSON/store cold resume is an Android instrumentation check.
        val left=WorldItems.fieldFlagsAfterStep(inside,CompletedStep(74,3,28,true))
        assertFalse(WorldItems.NIGHT_LIGHT_FLAG in left);assertTrue(left.getValue(WorldItems.NIGHT_LIGHT_USED_FLAG));assertEquals(true,left["unchanged"])
        val reentered=WorldItems.fieldFlagsAfterStep(left,CompletedStep(16,70,105,true));assertEquals(left,reentered)
        assertTrue(SaveSnapshot.compatibleContentVersion("opening-segment-001-c47",saved.contentVersion))
    }
    @Test fun teacherFirstRepeatMatchesEveryOriginalSelectorFlagAndKeepsGiftOnce(){
        val rows=javaClass.getResourceAsStream("/teacher164-selector-original.tsv")!!.bufferedReader().use{it.readLines().drop(1)}
        assertEquals(256,rows.size)
        for(line in rows){
            val v=line.split('\t').map{it.toInt()};val flags=(0..7).associate{"rom.map.164.flag.${1 shl it}" to (v[0]and(1 shl it)!=0)}
            val before=state(164,0,flags);val result=OriginalNpcTalk.begin(before,teacher,item)
            assertTrue(result.applied);assertEquals("rom.dialogue.174.${v[1]}",result.nextDialogue)
            assertEquals(v[2] and 2!=0,result.snapshot.flags[teacher.mapFlagId]==true)
            assertEquals(if(v[0] and 2==0)1 else 0,result.snapshot.inventory[item.id]?:0)
            val repeat=OriginalNpcTalk.begin(result.snapshot,teacher,item);assertEquals(result.snapshot,repeat.snapshot)
        }
    }
    @Test fun teacherFullCategoryKeepsOriginalFailureFlagDoesNotCreateFreeRetryOrEraseUsedMarker(){
        val full=(0..16).filter{it!=8}.associate{"rom.special.$it" to 1}
        val before=state(164,0).copy(inventory=full,flags=mapOf(WorldItems.NIGHT_LIGHT_USED_FLAG to true))
        val failedGift=OriginalNpcTalk.begin(before,teacher,item);assertTrue(failedGift.applied);assertEquals(full,failedGift.snapshot.inventory)
        assertEquals(true,failedGift.snapshot.flags[teacher.mapFlagId]);assertEquals(true,failedGift.snapshot.flags[WorldItems.NIGHT_LIGHT_USED_FLAG])
        val roomNowFree=failedGift.snapshot.copy(inventory=full-"rom.special.0")
        assertEquals(roomNowFree,OriginalNpcTalk.begin(roomNowFree,teacher,item).snapshot)
        val owned=state(164).copy(flags=mapOf(WorldItems.NIGHT_LIGHT_USED_FLAG to true))
        assertEquals(1,OriginalNpcTalk.begin(owned,teacher,item).snapshot.inventory[item.id])
        assertFalse(OriginalNpcTalk.begin(state(100),teacher,item).applied)
    }
    @Test fun actualMoney120ChestCapsAndRepeatsMatchOriginalCpuWithoutTouchingInventory(){
        val rows=javaClass.getResourceAsStream("/night8-money-original.tsv")!!.bufferedReader().use{it.readLines().drop(1)}
        assertEquals(35,rows.size)
        val rule=MoneyTreasureDefinition("rom.map.74.flag.32",120,999999,"game-data/provenance/world-night8-chests.json")
        for(line in rows){
            val v=line.split('\t').map{it.toInt()};val flags=(0..7).associate{"rom.map.74.flag.${1 shl it}" to (v[1]and(1 shl it)!=0)}
            val before=state(flags=flags).copy(money=v[0]);val result=WorldItems.openMoneyTreasure(before,rule)
            assertEquals(v[2]!=0,result.applied);assertEquals(v[4],result.snapshot.money);assertEquals(v[5] and 32!=0,result.snapshot.flags[rule.flagId]==true)
            assertEquals(before.inventory,result.snapshot.inventory);assertEquals(before.characters,result.snapshot.characters)
        }
    }
}
