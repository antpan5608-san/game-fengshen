package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class OriginalFerryTest {
    private fun rule(event:Int)=FerryDefinition("rom.ferry.$event",event,
        if(event==45)FerryLeg(4,11,3,Key.LEFT)else FerryLeg(16,150,135,Key.DOWN),
        if(event==45)10 else 150,if(event==45)3 else 136,OriginalFerry.originalLegs(event),
        "actor-218-ferry.png","game-data/provenance/world-ferry-original.json")
    private val rules=listOf(rule(45),rule(46))
    private fun before(event:Int,hp:Int=100)=SaveSnapshot("fixture",if(event==45)4 else 16,
        (if(event==45)11 else 150)*16+8,(if(event==45)3 else 135)*16+8,Key.DOWN,
        listOf(CharacterState("nezha",13,3333,hp,100,10,50,10,15,5,statusMask=2),
            CharacterState("xiaolongnv",12,2222,0,60,40,30,14,26,43,statusMask=32,maxMp=400),
            CharacterState("yangjian",24,26000,1,495,54,96,60,28,69,statusMask=1,maxMp=54)),
        mapOf(HerbUse.ID to 4,"rom.special.19" to 1),
        mapOf("unrelated" to true,OriginalFerry.PARKED_FLAG to(event==46)),887,if(event==45)60 else 0)
    @Test fun bothRoutesMatchOriginalPoisonStepsAndFinalLandingWithoutRewards() {
        val rows=javaClass.getResourceAsStream("/world-ferry-original-steps.tsv")!!.bufferedReader().readLines().drop(1)
            .map{it.split('\t')}
        var comparisons=0
        for(event in listOf(45,46)){
            val rule=rules.single{it.eventId==event};val source=before(event)
            var s=OriginalFerry.begin(source,rule,rule.start.direction,rules).snapshot
            assertTrue(OriginalFerry.validPending(s,rules))
            val label=if(event==45)"forward"else"reverse"
            val expected=rows.filter{it[0]=="$label-hp-change"&&it[8].toInt()in 82..99}
            val stages=mutableMapOf<Int,SaveSnapshot>()
            for(i in rule.legs.indices){
                val old=s;val result=OriginalFerry.advance(old,rule,i,rules)
                assertTrue(result.applied);s=result.snapshot;stages[s.characters[0].hp]=s
                assertFalse(OriginalFerry.advance(s,rule,i,rules).applied)
                assertEquals(source.inventory,s.inventory);assertEquals(source.money,s.money)
                assertEquals(source.characters.drop(1),s.characters.drop(1));assertEquals(source.characters[0].experience,s.characters[0].experience)
                assertEquals(source.characters[0].equipment,s.characters[0].equipment);assertEquals(true,s.flags["unrelated"])
                if(i<17)assertTrue(OriginalFerry.validPending(s,rules))
            }
            for(row in expected){
                // The first forward cost precedes original map reconstruction.
                // A living durable boundary is the settled 146,148; the failed
                // first-step position is independently tested below.
                if(event==45&&row[8].toInt()==99)continue
                val at=stages.getValue(row[8].toInt())
                assertEquals(row.toString(),row[2].toInt(),at.mapId)
                assertEquals(row[3].toDouble().toInt(),at.x/16);assertEquals(row[4].toDouble().toInt(),at.y/16)
                if(row[8].toInt()>82)assertEquals(row[7].toInt(),at.encounterSteps)
                assertEquals(row[9].toInt(),at.characters[0].statusMask);comparisons++
            }
            val last=rows.single{it[0]=="$label-after"}
            assertEquals(last[2].toInt(),s.mapId);assertEquals(last[3].toDouble().toInt(),s.x/16)
            assertEquals(last[4].toDouble().toInt(),s.y/16);assertEquals(last[7].toInt(),s.encounterSteps)
            assertEquals(82,s.characters[0].hp);assertEquals(OriginalStatus.POISON,s.characters[0].statusMask)
            assertNull(OriginalFerry.pending(s.flags,rules));assertEquals(event==45,s.flags[OriginalFerry.PARKED_FLAG])
            assertFalse(s.flags.keys.any{it.startsWith("rom.event.$event.ferry.")})
        };assertEquals(35,comparisons)
    }
    @Test fun everyDurableStageCanResumeFromIndependentStateWithoutPayingTwiceAndStaleCommandRejects() {
        val r=rule(45);var s=OriginalFerry.begin(before(45),r,Key.LEFT,rules).snapshot
        repeat(18){i->
            // Android JSON roundtrip/cold launch belongs to the instrument gate.
            val restarted=s.copy(characters=s.characters.toList(),inventory=s.inventory.toMap(),flags=s.flags.toMap())
            val resumed=OriginalFerry.advance(restarted,r,i,rules);assertTrue(resumed.applied)
            assertEquals(OriginalFerry.advance(s,r,i,rules).snapshot,resumed.snapshot)
            assertFalse(OriginalFerry.advance(resumed.snapshot,r,i,rules).applied)
            assertFalse(OriginalFerry.begin(restarted,r,Key.LEFT,rules).applied)
            s=resumed.snapshot
        }
    }
    @Test fun originalAllPoisonLowStopsAtFirstFailedCostBeforeReconstruction() {
        val start=before(45,1).copy(characters=before(45,1).characters.map{if(it.id=="yangjian")it.copy(statusMask=2)else it})
        val r=rule(45);val active=OriginalFerry.begin(start,r,Key.LEFT,rules).snapshot
        val first=OriginalFerry.advance(active,r,0,rules).snapshot
        val row=javaClass.getResourceAsStream("/world-ferry-original-steps.tsv")!!.bufferedReader().readLines()
            .single{it.startsWith("all-poison-low-after\t")}.split('\t')
        assertEquals(row[2].toInt(),first.mapId);assertEquals(row[3].toDouble().toInt(),first.x/16)
        assertEquals(row[4].toDouble().toInt(),first.y/16);assertEquals(row[7].toInt(),first.encounterSteps)
        assertEquals(row[8].toInt(),first.characters[0].hp);assertEquals(row[9].toInt(),first.characters[0].statusMask)
        assertEquals(row[10].toInt(),first.characters[2].hp);assertEquals(row[11].toInt(),first.characters[2].statusMask)
        assertTrue(OriginalFerry.validPending(first,rules));assertTrue(OriginalStatus.allDisabled(first.characters))
        assertFalse(OriginalFerry.advance(first,r,1,rules).applied)
        assertEquals(start.money,first.money);assertEquals(start.inventory,first.inventory)
    }
    @Test fun exactScriptPointDoesNotEnableAnyOtherSeaOrOtherBoatAndInvalidStagesReject() {
        val w=256;val collision=IntArray(w*192){3};collision[135*w+150]=25;collision[134*w+150]=0
        val base=Scene("fixture",w,192,IntArray(collision.size),collision,setOf(134*w+150),150,134,16,setOf(0))
        val r=rule(45);val stage=OriginalFerry.advance(OriginalFerry.begin(before(45),r,Key.LEFT,rules).snapshot,r,0,rules).snapshot
        val view=OriginalFerry.sceneView(base,stage.flags,rules)
        assertNull(view.check(146,148));assertNull(view.check(150,135));assertNotNull(view.check(146,147))
        assertEquals(setOf(148*w+146,135*w+150),view.enabled-base.enabled)
        val idle=OriginalFerry.sceneView(base,emptyMap(),rules);assertNotNull(idle.check(146,148));assertNull(idle.check(150,135))
        assertFalse(OriginalFerry.validPending(stage.copy(x=stage.x+16),rules))
        assertFalse(OriginalFerry.validPending(stage.copy(flags=stage.flags+(r.stageFlag(8) to true)),rules))
        assertFalse(OriginalFerry.validPending(stage.copy(flags=stage.flags+("rom.event.45.ferry.step.99" to true)),rules))
        assertFalse(OriginalFerry.begin(before(46).copy(flags=emptyMap()),rule(46),Key.DOWN,rules).applied)
        assertFalse(OriginalFerry.begin(before(45),r,Key.RIGHT,rules).applied)
        assertFalse(OriginalFerry.begin(before(45),r.copy(legs=r.legs.map{it.copy(x=it.x+1)}),Key.LEFT,rules).applied)
        val bad=stage.copy(flags=stage.flags+(rule(46).pendingFlag to true));assertFalse(OriginalFerry.validPending(bad,rules))
    }
}
