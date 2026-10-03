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
        assertEquals(setOf(1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,137,138,139,140),rules.enemies.keys)
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
        // c1 has neither scoped combat nor later automatic combat actors. Keep
        // the fixture coherent instead of removing combat under a current actor.
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
