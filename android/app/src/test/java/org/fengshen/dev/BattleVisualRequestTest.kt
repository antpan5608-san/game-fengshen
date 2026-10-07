package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class BattleVisualRequestTest {
    private val selection=BattleVisualSelection(mapOf(
        "nezha" to listOf("n-portrait","n-idle","n-attack"),
        "xiaolongnv" to listOf("x-portrait","x-idle","x-cast"),
        "yangjian" to listOf("y-portrait","y-idle")),
        mapOf(1 to "enemy1",137 to "enemy137"),
        mapOf("grass" to "grass","cave" to "cave"),mapOf(16 to "grass"))

    @Test fun requestsSnapshotIdentitiesAndIgnoreUnknownsWithoutInventingArt(){
        val actors=mutableListOf("nezha","unknown","nezha")
        val enemies=mutableListOf(1,999,1)
        val request=BattleVisualRequest(actors,enemies,16,false)
        actors.clear();actors.add("yangjian");enemies.clear();enemies.add(137)
        assertEquals(setOf("n-portrait","n-idle","n-attack","enemy1","grass"),selection.battle(request))
        assertEquals(setOf("nezha","unknown"),request.actorIds)
        assertEquals(setOf(1,999),request.enemyIds)
    }

    @Test fun blackSceneUsesOnlyReviewedCaveAndUnmappedScenesKeepNativeBackground(){
        assertEquals(setOf("cave"),selection.battle(BattleVisualRequest(emptyList(),emptyList(),16,true)))
        assertEquals(emptySet<String>(),selection.battle(BattleVisualRequest(listOf("missing"),listOf(2),999,false)))
        assertEquals(setOf("enemy137","cave"),selection.battle(BattleVisualRequest(emptyList(),listOf(137),999,true)))
    }

    @Test fun consecutiveBattlesSelectOnlyTheirOwnActorsEnemiesAndAvailablePoses(){
        val first=BattleVisualRequest(listOf("nezha"),listOf(1),16,false)
        val second=BattleVisualRequest(listOf("xiaolongnv","yangjian"),listOf(137),999,true)
        val saved=selection.battle(first)
        assertEquals(setOf("x-portrait","x-idle","x-cast","y-portrait","y-idle","enemy137","cave"),selection.battle(second))
        assertEquals(saved,selection.battle(first))
        assertTrue(selection.battle(second).none{it.startsWith("n-")||it=="enemy1"||it=="grass"})
    }
}
