package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class OriginalBattleMagicTest {
    private val x=CharacterState("xiaolongnv",12,2000,92,92,44,22,14,26,43,maxMp=44)
    private val n=CharacterState("nezha",1,0,5,200,0,8,4,2,4,maxMp=0)
    private fun rows(name:String)=javaClass.getResourceAsStream("/original-battle-magic/$name.tsv")!!
        .bufferedReader().use{it.readLines().drop(1).map{line->line.split('\t')}}
    private val heal=OriginalBattleMagic.spells[0]
    private val cure=OriginalBattleMagic.spells[1]

    @Test fun originalTargetPredicateDiffersFromField(){
        for(row in rows("battle-row0-target-status")){
            val target=n.copy(statusMask=row[0].toInt())
            assertNull(OriginalBattleMagic.selectionReason(x,1,heal.id,target))
            val effect=OriginalBattleMagic.effect(x,target,heal)
            assertEquals(row[3].toInt(),effect.target.hp)
            assertEquals(row[4].toInt(),x.mp) // Native caster MP before separate debit.
            assertEquals(target.mp,effect.target.mp)
            assertEquals(row[5].toInt(),effect.target.statusMask)
        }
    }

    @Test fun originalFixedIdentityHealingIncludingReorderedPartyAndSelf(){
        for(row in rows("battle-row0-heal")){
            val ids=row[0].split(',').map{it.toInt()};val targetId=ids[row[2].toInt()]
            val caster=x.copy(level=row[3].toInt()+1)
            val target=(if(targetId==2)caster else n).copy(hp=row[4].toInt(),maxHp=row[5].toInt())
            val effect=OriginalBattleMagic.effect(caster,target,heal)
            assertEquals(row[6].toInt(),effect.target.hp)
            if(targetId==2){
                val afterDebit=OriginalBattleMagic.debit(effect.target,heal)
                assertEquals(effect.target.hp,afterDebit.hp);assertEquals(41,afterDebit.mp)
            }
        }
    }

    @Test fun originalInitialMpAndLateDebitAreDifferentPhases(){
        for(row in rows("battle-initial-mp-prefix")){
            val mp=row[0].toInt()
            if(mp>9999)continue // Original controlled16bit input is outside the player-save domain.
            assertEquals(row[2]=="1",OriginalBattleMagic.selectionReason(x.copy(mp=mp,maxMp=null),1,heal.id,n)==null)
        }
        for(row in rows("battle-row0-mp-debit")){
            val mp=row[0].toInt()
            if(mp>9999)continue
            val caster=x.copy(mp=mp,maxMp=null)
            assertEquals(row[1].toInt(),OriginalBattleMagic.debit(caster,heal).mp)
            assertEquals(mp,caster.mp)
        }
    }

    @Test fun originalCurePredicateIsEqualityNotPoisonBit(){
        for(row in rows("battle-antidote-target-prefix")){
            val target=n.copy(statusMask=row[0].toInt())
            val effect=OriginalBattleMagic.effect(x,target,cure)
            assertEquals(row[2]=="1",effect.applied)
            assertEquals(if(row[2]=="1")0 else target.statusMask,effect.target.statusMask)
            assertEquals(41,OriginalBattleMagic.debit(x,cure).mp) // Healthy/refused still pays.
        }
    }

    @Test fun originalPostHpStateBoundaryDoesNotApplyToField(){
        for(row in rows("battle-post-hp-status")){
            val before=n.copy(hp=row[1].toInt(),maxHp=row[2].toInt(),statusMask=row[3].toInt())
            val after=OriginalBattleMagic.afterHp(before)
            assertEquals(row[4].toInt(),after.statusMask)
            assertEquals(before.hp,after.hp);assertEquals(before.mp,after.mp)
        }
    }

    @Test fun actualOriginalLearningAndInvalidIdentityRemainBounded(){
        for(row in rows("battle-availability-controlled-original")){
            if(row[0]!="2")continue // Other roles' effects remain outside this adapter.
            val expected=listOf(heal.id,cure.id).take(minOf(2,row[2].toInt()))
            assertEquals(expected,OriginalBattleMagic.learned(x.copy(level=row[1].toInt()),1).map{it.id})
        }
        assertEquals(listOf(heal.id),OriginalBattleMagic.learned(x.copy(level=9),1).map{it.id})
        assertEquals(listOf(heal.id,cure.id),OriginalBattleMagic.learned(x.copy(level=10),1).map{it.id})
        assertTrue(OriginalBattleMagic.learned(n,0).isEmpty())
        assertTrue(OriginalBattleMagic.learned(x,2).isEmpty())
        assertTrue(OriginalBattleMagic.learned(x.copy(level=81),1).isEmpty())
        assertNotNull(OriginalBattleMagic.selectionReason(x,1,"unverified",n))
        assertNotNull(OriginalBattleMagic.selectionReason(x.copy(mp=2),1,heal.id,n))
        assertNotNull(OriginalBattleMagic.selectionReason(x,1,heal.id,null))
        assertNotNull(OriginalBattleMagic.selectionReason(x,1,heal.id,n.copy(hp=201)))
        assertEquals(32,OriginalBattleMagic.afterHp(n.copy(hp=0,statusMask=2)).statusMask)
    }
}
