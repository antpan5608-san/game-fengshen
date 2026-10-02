package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Controlled event-state tests; no claim of normal original or Android playthrough. */
class StoryEntryTest {
    private fun north()=StoryBattleDefinition("rom.boss.139","rom.npc.139.1","rom.map.139.flag.128",
        EncounterGroup(154,listOf(EncounterMember(3,139))),"rom.dialogue.north.victory").apply {
            entryTrigger=StoryEntryTrigger(139,2,4);commitAfterDialogue=true
        }
    @Test fun automaticEntryRequiresTheExactMapCellAndNoVictoryOrPendingReward(){
        val s=north()
        assertTrue(s.triggersAt(139,2,4,emptyMap()))
        for((m,x,y) in listOf(Triple(98,2,4),Triple(139,2,3),Triple(139,3,4)))
            assertFalse(s.triggersAt(m,x,y,emptyMap()))
        for(f in listOf(s.flagId,s.pendingFlag))assertFalse(s.triggersAt(139,2,4,mapOf(f to true)))
    }
    @Test fun victoryPreservesOriginalFlagUntilDialogueAndPreventsBattleReplay(){
        val s=north();val before=mapOf("previous" to true,s.flagId to false)
        val reward=s.rewardFlags(before)
        assertEquals(false,reward[s.flagId]);assertEquals(true,reward[s.pendingFlag])
        assertTrue(s.alreadyWon(reward));assertFalse(s.triggersAt(139,2,4,reward))
        assertEquals(mapOf("previous" to true,s.flagId to false),before)
        val complete=s.completeDialogue(reward)
        assertEquals(mapOf("previous" to true,s.flagId to true),complete)
        assertEquals(complete,s.completeDialogue(complete))
    }
    @Test fun talkingBeforeVictoryCannotSetTheCompletionFlagOrGrantTheTreasure(){
        val s=north();val original=mapOf("rom.map.139.flag.2" to false)
        assertEquals(original,s.completeDialogue(original))
        val reward=s.rewardFlags(original)
        assertEquals(false,reward["rom.map.139.flag.2"])
        assertEquals(false,s.completeDialogue(reward)["rom.map.139.flag.2"])
    }
    @Test fun legacyNpcBattlesKeepTheirExistingAtomicFlagAndPendingDialogue(){
        val s=StoryBattleDefinition("rom.boss.137","rom.npc.97.0","rom.event.97.40.1",
            EncounterGroup(153,listOf(EncounterMember(3,137))),"victory")
        assertNull(s.entryTrigger);assertFalse(s.commitAfterDialogue)
        assertFalse(s.triggersAt(97,15,9,emptyMap()))
        val won=s.rewardFlags(mapOf("old" to true))
        assertEquals(mapOf("old" to true,s.flagId to true,s.pendingFlag to true),won)
        assertEquals(mapOf("old" to true,s.flagId to true),s.completeDialogue(won))
    }
}
