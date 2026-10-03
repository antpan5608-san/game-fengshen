package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Original direct-damage CPU captures are independent expected values, not normal App proof. */
class IceIdentityBattleTest {
    private val rows=javaClass.getResourceAsStream("/enemy-ice-identities-cpu.tsv")!!.bufferedReader().use{it.readLines()}
        .filter{it.isNotBlank()&&!it.startsWith("#")}.map{it.split('\t').map(String::toInt)}
    private fun actor(id:String,hp:Int,status:Int=0,stamina:Int=0)=CharacterState(
        id,12,2000,hp,maxOf(hp,100),0,1,stamina,1,0,0,EquipmentState(0,-1,0,28),statusMask=status)
    @Test fun fullIdentityDomainMatchesBothPartySnapshotsWithoutDefenseSubtraction(){
        assertEquals(1700,rows.size)
        for(r in rows){
            val (eid,index,mask,hp,armor)=r.take(5);val base=r[5];val damage=r[6];val after=r[7]
            assertEquals(damage,OriginalStatus.incomingDamage(base,mask))
            if(mask==OriginalStatus.DEAD)continue // HP-positive dead is only a CPU arithmetic fixture.
            val party=mutableListOf(actor("nezha",65535),actor("xiaolongnv",65535));val target=party[index].id
            party[index]=actor(target,hp,mask,armor)
            val enemy=EnemyDefinition(eid,"原名未核",65535,1,65535,1,1,255,3,base)
            val physical=PhysicalRules(mapOf(-1 to 64,0 to 64),List(36){255})
            val content=BattleContent(67,emptyList(),emptyList(),mapOf(eid to enemy),listOf(GrowthRow(13,3131,1,0,0,0,0,0,false)),0,0,0,0,
                mapOf(eid to 255),true,true,physicalRules=physical).also{
                it.characterPhysicalRules=mapOf("xiaolongnv" to physical)
                it.characterGrowth=mapOf("xiaolongnv" to listOf(GrowthRow(13,2525,1,0,0,0,0,0,false)))
            }
            val b=OpeningBattle(EncounterGroup(167,listOf(EncounterMember(3,eid))),content,party.first(),0,armor).also{
                it.configureParty(party,mapOf("nezha" to 0,"xiaolongnv" to 1),party.associate{p->p.id to 0},party.associate{p->p.id to armor})
            }
            var t:BattleTurn?=null
            while(b.inputHero!=null&&t==null)t=b.attack(3){0}
            if(t==null)t=b.continueSkippedCommands{0}
            val result=t!!;val hits=result.actions.filter{it.actorSlot==3&&it.kind==BattleActionKind.DAMAGE}
            assertEquals(r.toString(),2,hits.size)
            val hit=hits.single{it.targetId==target}
            assertEquals(after,hit.partyHp[target]);assertEquals(-minOf(damage,hp),hit.hpDelta)
            assertEquals(if(after==0)OriginalStatus.DEAD else mask,hit.partyStatus[target])
            assertEquals(1,result.actions.count{it.kind==BattleActionKind.ICE})
            assertFalse(result.actions.any{it.kind==BattleActionKind.SPECIAL})
            assertEquals(party[1].hp,hits.first().partyHp["xiaolongnv"])
        }
    }
}
