package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Expected results executed by the unchanged original CPU, not normal App evidence. */
class Field67StepTest {
    private val hero=CharacterState("nezha",1,0,11,65535,0,8,4,5,3)
    @Test fun allOriginalCompletedStepBoundariesMatchForOneTwoAndFourActors(){
        val rows=javaClass.getResourceAsStream("/hell-field67-step-cpu.tsv")!!
            .bufferedReader().use{it.readLines()}
        assertEquals(3840,rows.size)
        for(line in rows){
            val v=line.split('\t').map{it.toInt()}
            val party=List(v[5]){hero.copy(id="actor$it",hp=v[4],statusMask=v[3])}
            val result=OriginalStatus.step(party,v[0],v[0]==67&&(v[1]!=0||v[2]!=0))
            assertEquals(party.map{it.id},result.map{it.id})
            for(actor in result){assertEquals(line,v[6],actor.hp);assertEquals(line,v[7],actor.statusMask)}
            assertEquals(line,v[9]!=0,OriginalStatus.allDisabled(result))
        }
    }
    @Test fun mixedPartyPoisonFireProtectionDeathAndNoMutation(){
        val party=listOf(hero,hero.copy(id="poison",statusMask=2),
            hero.copy(id="protected",statusMask=66),hero.copy(id="dead",hp=0,statusMask=32))
        val result=OriginalStatus.step(party,67)
        assertEquals(listOf(1,0,10,0),result.map{it.hp})
        assertEquals(listOf(0,32,66,32),result.map{it.statusMask})
        assertEquals(listOf(11,11,11,0),party.map{it.hp})
        assertEquals(listOf(11,10,10,0),OriginalStatus.step(party,67,true).map{it.hp})
        assertEquals(OriginalStatus.step(party),OriginalStatus.step(party,66))
    }
    @Test fun completedSourceMapControlsEntryAndDepartureWithoutInventedTerrainDamage(){
        val into=CompletedStep(23,0,0,true)
        val out=CompletedStep(67,0,0,true)
        assertEquals(hero,OriginalStatus.step(listOf(hero),into.mapId).single())
        assertEquals(1,OriginalStatus.step(listOf(hero),out.mapId).single().hp)
        assertEquals(1,OriginalStatus.step(listOf(hero),67).single().hp)
        // Boundary: poison kills before the map-specific pass, with no resurrection.
        assertEquals(32,OriginalStatus.step(listOf(hero.copy(hp=1,statusMask=2)),67).single().statusMask)
        assertEquals(0,OriginalStatus.step(listOf(hero.copy(hp=10)),67).single().hp)
    }
}
