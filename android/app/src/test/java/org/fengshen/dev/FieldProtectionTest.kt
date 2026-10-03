package org.fengshen.dev
import org.junit.Test
import org.junit.Assert.*

class FieldProtectionTest {
    private val item=ItemDefinition(WorldItems.FIELD_PROTECTION_ID,"定神珠",null,"GAMEPLAY_VERIFIED",
        category="special",originalId=12,maxCount=1).also{
            it.fieldProtectionUse=WorldFieldProtectionDefinition("game-data/provenance/world-field67-item12.json")}
    private fun state(map:Int=67,count:Int=1,flags:Map<String,Boolean> = emptyMap())=
        SaveSnapshot("fixture",map,248,472,Key.UP,listOf(CharacterState("nezha",1,0,11,20,0,8,4,2,4)),
            if(count==0)emptyMap()else mapOf(item.id to count),flags,money=44,encounterSteps=9)
    @Test fun legalSelectionAndConfirmationMatchOriginalMenuDispatchAndBookkeeping(){
        val rows=javaClass.getResourceAsStream("/field67-item12-cpu.tsv")!!.bufferedReader().use{it.readLines()}
        assertEquals(48,rows.size)
        for(line in rows){
            val v=line.split('\t').map{it.toInt()}
            val flags=mapOf(WorldItems.FIELD_PENDING_FLAG to(v[1]!=0),WorldItems.FIELD_USED_FLAG to(v[2]and 128!=0),"untouched" to true)
            val before=state(v[0],v[2]and 127,flags)
            val result=WorldItems.useFieldProtection(before,item,true)
            assertEquals(line,v[4]!=0,result.applied)
            assertEquals(line,before.inventory,result.inventory)
            assertEquals(line,v[5]!=0,result.flags[WorldItems.FIELD_PENDING_FLAG]==true)
            assertEquals(line,v[6]and 128!=0,result.flags[WorldItems.FIELD_USED_FLAG]==true)
            assertEquals(true,result.flags["untouched"])
        }
    }
    @Test fun sourceStepPromotionMapReconstructionAndPoisonRemainSeparate(){
        val before=state();val used=WorldItems.useFieldProtection(before,item,true)
        assertTrue(used.applied);assertFalse(used.flags[WorldItems.FIELD_ACTIVE_FLAG]==true)
        val one=WorldItems.fieldFlagsAfterStep(used.flags,CompletedStep(67,15,28,false))
        assertTrue(one.getValue(WorldItems.FIELD_ACTIVE_FLAG))
        val out=WorldItems.fieldFlagsAfterStep(one,CompletedStep(67,15,29,true))
        assertFalse(WorldItems.FIELD_PENDING_FLAG in out);assertTrue(out.getValue(WorldItems.FIELD_ACTIVE_FLAG))
        val back=WorldItems.fieldFlagsAfterStep(out,CompletedStep(23,44,3,true))
        assertEquals(out,back)
        val poisoned=before.characters.single().copy(statusMask=2)
        assertEquals(10,OriginalStatus.step(listOf(poisoned),67,back[WorldItems.FIELD_ACTIVE_FLAG]==true).single().hp)
        assertEquals(2,OriginalStatus.step(listOf(poisoned),67,true).single().statusMask)
        assertEquals(11,before.characters.single().hp)
    }
    @Test fun unavailableContextsInvalidInventoryAndUnreviewedItemsHaveNoSideEffects(){
        for(s in listOf(state(23),state(count=0),state(count=2),state(count=-1))){
            val r=WorldItems.useFieldProtection(s,item,true)
            assertFalse(r.applied);assertEquals(s.inventory,r.inventory);assertEquals(s.flags,r.flags)
        }
        val before=state();assertFalse(WorldItems.useFieldProtection(before,item,false).applied)
        assertFalse(WorldItems.useFieldProtection(before,item.copy(id=WorldItems.ID),true).applied)
        assertFalse(WorldItems.FIELD_PENDING_FLAG in before.flags)
        // An independent valid second use is legal and cannot duplicate quantities.
        val first=WorldItems.useFieldProtection(before,item,true)
        val second=WorldItems.useFieldProtection(before.copy(flags=first.flags),item,true)
        assertEquals(first,second)
    }
}
