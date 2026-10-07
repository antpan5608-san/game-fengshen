package org.fengshen.dev

import android.content.Intent
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.util.Log
import android.view.ViewGroup
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.security.MessageDigest
import org.json.JSONObject

@Suppress("DEPRECATION")
class ContentTest:IsolatedGameTestCase(){
    /** Original Firecloud loader and gift codec, CONTROLLED not a normal pilgrimage. */
    fun testControlledWell8LocationItemPendingCodecAndNoDuplicateCompletion(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val item=c.itemDefinitions.getValue("rom.special.1");val rule=item.worldUse!!.sceneScript!!
        assertTrue(rule.verified());assertTrue(rule.locationTarget)
        assertEquals(0,rule.target.spriteId);assertTrue(c.npcs.none{it.id==rule.npcId})
        assertEquals((14..18).map{"rom.dialogue.18.$it"},rule.continuation.dialogueIds)
        val before=SaveSnapshot(c.scene.version,8,13*16+8,26*16+8,Key.LEFT,
            listOf(c.initialPlayer),mapOf(item.id to 1),mapOf("unrelated" to true),money=331)
        assertTrue(before.validate(c));assertEquals(before,SaveSnapshot.parse(before.json().toString()))
        var s=OriginalSceneItems.begin(before,item,rule.target,true).snapshot
        assertEquals(0,s.inventory[item.id]);assertTrue(s.flags[OriginalSceneItems.PLAGUE_FLAG]==true)
        for(message in rule.continuation.dialogueIds){
            assertTrue(s.validate(c));val cold=SaveSnapshot.parse(s.json().toString())
            assertEquals(s,cold);assertTrue(rule.validPending(cold));assertTrue(cold.validate(c))
            assertFalse(OriginalSceneItems.begin(cold,item,rule.target,true).applied)
            val result=OriginalSceneItems.advance(cold,rule,message);assertTrue(result.applied);s=result.snapshot
        }
        assertTrue(s.validate(c));assertEquals(s,SaveSnapshot.parse(s.json().toString()))
        assertTrue(s.flags["rom.map.8.flag.128"]==true);assertTrue(s.flags[OriginalSceneItems.WELL_CONTEXT]==true)
        assertEquals(before.characters,s.characters);assertEquals(before.money,s.money)
        assertEquals(before.x,s.x);assertEquals(before.y,s.y)
        assertFalse(OriginalSceneItems.advance(s,rule,"rom.dialogue.18.18").applied)
        val legacy=before.copy(contentVersion="opening-segment-001-c58")
        assertTrue(legacy.validate(c));assertEquals(before.characters,SaveSnapshot.parse(legacy.json().toString()).characters)
    }

    fun testControlledSages89GiftAndFullInventoryRepeat(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        assertTrue(89 in c.scenes);assertEquals(3,c.npcs.filter{it.mapId==89}.size)
        val npc=c.npcs.single{it.id=="rom.npc.89.0"};val rule=npc.originalTalk!!
        val item=c.itemDefinitions.getValue("rom.special.1");assertEquals("丹藥",item.name)
        if(item.worldUse!=null){assertTrue(item.worldUse.sceneScript!!.verified());assertTrue(item.worldUse.sceneScript!!.locationTarget)}
        assertNull(item.herbUse);assertNull(item.battleBindingUse)
        val party=listOf(c.initialPlayer,c.joinCharacters.getValue("xiaolongnv"),c.joinCharacters.getValue("yangjian"))
        val flags=mapOf(OriginalYangJoin.CONTEXT_FLAG to true,OriginalYangJoin.USED_FLAG to true,
            "rom.inventory.special.0.used" to true,"rom.map.37.flag.128" to true)
        val before=SaveSnapshot(c.scene.version,89,8*16+8,6*16+8,Key.UP,party,
            mapOf(OriginalYangJoin.ITEM_ID to 1,"rom.special.0" to 0),flags,money=9090,encounterSteps=0)
        assertTrue(before.validate(c));val gift=OriginalNpcTalk.begin(before,rule,item)
        assertTrue(gift.applied);assertEquals("rom.dialogue.99.0",gift.nextDialogue)
        assertTrue(gift.snapshot.validate(c));assertEquals(gift.snapshot,SaveSnapshot.parse(gift.snapshot.json().toString()))
        assertEquals(1,gift.snapshot.inventory[item.id]);assertFalse(gift.snapshot.inventory.containsKey("rom.special.0"))
        assertFalse(gift.snapshot.flags.containsKey("rom.inventory.special.0.used"))
        assertEquals(before.money,gift.snapshot.money);assertEquals(before.characters,gift.snapshot.characters)
        val repeat=OriginalNpcTalk.begin(gift.snapshot,rule,item);assertTrue(repeat.applied)
        assertEquals(gift.snapshot,repeat.snapshot);assertEquals("rom.dialogue.99.1",repeat.nextDialogue)
        assertTrue(c.npcsForState(89,gift.snapshot.flags).filter{it.id!=npc.id}.all{it.readOnlyDialogue})
        assertEquals(2,c.exits.count{it.fromMapId==89||it.fromMapId==16&&it.toMapId==89})
        assertTrue(before.copy(contentVersion="opening-segment-001-c57").validate(c))
        Log.i("FengshenSages89Test","CONTROLLED_LOADER_GIFT_CODEC_NOT_NORMAL_MAINLINE")
    }
    /** Actual scoped alternative room; CONTROLLED loader/codec, not normal cure/mainline. */
    fun testControlledMaster172ArrivalLetterAndSavedExplicitMap(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val rule=c.mapArrivals.single()
        assertTrue(rule.verified());assertTrue(171 in c.scenes&&172 in c.scenes)
        val flags=mapOf(OriginalYangJoin.CONTEXT_FLAG to true,OriginalYangJoin.USED_FLAG to true,
            "rom.inventory.special.0.used" to true,"rom.map.37.flag.128" to true)
        val party=listOf(c.initialPlayer,c.joinCharacters.getValue("xiaolongnv"),c.joinCharacters.getValue("yangjian"))
        val old=SaveSnapshot("opening-segment-001-c56",171,7*16+8,14*16+8,Key.UP,
            party,mapOf(OriginalYangJoin.ITEM_ID to 1,"rom.special.0" to 0),flags,money=1030,encounterSteps=7)
        assertTrue(old.validate(c));assertEquals(172,rule.resolve(171,party.size,old.inventory,flags))
        assertEquals(171,rule.resolve(171,party.size,emptyMap(),flags))
        assertEquals(old,SaveSnapshot.parse(old.json().toString()))
        val world=World(c.scenes,c.exits,101)
        world.arrivalResolver={rule.resolve(it,party.size,old.inventory,flags)}
        assertTrue(world.tryRestore(old.mapId,old.x,old.y));assertEquals(171,world.mapId)
        val room=old.copy(contentVersion=c.scene.version,mapId=172,y=5*16+8)
        assertTrue(room.validate(c));assertEquals(room,SaveSnapshot.parse(room.json().toString()))
        val letter=c.npcs.single{it.id=="rom.npc.172.2"}
        assertTrue(letter.readOnlyDialogue);assertEquals("rom.dialogue.182.2",letter.firstDialogue)
        assertEquals(7,letter.x);assertEquals(4,letter.y);assertTrue(letter.firstEffects.isEmpty())
        assertNull(letter.originalTalk);assertNull(letter.treasure);assertNull(letter.repeatDialogue)
        assertTrue(c.dialogues.getValue(letter.firstDialogue).text.contains("火雲洞"))
        assertTrue(c.npcsForState(172,flags).all{it.readOnlyDialogue})
        val back=c.exits.single{it.fromMapId==172}
        assertEquals(101,back.toMapId);assertEquals(32,back.spawnX);assertEquals(12,back.spawnY)
        Log.i("FengshenMaster172Test","CONTROLLED_LOADER_CODEC_NOT_NORMAL_CURE_MAINLINE")
    }
    /** Actual boat/fairy bundle and codec, isolated fixture; not a normal voyage. */
    fun testControlledFreeBoatFairyDefinitionsAndFailureSave(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        assertTrue("Requires actual free ship/fairy candidate",c.freeBoatEnabled)
        assertEquals(setOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT),c.freeBoatSprites.keys)
        assertEquals(setOf(0,219),c.exits.filter{it.arrivalTerrainMode!=null}.map{it.arrivalTerrainMode!!}.toSet())
        val flags=mapOf(OriginalYangJoin.CONTEXT_FLAG to true,OriginalYangJoin.USED_FLAG to true,
            OriginalSceneItems.SHIP_FLAG to true,"rom.inventory.special.14.used" to true)
        val roster=listOf(c.initialPlayer,c.joinCharacters.getValue("xiaolongnv"),c.joinCharacters.getValue("yangjian").copy(statusMask=64))
        val before=SaveSnapshot(c.scene.version,136,7*16+8,6*16+8,Key.UP,roster,
            mapOf(OriginalYangJoin.ITEM_ID to 1,"rom.special.14" to 0),flags,money=9103,encounterSteps=17)
        assertTrue(before.validate(c));assertEquals(before,SaveSnapshot.parse(before.json().toString()))
        val fairy=c.npcs.single{it.id=="rom.npc.136.0"};val rule=fairy.originalTalk!!
        val gift=OriginalNpcTalk.begin(before,rule,c.itemDefinitions.getValue(rule.itemId))
        assertTrue(gift.applied);assertEquals("rom.dialogue.146.1",gift.nextDialogue)
        assertEquals(1,gift.snapshot.inventory["rom.special.0"]);assertFalse(gift.snapshot.inventory.containsKey("rom.special.14"))
        assertEquals(before.characters,gift.snapshot.characters);assertEquals(before.money,gift.snapshot.money)
        assertTrue(gift.snapshot.validate(c));assertEquals(gift.snapshot,SaveSnapshot.parse(gift.snapshot.json().toString()))
        assertEquals("rom.dialogue.146.2",OriginalNpcTalk.begin(gift.snapshot,rule,c.itemDefinitions.getValue(rule.itemId)).nextDialogue)
        val boat=before.copy(mapId=16,x=68*16+8,y=88*16+8,terrainMode=219)
        assertTrue(boat.validate(c));assertFalse(boat.copy(terrainMode=0).validate(c))
        val parked=boat.copy(x=24*16+8,y=45*16+8,terrainMode=0,flags=OriginalBoat.park(flags,24,44,Key.DOWN))
        assertTrue(parked.validate(c));assertEquals(parked,SaveSnapshot.parse(parked.json().toString()))
        val dead=roster.map{it.copy(hp=0,statusMask=32)}
        for(outbound in listOf(true,false)){
            val failed=before.copy(mapId=if(outbound)16 else 10,x=9*16+8,y=(if(outbound)2 else 3)*16+8,
                terrainMode=if(outbound)219 else 148,direction=if(outbound)Key.UP else Key.LEFT,characters=dead,
                flags=flags+((if(outbound)OriginalBoat.FAILED_BOARD else OriginalBoat.FAILED_RETURN)to true)+("runtime.field-defeat.pending" to true))
            assertTrue(failed.validate(c));assertEquals(failed,SaveSnapshot.parse(failed.json().toString()))
            assertFalse(failed.copy(characters=roster).validate(c))
            assertFalse(failed.copy(x=10*16+8).validate(c))
        }
        assertTrue(boat.copy(contentVersion="opening-segment-001-c55").validate(c))
    }
    /** Current scene-item candidate only; CONTROLLED codec, not a normal gift/voyage. */
    fun testControlledSceneItemDefinitionsAndDurableCodec(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        assertTrue("Requires the actual scene-item candidate content",c.itemDefinitions.containsKey("rom.special.0"))
        assertEquals("神木槳",c.itemNames["rom.special.14"])
        assertEquals(if(c.itemDefinitions["rom.special.1"]?.worldUse?.sceneScript!=null)setOf(0,1,14)else setOf(0,14),
            c.sceneItemUses().map{it.originalItemId}.toSet())
        val templates=listOf(c.initialPlayer,c.joinCharacters.getValue("xiaolongnv"),
            c.joinCharacters.getValue("yangjian").copy(hp=1,mp=0,statusMask=64))
        for(id in listOf(0,14)){
            val item=c.itemDefinitions.getValue("rom.special.$id");val rule=item.worldUse!!.sceneScript!!
            val flags=mapOf(OriginalYangJoin.CONTEXT_FLAG to true,OriginalYangJoin.USED_FLAG to true,
                "rom.npccontext.37.196" to true)
            val before=SaveSnapshot(c.scene.version,rule.mapId,rule.target.x*16+8,(rule.target.y+1)*16+8,Key.UP,
                templates,mapOf(OriginalYangJoin.ITEM_ID to 1,item.id to 1),flags,money=1223,encounterSteps=33,
                interiorContext=if(id==14)InteriorContext(10,13,4)else null)
            assertTrue(before.validate(c));assertEquals(before,SaveSnapshot.parse(before.json().toString()))
            val started=OriginalSceneItems.begin(before,item,rule.target,true)
            assertTrue(started.applied);assertTrue(started.snapshot.validate(c))
            var restored=SaveSnapshot.parse(started.snapshot.json().toString());assertEquals(started.snapshot,restored)
            for(dialogue in rule.continuation.dialogueIds){
                val next=OriginalSceneItems.advance(restored,rule,dialogue);assertTrue(next.applied)
                assertTrue(next.snapshot.validate(c));restored=SaveSnapshot.parse(next.snapshot.json().toString())
                assertEquals(next.snapshot,restored)
            }
            assertEquals(before.money,restored.money);assertEquals(before.encounterSteps,restored.encounterSteps)
            assertTrue(restored.flags["rom.map.${rule.mapId}.flag.128"]==true)
            assertFalse(OriginalSceneItems.begin(restored,item,rule.target,true).applied)
            assertTrue(before.copy(contentVersion="opening-segment-001-c54").validate(c))
            assertFalse(restored.copy(inventory=restored.inventory+(item.id to 1),
                flags=restored.flags+(rule.pendingFlag to true)).validate(c))
        }
        Log.i("FengshenSceneItemTest","CONTROLLED_SCENE_ITEM_LOADER_CODEC_NOT_NORMAL_VOYAGE")
    }
    /** Local history is not the active slot or a second state schema. CONTROLLED codec regression. */
    fun testControlledSaveHistoryFullSnapshotCodecAndLegacyCompatibility(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val a=SaveSnapshot(c.scene.version,114,c.scene.spawnX*16+8,c.scene.spawnY*16+8,Key.DOWN,
            listOf(c.initialPlayer),mapOf(HerbUse.ID to 2),mapOf("opening.intro.seen" to true),money=88,encounterSteps=7)
        assertTrue(a.validate(c))
        val history=SaveHistory.append(emptyList(),a,SaveHistoryEntry.Kind.MANUAL,100,"codec-A")
        val encoded=SaveHistory.encode(history);assertEquals(history,SaveHistory.parse(encoded))
        val legacy=a.copy(contentVersion="opening-segment-001-c14")
        assertTrue(legacy.validate(c))
        assertEquals(legacy,SaveSnapshot.parse(legacy.json().toString()))
        val bad=JSONObject(encoded);bad.getJSONArray("entries").getJSONObject(0).put("snapshotSha256","0".repeat(64))
        assertTrue(runCatching{SaveHistory.parse(bad.toString())}.isFailure)
        assertTrue(runCatching{SaveHistory.parse("{invalid")}.isFailure)
        assertTrue(runCatching{SaveHistory.parse(JSONObject(encoded).put("schemaVersion",999).toString())}.isFailure)
    }
    /** CONTROLLED actual loader/caller codec; not a normal western voyage. */
    fun testWestHouseCallersOriginalWitnessAndFullSnapshotFixture(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        for(mid in listOf(41,42)){
            val scene=c.scenes.getValue(mid)
            val entry=c.exits.single{it.fromMapId==10&&it.toMapId==mid}
            val back=c.exits.single{it.fromMapId==mid&&it.returnToCaller}
            assertTrue(entry.captureCaller);assertEquals(10,back.toMapId)
            assertEquals(entry.spawnX to entry.spawnY,back.triggerX to back.triggerY)
            val caller=InteriorContext(10,entry.triggerX,entry.triggerY)
            val before=SaveSnapshot(c.scene.version,mid,scene.spawnX*16+8,scene.spawnY*16+8,Key.UP,
                listOf(c.initialPlayer),mapOf("rom.special.14" to 1,HerbUse.ID to 2),
                mapOf("opening.intro.seen" to true),money=987,encounterSteps=23,interiorContext=caller)
            assertTrue(before.validate(c));assertEquals(before,SaveSnapshot.parse(before.json().toString()))
            assertFalse(before.copy(interiorContext=null).validate(c))
            assertFalse(before.copy(interiorContext=InteriorContext(10,19,23)).validate(c))
            assertTrue(before.copy(contentVersion="opening-segment-001-c53").validate(c))
            val rule=c.npcs.single{it.id=="rom.npc.$mid.0"}.originalTalk!!
            val result=OriginalNpcTalk.begin(before,rule)
            assertTrue(result.applied);assertEquals(before.inventory,result.snapshot.inventory)
            assertEquals(before.characters,result.snapshot.characters);assertEquals(before.money,result.snapshot.money)
            assertEquals(if(mid==41)rule.repeatDialogue else rule.firstDialogue,result.nextDialogue)
        }
        assertEquals(2,c.npcs.count{it.mapId==42&&it.hiddenInvestigation})
        assertFalse(c.npcs.filter{it.mapId==42&&it.hiddenInvestigation}.any{c.npcInteractive(it)})
    }

    /** Real loader and isolated state fixtures, not a normal Jiameng route. */
    fun testWestVillagesOriginalSharedCallersAndSaveFixture(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        for(mid in listOf(8,9,10))assertNotNull(c.scenes[mid])
        assertEquals(21,c.npcs.count{it.mapId in listOf(8,9,10)})
        assertEquals(7,c.npcs.count{it.mapId in listOf(8,9,10)&&it.hiddenInvestigation})
        val witness=c.npcs.single{it.id=="rom.npc.8.0"}.originalTalk!!
        assertEquals(53,witness.actionId);assertEquals("rom.global.7c9.nonzero",witness.witnessFlagId)
        val boat=c.npcs.single{it.id=="rom.npc.10.6"};assertFalse(c.npcInteractive(boat));assertTrue(boat.talkDisabled)
        val special=c.itemDefinitions.getValue("rom.special.14")
        assertEquals("special",special.category);assertEquals(14,special.originalId);assertEquals(1,special.maxCount)
        if(c.freeBoatEnabled){
            assertEquals(14,special.worldUse?.sceneScript?.originalItemId)
            assertEquals(OriginalSceneItems.EVIDENCE,special.worldUse?.sceneScript?.evidence)
        }else assertNull(special.worldUse)
        assertNull(special.battleBindingUse)
        for(mid in listOf(8,9,10)){
            val scene=c.scenes.getValue(mid);val snapshot=SaveSnapshot(c.scene.version,mid,scene.spawnX*16+8,scene.spawnY*16+8,
                Key.DOWN,listOf(c.initialPlayer),mapOf(HerbUse.ID to 2),mapOf("opening.intro.seen" to true),money=999)
            assertTrue(snapshot.validate(c));assertEquals(snapshot,SaveSnapshot.parse(snapshot.json().toString()))
            assertTrue(snapshot.copy(contentVersion="opening-segment-001-c52").validate(c))
            assertEquals(6,c.serviceBindings.count{it.callerMapId==mid})
        }
        val returned=c.exits.single{it.fromMapId==9&&it.toMapId==16}
        assertEquals(40 to 81,returned.spawnX to returned.spawnY);assertTrue(returned.preserveArrivalDirection)
    }

    /** Real loader and isolated state fixtures, not a normal Jiameng route. */
    fun testJiamengOriginalMapsActorsAndBattleDefinitionsFixture(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets));val rules=c.battle!!
        for(mid in listOf(145,146,147,148,37))assertNotNull(c.scenes[mid])
        assertEquals(32,c.scenes.getValue(145).width);assertEquals(32,c.scenes.getValue(146).width)
        assertEquals(16,c.scenes.getValue(147).width);assertEquals(16,c.scenes.getValue(148).width)
        for(mid in 145..148){val zone=rules.zones.single{it.mapId==mid}
            assertEquals(12,zone.groups.size);assertTrue(zone.groups.all{it.zoneId==29});assertEquals(245,zone.randomThreshold);assertTrue(zone.highGate)}
        for(id in listOf(60,61,62,158,159,160,161)){
            assertNotNull(c.enemyGraphics[id]);assertNotNull(c.itemDefinitions[rules.enemies.getValue(id).loot!!.itemId])}
        val guard=c.npcsForState(145,emptyMap()).single{it.id=="rom.npc.145.0"}
        assertEquals("rom.dialogue.155.0",guard.firstDialogue)
        val active=mapOf("rom.npccontext.145.215" to true)
        val activated=c.npcsForState(145,active).single{it.id==guard.id}
        assertEquals("rom.dialogue.155.1",activated.firstDialogue);assertNotSame(guard.sprite,activated.sprite)
        val completed=active+("rom.npccontext.145.228" to true)
        assertFalse(c.npcVisible(activated,completed));assertFalse(10*32+4 in c.sceneForState(145,completed)!!.dynamicObjectCells)
        val bed=c.npcs.single{it.id=="rom.npc.37.yang-bed"}
        assertFalse(c.npcVisible(bed,emptyMap()));assertFalse(c.npcInteractive(bed))
        assertFalse(5*16+3 in c.sceneForState(37,emptyMap())!!.dynamicObjectCells)
        val context=mapOf("rom.npccontext.37.196" to true)
        assertTrue(c.npcVisible(bed,context));assertTrue(5*16+3 in c.sceneForState(37,context)!!.dynamicObjectCells)
        assertEquals(setOf("rom.map.145.flag.4","rom.map.145.flag.8"),c.sceneBarriers.filter{it.mapId==145}.map{it.removedFlagId}.toSet())
        val first=rules.storyBattles.getValue(guard.id);assertTrue(first.activeIn(active));assertFalse(first.activeIn(emptyMap()))
        assertTrue(first.finalizeWithoutDialogue);assertEquals(listOf(EncounterMember(3,158)),first.group.members)
        val three=rules.storyBattles.getValue("rom.npc.148.0")
        assertEquals(listOf(EncounterMember(0,159),EncounterMember(3,160),EncounterMember(6,161)),three.group.members)
        assertEquals(5,three.additionalEntryTriggers.size);assertTrue(three.intro!!.preserveOpeningPosition)
        assertEquals(37,three.continuation!!.destination!!.mapId)
        assertEquals(4 to 5,three.continuation!!.destination!!.let{it.x to it.y})
        assertEquals(5,c.itemDefinitions.getValue("rom.special.18").battleBindingUse!!.bindingMarker)
        assertNull(c.equipmentDefinitions["rom.armor.20"])
    }

    fun testJiamengSavedActorsDialogueAndManualReturnFixture(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val yang=c.joinCharacters.getValue("yangjian").copy(statusMask=64)
        val xiao=c.joinCharacters.getValue("xiaolongnv").copy(hp=0,mp=0,statusMask=64)
        val before=SaveSnapshot(c.scene.version,37,72,88,Key.UP,listOf(c.initialPlayer,xiao,yang),
            mapOf(HerbUse.ID to 2,"rom.special.18" to 1,OriginalYangJoin.ITEM_ID to 1),
            mapOf("rom.npccontext.37.196" to true,OriginalYangJoin.CONTEXT_FLAG to true,
                OriginalYangJoin.USED_FLAG to true,"rom.map.110.flag.128" to true),money=321)
        // An admitted joined actor carries its durable original join/item state.
        // Keep rejecting the incomplete fixture that previously failed here.
        assertFalse(before.copy(flags=before.flags-OriginalYangJoin.CONTEXT_FLAG).validate(c))
        assertFalse(before.copy(flags=before.flags-OriginalYangJoin.USED_FLAG).validate(c))
        assertFalse(before.copy(inventory=before.inventory-OriginalYangJoin.ITEM_ID).validate(c))
        assertTrue(before.validate(c));assertEquals(before,SaveSnapshot.parse(before.json().toString()))
        val npc=c.npcs.single{it.id=="rom.npc.37.0"};val first=OriginalNpcTalk.begin(before,npc.originalTalk!!)
        assertTrue(first.applied);assertEquals(before,first.snapshot);assertEquals("rom.dialogue.47.0",first.nextDialogue)
        val healthy=before.copy(characters=before.characters.map{if(it.id==yang.id)it.copy(statusMask=0)else it})
        val repeat=OriginalNpcTalk.begin(healthy,npc.originalTalk!!);assertTrue(repeat.snapshot.flags["rom.map.37.flag.1"]==true)
        assertEquals("rom.dialogue.47.1",repeat.nextDialogue);assertTrue(repeat.snapshot.validate(c))
        assertEquals(healthy.copy(flags=repeat.snapshot.flags),repeat.snapshot)
        assertEquals(repeat.snapshot,OriginalNpcTalk.begin(repeat.snapshot,npc.originalTalk!!).snapshot)
        val script=c.sceneStories.getValue("rom.npc.146.0")
        val source=before.copy(mapId=146,x=40,y=88,flags=before.flags-"rom.npccontext.37.196")
        assertTrue(source.validate(c));assertFalse(script.automaticallyTriggersAt(source))
        val opened=StoryFollowup.begin(source,script);assertEquals(source.characters,opened.snapshot.characters)
        assertTrue(opened.snapshot.validate(c));val saved=SaveSnapshot.parse(opened.snapshot.json().toString())
        assertEquals(opened.snapshot,saved);assertTrue(saved.validate(c))
        assertFalse(StoryFollowup.advance(saved,script,"wrong.dialogue").applied)
        val closed=StoryFollowup.advance(saved,script,"rom.dialogue.156.2");assertTrue(closed.applied)
        assertEquals(xiao.copy(hp=xiao.maxHp,mp=xiao.maxMp!!,statusMask=0),closed.snapshot.characters[1])
        assertEquals(source.inventory,closed.snapshot.inventory);assertEquals(source.money,closed.snapshot.money)
        assertEquals(source.characters[0],closed.snapshot.characters[0]);assertEquals(yang,closed.snapshot.characters[2])
        assertTrue(closed.snapshot.validate(c));assertEquals(closed.snapshot,SaveSnapshot.parse(closed.snapshot.json().toString()))
        assertFalse(StoryFollowup.advance(closed.snapshot,script,"rom.dialogue.156.2").applied)
        assertFalse(c.npcVisible(c.npcs.single{it.id==script.npcId},closed.snapshot.flags))
        assertTrue(source.copy(contentVersion="opening-segment-001-c50").validate(c))
        assertTrue(source.copy(contentVersion="opening-segment-001-c51-r1").validate(c))
    }
    /** Actual loader and controlled JSON/variant fixtures, separate from normal optional talk. */
    /** R1 dependencies, original services and isolated save rules; not normal route evidence. */
    fun testPlayableR1FrozenDependenciesAndMedicalPartySave(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        assertEquals(setOf(0,1,2,16,17,18,19,20,22,23,25,85,95,96,97,98,114,139),c.scenes.keys)
        assertEquals("opening-segment-001-c51-r1",c.scene.version)
        assertEquals(setOf("xiaolongnv"),c.joinCharacters.keys)
        assertEquals(setOf("rom.clinic.1.revival","rom.clinic.1.care","rom.clinic.2.revival","rom.clinic.2.care"),c.clinics.keys)
        assertMedicalPartySave(c)
    }
    fun testC60FrozenDependenciesAndMedicalPartySave(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        assertEquals(setOf(0,1,2,3,4,5,6,8,9,10,16,17,18,19,20,22,23,25,37,41,42,
            60,61,62,63,64,65,66,67,68,69,70,74,76,77,78,79,85,86,87,89,95,96,97,98,
            99,100,101,107,108,109,110,114,115,116,117,136,139,141,145,146,147,148,
            158,159,163,164,171,172),c.scenes.keys)
        assertEquals("opening-segment-001-c60",c.scene.version)
        assertEquals(setOf("xiaolongnv","yangjian"),c.joinCharacters.keys)
        assertEquals(listOf(1,2,3,4,5,6,8,9,10).flatMap{caller->
            listOf("rom.clinic.$caller.revival","rom.clinic.$caller.care")}.toSet(),c.clinics.keys)
        assertMedicalPartySave(c)
    }
    fun testC61FrozenDependenciesAndMedicalPartySave(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        assertEquals(setOf(0,1,2,3,4,5,6,7,8,9,10,16,17,18,19,20,22,23,25,37,41,42,
            60,61,62,63,64,65,66,67,68,69,70,74,76,77,78,79,85,86,87,89,95,96,97,98,
            99,100,101,107,108,109,110,114,115,116,117,121,136,139,141,142,145,146,147,148,
            158,159,163,164,171,172),c.scenes.keys)
        assertEquals("opening-segment-001-c61",c.scene.version)
        assertJiangFrozenCapabilities(c)
    }
    /** Retained operative lookups, missing-rule rejection and medical/save guards. */
    private fun assertJiangFrozenCapabilities(c:Content){
        assertEquals(setOf("xiaolongnv","yangjian","jiangziya"),c.joinCharacters.keys)
        for(id in listOf(7,44))assertEquals(51,c.battle!!.physicalRules!!.weaponHitThreshold[id])
        // A self-consistent package hash must not conceal a missing operative
        // lookup that would otherwise crash only when its actor executes.
        val original=AssetSource(instrumentation.targetContext.assets)
        for(id in listOf(7,44)){
            val combat=JSONObject(original.read("combat.json").toString(Charsets.UTF_8))
            combat.getJSONObject("physicalRules").getJSONObject("weaponHitThreshold").remove(id.toString())
            val bytes=combat.toString().toByteArray(Charsets.UTF_8)
            val manifest=JSONObject(original.read("manifest.json").toString(Charsets.UTF_8))
            manifest.getJSONObject("files").put("combat.json",java.security.MessageDigest.getInstance("SHA-256")
                .digest(bytes).joinToString(""){"%02x".format(it)})
            val fake=object:ContentSource{override fun read(name:String)=when(name){
                "combat.json"->bytes;"manifest.json"->manifest.toString().toByteArray(Charsets.UTF_8);else->original.read(name)}}
            val failure=runCatching{ContentLoader.load(fake)}.exceptionOrNull()
            assertNotNull(failure);assertTrue(failure!!.message.orEmpty().contains("weapon hit lookup"))
        }
        // An enabled c61 invitation cannot be packaged without its combat rules.
        // Legacy compatibility below removes the later feature, not this guard.
        val noCombatManifest=JSONObject(original.read("manifest.json").toString(Charsets.UTF_8))
        noCombatManifest.getJSONObject("files").remove("combat.json")
        val noCombat=object:ContentSource{override fun read(name:String)=if(name=="manifest.json")
            noCombatManifest.toString().toByteArray(Charsets.UTF_8) else original.read(name)}
        val noCombatFailure=runCatching{ContentLoader.load(noCombat)}.exceptionOrNull()
        assertNotNull(noCombatFailure);assertEquals("Jiang physical rules missing",noCombatFailure!!.message)
        assertEquals(listOf(1,2,3,4,5,6,8,9,10).flatMap{caller->
            listOf("rom.clinic.$caller.revival","rom.clinic.$caller.care")}.toSet(),c.clinics.keys)
        assertMedicalPartySave(c)
    }
    fun testC62Room28DependenciesAndInteriorSaveCodec(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        assertEquals("opening-segment-001-c62",c.scene.version)
        assertEquals(setOf(0,1,2,3,4,5,6,7,8,9,10,16,17,18,19,20,22,23,25,28,37,41,42,
            60,61,62,63,64,65,66,67,68,69,70,74,76,77,78,79,85,86,87,89,95,96,97,98,
            99,100,101,107,108,109,110,114,115,116,117,121,136,139,141,142,145,146,147,148,
            158,159,163,164,171,172),c.scenes.keys)
        assertJiangFrozenCapabilities(c)
        val room=c.scenes.getValue(28)
        // CONTROLLED validation of the actual failed v86 normal gift endpoint.
        // Marker admission still requires every existing scene/state/caller guard.
        val previous=SaveSnapshot("opening-segment-001-c61",114,184,360,Key.DOWN,
            listOf(c.initialPlayer),mapOf(OpeningEquipment.KNIFE_ID to 1),
            mapOf("opening.intro.seen" to true,"rom.npc.114.2" to true),money=0,encounterSteps=4)
        assertTrue(previous.validate(c));assertEquals(previous,SaveSnapshot.parse(previous.json().toString()))
        assertFalse(previous.copy(mapId=999).validate(c))
        assertFalse(previous.copy(x=185).validate(c))
        assertFalse(previous.copy(contentVersion="opening-segment-001-c63").validate(c))
        assertEquals(16,room.width);assertEquals(15,room.height)
        val enter=c.exits.single{it.fromMapId==0&&it.toMapId==28}
        val back=c.exits.single{it.fromMapId==28}
        assertEquals(12 to 23,enter.triggerX to enter.triggerY);assertEquals(6 to 10,enter.spawnX to enter.spawnY)
        assertTrue(enter.captureCaller);assertTrue(back.returnToCaller)
        assertEquals(6 to 10,back.triggerX to back.triggerY);assertEquals(12 to 23,back.spawnX to back.spawnY)
        val hint=c.npcs.single{it.id=="rom.npc.28.0"};assertTrue(hint.readOnlyDialogue)
        assertEquals("rom.dialogue.38.12",hint.firstDialogue);assertTrue(hint.firstEffects.isEmpty())
        val hidden=c.npcs.single{it.id=="rom.npc.28.1"};assertTrue(hidden.hiddenInvestigation)
        assertEquals(HerbUse.ID,hidden.treasure!!.itemId);assertEquals(0,hidden.treasure.categoryGrant)
        val saved=SaveSnapshot(c.scene.version,28,104,120,Key.UP,listOf(c.initialPlayer),
            mapOf(HerbUse.ID to 1),mapOf("rom.map.28.flag.1" to true),money=321,
            encounterSteps=17,interiorContext=InteriorContext(0,12,23))
        assertTrue(saved.validate(c));assertEquals(saved,SaveSnapshot.parse(saved.json().toString()))
        val world=World(c.scenes,c.exits,28)
        assertTrue(world.tryRestore(28,saved.x,saved.y,0,saved.direction,saved.interiorContext,0))
        assertEquals(saved.interiorContext,world.interiorContext)
        assertFalse(WorldItems.openTreasure(saved,hidden.treasure,c.itemDefinitions.getValue(HerbUse.ID)).applied)
    }
    /** Loaded c61 source-cell edges and durable event codec; no normal mainline claim. */
    fun testControlledJiangInvitationCodecAndDepartureBoundaries(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val rule=c.jiangJoin!!;val jiang=c.joinCharacters.getValue("jiangziya")
        val before=SaveSnapshot(c.scene.version,121,23*16+8,13*16+8,Key.UP,
            listOf(c.initialPlayer,c.joinCharacters.getValue("xiaolongnv"),c.joinCharacters.getValue("yangjian")),
            mapOf(OriginalYangJoin.ITEM_ID to 1,HerbUse.ID to 7),mapOf(OriginalYangJoin.CONTEXT_FLAG to true,
                OriginalYangJoin.USED_FLAG to true,OriginalSceneItems.PLAGUE_FLAG to true,
                OriginalJiangJoin.PANXI_FLAG to true),money=8342,encounterSteps=113)
        assertTrue(before.validate(c))
        var result=OriginalJiangJoin.begin(before,rule)
        for(message in rule.continuation.dialogueIds){
            val cold=SaveSnapshot.parse(result.snapshot.json().toString())
            assertEquals(result.snapshot,cold);assertTrue(cold.validate(c));assertTrue(rule.validPending(cold))
            val bad=OriginalJiangJoin.advance(cold,rule,"wrong.dialogue",jiang)
            assertFalse(bad.applied);assertEquals(cold,bad.snapshot)
            result=OriginalJiangJoin.advance(cold,rule,message,jiang);assertTrue(result.applied)
        }
        assertEquals(OriginalJiangJoin.FULL_PARTY,result.snapshot.characters.map{it.id})
        assertEquals(CharacterState("jiangziya",38,190000,1608,1608,151,235,109,63,124,
            maxMp=151,equipment=EquipmentState(44,-1,24,28)),result.snapshot.characters.last())
        assertEquals(before.inventory,result.snapshot.inventory);assertEquals(before.money,result.snapshot.money)
        assertTrue(result.snapshot.validate(c));assertEquals(result.snapshot,SaveSnapshot.parse(result.snapshot.json().toString()))
        assertFalse(OriginalJiangJoin.advance(result.snapshot,rule,rule.continuation.dialogueIds.last(),jiang).applied)
        val departures=c.exits.filter{it.fromMapId==142&&it.toMapId==16}
        assertEquals((13..17).map{it to 42},departures.map{it.triggerX to it.triggerY})
        for(x in 13..17){
            val w=World(c.scenes,c.exits,142)
            assertTrue(w.tryRestore(142,x*16+8,42*16+8,0,Key.DOWN))
            w.tick(Key.DOWN);assertEquals(16,w.mapId)
            assertEquals(42*16+8,w.x);assertEquals(78*16+8,w.y);assertEquals(Key.DOWN,w.direction)
            assertTrue(departures.single{it.triggerX==x}.resetEncounterSteps)
        }
        assertFalse(c.exits.any{it.fromMapId==142&&it.triggerX==3&&it.triggerY==10})
        assertFalse(c.exits.any{it.fromMapId==121&&it.triggerX==1&&it.triggerY==29})
        val landing=World(c.scenes,c.exits,142)
        assertTrue(landing.tryRestore(142,15*16+8,43*16+8,0,Key.DOWN))
        landing.tick(Key.DOWN);assertEquals(142,landing.mapId) // Tested failed native pose; no auto-bounce.
    }
    private fun assertMedicalPartySave(c:Content){
        val room=c.scenes.getValue(20)
        assertEquals(setOf(0,2,5),room.walkableClasses)
        assertTrue(room.sourceEdges.isEmpty());assertTrue(room.targetEdges.isEmpty())
        assertEquals(2,c.npcs.count{it.mapId==20&&it.clinicId!=null})
        for(caller in listOf(1,2)){
            assertEquals(2,c.serviceBindings.count{it.callerMapId==caller&&it.interiorMapId==20})
            assertTrue(ClinicRevival.valid(c.clinics.getValue("rom.clinic.$caller.revival")))
            assertTrue(ClinicCare.valid(c.clinics.getValue("rom.clinic.$caller.care")))
        }
        val base=SaveSnapshot(c.scene.version,20,7*16+8,12*16+8,Key.DOWN,
            listOf(c.initialPlayer,c.joinCharacters.getValue("xiaolongnv").copy(hp=0,statusMask=32)),
            mapOf(HerbUse.ID to 2),mapOf("unrelated" to true),887,interiorContext=InteriorContext(2,22,26))
        assertTrue(base.validate(c))
        val result=ClinicRevival.apply(base.money,base.characters,"xiaolongnv",c.clinics.getValue("rom.clinic.2.revival"))
        assertTrue(result.applied)
        val saved=base.copy(money=result.money,characters=result.characters)
        assertTrue(saved.validate(c));assertEquals(saved,SaveSnapshot.parse(saved.json().toString()))
        assertEquals(base.characters.first(),saved.characters.first())
        assertEquals(base.inventory,saved.inventory);assertEquals(base.flags,saved.flags)
    }

    fun testRoom116OriginalEventAndPreservedVariantCapabilitiesFixture(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        assertEquals(32,c.scenes.getValue(116).width);assertEquals(15,c.scenes.getValue(116).height)
        val n=c.npcs.single{it.id=="rom.npc.116.0"};assertEquals(41,n.originalTalk!!.actionId)
        val before=SaveSnapshot(c.scene.version,116,88,72,Key.UP,listOf(c.initialPlayer),
            mapOf(HerbUse.ID to 2),emptyMap(),money=89,encounterSteps=17)
        assertTrue(before.validate(c))
        val first=OriginalNpcTalk.begin(before,n.originalTalk!!);assertTrue(first.applied);assertTrue(first.snapshot.validate(c))
        assertEquals(first.snapshot,SaveSnapshot.parse(first.snapshot.json().toString()))
        val second=OriginalNpcTalk.advanceRoom116(first.snapshot,n.originalTalk!!,first.nextDialogue!!)
        assertTrue(second.applied);assertTrue(second.snapshot.validate(c))
        assertEquals(second.snapshot,SaveSnapshot.parse(second.snapshot.json().toString()))
        val completed=OriginalNpcTalk.advanceRoom116(second.snapshot,n.originalTalk!!,second.nextDialogue!!)
        assertTrue(completed.applied);assertTrue(completed.snapshot.validate(c))
        assertEquals(before.copy(flags=completed.snapshot.flags),completed.snapshot)
        val actors=c.npcsForState(116,completed.snapshot.flags)
        val variant=actors.single{it.id==n.id};assertEquals(6 to 3,variant.x to variant.y)
        assertSame(n.originalTalk,variant.originalTalk);assertEquals(n.removedFlagId,variant.removedFlagId)
        assertEquals(16 to 5,actors.single{it.id=="rom.npc.116.3"}.let{it.x to it.y})
        val removed=completed.snapshot.flags+("rom.npccontext.116.210" to true)
        assertTrue(c.npcsForState(116,removed).none{c.npcVisible(it,removed)})
        val dynamic=c.sceneForState(116,removed)!!.dynamicObjectCells
        assertFalse(3*32+6 in dynamic);assertFalse(5*32+16 in dynamic)
        assertFalse(first.snapshot.copy(mapId=141).validate(c))
        assertTrue(before.copy(contentVersion="opening-segment-001-c49").validate(c))
    }

    /** Bundled Queen resources + controlled save round trips; not a normal win. */
    fun testQueenOriginalResourcesAndDurableHuangGiftFixture(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        for((mid,w,h)in listOf(Triple(141,32,30),Triple(115,64,45),Triple(117,16,15))){
            assertEquals(w,c.scenes.getValue(mid).width);assertEquals(h,c.scenes.getValue(mid).height)
        }
        val rules=c.battle!!;val queen=rules.enemies.getValue(157)
        assertEquals(7000,queen.hp);assertEquals(372,queen.attack);assertEquals(178,queen.defense)
        assertEquals(3000,queen.experienceReward);assertEquals(2400,queen.moneyReward)
        assertEquals(2,queen.requiredBindingMarker)
        val rope=c.itemDefinitions.getValue("rom.special.13");assertEquals("捆妖繩",rope.name)
        assertEquals(2,rope.battleBindingUse!!.bindingMarker)
        for(id in listOf(58,59,157))assertNotNull(c.enemyGraphics[id])
        val boss=rules.storyBattles.getValue("rom.npc.117.1")
        assertEquals(StoryEntryTrigger(117,7,5),boss.entryTrigger);assertEquals(0,boss.intro!!.openingMovement.completedSteps)
        assertTrue(boss.commitAfterDialogue)
        val before=SaveSnapshot(c.scene.version,117,120,72,Key.UP,listOf(c.initialPlayer),
            mapOf(rope.id to 1),mapOf("rom.map.117.flag.128" to true,"rom.npccontext.117.231" to true),money=73)
        assertTrue(before.validate(c));val npc=c.npcs.single{it.id=="rom.npc.117.0"};val item=c.itemDefinitions.getValue("rom.special.18")
        val first=OriginalNpcTalk.begin(before,npc.originalTalk!!,item);assertTrue(first.applied)
        assertTrue(first.snapshot.validate(c));assertEquals(1,first.snapshot.inventory[item.id])
        val restored=SaveSnapshot.parse(first.snapshot.json().toString());assertEquals(first.snapshot,restored)
        assertTrue(restored.validate(c));assertEquals(restored,OriginalNpcTalk.begin(restored,npc.originalTalk!!,item).snapshot)
        val closed=OriginalNpcTalk.finishHuang(restored,npc.originalTalk!!,"rom.dialogue.127.14")
        assertTrue(closed.applied);assertTrue(closed.snapshot.validate(c));assertFalse(c.npcVisible(npc,closed.snapshot.flags))
        assertEquals(first.snapshot.inventory,closed.snapshot.inventory);assertEquals(before.characters,closed.snapshot.characters)
        assertEquals(closed.snapshot,SaveSnapshot.parse(closed.snapshot.json().toString()))
        assertFalse(OriginalNpcTalk.begin(closed.snapshot,npc.originalTalk!!,item).applied)
        assertFalse(restored.copy(mapId=16).validate(c));assertTrue(before.copy(contentVersion="opening-segment-001-c48").validate(c))
        assertFalse(before.copy(contentVersion="opening-segment-001-c51").validate(c))
        val women=c.npcs.filter{it.mapId==115&&it.id.substringAfterLast('.').toInt()<5}
        assertTrue(women.all{c.npcVisible(it,emptyMap())});assertTrue(women.none{c.npcVisible(it,mapOf("rom.npccontext.115.208" to true))})
        assertEquals(7 to 10,c.npcsForState(164,mapOf("rom.npccontext.164.220" to true)).first{it.id=="rom.npc.164.0"}.let{it.x to it.y})
        assertEquals("rom.dialogue.174.0",c.npcsForState(164,mapOf("rom.npccontext.164.220" to true)).first{it.id=="rom.npc.164.0"}.firstDialogue)
    }

    /** Real loader/JSON fixtures; actual normal gift/use remains a separate recorded route. */
    fun testNightEightOriginalMapAtlasGiftAndColdSaveFixture(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        assertEquals(48,c.scenes.getValue(100).width);assertEquals(75,c.scenes.getValue(100).height)
        assertEquals(16,c.scenes.getValue(164).width);assertEquals(48,c.scenes.getValue(74).width)
        val item=c.itemDefinitions.getValue(WorldItems.NIGHT_LIGHT_ID);assertEquals("夜明珠",item.name)
        assertNotNull(item.nightLightUse);assertNull(c.itemDefinitions.getValue("rom.special.13").nightLightUse)
        val before=SaveSnapshot(c.scene.version,164,120,88,Key.UP,listOf(c.initialPlayer),emptyMap(),emptyMap(),money=123)
        assertTrue(before.validate(c));val n=c.npcs.single{it.id=="rom.npc.164.1"}
        val first=OriginalNpcTalk.begin(before,n.originalTalk!!,item);assertTrue(first.applied)
        assertEquals(1,first.snapshot.inventory[item.id]);assertTrue(first.snapshot.validate(c))
        assertEquals(first.snapshot,OriginalNpcTalk.begin(first.snapshot,n.originalTalk!!,item).snapshot)
        assertTrue(c.dialogues.getValue(first.nextDialogue!!).text.contains("夜明珠"))
        val cave=first.snapshot.copy(mapId=74,x=56,y=440);assertTrue(cave.validate(c))
        val used=WorldItems.useNightLight(cave,item,true);assertTrue(used.applied)
        val saved=cave.copy(inventory=used.inventory,flags=used.flags);assertTrue(saved.validate(c))
        assertEquals(saved,SaveSnapshot.parse(saved.json().toString()))
        val dark=c.atlasForState(74,emptyMap());val lit=c.atlasForState(74,saved.flags)
        assertEquals(256,dark.width);assertEquals(256,lit.width)
        val a=IntArray(256*256);val b=IntArray(a.size);dark.getPixels(a,0,256,0,0,256,256);lit.getPixels(b,0,256,0,0,256,256)
        assertFalse(a.contentEquals(b));assertEquals(saved,cave.copy(inventory=used.inventory,flags=used.flags))
        val reset=WorldItems.fieldFlagsAfterStep(saved.flags,CompletedStep(74,3,28,true))
        assertFalse(WorldItems.NIGHT_LIGHT_FLAG in reset);assertEquals(true,reset[WorldItems.NIGHT_LIGHT_USED_FLAG])
        assertSame(dark,c.atlasForState(74,reset))
        val oldVillage=cave.copy(mapId=6,x=248,y=456,contentVersion="opening-segment-001-c47")
        assertTrue(oldVillage.validate(c));assertEquals(oldVillage,SaveSnapshot.parse(oldVillage.json().toString()))
        for(id in listOf(52,53,56,57))assertNotNull(c.enemyGraphics[id])
        val chest=c.npcs.single{it.id=="rom.npc.74.5"};assertEquals("rom.special.13",chest.treasure!!.itemId)
        val grant=WorldItems.openTreasure(saved,chest.treasure!!,c.itemDefinitions.getValue("rom.special.13"));assertTrue(grant.applied)
        val after=saved.copy(inventory=grant.inventory,flags=grant.flags);assertTrue(after.validate(c))
        assertEquals(after,SaveSnapshot.parse(after.json().toString()));assertFalse(WorldItems.openTreasure(after,chest.treasure!!,c.itemDefinitions.getValue("rom.special.13")).applied)
    }

    /** Real bundled loader/JSON fixtures; separate from normal route recording. */
    fun testVillageSixOriginalServicesTalkAndHiddenSaveRoundTrip(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val scene=c.scenes.getValue(6);assertEquals(32,scene.width);assertEquals(30,scene.height)
        val girls=c.npcs.filter{it.mapId==6};assertEquals(8,girls.size)
        for((room,ids)in listOf(17 to listOf(9,24,36),18 to listOf(5,13,19,31,39),19 to listOf(1,2,4,6,7,10,12))){
            assertEquals(ids,c.shops.getValue("rom.shop.6.$room").items.map{it.substringAfterLast('.').toInt()})
        }
        assertEquals(200,c.inns.getValue("rom.inn.6").price)
        val hidden=girls.single{it.id=="rom.npc.6.3"};assertTrue(hidden.hiddenInvestigation)
        assertEquals("rom.medicine.1",hidden.treasure!!.itemId);assertEquals("rom.map.6.flag.8",hidden.treasure!!.flagId)
        val hero=c.initialPlayer;val girl=c.joinCharacters.getValue("xiaolongnv").copy(statusMask=64)
        val yang=c.joinCharacters.getValue("yangjian")
        val start=SaveSnapshot(c.scene.version,6,15*16+8,28*16+8,Key.UP,listOf(hero,girl,yang),
            mapOf(OriginalYangJoin.ITEM_ID to 1),mapOf("rom.map.110.flag.128" to true,
                OriginalYangJoin.CONTEXT_FLAG to true,OriginalYangJoin.USED_FLAG to true),money=1000)
        assertTrue(start.validate(c));assertTrue(start.copy(contentVersion="opening-segment-001-c46").validate(c))
        val npc=girls.single{it.id=="rom.npc.6.2"};val rule=npc.originalTalk!!
        val first=OriginalNpcTalk.begin(start,rule);assertEquals(start,first.snapshot)
        assertEquals("rom.dialogue.16.9",first.nextDialogue)
        assertTrue(c.dialogues.getValue(first.nextDialogue!!).text.contains("捆妖繩"))
        val witnessed=start.copy(flags=start.flags+(rule.witnessFlagId to true))
        val result=OriginalNpcTalk.begin(witnessed,rule)
        assertEquals("rom.dialogue.16.10",result.nextDialogue);assertTrue(result.snapshot.validate(c))
        assertEquals(result.snapshot,SaveSnapshot.parse(result.snapshot.json().toString()))
        assertEquals(start.characters,result.snapshot.characters);assertEquals(start.inventory,result.snapshot.inventory)
        assertEquals(start.money,result.snapshot.money)
        val grant=WorldItems.openTreasure(result.snapshot,hidden.treasure!!,c.itemDefinitions.getValue("rom.medicine.1"))
        assertTrue(grant.applied);val after=result.snapshot.copy(flags=grant.flags,inventory=grant.inventory)
        assertTrue(after.validate(c));assertEquals(after,SaveSnapshot.parse(after.json().toString()))
        assertFalse(WorldItems.openTreasure(after,hidden.treasure!!,c.itemDefinitions.getValue("rom.medicine.1")).applied)
        assertTrue(5*32+18 in scene.dynamicObjectCells)
    }
    /** Scoped loader/save fixtures; these do not claim a normal flower Boss win. */
    fun testCave87ActualEventSixDepartureAndMoneyChestLoader(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val scene=c.scenes.getValue(87);assertEquals(32,scene.width);assertEquals(15,scene.height)
        val boss=c.battle!!.storyBattles.getValue("rom.npc.87.0");val chain=boss.continuation!!
        assertEquals(StoryEntryTrigger(87,1,7),boss.entryTrigger)
        assertEquals((11..17).map{"rom.dialogue.97.$it"},chain.dialogueIds)
        assertEquals("xiaolongnv",chain.departureCharacterId);assertEquals(setOf(3,5),chain.movementsBeforeDialogue.keys)
        val girl=c.joinCharacters.getValue("xiaolongnv");val yang=c.joinCharacters.getValue("yangjian")
        val before=SaveSnapshot(c.scene.version,87,1*16+8,7*16+8,Key.UP,
            listOf(c.initialPlayer,girl,yang),mapOf(HerbUse.ID to 8,OriginalYangJoin.ITEM_ID to 1),
            mapOf("rom.map.110.flag.128" to true,OriginalYangJoin.CONTEXT_FLAG to true,OriginalYangJoin.USED_FLAG to true),money=5000)
        assertTrue(before.validate(c))
        var s=StoryFollowup.approachBattle(before,boss).snapshot
        assertTrue(s.validate(c));assertEquals(s,SaveSnapshot.parse(s.json().toString()))
        // Isolated already-won fixture, not normal player victory or reward.
        s=s.copy(flags=boss.rewardFlags(s.flags));assertTrue(s.validate(c))
        for(id in chain.dialogueIds){
            s=StoryFollowup.advance(s,boss,id,c.joinCharacters).snapshot
            assertTrue(s.validate(c));assertEquals(s,SaveSnapshot.parse(s.json().toString()))
        }
        assertEquals(girl.copy(statusMask=girl.statusMask or 64),s.characters[1])
        assertEquals(listOf("nezha","yangjian"),OriginalPartyRules.battleCharacters(s.characters).map{it.id})
        assertEquals(before.inventory,s.inventory);assertEquals(before.money,s.money)
        assertFalse(c.npcsForState(87,s.flags).any{it.id=="rom.npc.87.0"})
        val chest=c.npcs.single{it.id=="rom.npc.87.4"}.moneyTreasure!!
        val grant=WorldItems.openMoneyTreasure(s,chest);assertTrue(grant.applied)
        assertEquals(s.money+550,grant.snapshot.money);assertTrue(grant.snapshot.validate(c))
        assertFalse(WorldItems.openMoneyTreasure(grant.snapshot,chest).applied)
        assertEquals("rom.medicine.0",c.npcs.single{it.id=="rom.npc.87.6"}.treasure!!.itemId)
    }
    /** Controlled loader/gift/scheduler fixture, not a normal route recording. */
    fun testFiveDragonOriginalGiftAndReusableBattleItem(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        assertEquals(48,c.scenes.getValue(99).width);assertEquals(75,c.scenes.getValue(99).height)
        assertEquals(16,c.scenes.getValue(163).width)
        val teacher=c.npcs.single{it.id=="rom.npc.163.1"}
        val gate=OriginalNpcTalk.flagsAfterMapLoad(79,3,emptyMap())
        val baseRoom=c.sceneForState(163,emptyMap())!!;val openRoom=c.sceneForState(163,gate)!!
        assertTrue(10*16+7 in baseRoom.dynamicObjectCells);assertFalse(10*16+7 in openRoom.dynamicObjectCells)
        assertTrue(9*16+7 in openRoom.dynamicObjectCells)
        assertEquals(baseRoom.collision.toList(),openRoom.collision.toList())
        assertEquals(baseRoom.enabled,openRoom.enabled)
        val disciple=c.npcsForState(163,gate).single{it.id=="rom.npc.163.0"}
        assertEquals(7 to 9,disciple.x to disciple.y);assertEquals("rom.dialogue.173.1",disciple.firstDialogue)
        assertEquals(10,c.npcsForState(163,emptyMap()).single{it.id==disciple.id}.y)
        assertEquals(7 to 5,teacher.interactionCell);assertEquals(Key.UP,teacher.interactionDirection)
        val item=c.itemDefinitions.getValue("rom.special.9")
        assertEquals(9,item.originalId);assertEquals(1,item.maxCount)
        assertNotNull(item.battleBindingUse);assertNull(item.herbUse);assertNull(item.buyPrice)
        val initial=SaveSnapshot(c.scene.version,163,7*16+8,5*16+8,Key.UP,
            listOf(c.initialPlayer),emptyMap(),money=100)
        assertTrue(initial.validate(c))
        val gift=OriginalNpcTalk.begin(initial,teacher.originalTalk!!,item)
        assertTrue(gift.applied);assertEquals(1,gift.snapshot.inventory[item.id])
        assertEquals(initial.money,gift.snapshot.money);assertEquals(initial.characters,gift.snapshot.characters)
        assertTrue(gift.snapshot.validate(c));assertEquals(gift.snapshot,SaveSnapshot.parse(gift.snapshot.json().toString()))
        val repeat=OriginalNpcTalk.begin(gift.snapshot,teacher.originalTalk!!,item)
        assertEquals("rom.dialogue.173.3",repeat.nextDialogue);assertEquals(gift.snapshot,repeat.snapshot)
        val boss=c.battle!!.storyBattles.getValue("rom.npc.76.0")
        val fight=OpeningBattle(boss.group,c.battle!!,c.initialPlayer.copy(hp=1000,maxHp=1000),0,0)
        assertTrue(fight.bindingAvailable(1,item));assertFalse(fight.bindingAvailable(0,item))
        assertNotNull(fight.useBinding(1,item,false){0})
        assertEquals(gift.snapshot.inventory,fight.inventoryAfterBattle(gift.snapshot.inventory))
    }
    /** Bundled event7/load/save fixtures; actual island play is separately recorded. */
    fun testIslandCompositeIntroChestAndOnceFinalization(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        for(mid in listOf(76,77,78))assertEquals(16,c.scenes.getValue(mid).width)
        val boss=c.battle!!.storyBattles.getValue("rom.npc.76.0");val intro=boss.intro!!
        assertEquals(listOf(0,2,4,6),boss.group.members.map{it.slot})
        assertEquals(listOf(152,153,154,155),boss.group.members.map{it.enemyId})
        assertTrue(boss.finalizeWithoutDialogue)
        val hero=c.initialPlayer.copy(hp=100,maxHp=100,statusMask=OriginalStatus.POISON)
        val old=SaveSnapshot("opening-segment-001-c41",76,12*16+8,12*16+8,Key.UP,
            listOf(hero),mapOf(HerbUse.ID to 4),money=1019,encounterSteps=1)
        assertTrue(old.validate(c));var pending=StoryFollowup.begin(old,intro).snapshot
        assertEquals(96,pending.characters.single().hp);assertEquals(5,pending.encounterSteps)
        assertEquals(9,pending.x/16);assertEquals(11,pending.y/16)
        for(id in intro.continuation.dialogueIds){
            assertTrue(pending.validate(c));pending=SaveSnapshot.parse(pending.json().toString())
            assertEquals(id,intro.pendingDialogue(pending.flags))
            pending=StoryFollowup.advance(pending,intro,id).snapshot
        }
        assertTrue(pending.validate(c));assertTrue(boss.triggersAt(76,9,11,pending.flags))
        val final=pending.copy(flags=boss.rewardFlags(pending.flags))
        assertTrue(final.validate(c));assertFalse(boss.triggersAt(76,12,12,final.flags))
        assertFalse(final.flags[boss.pendingFlag]==true)
        assertEquals(final.flags,boss.rewardFlags(final.flags))
        val beforeScene=c.sceneForState(76,emptyMap())!!;val afterScene=c.sceneForState(76,final.flags)!!
        for(npc in c.npcs.filter{it.mapId==76&&it.automaticStoryOnly}){
            assertTrue(c.npcVisible(npc,emptyMap()));assertFalse(c.npcVisible(npc,final.flags))
            assertNotNull(beforeScene.check(npc.x,npc.y));assertNull(afterScene.check(npc.x,npc.y))
        }
        val money=c.npcs.single{it.id=="rom.npc.76.6"}.moneyTreasure!!
        val opened=WorldItems.openMoneyTreasure(final,money)
        assertTrue(opened.applied);assertEquals(final.money+100,opened.snapshot.money)
        assertTrue(opened.snapshot.validate(c));assertEquals(opened.snapshot,SaveSnapshot.parse(opened.snapshot.json().toString()))
        assertEquals(opened.snapshot,WorldItems.openMoneyTreasure(opened.snapshot,money).snapshot)
        assertEquals(final.inventory,opened.snapshot.inventory);assertEquals(final.characters,opened.snapshot.characters)
    }
    /** Isolated original contact/poison snapshots; not a normal-route recording. */
    fun testOriginalFerryEverySavedStageAndExactDockScope(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        assertEquals(setOf("rom.ferry.45","rom.ferry.46"),c.ferries.keys)
        val forward=c.ferries.getValue("rom.ferry.45");val reverse=c.ferries.getValue("rom.ferry.46")
        val hero=c.initialPlayer.copy(hp=100,maxHp=100,statusMask=OriginalStatus.POISON)
        val old=SaveSnapshot("opening-segment-001-c40",4,11*16+8,3*16+8,Key.LEFT,listOf(hero),emptyMap(),money=81,encounterSteps=60)
        assertTrue(old.validate(c));var current=old.copy(contentVersion=c.scene.version)
        for(rule in listOf(forward,reverse)){
            val start=OriginalFerry.begin(current,rule,rule.start.direction,c.ferries.values)
            assertTrue(start.applied);current=start.snapshot;assertTrue(current.validate(c))
            for(i in rule.legs.indices){
                current=SaveSnapshot.parse(current.json().toString());assertTrue(current.validate(c))
                val applied=OriginalFerry.advance(current,rule,i,c.ferries.values)
                assertTrue(applied.applied);assertTrue(applied.snapshot.validate(c))
                assertFalse(OriginalFerry.advance(applied.snapshot,rule,i,c.ferries.values).applied)
                assertEquals(current.money,applied.snapshot.money);assertEquals(current.inventory,applied.snapshot.inventory)
                current=applied.snapshot
            }
            assertNull(OriginalFerry.pending(current.flags,c.ferries.values))
        }
        assertEquals(64,current.characters.single().hp);assertEquals(4,current.mapId)
        assertEquals(11*16+8,current.x);assertEquals(3*16+8,current.y)
        val deadStart=old.copy(characters=listOf(hero.copy(hp=1)))
        val begin=OriginalFerry.begin(deadStart,forward,Key.LEFT,c.ferries.values)
        val dead=OriginalFerry.advance(begin.snapshot,forward,0,c.ferries.values).snapshot
        assertEquals(0,dead.characters.single().hp);assertEquals(16,dead.mapId)
        assertEquals(10*16+8,dead.x);assertEquals(3*16+8,dead.y);assertTrue(dead.validate(c))
        assertEquals(dead,SaveSnapshot.parse(dead.json().toString()))
        assertFalse(dead.copy(x=11*16+8).validate(c))
        val world=c.sceneForState(16,emptyMap())!!
        assertNull(world.check(150,135));assertNotNull(world.check(150,136))
    }
    /** Bundled map4 and isolated old save; normal service recording is separate. */
    fun testVillageFourOriginalBridgeServicesNpcRulesAndPriorSave(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets));val m=c.scenes.getValue(4)
        assertEquals(32,m.width);assertEquals(30,m.height)
        assertEquals(setOf(Key.UP,Key.DOWN),m.sourceEdges.getValue(10))
        assertEquals(setOf(Key.LEFT,Key.RIGHT),m.sourceEdges.getValue(11))
        assertFalse(m.targetEdges.containsKey(10));assertFalse(m.targetEdges.containsKey(11))
        assertNotNull(m.check(10,3));assertEquals(8,c.npcs.count{it.mapId==4})
        val seven=c.npcs.single{it.id=="rom.npc.4.7"}.originalTalk!!
        assertEquals(50,seven.actionId);assertEquals("rom.map.4.flag.64",seven.mapFlagId)
        assertEquals("rom.dialogue.14.20",seven.firstDialogue);assertEquals("rom.dialogue.14.14",seven.repeatDialogue)
        assertNull(c.npcs.single{it.id=="rom.npc.4.8"}.originalTalk)
        assertEquals("『我把船借給你們。』",c.dialogues.getValue("rom.dialogue.14.15").text)
        assertEquals(listOf(7,22,34),c.shops.getValue("rom.shop.4.17").items.map{c.itemDefinitions.getValue(it).originalId})
        assertEquals(listOf(4,12,18,30,39),c.shops.getValue("rom.shop.4.18").items.map{c.itemDefinitions.getValue(it).originalId})
        assertEquals(listOf(1,2,4,6,7,11,12),c.shops.getValue("rom.shop.4.19").items.map{c.itemDefinitions.getValue(it).originalId})
        assertEquals(90,c.inns.getValue("rom.inn.4").price)
        assertEquals(6,c.serviceBindings.count{it.callerMapId==4})
        val back=c.exits.single{it.fromMapId==4&&it.toMapId==16}
        assertEquals(15 to 29,back.triggerX to back.triggerY);assertEquals(Key.DOWN,back.edgeDirection)
        assertEquals(146 to 150,back.spawnX to back.spawnY);assertTrue(back.preserveArrivalDirection)
        val old=SaveSnapshot("opening-segment-001-c39",110,7*16+8,6*16+8,Key.LEFT,
            listOf(c.initialPlayer,c.joinCharacters.getValue("xiaolongnv"),c.joinCharacters.getValue("yangjian")),
            mapOf("rom.special.19" to 1),mapOf("rom.map.110.flag.128" to true,OriginalYangJoin.CONTEXT_FLAG to true,OriginalYangJoin.USED_FLAG to true),1019)
        assertTrue(old.validate(c));assertEquals(old,SaveSnapshot.parse(old.json().toString()))
    }

    fun testOriginalYangSignalPendingSaveCollisionAndOwnBattleContent(){
        // Isolated definition/save fixture; never a normal-route claim.
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val item=c.itemDefinitions.getValue(OriginalYangJoin.ITEM_ID);val rule=c.yangJoin()!!
        val target=c.worldItemTargets().single{it.id==rule.npcId}
        val old=SaveSnapshot(c.scene.version,110,7*16+8,6*16+8,Key.LEFT,
            listOf(c.initialPlayer,c.joinCharacters.getValue("xiaolongnv")),mapOf(item.id to 1),
            mapOf("rom.global.7c8.1" to true),money=83,encounterSteps=17)
        assertTrue(old.validate(c));assertNotNull(c.sceneForState(110,old.flags)!!.check(6,6))
        val result=OriginalYangJoin.begin(old,item,target,c.joinCharacters["yangjian"],true)
        assertTrue(result.applied);assertTrue(result.snapshot.validate(c))
        val restored=SaveSnapshot.parse(result.snapshot.json().toString())
        assertEquals(result.snapshot,restored);assertTrue(rule.validPending(restored))
        assertFalse(c.npcVisible(c.npcs.single{it.id==rule.npcId},restored.flags))
        assertNull(c.sceneForState(110,restored.flags)!!.check(6,6))
        val page=StoryFollowup.advance(restored,rule,"rom.dialogue.120.2")
        val savedPage=SaveSnapshot.parse(page.snapshot.json().toString());assertTrue(savedPage.validate(c))
        val finished=StoryFollowup.advance(savedPage,rule,"rom.dialogue.120.3")
        assertTrue(finished.applied);assertTrue(finished.snapshot.validate(c));assertEquals(17,finished.snapshot.encounterSteps)
        assertEquals(1,finished.snapshot.inventory[item.id]);assertEquals(3,finished.snapshot.characters.size)
        val actor=c.joinCharacters.getValue("yangjian")
        assertEquals(96,actor.strength);assertEquals(28,actor.agility);assertEquals(58,OriginalYangJoin.initialHandContribution(finished.snapshot,actor))
        assertEquals(79,c.battle!!.growthFor(actor.id).size);assertNotNull(c.battle!!.physicalFor(actor.id))
        assertEquals(setOf("nezha","yangjian"),c.equipmentDefinitions.getValue("rom.armor.29").allowedCharacters)
        assertFalse(c.equipmentDefinitions.getValue("rom.weapon.33").operationEnabled)
        assertNull(OpeningEquipment.unequip(actor,emptyMap(),c.equipmentDefinitions.getValue("rom.weapon.33")))
        assertFalse(OriginalYangJoin.begin(finished.snapshot,item,target,actor,true).applied)
    }

    fun testRoom171OriginalCounterAndTeacherSignalDefinition(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets));val scene=c.scenes.getValue(171)
        assertEquals(16,scene.width);assertEquals(15,scene.height);assertEquals(setOf(0,2),scene.walkableClasses)
        assertEquals(3,c.npcs.count{it.mapId==171});assertFalse(c.battle!!.zones.any{it.mapId==171})
        val npc=c.npcs.single{it.id=="rom.npc.171.1"};val rule=npc.originalTalk!!
        assertEquals(12,rule.actionId);assertEquals(7 to 5,npc.interactionCell);assertEquals(Key.UP,npc.interactionDirection)
        val item=c.itemDefinitions.getValue("rom.special.19")
        assertEquals(19,item.originalId);assertEquals("special",item.category);assertEquals(1,item.maxCount)
        assertNotNull(item.worldUse);assertEquals(130,item.worldUse!!.targetSpriteId)
        assertNotNull(item.worldUse!!.yangJoin);assertNull(item.buyPrice);assertNull(item.sellPrice)
        val old=SaveSnapshot("opening-segment-001-c37",171,7*16+8,5*16+8,Key.UP,
            listOf(c.initialPlayer),emptyMap(),mapOf("rom.global.7c8.1" to true),0)
        assertTrue(old.validate(c));val grant=OriginalNpcTalk.begin(old,rule,item)
        assertTrue(grant.applied);assertEquals(1,grant.snapshot.inventory[item.id])
        assertEquals("rom.dialogue.181.1",grant.nextDialogue);assertTrue(grant.snapshot.flags[rule.mapFlagId]!=true)
        val repeat=OriginalNpcTalk.begin(grant.snapshot,rule,item)
        assertEquals("rom.dialogue.181.2",repeat.nextDialogue);assertEquals(grant.snapshot,repeat.snapshot)
        assertEquals(old.money,repeat.snapshot.money);assertEquals(old.characters,repeat.snapshot.characters)
    }
    fun testTreeFourFloorsActorContactsFullRegionsAndYangInitialTalk(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        for(mid in 107..110){
            val scene=c.scenes.getValue(mid);assertEquals(16,scene.width);assertEquals(15,scene.height)
            assertEquals(setOf(0),scene.walkableClasses)
            val zone=c.battle!!.zones.single{it.mapId==mid};assertEquals(11,zone.groups.size)
            assertTrue(zone.highGate);assertEquals(245,zone.randomThreshold)
            assertEquals(setOf(41,42),zone.groups.flatMap{it.members}.map{it.enemyId}.toSet())
        }
        val contacts=c.exits.filter{it.contactActorId!=null};assertEquals(2,contacts.size)
        assertEquals(setOf(231,232),contacts.map{it.contactActorId}.toSet())
        for(e in contacts){assertEquals(16,e.fromMapId);assertEquals(107,e.toMapId);assertEquals(7,e.spawnX);assertEquals(14,e.spawnY)}
        val back=c.exits.single{it.fromMapId==107&&it.toMapId==16};assertEquals(169,back.spawnX);assertEquals(149,back.spawnY)
        val chest=c.npcs.filter{it.mapId in 107..108&&it.treasure!=null};assertEquals(3,chest.size)
        assertEquals(setOf("rom.weapon.7","rom.armor.30","rom.medicine.14"),chest.map{it.treasure!!.itemId}.toSet())
        val npc=c.npcs.single{it.id=="rom.npc.110.0"};assertNotNull(npc.originalTalk)
        assertEquals("rom.global.7c8.1",npc.originalTalk!!.witnessFlagId)
        assertEquals("rom.special.19",npc.originalTalk!!.itemId)
        assertTrue(c.dialogues.containsKey(npc.originalTalk!!.firstDialogue))
        val old=SaveSnapshot("opening-segment-001-c36",16,238*16+8,160*16+8,Key.UP,listOf(c.initialPlayer),emptyMap(),emptyMap(),93)
        assertTrue(old.validate(c));assertEquals(old,SaveSnapshot.parse(old.json().toString()))
    }
    fun testForest101OriginalFootEdgesCompleteRegionAndRealReturn(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets));val s=c.scenes.getValue(101)
        assertEquals(setOf(0,3,7,8,9),s.walkableClasses)
        assertEquals(mapOf(3 to setOf(Key.LEFT,Key.RIGHT),7 to setOf(Key.LEFT,Key.RIGHT)),s.targetEdges)
        assertEquals(MovementBlock.PHYSICAL,s.probeFrom(26,19,Key.RIGHT))
        assertEquals(MovementBlock.PHYSICAL,s.probeFrom(8,51,Key.DOWN))
        assertEquals(MovementBlock.NONE,s.probeFrom(8,51,Key.UP))
        val enter=c.exits.single{it.fromMapId==16&&it.triggerX==213&&it.triggerY==155}
        assertEquals(101,enter.toMapId);assertEquals(8,enter.spawnX);assertEquals(51,enter.spawnY)
        val back=c.exits.single{it.fromMapId==101&&it.toMapId==16}
        assertEquals(8,back.triggerX);assertEquals(51,back.triggerY);assertEquals(213,back.spawnX);assertEquals(155,back.spawnY)
        val zone=c.battle!!.zones.single{it.mapId==101}
        assertEquals(17,zone.groups.first().zoneId);assertEquals(13,zone.groups.size);assertTrue(zone.highGate);assertEquals(245,zone.randomThreshold)
        assertEquals(setOf(38,39),zone.groups.flatMap{it.members}.map{it.enemyId}.toSet())
        assertEquals(250,c.battle!!.enemies.getValue(38).hp);assertEquals(262,c.battle!!.enemies.getValue(39).hp)
        val old=SaveSnapshot("opening-segment-001-c34",16,238*16+8,160*16+8,Key.UP,listOf(c.initialPlayer),emptyMap(),emptyMap(),93)
        assertTrue(old.validate(c));assertEquals(old,SaveSnapshot.parse(old.json().toString()))
    }
    /** Bundled original ground bridges; normal App traversal is separately verified. */
    fun testContinentFootBridgesOriginalDirectionsEncountersAndLegacySave(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets));val scene=c.scenes.getValue(16)
        assertEquals(setOf(0,2,15,16),scene.walkableClasses)
        assertEquals(MovementBlock.PHYSICAL,scene.probeFrom(222,153,Key.UP))
        val opened=c.sceneForState(16,mapOf("rom.map.16.flag.32" to true))!!
        assertEquals(MovementBlock.NONE,opened.probeFrom(222,153,Key.UP))
        assertEquals(MovementBlock.PHYSICAL,opened.probeFrom(222,152,Key.UP))
        for(x in listOf(235,234,233))assertEquals(MovementBlock.NONE,scene.probeFrom(x,159,Key.LEFT))
        assertEquals(MovementBlock.NONE,scene.probeFrom(233,159,Key.RIGHT))
        assertEquals(MovementBlock.PHYSICAL,scene.probeFrom(234,159,Key.UP))
        assertEquals(MovementBlock.PHYSICAL,scene.probeFrom(233,159,Key.DOWN))
        val zone=c.battle!!.zones.single{it.mapId==16&&it.contains(16,234,159)}
        assertEquals(19,zone.groups.size);assertEquals((0..18).toList(),zone.groups.map{it.id})
        assertEquals(setOf(35,36,37),zone.groups.flatMap{it.members}.map{it.enemyId}.toSet())
        assertEquals(16,zone.randomThreshold);assertFalse(zone.highGate)
        for((id,hp)in mapOf(35 to 184,36 to 173,37 to 221))assertEquals(hp,c.battle!!.enemies.getValue(id).hp)
        val old=SaveSnapshot("opening-segment-001-c32",16,238*16+8,160*16+8,Key.UP,
            listOf(c.initialPlayer),emptyMap(),emptyMap(),87)
        assertTrue(old.validate(c));assertEquals(old,SaveSnapshot.parse(old.json().toString()))
    }
    /** Check actual candidate geometry before a long normal east-palace recording. */
    fun testEastPreparationAndIndependentSeaEntryHaveLegalGeometry(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val scene=c.scenes.getValue(25)
        assertEquals(MovementBlock.PHYSICAL,scene.blockType(27,14))
        fun reachable(sx:Int,sy:Int,tx:Int,ty:Int):Boolean{
            val start=(sy*scene.width+sx) to 0
            val queue=java.util.ArrayDeque<Pair<Int,Int>>();queue.add(start)
            val seen=mutableSetOf(start)
            while(queue.isNotEmpty()){
                val at=queue.removeFirst();val x=at.first%scene.width;val y=at.first/scene.width
                if(x==tx&&y==ty)return true
                for((key,d)in listOf(Key.UP to (0 to -1),Key.DOWN to (0 to 1),Key.LEFT to (-1 to 0),Key.RIGHT to (1 to 0))){
                    if(scene.probeFrom(x,y,key,at.second)!=MovementBlock.NONE)continue
                    val nx=x+d.first;val ny=y+d.second
                    if((nx!=tx||ny!=ty)&&c.exits.any{it.fromMapId==25&&it.triggerX==nx&&it.triggerY==ny})continue
                    val next=(ny*scene.width+nx) to scene.terrainDecision(x,y,key,at.second).nextMode
                    if(seen.add(next))queue.add(next)
                }
            }
            return false
        }
        assertTrue("Preparation must use connected ordinary sea",reachable(26,14,39,41))
        val entry=c.exits.single{it.fromMapId==16&&it.triggerX==214&&it.triggerY==110}
        assertEquals(25,entry.toMapId);assertEquals(54,entry.spawnX);assertEquals(22,entry.spawnY)
        assertTrue("East door must reach real palace without coral bypass",reachable(entry.spawnX,entry.spawnY,49,21))
        assertFalse("West door must not be treated as east component",reachable(26,14,49,21))
    }
    /** Actual loader and isolated durable proposals; not a normal final Boss victory. */
    fun testControlledFinalHallAndRebirthDialogueSaveBoundaries(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val boss=c.battle!!.storyBattles.getValue("rom.npc.68.1")
        val enemy=c.battle!!.enemies.getValue(151)
        assertEquals(3500,enemy.hp);assertEquals(8,enemy.behaviorByte)
        val barrier=c.sceneBarriers.single{it.mapId==68}
        assertEquals(MovementBlock.PHYSICAL,c.sceneForState(68,emptyMap())!!.blockType(barrier.x,barrier.y))
        val won=boss.completeDialogue(boss.rewardFlags(emptyMap()))
        assertTrue(won["rom.map.68.flag.1"]==true);assertTrue(won["rom.map.68.flag.2"]==true)
        assertNull(c.sceneForState(68,won)!!.check(barrier.x,barrier.y))
        val exit=c.exits.single{it.fromMapId==68&&it.toMapId==86}
        assertEquals(12,exit.triggerX);assertEquals(1,exit.triggerY)
        assertEquals(12,exit.spawnX);assertEquals(5,exit.spawnY)
        val story=c.sceneStories.getValue("rom.npc.86.0")
        assertEquals((2..12).map{"rom.dialogue.96.$it"},story.continuation.dialogueIds)
        assertFalse(c.battle!!.zones.any{it.mapId==86})
        assertTrue(c.npcs.single{it.id==story.npcId}.scriptedActor)
        val arrived=SaveSnapshot(c.scene.version,86,12*16+8,5*16+8,Key.UP,
            listOf(c.initialPlayer,c.joinCharacters.getValue("xiaolongnv")),mapOf(HerbUse.ID to 2),won,887)
        assertTrue(arrived.validate(c))
        var saved=StoryFollowup.begin(arrived,story).snapshot
        assertEquals(7,saved.x/16);assertEquals(4,saved.y/16)
        for(id in story.continuation.dialogueIds){
            assertTrue(saved.validate(c));assertEquals(saved,SaveSnapshot.parse(saved.json().toString()))
            assertFalse(saved.copy(mapId=16).validate(c))
            val a=StoryFollowup.advance(saved,story,id);assertTrue(a.applied);saved=a.snapshot
            assertFalse(StoryFollowup.advance(saved,story,id).applied)
        }
        assertTrue(saved.validate(c));assertEquals(16,saved.mapId)
        assertEquals(238,saved.x/16);assertEquals(160,saved.y/16)
        assertEquals(arrived.characters,saved.characters);assertEquals(arrived.inventory,saved.inventory)
        assertEquals(arrived.money,saved.money);assertTrue(saved.flags[story.flagId]==true)
        assertTrue(saved.flags[story.pendingFlag]!=true)
        val reentered=saved.copy(mapId=86,x=12*16+8,y=5*16+8)
        assertTrue(reentered.validate(c));assertFalse(StoryFollowup.begin(reentered,story).applied)
    }
    /** Loaded candidate routes and isolated checkpoints; not normal input traversal. */
    fun testControlledSeventhSideRoomLoadingReturnsEncountersAndSave(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        for((mid,zone)in listOf(69 to 15,158 to 10,159 to 10)){
            val s=c.scenes.getValue(mid)
            assertEquals(16,s.width);assertEquals(15,s.height)
            val actual=c.battle!!.zones.single{it.mapId==mid}
            assertTrue(actual.groups.all{it.zoneId==zone});assertEquals(245,actual.randomThreshold)
            assertTrue(actual.groups.isNotEmpty())
            assertEquals(2,c.exits.count{it.fromMapId==mid&&it.toMapId==67})
            val entry=c.exits.single{it.fromMapId==67&&it.toMapId==mid&&it.spawnX==7&&it.spawnY==13}
            assertEquals(7,entry.spawnX);assertEquals(13,entry.spawnY)
            val npc=c.npcs.single{it.id=="rom.npc.$mid.1"}
            assertTrue(npc.firstDialogue.isNotBlank())
            val save=SaveSnapshot(c.scene.version,mid,7*16+8,13*16+8,Key.UP,
                listOf(c.initialPlayer,c.joinCharacters.getValue("xiaolongnv")),emptyMap(),emptyMap(),400)
            assertTrue(save.validate(c));assertEquals(save,SaveSnapshot.parse(save.json().toString()))
        }
    }
    /** Actual candidate loader plus isolated original proposals, NOT normal route evidence. */
    fun testControlledSeventhHallSceneDamageProtectionAndSave(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val scene=c.scenes.getValue(67)
        val enemy=c.battle!!.enemies.getValue(150)
        assertEquals(2500,enemy.hp);assertEquals(3,enemy.behaviorByte);assertEquals(40,enemy.iceBaseDamage)
        val item=c.itemDefinitions.getValue(WorldItems.FIELD_PROTECTION_ID)
        assertNotNull(item.fieldProtectionUse)
        val before=SaveSnapshot(c.scene.version,67,scene.spawnX*16+8,scene.spawnY*16+8,Key.UP,
            listOf(c.initialPlayer,c.joinCharacters.getValue("xiaolongnv")),emptyMap(),emptyMap(),400)
        assertTrue(before.validate(c))
        val chest=c.npcs.single{it.id=="rom.npc.67.2"}.treasure!!
        val acquired=WorldItems.openTreasure(before,chest,item)
        assertTrue(acquired.applied)
        val owned=before.copy(inventory=acquired.inventory,flags=acquired.flags)
        assertFalse(WorldItems.useFieldProtection(owned.copy(mapId=23),item,true).applied)
        assertFalse(WorldItems.useFieldProtection(owned,item,false).applied)
        val used=WorldItems.useFieldProtection(owned,item,true)
        assertTrue(used.applied);assertEquals(owned.inventory,used.inventory)
        assertTrue(used.flags[WorldItems.FIELD_PENDING_FLAG]==true)
        assertTrue(used.flags[WorldItems.FIELD_ACTIVE_FLAG]!=true)
        val flags=WorldItems.fieldFlagsAfterStep(used.flags,CompletedStep(67,15,28,false))
        assertTrue(flags[WorldItems.FIELD_ACTIVE_FLAG]==true)
        assertEquals(before.characters,OriginalStatus.step(before.characters,67,true))
        val departed=WorldItems.fieldFlagsAfterStep(flags,CompletedStep(67,15,29,true))
        assertTrue(departed[WorldItems.FIELD_ACTIVE_FLAG]==true)
        assertTrue(departed[WorldItems.FIELD_PENDING_FLAG]!=true)
        val save=owned.copy(flags=departed)
        assertTrue(save.validate(c));assertEquals(save,SaveSnapshot.parse(save.json().toString()))
        assertFalse(WorldItems.openTreasure(save,chest,item).applied)
        assertEquals(400,save.money);assertEquals(before.characters,save.characters)
    }
    /** Version admission never bypasses the actual scene or state validation. */
    fun testControlledLegacyContentVersionsKeepStateAndRejectInvalidPosition(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val s=SaveSnapshot(c.scene.version,114,c.scene.spawnX*16+8,c.scene.spawnY*16+8,Key.DOWN,
            listOf(c.initialPlayer),flags=mapOf("opening.intro.seen" to true),money=c.initialMoney)
        assertTrue(s.validate(c))
        for(i in 18..30){
            val old=s.copy(contentVersion="opening-segment-001-c$i")
            assertTrue(old.validate(c));assertEquals(old,SaveSnapshot.parse(old.json().toString()))
            assertFalse(old.copy(x=-8).validate(c))
            assertFalse(old.copy(mapId=255).validate(c))
            assertFalse(old.copy(money=-1).validate(c))
        }
        assertFalse(s.copy(contentVersion="opening-segment-001-c99").validate(c))
    }
    /** Actual candidate loader and isolated proposals; not a normal Hell route. */
    fun testControlledHallBatchGroundChestsAndIndependentRewards(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val battle=c.battle!!
        for(mid in listOf(61,62,63,64,65,66)){
            val barrier=c.sceneBarriers.single{it.mapId==mid}
            assertEquals(MovementBlock.PHYSICAL,c.sceneForState(mid,emptyMap())!!.blockType(barrier.x,barrier.y))
            val boss=c.npcs.filter{it.mapId==mid}.mapNotNull{battle.storyBattles[it.id]}.single()
            val won=boss.rewardFlags(mapOf("unrelated" to true))
            assertTrue(won[barrier.removedFlagId]==true)
            assertNull(c.sceneForState(mid,won)!!.check(barrier.x,barrier.y))
            assertEquals(won,boss.rewardFlags(won))
            val finished=boss.completeDialogue(won)
            assertFalse(finished[boss.pendingFlag]==true)
            assertEquals(finished,boss.completeDialogue(finished))
        }
        for(id in listOf("rom.npc.61.8","rom.npc.62.7","rom.npc.62.8","rom.npc.63.8","rom.npc.63.9","rom.npc.66.9")){
            val npc=c.npcs.single{it.id==id};val treasure=npc.treasure!!;val item=c.itemDefinitions.getValue(treasure.itemId)
            val scene=c.scenes.getValue(npc.mapId)
            val save=SaveSnapshot(c.scene.version,npc.mapId,scene.spawnX*16+8,scene.spawnY*16+8,Key.UP,
                listOf(c.initialPlayer,c.joinCharacters.getValue("xiaolongnv")),emptyMap(),emptyMap(),400)
            assertTrue(save.validate(c))
            val grant=WorldItems.openTreasure(save,treasure,item)
            assertTrue(grant.applied);assertEquals(1,grant.inventory[item.id]);assertTrue(grant.flags[treasure.flagId]==true)
            val after=save.copy(inventory=grant.inventory,flags=grant.flags)
            assertTrue(after.validate(c));assertEquals(after,SaveSnapshot.parse(after.json().toString()))
            val repeated=WorldItems.openTreasure(after,treasure,item)
            assertFalse(repeated.applied);assertEquals(after.inventory,repeated.inventory);assertEquals(after.flags,repeated.flags)
            assertEquals(400,after.money);assertEquals(save.characters,after.characters)
        }
    }

    /** Candidate definitions and durable flags only; not a normal second hall victory. */
    fun testControlledSecondHallSpecialAndIndependentMapFlags(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val b=c.battle!!;val boss=b.storyBattles.getValue("rom.npc.60.1")
        val e=b.enemies.getValue(143)
        assertEquals(1,e.behaviorByte);assertEquals(600,e.hp);assertEquals(15,e.specialBaseDamage);assertNull(e.iceBaseDamage)
        assertEquals(10,b.zones.single{it.mapId==60}.groups.size)
        assertEquals(MovementBlock.PHYSICAL,c.sceneForState(60,emptyMap())!!.blockType(28,22))
        val first=b.storyBattles.getValue("rom.npc.70.1").completeDialogue(b.storyBattles.getValue("rom.npc.70.1").rewardFlags(emptyMap()))
        assertFalse(boss.alreadyWon(first));assertEquals(MovementBlock.PHYSICAL,c.sceneForState(60,first)!!.blockType(28,22))
        val won=boss.rewardFlags(first);val save=SaveSnapshot(c.scene.version,60,1*16+8,28*16+8,Key.UP,
            listOf(c.initialPlayer,c.joinCharacters.getValue("xiaolongnv")),mapOf(HerbUse.ID to 2),won,380)
        assertTrue(save.validate(c));val restored=SaveSnapshot.parse(save.json().toString());assertEquals(save,restored)
        assertTrue(boss.alreadyWon(restored.flags));assertEquals("rom.dialogue.70.4",boss.pendingDialogue(restored.flags))
        assertNull(c.sceneForState(60,restored.flags)!!.check(28,22));assertEquals(won,boss.rewardFlags(won))
        val done=boss.completeDialogue(won);assertTrue(done["rom.map.70.flag.4"]==true);assertTrue(done["rom.map.60.flag.4"]==true)
        assertTrue(done[boss.pendingFlag]!=true);assertEquals(done,boss.completeDialogue(done))
    }

    /** Controlled actual c23 loader/state roundtrip; not a normal Boss victory. */
    fun testControlledFirstHallBarrierBattleAndColdFlags(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val scene=c.scenes.getValue(70);val rule=c.sceneBarriers.single{it.mapId==70}
        assertEquals(MovementBlock.PHYSICAL,c.sceneForState(70,emptyMap())!!.blockType(23,2))
        val exit=c.exits.single{it.fromMapId==70&&it.triggerX==23&&it.triggerY==2}
        assertTrue(validExitPlacement(scene,exit.triggerX,exit.triggerY,c.sceneBarriers))
        assertEquals(MovementBlock.PHYSICAL,c.sceneForState(70,mapOf("rom.map.70.flag.2" to true))!!.blockType(23,2))
        val boss=c.battle!!.storyBattles.getValue("rom.npc.70.1")
        assertEquals(142,boss.group.members.single().enemyId);assertEquals(520,c.battle.enemies.getValue(142).hp)
        val won=boss.rewardFlags(mapOf("unrelated" to true))
        val save=SaveSnapshot(c.scene.version,70,1*16+8,13*16+8,Key.UP,
            listOf(c.initialPlayer,c.joinCharacters.getValue("xiaolongnv")),mapOf(HerbUse.ID to 3),won,50)
        assertTrue(save.validate(c));val restored=SaveSnapshot.parse(save.json().toString())
        assertEquals(save,restored);assertTrue(boss.alreadyWon(restored.flags));assertEquals("rom.dialogue.80.2",boss.pendingDialogue(restored.flags))
        val current=c.sceneForState(70,restored.flags)!!;assertNull(current.check(23,2))
        assertEquals(scene.dynamicObjectCells-setOf(rule.y*scene.width+rule.x),current.dynamicObjectCells)
        val done=boss.completeDialogue(restored.flags)
        assertTrue(done["rom.map.70.flag.2"]==true);assertTrue(done["rom.map.70.flag.4"]==true);assertTrue(done["unrelated"]==true)
        assertTrue(done[boss.pendingFlag]!=true);assertEquals(done,boss.completeDialogue(done))
        val npc=c.npcs.single{it.id==boss.npcId};assertEquals(28 to 6,npc.interactionCell);assertEquals(Key.UP,npc.interactionDirection)
        assertEquals(10,c.battle.zones.single{it.mapId==70}.groups.size)
    }

    /** Bundled room and isolated state; not normal Android medical acquisition. */
    fun testControlledSharedMedicalRoomPolicyCallerAndSave(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val room=c.scenes.getValue(20)
        assertEquals(16,room.width);assertEquals(15,room.height)
        assertEquals(setOf(0,2,5),room.walkableClasses)
        assertTrue(room.sourceEdges.isEmpty());assertTrue(room.targetEdges.isEmpty())
        assertEquals(MovementBlock.NONE,room.probeFrom(12,5,Key.LEFT))
        assertEquals(MovementBlock.NONE,room.probeFrom(11,5,Key.RIGHT))
        assertEquals(MovementBlock.PHYSICAL,room.probeFrom(12,7,Key.UP))
        assertEquals(MovementBlock.PHYSICAL,room.probeFrom(13,5,Key.UP))
        assertEquals(2,c.npcs.count{it.mapId==20&&it.clinicId!=null})
        assertEquals(8,c.clinics.size)
        for(caller in listOf(1,2,3,4)){
            val bindings=c.serviceBindings.filter{it.callerMapId==caller&&it.interiorMapId==20}
            assertEquals(2,bindings.size);assertEquals(setOf("rom.clinic.$caller.revival","rom.clinic.$caller.care"),bindings.map{it.clinicId}.toSet())
            assertTrue(ClinicRevival.valid(c.clinics.getValue("rom.clinic.$caller.revival")))
            assertTrue(ClinicCare.valid(c.clinics.getValue("rom.clinic.$caller.care")))
        }
        val base=SaveSnapshot(c.scene.version,20,7*16+8,12*16+8,Key.DOWN,
            listOf(c.initialPlayer.copy(hp=minOf(5,c.initialPlayer.maxHp)),c.joinCharacters.getValue("xiaolongnv").copy(hp=0,statusMask=32)),
            mapOf(HerbUse.ID to 2),mapOf("unrelated" to true),887,
            interiorContext=InteriorContext(3,7,18))
        assertTrue(base.validate(c))
        val revived=ClinicRevival.apply(base.money,base.characters,"xiaolongnv",c.clinics.getValue("rom.clinic.3.revival"))
        assertTrue(revived.applied)
        val saved=base.copy(money=revived.money,characters=revived.characters)
        assertTrue(saved.validate(c));assertEquals(saved,SaveSnapshot.parse(saved.json().toString()))
        assertEquals(base.inventory,saved.inventory);assertEquals(base.flags,saved.flags)
        assertTrue(saved.copy(contentVersion="opening-segment-001-c31",mapId=3,x=15*16+8,y=29*16+8,interiorContext=null).validate(c))
    }

    /** Actual bundled definitions with isolated inventory; not a normal acquisition recording. */
    fun testControlledVillageThreeCallerStockPriceSlotReturnAndLegacySave(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        assertTrue(c.scenes.containsKey(3));assertEquals(10,c.npcs.count{it.mapId==3})
        assertTrue(c.npcs.filter{it.mapId==3}.all{n->n.sprite.width==16&&n.sprite.height==16&&
            (0 until 256).any{i->android.graphics.Color.alpha(n.sprite.getPixel(i%16,i/16))>0}})
        assertEquals(4,c.serviceBindings.count{it.callerMapId==3&&it.clinicId==null})
        assertEquals(2,c.serviceBindings.count{it.callerMapId==3&&it.clinicId!=null})
        assertEquals(40,c.inns.getValue("rom.inn.3").price)
        assertEquals(listOf("rom.weapon.6","rom.weapon.21","rom.weapon.33"),c.shops.getValue("rom.shop.3.17").items)
        assertFalse(c.shops.getValue("rom.shop.3.19").items.contains(HerbUse.ID))
        val edge=c.exits.single{it.fromMapId==3&&it.toMapId==16}
        assertEquals(Key.DOWN,edge.edgeDirection);assertEquals(239 to 160,edge.spawnX to edge.spawnY)
        val girl=c.joinCharacters.getValue("xiaolongnv")
        val item=c.itemDefinitions.getValue("rom.weapon.21");val bag=mapOf(item.id to 1)
        val equipment=c.equipmentDefinitions.getValue(item.id)
        assertNull(OpeningEquipment.replace(c.initialPlayer,bag,equipment,c.equipmentDefinitions.values))
        val result=OpeningEquipment.replace(girl,bag,equipment,c.equipmentDefinitions.values)!!
        assertEquals(21,result.first.equipment!!.rightHand);assertFalse(result.second.containsKey(item.id))
        assertEquals(1,result.second["rom.weapon.19"])
        val purchased=TownTrade.buy(580,emptyMap(),c.shops.getValue("rom.shop.3.17"),item)
        assertNull(purchased.error);assertEquals(0,purchased.money);assertEquals(1,purchased.inventory[item.id])
        val failed=TownTrade.buy(purchased.money,purchased.inventory,c.shops.getValue("rom.shop.3.17"),item)
        assertNotNull(failed.error);assertEquals(purchased.inventory,failed.inventory);assertEquals(0,failed.money)
        val save=SaveSnapshot(c.scene.version,3,15*16+8,29*16+8,Key.UP,
            listOf(c.initialPlayer,result.first),result.second,mapOf("unrelated" to true),100)
        assertTrue(save.validate(c));assertEquals(save,SaveSnapshot.parse(save.json().toString()))
        assertTrue(save.copy(contentVersion="opening-segment-001-c30").validate(c))
        assertFalse(save.copy(x=32*16+8).validate(c))
        val paired=c.equipmentDefinitions.getValue("rom.weapon.33")
        assertEquals(setOf("yangjian"),paired.allowedCharacters)
        assertFalse(paired.operationEnabled)
        assertNull(OpeningEquipment.replace(girl,mapOf("rom.weapon.33" to 1),paired,c.equipmentDefinitions.values))
        for(id in listOf("rom.medicine.10","rom.medicine.14")){
            assertNull(c.itemDefinitions.getValue(id).herbUse);assertNull(c.itemDefinitions.getValue(id).antidoteUse)
        }
    }
    /** Actual bundled definitions with isolated inventory; not a normal acquisition recording. */
    fun testControlledVillage2GirlEquipmentAndLegacyLootInventory(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val girl=c.joinCharacters.getValue("xiaolongnv")
        val bag=mapOf("rom.weapon.18" to 1,"rom.armor.10" to 1,"rom.medicine.7" to 9)
        val weapon=c.equipmentDefinitions.getValue("rom.weapon.18")
        assertNull(OpeningEquipment.replace(c.initialPlayer,bag,weapon,c.equipmentDefinitions.values))
        val equipped=OpeningEquipment.replace(girl,bag,weapon,c.equipmentDefinitions.values)!!
        assertEquals(18,equipped.first.equipment!!.rightHand);assertEquals(1,equipped.second["rom.weapon.19"])
        assertFalse(equipped.second.containsKey("rom.weapon.18"));assertEquals(girl,equipped.first.copy(equipment=girl.equipment))
        val armored=OpeningEquipment.replace(equipped.first,equipped.second,c.equipmentDefinitions.getValue("rom.armor.10"),c.equipmentDefinitions.values)!!
        assertEquals(10,armored.first.equipment!!.body);assertEquals(1,armored.second["rom.armor.11"])
        val save=SaveSnapshot(c.scene.version,2,30*16+8,19*16+8,Key.DOWN,
            listOf(c.initialPlayer,armored.first),armored.second,mapOf("unrelated" to true),50)
        assertTrue(save.validate(c));assertEquals(save,SaveSnapshot.parse(save.json().toString()))
        val shop=c.shops.getValue("rom.shop.2.19");val oldDrop=c.itemDefinitions.getValue("rom.medicine.7")
        val purchased=TownTrade.buy(save.money,save.inventory,shop,oldDrop)
        assertNull(purchased.error);assertEquals(35,purchased.money);assertEquals(10,purchased.inventory[oldDrop.id])
        val full=TownTrade.buy(purchased.money,purchased.inventory,shop,oldDrop)
        assertNotNull(full.error);assertEquals(purchased.inventory,full.inventory);assertEquals(purchased.money,full.money)
        val sold=TownTrade.sell(purchased.money,purchased.inventory,shop,oldDrop)
        assertNull(sold.error);assertEquals(42,sold.money);assertEquals(9,sold.inventory[oldDrop.id])
        assertNull(oldDrop.herbUse);assertNull(oldDrop.antidoteUse)
    }
    /** Bundled Hell data/loader fixture; it does not claim normal route acceptance. */
    fun testScopedHellZonesRetainEveryGroupAndSupportedStatusBehavior(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val rules=c.battle!!
        val zones=rules.zones.filter{it.mapId==23}
        assertEquals(3,zones.size)
        assertEquals(listOf(13,12,15),zones.map{it.groups.size})
        assertTrue(c.scenes.getValue(23).unavailableRegions.isEmpty())
        for(id in listOf(22,23,28,29,30)){
            val enemy=rules.enemies.getValue(id)
            assertTrue(OriginalStatus.enemySupported(enemy))
            assertNotNull(c.enemyGraphics[id]);assertNotNull(c.itemDefinitions[enemy.loot!!.itemId])
        }
        assertEquals(8,rules.enemies.getValue(29).behaviorByte)
        assertEquals(7,rules.enemies.getValue(30).behaviorByte)
        assertEquals(setOf("xiaolongnv"),c.equipmentDefinitions.getValue("rom.weapon.18").allowedCharacters)
        assertEquals("rightHand",c.equipmentDefinitions.getValue("rom.weapon.18").slot)
        assertNull(c.itemDefinitions.getValue("rom.medicine.4").herbUse)
    }
    /** Isolated persistence/collision fixture, not normal acquisition or route evidence. */
    fun testControlledReusableWorldItemRestoresRemovedObjectState(){
        val content=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val target=content.mapObjects.single{it.id=="rom.object.25.0"}.itemTarget!!
        val item=content.itemDefinitions.getValue(WorldItems.ID)
        val rule=item.worldUse!!
        // The real south neighbour is class 7 wall. Use the actual west approach;
        // this inventory fixture is not normal acquisition or route evidence.
        val before=SaveSnapshot(content.scene.version,25,46*16+8,40*16+8,Key.RIGHT,
            listOf(content.initialPlayer),mapOf(item.id to 1),mapOf("unrelated" to true))
        assertTrue("West approach must be a valid original standing cell",before.validate(content))
        assertFalse(before.copy(x=47*16+8).validate(content))
        assertEquals(MovementBlock.PHYSICAL,content.sceneForState(25,before.flags)!!.blockType(47,40))
        repeat(10){assertTrue(WorldItems.available(before,item,rule,target,true))}
        assertEquals(mapOf("unrelated" to true),before.flags)
        val result=WorldItems.use(before,item,rule,target,true)
        assertTrue(result.applied)
        val after=before.copy(inventory=result.inventory,flags=result.flags)
        val roundTrip=SaveSnapshot.parse(after.json().toString())
        assertEquals(after,roundTrip)
        assertEquals(1,roundTrip.inventory[item.id])
        assertEquals(true,roundTrip.flags[rule.usedFlagId])
        assertTrue(roundTrip.copy(x=47*16+8).validate(content))
        assertEquals(MovementBlock.NONE,content.sceneForState(25,roundTrip.flags)!!.blockType(47,40))
        // Original cached scene remains blocked: flag-dependent views do not mutate shared data.
        assertEquals(MovementBlock.PHYSICAL,content.scenes.getValue(25).blockType(47,40))
        assertEquals(MovementBlock.PHYSICAL,content.sceneForState(25,before.flags)!!.blockType(47,40))
        val duplicate=WorldItems.use(roundTrip,item,rule,target,true)
        assertFalse(duplicate.applied);assertEquals(roundTrip.inventory,duplicate.inventory);assertEquals(roundTrip.flags,duplicate.flags)
        var currentFlags=before.flags
        val world=World(content.scenes,content.exits,114)
        world.sceneResolver={id->content.sceneForState(id,currentFlags)}
        assertFalse(world.tryRestore(25,47*16+8,40*16+8,0,Key.UP))
        assertEquals(114,world.mapId)
        currentFlags=roundTrip.flags
        assertTrue(world.tryRestore(25,47*16+8,40*16+8,0,Key.UP))
        assertEquals(25,world.mapId);assertEquals(40*16+8,world.y)
    }
    /** Isolated mechanism snapshot, not normal East route evidence. */
    fun testControlledSceneMechanismSessionSerialization(){
        val base=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val cells=(17..19).flatMap{y->(12..14).map{x->SceneCellChange(x,y,if(y==17)112 else 113,102,1,0)}}
        val mechanism=SceneMechanism("rom.mechanism.95.0",95,12,21,"runtime.session.map95.mechanism0",cells)
        val grid=IntArray(32*30);val collision=IntArray(grid.size)
        for(c in cells){grid[c.y*32+c.x]=c.fromTile;collision[c.y*32+c.x]=1}
        val fixture=Scene(base.scene.version,32,30,grid,collision,collision.indices.filter{collision[it]==0}.toSet(),12,21,95)
        val content=base.copy(scenes=base.scenes+(95 to fixture)).also{it.mechanisms=listOf(mechanism)}
        val before=SaveSnapshot(base.scene.version,95,12*16+8,21*16+8,Key.DOWN,listOf(base.initialPlayer),
            mapOf("rom.medicine.0" to 2),mapOf("original" to true),30)
        assertTrue(before.validate(content))
        val after=before.copy(flags=before.flags+(mechanism.sessionFlag to true))
        val parsed=SaveSnapshot.parse(after.json().toString())
        assertEquals(after,parsed);assertEquals(before,parsed.copy(flags=before.flags))
        assertFalse(before.copy(x=12*16+8,y=17*16+8).validate(content))
        assertTrue(parsed.copy(x=12*16+8,y=17*16+8).validate(content))
        assertFalse(mechanism.triggered(95,12,21,true,parsed.flags))
        assertEquals(112,content.scenes.getValue(95).grid[17*32+12])
        assertEquals(102,content.sceneForState(95,parsed.flags)!!.grid[17*32+12])
        assertEquals(112,content.sceneForState(95,before.flags)!!.grid[17*32+12])
        assertEquals(setOf("original",mechanism.sessionFlag),parsed.flags.keys)
    }
    fun testOpeningCombatPackageExecutesEveryRomGroup(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val rules=c.battle!!
        assertEquals(16,rules.zoneMapId)
        assertEquals(19,rules.groups.size)
        // The original opening zone remains intact when later scoped zones add
        // enemy definitions to the same unified package.
        assertTrue(rules.enemies.keys.containsAll(setOf(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,137,138,139,140)))
        assertTrue(rules.groups.flatMap{it.members}.all{it.enemyId in rules.enemies})
        assertEquals(listOf(2,3),rules.groups[11].members.map{it.enemyId})
        for(group in rules.groups){
            val fight=OpeningBattle(group,rules,c.initialPlayer.copy(hp=1000,maxHp=1000),2) // Isolated group-execution fixture, not normal gameplay.
            var turns=0
            while(fight.phase==BattlePhase.TARGET && turns++<100){
                val target=fight.enemies.first{it.hp>0}
                assertNotNull(fight.attack(target.slot){0})
            }
            assertEquals("group ${group.id}",BattlePhase.VICTORY,fight.phase)
            assertNotNull(fight.settle(100))
            assertNull(fight.settle(100))
        }
    }
    fun testBundledAndDirectoryUseSameLoader(){
        val ctx=instrumentation.targetContext;val asset=AssetSource(ctx.assets);val c=ContentLoader.load(asset)
        assertEquals(32,c.scene.width);assertEquals(30,c.scene.height);assertEquals(443,c.scene.enabled.size);assertEquals(256,c.atlas.width)
        assertTrue(c.scenes.keys.containsAll(setOf(114,16,0,17,18,19,25,97))) // Historical map golden subset stays required.
        assertTrue(c.scenes.keys.contains(22));assertEquals(4,c.inns.getValue("rom.inn.0").price)
        val armor=c.atlases.getValue(18)
        assertTrue("Armor room must not repeat the v26 all-black atlas",(0 until 65536).any{i->armor.getPixel(i%256,i/256)!=android.graphics.Color.BLACK})
        assertEquals(256,c.scenes.getValue(16).width);assertEquals(181,c.scenes.getValue(16).height)
        assertEquals(32,c.scenes.getValue(0).width)
        val dir=File(ctx.cacheDir,"content-test");dir.mkdirs()
        for(name in ctx.assets.list("development")!!){File(dir,name).writeBytes(asset.read(name))}
        val cached=ContentLoader.load(DirectorySource(dir));assertEquals(c.scene.version,cached.scene.version);assertTrue(c.scene.grid.contentEquals(cached.scene.grid))
    }
    fun testTamperedContentRejected(){
        val original=AssetSource(instrumentation.targetContext.assets)
        val bad=object:ContentSource{override fun read(name:String):ByteArray{val b=original.read(name);if(name=="scene.json")b[0]=0;return b}}
        try{ContentLoader.load(bad);fail("Hash mismatch must reject content")}catch(_:IllegalArgumentException){}
    }
    fun testPreviousContentWithoutCharacterNameStillLoads(){
        val original=AssetSource(instrumentation.targetContext.assets)
        val scene=JSONObject(String(original.read("scene.json"),Charsets.UTF_8))
        scene.getJSONObject("initialPlayer").remove("name")
        scene.getJSONObject("initialPlayer").remove("portraitAsset")
        scene.getJSONObject("initialPlayer").remove("portraitSource")
        // c1 has neither scoped combat nor later automatic scene scripts/actors.
        // Keep the fixture coherent instead of retaining a script without its actor.
        scene.remove("sceneStories")
        scene.remove("originalJiangJoin") // c1 also predates the combat-dependent invitation.
        val oldNpcs=scene.getJSONArray("npcs");val c1Npcs=org.json.JSONArray()
        for(i in 0 until oldNpcs.length())if(!oldNpcs.getJSONObject(i).optBoolean("scriptedActor",false))
            c1Npcs.put(oldNpcs.getJSONObject(i))
        scene.put("npcs",c1Npcs)
        scene.put("version","opening-segment-001-c1")
        val outside=JSONObject(String(original.read("scene16.json"),Charsets.UTF_8))
            .put("version","opening-segment-001-c1")
        val village=JSONObject(String(original.read("scene0.json"),Charsets.UTF_8))
            .put("version","opening-segment-001-c1")
        val manifest=JSONObject(String(original.read("manifest.json"),Charsets.UTF_8))
            .put("version","opening-segment-001-c1")
        manifest.getJSONObject("files").remove("combat.json") // c1 predates the scoped combat package.
        val files=mutableMapOf("scene.json" to scene.toString().toByteArray(Charsets.UTF_8),
            "scene16.json" to outside.toString().toByteArray(Charsets.UTF_8),
            "scene0.json" to village.toString().toByteArray(Charsets.UTF_8))
        for(mid in contentMapIds(scene).filter{it !in setOf(114,16,0)})files["scene$mid.json"]=JSONObject(String(original.read("scene$mid.json"),Charsets.UTF_8))
            .put("version","opening-segment-001-c1").toString().toByteArray(Charsets.UTF_8)
        for((name,bytes)in files)manifest.getJSONObject("files").put(name,
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)})
        val older=object:ContentSource{override fun read(name:String)=when(name){
            "manifest.json"->manifest.toString().toByteArray(Charsets.UTF_8)
            else->files[name]?:original.read(name)
        }}
        val content=ContentLoader.load(older)
        assertEquals("nezha",content.playerNames["nezha"])
        assertNull(content.battle)
        assertNull(content.jiangJoin)
        assertTrue(content.sceneStories.isEmpty())
        assertFalse(content.npcs.any{it.scriptedActor})
    }
    private fun contentMapIds(scene:JSONObject)=scene.getJSONArray("maps").let{a->(0 until a.length()).map{a.getJSONObject(it).getInt("id")}}
    fun testScopedEastPartyAndContinuationLoadAndSerialize(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val actor=c.joinCharacters.getValue("xiaolongnv")
        assertEquals(1,c.characterDefinitions.getValue(actor.id).originalActorIndex)
        assertEquals(12,actor.level);assertEquals(2000,actor.experience);assertEquals(92,actor.hp);assertEquals(44,actor.mp)
        val rules=c.battle!!
        assertEquals(1972,rules.growthFor(actor.id).single{it.level==12}.threshold)
        assertEquals(2525,rules.growthFor(actor.id).single{it.level==13}.threshold)
        val story=rules.storyBattles.getValue("rom.npc.95.0");val continuation=story.continuation!!
        assertEquals(12,continuation.dialogueIds.size);assertEquals("rom.dialogue.105.3",continuation.dialogueIds.first())
        assertEquals("rom.dialogue.105.14",continuation.dialogueIds.last())
        val mechanism=c.mechanisms.single{it.mapId==95}
        val flags=mapOf(story.flagId to true,story.pendingFlag to true,mechanism.sessionFlag to true)
        var save=SaveSnapshot(c.scene.version,95,13*16+8,4*16+8,Key.UP,listOf(c.initialPlayer),emptyMap(),flags,100)
        for(id in continuation.dialogueIds){
            assertTrue(save.validate(c));val before=save
            val result=StoryFollowup.advance(save,story,id,c.joinCharacters);assertTrue(result.applied)
            save=SaveSnapshot.parse(result.snapshot.json().toString())
            assertEquals(before.characters.first(),save.characters.first());assertEquals(100,save.money)
            assertEquals(before.inventory,save.inventory)
            if(result.nextDialogue!=null){assertEquals(95,save.mapId);assertEquals(1,save.characters.size)}
        }
        assertTrue(save.validate(c));assertEquals(listOf("nezha","xiaolongnv"),save.characters.map{it.id})
        assertEquals(actor,save.characters[1]);assertEquals(23,save.mapId)
        assertEquals(54*16+8,save.x);assertEquals(92*16+8,save.y);assertEquals(Key.UP,save.direction)
        assertEquals(true,save.flags["rom.map.95.flag.128"]);assertTrue(save.flags[story.pendingFlag]!=true)
        assertFalse(StoryFollowup.advance(save,story,continuation.dialogueIds.last(),c.joinCharacters).applied)
        // JSON/loader/state fixture only; normal touch playback is a separate gate.
    }
    fun testOriginalOpeningExitAndCollision(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val w=World(c.scenes,c.exits,114)
        assertEquals(8*16+8,w.x);assertEquals(21*16+8,w.y)
        assertNull(c.scene.check(8,26));assertNull(c.scene.check(8,27)) // ROM class2 is passable door occlusion.
        repeat(8*8){w.tick(Key.DOWN)}
        assertEquals(16,w.mapId);assertEquals(203*16+8,w.x);assertEquals(142*16+8,w.y)
        assertEquals(Key.DOWN,w.direction)
        assertEquals(0,w.remaining)
        assertNotNull(w.scene.check(0,0)) // ROM terrain class 3 remains blocked.
        assertNull(w.scene.check(200,137)) // Normal-foot terrain beyond the old observed path is open.
    }
    fun testWorld01NormalRouteAndRepeatedRoundTrips(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val w=World(c.scenes,c.exits,114)
        fun step(key:Key){repeat(8){w.tick(key)};assertEquals(0,w.remaining)}
        repeat(8){step(Key.DOWN)}
        assertEquals(16,w.mapId)
        repeat(10){
            step(Key.UP);step(Key.DOWN);assertEquals(114,w.mapId)
            assertEquals(8*16+8,w.x);assertEquals(29*16+8,w.y)
            step(Key.UP);step(Key.DOWN);assertEquals(16,w.mapId)
            assertEquals(203*16+8,w.x);assertEquals(142*16+8,w.y)
        }
        val route=listOf(Key.UP,Key.LEFT,Key.UP,Key.UP,Key.LEFT,Key.LEFT)+
            List(9){Key.UP}+listOf(Key.RIGHT,Key.RIGHT)
        route.forEach(::step)
        assertEquals(0,w.mapId);assertEquals(8,w.x);assertEquals(15*16+8,w.y);assertEquals(Key.DOWN,w.direction)
        repeat(3){
            step(Key.UP);w.tick(Key.LEFT)
            assertEquals(16,w.mapId);assertEquals(202*16+8,w.x);assertEquals(130*16+8,w.y);assertEquals(Key.DOWN,w.direction)
            step(Key.LEFT);step(Key.RIGHT)
            assertEquals(0,w.mapId);assertEquals(8,w.x);assertEquals(15*16+8,w.y)
        }
    }
    fun testWorld01SpecialEntranceDoesNotOpenOtherUnknownTerrain(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val world=c.scenes.getValue(16)
        assertNull(world.check(202,130));assertNull(world.check(203,142))
        assertNotNull(world.check(0,0));assertNotNull(c.scenes.getValue(0).check(0,16))
        assertNotNull(c.scene.check(0,0))
    }
    fun testDevelopmentInitialCharacterFields(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val p=c.initialPlayer
        assertEquals("nezha",p.id);assertEquals(1,p.level);assertEquals(0,p.experience)
        assertEquals(20,p.hp);assertEquals(20,p.maxHp);assertEquals(0,p.mp);assertEquals(0,p.maxMp)
        assertEquals(EquipmentState(0,-1,0,28),p.equipment)
        assertEquals(8,p.strength);assertEquals(4,p.stamina);assertEquals(2,p.agility);assertEquals(4,p.spirit)
        assertEquals("哪吒",c.playerNames[p.id])
        assertEquals("小刀",c.itemNames[OpeningEquipment.KNIFE_ID])
        val definition=c.characterDefinitions.getValue(p.id)
        assertEquals("哪吒",definition.name)
        assertEquals("player-down.png",definition.portraitAsset)
        assertEquals(c.sprites.getValue(Key.DOWN),definition.portrait)
        assertEquals("GAMEPLAY_VERIFIED",c.itemDefinitions.getValue(OpeningEquipment.KNIFE_ID).source)
        assertEquals(2,c.equipmentDefinitions.getValue(OpeningEquipment.KNIFE_ID).attackBonus)
        assertNull(c.itemDefinitions.getValue(OpeningEquipment.KNIFE_ID).description)
    }
    fun testCloudSessionTokenEncryptedAndCleared(){
        val ctx=instrumentation.targetContext
        val store=CloudTokenStore(ctx)
        val session=CloudSession("test-account","test-token-value-that-is-long-enough-for-session")
        val prefs=ctx.getSharedPreferences("cloud-session",0)
        val oldCiphertext=prefs.getString("ciphertext",null);val oldIv=prefs.getString("iv",null)
        try {
            store.clear();store.save(session)
            assertEquals(session,store.load())
            assertFalse(prefs.all.values.any{it.toString().contains(session.token)})
            store.clear();assertNull(store.load())
        } finally {
            val editor=prefs.edit().clear()
            if(oldCiphertext!=null)editor.putString("ciphertext",oldCiphertext)
            if(oldIv!=null)editor.putString("iv",oldIv)
            editor.commit()
        }
    }
    fun testLiveCloudReadWhenPrivateCredentialProvided(){
        val ctx=instrumentation.targetContext
        val credential=File(ctx.filesDir,"cloud-live-password")
        if(!credential.exists()){
            Log.i("FengshenCloudTest","SKIPPED_LIVE_CLOUD_NO_PRIVATE_CREDENTIAL")
            return // Default offline suite; explicit live run supplies this ignored private file.
        }
        val proxy=Proxy(Proxy.Type.HTTP,InetSocketAddress("10.0.2.2",8888))
        val client=CloudSaveClient(proxy)
        val session=client.login("antpan",credential.readText().trim())
        val prefs=ctx.getSharedPreferences("opening-local-save",0)
        val before=prefs.all.toMap()
        var activity:MainActivity?=null
        try {
            val remote=client.fetch(session)
            assertNotNull(remote)
            assertTrue(remote!!.snapshot.validate(ContentLoader.load(AssetSource(ctx.assets))))
            assertEquals("nezha",remote.snapshot.characters.first().id)
            prefs.edit().clear().commit() // Simulate loss of local gameplay data.
            activity=instrumentation.startActivitySync(Intent(ctx,MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
            var game:GameView?=null
            for(i in 0..100){
                instrumentation.runOnMainSync{
                    game=(activity!!.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as? GameView)
                }
                if(game!=null)break
                SystemClock.sleep(50)
            }
            assertNotNull(game)
            instrumentation.runOnMainSync{assertTrue(game!!.restoreSnapshot(remote.snapshot))}
            assertEquals(remote.snapshot.mapId,game!!.world.mapId)
            assertEquals(remote.snapshot.x,game!!.world.x)
            assertEquals(remote.snapshot.y,game!!.world.y)
            assertEquals(remote.snapshot.characters.first(),game!!.currentSnapshot().characters.first())
        } finally {
            activity?.let{instrumentation.runOnMainSync{it.finish()};instrumentation.waitForIdleSync()}
            val editor=prefs.edit().clear()
            before.forEach{(key,value)->when(value){
                is String->editor.putString(key,value)
                is Int->editor.putInt(key,value)
                is Long->editor.putLong(key,value)
                is Boolean->editor.putBoolean(key,value)
                is Float->editor.putFloat(key,value)
            }}
            editor.commit()
            client.logout(session)
        }
    }
    fun testPathTraversalRejected(){try{checkedName("../scene.json");fail()}catch(_:IllegalArgumentException){}}
    fun testTownTradeFailuresAndEquipmentCycle(){
        val c=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val shop=c.shops.getValue("rom.shop.17");val knife=c.itemDefinitions.getValue("rom.item.0")
        assertEquals(0,TownTrade.buy(0,emptyMap(),shop,knife).money)
        assertTrue(TownTrade.buy(14,emptyMap(),shop,knife).inventory.isEmpty())
        val bought=TownTrade.buy(100,emptyMap(),shop,knife)
        assertEquals(85,bought.money);assertEquals(1,bought.inventory[knife.id])
        assertNotNull(TownTrade.buy(100,mapOf(knife.id to 10),shop,knife).error)
        assertEquals(107,TownTrade.sell(100,bought.inventory,shop,knife).money)
        val herb=c.itemDefinitions.getValue("rom.medicine.0");val general=c.shops.getValue("rom.shop.19")
        val sold=TownTrade.sell(74,mapOf(herb.id to 2),general,herb)
        assertEquals(81,sold.money);assertEquals(1,sold.inventory[herb.id]) // Native controller witness.
        val cloth=c.equipmentDefinitions.getValue("rom.armor.1")
        val removed=OpeningEquipment.unequip(c.initialPlayer,emptyMap(),c.equipmentDefinitions.getValue("rom.armor.0"))!!
        val equipped=OpeningEquipment.equip(removed.first,removed.second+(cloth.itemId to 1),cloth)!!
        assertEquals(1,equipped.first.equipment!!.body);assertEquals(6,cloth.defenseBonus)
        assertNull(OpeningEquipment.equip(equipped.first,equipped.second,cloth))
        val save=SaveSnapshot(c.scene.version,18,7*16+8,7*16+8,Key.UP,listOf(equipped.first),equipped.second,money=85,interiorContext=InteriorContext(0,6,19))
        assertTrue(save.validate(c));assertEquals(save,SaveSnapshot.parse(save.json().toString()))
    }
}
