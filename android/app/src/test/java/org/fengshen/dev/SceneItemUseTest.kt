package org.fengshen.dev

import org.junit.Assert.*
import org.junit.Test

class SceneItemUseTest {
    private fun item(id:Int)=ItemDefinition("rom.special.$id",if(id==0)"雪蓮"else"神木槳",null,"fixture","special",id,maxCount=1,
        worldUse=WorldItemUseDefinition(if(id==0)130 else 162,"rom.inventory.special.$id.used").also{
            it.sceneScript=OriginalSceneItemDefinition(OriginalSceneItems.EVIDENCE,id)})
    private fun state(id:Int):SaveSnapshot {
        val r=item(id).worldUse!!.sceneScript!!
        return SaveSnapshot("fixture",r.mapId,r.target.x*16+8,(r.target.y+1)*16+8,Key.UP,
            listOf(CharacterState("nezha",29,123456,298,430,23,120,91,87,80),
                CharacterState("yangjian",28,120000,27,495,0,90,60,28,69,maxMp=54,statusMask=64)),
            mapOf("rom.special.18" to 1,"rom.special.$id" to 1),
            mapOf("unrelated" to true,"rom.npccontext.37.196" to true),money=9021,encounterSteps=55)
    }
    @Test fun paddleUsesCurrentMenuCommandOnceAndCompletesOriginalTwoMessagesWithoutTravel(){
        val item=item(14);val r=item.worldUse!!.sceneScript!!;val s=state(14)
        val used=OriginalSceneItems.begin(s,item,r.target,true);assertTrue(used.applied)
        assertEquals("rom.dialogue.52.6",used.nextDialogue);assertEquals(s.characters,used.snapshot.characters)
        assertEquals(0,used.snapshot.inventory[item.id]);assertTrue(used.snapshot.flags[OriginalSceneItems.SHIP_FLAG]==true)
        assertTrue(r.validPending(used.snapshot));assertFalse(OriginalSceneItems.begin(used.snapshot,item,r.target,true).applied)
        val wrong=OriginalSceneItems.advance(used.snapshot,r,"rom.dialogue.52.7");assertFalse(wrong.applied);assertEquals(used.snapshot,wrong.snapshot)
        val second=OriginalSceneItems.advance(used.snapshot,r,"rom.dialogue.52.6");assertEquals("rom.dialogue.52.7",second.nextDialogue)
        val final=OriginalSceneItems.advance(second.snapshot,r,"rom.dialogue.52.7");assertTrue(final.applied);assertNull(final.nextDialogue)
        assertTrue(final.snapshot.flags["rom.map.42.flag.128"]==true);assertTrue(final.snapshot.flags[r.pendingFlag]!=true)
        assertEquals(s.mapId,final.snapshot.mapId);assertEquals(s.x,final.snapshot.x);assertEquals(s.y,final.snapshot.y)
        assertEquals(s.money,final.snapshot.money);assertEquals(s.encounterSteps,final.snapshot.encounterSteps)
        assertFalse(OriginalSceneItems.advance(final.snapshot,r,"rom.dialogue.52.7").applied)
        val ownedAgain=final.snapshot.copy(inventory=final.snapshot.inventory+(item.id to 1))
        val reuse=OriginalSceneItems.begin(ownedAgain,item,r.target,true);assertTrue(reuse.applied)
        assertTrue(reuse.snapshot.flags["rom.map.42.flag.128"]==true);assertTrue(r.validPending(reuse.snapshot))
    }
    @Test fun snowRestoresOriginalYangHpMpAndStatusBeforeMessageAndRemovesBedContextAfter(){
        val item=item(0);val r=item.worldUse!!.sceneScript!!;val s=state(0)
        val used=OriginalSceneItems.begin(s,item,r.target,true);assertTrue(used.applied)
        assertEquals("rom.dialogue.47.4",used.nextDialogue);assertEquals(s.characters.first(),used.snapshot.characters.first())
        val yang=used.snapshot.characters.last();assertEquals(s.characters.last().copy(hp=495,mp=54,statusMask=0),yang)
        assertEquals(0,used.snapshot.inventory[item.id]);assertTrue(used.snapshot.flags["rom.inventory.special.0.used"]==true)
        assertTrue(used.snapshot.flags["rom.npccontext.37.196"]==true);assertTrue(r.validPending(used.snapshot))
        val done=OriginalSceneItems.advance(used.snapshot,r,"rom.dialogue.47.4")
        assertTrue(done.applied);assertTrue(done.snapshot.flags["rom.map.37.flag.128"]==true)
        assertEquals(false,done.snapshot.flags["rom.npccontext.37.196"])
        assertEquals(s.x,done.snapshot.x);assertEquals(s.y,done.snapshot.y);assertEquals(s.money,done.snapshot.money)
        assertEquals(s.encounterSteps,done.snapshot.encounterSteps);assertFalse(OriginalSceneItems.begin(done.snapshot,item,r.target,true).applied)
    }
    @Test fun all16384OriginalSceneEffectsMatchWithoutChangingOtherPartyOrEconomy(){
        val rows=javaClass.getResourceAsStream("/world-west-scene-items-original.tsv")!!.bufferedReader().readLines().drop(1)
        assertEquals(16384,rows.size)
        for(line in rows){
            val v=line.split('\t').map(String::toInt);val item=item(v[0]);val rule=item.worldUse!!.sceneScript!!
            val original=state(v[0]);val before=original.copy(mapId=v[1],characters=original.characters.map{
                if(it.id=="yangjian")it.copy(statusMask=v[3])else it})
            val result=OriginalSceneItems.begin(before,item,rule.target.copy(spriteId=v[2]),true)
            assertEquals(line,v[4]==1,result.applied)
            if(!result.applied){assertEquals(line,before,result.snapshot);continue}
            val after=result.snapshot;val yang=after.characters.last()
            assertEquals(line,v[5],yang.statusMask);assertEquals(line,v[6],yang.hp);assertEquals(line,v[7],yang.mp)
            assertEquals(line,v[8]==1,after.flags[OriginalSceneItems.SHIP_FLAG]==true)
            assertEquals(line,before.characters.first(),after.characters.first())
            assertEquals(line,before.money,after.money);assertEquals(line,before.encounterSteps,after.encounterSteps)
            assertEquals(line,before.mapId,after.mapId);assertEquals(line,before.x,after.x);assertEquals(line,before.y,after.y)
            assertEquals(line,0,after.inventory[item.id]);assertTrue(line,rule.validPending(after))
        }
    }
    @Test fun unavailableContextTargetCountOrMissingRecordNeverConsumesOrCreatesParty(){
        for(id in listOf(0,14)){
            val item=item(id);val r=item.worldUse!!.sceneScript!!;val s=state(id)
            val invalid=listOf(s.copy(mapId=16),s.copy(direction=Key.DOWN),s.copy(x=s.x+16),
                s.copy(inventory=s.inventory-item.id),s.copy(inventory=s.inventory+(item.id to 2)))+
                if(id==0)listOf(s.copy(flags=s.flags-"rom.npccontext.37.196"),s.copy(characters=s.characters.dropLast(1)))else emptyList()
            for(before in invalid){val result=OriginalSceneItems.begin(before,item,r.target,true)
                assertFalse(result.applied);assertEquals(before,result.snapshot)}
            assertFalse(OriginalSceneItems.begin(s,item,r.target,false).applied)
            assertFalse(OriginalSceneItems.begin(s,item,r.target.copy(spriteId=226),true).applied)
        }
    }
}
