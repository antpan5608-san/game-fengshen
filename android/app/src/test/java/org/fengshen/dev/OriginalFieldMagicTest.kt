package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Expectations are actual target-ROM CPU output; these tests never claim App acceptance. */
class OriginalFieldMagicTest {
    private fun actor(index:Int,level:Int=12,hp:Int=5,maximum:Int=200,mp:Int=44,status:Int=0)=
        CharacterState("actor$index",level,2000,hp,maximum,mp,22,14,26,43,mp,statusMask=status)
    private val indices=(0..3).associate{"actor$it" to it}
    private fun rows(name:String)=javaClass.getResourceAsStream("/$name.tsv")!!
        .bufferedReader().use{it.readLines().drop(1)}

    @Test fun matchesAllNativeObservedCasterAndHealingTargetPredicateCpuCases(){
        for((name,count)in listOf("field-caster-status-controlled-original" to 256,
            "field-heal-target-status-controlled-original" to 512)){
            val source=rows(name);assertEquals(count,source.size)
            for(row in source){val c=row.split('\t').map{it.toInt()}
                assertEquals(row,c[4]==0,OriginalFieldMagic.statusAllows(c[1]))
                assertEquals(row,c[5],c[6])}
        }
    }

    @Test fun matchesOriginalHealingFormulaIncludingOriginalPartySearch(){
        val source=rows("heal-controlled-original");assertEquals(240,source.size)
        for(row in source){val c=row.split('\t')
            val data=c.take(10).map{it.toInt()}
            val party=listOf(data[1],data[2]).map{index->actor(index,1+if(index==0)data[3] else data[4],
                if(index==data[5])data[6] else 5,if(index==data[5])data[7] else 200)}
            val target=party.indexOfFirst{it.id=="actor${data[5]}"}
            assertEquals(row,data[8],OriginalFieldMagic.healedHp(party,indices,target,data[0]))
        }
    }

    @Test fun nativeMpPrefixBoundariesAreEnforcedBeforeEffectCommit(){
        val source=rows("field-mp-validation-controlled-original");assertEquals(2052,source.size)
        for(row in source){val c=row.split('\t').map{it.toInt()};if(c[0]!=3)continue
            val party=listOf(actor(0),actor(1,mp=c[1]))
            val result=OriginalFieldMagic.apply(party,indices,"actor1","actor0",OriginalFieldMagic.SPELL_ID,true)
            assertEquals(row,c[2]==1,result.applied)
            assertEquals(row,c[1]-if(result.applied)3 else 0,result.characters[1].mp)
            if(!result.applied)assertEquals(row,party,result.characters)
            assertEquals(row,c[1],c[3])
        }
    }

    @Test fun nativeInitialSelfAndFullHealthHealingPreservesUnrelatedFields(){
        for(target in listOf("actor0","actor1")){
            val party=listOf(actor(0),actor(1,hp=92,maximum=92))
            val result=OriginalFieldMagic.apply(party,indices,"actor1",target,OriginalFieldMagic.SPELL_ID,true)
            assertTrue(result.applied)
            val expected=party.map{it.copy(hp=if(it.id==target)minOf(it.maxHp,it.hp+53) else it.hp,
                mp=if(it.id=="actor1")41 else it.mp)}
            assertEquals(expected,result.characters);assertEquals(44,party[1].mp)
        }
    }

    @Test fun illegalSceneIdentityLearningAndStatusNeverMutateTheProposalInput(){
        val party=listOf(actor(0),actor(1))
        for((caster,target,spell,mapMenu)in listOf(
            Case("actor0","actor1",OriginalFieldMagic.SPELL_ID,true),
            Case("missing","actor0",OriginalFieldMagic.SPELL_ID,true),
            Case("actor1","missing",OriginalFieldMagic.SPELL_ID,true),
            Case("actor1","actor0","rom.magic.field.1.1",true),
            Case("actor1","actor0",OriginalFieldMagic.SPELL_ID,false))){
            val result=OriginalFieldMagic.apply(party,indices,caster,target,spell,mapMenu)
            assertFalse(result.applied);assertEquals(party,result.characters)
        }
        for(slot in 0..1)for(status in listOf(16,32,64,128,255)){
            val changed=party.mapIndexed{i,h->if(i==slot)h.copy(statusMask=status) else h}
            val result=OriginalFieldMagic.apply(changed,indices,"actor1","actor0",OriginalFieldMagic.SPELL_ID,true)
            assertFalse(result.applied);assertEquals(changed,result.characters)
        }
        assertFalse(OriginalFieldMagic.learned(actor(1,level=81),1))
        assertFalse(OriginalFieldMagic.learned(actor(0),0))
    }
    private data class Case(val caster:String,val target:String,val spell:String,val mapMenu:Boolean)
}
