package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Source-scoped CPU expectations; not a normal Android route claim. */
class JiamengActivationTest {
    private val current="rom.npccontext.145.215"
    private val legacy=mapOf("rom.npccontext.121.215" to true,
        OriginalNpcTalk.HUANG_COMPLETED_FLAG to true,"rom.global.7fe.128" to true)
    private fun first()=StoryBattleDefinition("rom.boss.158","rom.npc.145.0","rom.map.145.flag.2",
        EncounterGroup(0,listOf(EncounterMember(3,158))),"original.first.followup")
        .also{it.activationFlagId=current}
    @Test fun guardingContextCannotStartBossBeforeActualHuangCompletion(){
        val boss=first();assertFalse(boss.activeIn(emptyMap()))
        assertTrue(boss.activeIn(mapOf(current to true)))
        assertFalse(boss.activeIn(mapOf(current to false)))
        assertFalse(boss.activeIn(mapOf("rom.npccontext.121.215" to true)))
        assertEquals(EncounterGroup(0,listOf(EncounterMember(3,158))),boss.group)
    }
    @Test fun legacyReadOnlyCompatibilityRequiresCompleteEvidenceAndExplicitFalseWins(){
        val boss=first();val before=legacy.toMap()
        assertTrue(boss.activeIn(legacy));assertEquals(before,legacy)
        for(key in legacy.keys)assertFalse(boss.activeIn(legacy-key))
        assertFalse(boss.activeIn(legacy+(current to false)))
        assertTrue(boss.activeIn(mapOf(current to true)))
    }
    @Test fun npcVariantUsesExactlyTheSameContextWithoutPersistingAnything(){
        val variant=NpcStateVariant(current,4,10,"rom.dialogue.155.1","rom.dialogue.155.1")
        assertEquals(first().activeIn(legacy),variant.activeIn(legacy))
        assertFalse(variant.activeIn(legacy+(current to false)))
        val existing=NpcStateVariant("existing.flag",7,9,"old.first","old.repeat")
        assertFalse(existing.activeIn(legacy));assertTrue(existing.activeIn(mapOf("existing.flag" to true)))
    }
    @Test fun exactOriginalFiveTriggersRejectOtherCellsMapsAndRepeatVictory(){
        val points=setOf(6 to 5,10 to 5,7 to 6,8 to 6,9 to 6)
        val boss=StoryBattleDefinition("rom.boss.159-161","rom.npc.148.0","rom.map.148.flag.128",
            EncounterGroup(0,listOf(EncounterMember(0,159),EncounterMember(3,160),EncounterMember(6,161))),"original.script30")
            .also{it.entryTrigger=StoryEntryTrigger(148,8,6)
                it.additionalEntryTriggers=points.map{p->StoryEntryTrigger(148,p.first,p.second)}.toSet()}
        val rows=javaClass.getResourceAsStream("/jiameng-three-trigger-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(960,rows.size)
        for(line in rows){val r=line.split('\t').map(String::toInt)
            val flags=if(r[2] and 128!=0)mapOf(boss.flagId to true) else emptyMap()
            assertEquals(line,r[3]!=0,boss.triggersAt(148,r[0],r[1],flags))
            assertFalse(boss.triggersAt(147,r[0],r[1],flags))}
        for(p in points){assertTrue(boss.triggersAt(148,p.first,p.second,emptyMap()))
            assertFalse(boss.triggersAt(148,p.first,p.second,mapOf(boss.pendingFlag to true)))}
    }
    @Test fun existingStoryDefinitionsKeepTheirUnconditionalPolicy(){
        val boss=StoryBattleDefinition("existing","existing.npc","existing.complete",
            EncounterGroup(0,listOf(EncounterMember(3,157))),"existing.dialogue")
            .also{it.entryTrigger=StoryEntryTrigger(117,7,5)}
        assertTrue(boss.activeIn(emptyMap()));assertTrue(boss.triggersAt(117,7,5,emptyMap()))
        assertFalse(boss.triggersAt(117,7,5,mapOf(boss.flagId to true)))
        assertFalse(boss.triggersAt(117,8,5,emptyMap()))
    }
    @Test fun originalReservedYangSlotDoesNotInventActorOrRejectAbsentRoster(){
        val boss=StoryBattleDefinition("rom.boss.159","rom.npc.148.0","rom.map.148.flag.128",
            EncounterGroup(0,listOf(EncounterMember(0,159),EncounterMember(3,160),EncounterMember(6,161))),"original.script30")
            .also{it.victoryCharacterChanges=listOf(StoryCharacterChange("yangjian",statusOrMask=64))}
        val nezha=CharacterState("nezha",20,12000,100,500,7,80,40,60,10)
        val yang=nezha.copy(id="yangjian",statusMask=2)
        assertEquals(listOf(nezha),boss.charactersOnVictory(listOf(nezha)))
        assertEquals(listOf(nezha,yang.copy(statusMask=66)),boss.charactersOnVictory(listOf(nezha,yang)))
        val after=boss.charactersOnVictory(listOf(nezha,yang))!!
        assertEquals(after,boss.charactersOnVictory(after)) // Status OR is idempotent, rewards are separate.
        assertEquals(2,yang.statusMask);assertEquals(12000,after[1].experience)
        val other=first().also{it.victoryCharacterChanges=boss.victoryCharacterChanges}
        assertNull(other.charactersOnVictory(listOf(nezha))) // No generic skip-missing weakening.
    }
    @Test fun fiveOriginalScript31EntrancesKeepTheirOwnCompletedStepPosition(){
        val flag="runtime.story.148.event1.intro.complete"
        val ids=(3..5).map{"rom.dialogue.148.$it"}
        val points=setOf(6 to 5,10 to 5,7 to 6,8 to 6,9 to 6)
        val story=SceneStoryDefinition("rom.scene-story.148.three-generals-intro","rom.npc.148.0",flag,
            StoryEntryTrigger(148,8,6),StoryContinuation(ids,null,null,setOf(flag)),
            StoryMovement(StoryDestination(148,8,6,null,null,null),0),emptyMap()).also{
                it.additionalEntryTriggers=points.map{p->StoryEntryTrigger(148,p.first,p.second)}.toSet()
                it.preserveOpeningPosition=true}
        val actor=CharacterState("nezha",20,12000,100,500,7,80,40,60,10,statusMask=2)
        for((x,y)in points){
            val before=SaveSnapshot("fixture",148,x*16+8,y*16+8,Key.UP,listOf(actor),mapOf(HerbUse.ID to 3),emptyMap(),1234)
            assertTrue(story.automaticallyTriggersAt(before))
            val begin=StoryFollowup.begin(before,story);assertTrue(begin.applied)
            assertEquals(before.copy(flags=mapOf(story.pendingFlag to true)),begin.snapshot)
            var next=begin.snapshot
            for(id in ids){val result=StoryFollowup.advance(next,story,id);assertTrue(result.applied);next=result.snapshot}
            assertEquals(before.x,next.x);assertEquals(before.y,next.y);assertEquals(before.characters,next.characters)
            assertEquals(before.money,next.money);assertEquals(before.inventory,next.inventory)
            assertTrue(next.flags[flag]==true);assertFalse(story.automaticallyTriggersAt(next))
        }
        assertFalse(story.automaticallyTriggersAt(SaveSnapshot("fixture",148,8*16+8,7*16+8,
            Key.UP,listOf(actor),emptyMap(),emptyMap(),1234)))
    }
}
