package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class TouchUxTest {
    private val knife=EquipmentDefinition("knife",0,"rightHand",2,setOf("nezha"),"existing")
    private val hand=EquipmentDefinition("hand",1,"rightHand",7,setOf("nezha"),"existing")
    private val hero=CharacterState("nezha",1,0,20,20,0,8,6,4,3,equipment=EquipmentState(0,-1,0,28))
    @Test fun replacementEqualsExistingLegalSequence(){
        val bag=mapOf("hand" to 2,"knife" to 9)
        val removed=OpeningEquipment.unequip(hero,bag,knife)!!
        val old=OpeningEquipment.equip(removed.first,removed.second,hand)!!
        assertEquals(old,OpeningEquipment.replace(hero,bag,hand,listOf(knife,hand)))
        assertEquals(EquipmentState(0,-1,0,28),hero.equipment);assertEquals(9,bag["knife"])
    }
    @Test fun fullReturnOrMissingCandidateNeverLosesEquipment(){
        assertNull(OpeningEquipment.replace(hero,mapOf("hand" to 1,"knife" to 10),hand,listOf(knife,hand)))
        assertNull(OpeningEquipment.replace(hero,mapOf("knife" to 1),hand,listOf(knife,hand)))
        assertEquals(0,hero.equipment!!.rightHand)
    }
    @Test fun unknownOldEquipmentAndIneligibleTargetRejected(){
        assertNull(OpeningEquipment.replace(hero,mapOf("hand" to 1),hand,listOf(hand)))
        assertNull(OpeningEquipment.replace(hero.copy(id="other"),mapOf("hand" to 1),hand,listOf(knife,hand)))
        assertNull(OpeningEquipment.replace(hero,mapOf("knife" to 1),knife,listOf(knife,hand)))
    }
    @Test fun emptySlotUsesOriginalEquipResult(){
        val empty=hero.copy(equipment=hero.equipment!!.copy(rightHand=-1));val bag=mapOf("hand" to 1)
        assertEquals(OpeningEquipment.equip(empty,bag,hand),OpeningEquipment.replace(empty,bag,hand,listOf(knife,hand)))
    }
    private fun overlaps(a:Box,b:Box)=a.x<b.x+b.w&&a.x+a.w>b.x&&a.y<b.y+b.h&&a.y+a.h>b.y
    @Test fun phoneGeometryKeepsDpTargetsAndSeparateHitRegions(){
        for(scale in listOf(1f,1.3f,2f))for(secondary in listOf(false,true)){
            val l=touchModalLayout(Box(90f,0f,2460f,1216f),3f,scale,4,1,secondary)
            assertTrue(l.wide)
            val targets=l.tabs+listOf(l.close,l.primary)+if(secondary)listOf(l.secondary) else emptyList()
            targets.forEach{assertTrue(it.w>=144);assertTrue(it.h>=144)}
            for(i in targets.indices)for(j in i+1 until targets.size)assertFalse(overlaps(targets[i],targets[j]))
            assertTrue(l.detail.y+l.detail.h<=if(secondary)l.secondary.y else l.primary.y)
            assertTrue(l.primary.y+l.primary.h<=l.frame.y+l.frame.h)
        }
    }
    @Test fun shortSafeViewportAndLargeFontKeepLabelsAndTargetsSeparate(){
        for(secondary in listOf(false,true)){
            val l=touchModalLayout(Box(0f,136f,1920f,808f),3f,2f,4,1,secondary)
            assertTrue(l.compactHeader);assertTrue(l.close.w>=72*3)
            val targets=l.tabs+listOf(l.close,l.primary)+if(secondary)listOf(l.secondary) else emptyList()
            for(i in targets.indices)for(j in i+1 until targets.size)assertFalse(overlaps(targets[i],targets[j]))
            assertTrue(l.detail.h>=35*3)
        }
    }
    @Test fun narrowWindowUsesListThenDetailAndScrolling(){
        val l=touchModalLayout(Box(0f,0f,320f,600f),1f,1.3f,4,1,false)
        assertFalse(l.wide);assertEquals(l.list.x,l.detail.x,0f)
        assertTrue(l.maxScroll(8)>0);assertEquals(0f,l.maxScroll(0),0f)
        assertEquals(l.list.y,l.row(0,0f).y,0f)
    }
}
