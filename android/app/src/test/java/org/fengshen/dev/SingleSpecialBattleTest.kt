package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Expectations are captured original CPU outputs. Isolated fixtures are not normal App acceptance. */
class SingleSpecialBattleTest {
    private val rows=javaClass.getResourceAsStream("/enemy-single-special-cpu.tsv")!!.bufferedReader().use{it.readLines()}
        .filter{it.isNotBlank()&&!it.startsWith("#")}.map{it.split('\t')}
    private fun actor(id:String,hp:Int=100,status:Int=0,stamina:Int=20)=CharacterState(
        id,12,2000,hp,maxOf(hp,100),0,1,stamina,1,0,0,EquipmentState(0,-1,0,28),statusMask=status)
    private fun battle(party:List<CharacterState>,behavior:Int,base:Int,id:Int,hit:Int=193,armor:Int=20):OpeningBattle {
        val enemy=EnemyDefinition(id,"原名未核",600,60,40,250,380,hit,behavior).also{it.specialBaseDamage=base}
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
    @Test fun allOriginalAiBytesAndAliveSubsetsMatchSingleTargetSelection(){
        val expected=rows.filter{it[0]=="D"};assertEquals(2048,expected.size)
        for(r in expected){
            val behavior=r[1].toInt();val id=r[2].toInt();val hit=r[3].toInt();val byte=r[4].toInt();val aliveMask=r[5].toInt()
            val party=listOf(actor("nezha",if(aliveMask and 1!=0)1000 else 0,status=if(aliveMask and 1!=0)0 else 32),
                actor("xiaolongnv",if(aliveMask and 2!=0)1000 else 0,status=if(aliveMask and 2!=0)0 else 32))
            val original=party.mapIndexed{i,a->OriginalPartyRules.Actor(i,i,a.hp,a.statusMask,a.agility)}
            assertEquals(r.toString(),r[8].toInt().takeIf{it>=0},OriginalPartyRules.enemyTarget(original,byte))
            if(aliveMask==0)continue
            val b=battle(party,behavior,15,id,hit);val t=round(b,byte)
            val special=r[6]=="1";val succeeds=r[7]=="1"
            assertEquals(r.toString(),special,t.actions.any{it.kind==BattleActionKind.SPECIAL})
            assertFalse(t.actions.any{it.kind==BattleActionKind.ICE})
            val hits=t.actions.filter{it.actorSlot==3&&it.kind==BattleActionKind.DAMAGE}
            assertEquals(if(succeeds)1 else 0,hits.size)
            if(succeeds){val selected=party[r[8].toInt()].id;assertEquals(selected,hits.single().targetId)
                assertEquals(if(special)15 else 20,-hits.single().hpDelta)
                val other=party.first{it.id!=selected};assertEquals(other.hp,hits.single().partyHp[other.id])}
        }
    }
    @Test fun allElevenIdentityDamageOutputsAffectOnlyTheSelectedLivingActor(){
        val expected=rows.filter{it[0]=="H"};assertEquals(1100,expected.size)
        for(r in expected){
            val behavior=r[1].toInt();val id=r[2].toInt();val targetIndex=r[3].toInt();val mask=r[4].toInt()
            val hp=r[5].toInt();val armor=r[6].toInt();val base=r[7].toInt();val damage=r[8].toInt();val after=r[9].toInt()
            assertEquals(damage,OriginalStatus.incomingDamage(base,mask))
            if(mask==32)continue // Raw CPU arithmetic fixture, not a legal living actor.
            val party=mutableListOf(actor("nezha",65535),actor("xiaolongnv",65535));val selected=party[targetIndex].id
            party[targetIndex]=actor(selected,hp,mask,armor)
            val t=round(battle(party,behavior,base,id,armor=armor),targetIndex*4)
            val hits=t.actions.filter{it.actorSlot==3&&it.kind==BattleActionKind.DAMAGE}
            assertEquals(r.toString(),1,hits.size);val result=hits.single()
            assertEquals(selected,result.targetId);assertEquals(after,result.partyHp[selected])
            assertEquals(-minOf(damage,hp),result.hpDelta)
            assertEquals(if(after==0)OriginalStatus.DEAD else r[10].toInt(),result.partyStatus[selected])
            val other=party[1-targetIndex];assertEquals(other.hp,result.partyHp[other.id]);assertEquals(other.statusMask,result.partyStatus[other.id])
        }
    }
    @Test fun unsupportedMissingOrConflictingDefinitionsStayRejected(){
        for(behavior in listOf(2,4)){
            val enemy=EnemyDefinition(147,"原名未核",1540,1,1,1,1,255,behavior)
            assertFalse(OriginalStatus.enemySupported(enemy));enemy.specialBaseDamage=100;assertTrue(OriginalStatus.enemySupported(enemy))
            enemy.specialBaseDamage=65536;assertFalse(OriginalStatus.enemySupported(enemy))
            assertFalse(OriginalStatus.enemySupported(enemy.copy(iceBaseDamage=100).also{it.specialBaseDamage=100}))
        }
        for(behavior in listOf(5,10,11))assertFalse(OriginalStatus.enemySupported(EnemyDefinition(1,"未核",1,1,1,1,1,255,behavior).also{it.specialBaseDamage=100}))
    }
}
