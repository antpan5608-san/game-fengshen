package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Compare Kotlin to actual original CPU outputs; no Android or native normal claim. */
class JiangFourPartyTest {
    private fun rows(name:String)=javaClass.getResourceAsStream("/$name")!!.bufferedReader()
        .use{it.readLines()}.drop(1).map{it.split('\t')}
    private fun vector(s:String)=s.split(',').map{it.toInt()}
    @Test fun fourthTargetAndOwnMultiplierMatchAllOriginalCpuCases(){
        val table=javaClass.getResourceAsStream("/world-jiang-multiplier-original.tsv")!!.bufferedReader()
            .use{it.readText().trim()}.split('\t').map{it.toInt()}
        val cases=rows("world-jiang-four-party-original.tsv");assertEquals(7680,cases.size)
        for(row in cases){
            val a=row[1].toInt();val byte=row[2].toInt();val expected=row[4].toInt()
            if(row[0]=="four-target"){
                val actors=(0..3).map{OriginalPartyRules.Actor(it,it,if(a and (1 shl it)!=0)1 else 0,0,0)}
                assertEquals(row.toString(),expected.takeIf{it>=0},OriginalPartyRules.enemyTarget(actors,byte))
            }else{
                assertEquals("four-multiplier",row[0])
                assertEquals(row.toString(),expected,OriginalPartyRules.physicalMultiplier(3,a,byte,mapOf(3 to table)))
            }
        }
    }
    @Test fun fourthSchedulerLivingExperienceAndSequentialRecoveryMatchOriginalCpu(){
        val cases=rows("world-jiang-four-party-shared-original.tsv");assertEquals(1223,cases.size)
        for(row in cases)when(row[0]){
            "scheduler"->{
                val agility=vector(row[1]);val mask=row[2].toInt()
                val actors=(0..3).map{OriginalPartyRules.Actor(it,it,1,0,agility[it],if(mask and (1 shl it)!=0)3 else 1)}
                val enemies=vector(row[3]).mapIndexed{i,v->OriginalPartyRules.Enemy(i,1,v)}
                assertEquals(vector(row[4]),OriginalPartyRules.actionOrder(actors,enemies))
            }
            "experience"->{
                val mask=row[1].toInt();val actors=(0..3).map{OriginalPartyRules.Actor(it,it,if(mask and (1 shl it)!=0)1 else 0,0,0)}
                val shares=OriginalPartyRules.experienceShares(listOf(row[2].toInt()),actors)
                assertEquals(vector(row[4]),(0..3).map{shares[it]?:0})
            }
            "recovery"->{
                val statuses=vector(row[1]);val actors=(0..3).map{OriginalPartyRules.Actor(it,it,1,statuses[it],0)}
                val result=OriginalPartyRules.recoverStatuses(actors,row[2].toInt())
                assertEquals(vector(row[4]),(0..3).map{result.statusByActorIndex.getValue(it)})
                assertEquals(row[5].toInt(),result.randomByte)
            }
            else->fail("Unknown original CPU family")
        }
    }
    @Test fun fifthActorDuplicateIdentityAndMissingFourthOwnerRemainRejected(){
        assertNotNull(runCatching{OriginalPartyRules.Actor(4,4,1,0,0)}.exceptionOrNull())
        assertNotNull(runCatching{OriginalPartyRules.physicalMultiplier(3,37,0,mapOf(0 to List(36){255}))}.exceptionOrNull())
        val actors=(0..3).map{OriginalPartyRules.Actor(it,it,1,0,0)}
        assertNotNull(runCatching{OriginalPartyRules.enemyTarget(actors+actors.last(),0)}.exceptionOrNull())
        assertNotNull(runCatching{OriginalPartyRules.enemyTarget(actors.map{it.copy(originalActorIndex=0)},0)}.exceptionOrNull())
    }
}
