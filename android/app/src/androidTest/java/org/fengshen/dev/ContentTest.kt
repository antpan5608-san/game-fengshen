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
        val base=ContentLoader.load(AssetSource(instrumentation.targetContext.assets))
        val target=WorldObjectTarget("rom.object.25.0",25,47,40,226,"rom.map.25.flag.1","rom.map.25.flag.128")
        // c16 has no interactive whirlpool yet. This controlled fixture adds only
        // its evidenced collision cell; the reused bitmap proves no visual equivalence.
        val objectBefore=MapObject(target.id,25,47,40,base.mapObjects.first().sprite,target)
        val sea=base.scenes.getValue(25)
        val fixtureSea=sea.copy(dynamicObjectCells=sea.dynamicObjectCells+(40*sea.width+47))
        val content=base.copy(scenes=base.scenes+(25 to fixtureSea),mapObjects=base.mapObjects.filter{it.id!=target.id}+objectBefore)
        val rule=WorldItemUseDefinition(226,"rom.inventory.special.11.used")
        val item=ItemDefinition(WorldItems.ID,"定海珠",null,"GAMEPLAY_VERIFIED",category="special",originalId=11,maxCount=1,worldUse=rule)
        val before=SaveSnapshot(content.scene.version,25,47*16+8,41*16+8,Key.UP,
            listOf(content.initialPlayer),mapOf(item.id to 1),mapOf("unrelated" to true))
        assertTrue(before.validate(content))
        assertFalse(before.copy(y=40*16+8).validate(content))
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
        assertTrue(roundTrip.copy(y=40*16+8).validate(content))
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
    }
    private fun contentMapIds(scene:JSONObject)=scene.getJSONArray("maps").let{a->(0 until a.length()).map{a.getJSONObject(it).getInt("id")}}
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
