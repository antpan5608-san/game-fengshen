package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Expectations are captured original CPU outputs. Isolated fixtures are not normal App acceptance. */
class Status16BattleTest {
    private val rows=javaClass.getResourceAsStream("/enemy-status16-cpu.tsv")!!.bufferedReader().use{it.readLines()}
        .filter{it.isNotBlank()&&!it.startsWith("#")}.map{it.split('\t')}
    private fun actor(id:String,hp:Int=100,status:Int=0,stamina:Int=20)=CharacterState(
        id,12,2000,hp,maxOf(hp,100),0,1,stamina,1,0,0,EquipmentState(0,-1,0,28),statusMask=status)
    private fun battle(party:List<CharacterState>,behavior:Int,base:Int,id:Int,hit:Int=193,armor:Int=20):OpeningBattle {
        val enemy=EnemyDefinition(id,"原名未核",600,60,40,250,380,hit,behavior)
        val physical=PhysicalRules(mapOf(-1 to 64,0 to 64),List(36){255})
        val content=BattleContent(60,emptyList(),emptyList(),mapOf(id to enemy),listOf(GrowthRow(13,3131,1,0,0,0,0,0,false)),
            0,0,0,0,mapOf(id to 255),true,true,physicalRules=physical).also{
            it.characterPhysicalRules=mapOf("xiaolongnv" to physical)
            it.characterGrowth=mapOf("xiaolongnv" to listOf(GrowthRow(13,2525,1,0,0,0,0,0,false)))
        }
        return OpeningBattle(EncounterGroup(160,listOf(EncounterMember(3,id))),content,party.first(),0,armor).also{
            if(party.size>1)it.configureParty(party,party.mapIndexed{i,p->p.id to i}.toMap(),party.associate{p->p.id to 0},party.associate{p->p.id to armor})
        }
    }
    private fun round(b:OpeningBattle,byte:Int,onRead:()->Unit={}):BattleTurn {
        var first=true
        while(b.inputHero!=null){
            val turn=b.attack(3){onRead();if(first){first=false;byte}else 0}
            if(turn!=null)return turn
        }
        return b.continueSkippedCommands{onRead();byte}!!
    }
    @Test fun originalAllBytesDistinguishSpecialHitSpecialMissAndPhysicalFallback(){
        val expected=rows.filter{it[0]=="D"};assertEquals(1024,expected.size)
        for(r in expected){
            val byte=r[1].toInt();val alive=r[2].toInt();val choice=r[3]=="1";val hitStatus=r[4]=="1";val hit=r[5]=="1";val target=r[6].toInt()
            assertEquals(hitStatus,OriginalStatus.status16Hits(byte))
            val party=listOf(actor("nezha",if(alive and 1!=0)1000 else 0,if(alive and 1!=0)0 else 32),
                actor("xiaolongnv",if(alive and 2!=0)1000 else 0,if(alive and 2!=0)0 else 32))
            if(alive==0)continue
            val b=battle(party,6,0,39,hit=181);val t=round(b,byte)
            val enemyActions=t.actions.filter{it.actorSlot==3}
            assertEquals(if(choice)0 else if(hit)20 else 0,t.enemyDamage)
            assertEquals(if(choice&&!hitStatus||!choice&&!hit)1 else 0,enemyActions.count{it.kind==BattleActionKind.MISS})
            assertEquals(if(hitStatus)1 else 0,enemyActions.count{it.kind==BattleActionKind.STATUS})
            if(hitStatus){val frame=enemyActions.last{it.kind==BattleActionKind.STATUS};val id=party[target].id
                assertEquals(16,frame.partyStatus[id]);assertEquals(party[target].hp,frame.partyHp[id])
                val other=party[1-target];assertEquals(other.statusMask,frame.partyStatus[other.id]);assertEquals(other.hp,frame.partyHp[other.id])
                assertFalse(t.actions.any{it.actorId==id&&it.kind==BattleActionKind.ATTACK})}
            assertEquals(BattlePhase.TARGET,t.phase)
            // A living stone + dead companion is not the original all-bit10 defeat test.
            if(alive in listOf(1,2))assertEquals(BattlePhase.TARGET,t.phase)
        }
    }
    @Test fun originalPriorityCoversEveryByteAndPreservesHpInLegalLivingTargets(){
        val expected=rows.filter{it[0]=="S"};assertEquals(1536,expected.size)
        for(r in expected){val mask=r[2].toInt();val hp=r[3].toInt();val next=r[4].toInt()
            if(hp==0)continue // Original enemyTarget filters this unsafe direct dispatch fixture.
            val h=actor("nezha",hp,mask);val applied=OriginalStatus.applyStatus16(h)
            assertEquals(r.toString(),next,applied.statusMask);assertEquals(h,applied.copy(statusMask=mask))
            assertEquals(0,r[5].toInt())}
        val dead=actor("nezha",0,32);assertEquals(dead,OriginalStatus.applyStatus16(dead))
    }
    @Test fun originalDefeatDistinguishesAllStoneFromDeadPlusStone(){
        val expected=rows.filter{it[0]=="F"};assertEquals(9,expected.size)
        for(r in expected){val a=r[1].toInt();val b=r[2].toInt()
            val actors=listOf(OriginalPartyRules.Actor(0,0,if(a==32)0 else 50,a,1),OriginalPartyRules.Actor(1,1,if(b==32)0 else 50,b,1))
            assertEquals(r.toString(),r[3]=="1",OriginalPartyRules.defeated(actors))}
        val lone=battle(listOf(actor("nezha",1000)),6,0,39)
        var calls=0;val t=round(lone,0){calls++}
        assertEquals(1,calls);assertEquals(BattlePhase.DEFEAT,t.phase);assertEquals(1000,lone.hero.hp)
        assertEquals(16,lone.hero.statusMask);assertNull(lone.settle(100))
    }
    @Test fun mixedNoInputPartyContinuesTheExistingSchedulerWithoutInventedCommand(){
        val expected=rows.filter{it[0]=="I"};assertEquals(6,expected.size)
        for(r in expected){val states=listOf(r[1].toInt(),r[2].toInt())
            assertEquals("0xba18",r[3])
            val party=listOf(actor("nezha",if(states[0]==32)0 else 1000,states[0]),actor("xiaolongnv",if(states[1]==32)0 else 1000,states[1]))
            val b=battle(party,0,0,20);assertNull(b.inputHero)
            if(party.none{it.hp>0}){assertNull(b.continueSkippedCommands{error("No survivors cannot advance")});continue}
            var calls=0;val t=b.continueSkippedCommands{calls++;255}!!
            assertEquals(1,calls);assertEquals(0,t.playerDamage);assertEquals(0,t.enemyDamage)
            assertFalse(t.actions.any{it.actorId!=null&&it.kind==BattleActionKind.ATTACK})
            assertEquals(if(states.all{it and 16!=0})BattlePhase.DEFEAT else BattlePhase.TARGET,t.phase)
        }
    }
}
