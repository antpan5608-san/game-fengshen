package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

/** Expectations are captured original CPU outputs. Isolated fixtures are not normal App acceptance. */
class Behavior1BattleTest {
    private val rows=javaClass.getResourceAsStream("/enemy-behavior1-cpu.tsv")!!.bufferedReader().use{it.readLines()}
        .filter{it.isNotBlank()&&!it.startsWith("#")}.map{it.split('\t')}
    private fun actor(id:String,hp:Int=100,status:Int=0,stamina:Int=20)=CharacterState(
        id,12,2000,hp,maxOf(hp,100),0,1,stamina,1,0,0,EquipmentState(0,-1,0,28),statusMask=status)
    private fun battle(party:List<CharacterState>,base:Int=15,id:Int=143,armor:Int=20):OpeningBattle {
        val enemy=EnemyDefinition(id,"楚江王",600,60,40,250,380,193,1).also{it.specialBaseDamage=base}
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
    @Test fun oneAiByteMatchesAllOriginalDispatchHitAndTargetBoundaries(){
        val expected=rows.filter{it[0]=="D"};assertEquals(256,expected.size)
        for(r in expected){
            val byte=r[1].toInt();val special=r[2]=="1";val hit=r[3]=="1"
            val b=battle(listOf(actor("nezha")));var reads=0;val t=round(b,byte){reads++}
            assertEquals(r.toString(),special,t.actions.any{it.kind==BattleActionKind.SPECIAL})
            assertEquals(special,OriginalStatus.choosesSpecial1(byte));assertEquals(2,reads)
            assertEquals(if(special)15 else if(hit)20 else 0,t.enemyDamage)
            assertEquals(hit,t.actions.any{it.actorSlot==3&&it.kind==BattleActionKind.DAMAGE})
            assertEquals(600,t.actions.first().enemyHp[3]);assertEquals(100,t.actions.first().heroHp)
        }
    }
    @Test fun actualCpuDamageSnapshotsIgnoreArmorAndStaminaForEachLivingTarget(){
        val expected=rows.filter{it[0]=="H"};assertEquals(160,expected.size)
        for(r in expected){
            val actorIndex=r[1].toInt();val mask=r[2].toInt();val hp=r[3].toInt();val defense=r[4].toInt()
            val damage=r[5].toInt();val after=r[6].toInt();val status=r[7].toInt()
            assertEquals(damage,OriginalStatus.incomingDamage(15,mask))
            // CPU direct-damage fixture32 has positive HP and death status; it is not a legal live App target.
            if(mask==32)continue
            val party=listOf(actor("nezha",1000),actor("xiaolongnv",1000)).toMutableList()
            val id=party[actorIndex].id;party[actorIndex]=actor(id,hp,mask,defense)
            val b=battle(party,armor=defense);val t=round(b,0)
            val hits=t.actions.filter{it.actorSlot==3&&it.kind==BattleActionKind.DAMAGE}
            assertEquals(2,hits.size)
            val actual=hits.single{it.targetId==id}
            assertEquals(r.toString(),after,actual.partyHp[id])
            // Original direct A956 damage capture precedes the later death-flag dispatcher.
            assertEquals(if(after==0)OriginalStatus.DEAD else status,actual.partyStatus[id])
            assertEquals(-minOf(damage,hp),actual.hpDelta)
            assertEquals(party[1].hp,hits[0].partyHp["xiaolongnv"])
        }
    }
    @Test fun allSevenOriginalIdentityValuesApplyWithoutReusingIceOrDragonIndex(){
        val expected=rows.filter{it[0]=="I"};assertEquals(28,expected.size)
        for(r in expected){
            val enemy=r[1].toInt();val actorIndex=r[2].toInt();val mask=r[3].toInt();val base=r[4].toInt()
            val party=listOf(actor("nezha",65535),actor("xiaolongnv",65535)).toMutableList()
            val id=party[actorIndex].id;party[actorIndex]=party[actorIndex].copy(statusMask=mask)
            val t=round(battle(party,base,enemy,65535),0)
            val hit=t.actions.single{it.actorSlot==3&&it.kind==BattleActionKind.DAMAGE&&it.targetId==id}
            assertEquals(r.toString(),r[7].toInt(),hit.partyHp[id]);assertEquals(-r[5].toInt(),hit.hpDelta)
            assertEquals(1,t.actions.count{it.kind==BattleActionKind.SPECIAL});assertFalse(t.actions.any{it.kind==BattleActionKind.ICE})
        }
    }
    @Test fun oneAllTargetActionCompletesDeathThenNoExtraActionOrSettlement(){
        val b=battle(listOf(actor("nezha",5),actor("xiaolongnv",6)));var reads=0
        val t=round(b,0){reads++};assertEquals(BattlePhase.DEFEAT,t.phase);assertEquals(1,reads)
        val hits=t.actions.filter{it.kind==BattleActionKind.DAMAGE};assertEquals(2,hits.size)
        assertEquals(6,hits[0].partyHp["xiaolongnv"]);assertEquals(0,hits[1].partyHp["xiaolongnv"])
        assertTrue(b.party.all{it.hp==0&&it.statusMask==OriginalStatus.DEAD});assertNull(b.settle(100))
    }
    @Test fun missingOrConflictingSpecialDefinitionCannotSilentlyBecomePhysical(){
        val bare=EnemyDefinition(143,"楚江王",600,60,40,250,380,193,1)
        assertFalse(OriginalStatus.enemySupported(bare));bare.specialBaseDamage=15;assertTrue(OriginalStatus.enemySupported(bare))
        bare.specialBaseDamage=65536;assertFalse(OriginalStatus.enemySupported(bare))
        val conflicting=bare.copy(iceBaseDamage=15).also{it.specialBaseDamage=15}
        assertFalse(OriginalStatus.enemySupported(conflicting))
    }
}
