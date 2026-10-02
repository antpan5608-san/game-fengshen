package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class CharacterGrowthTest {
    private val nezha=listOf(GrowthRow(12,2100,7,0,1,1,1,0,false),GrowthRow(13,2850,7,0,1,1,1,0,false))
    private val girl=listOf(GrowthRow(12,1972,6,4,1,1,2,2,false),GrowthRow(13,2525,6,4,1,1,2,2,false))
    private fun content()=BattleContent(16,emptyList(),emptyList(),emptyMap(),nezha,0,1,2,10).also{
        it.characterGrowth=mapOf("xiaolongnv" to girl);it.characterLevelLimits=mapOf("xiaolongnv" to 80)}
    @Test fun distinctOwnersUseDistinctCumulativeIntervals(){
        val c=content();val hero=CharacterState("xiaolongnv",12,2000,92,92,44,22,14,26,43)
        val progress=experienceProgress(hero,c.growthFor(hero.id),hero.id,c.maxLevelFor(hero.id))
        assertEquals(ExperienceProgress.Status.PROGRESS,progress.status)
        assertEquals(28,progress.earned);assertEquals(553,progress.span);assertEquals(525,progress.remaining)
        assertEquals(nezha,c.growthFor("nezha"));assertEquals(girl,c.growthFor("xiaolongnv"))
        assertEquals(emptyList<GrowthRow>(),c.growthFor("unknown"));assertNull(c.maxLevelFor("unknown"))
    }
    @Test fun unknownOwnerCannotBorrowNezhaOrReportMax(){
        val c=content();val other=CharacterState("unknown",12,2000,92,92,44,22,14,26,43)
        assertEquals(ExperienceProgress.Status.MISSING,experienceProgress(other,c.growthFor(other.id),other.id,c.maxLevelFor(other.id)).status)
    }
}
