package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class BattleEnemyLabelTest {
    @Test fun duplicateContentNamesKeepSparseInstanceIdentity() {
        val name="原名未核（敵人35）"
        val labels=listOf(0,2,5).map{battleEnemyLabel(it,6,true,name,true)}
        assertEquals(3,labels.toSet().size)
        for((slot,label)in listOf(0,2,5).zip(labels)) {
            assertEquals("#${slot+1} "+name,label)
            assertTrue(label.endsWith(name))
        }
    }
    @Test fun compactDefeatedEnemiesKeepTheirExistingIdentityWithoutExtraDeathText() {
        for(slot in 0..5)assertEquals(battleEnemyLabel(slot,6,true,"原名未核（敵人35）",true),
            battleEnemyLabel(slot,6,true,"原名未核（敵人35）",false))
    }
    @Test fun singleEnemyDoesNotAcquireAnInstancePrefixEvenWithSparseSlot() {
        assertEquals("南海龍王",battleEnemyLabel(3,1,false,"南海龍王",true))
        assertEquals("南海龍王 · 倒下",battleEnemyLabel(3,1,false,"南海龍王",false))
    }
    @Test fun noncompactEnemiesPreservePreviousNameAndDeathPresentation() {
        for(name in listOf("南海龍王","原名未核（敵人35）"))for(slot in 0..2){
            assertEquals("#${slot+1} $name",battleEnemyLabel(slot,3,false,name,true))
            assertEquals("#${slot+1} $name · 倒下",battleEnemyLabel(slot,3,false,name,false))
        }
    }
}
