package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class ResourceMapTest {
    @Test fun indexLookupDoesNotLoadResources(){
        var calls=0;val map=ResourceMap((0..174).toList(),8){calls++;"scene$it"}
        assertEquals(175,map.size);assertTrue(map.containsKey(174));assertFalse(map.containsKey(175))
        assertEquals(175,map.keys.size);assertEquals(0,calls);assertTrue(map.cachedKeys().isEmpty())
    }
    @Test fun boundedLruPreservesRecentlyUsedResourceAndDistinctIds(){
        val calls=mutableListOf<Int>();val map=ResourceMap((0..174).toList(),2){calls.add(it);"same-pixels"}
        map.getValue(69);map.getValue(158);map.getValue(69);map.getValue(159)
        assertEquals(setOf(69,159),map.cachedKeys());assertEquals(listOf(69,158,159),calls)
        assertEquals(175,map.size);map.getValue(158);assertEquals(listOf(69,158,159,158),calls)
    }
    @Test fun failureIsRetryableAndDoesNotPoisonCache(){
        var failed=true;val map=ResourceMap(listOf(1,2),2){if(it==2&&failed)error("Bad content hash");it*10}
        assertEquals(10,map[1]);try{map[2];fail()}catch(_:IllegalStateException){}
        assertEquals(setOf(1),map.cachedKeys());failed=false;assertEquals(20,map[2])
    }
    @Test fun duplicateIdentityAndInvalidBudgetRejected(){
        try{ResourceMap(listOf(1,1),2){it};fail()}catch(_:IllegalArgumentException){}
        try{ResourceMap(listOf(1),0){it};fail()}catch(_:IllegalArgumentException){}
    }
    @Test fun byteBudgetEvictsLeastRecentWhilePreservingIdentityIndex(){
        val sizes=mapOf(1 to 39,2 to 25,3 to 10)
        val map=ResourceMap(listOf(1,2,3),9){ByteArray(sizes.getValue(it))}
            .limitBytes(64){it.size.toLong()}
        map.getValue(1);map.getValue(2);assertEquals(64L,map.cachedBytes())
        map.getValue(1);map.getValue(3)
        assertEquals(setOf(1,3),map.cachedKeys());assertEquals(49L,map.cachedBytes())
        assertEquals(setOf(1,2,3),map.keys)
    }
    @Test fun oversizedOrInvalidWeightNeverPoisonsExistingCachedImages(){
        var size=65
        val map=ResourceMap(listOf(1,2),9){ByteArray(if(it==1)20 else size)}
            .limitBytes(64){it.size.toLong()}
        map.getValue(1)
        try{map.getValue(2);fail()}catch(_:IllegalArgumentException){}
        assertEquals(setOf(1),map.cachedKeys());assertEquals(20L,map.cachedBytes())
        size=44;map.getValue(2);assertEquals(64L,map.cachedBytes())
        try{map.limitBytes(32){it.size.toLong()};fail()}catch(_:IllegalArgumentException){}
        val invalid=ResourceMap(listOf(1),2){"image"}.limitBytes(64){-1L}
        try{invalid.getValue(1);fail()}catch(_:IllegalArgumentException){}
        assertTrue(invalid.cachedKeys().isEmpty());assertEquals(0L,invalid.cachedBytes())
        try{ResourceMap(listOf(1),2){it}.limitBytes(0){1L};fail()}catch(_:IllegalArgumentException){}
    }
}
