package org.fengshen.dev

import android.content.Intent
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.MotionEvent
import android.view.ViewGroup
import android.graphics.Bitmap
import java.io.File
import org.json.JSONObject

@Suppress("DEPRECATION")
class TouchTest:IsolatedGameTestCase(){
    /** CONTROLLED state, real foreground five-minute clock; no timer injection. */
    fun testControlledSaveHistoryRealForegroundFiveMinuteAutoSave(){
        val(activity,v)=launch()
        val prefs=instrumentation.targetContext.getSharedPreferences("opening-local-save",0)
        lateinit var expected:SaveSnapshot
        val started=SystemClock.elapsedRealtime()
        instrumentation.runOnMainSync{
            val scene=v.content.scenes.getValue(0)
            assertTrue(v.restoreSnapshot(v.currentSnapshot().copy(mapId=0,x=scene.spawnX*16+8,y=scene.spawnY*16+8,
                flags=v.currentSnapshot().flags+("opening.intro.seen" to true))))
            assertTrue(v.saveHistoryResult())
            // Isolated changed-state fixture: unchanged AUTO deliberately
            // coalesces with the latest manual snapshot (JVM covered).
            assertTrue(v.restoreSnapshot(v.currentSnapshot().copy(money=v.currentSnapshot().money+1)))
            expected=v.currentSnapshot()
        }
        var automatic:SaveHistoryEntry?=null
        while(SystemClock.elapsedRealtime()-started<330000&&automatic==null){
            SystemClock.sleep(500)
            automatic=SaveHistory.parse(prefs.getString(SaveHistory.KEY,null)).firstOrNull{it.kind==SaveHistoryEntry.Kind.AUTO}
        }
        assertNotNull("Actual foreground timer did not save within its bounded allowance",automatic)
        assertTrue("Auto save must not fire before five real minutes",SystemClock.elapsedRealtime()-started>=300000)
        assertEquals(expected,automatic!!.snapshot)
        assertEquals(expected,SaveSnapshot.parse(prefs.getString("saveJson",null)!!))
        screenshot(v,"world-save-history-real-five-minute-auto-controlled")
        instrumentation.runOnMainSync{activity.finish()}
    }
    /** CONTROLLED emulator fixture, not normal main-story progression. Native buttons use the player entry. */
    fun testControlledSaveHistoryManualRollbackAndActivityRestart(){
        val(activity,v)=launch();val prefs=instrumentation.targetContext.getSharedPreferences("opening-local-save",0)
        lateinit var a:SaveSnapshot
        instrumentation.runOnMainSync{
            val initial=v.currentSnapshot().copy(flags=v.currentSnapshot().flags+("opening.intro.seen" to true))
            assertTrue(v.restoreSnapshot(initial));a=v.currentSnapshot()
        }
        fun dialog()=GameView::class.java.getDeclaredField("modalDialog").apply{isAccessible=true}.get(v) as android.app.AlertDialog
        // AlertDialog posts dismissal callbacks; a new child is opened from
        // that callback, after performClick/performItemClick has returned.
        fun dialogAction(action:()->Unit){
            var failure:Throwable?=null
            instrumentation.runOnMainSync{try{action()}catch(error:Throwable){failure=error}}
            failure?.let{throw it} // Report instrumentation failures on its test thread.
            instrumentation.waitForIdleSync()
        }
        fun history(){
            // Native window dismissal/focus delivery can continue after a
            // Looper idle barrier. Do not inject focus or tap behind that window.
            var ready=false
            val deadline=SystemClock.elapsedRealtime()+5000
            while(!ready&&SystemClock.elapsedRealtime()<deadline){
                instrumentation.runOnMainSync{
                    ready=v.active&&v.focused&&v.hasWindowFocus()&&
                        GameView::class.java.getDeclaredField("modalDialog").apply{isAccessible=true}.get(v)==null
                }
                if(!ready)SystemClock.sleep(25)
            }
            assertTrue("Game window must regain real focus before reopening history",ready)
            if(v.layer==GameView.Layer.MAP)tap(v,center(layoutFor(v).buttons.getValue(Key.MENU)))
            assertEquals(GameView.Layer.MENU,v.layer);tap(v,menuPoint(v,3))
            dialogAction{val d=dialog();d.listView.performItemClick(d.listView.getChildAt(0),10,d.listView.adapter.getItemId(10))}
            var showing=false;var neutral:String?=null
            dialogAction{val d=dialog();showing=d.isShowing;neutral=d.getButton(android.app.AlertDialog.BUTTON_NEUTRAL)?.text?.toString()}
            assertTrue("History child dialog must be showing after settings dismissal",showing)
            assertEquals("手动存档",neutral)
        }
        history()
        dialogAction{dialog().getButton(android.app.AlertDialog.BUTTON_NEUTRAL).performClick()}
        val manual=SaveHistory.parse(prefs.getString(SaveHistory.KEY,null)).first()
        assertEquals(SaveHistoryEntry.Kind.MANUAL,manual.kind);assertEquals(a,manual.snapshot)
        dialogAction{dialog().getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()}
        instrumentation.runOnMainSync{v.handleBack()}
        val destination=v.content.scenes.getValue(0)
        val b=a.copy(mapId=0,x=destination.spawnX*16+8,y=destination.spawnY*16+8,money=a.money+50,
            inventory=a.inventory+(HerbUse.ID to 3),flags=a.flags+("fixture.history.flag" to true),
            characters=a.characters.map{it.copy(level=it.level+1,experience=it.experience+20)},encounterSteps=7)
        assertTrue(b.validate(v.content))
        lateinit var actualB:SaveSnapshot
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(b));actualB=v.currentSnapshot()}
        history()
        dialogAction{val d=dialog();d.listView.performItemClick(d.listView.getChildAt(0),0,d.listView.adapter.getItemId(0))}
        dialogAction{dialog().getButton(android.app.AlertDialog.BUTTON_NEGATIVE).performClick()}
        assertEquals(actualB,v.currentSnapshot());assertEquals(1,SaveHistory.parse(prefs.getString(SaveHistory.KEY,null)).size)
        history()
        dialogAction{val d=dialog();d.listView.performItemClick(d.listView.getChildAt(0),0,d.listView.adapter.getItemId(0))}
        dialogAction{dialog().getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()}
        assertEquals(a,v.currentSnapshot());assertEquals(a,SaveSnapshot.parse(prefs.getString("saveJson",null)!!))
        val entries=SaveHistory.parse(prefs.getString(SaveHistory.KEY,null));assertEquals(2,entries.size)
        assertEquals(SaveHistoryEntry.Kind.BEFORE_RESTORE,entries.first().kind);assertEquals(actualB,entries.first().snapshot)
        screenshot(v,"world-save-history-restored-controlled")
        File(instrumentation.targetContext.getExternalFilesDir(null),"world-save-history-expected-save.json").writeText(a.json().toString())
        instrumentation.runOnMainSync{activity.finish()};instrumentation.waitForIdleSync()
        val(restarted,cold)=launch();assertEquals(a,cold.currentSnapshot())
        assertEquals(entries,SaveHistory.parse(prefs.getString(SaveHistory.KEY,null)))
        instrumentation.runOnMainSync{restarted.finish()}
    }
    fun testControlledSaveHistoryCorruptionAndRetentionProtectActiveAndMigration(){
        val(activity,v)=launch();val prefs=instrumentation.targetContext.getSharedPreferences("opening-local-save",0)
        val original=v.currentSnapshot();val backup=original.json().toString()
        instrumentation.runOnMainSync{
            assertTrue(prefs.edit().putString("preContentMigration",backup).putString("preCloudRecovery",backup).commit())
            repeat(21){n->
                assertTrue(v.restoreSnapshot(original.copy(money=original.money+n)))
                assertTrue(v.saveHistoryResult())
            }
        }
        val entries=SaveHistory.parse(prefs.getString(SaveHistory.KEY,null))
        assertEquals(20,entries.size);assertEquals(original.money+20,entries.first().snapshot.money)
        assertEquals(original.money+1,entries.last().snapshot.money)
        val active=prefs.getString("saveJson",null);val current=v.currentSnapshot()
        instrumentation.runOnMainSync{
            assertTrue(prefs.edit().putString(SaveHistory.KEY,"{broken snapshot").commit())
            assertFalse(v.restoreHistoryResult(entries.last().id));assertFalse(v.saveHistoryResult())
        }
        assertEquals(active,prefs.getString("saveJson",null));assertEquals(current,v.currentSnapshot())
        assertEquals(backup,prefs.getString("preContentMigration",null));assertEquals(backup,prefs.getString("preCloudRecovery",null))
        instrumentation.runOnMainSync{
            assertTrue(prefs.edit().putString(SaveHistory.KEY,SaveHistory.encode(entries)).commit())
            val invalid=entries.first().copy(snapshot=entries.first().snapshot.copy(mapId=999))
            assertTrue(prefs.edit().putString(SaveHistory.KEY,SaveHistory.encode(listOf(invalid))).commit())
            assertFalse(v.restoreHistoryResult(invalid.id))
        }
        assertEquals(active,prefs.getString("saveJson",null));assertEquals(current,v.currentSnapshot())
        val tampered=JSONObject(SaveHistory.encode(entries));val row=tampered.getJSONArray("entries").getJSONObject(0)
        row.put("snapshotJson",JSONObject(row.getString("snapshotJson")).put("money",0).toString())
        instrumentation.runOnMainSync{
            assertTrue(prefs.edit().putString(SaveHistory.KEY,tampered.toString()).commit())
            assertFalse(v.restoreHistoryResult(entries.first().id))
        }
        assertEquals(active,prefs.getString("saveJson",null));assertEquals(current,v.currentSnapshot())
        instrumentation.runOnMainSync{activity.finish()}
    }
    /** Invoke only after the original external recorder has force-stopped this isolated App. */
    fun testSaveHistoryExternalColdStartMatchesRestoredSnapshot(){
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"world-save-history-expected-save.json")
        assertTrue(file.exists());val expected=SaveSnapshot.parse(file.readText(Charsets.UTF_8))
        val(activity,v)=launch();assertEquals(expected,v.currentSnapshot())
        val entries=SaveHistory.parse(instrumentation.targetContext.getSharedPreferences("opening-local-save",0).getString(SaveHistory.KEY,null))
        assertTrue(entries.any{it.kind==SaveHistoryEntry.Kind.BEFORE_RESTORE})
        screenshot(v,"world-save-history-external-cold-controlled")
        instrumentation.runOnMainSync{activity.finish()}
    }
    /** Bounded CI transport only: App-owned bytes, no launch or state restore. */
    fun testImportVerifiedCheckpointBytes(){
        val args=(instrumentation as android.test.InstrumentationTestRunner).arguments
        val names=setOf("north-palace","hell-village2","ferry","yang-join","runtime-storage-probe")
            .map{"world-$it-expected-save.json"}.toSet()
        val name=args.getString("checkpointName")!!;assertTrue(name in names)
        val encoded=args.getString("checkpointBytesBase64")!!;assertTrue(encoded.length<=87384)
        val bytes=android.util.Base64.decode(encoded,android.util.Base64.DEFAULT)
        assertTrue(bytes.size in 1..65536)
        fun hash(value:ByteArray)=java.security.MessageDigest.getInstance("SHA-256")
            .digest(value).joinToString(""){"%02x".format(it)}
        val expected=args.getString("checkpointSha256")!!
        assertTrue(expected.matches(Regex("[a-f0-9]{64}")));assertEquals(expected,hash(bytes))
        val ctx=instrumentation.targetContext
        assertEquals(args.getString("checkpointVersionCode")!!.toInt(),ctx.packageManager.getPackageInfo(ctx.packageName,0).versionCode)
        val content=ContentLoader.load(AssetSource(ctx.assets))
        assertEquals(args.getString("checkpointContentVersion"),content.scene.version)
        val snapshot=SaveSnapshot.parse(bytes.toString(Charsets.UTF_8))!!
        assertTrue(snapshot.validate(content))
        val root=ctx.getExternalFilesDir(null)!!.canonicalFile
        val file=File(root,name);assertEquals(root,file.canonicalFile.parentFile)
        file.writeBytes(bytes)
        assertEquals(expected,hash(file.readBytes()))
    }
    // The opening zone is a separate original root, not an entry in the later
    // zones array. A normal training driver must query both existing domains.
    private fun inExistingEncounterRegion(content:BattleContent,mapId:Int,x:Int,y:Int)=
        content.zones.any{it.contains(mapId,x,y)}||
            (mapId==content.zoneMapId&&content.zoneRects.any{it.contains(x,y)})
    private fun jiangDialogueId(v:GameView)=
        (GameView::class.java.getDeclaredField("dialogueText").apply{isAccessible=true}.get(v) as? StoryText)?.id
    private fun completeJiangMessage(v:GameView,id:String){
        assertEquals(id,jiangDialogueId(v));var pages=0
        while(v.layer==GameView.Layer.DIALOGUE&&jiangDialogueId(v)==id){
            assertTrue("Bounded original Jiang dialogue pages",pages++<32)
            tap(v,Pair(v.width*.5f,v.height*.5f))
        }
    }
    /** Explicit isolated three-actor fixture, actual touch join, saved mid-dialogue. */
    fun testControlledJiangInvitationPendingAtColdBoundary(){
        val(activity,v)=launch();val c=v.content;val rule=c.jiangJoin!!
        verifyControlledPanxiOwnedDialogue(v)
        val source=SaveSnapshot(c.scene.version,121,23*16+8,13*16+8,Key.UP,
            listOf(c.initialPlayer,c.joinCharacters.getValue("xiaolongnv"),c.joinCharacters.getValue("yangjian")),
            mapOf(OriginalYangJoin.ITEM_ID to 1,HerbUse.ID to 7),mapOf("opening.intro.seen" to true,
                OriginalYangJoin.CONTEXT_FLAG to true,OriginalYangJoin.USED_FLAG to true,
                OriginalSceneItems.PLAGUE_FLAG to true,OriginalJiangJoin.PANXI_FLAG to true),money=8342,encounterSteps=113)
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(source));v.persistState()}
        val before=v.currentSnapshot();tapMapActor(v,c.npcs.single{it.id==rule.kingNpcId})
        assertEquals(GameView.Layer.DIALOGUE,v.layer);assertEquals("rom.dialogue.131.16",jiangDialogueId(v))
        val pending=v.currentSnapshot();assertEquals(before.characters,pending.characters)
        val middle=Pair(v.width*.5f,v.height*.5f)
        send(v,MotionEvent.ACTION_DOWN,listOf(middle));send(v,MotionEvent.ACTION_CANCEL,listOf(middle))
        send(v,MotionEvent.ACTION_UP,listOf(middle));assertEquals(pending,v.currentSnapshot())
        instrumentation.runOnMainSync{v.handleBack()};assertEquals(pending,v.currentSnapshot())
        assertEquals(pending,v.currentSnapshot())
        assertEquals(GameView.Layer.DIALOGUE,v.layer)
        completeJiangMessage(v,"rom.dialogue.131.16")
        val joined=v.currentSnapshot();assertEquals(OriginalJiangJoin.FULL_PARTY,joined.characters.map{it.id})
        assertEquals(c.joinCharacters.getValue("jiangziya"),joined.characters.last())
        assertEquals(before.characters,joined.characters.take(3));assertEquals(before.money,joined.money)
        assertEquals(before.inventory,joined.inventory);assertEquals(7,joined.mapId)
        assertEquals("rom.dialogue.17.12",jiangDialogueId(v));assertTrue(rule.validPending(joined))
        send(v,MotionEvent.ACTION_UP,listOf(middle));assertEquals(joined,v.currentSnapshot())
        val out=File(instrumentation.targetContext.getExternalFilesDir(null),"world-jiang-pending-expected-save.json")
        out.writeText(joined.json().toString());screenshot(v,"world-jiang-controlled-four-actor-pending-cold-boundary")
        instrumentation.runOnMainSync{v.persistState();activity.finish()}
    }
    private fun verifyControlledPanxiOwnedDialogue(v:GameView){
        // Explicit fixture; real actor tap and page completion, not a normal route.
        val c=v.content;val npc=c.npcs.single{it.id=="rom.npc.7.4"};val scene=c.scenes.getValue(7)
        val pose=listOf(Triple(npc.x,npc.y-1,Key.DOWN),Triple(npc.x,npc.y+1,Key.UP),
            Triple(npc.x-1,npc.y,Key.RIGHT),Triple(npc.x+1,npc.y,Key.LEFT)).first{(x,y,direction)->
                scene.check(x,y)==null&&(npc.interactionDirection==null||npc.interactionDirection==direction)&&
                    c.npcs.none{it.mapId==7&&it.x==x&&it.y==y}}
        val s=SaveSnapshot(c.scene.version,7,pose.first*16+8,pose.second*16+8,pose.third,
            listOf(c.initialPlayer,c.joinCharacters.getValue("xiaolongnv"),c.joinCharacters.getValue("yangjian")),
            mapOf(OriginalYangJoin.ITEM_ID to 1,HerbUse.ID to 7),mapOf("opening.intro.seen" to true,
                OriginalYangJoin.CONTEXT_FLAG to true,OriginalYangJoin.USED_FLAG to true,
                OriginalSceneItems.PLAGUE_FLAG to true),money=8342,encounterSteps=113)
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(s))}
        val before=v.currentSnapshot();tapMapActor(v,npc)
        val pending=v.currentSnapshot();assertEquals(GameView.Layer.DIALOGUE,v.layer)
        assertEquals("rom.dialogue.17.13",jiangDialogueId(v))
        instrumentation.runOnMainSync{v.handleBack()};assertEquals(pending,v.currentSnapshot())
        assertEquals(GameView.Layer.DIALOGUE,v.layer)
        completeJiangMessage(v,"rom.dialogue.17.13");completeJiangMessage(v,"rom.dialogue.17.6")
        assertEquals(GameView.Layer.MAP,v.layer)
        assertEquals(before.copy(flags=before.flags+(OriginalJiangJoin.PANXI_FLAG to true)),v.currentSnapshot())
        screenshot(v,"world-jiang-controlled-panxi-owned-pages-no-resource-change")
    }
    /** Recorder launches after real external force-stop; resume exact pending data. */
    fun testJiangExternalColdStartMatchesPendingAndCompletesOnce(){
        val expected=SaveSnapshot.parse(File(instrumentation.targetContext.getExternalFilesDir(null),
            "world-jiang-pending-expected-save.json").readText())
        val(activity,v)=launch(false);val rule=v.content.jiangJoin!!
        assertEquals(expected,v.currentSnapshot());assertEquals(GameView.Layer.DIALOGUE,v.layer)
        assertEquals("rom.dialogue.17.12",jiangDialogueId(v));screenshot(v,"world-jiang-controlled-cold-exact-pending")
        instrumentation.runOnMainSync{v.handleBack()}
        assertEquals(expected,v.currentSnapshot());assertEquals(GameView.Layer.DIALOGUE,v.layer)
        assertEquals("rom.dialogue.17.12",jiangDialogueId(v)) // Pending pose cannot become movable/unsavable.
        for(id in rule.continuation.dialogueIds.drop(1))completeJiangMessage(v,id)
        val after=v.currentSnapshot();assertEquals(GameView.Layer.MAP,v.layer)
        assertEquals(expected.characters,after.characters);assertEquals(expected.money,after.money)
        assertEquals(expected.inventory,after.inventory);assertTrue(after.flags["rom.map.7.flag.128"]==true)
        assertTrue(after.flags[rule.pendingFlag]!=true)
        send(v,MotionEvent.ACTION_UP,listOf(Pair(v.width*.5f,v.height*.5f)));assertEquals(after,v.currentSnapshot())
        assertEquals(after,SaveSnapshot.parse(instrumentation.targetContext.getSharedPreferences("opening-local-save",0)
            .getString("saveJson",null)!!));screenshot(v,"world-jiang-controlled-cold-complete-no-double-join")
        verifyControlledJiangFourActorBattle(v)
        instrumentation.runOnMainSync{activity.finish()}
    }
    private fun verifyControlledJiangFourActorBattle(v:GameView,evidenceSuffix:String=""){
        // Explicit battle fixture, using the real production party constructor
        // and native loaded stats/owner tables; not a normal map encounter claim.
        val before=v.currentSnapshot();val rules=v.content.battle!!;val group=rules.groups.first()
        var fight:OpeningBattle?=null;val p=BattlePresentation()
        val rng=object:java.security.SecureRandom(){var draws=0
            override fun nextInt(bound:Int):Int {draws++;return super.nextInt(bound)}}
        instrumentation.runOnMainSync{
            fight=GameView::class.java.getDeclaredMethod("createPartyBattle",EncounterGroup::class.java,BattleContent::class.java)
                .apply{isAccessible=true}.invoke(v,group,rules) as OpeningBattle
            fun field(name:String,value:Any?){GameView::class.java.getDeclaredField(name).apply{isAccessible=true}.set(v,value)}
            field("battle",fight);field("battlePresentation",p);field("battleID","controlled-jiang-four-party")
            field("battleRandom",rng)
            field("battleCommitted",false);field("storyBattle",null);field("layer",GameView.Layer.BATTLE)
            v.input.clear();p.tick(400)
        }
        assertEquals(before.characters,fight!!.party);assertEquals(4,fight!!.party.size)
        screenshot(v,"world-jiang-controlled-four-actor-battle-ready"+evidenceSuffix)
        for(hero in fight!!.party){
            tap(v,center(v.battlePartyCardBounds(hero.id)))
            hardwareButton(v,android.view.KeyEvent.KEYCODE_BUTTON_A)
            assertEquals(BattlePresentation.Screen.COMMAND,p.screen)
            assertEquals(hero.id,GameView::class.java.getDeclaredField("battleInfoHeroId").apply{isAccessible=true}.get(v))
            assertEquals(before,v.currentSnapshot());assertEquals(0,fight!!.inputRevision);assertEquals(0,rng.draws)
            tap(v,center(v.battleInfoCloseBounds()))
        }
        tap(v,center(v.battleTargetBounds(fight!!.enemies.last().slot)))
        assertTrue(GameView::class.java.getDeclaredField("battleInfoOpen").apply{isAccessible=true}.getBoolean(v))
        assertEquals(before,v.currentSnapshot());assertEquals(0,fight!!.inputRevision);assertEquals(0,rng.draws)
        screenshot(v,"world-jiang-controlled-default-enemy-info-no-action"+evidenceSuffix)
        tap(v,center(v.battleInfoCloseBounds()))
        tap(v,center(v.battleCommandBounds(0)))
        val targetPoint=center(v.battleTargetBounds(fight!!.enemies.first().slot))
        send(v,MotionEvent.ACTION_DOWN,listOf(targetPoint))
        instrumentation.runOnMainSync{v.active=false}
        send(v,MotionEvent.ACTION_UP,listOf(targetPoint));instrumentation.runOnMainSync{v.active=true}
        assertEquals(BattlePresentation.Screen.COMMAND,p.screen);assertEquals(0,fight!!.inputRevision);assertEquals(0,rng.draws)
        tap(v,center(v.battleCommandBounds(2)))
        for(hero in fight!!.party){
            val box=v.battleItemTargetBounds(hero.id)
            assertTrue(box.x>=0&&box.x+box.w<=v.width&&box.y>=0&&box.y+box.h<=v.height)
            tap(v,center(box))
            hardwareButton(v,android.view.KeyEvent.KEYCODE_BUTTON_A)
            assertEquals(BattlePresentation.Screen.COMMAND,p.screen)
            assertEquals(hero.id,GameView::class.java.getDeclaredField("selectedBattleTarget")
                .apply{isAccessible=true}.get(v))
            assertEquals(0,fight!!.inputRevision);assertEquals(before.characters,fight!!.party)
            assertEquals(before.inventory,v.currentSnapshot().inventory)
        }
        screenshot(v,"world-jiang-controlled-four-actor-item-targets-selection-only"+evidenceSuffix)
        tap(v,center(v.battleItemBounds(HerbUse.ID)))
        val medicine=GameView::class.java.getDeclaredMethod("battleItemLayout").apply{isAccessible=true}.invoke(v) as TouchModalLayout
        val dp=v.resources.displayMetrics.density;val font=v.resources.configuration.fontScale
        assertTrue("Four-party target HP and two core medicine lines must remain visible",medicine.detail.h>=(14*font*1.25f+4)*2*dp)
        val measured=android.graphics.Paint()
        for(hero in fight!!.party){
            val box=v.battleItemTargetBounds(hero.id)
            measured.textSize=14*v.resources.displayMetrics.scaledDensity
            assertTrue("Full medicine target name",measured.measureText(v.content.characterDefinitions.getValue(hero.id).name)<=box.w-16*dp)
            measured.textSize=12*v.resources.displayMetrics.scaledDensity
            assertTrue("Full medicine target HP",measured.measureText("HP ${hero.hp}/${hero.maxHp}")<=box.w-16*dp)
        }
        screenshot(v,"world-jiang-controlled-four-actor-item-selected-medicine"+evidenceSuffix)
        val medicineScroll=center(medicine.detail);val medicineEnd=medicineScroll.first to medicineScroll.second-40*dp
        send(v,MotionEvent.ACTION_DOWN,listOf(medicineScroll));send(v,MotionEvent.ACTION_MOVE,listOf(medicineEnd))
        send(v,MotionEvent.ACTION_UP,listOf(medicineEnd))
        hardwareButton(v,android.view.KeyEvent.KEYCODE_BUTTON_A)
        assertEquals(0,fight!!.inputRevision);assertEquals(0,rng.draws)
        assertEquals(before,v.currentSnapshot());assertEquals(before.characters,fight!!.party)
        tap(v,center(v.battleItemCloseBounds()))
        val beforeEnemy=fight!!.enemies.map{it.hp}
        repeat(3){i->
            assertEquals(before.characters[i].id,fight!!.inputHero!!.id)
            val slot=fight!!.enemies.first{it.hp>0}.slot
            tap(v,center(v.battleCommandBounds(0)))
            lateinit var stale:BattleTouchCommand
            instrumentation.runOnMainSync{
                val point=center(v.battleTargetBounds(slot))
                stale=GameView::class.java.getDeclaredMethod("battleHit",Float::class.javaPrimitiveType,Float::class.javaPrimitiveType)
                    .apply{isAccessible=true}.invoke(v,point.first,point.second) as BattleTouchCommand
            }
            tap(v,center(v.battleTargetBounds(slot)))
            assertEquals(i+1,fight!!.inputRevision);assertEquals(beforeEnemy,fight!!.enemies.map{it.hp})
            assertEquals(before.characters,fight!!.party)
            assertEquals(BattlePresentation.Screen.COMMAND,p.screen);assertEquals(0,p.command);assertEquals(0,rng.draws)
            instrumentation.runOnMainSync{
                val submit=GameView::class.java.getDeclaredMethod("submitBattle",BattleTouchCommand::class.java).apply{isAccessible=true}
                submit.invoke(v,stale) // Captured old callback, no new player command.
                val wrongActor=stale.copy(revision=p.revision).also{it.actorId=stale.actorId;it.inputRevision=fight!!.inputRevision}
                submit.invoke(v,wrongActor)
                val wrongInput=stale.copy(revision=p.revision).also{it.actorId=fight!!.inputHero!!.id;it.inputRevision=stale.inputRevision}
                submit.invoke(v,wrongInput)
            }
            repeat(5){send(v,MotionEvent.ACTION_UP,listOf(center(v.battleTargetBounds(slot))))}
            assertEquals(i+1,fight!!.inputRevision);assertEquals(0,rng.draws);assertEquals(before,v.currentSnapshot())
            // A new actor must select its own command; a plain target tap stays read-only.
            tap(v,center(v.battleTargetBounds(slot)))
            assertEquals(i+1,fight!!.inputRevision);assertEquals(0,rng.draws)
            tap(v,center(v.battleInfoCloseBounds()))
        }
        assertEquals("jiangziya",fight!!.inputHero!!.id)
        val deadline=SystemClock.elapsedRealtime()+60000
        var capturedAction=false
        while(fight!!.phase==BattlePhase.TARGET){
            assertTrue("Bounded actual four-actor physical smoke",SystemClock.elapsedRealtime()<deadline)
            if(p.screen in listOf(BattlePresentation.Screen.COMMAND,BattlePresentation.Screen.TARGET))
                chooseAttack(v,fight!!.enemies.first{it.hp>0}.slot)
            if(!capturedAction&&p.screen==BattlePresentation.Screen.ACTING){
                screenshot(v,"world-jiang-controlled-four-actor-live-action"+evidenceSuffix);capturedAction=true
            }
            SystemClock.sleep(40)
        }
        assertEquals(BattlePhase.VICTORY,fight!!.phase)
        var committed=false
        while(!committed){
            instrumentation.runOnMainSync{committed=p.screen==BattlePresentation.Screen.RESULT&&
                GameView::class.java.getDeclaredField("battleCommitted").apply{isAccessible=true}.getBoolean(v)}
            assertTrue(SystemClock.elapsedRealtime()<deadline);if(!committed)SystemClock.sleep(40)
        }
        val settled=v.currentSnapshot();assertEquals(OriginalJiangJoin.FULL_PARTY,settled.characters.map{it.id})
        assertTrue(settled.validate(v.content));assertEquals(before.inventory,settled.inventory)
        assertEquals(before.money+fight!!.enemies.sumOf{it.definition.moneyReward},settled.money)
        screenshot(v,"world-jiang-controlled-four-actor-victory"+evidenceSuffix)
        send(v,MotionEvent.ACTION_UP,listOf(Pair(v.width*.5f,v.height*.5f)))
        assertEquals(settled,v.currentSnapshot())
        val scene=GameView::class.java.getDeclaredMethod("battleScene").apply{isAccessible=true}.invoke(v) as BattleSceneLayout
        val footer=scene.resultFooter
        tap(v,Pair(footer.x+footer.w*.25f,footer.y+footer.h*.5f))
        assertTrue(GameView::class.java.getDeclaredField("battleResultDetails").apply{isAccessible=true}.getBoolean(v))
        screenshot(v,"world-jiang-controlled-four-actor-reward-details"+evidenceSuffix)
        val scroll=center(scene.touch.result)
        send(v,MotionEvent.ACTION_DOWN,listOf(scroll));send(v,MotionEvent.ACTION_MOVE,listOf(scroll.first to scroll.second-100))
        send(v,MotionEvent.ACTION_UP,listOf(scroll.first to scroll.second-100));SystemClock.sleep(3000)
        assertEquals(GameView.Layer.BATTLE,v.layer);assertEquals(settled,v.currentSnapshot())
        screenshot(v,"world-jiang-controlled-four-actor-reward-details-scrolled"+evidenceSuffix)
        if(evidenceSuffix.isEmpty())tap(v,Pair(footer.x+footer.w*.75f,footer.y+footer.h*.5f))
        else{
            hardwareButton(v,android.view.KeyEvent.KEYCODE_BUTTON_B)
            assertFalse(GameView::class.java.getDeclaredField("battleResultDetails").apply{isAccessible=true}.getBoolean(v))
            assertEquals(GameView.Layer.BATTLE,v.layer);assertEquals(settled,v.currentSnapshot())
            tap(v,Pair(footer.x+footer.w*.25f,footer.y+footer.h*.5f))
            hardwareButton(v,android.view.KeyEvent.KEYCODE_BUTTON_A)
        }
        assertEquals(GameView.Layer.MAP,v.layer);assertEquals(settled,v.currentSnapshot())
        repeat(5){send(v,MotionEvent.ACTION_UP,listOf(scroll))};assertEquals(settled,v.currentSnapshot())
    }
    private fun launch(dismissOpening:Boolean=true):Pair<MainActivity,GameView>{
        val activity=instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var view:GameView?=null
        // A fresh emulator install may still be dex-optimizing while the content loader runs.
        for(i in 0..800){
            instrumentation.runOnMainSync{view=(activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as? GameView)?.takeIf{it.width>0&&it.height>0}}
            if(view!=null)break
            SystemClock.sleep(50)
        }
        if(view==null){
            var visibleMessage=""
            instrumentation.runOnMainSync{visibleMessage=(activity.findViewById<ViewGroup>(android.R.id.content)
                .getChildAt(0) as? android.widget.TextView)?.text?.toString()?:"No GameView or loading message"}
            fail("App failed to become interactive: $visibleMessage")
        }
        instrumentation.runOnMainSync{view!!.active=true;view!!.focused=true}
        if(dismissOpening&&view!!.layer==GameView.Layer.DIALOGUE)tap(view!!,Pair(view!!.width*.5f,view!!.height*.5f))
        return activity to view!!
    }
    private fun send(v:GameView,action:Int,points:List<Pair<Float,Float>>){
        instrumentation.runOnMainSync{dispatchTouchOnMain(v,action,points)}
    }
    private fun dispatchTouchOnMain(v:GameView,action:Int,points:List<Pair<Float,Float>>){
            val props=Array(points.size){i->MotionEvent.PointerProperties().apply{id=i;toolType=MotionEvent.TOOL_TYPE_FINGER}}
            val coords=Array(points.size){i->MotionEvent.PointerCoords().apply{x=points[i].first;y=points[i].second;pressure=1f;size=1f}}
            val time=SystemClock.uptimeMillis()
            val event=MotionEvent.obtain(time,time,action,points.size,props,coords,0,0,1f,1f,0,0,0,0)
            v.dispatchTouchEvent(event);event.recycle()
    }
    private fun tap(v:GameView,p:Pair<Float,Float>){send(v,MotionEvent.ACTION_DOWN,listOf(p));send(v,MotionEvent.ACTION_UP,listOf(p))}
    private fun hardwareButton(v:GameView,code:Int){instrumentation.runOnMainSync{
        v.onKeyDown(code,android.view.KeyEvent(android.view.KeyEvent.ACTION_DOWN,code))
        v.onKeyUp(code,android.view.KeyEvent(android.view.KeyEvent.ACTION_UP,code))
    }}
    /** Real player sequence: each actor chooses a command before its target. */
    private fun chooseAttack(v:GameView,slot:Int){
        tap(v,center(v.battleCommandBounds(0)));tap(v,center(v.battleTargetBounds(slot)))
    }
    private fun layoutFor(v:GameView)=layout(v.width,v.height,v.resources.displayMetrics.density,v.safe,DisplayMode.FULL,ControlConfig())
    private fun tapMapActor(v:GameView,npc:StoryNpc){
        var point:Pair<Float,Float>?=null
        instrumentation.runOnMainSync{
            assertEquals(npc.mapId,v.world.mapId)
            val at=v.world.destinationCell()
            assertTrue(npc.interactionCell==at||kotlin.math.abs(at.first-npc.x)+kotlin.math.abs(at.second-npc.y)==1)
            val actualUi=GameView::class.java.getDeclaredField("ui").apply{isAccessible=true}.get(v) as ScreenLayout
            point=actualUi.worldToScreen((npc.x*16+8).toFloat(),(npc.y*16+8).toFloat(),
                v.world.camera(actualUi.viewWidth,actualUi.viewHeight))
        }
        tap(v,point!!)
    }
    private fun center(b:Box)=Pair(b.x+b.w/2,b.y+b.h/2)
    private fun tabPoint(v:GameView,index:Int)=center(v.panelTabBounds(index))
    private fun menuPoint(v:GameView,row:Int):Pair<Float,Float>{
        val safe=layoutFor(v).safe;val dp=v.resources.displayMetrics.density
        val h=minOf(safe.h*.78f,360*dp)
        return Pair(safe.x+safe.w/2,safe.y+(safe.h-h)/2+h*(.22f+.18f*(row+.5f)))
    }
    private fun stickStep(v:GameView,key:Key){
        val stick=layoutFor(v).stick;val middle=center(stick)
        val point=when(key){
            Key.UP->Pair(middle.first,stick.y+2f)
            Key.DOWN->Pair(middle.first,stick.y+stick.h-2f)
            Key.LEFT->Pair(stick.x+2f,middle.second)
            Key.RIGHT->Pair(stick.x+stick.w-2f,middle.second)
            else->error("Direction required")
        }
        var beforeMap=0;var beforeSeq=0L;var beforeX=0;var beforeY=0
        instrumentation.runOnMainSync{beforeMap=v.world.mapId;beforeSeq=v.world.completedStepSeq;beforeX=v.world.x;beforeY=v.world.y}
        send(v,MotionEvent.ACTION_DOWN,listOf(middle));send(v,MotionEvent.ACTION_MOVE,listOf(point))
        var started=false
        for(i in 0..100){
            // World completion sets remaining=0 before dispatching its exit, in the same UI callback.
            // Observe that callback atomically; a background read can see the doorway before the map changes.
            instrumentation.runOnMainSync{
                started=v.world.remaining>0||v.world.mapId!=beforeMap||v.world.completedStepSeq!=beforeSeq||v.layer==GameView.Layer.BATTLE
                // Release in the observation callback. A second queued callback
                // can otherwise leave a real held stick active for another frame.
                if(started)dispatchTouchOnMain(v,MotionEvent.ACTION_UP,listOf(point))
            }
            if(started)break
            SystemClock.sleep(5)
        }
        if(!started){
            send(v,MotionEvent.ACTION_UP,listOf(point));screenshot(v,"world-touch-step-failure")
            instrumentation.runOnMainSync{
                File(instrumentation.targetContext.getExternalFilesDir(null),"world-touch-step-failure.json").writeText(org.json.JSONObject()
                    .put("key",key.name).put("beforeMap",beforeMap).put("beforeX",beforeX).put("beforeY",beforeY)
                    .put("beforeSeq",beforeSeq).put("snapshot",v.currentSnapshot().json()).put("layer",v.layer.name)
                    .put("probe",v.world.scene.probeFrom(v.world.x/16,v.world.y/16,key,v.world.terrainMode).name)
                    .put("message",v.world.message).toString())
            }
        }
        assertTrue("No step: key=$key map=$beforeMap from=$beforeX,$beforeY x=${v.world.x} y=${v.world.y} message=${v.world.message}",started)
        var remaining=0
        for(i in 0..100){
            instrumentation.runOnMainSync{remaining=v.world.remaining}
            if(remaining==0)break
            SystemClock.sleep(5)
        }
        instrumentation.runOnMainSync{assertEquals("Unfinished normal touch step",0,v.world.remaining)}
    }

    /** Isolated acquired contact fixture; actual lifecycle, not normal supply proof. */
    fun testControlledFerryPauseSavedStageAndActivityRestart(){
        val(activity,v)=launch();val c=v.content;val rule=c.ferries.getValue("rom.ferry.45")
        val initial=SaveSnapshot(c.scene.version,4,11*16+8,3*16+8,Key.LEFT,
            listOf(c.initialPlayer.copy(hp=100,maxHp=100,statusMask=OriginalStatus.POISON)),emptyMap(),money=81,encounterSteps=60)
        instrumentation.runOnMainSync{v.active=false;assertTrue(v.restoreSnapshot(initial));v.active=true}
        val stick=layoutFor(v).stick;val middle=center(stick);val point=Pair(stick.x+2f,middle.second)
        send(v,MotionEvent.ACTION_DOWN,listOf(middle));send(v,MotionEvent.ACTION_MOVE,listOf(point))
        val deadline=SystemClock.elapsedRealtime()+12000;var paused:SaveSnapshot?=null
        while(paused==null){
            assertTrue(SystemClock.elapsedRealtime()<deadline)
            instrumentation.runOnMainSync{
                val s=v.currentSnapshot();val stage=rule.stage(s.flags)
                if(stage!=null&&stage in 2..15){
                    dispatchTouchOnMain(v,MotionEvent.ACTION_UP,listOf(point));v.active=false;v.persistState();paused=v.currentSnapshot()
                }
            }
            if(paused==null)SystemClock.sleep(15)
        }
        screenshot(v,"world-ferry-controlled-paused-durable-stage")
        SystemClock.sleep(250);assertEquals(paused,v.currentSnapshot())
        val prefs=instrumentation.targetContext.getSharedPreferences("opening-local-save",0)
        assertEquals(paused,SaveSnapshot.parse(prefs.getString("saveJson",null)!!))
        instrumentation.runOnMainSync{activity.finish()};val(a2,v2)=launch()
        val settleDeadline=SystemClock.elapsedRealtime()+12000;var done=false
        while(!done){
            assertTrue(SystemClock.elapsedRealtime()<settleDeadline)
            instrumentation.runOnMainSync{done=OriginalFerry.pending(v2.currentSnapshot().flags,c.ferries.values)==null}
            if(!done)SystemClock.sleep(15)
        }
        var expected=OriginalFerry.begin(initial,rule,Key.LEFT,c.ferries.values).snapshot
        for(i in rule.legs.indices)expected=OriginalFerry.advance(expected,rule,i,c.ferries.values).snapshot
        assertEquals(expected,v2.currentSnapshot());assertEquals(82,v2.currentSnapshot().characters.single().hp)
        assertEquals(GameView.Layer.MAP,v2.layer);screenshot(v2,"world-ferry-controlled-resumed-exact-cost-no-double-charge")
        instrumentation.runOnMainSync{a2.finish()}
    }
    /** Explicit isolated pending-story fixture, not the normal final-hall recording. */
    fun testControlledRebirthCancelPendingRestartAndOnceOnlyCompletion(){
        var (activity,v)=launch()
        val c=v.content;val story=c.sceneStories.getValue("rom.npc.86.0")
        val base=SaveSnapshot(c.scene.version,86,12*16+8,5*16+8,Key.UP,
            listOf(c.initialPlayer,c.joinCharacters.getValue("xiaolongnv")),mapOf(HerbUse.ID to 2),emptyMap(),887)
        val pending=StoryFollowup.begin(base,story).snapshot
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(pending));v.startOpeningIfNeeded()}
        assertEquals(GameView.Layer.DIALOGUE,v.layer);assertEquals(pending,v.currentSnapshot())
        val middle=Pair(v.width*.5f,v.height*.5f)
        send(v,MotionEvent.ACTION_DOWN,listOf(middle));send(v,MotionEvent.ACTION_CANCEL,listOf(middle))
        send(v,MotionEvent.ACTION_UP,listOf(middle));assertEquals(pending,v.currentSnapshot())
        instrumentation.runOnMainSync{v.handleBack()};assertEquals(pending,v.currentSnapshot())
        assertEquals(GameView.Layer.DIALOGUE,v.layer)
        val text=GameView::class.java.getDeclaredField("dialogueText").apply{isAccessible=true}
        fun currentId()=(text.get(v)as StoryText).id
        for(index in 0..4){
            val id=story.continuation.dialogueIds[index];assertEquals(id,currentId())
            var pages=0
            while(v.layer==GameView.Layer.DIALOGUE&&currentId()==id){
                assertTrue(pages++<32);tap(v,Pair(v.width*.5f,v.height*.5f))}
        }
        val mid=v.currentSnapshot();assertEquals("rom.dialogue.96.7",currentId())
        val prefs=instrumentation.targetContext.getSharedPreferences("opening-local-save",0)
        assertEquals(mid,SaveSnapshot.parse(prefs.getString("saveJson",null)!!))
        screenshot(v,"world-rebirth-controlled-pending-before-restart")
        instrumentation.runOnMainSync{v.persistState();activity.finish()};instrumentation.waitForIdleSync()
        val restarted=launch(false);activity=restarted.first;v=restarted.second
        assertEquals(mid,v.currentSnapshot());assertEquals(GameView.Layer.DIALOGUE,v.layer)
        assertEquals("rom.dialogue.96.7",currentId())
        screenshot(v,"world-rebirth-controlled-pending-restored")
        for(index in 5..10){
            val id=story.continuation.dialogueIds[index];assertEquals(id,currentId())
            var pages=0
            while(v.layer==GameView.Layer.DIALOGUE&&currentId()==id){
                assertTrue(pages++<32);tap(v,Pair(v.width*.5f,v.height*.5f))}
        }
        assertEquals(GameView.Layer.MAP,v.layer);val done=v.currentSnapshot()
        assertEquals(16,done.mapId);assertEquals(238,done.x/16);assertEquals(160,done.y/16)
        assertEquals(base.characters,done.characters);assertEquals(base.inventory,done.inventory);assertEquals(base.money,done.money)
        assertTrue(done.flags[story.flagId]==true);assertTrue(done.flags[story.pendingFlag]!=true)
        send(v,MotionEvent.ACTION_UP,listOf(Pair(v.width*.5f,v.height*.5f)));assertEquals(done,v.currentSnapshot())
        assertEquals(done,SaveSnapshot.parse(prefs.getString("saveJson",null)!!))
        screenshot(v,"world-rebirth-controlled-completed-no-extra-reward")
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testUnrestorableSaveCannotBeOverwritten(){
        val (activity,v)=launch()
        val prefs=instrumentation.targetContext.getSharedPreferences("opening-local-save",0)
        // Emulator-only corrupt/missing-scene fixture; the user's preferences are restored by IsolatedGameTestCase.
        val broken=v.currentSnapshot().copy(mapId=255).json().toString()
        instrumentation.runOnMainSync{
            assertTrue(prefs.edit().putString("saveJson",broken).commit())
            v.restorePersisted();v.persistState();v.active=false;v.persistState()
            assertEquals(broken,prefs.getString("saveJson",null))
            val valid=v.currentSnapshot()
            assertTrue(v.restoreSnapshot(valid));v.persistState()
            assertEquals(valid.json().toString(),prefs.getString("saveJson",null))
            activity.finish()
        }
    }
    /** Isolated boundary fixture; distinct from the normal bought-supply and sea route. */
    fun testWorldLegacyInteriorContextAndRestart(){
        val(activity,v)=launch();val original=v.currentSnapshot()
        val legacy=original.copy(contentVersion="opening-segment-001-c14",mapId=18,x=7*16+8,y=7*16+8,
            direction=Key.UP,interiorContext=null)
        instrumentation.runOnMainSync{
            assertFalse("New room save with missing caller must not silently return to town0",v.restoreSnapshot(legacy.copy(contentVersion=v.content.scene.version)))
            assertEquals(original,v.currentSnapshot());assertTrue(v.restoreSnapshot(legacy))
        }
        val migrated=v.currentSnapshot();assertEquals(InteriorContext(0,6,19),migrated.interiorContext)
        assertEquals(original.characters,migrated.characters);assertEquals(original.inventory,migrated.inventory)
        assertEquals(original.money,migrated.money);assertEquals(original.flags,migrated.flags)
        instrumentation.runOnMainSync{v.persistState();activity.finish()}
        val(a2,v2)=launch();assertEquals(migrated,v2.currentSnapshot())
        repeat(5){stickStep(v2,Key.DOWN)};assertEquals(0,v2.world.mapId)
        assertEquals(6,v2.world.x/16);assertEquals(19,v2.world.y/16);assertNull(v2.currentSnapshot().interiorContext)
        assertEquals(original.characters,v2.currentSnapshot().characters);assertEquals(original.inventory,v2.currentSnapshot().inventory)
        assertEquals(original.money,v2.currentSnapshot().money);assertEquals(original.flags,v2.currentSnapshot().flags)
        screenshot(v2,"world-legacy-room-return");instrumentation.runOnMainSync{a2.finish()}
    }

    /** Isolated boundary fixture; distinct from the normal bought-supply and sea route. */
    fun testControlledWorldAntidoteAndFieldPoison(){
        val(activity,v)=launch();val start=v.currentSnapshot()
        val poison=v.content.initialPlayer.copy(hp=5,statusMask=OriginalStatus.POISON)
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(start.copy(characters=listOf(poison),
            inventory=mapOf(AntidoteUse.ID to 3),flags=mapOf("opening.intro.seen" to true))))}
        tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));scrollToItem(v,AntidoteUse.ID)
        val old=v.currentSnapshot();tap(v,center(v.panelItemBounds(AntidoteUse.ID)))
        assertEquals(old,v.currentSnapshot());screenshot(v,"world-antidote-controlled-selected")
        val action=center(v.panelPrimaryBounds())
        send(v,MotionEvent.ACTION_DOWN,listOf(action));send(v,MotionEvent.ACTION_CANCEL,listOf(action));send(v,MotionEvent.ACTION_UP,listOf(action))
        assertEquals(old,v.currentSnapshot())
        tap(v,action);val after=v.currentSnapshot();assertEquals(1,after.inventory[AntidoteUse.ID])
        assertEquals(0,after.characters.single().statusMask);assertEquals(5,after.characters.single().hp)
        send(v,MotionEvent.ACTION_UP,listOf(action));assertEquals(after,v.currentSnapshot())
        screenshot(v,"world-antidote-controlled-cured");instrumentation.runOnMainSync{v.handleBack()}
        // Reopen the exact persisted JSON, without rebuilding quantities or status.
        val encoded=instrumentation.targetContext.getSharedPreferences("opening-local-save",0).getString("saveJson",null)!!
        assertEquals(after,SaveSnapshot.parse(encoded))
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(SaveSnapshot.parse(encoded)))}
        assertEquals(after,v.currentSnapshot())
        for((status,hp) in listOf(0 to 5,32 to 0)){
            instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(start.copy(characters=listOf(poison.copy(hp=hp,statusMask=status)),
                inventory=mapOf(AntidoteUse.ID to 3),flags=mapOf("opening.intro.seen" to true))))}
            tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));tap(v,center(v.panelItemBounds(AntidoteUse.ID)));tap(v,center(v.panelPrimaryBounds()))
            assertEquals(2,v.currentSnapshot().inventory[AntidoteUse.ID]);assertEquals(hp,v.currentSnapshot().characters.single().hp)
            assertEquals(status,v.currentSnapshot().characters.single().statusMask);instrumentation.runOnMainSync{v.handleBack()}
        }
        // Keep movement boundaries independent of the previous test's returned shop doorway.
        // The normal opening route already verifies these two adjacent cells; this remains a fixture.
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(start.copy(mapId=114,x=8*16+8,y=21*16+8,
            direction=Key.DOWN,interiorContext=null,characters=listOf(poison.copy(hp=2)),
            inventory=emptyMap(),flags=mapOf("opening.intro.seen" to true))))}
        stickStep(v,Key.RIGHT);assertEquals(1,v.currentSnapshot().characters.single().hp)
        assertEquals(2,v.currentSnapshot().characters.single().statusMask)
        val persisted=v.currentSnapshot();instrumentation.runOnMainSync{v.persistState();activity.finish()}
        val(a2,v2)=launch();assertEquals(persisted,v2.currentSnapshot())
        stickStep(v2,Key.LEFT);assertEquals(0,v2.currentSnapshot().characters.single().hp)
        assertEquals(32,v2.currentSnapshot().characters.single().statusMask);assertEquals(GameView.Layer.FIELD_FAILURE,v2.layer)
        screenshot(v2,"world-field-defeat-controlled")
        val dialog=GameView::class.java.getDeclaredField("modalDialog").apply{isAccessible=true}.get(v2) as android.app.AlertDialog
        instrumentation.runOnMainSync{dialog.getButton(android.app.AlertDialog.BUTTON_POSITIVE).performClick()}
        assertEquals(GameView.Layer.MAP,v2.layer);assertEquals(114,v2.world.mapId)
        assertEquals(20,v2.currentSnapshot().characters.single().hp);assertEquals(0,v2.currentSnapshot().characters.single().statusMask)
        assertFalse(v2.currentSnapshot().flags["runtime.field-defeat.pending"]==true)
        instrumentation.runOnMainSync{a2.finish()}
    }
    fun testNormalOpeningRouteGiftAndMap16Encounter(){normalOpeningBattle(false)}
    fun testNormalOpeningEscapeAndDefeat(){normalOpeningBattle(true)}
    fun testNormalTownShopsBuySellAndReturn(){normalTownShops(false)}
    fun testNormalHerbSupplyLoop(){normalTownShops(true)}
    fun testNormalTownRoom28EntryInvestigationAndSave(){normalTownShops(false,room28=true)}
    fun testTownRoom28ExternalColdStartReturnAndNoSecondGrant(){
        val root=instrumentation.targetContext.getExternalFilesDir(null)!!
        val expected=SaveSnapshot.parse(File(root,"world-room28-expected-save.json").readText())
        val(activity,v)=launch()
        assertEquals(expected,v.currentSnapshot());assertEquals(28,v.world.mapId)
        assertEquals(InteriorContext(0,12,23),v.world.interiorContext)
        assertEquals(true,expected.flags["rom.map.28.flag.1"])
        screenshot(v,"world-room28-cold-full-state")
        repeat(3){stickStep(v,Key.DOWN)}
        assertEquals(0,v.world.mapId);assertEquals(12 to 23,v.world.x/16 to v.world.y/16)
        assertNull(v.world.interiorContext)
        stickStep(v,Key.DOWN);stickStep(v,Key.UP)
        assertEquals(28,v.world.mapId);assertEquals(6 to 10,v.world.x/16 to v.world.y/16)
        repeat(6){stickStep(v,Key.UP)};stickStep(v,Key.LEFT);stickStep(v,Key.RIGHT)
        assertEquals(6 to 4,v.world.x/16 to v.world.y/16)
        val before=v.currentSnapshot();val action=center(layoutFor(v).buttons.getValue(Key.A))
        tap(v,action);assertEquals(before,v.currentSnapshot())
        assertEquals(expected.inventory,before.inventory);assertEquals(expected.money,before.money)
        assertEquals(expected.characters,before.characters)
        screenshot(v,"world-room28-cold-reentry-no-repeat-grant")
        instrumentation.runOnMainSync{v.persistState();activity.finish()}
    }
    fun testExportCurrentSaveForUpgrade(){
        val(activity,v)=launch()
        // On the old APK, create a real gift/position save with normal inputs.
        if(v.world.mapId==114 && v.world.x/16==8 && v.world.y/16==21){
            repeat(3){stickStep(v,Key.RIGHT)};stickStep(v,Key.DOWN)
            tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
            repeat(12){if(v.layer==GameView.Layer.DIALOGUE)tap(v,Pair(v.width*.5f,v.height*.5f))}
            assertEquals(1,v.currentSnapshot().inventory[OpeningEquipment.KNIFE_ID])
        }
        File(instrumentation.targetContext.getExternalFilesDir(null),"town02-upgrade-source.json").writeText(v.currentSnapshot().json().toString())
        val oldBackup=instrumentation.targetContext.getSharedPreferences("opening-local-save",0).getString("preContentMigration",null)
        val backupFile=File(instrumentation.targetContext.getExternalFilesDir(null),"town02-upgrade-existing-backup.json")
        if(oldBackup!=null)backupFile.writeText(oldBackup) else backupFile.delete()
        instrumentation.runOnMainSync{v.persistState();activity.finish()}
    }
    fun testUpgradeKeepsPreviousSave(){
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"town02-upgrade-source.json")
        assertTrue("Old APK must first export its normal save",file.exists())
        val old=SaveSnapshot.parse(file.readText())
        val(activity,v)=launch()
        assertEquals(old.copy(contentVersion=v.content.scene.version),v.currentSnapshot())
        if(old.contentVersion!=v.content.scene.version){
            val backup=instrumentation.targetContext.getSharedPreferences("opening-local-save",0).getString("preContentMigration",null)
            val priorBackup=File(instrumentation.targetContext.getExternalFilesDir(null),"town02-upgrade-existing-backup.json")
            val first=if(priorBackup.exists())SaveSnapshot.parse(priorBackup.readText())else old
            assertEquals("First original pre-upgrade JSON must remain recoverable",first,SaveSnapshot.parse(backup!!))
        }
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testContentMigrationKeepsFirstRecoverableBackup(){
        val source=File(instrumentation.targetContext.getExternalFilesDir(null),"town02-upgrade-source.json").readText()
        val prefs=instrumentation.targetContext.getSharedPreferences("opening-local-save",0)
        val first=prefs.getString("preContentMigration",null)
        val sourceSnapshot=SaveSnapshot.parse(source)
        val(activity,v)=launch()
        val expected=sourceSnapshot.copy(contentVersion=v.content.scene.version)
        val priorFile=File(instrumentation.targetContext.getExternalFilesDir(null),"town02-upgrade-existing-backup.json")
        val priorRaw=if(priorFile.exists())priorFile.readText()else null
        val expectedAfterCovering=priorRaw?:if(sourceSnapshot.contentVersion!=v.content.scene.version)source else null
        assertEquals("Actual covering install must preserve the prior raw backup or migrate only changed content",
            expectedAfterCovering,first)
        assertEquals(expected,v.currentSnapshot())
        assertEquals("Same-content covering install must not fabricate or replace a migration backup",first,
            prefs.getString("preContentMigration",null))
        var expectedFirst=first
        // CONTROLLED version-only migration fixtures based on the real covering
        // save, not a claim that this APK's unchanged content caused migration.
        val versions=listOf("opening-segment-001-c60","opening-segment-001-c51","opening-segment-001-c14")
            .filter{it!=v.content.scene.version}.take(2)
        val reports=org.json.JSONArray()
        for(version in versions){
            val fixture=sourceSnapshot.copy(contentVersion=version)
            assertEquals(sourceSnapshot,fixture.copy(contentVersion=sourceSnapshot.contentVersion))
            val encoded=fixture.json().toString()
            var committed=false
            instrumentation.runOnMainSync{
                committed=prefs.edit().putString("saveJson",encoded).commit()
                if(committed){v.restorePersisted();v.persistState()}
            }
            assertTrue("Write the isolated version-only fixture",committed)
            if(expectedFirst==null)expectedFirst=encoded
            assertEquals("First raw pre-migration JSON must stay byte-for-byte recoverable",expectedFirst,
                prefs.getString("preContentMigration",null))
            assertEquals(expected,v.currentSnapshot())
            val saved=prefs.getString("saveJson",null)
            assertNotNull("Migration must retain a persisted current save",saved)
            assertEquals(expected,SaveSnapshot.parse(saved!!))
            reports.put(JSONObject().put("sourceContentVersion",version).put("currentSaveMatches",true)
                .put("firstBackupPreserved",true))
        }
        File(instrumentation.targetContext.getExternalFilesDir(null),"world-content-migration-backup.json").writeText(
            JSONObject().put("kind","CONTROLLED_VERSION_ONLY_MIGRATION_FROM_ACTUAL_COVERING_SAVE")
                .put("coveringContentVersion",sourceSnapshot.contentVersion).put("targetContentVersion",v.content.scene.version)
                .put("hadBackupAfterActualCovering",first!=null).put("sameContentCovering",sourceSnapshot.contentVersion==v.content.scene.version)
                .put("checks",reports).toString())
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testHerbColdStartMatchesNormalSave(){
        val expectedFile=File(instrumentation.targetContext.getExternalFilesDir(null),"town02-expected-save.json")
        assertTrue("Run the normal supply test before an external force-stop",expectedFile.exists())
        val expected=SaveSnapshot.parse(expectedFile.readText())
        val(activity,v)=launch()
        assertEquals(expected,v.currentSnapshot())
        assertEquals(0,v.currentSnapshot().inventory[HerbUse.ID]?:0)
        val next=listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT).first{
            v.world.scene.probeFrom(v.world.x/16,v.world.y/16,it)==MovementBlock.NONE}
        stickStep(v,next)
        assertTrue("Cold restart must allow continued exploration",v.currentSnapshot().let{it.x!=expected.x || it.y!=expected.y || it.mapId!=expected.mapId})
        assertEquals(expected.characters,v.currentSnapshot().characters)
        assertEquals(expected.inventory,v.currentSnapshot().inventory)
        instrumentation.runOnMainSync{v.persistState();activity.finish()}
    }
    fun testControlledHerbBoundariesAndSaveCompatibility(){
        val(activity,v)=launch()
        val baseline=v.currentSnapshot()
        val hero=v.content.initialPlayer.copy(maxHp=100)
        var action=Pair(0f,0f)
        fun fixture(hp:Int,count:Int){
            instrumentation.runOnMainSync{
                if(v.layer!=GameView.Layer.MAP)v.handleBack()
                assertTrue(v.restoreSnapshot(baseline.copy(contentVersion="opening-segment-001-c11",
                    characters=listOf(hero.copy(hp=hp)),inventory=if(count>0)mapOf(HerbUse.ID to count) else emptyMap())))
            }
            tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2))
            if(count>0){scrollToItem(v,HerbUse.ID);tap(v,center(v.panelItemBounds(HerbUse.ID)))}
            action=center(v.panelPrimaryBounds())
        }
        fixture(5,3)
        val before=v.currentSnapshot()
        send(v,MotionEvent.ACTION_DOWN,listOf(action));send(v,MotionEvent.ACTION_CANCEL,listOf(action))
        assertEquals(before,v.currentSnapshot())
        send(v,MotionEvent.ACTION_DOWN,listOf(action))
        send(v,MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(action,action))
        send(v,MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(action,action))
        send(v,MotionEvent.ACTION_UP,listOf(action))
        assertEquals(before,v.currentSnapshot()) // Multi-touch cancels the whole candidate gesture.
        tap(v,action) // A fresh independent single touch is the valid submit.
        assertEquals(55,v.currentSnapshot().characters.single().hp)
        assertEquals(2,v.currentSnapshot().inventory[HerbUse.ID])
        val once=v.currentSnapshot();repeat(10){send(v,MotionEvent.ACTION_UP,listOf(action))};assertEquals(once,v.currentSnapshot())
        for(hp in listOf(95,100)){
            fixture(hp,3);tap(v,action)
            assertEquals(100,v.currentSnapshot().characters.single().hp)
            assertEquals(2,v.currentSnapshot().inventory[HerbUse.ID])
        }
        fixture(0,3);val dead=v.currentSnapshot();tap(v,action);assertEquals(dead,v.currentSnapshot())
        fixture(5,0);val empty=v.currentSnapshot();tap(v,action);assertEquals(empty,v.currentSnapshot())
        instrumentation.runOnMainSync{activity.finish()}
    }
    private fun normalTownShops(useHerb:Boolean,touchUx:Boolean=false,innSupply:Boolean=false,room28:Boolean=false){
        instrumentation.targetContext.getSharedPreferences("opening-local-save",0).edit().clear().commit()
        val(activity,v)=launch()
        fun capture(name:String){
            SystemClock.sleep(250)
            File(instrumentation.targetContext.getExternalFilesDir(null),"town01-$name.png").outputStream().use{
                instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        var seekingInjury=false
        var approachingVillage=false
        val injuryEvents=org.json.JSONArray()
        fun injuryState(name:String,fight:OpeningBattle?=null){
            val event=org.json.JSONObject().put("name",name).put("androidUptimeMs",SystemClock.elapsedRealtime())
                .put("snapshot",v.currentSnapshot().json())
            if(fight!=null)event.put("battlePhase",fight.phase.name).put("battleHeroHp",fight.hero.hp)
                .put("groupId",fight.group.id).put("enemies",org.json.JSONArray().also{rows->
                    fight.enemies.forEach{rows.put(org.json.JSONObject().put("slot",it.slot)
                        .put("enemyId",it.definition.id).put("hp",it.hp))}})
            injuryEvents.put(event)
            File(instrumentation.targetContext.getExternalFilesDir(null),"town01-normal-injury-attempts.json").writeText(
                org.json.JSONObject().put("kind","NORMAL_NEW_GAME_TOUCH_INPUTS")
                    .put("stateGrants",false).put("events",injuryEvents).toString())
        }
        fun finishFight(){
            if(v.layer!=GameView.Layer.BATTLE)return
            val f=GameView::class.java.getDeclaredField("battle").apply{isAccessible=true}
            val p=GameView::class.java.getDeclaredField("battlePresentation").apply{isAccessible=true}
            for(i in 0..1000){
                if(v.layer!=GameView.Layer.BATTLE)break
                val fight=f.get(v) as OpeningBattle;val presentation=p.get(v) as BattlePresentation
                if(presentation.screen !in listOf(BattlePresentation.Screen.ENTRY,BattlePresentation.Screen.ACTING)){
                    if(fight.phase==BattlePhase.DEFEAT){injuryState("normal-route-defeat",fight);capture("herb-route-defeat")}
                    assertTrue("Town route must survive normally",fight.phase!=BattlePhase.DEFEAT)
                    if(presentation.screen in listOf(BattlePresentation.Screen.COMMAND,BattlePresentation.Screen.TARGET)){
                        // A hand-knife can legitimately defeat early single enemies before they act.
                        // Use the actual escape button while still at full HP to expose ordinary
                        // retaliation; never edit HP, force an enemy, or alter random consumption.
                        val escape=(seekingInjury&&fight.hero.hp==fight.hero.maxHp)||
                            (approachingVillage&&(fight.enemies.count{it.hp>0}>1||fight.hero.hp<=fight.hero.maxHp*2/3))
                        injuryState(if(escape)"normal-touch-escape" else "normal-touch-attack",fight)
                        if(escape)
                            tap(v,center(v.battleCommandBounds(3)))
                        else chooseAttack(v,fight.enemies.first{it.hp>0}.slot)
                    }
                    else if(presentation.screen==BattlePresentation.Screen.RESULT)SystemClock.sleep(40)
                }
                SystemClock.sleep(40)
            }
            assertEquals(GameView.Layer.MAP,v.layer)
            injuryState("normal-encounter-ended")
        }
        fun step(k:Key){stickStep(v,k);finishFight()}
        fun walkTo(tx:Int,ty:Int){
            val scene=v.world.scene;val goal=ty*scene.width+tx
            var attempts=0
            while(v.world.mapId==scene.mapId && v.world.y/16*scene.width+v.world.x/16!=goal){
                assertTrue("Normal route did not converge",attempts++<200)
                val start=v.world.y/16*scene.width+v.world.x/16
                val queue=java.util.ArrayDeque<Int>();queue.add(start)
                val parent=mutableMapOf(start to (-1 to Key.UP))
                while(queue.isNotEmpty() && goal !in parent){
                    val at=queue.removeFirst();val x=at%scene.width;val y=at/scene.width
                    for((key,delta)in listOf(Key.UP to (0 to -1),Key.DOWN to (0 to 1),Key.LEFT to (-1 to 0),Key.RIGHT to (1 to 0))){
                        val nx=x+delta.first;val ny=y+delta.second
                        if(scene.probeFrom(x,y,key)!=MovementBlock.NONE)continue
                        val next=ny*scene.width+nx
                        if(next in parent || (next!=goal && v.content.exits.any{it.fromMapId==scene.mapId && it.triggerX==nx&&it.triggerY==ny&&it.edgeDirection==null}))continue
                        parent[next]=at to key;queue.add(next)
                    }
                }
                assertTrue("No normal path to $tx,$ty on map ${scene.mapId}",goal in parent)
                var cursor=goal
                while(parent.getValue(cursor).first!=start)cursor=parent.getValue(cursor).first
                step(parent.getValue(cursor).second)
            }
        }
        fun talk(){tap(v,center(layoutFor(v).buttons.getValue(Key.A)));repeat(12){if(v.layer==GameView.Layer.DIALOGUE)tap(v,Pair(v.width*.5f,v.height*.5f))}}
        walkTo(7,16);talk();assertEquals(100,v.currentSnapshot().money)
        walkTo(11,22);talk();assertEquals(1,v.currentSnapshot().inventory[OpeningEquipment.KNIFE_ID])
        walkTo(8,29);assertEquals(16,v.world.mapId)
        // Initial equipment already includes a knife. Reach optional supply with real
        // escape commands for risky groups; no forced win, encounter or HP edits.
        approachingVillage=true;injuryState("normal-town-approach-start")
        walkTo(202,130);assertEquals("Town entrance at actual ${v.world.x/16},${v.world.y/16}",0,v.world.mapId)
        approachingVillage=false;injuryState("normal-town-approach-end")
        capture("town")
        for(mid in listOf(17,18,19)){
            val entry=v.content.exits.first{it.fromMapId==0 && it.toMapId==mid}
            walkTo(entry.triggerX,entry.triggerY)
            assertEquals("Shop entry $mid at ${v.world.mapId}:${v.world.x/16},${v.world.y/16}, remaining=${v.world.remaining}, seq=${v.world.completedStepSeq}",mid,v.world.mapId)
            val keeper=v.content.npcs.first{it.mapId==mid};walkTo(keeper.interactionCell!!.first,keeper.interactionCell.second)
            talk();assertEquals(GameView.Layer.SHOP,v.layer);assertFalse(v.visibleMapControls());assertNull(v.input.direction())
            fun action(i:Int)=tap(v,center(v.shopActionBounds(i)))
            action(1)
            val definition=v.content.shops.values.first{it.mapId==mid};val item=v.content.itemDefinitions.getValue(definition.items[if(mid==17)1 else 0])
            scrollToShopItem(v,item.id);tap(v,center(v.shopItemBounds(item.id)));capture("shop$mid")
            val before=v.currentSnapshot()
            if(mid==17){
                val point=center(v.shopActionBounds(4));send(v,MotionEvent.ACTION_DOWN,listOf(point))
                instrumentation.runOnMainSync{instrumentation.callActivityOnPause(activity)}
                instrumentation.runOnMainSync{instrumentation.callActivityOnResume(activity)}
                send(v,MotionEvent.ACTION_UP,listOf(point));assertEquals(before,v.currentSnapshot())
            }
            action(4)
            val once=v.currentSnapshot();assertEquals(before.money-item.buyPrice!!,once.money)
            assertEquals((before.inventory[item.id]?:0)+1,once.inventory[item.id])
            repeat(10){send(v,MotionEvent.ACTION_UP,listOf(center(v.shopActionBounds(4))))};assertEquals(once,v.currentSnapshot())
            assertEquals(before.characters,once.characters) // Native purchase does not auto-equip.
            capture("bought$mid")
            if(touchUx&&mid==17){
                action(2);tap(v,center(v.shopItemBounds(OpeningEquipment.KNIFE_ID)));val saleBefore=v.currentSnapshot()
                action(4);assertEquals(saleBefore.money+7,v.currentSnapshot().money)
                assertEquals(0,v.currentSnapshot().inventory[OpeningEquipment.KNIFE_ID]?:0);capture("touch-ux-sold")
            }
            if(mid==19 && !useHerb){
                action(2);scrollToShopItem(v,item.id);tap(v,center(v.shopItemBounds(item.id)));capture("sell");val prior=v.currentSnapshot();action(4)
                assertEquals(prior.money+7,v.currentSnapshot().money)
                assertEquals((prior.inventory[item.id]?:0)-1,v.currentSnapshot().inventory[item.id]?:0)
                val sold=v.currentSnapshot();repeat(10){send(v,MotionEvent.ACTION_UP,listOf(center(v.shopActionBounds(4))))};assertEquals(sold,v.currentSnapshot())
            }
            action(3);assertEquals(GameView.Layer.MAP,v.layer)
            val exit=v.content.exits.first{it.fromMapId==mid};walkTo(exit.triggerX,exit.triggerY)
            assertEquals(0,v.world.mapId);assertEquals(entry.triggerX,v.world.x/16);assertEquals(entry.triggerY,v.world.y/16)
            capture("returned$mid")
        }
        if(room28){
            normalTownRoom28OnView(activity,v,::walkTo)
            return
        }
        if(touchUx){
            tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));scrollToItem(v,"rom.weapon.1")
            val before=v.currentSnapshot();scrollToItem(v,"rom.weapon.1");tap(v,center(v.panelItemBounds("rom.weapon.1")));assertEquals(before,v.currentSnapshot())
            tap(v,center(v.panelPrimaryBounds()));assertEquals(1,v.currentSnapshot().characters.first().equipment!!.rightHand);capture("touch-ux-equipped")
            tap(v,tabPoint(v,1));tap(v,center(v.panelSlotBounds("rightHand")));tap(v,center(v.panelPrimaryBounds()))
            assertEquals(-1,v.currentSnapshot().characters.first().equipment!!.rightHand);capture("touch-ux-unequipped")
            tap(v,tabPoint(v,2));scrollToItem(v,"rom.weapon.1");tap(v,center(v.panelItemBounds("rom.weapon.1")));tap(v,center(v.panelPrimaryBounds()))
            instrumentation.runOnMainSync{v.handleBack()}
            File(instrumentation.targetContext.getExternalFilesDir(null),"touch-ux-after-counts.json").writeText("""{"kind":"NORMAL_APP_FLOW","buyNonFirstFromBuyList":2,"sellFromSellList":2,"replaceFromInventoryList":2,"unequipFromSlotList":2,"useFromItemList":2,"definition":"actual taps; list already open, role selected"}""")
        }
        if(innSupply){
            val entry=v.content.exits.first{it.fromMapId==0&&it.toMapId==22}
            walkTo(entry.triggerX,entry.triggerY);assertEquals(22,v.world.mapId)
            capture("inn-room")
            val keeper=v.content.npcs.first{it.innId=="rom.inn.0"}
            walkTo(keeper.interactionCell!!.first,keeper.interactionCell.second)
            talk();assertEquals(GameView.Layer.INN,v.layer);assertFalse(v.visibleMapControls())
            val before=v.currentSnapshot();capture("inn-offer")
            instrumentation.runOnMainSync{v.handleBack()};assertEquals(before,v.currentSnapshot())
            talk();val button=center(v.innStayBounds())
            send(v,MotionEvent.ACTION_DOWN,listOf(button));send(v,MotionEvent.ACTION_CANCEL,listOf(button))
            assertEquals(before,v.currentSnapshot())
            tap(v,button);assertEquals(GameView.Layer.MAP,v.layer)
            val expected=InnStay.apply(before.money,before.characters,v.content.inns.getValue("rom.inn.0"))
            assertNull(expected.error);assertEquals(expected.money,v.currentSnapshot().money)
            assertEquals(expected.characters,v.currentSnapshot().characters)
            assertEquals(before.inventory,v.currentSnapshot().inventory);assertEquals(before.flags,v.currentSnapshot().flags)
            val paid=v.currentSnapshot();repeat(10){send(v,MotionEvent.ACTION_UP,listOf(button))};assertEquals(paid,v.currentSnapshot())
            capture("inn-restored")
            walkTo(12,14);assertEquals(0,v.world.mapId);assertEquals(6,v.world.x/16);assertEquals(25,v.world.y/16)
            capture("inn-returned")
        }
        if(useHerb){
            // Only normal movement and battle commands create the injury. No HP/item fixture.
            if(v.currentSnapshot().characters.first().hp==v.currentSnapshot().characters.first().maxHp){
                walkTo(0,14);step(Key.LEFT);assertEquals(16,v.world.mapId)
                walkTo(200,130)
                seekingInjury=true;injuryState("normal-injury-preparation-start")
                for(i in 0 until 320){
                    val hero=v.currentSnapshot().characters.first()
                    if(hero.hp in 1 until hero.maxHp)break
                    val key=if(v.world.y/16==130)Key.UP else Key.DOWN
                    step(key)
                }
                seekingInjury=false;injuryState("normal-injury-preparation-end")
                assertTrue("Normal encounters must produce a living injured hero; see town01-normal-injury-attempts.json",v.currentSnapshot().characters.first().let{it.hp in 1 until it.maxHp})
                walkTo(202,130);assertEquals(0,v.world.mapId)
            }
            val before=v.currentSnapshot();val hero=before.characters.first()
            assertTrue(hero.hp in 1 until hero.maxHp)
            assertEquals(1,before.inventory[HerbUse.ID])
            tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2))
            val panel=v.characterPanelBounds()
            val index=before.inventory.filterValues{it>0}.toSortedMap().keys.indexOf(HerbUse.ID)
            assertTrue(index in 0..3)
            scrollToItem(v,HerbUse.ID)
            val row=center(v.panelItemBounds(HerbUse.ID))
            val action=center(v.panelPrimaryBounds())
            tap(v,row);capture("herb-selected")
            send(v,MotionEvent.ACTION_DOWN,listOf(action));send(v,MotionEvent.ACTION_CANCEL,listOf(action))
            assertEquals(before,v.currentSnapshot())
            send(v,MotionEvent.ACTION_DOWN,listOf(action))
            instrumentation.runOnMainSync{instrumentation.callActivityOnPause(activity);instrumentation.callActivityOnResume(activity)}
            send(v,MotionEvent.ACTION_UP,listOf(action));assertEquals(before,v.currentSnapshot())
            tap(v,action)
            val used=v.currentSnapshot()
            assertEquals(minOf(hero.maxHp,hero.hp+50),used.characters.first().hp)
            assertEquals(0,used.inventory[HerbUse.ID]?:0)
            assertEquals(before.money,used.money);assertEquals(before.flags,used.flags)
            repeat(10){send(v,MotionEvent.ACTION_UP,listOf(action))};assertEquals(used,v.currentSnapshot())
            capture("herb-used")
            instrumentation.runOnMainSync{v.handleBack();v.persistState();activity.finish()}
            val(restarted,reloaded)=launch()
            assertEquals(used,reloaded.currentSnapshot())
            capture("herb-restored")
            File(instrumentation.targetContext.getExternalFilesDir(null),"town02-expected-save.json").writeText(used.json().toString())
            instrumentation.runOnMainSync{restarted.finish()}
            return
        }
        tap(v,center(v.hudBounds()));tap(v,tabPoint(v,1));capture("equipment")
        val state=v.currentSnapshot();assertEquals(EquipmentState(0,-1,0,28),state.characters.first().equipment)
        assertEquals(true,state.flags["rom.npc.114.2"]);assertEquals(1,state.inventory[OpeningEquipment.KNIFE_ID])
        val panel=v.characterPanelBounds()
        tap(v,center(v.panelPrimaryBounds())) // Existing explicit remove action.
        assertEquals(-1,v.currentSnapshot().characters.first().equipment!!.rightHand)
        tap(v,tabPoint(v,2));capture("inventory")
        val index=v.currentSnapshot().inventory.filterValues{it>0}.toSortedMap().keys.indexOf("rom.weapon.1")
        assertTrue(index in 0..3)
        scrollToItem(v,"rom.weapon.1");tap(v,center(v.panelItemBounds("rom.weapon.1")))
        tap(v,center(v.panelPrimaryBounds()))
        assertEquals(1,v.currentSnapshot().characters.first().equipment!!.rightHand)
        assertEquals(2,v.currentSnapshot().inventory[OpeningEquipment.KNIFE_ID])
        assertEquals(0,v.currentSnapshot().inventory["rom.weapon.1"]?:0)
        tap(v,tabPoint(v,1));capture("equipped-handknife")
        instrumentation.runOnMainSync{v.handleBack()}
        instrumentation.runOnMainSync{v.persistState();activity.finish()}
    }
    /** Continues the actual new-game town route; no restoreSnapshot or state grants. */
    private fun normalTownRoom28OnView(activity:MainActivity,v:GameView,walk:(Int,Int)->Unit){
        val root=instrumentation.targetContext.getExternalFilesDir(null)!!
        assertEquals(0,v.world.mapId)
        walk(12,24);stickStep(v,Key.UP)
        assertEquals(28,v.world.mapId);assertEquals(6 to 10,v.world.x/16 to v.world.y/16)
        assertEquals(InteriorContext(0,12,23),v.world.interiorContext)
        screenshot(v,"world-room28-normal-original-door-entry")
        // Approach on a legal final UP step; stickStep requires a completed
        // move and must not be used to face into an original blocked NPC cell.
        walk(4,7);stickStep(v,Key.UP)
        val hintBefore=v.currentSnapshot();val action=center(layoutFor(v).buttons.getValue(Key.A))
        tap(v,action);assertEquals(GameView.Layer.DIALOGUE,v.layer)
        assertEquals(hintBefore,v.currentSnapshot())
        screenshot(v,"world-room28-normal-read-only-hint")
        hardwareButton(v,android.view.KeyEvent.KEYCODE_BUTTON_B)
        assertEquals(GameView.Layer.MAP,v.layer);assertEquals(hintBefore,v.currentSnapshot())
        walk(5,4);stickStep(v,Key.RIGHT)
        val before=v.currentSnapshot()
        val hidden=v.content.npcs.single{it.id=="rom.npc.28.1"}
        assertEquals(HerbUse.ID,hidden.treasure!!.itemId)
        assertTrue(before.flags["rom.map.28.flag.1"]!=true)
        val quantity=before.inventory[HerbUse.ID]?:0
        assertTrue(quantity in 0..9)
        // Expected grant comes from the executed original CPU/native evidence,
        // not from calling the same transaction as the App under test.
        val expectedInventory=before.inventory+(HerbUse.ID to quantity+1)
        val expectedFlags=before.flags+("rom.map.28.flag.1" to true)
        send(v,MotionEvent.ACTION_DOWN,listOf(action));send(v,MotionEvent.ACTION_CANCEL,listOf(action))
        assertEquals(before,v.currentSnapshot())
        tap(v,action);val after=v.currentSnapshot()
        assertEquals(before.copy(inventory=expectedInventory,flags=expectedFlags),after)
        repeat(4){send(v,MotionEvent.ACTION_UP,listOf(action))};assertEquals(after,v.currentSnapshot())
        tap(v,action);assertEquals(after,v.currentSnapshot())
        assertEquals(MovementBlock.PHYSICAL,v.world.scene.probeFrom(6,4,Key.RIGHT))
        screenshot(v,"world-room28-normal-hidden-herb-once")
        walk(6,10);assertEquals(0,v.world.mapId)
        assertEquals(12 to 23,v.world.x/16 to v.world.y/16);assertNull(v.world.interiorContext)
        assertEquals(after.inventory,v.currentSnapshot().inventory)
        assertEquals(after.money,v.currentSnapshot().money);assertEquals(after.characters,v.currentSnapshot().characters)
        screenshot(v,"world-room28-normal-return-original-door")
        stickStep(v,Key.DOWN);stickStep(v,Key.UP)
        assertEquals(28,v.world.mapId);walk(6,7)
        val saved=v.currentSnapshot()
        instrumentation.runOnMainSync{v.persistState()}
        val persisted=SaveSnapshot.parse(instrumentation.targetContext.getSharedPreferences("opening-local-save",0).getString("saveJson",null)!!)
        assertEquals(saved,persisted)
        File(root,"world-room28-expected-save.json").writeText(saved.json().toString())
        File(root,"world-room28-normal-index.json").writeText(JSONObject()
            .put("kind","NORMAL_NEW_GAME_GAMEVIEW_TOUCH_INPUTS")
            .put("stateGrants",false).put("restoredFixture",false)
            .put("expectedSave",saved.json()).toString())
        screenshot(v,"world-room28-normal-saved-interior")
        instrumentation.runOnMainSync{activity.finish()}
    }
    private fun screenshot(v:GameView,name:String){
        SystemClock.sleep(350)
        File(instrumentation.targetContext.getExternalFilesDir(null),name+".png").outputStream().use{
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun scrollToItem(v:GameView,id:String){
        val list=v.panelListBounds();val dp=v.resources.displayMetrics.density
        val layout=GameView::class.java.getDeclaredMethod("modalLayout").apply{isAccessible=true}.invoke(v) as TouchModalLayout
        val distance=minOf(list.h*.64f,64*dp)
        val itemCount=v.currentSnapshot().inventory.count{it.value>0}
        // The expanded catalogue needs more travel than the original fixed
        // 60 gestures. Derive a finite budget from the real shared geometry.
        val budget=maxOf(60,kotlin.math.ceil(layout.maxScroll(itemCount)/distance).toInt()+4)
        for(i in 0..budget){
            if(v.panelItemBounds(id).h>=48*dp)return
            val down=Pair(list.x+list.w*.5f,list.y+list.h*.82f);val up=Pair(down.first,down.second-distance)
            send(v,MotionEvent.ACTION_DOWN,listOf(down));send(v,MotionEvent.ACTION_MOVE,listOf(up));send(v,MotionEvent.ACTION_UP,listOf(up))
        }
        screenshot(v,"touch-ux-scroll-failure")
        fail("Item $id never reached an accessible row: row=${v.panelItemBounds(id)}, list=$list, items=$itemCount, budget=$budget, layer=${v.layer}, active=${v.active}, focused=${v.focused}, view=${v.width}x${v.height}, density=$dp, font=${v.resources.configuration.fontScale}")
    }
    private fun scrollToShopItem(v:GameView,id:String){
        val list=v.panelListBounds();val dp=v.resources.displayMetrics.density
        for(i in 0..12){
            val row=v.shopItemBounds(id)
            if(row.h>=48*dp)return
            val down=Pair(list.x+list.w*.5f,list.y+list.h*.82f);val up=Pair(down.first,list.y+list.h*.18f)
            send(v,MotionEvent.ACTION_DOWN,listOf(down));send(v,MotionEvent.ACTION_MOVE,listOf(up));send(v,MotionEvent.ACTION_UP,listOf(up))
        };fail("Shop item $id never reached an accessible row: ${v.shopItemBounds(id)}, view=${v.width}x${v.height}, density=$dp")
    }
    private fun scrollToBattleItem(v:GameView,id:String){
        val method=GameView::class.java.getDeclaredMethod("battleItemLayout").apply{isAccessible=true}
        val dp=v.resources.displayMetrics.density
        for(i in 0..24){
            if(v.battleItemBounds(id).h>=48*dp)return
            val list=(method.invoke(v) as TouchModalLayout).list
            val down=Pair(list.x+list.w*.5f,list.y+list.h*.82f);val up=Pair(down.first,list.y+list.h*.18f)
            send(v,MotionEvent.ACTION_DOWN,listOf(down));send(v,MotionEvent.ACTION_MOVE,listOf(up));send(v,MotionEvent.ACTION_UP,listOf(up))
        };fail("Original special $id never reached a real touch row")
    }
    fun testTouchUxSelectionScrollAndAtomicEquipment(){
        val(activity,v)=launch();val base=v.currentSnapshot()
        val bag=v.content.itemDefinitions.keys.associateWith{2}
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(base.copy(inventory=bag,money=1000)))}
        tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));val initial=v.currentSnapshot()
        tap(v,center(v.panelItemBounds("rom.armor.0")));assertEquals(initial,v.currentSnapshot())
        scrollToItem(v,"rom.weapon.2");assertEquals(initial,v.currentSnapshot())
        tap(v,center(v.panelItemBounds("rom.weapon.2")));assertEquals(initial,v.currentSnapshot())
        val hero=initial.characters.first();val old=v.content.equipmentDefinitions.getValue(OpeningEquipment.KNIFE_ID)
        val next=v.content.equipmentDefinitions.getValue("rom.weapon.2")
        val removed=OpeningEquipment.unequip(hero,initial.inventory,old)!!
        val legacy=OpeningEquipment.equip(removed.first,removed.second,next)!!
        tap(v,center(v.panelPrimaryBounds()))
        assertEquals(legacy.first,v.currentSnapshot().characters.first());assertEquals(legacy.second,v.currentSnapshot().inventory)
        val once=v.currentSnapshot();repeat(10){send(v,MotionEvent.ACTION_UP,listOf(center(v.panelPrimaryBounds())))};assertEquals(once,v.currentSnapshot())
        tap(v,tabPoint(v,1));tap(v,center(v.panelSlotBounds("rightHand")))
        val beforeRemove=v.currentSnapshot();tap(v,center(v.panelPrimaryBounds()))
        val result=OpeningEquipment.unequip(beforeRemove.characters.first(),beforeRemove.inventory,next)!!
        assertEquals(result.first,v.currentSnapshot().characters.first());assertEquals(result.second,v.currentSnapshot().inventory)
        tap(v,center(v.panelSecondaryBounds()));assertEquals(GameView.CharacterTab.ITEMS,v.activeCharacterTab)
        assertEquals(-1,v.currentSnapshot().characters.first().equipment!!.rightHand)
        instrumentation.runOnMainSync{activity.onBackPressed();assertNull(v.input.direction());activity.finish()}
    }
    fun testTouchUxTradeGesturesAndResultEquivalence(){
        val(activity,v)=launch();val base=v.currentSnapshot()
        val bag=mapOf(OpeningEquipment.KNIFE_ID to 1,"rom.weapon.1" to 1,"rom.weapon.2" to 1)
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(base.copy(mapId=17,x=7*16+8,y=7*16+8,money=1000,inventory=bag,interiorContext=InteriorContext(0,13,18))))}
        tap(v,center(layoutFor(v).buttons.getValue(Key.A)));assertEquals(GameView.Layer.SHOP,v.layer)
        val initial=v.currentSnapshot();val item=v.content.itemDefinitions.getValue("rom.weapon.1")
        val shop=v.content.shops.values.first{it.mapId==17}
        scrollToShopItem(v,item.id);tap(v,center(v.shopItemBounds(item.id)));assertEquals(initial,v.currentSnapshot())
        val p=center(v.shopActionBounds(4));val off=Pair(p.first,p.second-64*v.resources.displayMetrics.density)
        send(v,MotionEvent.ACTION_DOWN,listOf(p));send(v,MotionEvent.ACTION_MOVE,listOf(off));send(v,MotionEvent.ACTION_MOVE,listOf(p));send(v,MotionEvent.ACTION_UP,listOf(p));assertEquals(initial,v.currentSnapshot())
        send(v,MotionEvent.ACTION_DOWN,listOf(p));send(v,MotionEvent.ACTION_POINTER_DOWN or(1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(p,p));send(v,MotionEvent.ACTION_POINTER_UP or(1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(p,p));send(v,MotionEvent.ACTION_UP,listOf(p));assertEquals(initial,v.currentSnapshot())
        tap(v,p);val expected=TownTrade.buy(initial.money,initial.inventory,shop,item)
        assertEquals(expected.money,v.currentSnapshot().money);assertEquals(expected.inventory,v.currentSnapshot().inventory)
        val once=v.currentSnapshot();repeat(10){send(v,MotionEvent.ACTION_UP,listOf(p))};assertEquals(once,v.currentSnapshot())
        tap(v,p);assertEquals(once.money-50,v.currentSnapshot().money);assertEquals(3,v.currentSnapshot().inventory[item.id])
        tap(v,center(v.shopActionBounds(2)));scrollToShopItem(v,"rom.weapon.2");tap(v,center(v.shopItemBounds("rom.weapon.2")))
        val beforeSell=v.currentSnapshot();tap(v,center(v.shopActionBounds(4)))
        val sale=TownTrade.sell(beforeSell.money,beforeSell.inventory,shop,v.content.itemDefinitions.getValue("rom.weapon.2"))
        assertEquals(sale.money,v.currentSnapshot().money);assertEquals(sale.inventory,v.currentSnapshot().inventory)
        val last=v.currentSnapshot();repeat(10){send(v,MotionEvent.ACTION_UP,listOf(p))};tap(v,p);assertEquals(last,v.currentSnapshot()) // Removed item never transfers selection.
        scrollToShopItem(v,item.id);tap(v,center(v.shopItemBounds(item.id)));tap(v,p);assertEquals(2,v.currentSnapshot().inventory[item.id])
        tap(v,center(v.shopActionBounds(1)));scrollToShopItem(v,item.id);tap(v,center(v.shopItemBounds(item.id)))
        send(v,MotionEvent.ACTION_DOWN,listOf(p))
        val changed=v.currentSnapshot().copy(money=0)
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(changed))}
        send(v,MotionEvent.ACTION_UP,listOf(p));assertEquals(changed,v.currentSnapshot())
        scrollToShopItem(v,item.id);tap(v,center(v.shopItemBounds(item.id)));tap(v,p);assertEquals(changed,v.currentSnapshot())
        screenshot(v,"touch-ux-trade-failure")
        tap(v,center(v.shopActionBounds(3)));assertEquals(GameView.Layer.MAP,v.layer);assertNull(v.input.direction())
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testTouchUxPhoneSizeAndLargeFont(){
        val(activity,v)=launch();val initial=v.currentSnapshot();val bag=v.content.itemDefinitions.keys.associateWith{2}
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(initial.copy(inventory=bag,money=1000)))}
        tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2))
        val dp=v.resources.displayMetrics.density;val font=v.resources.configuration.fontScale
        val screen=instrumentation.uiAutomation.takeScreenshot()
        assertEquals("Actual display width, not only wm override",2640,screen.width)
        assertEquals("Actual display height",1216,screen.height)
        fun verifyRegions(secondary:Boolean,tabs:Int=4,party:Int=1){
            val modal=touchModalLayout(layoutFor(v).safe,dp,font,tabs,party,secondary)
            val targets=modal.tabs+listOf(modal.close,modal.primary)+if(secondary)listOf(modal.secondary) else emptyList()
            for(i in targets.indices)for(j in i+1 until targets.size){val a=targets[i];val b=targets[j]
                assertFalse("Overlapping actual phone targets: $a / $b",a.x<b.x+b.w&&a.x+a.w>b.x&&a.y<b.y+b.h&&a.y+a.h>b.y)}
            assertTrue("A full scaled text line must fit in scrollable details",modal.detail.h>=17.5f*font*dp)
        }
        verifyRegions(false)
        screenshot(v,"touch-ux-phone-entry-$font")
        scrollToItem(v,HerbUse.ID);tap(v,center(v.panelItemBounds(HerbUse.ID)))
        assertTrue(v.panelItemBounds(HerbUse.ID).h>=48*dp);assertTrue(v.panelPrimaryBounds().w>=48*dp);assertTrue(v.panelPrimaryBounds().h>=48*dp)
        screenshot(v,"touch-ux-phone-items-$font")
        tap(v,tabPoint(v,1));tap(v,center(v.panelSlotBounds("rightHand")))
        verifyRegions(true)
        val a=v.panelPrimaryBounds();val b=v.panelSecondaryBounds()
        assertTrue(a.w>=48*dp&&a.h>=48*dp&&b.w>=48*dp&&b.h>=48*dp)
        assertTrue(a.x+a.w<=b.x);screenshot(v,"touch-ux-phone-equipment-$font")
        instrumentation.runOnMainSync{activity.onBackPressed();assertTrue(v.restoreSnapshot(initial.copy(mapId=17,x=7*16+8,y=7*16+8,money=1000,inventory=bag,interiorContext=InteriorContext(0,13,18))))}
        tap(v,center(layoutFor(v).buttons.getValue(Key.A)));scrollToShopItem(v,"rom.weapon.1");tap(v,center(v.shopItemBounds("rom.weapon.1")))
        verifyRegions(false,2,0)
        val unchanged=v.currentSnapshot();screenshot(v,"touch-ux-phone-shop-$font");tap(v,center(v.shopActionBounds(4)))
        assertEquals(unchanged.money-50,v.currentSnapshot().money)
        File(instrumentation.targetContext.getExternalFilesDir(null),"touch-ux-phone-$font.json").writeText("""{"screenWidth":${screen.width},"screenHeight":${screen.height},"width":${v.width},"height":${v.height},"density":$dp,"fontScale":$font,"minTouchDp":48,"kind":"EMULATOR_SIZE_VALIDATION"}""")
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testTouchUxBaselineClickPath(){
        // Runs only on the actual previous APK, with a labeled, isolated comparison fixture.
        val(activity,v)=launch();val base=v.currentSnapshot();val bag=mapOf(OpeningEquipment.KNIFE_ID to 1,"rom.weapon.1" to 1)
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(base.copy(mapId=17,x=7*16+8,y=7*16+8,money=1000,inventory=bag,interiorContext=InteriorContext(0,13,18))))}
        tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
        fun action(i:Int){tap(v,center(v.shopActionBounds(i)));SystemClock.sleep(400)}
        action(1);val beforeBuy=v.currentSnapshot();action(8);action(4);action(6)
        assertEquals(beforeBuy.money-50,v.currentSnapshot().money)
        action(5);action(2);val beforeSell=v.currentSnapshot();action(4);action(6)
        assertEquals(beforeSell.money+7,v.currentSnapshot().money)
        action(5);action(3)
        fun oldTab(i:Int){val p=v.characterPanelBounds();val dp=v.resources.displayMetrics.density;val w=(p.w-16*dp)/4
            tap(v,Pair(p.x+8*dp+w*(i+.5f),p.y+p.h*.4525f));SystemClock.sleep(400)}
        tap(v,center(v.hudBounds()));oldTab(2) // Comparison begins with inventory open, role selected.
        val p=v.characterPanelBounds();val equipmentAction=Pair(p.x+p.w/2,p.y+p.h*.8975f)
        oldTab(1);tap(v,equipmentAction);SystemClock.sleep(400);oldTab(2)
        tap(v,Pair(p.x+p.w/2,p.y+p.h*.63f));SystemClock.sleep(400);tap(v,equipmentAction);SystemClock.sleep(400)
        assertEquals(1,v.currentSnapshot().characters.first().equipment!!.rightHand)
        File(instrumentation.targetContext.getExternalFilesDir(null),"touch-ux-before-counts.json").writeText("""{"kind":"CONTROLLED_UI_COMPARISON","buyNonFirstFromBuyList":3,"sellSelectedFromSellList":2,"replaceFromInventoryList":5,"definition":"actual taps; list already open, role selected"}""")
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testNormalTouchUxSupplyAndEquipment(){normalTownShops(true,true)}
    fun testNormalWorldFullCurrentServices(){normalTownShops(true,true,true)}
    fun testControlledInnTransactionsAndGestureSafety(){
        val(activity,v)=launch();val baseline=v.currentSnapshot();val service=v.content.inns.getValue("rom.inn.0")
        fun fixture(money:Int,status:Int=0,hp:Int=5){
            instrumentation.runOnMainSync{
                if(v.layer!=GameView.Layer.MAP)v.handleBack()
                val hero=v.content.initialPlayer.copy(hp=hp,maxHp=100,mp=1,maxMp=10,statusMask=status)
                assertTrue(v.restoreSnapshot(baseline.copy(mapId=22,x=12*16+8,y=7*16+8,money=money,characters=listOf(hero),interiorContext=InteriorContext(0,6,25))))
            }
            tap(v,center(layoutFor(v).buttons.getValue(Key.A)));assertEquals(GameView.Layer.INN,v.layer)
        }
        fixture(3);val poor=v.currentSnapshot();tap(v,center(v.innStayBounds()));assertEquals(poor,v.currentSnapshot())
        fixture(315);val before=v.currentSnapshot();val action=center(v.innStayBounds())
        send(v,MotionEvent.ACTION_DOWN,listOf(action));send(v,MotionEvent.ACTION_POINTER_DOWN or(1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(action,action))
        send(v,MotionEvent.ACTION_POINTER_UP or(1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(action,action));send(v,MotionEvent.ACTION_UP,listOf(action))
        assertEquals(before,v.currentSnapshot());assertEquals(GameView.Layer.INN,v.layer)
        send(v,MotionEvent.ACTION_DOWN,listOf(action));instrumentation.runOnMainSync{instrumentation.callActivityOnPause(activity);instrumentation.callActivityOnResume(activity)}
        send(v,MotionEvent.ACTION_UP,listOf(action));assertEquals(before,v.currentSnapshot())
        tap(v,action);assertEquals(311,v.currentSnapshot().money);assertEquals(100,v.currentSnapshot().characters.first().hp)
        assertEquals(10,v.currentSnapshot().characters.first().mp)
        for(status in listOf(2,4,16,32,64)){
            fixture(315,status,if(status==32)0 else 5);val old=v.currentSnapshot();tap(v,center(v.innStayBounds()))
            val result=InnStay.apply(old.money,old.characters,service)
            assertEquals(result.money,v.currentSnapshot().money);assertEquals(result.characters,v.currentSnapshot().characters)
            assertEquals(v.currentSnapshot(),SaveSnapshot.parse(v.currentSnapshot().json().toString()))
        }
        val saved=v.currentSnapshot();instrumentation.runOnMainSync{v.persistState();activity.finish()}
        val(reopened,reloaded)=launch();assertEquals(saved,reloaded.currentSnapshot());instrumentation.runOnMainSync{reopened.finish()}
    }
    /** Isolated real UI gestures; not normal acquisition, death or poison evidence. */
    fun testControlledMedicalCommandsCancellationGestureAndSave(){
        val(activity,v)=launch();val baseline=v.currentSnapshot()
        val caller=if(v.content.scenes.containsKey(3))3 else 2
        val door=if(caller==3)7 to 18 else 22 to 26
        fun fixture(role:String,status:Int,hp:Int=0,money:Int=887){
            instrumentation.runOnMainSync{
                if(v.layer!=GameView.Layer.MAP)v.handleBack()
                val girl=v.content.joinCharacters.getValue("xiaolongnv").copy(hp=hp,statusMask=status)
                val point=if(role=="revival")3 to 7 else 13 to 5
                assertTrue(v.restoreSnapshot(baseline.copy(mapId=20,x=point.first*16+8,y=point.second*16+8,
                    money=money,characters=listOf(v.content.initialPlayer.copy(hp=minOf(5,v.content.initialPlayer.maxHp)),girl),
                    interiorContext=InteriorContext(caller,door.first,door.second))))
            }
            tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
            assertEquals(GameView.Layer.INN,v.layer);assertEquals("rom.clinic.$caller.$role",v.activeClinicId)
        }
        fixture("revival",32);val before=v.currentSnapshot()
        tap(v,center(v.clinicTargetBounds("xiaolongnv")));assertEquals(before,v.currentSnapshot())
        val action=center(v.clinicReviveBounds())
        send(v,MotionEvent.ACTION_DOWN,listOf(action));send(v,MotionEvent.ACTION_CANCEL,listOf(action))
        send(v,MotionEvent.ACTION_UP,listOf(action));assertEquals(before,v.currentSnapshot())
        send(v,MotionEvent.ACTION_DOWN,listOf(action));instrumentation.runOnMainSync{instrumentation.callActivityOnPause(activity);instrumentation.callActivityOnResume(activity)}
        send(v,MotionEvent.ACTION_UP,listOf(action));assertEquals(before,v.currentSnapshot())
        send(v,MotionEvent.ACTION_DOWN,listOf(action));send(v,MotionEvent.ACTION_POINTER_DOWN or(1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(action,action))
        send(v,MotionEvent.ACTION_POINTER_UP or(1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(action,action));send(v,MotionEvent.ACTION_UP,listOf(action))
        assertEquals(before,v.currentSnapshot())
        instrumentation.runOnMainSync{v.handleBack()};assertEquals(before,v.currentSnapshot())
        fixture("revival",32);tap(v,center(v.clinicTargetBounds("xiaolongnv")))
        val expected=ClinicRevival.apply(v.currentSnapshot().money,v.currentSnapshot().characters,"xiaolongnv",v.content.clinics.getValue("rom.clinic.$caller.revival"))
        tap(v,center(v.clinicReviveBounds()));assertEquals(GameView.Layer.MAP,v.layer)
        assertEquals(expected.money,v.currentSnapshot().money);assertEquals(expected.characters,v.currentSnapshot().characters)
        val saved=v.currentSnapshot();send(v,MotionEvent.ACTION_UP,listOf(action));assertEquals(saved,v.currentSnapshot())
        assertEquals(saved,SaveSnapshot.parse(instrumentation.targetContext.getSharedPreferences("opening-local-save",0).getString("saveJson",null)!!))
        fixture("care",34);tap(v,center(v.clinicTreatmentBounds("poison")))
        val poisoned=v.currentSnapshot();tap(v,center(v.clinicTargetBounds("xiaolongnv")));assertEquals(poisoned,v.currentSnapshot())
        tap(v,center(v.clinicReviveBounds()));assertEquals(poisoned.money-2,v.currentSnapshot().money)
        assertEquals(32,v.currentSnapshot().characters[1].statusMask);assertEquals(0,v.currentSnapshot().characters[1].hp)
        fixture("care",4,29);tap(v,center(v.clinicTreatmentBounds("confusion")))
        tap(v,center(v.clinicTargetBounds("xiaolongnv")));tap(v,center(v.clinicReviveBounds()))
        assertEquals(884,v.currentSnapshot().money);assertEquals(0,v.currentSnapshot().characters[1].statusMask)
        assertEquals(29,v.currentSnapshot().characters[1].hp)
        fixture("care",2,29,1);tap(v,center(v.clinicTreatmentBounds("poison")))
        val poor=v.currentSnapshot();tap(v,center(v.clinicTargetBounds("xiaolongnv")));tap(v,center(v.clinicReviveBounds()))
        assertEquals(poor,v.currentSnapshot());instrumentation.runOnMainSync{v.handleBack()}
        val persisted=v.currentSnapshot();instrumentation.runOnMainSync{v.persistState();activity.finish()}
        val(reopened,reloaded)=launch();assertEquals(persisted,reloaded.currentSnapshot())
        instrumentation.runOnMainSync{reopened.finish()}
    }
    fun testControlledOneHitAndSameKindInstances(){
        val(activity,v)=launch();val rules=v.content.battle!!
        fun field(name:String,value:Any?){GameView::class.java.getDeclaredField(name).apply{isAccessible=true}.set(v,value)}
        fun prepare(group:EncounterGroup,hero:CharacterState):Pair<OpeningBattle,BattlePresentation>{
            val fight=OpeningBattle(group,rules,hero,2);val p=BattlePresentation()
            instrumentation.runOnMainSync{
                field("layer",GameView.Layer.MAP);assertTrue(v.restoreSnapshot(v.currentSnapshot().copy(characters=listOf(hero))))
                field("battle",fight);field("battlePresentation",p);field("battleCommitted",false)
                field("battleID","controlled-${group.id}");field("selectedBattleSlot",fight.enemies.first().slot)
                field("layer",GameView.Layer.BATTLE);v.input.clear();p.tick(400)
            }
            return fight to p
        }
        // Explicitly controlled high-attack fixture, not proof of the phone's Lv4 stats.
        val single=rules.groups.first{it.members.size==1}
        val (fight,p)=prepare(single,v.content.initialPlayer.copy(level=4,strength=40))
        tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
        assertEquals(BattlePresentation.Screen.TARGET,p.screen)
        tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
        assertEquals(BattlePhase.VICTORY,fight.phase);assertEquals(BattlePresentation.Screen.ACTING,p.screen)
        repeat(10){tap(v,center(layoutFor(v).buttons.getValue(Key.A)))}
        assertEquals(BattlePresentation.Screen.ACTING,p.screen)
        for(i in 0..200){if(p.screen==BattlePresentation.Screen.RESULT)break;SystemClock.sleep(25)}
        assertEquals(BattlePresentation.Screen.RESULT,p.screen)
        val settled=v.currentSnapshot();tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
        assertEquals(settled,v.currentSnapshot());assertEquals(GameView.Layer.MAP,v.layer)
        val twins=rules.groups.first{g->g.members.groupBy{it.enemyId}.values.any{it.size>1}}
        val (many,q)=prepare(twins,v.content.initialPlayer)
        val target=many.enemies.first();val oldHp=many.enemies.associate{it.slot to it.hp}
        tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
        tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
        assertEquals(BattlePresentation.Screen.ACTING,q.screen)
        assertTrue(target.hp<oldHp.getValue(target.slot))
        many.enemies.filter{it.slot!=target.slot}.forEach{assertEquals(oldHp.getValue(it.slot),it.hp)}
        assertTrue(many.enemies.any{it.hp>0});assertEquals(BattlePhase.TARGET,many.phase)
        instrumentation.runOnMainSync{activity.finish()}
    }
    private fun normalOpeningBattle(escapeOnly:Boolean){
        instrumentation.targetContext.getSharedPreferences("opening-local-save",0).edit().clear().commit()
        val (activity,v)=launch()
        fun capture(name:String,delay:Long=500){
            SystemClock.sleep(delay)
            val file=File(instrumentation.targetContext.getExternalFilesDir(null),"world01-${if(escapeOnly)"escape-" else ""}$name.png")
            file.outputStream().use{instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        capture("map114")
        listOf(Key.DOWN,Key.RIGHT,Key.RIGHT,Key.RIGHT).forEach{stickStep(v,it)}
        assertEquals(11*16+8,v.world.x);assertEquals(22*16+8,v.world.y)
        tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
        assertEquals(GameView.Layer.DIALOGUE,v.layer)
        repeat(10){if(v.layer==GameView.Layer.DIALOGUE)tap(v,Pair(v.width*.5f,v.height*.5f))}
        assertEquals(GameView.Layer.MAP,v.layer)
        assertEquals(1,v.currentSnapshot().inventory[OpeningEquipment.KNIFE_ID])
        assertEquals(true,v.currentSnapshot().flags["rom.npc.114.2"])
        listOf(Key.LEFT,Key.DOWN,Key.DOWN,Key.DOWN,Key.LEFT,Key.LEFT,
            Key.DOWN,Key.DOWN,Key.DOWN,Key.DOWN).forEach{stickStep(v,it)}
        assertEquals(16,v.world.mapId)
        capture("map16")
        // BATTLE-01 makes uninterrupted map-16 loops an invalid UI expectation. The
        // same 10 home + 3 village round trips remain asserted in ContentTest's World test.
        // Here, keep real joystick input and verify that it now reaches a normal battle.
        if(v.layer==GameView.Layer.MAP)stickStep(v,Key.LEFT) // move off the home-return trigger at (203,142)
        repeat(50){
            if(v.layer==GameView.Layer.MAP)stickStep(v,Key.UP)
            if(v.layer==GameView.Layer.MAP)stickStep(v,Key.DOWN)
        }
        assertEquals(GameView.Layer.BATTLE,v.layer)
        capture("battle-entry",0)
        val battlePosition=Triple(v.world.mapId,v.world.x,v.world.y)
        val beforeBattle=v.currentSnapshot()
        tap(v,center(layoutFor(v).stick))
        tap(v,center(layoutFor(v).buttons.getValue(Key.MENU)))
        assertEquals(GameView.Layer.BATTLE,v.layer)
        assertEquals(battlePosition,Triple(v.world.mapId,v.world.x,v.world.y))
        assertFalse(v.visibleMapControls())
        capture("battle")
        val battleField=GameView::class.java.getDeclaredField("battle").apply{isAccessible=true}
        var fight=battleField.get(v) as OpeningBattle
        val presentationField=GameView::class.java.getDeclaredField("battlePresentation").apply{isAccessible=true}
        var presentation=presentationField.get(v) as BattlePresentation
        val bounds=GameView::class.java.getDeclaredMethod("battleBox").apply{isAccessible=true}.invoke(v) as Box
        val scale=bounds.w/256f;val bx=bounds.x;val by=bounds.y
        fun waitPresentation(){
            for(i in 0..400){if(presentation.screen !in listOf(BattlePresentation.Screen.ENTRY,BattlePresentation.Screen.ACTING))return;SystemClock.sleep(50)}
            fail("Battle presentation did not finish")
        }
        waitPresentation();capture("battle-command")
        var turns=0
        var escapes=0;var failures=0
        if(escapeOnly){
            repeat(30){
                while(fight.phase==BattlePhase.TARGET && turns++<90){
                    val hp=fight.hero.hp
                    tap(v,Pair(bx+40*scale,by+208*scale)) // original escape command
                    assertEquals(BattlePresentation.Screen.ACTING,presentation.screen)
                    val once=fight.hero.hp to fight.phase
                    tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
                    assertEquals(once,fight.hero.hp to fight.phase)
                    waitPresentation()
                    if(fight.phase!=BattlePhase.ESCAPED){failures++;assertTrue(fight.hero.hp<=hp)}
                }
                if(fight.phase==BattlePhase.ESCAPED){
                    escapes++;capture("success")
                    assertEquals(beforeBattle.money,v.currentSnapshot().money)
                    assertEquals(beforeBattle.characters[0].experience,v.currentSnapshot().characters[0].experience)
                    assertEquals(beforeBattle.inventory,v.currentSnapshot().inventory)
                    val savedEscape=v.currentSnapshot()
                    tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
                    assertEquals(savedEscape,v.currentSnapshot())
                    assertEquals(GameView.Layer.MAP,v.layer)
                    assertNull(v.input.direction());assertFalse(v.input.pressed(Key.A));assertFalse(v.input.pressed(Key.B))
                    // Re-check the actual settled tile after each released gesture. A slow
                    // instrumented frame can complete an extra legal step before ACTION_UP;
                    // a fixed UP/DOWN pair can then eventually hit real terrain. Keep the
                    // normal encounter/escape/defeat assertions, without bypassing collision.
                    repeat(100){
                        if(v.layer==GameView.Layer.MAP){
                            val key=listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT).firstOrNull { k->
                                val dx=if(k==Key.RIGHT)1 else if(k==Key.LEFT)-1 else 0
                                val dy=if(k==Key.DOWN)1 else if(k==Key.UP)-1 else 0
                                val x=v.world.x/16+dx;val y=v.world.y/16+dy
                                v.content.scenes.getValue(v.world.mapId).probeFrom(v.world.x/16,v.world.y/16,k)==MovementBlock.NONE && v.content.exits.none{e->
                                    e.fromMapId==v.world.mapId && e.triggerX==x && e.triggerY==y}
                            }
                            assertNotNull("A normal walkable neighbor is required to resume encounters",key)
                            stickStep(v,key!!)
                        }
                    }
                    assertEquals(GameView.Layer.BATTLE,v.layer)
                    fight=battleField.get(v) as OpeningBattle
                    presentation=presentationField.get(v) as BattlePresentation
                    waitPresentation()
                }
                if(fight.phase==BattlePhase.DEFEAT)return@repeat
            }
            assertTrue("Normal inputs must exercise a successful escape",escapes>0)
            assertTrue("Normal inputs must exercise a failed escape",failures>0)
            assertEquals("Ordinary repeated escape attempts must reach death without editing HP",BattlePhase.DEFEAT,fight.phase)
        }
        while(fight.phase==BattlePhase.TARGET && turns++<40){
            val enemy=fight.enemies.first{it.hp>0};val hp=enemy.hp
            tap(v,Pair(bx+40*scale,by+156*scale)) // original battle command, not a damage row
            assertEquals(BattlePresentation.Screen.TARGET,presentation.screen)
            tap(v,Pair(bx+(16+32*enemy.slot+12)*scale,by+85*scale))
            assertEquals("Selecting a target must not attack",hp,enemy.hp)
            if(turns==1)capture("battle-target",0)
            tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
            assertEquals(BattlePresentation.Screen.ACTING,presentation.screen)
            assertTrue("One legal confirmation damages the selected target",enemy.hp<hp)
            val once=fight.hero.hp to fight.enemies.map{it.hp}
            tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
            assertEquals("Repeated touch during action cannot submit again",once,fight.hero.hp to fight.enemies.map{it.hp})
            if(turns==1){
                capture("battle-action",100);capture("battle-hit",650)
                send(v,MotionEvent.ACTION_DOWN,listOf(center(layoutFor(v).buttons.getValue(Key.A))))
                val revision=presentation.revision
                instrumentation.runOnMainSync{instrumentation.callActivityOnPause(activity)}
                SystemClock.sleep(900);assertEquals(revision,presentation.revision)
                instrumentation.runOnMainSync{instrumentation.callActivityOnResume(activity)}
                // An old held confirmation cannot submit the next command or close results.
                waitPresentation();send(v,MotionEvent.ACTION_UP,listOf(center(layoutFor(v).buttons.getValue(Key.A))))
                assertEquals(once,fight.hero.hp to fight.enemies.map{it.hp})
            }
            waitPresentation()
            if(fight.phase!=BattlePhase.DEFEAT)assertEquals(battlePosition,Triple(v.world.mapId,v.world.x,v.world.y))
        }
        assertTrue("Battle did not reach a result",fight.phase!=BattlePhase.TARGET)
        assertEquals(BattlePresentation.Screen.RESULT,presentation.screen);capture("battle-result")
        val resultBeforeClose=v.currentSnapshot()
        tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
        assertEquals(GameView.Layer.MAP,v.layer)
        assertEquals("Closing results cannot settle twice",resultBeforeClose,v.currentSnapshot())
        if(fight.phase==BattlePhase.VICTORY){
            val after=v.currentSnapshot()
            assertEquals(beforeBattle.characters[0].experience+
                fight.enemies.sumOf{it.definition.experienceReward},after.characters[0].experience)
            assertEquals(beforeBattle.money+fight.enemies.sumOf{it.definition.moneyReward},after.money)
            assertEquals(1,after.inventory[OpeningEquipment.KNIFE_ID])
            assertEquals(true,after.flags["rom.npc.114.2"])
        }else{
            assertEquals(BattlePhase.DEFEAT,fight.phase)
            assertEquals(114,v.world.mapId);assertEquals(8*16+8,v.world.x);assertEquals(21*16+8,v.world.y)
            assertEquals(listOf(v.content.initialPlayer),v.currentSnapshot().characters)
            assertTrue(v.currentSnapshot().inventory.isEmpty())
            assertEquals(v.content.initialMoney,v.currentSnapshot().money)
            assertFalse(v.currentSnapshot().flags["rom.npc.114.2"]==true)
        }
        val saved=v.currentSnapshot()
        assertEquals(resultBeforeClose,saved)
        File(instrumentation.targetContext.getExternalFilesDir(null),"battle02-${if(escapeOnly)"escape-defeat" else "normal"}-context.json")
            .writeText(org.json.JSONObject().put("groupId",fight.group.id).put("enemyIds",fight.enemies.map{it.definition.id})
                .put("outcome",fight.phase.name).put("turns",turns).put("successfulEscapes",escapes).put("failedEscapes",failures)
                .put("normalInputs",true).put("contentVersion",v.content.scene.version).toString())
        instrumentation.runOnMainSync{activity.finish()}
        val (reopened,restored)=launch()
        assertEquals(saved,restored.currentSnapshot())
        capture("restored")
        instrumentation.runOnMainSync{reopened.finish()}
    }
    fun testMapControlsMenuIsolationAndTenCycles(){
        val (activity,v)=launch();val l=layoutFor(v)
        instrumentation.runOnMainSync{v.world.restore(114,8*16+8,21*16+8,0,Key.DOWN)}
        val a=center(l.buttons.getValue(Key.A));val b=center(l.buttons.getValue(Key.B));val menu=center(l.buttons.getValue(Key.MENU));val stick=center(l.stick)
        val aBox=l.buttons.getValue(Key.A);val bBox=l.buttons.getValue(Key.B)
        assertTrue(aBox.x>=bBox.x+bBox.w || bBox.y>=aBox.y+aBox.h || aBox.y>=bBox.y+bBox.h)
        assertEquals(GameView.Layer.MAP,v.layer);assertTrue(v.visibleMapControls())
        assertFalse(v.mapControlEnabled(Key.A));assertFalse(v.mapControlEnabled(Key.B));assertTrue(v.mapControlEnabled(Key.MENU))
        val x=v.world.x;val y=v.world.y
        tap(v,a);tap(v,b);assertEquals(GameView.Layer.MAP,v.layer);assertEquals(x,v.world.x);assertEquals(y,v.world.y)
        repeat(10){
            tap(v,menu);assertEquals(GameView.Layer.MENU,v.layer);assertFalse(v.visibleMapControls())
            assertNull(v.input.direction());assertFalse(v.input.pressed(Key.MENU))
            tap(v,stick);tap(v,a);tap(v,b);tap(v,menu)
            assertEquals(GameView.Layer.MENU,v.layer);assertEquals(x,v.world.x);assertEquals(y,v.world.y)
            tap(v,menuPoint(v,0));assertEquals(GameView.Layer.MAP,v.layer);assertTrue(v.visibleMapControls())
            assertEquals(l.buttons,layoutFor(v).buttons)
        }
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testHeldJoystickMenuOpenReleaseDoesNotResumeMovement(){
        val (activity,v)=launch();val l=layoutFor(v);val stick=center(l.stick);val up=Pair(stick.first,l.stick.y+2);val menu=center(l.buttons.getValue(Key.MENU))
        send(v,MotionEvent.ACTION_DOWN,listOf(stick));send(v,MotionEvent.ACTION_MOVE,listOf(up));assertEquals(Key.UP,v.input.direction())
        send(v,MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(up,menu))
        send(v,MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(up,menu))
        assertEquals(GameView.Layer.MENU,v.layer);assertNull(v.input.direction());assertEquals(0,v.world.remaining)
        val x=v.world.x;val y=v.world.y
        send(v,MotionEvent.ACTION_MOVE,listOf(up));send(v,MotionEvent.ACTION_UP,listOf(up))
        tap(v,menuPoint(v,0));assertEquals(GameView.Layer.MAP,v.layer)
        SystemClock.sleep(450);assertEquals(x,v.world.x);assertEquals(y,v.world.y);assertNull(v.input.direction())
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testInput01RealMapWallSlidesAndMenuCancellation(){
        val (activity,v)=launch()
        data class Spot(val map:Int,val x:Int,val y:Int)
        fun spot(blocked:Key,open:Key):Spot?=v.content.scenes.values.asSequence().flatMap{s->
            (1 until s.height-1).asSequence().flatMap{y->(1 until s.width-1).asSequence().map{x->Spot(s.mapId,x,y)}}
        }.firstOrNull{p->
            val s=v.content.scenes.getValue(p.map)
            val bx=p.x+if(blocked==Key.RIGHT)1 else 0
            val by=p.y+if(blocked==Key.UP)-1 else 0
            val ox=p.x+if(open==Key.RIGHT)1 else 0
            val oy=p.y+if(open==Key.UP)-1 else 0
            s.check(p.x,p.y)==null && s.blockType(bx,by)==MovementBlock.PHYSICAL && s.check(ox,oy)==null &&
                v.content.exits.none{it.fromMapId==p.map && it.triggerX==ox && it.triggerY==oy}
        }
        val horizontal=spot(Key.UP,Key.RIGHT);val vertical=spot(Key.RIGHT,Key.UP)
        assertNotNull("No horizontal wall in enabled maps",horizontal)
        assertNotNull("No vertical wall in enabled maps",vertical)
        val stick=layoutFor(v).stick;val center=center(stick);val radius=stick.w/2
        fun oneStep(p:Spot,dx:Float,dy:Float,expectedX:Int,expectedY:Int){
            instrumentation.runOnMainSync{v.world.restore(p.map,p.x*16+8,p.y*16+8,0,Key.DOWN)}
            assertEquals(p.map,v.world.mapId)
            val end=Pair(center.first+dx*radius,center.second+dy*radius)
            send(v,MotionEvent.ACTION_DOWN,listOf(center));send(v,MotionEvent.ACTION_MOVE,listOf(end))
            var started=false
            for(i in 0..100){if(v.world.remaining>0){started=true;break};SystemClock.sleep(5)}
            assertTrue("Joystick did not start a step",started)
            send(v,MotionEvent.ACTION_UP,listOf(end))
            for(i in 0..100){if(v.world.remaining==0)break;SystemClock.sleep(5)}
            assertEquals(0,v.world.remaining)
            assertEquals(expectedX,v.world.x);assertEquals(expectedY,v.world.y)
            SystemClock.sleep(650)
        }
        val h=horizontal!!;val z=vertical!!
        oneStep(h,.8f,0f,h.x*16+24,h.y*16+8) // normal full-speed direction
        oneStep(h,.6f,-.8f,h.x*16+24,h.y*16+8) // horizontal slide
        oneStep(h,.8f,-.6f,h.x*16+24,h.y*16+8) // horizontal component remains primary
        oneStep(z,.8f,-.6f,z.x*16+8,z.y*16-8) // vertical slide
        oneStep(z,.6f,-.8f,z.x*16+8,z.y*16-8) // vertical component remains primary
        instrumentation.runOnMainSync{v.world.restore(h.map,h.x*16+8,h.y*16+8,0,Key.DOWN)}
        val end=Pair(center.first+.6f*radius,center.second-.8f*radius)
        val menu=center(layoutFor(v).buttons.getValue(Key.MENU))
        send(v,MotionEvent.ACTION_DOWN,listOf(center));send(v,MotionEvent.ACTION_MOVE,listOf(end))
        for(i in 0..100){if(v.world.remaining>0)break;SystemClock.sleep(5)}
        send(v,MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(end,menu))
        send(v,MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(end,menu))
        assertEquals(GameView.Layer.MENU,v.layer);assertNull(v.input.movementIntent())
        val stopped=v.world.x to v.world.y
        send(v,MotionEvent.ACTION_UP,listOf(end));SystemClock.sleep(650)
        assertEquals(stopped,v.world.x to v.world.y)
        tap(v,menuPoint(v,0));assertEquals(GameView.Layer.MAP,v.layer)
        SystemClock.sleep(650);assertEquals(stopped,v.world.x to v.world.y)
        instrumentation.runOnMainSync{v.world.restore(h.map,h.x*16+8,h.y*16+8,0,Key.DOWN)}
        send(v,MotionEvent.ACTION_DOWN,listOf(center));send(v,MotionEvent.ACTION_MOVE,listOf(end))
        for(i in 0..100){if(v.world.remaining>0)break;SystemClock.sleep(5)}
        assertTrue(v.world.remaining>0)
        send(v,MotionEvent.ACTION_CANCEL,listOf(end))
        assertNull(v.input.movementIntent())
        for(i in 0..100){if(v.world.remaining==0)break;SystemClock.sleep(5)}
        assertEquals(h.x*16+24,v.world.x)
        SystemClock.sleep(300);assertEquals(h.x*16+24,v.world.x)
        instrumentation.runOnMainSync{v.world.restore(h.map,h.x*16+8,h.y*16+8,0,Key.DOWN)}
        send(v,MotionEvent.ACTION_DOWN,listOf(center));send(v,MotionEvent.ACTION_MOVE,listOf(end))
        for(i in 0..100){if(v.world.remaining>0)break;SystemClock.sleep(5)}
        assertTrue(v.world.remaining>0)
        instrumentation.runOnMainSync{v.active=false}
        assertNull(v.input.movementIntent());assertEquals(0,v.world.remaining)
        val backgroundStopped=v.world.x to v.world.y
        send(v,MotionEvent.ACTION_UP,listOf(end))
        instrumentation.runOnMainSync{v.active=true}
        SystemClock.sleep(350);assertEquals(backgroundStopped,v.world.x to v.world.y)
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testSettingsReturnToMenuThenGame(){
        val (activity,v)=launch();val menu=center(layoutFor(v).buttons.getValue(Key.MENU))
        tap(v,menu);tap(v,menuPoint(v,3));assertEquals(GameView.Layer.SETTINGS,v.layer)
        val x=v.world.x;val y=v.world.y
        instrumentation.runOnMainSync{activity.onBackPressed()}
        for(i in 0..40){if(v.layer==GameView.Layer.MENU)break;SystemClock.sleep(50)}
        assertEquals(GameView.Layer.MENU,v.layer)
        tap(v,menu);assertEquals(GameView.Layer.MENU,v.layer)
        assertEquals(x,v.world.x);assertEquals(y,v.world.y)
        tap(v,menuPoint(v,0));assertEquals(GameView.Layer.MAP,v.layer)
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testOpeningRouteByTouchAndLocalRestore(){
        instrumentation.targetContext.getSharedPreferences("opening-local-save",0).edit().clear().commit()
        val (activity,v)=launch();val stick=layoutFor(v).stick;val center=center(stick)
        assertEquals(114,v.world.mapId)
        send(v,MotionEvent.ACTION_DOWN,listOf(center))
        val down=Pair(center.first,stick.y+stick.h-2)
        send(v,MotionEvent.ACTION_MOVE,listOf(down))
        for(i in 0..100){if(v.world.mapId==16)break;SystemClock.sleep(50)}
        send(v,MotionEvent.ACTION_UP,listOf(down))
        assertEquals(16,v.world.mapId)
        instrumentation.runOnMainSync{v.world.finishStep();v.persistState()}
        val x=v.world.x;val y=v.world.y
        assertEquals(203*16+8,x);assertTrue(y in 142*16+8..143*16+8)
        instrumentation.runOnMainSync{activity.finish()}
        val (reopened,restored)=launch()
        assertEquals(16,restored.world.mapId);assertEquals(x,restored.world.x);assertEquals(y,restored.world.y)
        instrumentation.runOnMainSync{reopened.finish()}
    }
    fun testSnapshotRoundTripAfterLocalClear(){
        val (activity,v)=launch()
        val snapshot=v.currentSnapshot()
        val encoded=snapshot.json().toString()
        assertEquals(snapshot,SaveSnapshot.parse(encoded))
        instrumentation.targetContext.getSharedPreferences("opening-local-save",0).edit().clear().commit()
        instrumentation.runOnMainSync{activity.finish()}
        val (fresh,recovered)=launch()
        instrumentation.runOnMainSync{assertTrue(recovered.restoreSnapshot(SaveSnapshot.parse(encoded)))}
        assertEquals(snapshot,recovered.currentSnapshot())
        instrumentation.runOnMainSync{fresh.finish()}
    }
    fun testOpeningNpcFirstRepeatRewardAndRestore(){
        instrumentation.targetContext.getSharedPreferences("opening-local-save",0).edit().clear().commit()
        val (activity,v)=launch()
        val a=center(layoutFor(v).buttons.getValue(Key.A))
        instrumentation.runOnMainSync{v.world.restore(114,8*16+8,16*16+8,0,Key.UP)}
        assertTrue(v.mapControlEnabled(Key.A))
        tap(v,a);assertEquals(GameView.Layer.DIALOGUE,v.layer)
        repeat(4){tap(v,Pair(v.width*.5f,v.height*.5f))}
        assertEquals(GameView.Layer.MAP,v.layer)
        assertEquals(true,v.currentSnapshot().flags["rom.npc.114.0"])
        tap(v,a);assertEquals(GameView.Layer.DIALOGUE,v.layer)
        tap(v,Pair(v.width*.5f,v.height*.5f));assertEquals(GameView.Layer.MAP,v.layer)
        instrumentation.runOnMainSync{v.world.restore(114,7*16+8,16*16+8,0,Key.UP)}
        tap(v,a);repeat(4){tap(v,Pair(v.width*.5f,v.height*.5f))}
        assertEquals(100,v.currentSnapshot().money)
        assertEquals(true,v.currentSnapshot().flags["rom.npc.114.1"])
        tap(v,a);repeat(4){tap(v,Pair(v.width*.5f,v.height*.5f))}
        assertEquals(100,v.currentSnapshot().money)
        instrumentation.runOnMainSync{v.world.restore(114,11*16+8,22*16+8,0,Key.DOWN)}
        assertTrue(v.mapControlEnabled(Key.A))
        tap(v,a);repeat(4){tap(v,Pair(v.width*.5f,v.height*.5f))}
        assertEquals(1,v.currentSnapshot().inventory[OpeningEquipment.KNIFE_ID])
        tap(v,a);repeat(4){tap(v,Pair(v.width*.5f,v.height*.5f))}
        assertEquals(1,v.currentSnapshot().inventory[OpeningEquipment.KNIFE_ID])
        instrumentation.runOnMainSync{v.persistState()}
        val snapshot=v.currentSnapshot()
        instrumentation.runOnMainSync{activity.finish()}
        val (reopened,restored)=launch()
        assertEquals(snapshot,restored.currentSnapshot())
        instrumentation.runOnMainSync{reopened.finish()}
    }
    fun testPreviousDevelopmentSaveMigratesWithoutLosingProgress(){
        val (activity,v)=launch()
        val current=v.currentSnapshot()
        val older=current.copy(contentVersion="opening-to-world-b1")
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(older))}
        assertEquals(current,v.currentSnapshot())
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testPreviousOpeningContentSaveMigratesWithoutLosingProgress(){
        val (activity,v)=launch()
        val current=v.currentSnapshot()
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(current.copy(contentVersion="opening-segment-001-c1")))}
        assertEquals(current,v.currentSnapshot())
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testPreviousC2SaveMigratesWithoutLosingProgress(){
        val (activity,v)=launch();val current=v.currentSnapshot()
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(current.copy(contentVersion="opening-segment-001-c2")))}
        assertEquals(current,v.currentSnapshot())
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testPreviousC4SaveMigratesWithoutLosingProgress(){
        val (activity,v)=launch();val current=v.currentSnapshot()
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(current.copy(contentVersion="opening-segment-001-c4")))}
        assertEquals(current,v.currentSnapshot())
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testPreviousC5SaveMigratesWithoutLosingProgress(){
        val (activity,v)=launch();val current=v.currentSnapshot()
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(current.copy(contentVersion="opening-segment-001-c5")))}
        assertEquals(current,v.currentSnapshot())
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testAdjacentAAndTapNpcWithoutExtraMovement(){
        instrumentation.targetContext.getSharedPreferences("opening-local-save",0).edit().clear().commit()
        val (activity,v)=launch();val a=center(layoutFor(v).buttons.getValue(Key.A))
        instrumentation.runOnMainSync{v.world.restore(114,8*16+8,16*16+8,0,Key.DOWN)}
        assertTrue(v.mapControlEnabled(Key.A))
        val before=v.world.x to v.world.y
        tap(v,a);assertEquals(GameView.Layer.DIALOGUE,v.layer)
        assertEquals(Key.UP,v.world.direction);assertEquals(before,v.world.x to v.world.y)
        repeat(4){tap(v,Pair(v.width*.5f,v.height*.5f))}
        assertEquals(GameView.Layer.MAP,v.layer)
        val l=layoutFor(v);val cam=v.world.camera(l.viewWidth,l.viewHeight)
        val point=l.worldToScreen(8*16f+8,15*16f+8,cam)
        tap(v,point);assertEquals(GameView.Layer.DIALOGUE,v.layer)
        assertEquals(before,v.world.x to v.world.y)
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testMovingAEndsStepAndClearsJoystick(){
        val (activity,v)=launch();val a=center(layoutFor(v).buttons.getValue(Key.A))
        instrumentation.runOnMainSync{v.world.restore(114,8*16+8,17*16+8,0,Key.UP);v.world.tick(Key.UP)}
        assertTrue(v.mapControlEnabled(Key.A))
        tap(v,a);assertEquals(GameView.Layer.DIALOGUE,v.layer)
        assertEquals(8*16+8,v.world.x);assertEquals(16*16+8,v.world.y)
        assertNull(v.input.direction())
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testCharacterAndInventoryPanelsAreModalAndBackToMenu(){
        val (activity,v)=launch();val l=layoutFor(v);val menu=center(l.buttons.getValue(Key.MENU));val stick=center(l.stick)
        val start=v.world.x to v.world.y
        tap(v,menu);tap(v,menuPoint(v,1));assertEquals(GameView.Layer.CHARACTER,v.layer)
        assertFalse(v.visibleMapControls());tap(v,stick);assertEquals(start,v.world.x to v.world.y)
        instrumentation.runOnMainSync{activity.onBackPressed()};assertEquals(GameView.Layer.MENU,v.layer)
        tap(v,menuPoint(v,2));assertEquals(GameView.Layer.INVENTORY,v.layer)
        assertFalse(v.visibleMapControls());tap(v,stick);assertEquals(start,v.world.x to v.world.y)
        instrumentation.runOnMainSync{activity.onBackPressed()};assertEquals(GameView.Layer.MENU,v.layer)
        tap(v,menuPoint(v,0));assertEquals(GameView.Layer.MAP,v.layer)
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testHudOpensLeftPanelAndTabsWithoutMapInputLeak(){
        instrumentation.targetContext.getSharedPreferences("opening-local-save",0).edit().clear().commit()
        val (activity,v)=launch();val l=layoutFor(v);val hud=v.hudBounds();val panel=v.characterPanelBounds()
        assertTrue(hud.x>=v.safe.left.toFloat() && hud.y>=v.safe.top.toFloat())
        assertTrue(hud.x+hud.w<l.buttons.getValue(Key.MENU).x)
        assertTrue(panel.w/l.safe.w in .35f..42f)
        val stick=center(l.stick);val up=Pair(stick.first,l.stick.y+2);val hudPoint=center(hud)
        send(v,MotionEvent.ACTION_DOWN,listOf(stick));send(v,MotionEvent.ACTION_MOVE,listOf(up))
        send(v,MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(up,hudPoint))
        send(v,MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(up,hudPoint))
        assertEquals(GameView.Layer.CHARACTER,v.layer);assertEquals(GameView.CharacterTab.ATTRIBUTES,v.activeCharacterTab)
        assertNull(v.input.direction());assertEquals(0,v.world.remaining)
        val frozen=v.world.x to v.world.y
        send(v,MotionEvent.ACTION_UP,listOf(up));tap(v,center(l.buttons.getValue(Key.A)));tap(v,stick)
        assertEquals(frozen,v.world.x to v.world.y)
        for((index,tab)in GameView.CharacterTab.entries.withIndex()){
            tap(v,tabPoint(v,index));assertEquals(tab,v.activeCharacterTab)
        }
        instrumentation.runOnMainSync{activity.onBackPressed()}
        assertEquals(GameView.Layer.MAP,v.layer);assertNull(v.input.direction())
        SystemClock.sleep(350);assertEquals(frozen,v.world.x to v.world.y)
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testPartySwitchAndInventoryOnlyReadCurrentSnapshot(){
        val (activity,v)=launch();val original=v.currentSnapshot()
        val second=original.characters.first().copy(id="joined_test")
        val expanded=original.copy(characters=original.characters+second,inventory=mapOf(OpeningEquipment.KNIFE_ID to 2))
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(expanded))}
        tap(v,center(v.hudBounds()));assertEquals(GameView.Layer.CHARACTER,v.layer)
        assertEquals(original.characters.first().id,v.selectedCharacterId)
        val b=v.characterPanelBounds();val dp=v.resources.displayMetrics.density
        val size=minOf(37*dp,b.h*.095f)
        tap(v,Pair(b.x+12*dp+(size+9*dp)+size/2,b.y+b.h*.265f+size/2))
        assertEquals("joined_test",v.selectedCharacterId)
        tap(v,tabPoint(v,2));assertEquals(GameView.CharacterTab.ITEMS,v.activeCharacterTab)
        assertEquals(expanded.characters,v.currentSnapshot().characters)
        assertEquals(2,v.currentSnapshot().inventory[OpeningEquipment.KNIFE_ID])
        instrumentation.runOnMainSync{activity.onBackPressed();assertTrue(v.restoreSnapshot(original));activity.finish()}
    }
    fun testOpeningKnifeEquipCyclePersistsWithoutDuplication(){
        instrumentation.targetContext.getSharedPreferences("opening-local-save",0).edit().clear().commit()
        val (activity,v)=launch();val initial=v.currentSnapshot();val hero=initial.characters.first()
        assertEquals(EquipmentState(0,-1,0,28),hero.equipment)
        assertNull(OpeningEquipment.equipKnife(hero,mapOf(OpeningEquipment.KNIFE_ID to 1)))
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(initial.copy(inventory=mapOf(OpeningEquipment.KNIFE_ID to 1))))}
        tap(v,center(v.hudBounds()));tap(v,tabPoint(v,1))
        val panel=v.characterPanelBounds();val action=center(v.panelPrimaryBounds())
        tap(v,action)
        assertEquals(-1,v.currentSnapshot().characters.first().equipment?.rightHand)
        assertEquals(2,v.currentSnapshot().inventory[OpeningEquipment.KNIFE_ID])
        tap(v,action);assertEquals(2,v.currentSnapshot().inventory[OpeningEquipment.KNIFE_ID])
        tap(v,tabPoint(v,2))
        tap(v,center(v.panelItemBounds(OpeningEquipment.KNIFE_ID)))
        tap(v,center(v.panelPrimaryBounds()))
        assertEquals(0,v.currentSnapshot().characters.first().equipment?.rightHand)
        assertEquals(1,v.currentSnapshot().inventory[OpeningEquipment.KNIFE_ID])
        tap(v,action);assertEquals(1,v.currentSnapshot().inventory[OpeningEquipment.KNIFE_ID])
        val saved=v.currentSnapshot()
        instrumentation.runOnMainSync{activity.onBackPressed();activity.finish()}
        val (reopened,restored)=launch()
        assertEquals(saved,restored.currentSnapshot())
        instrumentation.runOnMainSync{reopened.finish()}
    }
    fun testC3KnifeSaveMigratesToRomIdAndOpeningEquipment(){
        val (activity,v)=launch();val current=v.currentSnapshot()
        val old=current.copy(contentVersion="opening-segment-001-c3",
            characters=current.characters.map{it.copy(maxMp=null,equipment=null)},
            inventory=mapOf("reference.item.1" to 2))
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(SaveSnapshot.parse(old.json().toString())))}
        val migrated=v.currentSnapshot()
        assertEquals(2,migrated.inventory[OpeningEquipment.KNIFE_ID])
        assertFalse(migrated.inventory.containsKey("reference.item.1"))
        assertEquals(0,migrated.characters.first().maxMp)
        assertEquals(EquipmentState(0,-1,0,28),migrated.characters.first().equipment)
        instrumentation.runOnMainSync{activity.finish()}
    }
    /** One continuous new-game controller flow. No restoreSnapshot, HP/level/item/flag writes. */
    fun testNormalNanhaiRouteBossAndVictory(){
        instrumentation.targetContext.getSharedPreferences("opening-local-save",0).edit().clear().commit() // Isolated emulator fixture only.
        val(activity,v)=launch();val events=org.json.JSONArray();val started=SystemClock.elapsedRealtime()
        var fightCount=0;var herbUses=0;var capturedBoss=false;var capturedIce=false;var capturedInfo=false;var capturedReward=false;var escapedBoss=false;var battleHerbUses=0;var capturedBattleHeal=false
        fun state(name:String,capture:Boolean=true){
            if(capture)screenshot(v,"nanhai-$name")
            events.put(org.json.JSONObject().put("name",name).put("elapsedMs",SystemClock.elapsedRealtime()-started)
                .put("androidUptimeMs",SystemClock.elapsedRealtime()).put("snapshot",v.currentSnapshot().json()))
            File(instrumentation.targetContext.getExternalFilesDir(null),"nanhai-normal-index.json").writeText(
                org.json.JSONObject().put("kind","NORMAL_CONTROLLER_NEW_GAME_NO_STATE_INJECTION").put("events",events)
                    .put("fights",fightCount).put("herbUses",herbUses).put("battleHerbUses",battleHerbUses).toString())
        }
        val f=GameView::class.java.getDeclaredField("battle").apply{isAccessible=true}
        val p=GameView::class.java.getDeclaredField("battlePresentation").apply{isAccessible=true}
        fun finishFight(){
            var initial:OpeningBattle?=null
            instrumentation.runOnMainSync{if(v.layer==GameView.Layer.BATTLE){assertNotNull(f.get(v));initial=f.get(v) as OpeningBattle}}
            val entered=initial?:return
            val boss=entered.enemies.any{it.definition.id==137}
            fightCount++
            val levelBefore=v.currentSnapshot().characters.first().level
            if(boss&&!capturedBoss){state("boss-entry");capturedBoss=true}
            val deadline=SystemClock.elapsedRealtime()+180000
            while(true){
                // Ordinary results now auto-close on the UI thread. Observe layer + battle atomically;
                // a background layer read followed by a reflection cast can race a valid close.
                var observed:Pair<OpeningBattle,BattlePresentation>?=null
                instrumentation.runOnMainSync{if(v.layer==GameView.Layer.BATTLE){
                    assertNotNull("Battle layer must have its instance",f.get(v))
                    observed=(f.get(v) as OpeningBattle) to (p.get(v) as BattlePresentation)
                }}
                val (fight,presentation)=observed?:break
                assertTrue("Normal fight did not finish",SystemClock.elapsedRealtime()<deadline)
                if(boss&&presentation.screen==BattlePresentation.Screen.ACTING&&!capturedIce&&
                    presentation.action?.text?.contains("冰系")==true){state("boss-ice");capturedIce=true}
                if(boss&&presentation.screen==BattlePresentation.Screen.ACTING&&!capturedBattleHeal&&
                    presentation.action?.kind==BattleActionKind.HEAL){
                    val action=presentation.action!!;assertEquals(minOf(fight.hero.maxHp,action.beforeHeroHp+50),action.heroHp)
                    state("mobile-battle-herb-heal");capturedBattleHeal=true
                }
                if(presentation.screen !in listOf(BattlePresentation.Screen.ENTRY,BattlePresentation.Screen.ACTING)){
                    assertTrue("Normal preparation must survive; no forced win or restored fixture",fight.phase!=BattlePhase.DEFEAT)
                    if(presentation.screen in listOf(BattlePresentation.Screen.COMMAND,BattlePresentation.Screen.TARGET)){
                        if(!capturedInfo&&fight.enemies.size>1){
                            val before=fight.hero;val hp=fight.enemies.map{it.hp}
                            tap(v,center(v.battleCommandBounds(4)));tap(v,center(v.battleTargetBounds(fight.enemies.last().slot)))
                            state("mobile-enemy-information");assertEquals(before,fight.hero);assertEquals(hp,fight.enemies.map{it.hp})
                            tap(v,center(v.battleInfoCloseBounds()));capturedInfo=true
                        }
                        if(boss&&!escapedBoss){tap(v,center(v.battleCommandBounds(3)));escapedBoss=true;state("mobile-boss-escape-submitted")}
                        else if(boss&&battleHerbUses==0&&v.battleHerbCount()>0&&fight.hero.hp==fight.hero.maxHp){
                            // Legitimate Boss escape attempt: wait for an actual enemy hit, never edit HP/RNG.
                            tap(v,center(v.battleCommandBounds(3)))
                        }else if(boss&&battleHerbUses==0&&fight.hero.hp<fight.hero.maxHp&&v.battleHerbCount()>0){
                            val before=fight.hero;val count=v.battleHerbCount();tap(v,center(v.battleCommandBounds(2)))
                            tap(v,center(v.battleItemBounds(HerbUse.ID)))
                            assertEquals(before,fight.hero);assertEquals(count,v.battleHerbCount());state("mobile-battle-herb-selected")
                            tap(v,center(v.battleItemUseBounds()));assertEquals(count-1,v.battleHerbCount());battleHerbUses++
                        }else chooseAttack(v,fight.enemies.first{it.hp>0}.slot)
                    }else if(presentation.screen==BattlePresentation.Screen.RESULT){
                        if(boss){state("boss-victory-result");tap(v,center(v.battleResultBounds()))}
                        else if(!capturedReward){state("mobile-ordinary-reward");capturedReward=true}
                    }
                }
                if(boss&&presentation.action?.kind==BattleActionKind.ESCAPE_FAILED&&
                    events.toString().contains("mobile-boss-escape-failed").not())state("mobile-boss-escape-failed")
                SystemClock.sleep(40)
            }
            if(v.layer==GameView.Layer.MAP&&v.currentSnapshot().characters.first().level>levelBefore){
                state("mobile-upgrade-${v.currentSnapshot().characters.first().level}")
                tap(v,center(v.hudBounds()));state("mobile-growth-detail-${v.currentSnapshot().characters.first().level}")
                instrumentation.runOnMainSync{v.handleBack()}
            }
        }
        fun herb(){
            assertEquals(GameView.Layer.MAP,v.layer)
            val before=v.currentSnapshot();assertTrue((before.inventory[HerbUse.ID]?:0)>0)
            tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));scrollToItem(v,HerbUse.ID)
            tap(v,center(v.panelItemBounds(HerbUse.ID)));assertEquals(before,v.currentSnapshot())
            tap(v,center(v.panelPrimaryBounds()));herbUses++
            val after=v.currentSnapshot();assertEquals(minOf(before.characters.first().maxHp,before.characters.first().hp+50),after.characters.first().hp)
            assertEquals((before.inventory[HerbUse.ID]?:0)-1,after.inventory[HerbUse.ID]?:0)
            instrumentation.runOnMainSync{v.handleBack()};assertEquals(GameView.Layer.MAP,v.layer)
        }
        fun healIfNeeded(){val s=v.currentSnapshot();val h=s.characters.first()
            if(v.layer==GameView.Layer.MAP&&h.hp<h.maxHp-7&&(s.inventory[HerbUse.ID]?:0)>0)herb()}
        fun step(key:Key){assertEquals(GameView.Layer.MAP,v.layer);stickStep(v,key);finishFight();healIfNeeded()}
        fun walkTo(tx:Int,ty:Int){
            val scene=v.world.scene;val goal=ty*scene.width+tx;var attempts=0
            while(v.world.mapId==scene.mapId&&v.world.y/16*scene.width+v.world.x/16!=goal){
                assertTrue("Normal path did not converge on ${scene.mapId}:$tx,$ty",attempts++<2000)
                val start=v.world.y/16*scene.width+v.world.x/16;val queue=java.util.ArrayDeque<Int>();queue.add(start)
                val parents=mutableMapOf(start to (-1 to Key.UP))
                while(queue.isNotEmpty()&&goal !in parents){
                    val at=queue.removeFirst();val x=at%scene.width;val y=at/scene.width
                    for((key,d)in listOf(Key.UP to (0 to -1),Key.DOWN to (0 to 1),Key.LEFT to (-1 to 0),Key.RIGHT to (1 to 0))){
                        val nx=x+d.first;val ny=y+d.second;if(scene.probeFrom(x,y,key)!=MovementBlock.NONE)continue
                        val next=ny*scene.width+nx
                        if(next in parents||(next!=goal&&v.content.exits.any{it.fromMapId==scene.mapId&&it.triggerX==nx&&it.triggerY==ny&&it.edgeDirection==null}))continue
                        parents[next]=at to key;queue.add(next)
                    }
                }
                assertTrue("No legal route ${scene.mapId}→$tx,$ty",goal in parents)
                var next=goal;while(parents.getValue(next).first!=start)next=parents.getValue(next).first
                step(parents.getValue(next).second)
            }
        }
        fun talk(){tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
            repeat(16){if(v.layer==GameView.Layer.DIALOGUE)tap(v,Pair(v.width*.5f,v.height*.5f))}}
        fun equip(id:String){
            tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));scrollToItem(v,id)
            tap(v,center(v.panelItemBounds(id)));tap(v,center(v.panelPrimaryBounds()))
            val d=v.content.equipmentDefinitions.getValue(id);val e=v.currentSnapshot().characters.first().equipment!!
            assertEquals(d.originalId,if(d.slot=="body")e.body else e.rightHand)
            instrumentation.runOnMainSync{v.handleBack()}
        }
        fun buy(mid:Int,id:String,count:Int){
            assertEquals(0,v.world.mapId)
            val entry=v.content.exits.first{it.fromMapId==0&&it.toMapId==mid};walkTo(entry.triggerX,entry.triggerY)
            val npc=v.content.npcs.first{it.mapId==mid&&it.shopId!=null};walkTo(npc.interactionCell!!.first,npc.interactionCell.second)
            talk();assertEquals(GameView.Layer.SHOP,v.layer);tap(v,center(v.shopActionBounds(1)))
            scrollToShopItem(v,id);tap(v,center(v.shopItemBounds(id)))
            repeat(count){val before=v.currentSnapshot();tap(v,center(v.shopActionBounds(4)))
                assertEquals(before.money-v.content.itemDefinitions.getValue(id).buyPrice!!,v.currentSnapshot().money)
                assertEquals((before.inventory[id]?:0)+1,v.currentSnapshot().inventory[id])}
            tap(v,center(v.shopActionBounds(3)));assertEquals(GameView.Layer.MAP,v.layer)
            val exit=v.content.exits.first{it.fromMapId==mid};walkTo(exit.triggerX,exit.triggerY);assertEquals(0,v.world.mapId)
        }
        fun town(){
            if(v.world.mapId==25){walkTo(39,42);assertEquals(16,v.world.mapId)}
            if(v.world.mapId==16){walkTo(202,130);assertEquals(0,v.world.mapId)}
        }
        fun leaveTown(){walkTo(0,14);step(Key.LEFT);assertEquals(16,v.world.mapId);walkTo(200,130)}
        fun refillAndUpgrade(){
            town()
            var s=v.currentSnapshot()
            if(s.characters.first().equipment!!.rightHand!=2&&s.money>=135){buy(17,"rom.weapon.2",1);equip("rom.weapon.2")}
            s=v.currentSnapshot();val count=minOf(5-(s.inventory[HerbUse.ID]?:0),s.money/15).coerceAtLeast(0)
            if(count>0)buy(19,HerbUse.ID,count)
            healIfNeeded();leaveTown()
        }
        state("new-game")
        walkTo(7,16);talk();assertEquals(100,v.currentSnapshot().money)
        walkTo(11,22);talk();assertEquals(1,v.currentSnapshot().inventory[OpeningEquipment.KNIFE_ID])
        walkTo(8,29);assertEquals(16,v.world.mapId);walkTo(202,130);assertEquals(0,v.world.mapId)
        buy(18,"rom.armor.1",1);equip("rom.armor.1");buy(19,HerbUse.ID,1);state("optional-supply")
        leaveTown();var steps=0
        while(v.currentSnapshot().characters.first().level<3){
            assertTrue("Bounded normal training",steps++<10000)
            val s=v.currentSnapshot();val h=s.characters.first()
            if((s.inventory[HerbUse.ID]?:0)==0&&h.hp<h.maxHp*3/4)refillAndUpgrade()
            val key=if(v.world.y/16==130)Key.DOWN else Key.UP;step(key)
        }
        state("world-trained")
        walkTo(199,130);assertEquals(25,v.world.mapId);assertEquals(0,v.currentSnapshot().encounterSteps);state("sea-entry")
        // Verified reverse exits through their true trigger cells, no coordinate swapping.
        walkTo(39,43);walkTo(39,42);assertEquals(16,v.world.mapId);assertEquals(0,v.currentSnapshot().encounterSteps);state("sea-return",false)
        walkTo(200,130);walkTo(199,130);assertEquals(25,v.world.mapId);walkTo(39,43)
        while(v.currentSnapshot().characters.first().level<8||v.currentSnapshot().characters.first().equipment!!.rightHand!=2){
            assertTrue("Normal sea training budget",steps++<20000)
            val s=v.currentSnapshot();val h=s.characters.first()
            if((s.inventory[HerbUse.ID]?:0)==0&&h.hp<h.maxHp*3/4||h.equipment!!.rightHand!=2&&s.money>=135){
                refillAndUpgrade();walkTo(199,130);assertEquals(25,v.world.mapId);walkTo(39,43)
            }
            step(if(v.world.x/16==39)Key.LEFT else Key.RIGHT)
        }
        refillAndUpgrade();walkTo(199,130);assertEquals(25,v.world.mapId);state("sea-prepared")
        walkTo(29,44);assertEquals(97,v.world.mapId);assertEquals(0,v.currentSnapshot().encounterSteps);state("palace-entry")
        walkTo(15,28);walkTo(15,29);assertEquals(25,v.world.mapId);assertEquals(0,v.currentSnapshot().encounterSteps);state("palace-return",false)
        // The verified palace return lands in the doorway with walls north/east/west.
        // Leave south through actual collision data, then re-enter, without a false shortcut.
        assertEquals(MovementBlock.PHYSICAL,v.world.scene.probeFrom(29,44,Key.UP))
        assertEquals(MovementBlock.NONE,v.world.scene.probeFrom(29,44,Key.DOWN))
        walkTo(29,45);walkTo(29,44);assertEquals(97,v.world.mapId)
        walkTo(15,14);tap(v,center(layoutFor(v).buttons.getValue(Key.A)));assertEquals(GameView.Layer.DIALOGUE,v.layer)
        state("guard-dialogue");repeat(16){if(v.layer==GameView.Layer.DIALOGUE)tap(v,Pair(v.width*.5f,v.height*.5f))}
        walkTo(15,4);val b=v.currentSnapshot();if((b.inventory[HerbUse.ID]?:0)>0&&b.characters.first().hp<b.characters.first().maxHp)herb()
        val beforeBoss=v.currentSnapshot();assertTrue(beforeBoss.flags["rom.event.97.39.1"]!=true)
        tap(v,center(layoutFor(v).buttons.getValue(Key.A)));assertEquals(GameView.Layer.DIALOGUE,v.layer);state("boss-dialogue")
        repeat(16){if(v.layer==GameView.Layer.DIALOGUE)tap(v,Pair(v.width*.5f,v.height*.5f))}
        assertEquals(GameView.Layer.BATTLE,v.layer);finishFight();assertEquals(GameView.Layer.DIALOGUE,v.layer)
        val won=v.currentSnapshot();assertEquals(beforeBoss.money+100,won.money);assertEquals(beforeBoss.characters.first().experience+60,won.characters.first().experience)
        assertEquals("Normal bought herb must be used in Boss combat",1,battleHerbUses);assertTrue(capturedBattleHeal)
        assertEquals((beforeBoss.inventory[HerbUse.ID]?:0)-1,won.inventory[HerbUse.ID]?:0)
        assertEquals(true,won.flags["rom.event.97.39.1"]);assertEquals(97,won.mapId);state("post-victory-dialogue")
        repeat(16){if(v.layer==GameView.Layer.DIALOGUE)tap(v,Pair(v.width*.5f,v.height*.5f))}
        assertEquals(GameView.Layer.MAP,v.layer);val settled=v.currentSnapshot();assertTrue(settled.flags["rom.event.97.39.1.dialogue.pending"]!=true)
        talk();assertEquals(GameView.Layer.MAP,v.layer);assertEquals(settled,v.currentSnapshot());state("repeat-no-reward")
        // A repeated conversation alone does not prove persisted plot state survives real exits.
        // Leave and re-enter using only normal movement; incidental encounters keep their legitimate rewards.
        walkTo(15,29);assertEquals(25,v.world.mapId);assertEquals(0,v.currentSnapshot().encounterSteps)
        assertEquals(true,v.currentSnapshot().flags["rom.event.97.39.1"]);state("victory-palace-return")
        walkTo(29,45);walkTo(29,44);assertEquals(97,v.world.mapId)
        assertEquals(0,v.currentSnapshot().encounterSteps);state("victory-palace-reentry")
        walkTo(15,4);val beforeRepeat=v.currentSnapshot()
        tap(v,center(layoutFor(v).buttons.getValue(Key.A)));assertEquals(GameView.Layer.DIALOGUE,v.layer)
        // Existing interaction faces the adjacent NPC; this is the only legitimate snapshot change.
        val facingNpc=beforeRepeat.copy(direction=Key.UP)
        assertEquals(facingNpc,v.currentSnapshot());state("reentered-victory-dialogue")
        repeat(16){if(v.layer==GameView.Layer.DIALOGUE)tap(v,Pair(v.width*.5f,v.height*.5f))}
        assertEquals(GameView.Layer.MAP,v.layer);assertEquals(facingNpc,v.currentSnapshot())
        assertEquals(true,v.currentSnapshot().flags["rom.event.97.39.1"]);state("reentered-no-reward")
        step(Key.DOWN);assertEquals(97,v.world.mapId);state("next-operable")
        instrumentation.runOnMainSync{v.persistState()}
        File(instrumentation.targetContext.getExternalFilesDir(null),"nanhai-expected-save.json").writeText(v.currentSnapshot().json().toString())
        instrumentation.runOnMainSync{activity.finish()}
    }
    /** Exact checkpoint produced above by normal new-game play; no changed HP/level/money/flags. */
    fun testNormalWorldSeaNorthFromVerifiedNanhaiSave(){normalWorldSeaNorthFromSave("nanhai-expected-save.json")}
    fun testNormalWorldSeaNorthFromVerifiedNorthPalaceSave(){normalWorldSeaNorthFromSave("world-north-palace-expected-save.json")}
    fun testControlledNorthTravelFromVerifiedPalaceSave(){
        val fixture=JSONObject(instrumentation.context.assets.open("north-travel-verified-palace.json").bufferedReader().use{it.readText()})
        assertEquals("CONTROLLED_REPLAY_OF_VERIFIED_NORMAL_SAVE",fixture.getString("kind"))
        val bytes=fixture.getString("savedJson").toByteArray(Charsets.UTF_8)
        assertEquals(fixture.getString("sourceSaveSha256"),java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString(""){"%02x".format(it)})
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"world-controlled-north-travel-source.json")
        file.writeBytes(bytes)
        normalWorldSeaNorthFromSave(file.name,"controlled-north-travel",fixture.getString("kind"))
    }
    private fun normalWorldSeaNorthFromSave(sourceName:String,label:String="north",
        kind:String="CONTINUATION_FROM_VERIFIED_NORMAL_SAVE"){
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),sourceName)
        assertTrue("The recorded normal source must exist before route continuation",file.exists())
        val source=SaveSnapshot.parse(file.readText());assertEquals(true,source.flags["rom.event.97.39.1"])
        val(activity,v)=launch();instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(source))}
        assertEquals(source,v.currentSnapshot())
        val started=SystemClock.elapsedRealtime();val events=org.json.JSONArray();var fights=0;var battleHerbs=0;var capturedPoison=false
        val f=GameView::class.java.getDeclaredField("battle").apply{isAccessible=true}
        val p=GameView::class.java.getDeclaredField("battlePresentation").apply{isAccessible=true}
        fun state(name:String){
            screenshot(v,"world-$label-$name")
            events.put(org.json.JSONObject().put("name",name).put("elapsedMs",SystemClock.elapsedRealtime()-started)
                .put("androidUptimeMs",SystemClock.elapsedRealtime()).put("snapshot",v.currentSnapshot().json()))
            val hash=java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString(""){"%02x".format(it)}
            File(instrumentation.targetContext.getExternalFilesDir(null),"world-$label-normal-index.json").writeText(
                org.json.JSONObject().put("kind",kind)
                    .put("sourceFile",file.name).put("sourceSha256",hash).put("sourceSnapshot",source.json())
                    .put("stateChangesAtLoad",false).put("events",events).put("fights",fights).put("battleHerbs",battleHerbs).toString())
        }
        fun mapMedicine(id:String){
            val before=v.currentSnapshot();assertTrue((before.inventory[id]?:0)>0)
            tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));scrollToItem(v,id)
            tap(v,center(v.panelItemBounds(id)));assertEquals(before,v.currentSnapshot())
            val expected=MapItemUse.apply(before.characters,before.inventory,before.characters.first().id,v.content.itemDefinitions.getValue(id),true)
            assertTrue(expected.applied);tap(v,center(v.panelPrimaryBounds()))
            assertEquals(expected.characters,v.currentSnapshot().characters);assertEquals(expected.inventory,v.currentSnapshot().inventory)
            state(if(id==AntidoteUse.ID)"normal-antidote-cured" else "normal-herb-healed")
            instrumentation.runOnMainSync{v.handleBack()}
        }
        fun supply(){
            if(v.layer!=GameView.Layer.MAP)return
            val s=v.currentSnapshot();val hero=s.characters.first()
            if(hero.statusMask and OriginalStatus.POISON!=0){
                assertTrue("Normal supplied antidote exhausted; do not inject inventory",(s.inventory[AntidoteUse.ID]?:0)>0)
                mapMedicine(AntidoteUse.ID)
            }
            val h=v.currentSnapshot().characters.first()
            if(h.hp<=h.maxHp*2/3&&(v.currentSnapshot().inventory[HerbUse.ID]?:0)>0)mapMedicine(HerbUse.ID)
        }
        fun finishFight(){
            var entered=false;instrumentation.runOnMainSync{entered=v.layer==GameView.Layer.BATTLE};if(!entered)return
            fights++;val deadline=SystemClock.elapsedRealtime()+180000
            while(true){
                var pair:Pair<OpeningBattle,BattlePresentation>?=null
                instrumentation.runOnMainSync{if(v.layer==GameView.Layer.BATTLE)pair=(f.get(v) as OpeningBattle) to (p.get(v) as BattlePresentation)}
                val (battle,presentation)=pair?:break
                assertTrue("Normal north encounter exceeded budget",SystemClock.elapsedRealtime()<deadline)
                if(battle.phase==BattlePhase.DEFEAT){
                    val enemies=org.json.JSONArray()
                    for(enemy in battle.enemies)enemies.put(JSONObject().put("slot",enemy.slot)
                        .put("definitionId",enemy.definition.id).put("hp",enemy.hp).put("maxHp",enemy.definition.hp))
                    File(instrumentation.targetContext.getExternalFilesDir(null),"world-$label-failed-battle.json")
                        .writeText(JSONObject().put("kind","FAILED_INPUT_RUN_BATTLE_OBSERVATION")
                            .put("inputOrigin",kind).put("battleNumber",fights).put("remainingHerbs",v.battleHerbCount())
                            .put("heroHp",battle.hero.hp).put("heroMaxHp",battle.hero.maxHp)
                            .put("heroStatus",battle.hero.statusMask).put("enemies",enemies)
                            .put("displayedActionKind",presentation.action?.kind?.name)
                            .put("displayedHeroHp",presentation.action?.heroHp).put("world",v.currentSnapshot().json()).toString())
                    state("failed-battle-$fights")
                }
                assertTrue("Normal north player defeated; no state repair allowed",battle.phase!=BattlePhase.DEFEAT)
                if(!capturedPoison&&presentation.screen==BattlePresentation.Screen.ACTING&&presentation.action?.kind==BattleActionKind.STATUS){
                    state("original-enemy-poison-action");capturedPoison=true
                }
                if(presentation.screen in listOf(BattlePresentation.Screen.COMMAND,BattlePresentation.Screen.TARGET)){
                    if(battle.hero.hp<=battle.hero.maxHp*2/3&&v.battleHerbCount()>0){
                        // Normal direct touch medicine, consuming the original turn and inventory.
                        // The previous driver attacked until death even while carrying usable herbs.
                        val before=battle.hero;val count=v.battleHerbCount()
                        tap(v,center(v.battleCommandBounds(2)));tap(v,center(v.battleItemBounds(HerbUse.ID)))
                        assertEquals(before,battle.hero);assertEquals(count,v.battleHerbCount())
                        tap(v,center(v.battleItemUseBounds()));assertEquals(count-1,v.battleHerbCount());battleHerbs++
                    }else chooseAttack(v,battle.enemies.first{it.hp>0}.slot)
                }
                SystemClock.sleep(40)
            }
            assertEquals(GameView.Layer.MAP,v.layer);supply()
        }
        fun step(key:Key){supply();stickStep(v,key);finishFight();supply()}
        fun walkTo(tx:Int,ty:Int){
            val scene=v.world.scene;val goal=ty*scene.width+tx;var attempts=0
            while(v.world.mapId==scene.mapId&&v.world.y/16*scene.width+v.world.x/16!=goal){
                assertTrue("Normal north path did not converge",attempts++<2000)
                val start=v.world.y/16*scene.width+v.world.x/16;val queue=java.util.ArrayDeque<Int>();queue.add(start)
                val parents=mutableMapOf(start to (-1 to Key.UP))
                while(queue.isNotEmpty()&&goal !in parents){
                    val at=queue.removeFirst();val x=at%scene.width;val y=at/scene.width
                    for((key,d)in listOf(Key.UP to (0 to -1),Key.DOWN to (0 to 1),Key.LEFT to (-1 to 0),Key.RIGHT to (1 to 0))){
                        val nx=x+d.first;val ny=y+d.second;if(scene.probeFrom(x,y,key)!=MovementBlock.NONE)continue
                        val next=ny*scene.width+nx
                        if(next in parents||(next!=goal&&v.content.exits.any{it.fromMapId==scene.mapId&&it.triggerX==nx&&it.triggerY==ny&&it.edgeDirection==null}))continue
                        parents[next]=at to key;queue.add(next)
                    }
                }
                assertTrue("Missing legal north route ${scene.mapId} $tx,$ty",goal in parents)
                var next=goal;while(parents.getValue(next).first!=start)next=parents.getValue(next).first
                step(parents.getValue(next).second)
            }
        }
        fun talk(){tap(v,center(layoutFor(v).buttons.getValue(Key.A)));repeat(16){if(v.layer==GameView.Layer.DIALOGUE)tap(v,Pair(v.width*.5f,v.height*.5f))}}
        state("verified-normal-source-loaded")
        if(v.world.mapId==97){walkTo(15,29);assertEquals(25,v.world.mapId)}
        else{assertEquals(25,v.world.mapId);assertEquals(true,source.flags["rom.map.139.flag.128"])}
        walkTo(39,42);assertEquals(16,v.world.mapId)
        walkTo(202,130);assertEquals(0,v.world.mapId)
        val entry=v.content.exits.first{it.fromMapId==0&&it.toMapId==19};walkTo(entry.triggerX,entry.triggerY)
        assertEquals(InteriorContext(0,24,25),v.currentSnapshot().interiorContext)
        val npc=v.content.npcs.first{it.mapId==19&&it.shopId!=null};walkTo(npc.interactionCell!!.first,npc.interactionCell.second)
        talk();assertEquals(GameView.Layer.SHOP,v.layer)
        tap(v,center(v.shopActionBounds(1)));scrollToShopItem(v,AntidoteUse.ID);tap(v,center(v.shopItemBounds(AntidoteUse.ID)))
        val count=minOf((10-(v.currentSnapshot().inventory[AntidoteUse.ID]?:0)).coerceAtLeast(0),v.currentSnapshot().money/20)
        assertTrue("Legitimate source must fund or already carry antidotes",(v.currentSnapshot().inventory[AntidoteUse.ID]?:0)+count>=2)
        repeat(count){val before=v.currentSnapshot();tap(v,center(v.shopActionBounds(4)))
            assertEquals(before.money-20,v.currentSnapshot().money)
            assertEquals((before.inventory[AntidoteUse.ID]?:0)+1,v.currentSnapshot().inventory[AntidoteUse.ID])}
        state("normal-antidote-purchased")
        val hcount=minOf(10-(v.currentSnapshot().inventory[HerbUse.ID]?:0),v.currentSnapshot().money/15)
        if(hcount>0){scrollToShopItem(v,HerbUse.ID);tap(v,center(v.shopItemBounds(HerbUse.ID)))
            repeat(hcount){val before=v.currentSnapshot();tap(v,center(v.shopActionBounds(4)));assertEquals(before.money-15,v.currentSnapshot().money)}}
        tap(v,center(v.shopActionBounds(3)));val exit=v.content.exits.first{it.fromMapId==19};walkTo(exit.triggerX,exit.triggerY)
        assertNull(v.currentSnapshot().interiorContext);assertEquals(0,v.world.mapId)
        walkTo(0,14);step(Key.LEFT);assertEquals(16,v.world.mapId);walkTo(199,130);assertEquals(25,v.world.mapId)
        state("sea-route-start")
        walkTo(26,14);assertEquals(16,v.world.mapId);assertEquals(186,v.world.x/16);assertEquals(102,v.world.y/16)
        assertEquals(0,v.currentSnapshot().encounterSteps);state("northwest-land-arrival")
        walkTo(187,102);walkTo(186,102);assertEquals(25,v.world.mapId);assertEquals(26,v.world.x/16);assertEquals(14,v.world.y/16)
        assertEquals(0,v.currentSnapshot().encounterSteps);assertEquals(true,v.currentSnapshot().flags["rom.event.97.39.1"])
        state("northwest-sea-return");step(Key.DOWN);state("next-operable")
        instrumentation.runOnMainSync{v.persistState()}
        File(instrumentation.targetContext.getExternalFilesDir(null),"world-$label-expected-save.json").writeText(v.currentSnapshot().json().toString())
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testWorldNorthColdStartMatchesNormalSave(){
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"world-north-expected-save.json")
        assertTrue(file.exists());val expected=SaveSnapshot.parse(file.readText());val(activity,v)=launch()
        assertEquals(expected,v.currentSnapshot());assertEquals(25,v.world.mapId)
        assertEquals(0,expected.characters.first().statusMask)
        stickStep(v,Key.DOWN);assertEquals(expected.characters,v.currentSnapshot().characters)
        assertEquals(expected.inventory,v.currentSnapshot().inventory);assertEquals(expected.flags,v.currentSnapshot().flags)
        assertEquals(expected.money,v.currentSnapshot().money);assertEquals(25,v.world.mapId)
        screenshot(v,"world-north-cold-restored-continue");instrumentation.runOnMainSync{v.persistState();activity.finish()}
    }

    /** Two isolated continuations load only the byte-exact save from this candidate's normal Nanhai run. */
    fun testNormalWorldWestPalaceFromVerifiedNanhaiSave(){normalWorldBatchContinuation(true)}
    fun testNormalWorldVillageOneServicesFromVerifiedNanhaiSave(){normalWorldBatchContinuation(false)}
    fun testNormalWorldVillageOneServicesFromVerifiedNorthPalaceSave(){normalWorldBatchContinuation(false,"world-north-palace-expected-save.json")}
    private fun normalWorldBatchContinuation(west:Boolean,sourceName:String="nanhai-expected-save.json"){
        val root=instrumentation.targetContext.getExternalFilesDir(null)
        val sourceFile=File(root,sourceName)
        assertTrue("The same candidate's normal Nanhai recording must produce this checkpoint",sourceFile.exists())
        val sourceBytes=sourceFile.readBytes();val source=SaveSnapshot.parse(sourceBytes.toString(Charsets.UTF_8))
        val sourceHash=java.security.MessageDigest.getInstance("SHA-256").digest(sourceBytes).joinToString(""){"%02x".format(it)}
        assertEquals(true,source.flags["rom.event.97.39.1"])
        if(west)assertTrue("Normal source must precede West victory",source.flags["rom.event.96.40.2"]!=true)
        val(activity,v)=launch();instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(source))}
        assertEquals("No resources or flags may be changed at continuation load",source,v.currentSnapshot())
        val label=if(west)"west" else "village1";val events=org.json.JSONArray();val started=SystemClock.elapsedRealtime()
        var fights=0;var battleHerbs=0;var bossHerbs=0;var capturedIce=false;var capturedBattleHerb=false
        val battleField=GameView::class.java.getDeclaredField("battle").apply{isAccessible=true}
        val presentationField=GameView::class.java.getDeclaredField("battlePresentation").apply{isAccessible=true}
        val committedField=GameView::class.java.getDeclaredField("battleCommitted").apply{isAccessible=true}
        fun state(name:String,capture:Boolean=true){
            if(capture)screenshot(v,"world-$label-$name")
            events.put(org.json.JSONObject().put("name",name).put("elapsedMs",SystemClock.elapsedRealtime()-started)
                .put("androidUptimeMs",SystemClock.elapsedRealtime()).put("snapshot",v.currentSnapshot().json()))
            File(root,"world-$label-normal-index.json").writeText(org.json.JSONObject()
                .put("kind","CONTINUATION_FROM_VERIFIED_NORMAL_SAVE").put("sourceFile",sourceFile.name)
                .put("sourceSha256",sourceHash).put("sourceSnapshot",source.json()).put("stateChangesAtLoad",false)
                .put("events",events).put("fights",fights).put("battleHerbs",battleHerbs).put("bossHerbs",bossHerbs)
                .put("westIceObserved",capturedIce).toString())
        }
        fun medicine(id:String){
            assertEquals(GameView.Layer.MAP,v.layer);val before=v.currentSnapshot()
            assertTrue("Normal supply exhausted: $id; never inject inventory",(before.inventory[id]?:0)>0)
            tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));scrollToItem(v,id)
            tap(v,center(v.panelItemBounds(id)));assertEquals(before,v.currentSnapshot())
            tap(v,center(v.panelPrimaryBounds()));val after=v.currentSnapshot()
            if(id==HerbUse.ID){
                assertEquals(minOf(before.characters.first().maxHp,before.characters.first().hp+50),after.characters.first().hp)
                assertEquals((before.inventory[id]?:0)-1,after.inventory[id]?:0)
            }else{
                assertEquals(0,after.characters.first().statusMask and OriginalStatus.POISON)
                assertEquals(before.characters.first().hp,after.characters.first().hp)
                assertEquals(((before.inventory[id]?:0)-2).coerceAtLeast(0),after.inventory[id]?:0)
            }
            state(if(id==HerbUse.ID)"normal-map-herb" else "normal-map-antidote")
            instrumentation.runOnMainSync{v.handleBack()};assertEquals(GameView.Layer.MAP,v.layer)
        }
        fun supply(){
            if(v.layer!=GameView.Layer.MAP)return
            if(v.currentSnapshot().characters.first().statusMask and OriginalStatus.POISON!=0)medicine(AntidoteUse.ID)
            val s=v.currentSnapshot();val h=s.characters.first()
            // The continuation crosses genuine multi-enemy sea groups. A half-HP
            // policy could die before the next turn; use bought herbs earlier.
            if(h.hp<=h.maxHp*3/4&&(s.inventory[HerbUse.ID]?:0)>0)medicine(HerbUse.ID)
        }
        fun finishFight(){
            var entered:OpeningBattle?=null
            instrumentation.runOnMainSync{if(v.layer==GameView.Layer.BATTLE)entered=battleField.get(v) as OpeningBattle}
            val initial=entered?:return;val boss=initial.enemies.any{it.definition.id==138};fights++
            if(boss)state("boss-entry")
            val deadline=SystemClock.elapsedRealtime()+240000
            while(true){
                var observed:Triple<OpeningBattle,BattlePresentation,Boolean>?=null
                instrumentation.runOnMainSync{if(v.layer==GameView.Layer.BATTLE)observed=Triple(
                    battleField.get(v) as OpeningBattle,presentationField.get(v) as BattlePresentation,committedField.getBoolean(v))}
                val (fight,presentation,committed)=observed?:break
                assertTrue("Normal $label encounter exceeded budget",SystemClock.elapsedRealtime()<deadline)
                assertTrue("Normal $label defeat; no state repair or forced victory permitted",fight.phase!=BattlePhase.DEFEAT)
                if(boss&&!capturedIce&&presentation.screen==BattlePresentation.Screen.ACTING&&presentation.action?.kind==BattleActionKind.ICE){
                    state("boss-ice-action");capturedIce=true
                }
                if(boss&&!capturedBattleHerb&&presentation.screen==BattlePresentation.Screen.ACTING&&presentation.action?.kind==BattleActionKind.HEAL){
                    val action=presentation.action!!;assertEquals(minOf(fight.hero.maxHp,action.beforeHeroHp+50),action.heroHp)
                    state("boss-herb-action");capturedBattleHerb=true
                }
                if(presentation.screen in listOf(BattlePresentation.Screen.COMMAND,BattlePresentation.Screen.TARGET)){
                    val heal=fight.hero.hp<=fight.hero.maxHp*3/4||(boss&&bossHerbs==0&&fight.hero.hp<fight.hero.maxHp)
                    if(heal&&v.battleHerbCount()>0){
                        val before=fight.hero;val count=v.battleHerbCount()
                        tap(v,center(v.battleCommandBounds(2)));tap(v,center(v.battleItemBounds(HerbUse.ID)))
                        assertEquals(before,fight.hero);assertEquals(count,v.battleHerbCount())
                        tap(v,center(v.battleItemUseBounds()));assertEquals(count-1,v.battleHerbCount())
                        battleHerbs++;if(boss)bossHerbs++
                    }else chooseAttack(v,fight.enemies.first{it.hp>0}.slot)
                }else if(boss&&presentation.screen==BattlePresentation.Screen.RESULT&&committed){
                    state("boss-victory-result");tap(v,center(v.battleResultBounds()))
                }
                SystemClock.sleep(40)
            }
            if(!boss){assertEquals(GameView.Layer.MAP,v.layer);supply()}
        }
        fun step(key:Key){assertEquals(GameView.Layer.MAP,v.layer);stickStep(v,key);finishFight();supply()}
        // Read-only BFS includes the original terrain plane. Every chosen edge is
        // executed through real joystick gestures; no world.tick/restore/teleport.
        fun walkTo(tx:Int,ty:Int){
            val routeMap=v.world.mapId;var replans=0
            while(v.world.mapId==routeMap&&(v.world.x/16!=tx||v.world.y/16!=ty)){
            assertTrue("Normal $label path did not converge; no position repair",replans++<4096)
            val scene=v.world.scene;val start=(v.world.y/16*scene.width+v.world.x/16) to v.world.terrainMode
            val target=ty*scene.width+tx;if(start.first==target)return
            val queue=java.util.ArrayDeque<Pair<Int,Int>>();queue.add(start)
            val parents=mutableMapOf<Pair<Int,Int>,Pair<Pair<Int,Int>,Key>>()
            parents[start]=start to Key.UP;var goal:Pair<Int,Int>?=null
            while(queue.isNotEmpty()&&goal==null){
                val at=queue.removeFirst();val x=at.first%scene.width;val y=at.first/scene.width
                for((key,d)in listOf(Key.UP to (0 to -1),Key.DOWN to (0 to 1),Key.LEFT to (-1 to 0),Key.RIGHT to (1 to 0))){
                    if(scene.probeFrom(x,y,key,at.second)!=MovementBlock.NONE)continue
                    val nx=x+d.first;val ny=y+d.second;val cell=ny*scene.width+nx
                    val next=cell to scene.terrainDecision(x,y,key,at.second).nextMode
                    if(next in parents||(cell!=target&&v.content.exits.any{it.fromMapId==scene.mapId&&it.triggerX==nx&&it.triggerY==ny&&it.edgeDirection==null}))continue
                    parents[next]=at to key
                    if(cell==target){goal=next;break};queue.add(next)
                }
            }
            assertNotNull("No original legal $label route map=${scene.mapId} plane=${start.second} to $tx,$ty",goal)
            val keys=mutableListOf<Key>();var cursor=goal!!
            while(cursor!=start){val parent=parents.getValue(cursor);keys.add(parent.second);cursor=parent.first}
            for((index,key)in keys.asReversed().withIndex()){
                assertEquals("Unexpected map before route step",scene.mapId,v.world.mapId)
                val beforeX=v.world.x/16;val beforeY=v.world.y/16;step(key)
                if(v.world.mapId!=scene.mapId){
                    val completed=v.world.lastCompletedStep!!
                    assertEquals(scene.mapId,completed.mapId);assertTrue(completed.transitioned)
                    assertEquals("Exit must be the requested original cell",tx to ty,completed.x to completed.y)
                    return
                }
                val expectedX=beforeX+if(key==Key.RIGHT)1 else if(key==Key.LEFT)-1 else 0
                val expectedY=beforeY+if(key==Key.DOWN)1 else if(key==Key.UP)-1 else 0
                // A held real joystick can travel farther under runner load.
                // Replan from its observed legal location instead of replaying stale keys.
                if(v.world.x/16!=expectedX||v.world.y/16!=expectedY)break
            }
            }
        }
        fun dialogue(){repeat(24){if(v.layer==GameView.Layer.DIALOGUE)tap(v,Pair(v.width*.5f,v.height*.5f))}}
        fun talk(){tap(v,center(layoutFor(v).buttons.getValue(Key.A)));dialogue()}
        fun enterService(caller:Int,room:Int):MapExit{
            assertEquals(caller,v.world.mapId);val entry=v.content.exits.first{it.fromMapId==caller&&it.toMapId==room}
            if(v.world.x/16==entry.triggerX&&v.world.y/16==entry.triggerY){
                // The actual return lands on the door. Walking to the same cell
                // is zero input; leave it normally before crossing it again.
                val departure=listOf(Key.DOWN,Key.LEFT,Key.RIGHT,Key.UP).first{key->
                    v.world.scene.probeFrom(entry.triggerX,entry.triggerY,key,v.world.terrainMode)==MovementBlock.NONE}
                step(departure);assertEquals(caller,v.world.mapId)
            }
            walkTo(entry.triggerX,entry.triggerY);assertEquals(room,v.world.mapId)
            assertEquals(InteriorContext(caller,entry.triggerX,entry.triggerY),v.currentSnapshot().interiorContext)
            val keeper=v.content.npcs.first{it.mapId==room&&(if(room==22)it.innId!=null else it.shopId!=null)}
            walkTo(keeper.interactionCell!!.first,keeper.interactionCell.second);talk()
            assertEquals(if(room==22)GameView.Layer.INN else GameView.Layer.SHOP,v.layer)
            return entry
        }
        fun leaveService(entry:MapExit){
            if(v.layer==GameView.Layer.SHOP)tap(v,center(v.shopActionBounds(3)))
            if(v.layer==GameView.Layer.INN)instrumentation.runOnMainSync{v.handleBack()}
            val exit=v.content.exits.first{it.fromMapId==entry.toMapId&&it.returnToCaller}
            walkTo(exit.triggerX,exit.triggerY);assertEquals(entry.fromMapId,v.world.mapId)
            assertEquals(entry.triggerX,v.world.x/16);assertEquals(entry.triggerY,v.world.y/16)
            assertNull(v.currentSnapshot().interiorContext)
        }
        fun trade(id:String,buy:Boolean,count:Int=1){
            assertEquals(GameView.Layer.SHOP,v.layer);tap(v,center(v.shopActionBounds(if(buy)1 else 2)))
            scrollToShopItem(v,id);val selected=v.currentSnapshot();tap(v,center(v.shopItemBounds(id)));assertEquals(selected,v.currentSnapshot())
            val item=v.content.itemDefinitions.getValue(id);val price=if(buy)item.buyPrice!! else item.sellPrice!!
            repeat(count){val before=v.currentSnapshot();tap(v,center(v.shopActionBounds(4)))
                assertEquals(before.money+if(buy)-price else price,v.currentSnapshot().money)
                assertEquals((before.inventory[id]?:0)+if(buy)1 else -1,v.currentSnapshot().inventory[id]?:0)
                val after=v.currentSnapshot();send(v,MotionEvent.ACTION_UP,listOf(center(v.shopActionBounds(4))));assertEquals(after,v.currentSnapshot())}
        }
        fun currentShop()=GameView::class.java.getDeclaredField("shop").apply{isAccessible=true}.get(v) as ShopDefinition
        fun equip(id:String){
            tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));scrollToItem(v,id)
            val before=v.currentSnapshot();tap(v,center(v.panelItemBounds(id)));assertEquals(before,v.currentSnapshot())
            tap(v,center(v.panelPrimaryBounds()));assertEquals(v.content.equipmentDefinitions.getValue(id).originalId,v.currentSnapshot().characters.first().equipment!!.rightHand)
            instrumentation.runOnMainSync{v.handleBack()}
        }
        state("verified-normal-source-loaded")
        if(v.world.mapId==97){walkTo(15,29);assertEquals(25,v.world.mapId)}
        else{assertEquals(25,v.world.mapId);assertEquals(true,source.flags["rom.map.139.flag.128"])}
        walkTo(39,42);assertEquals(16,v.world.mapId)
        walkTo(202,130);assertEquals(0,v.world.mapId)
        // SecureRandom makes the legitimate prior run's earnings variable. If
        // needed, earn the shortfall in the already verified opening zone and pay
        // the existing inn; do not change money/HP or rely on a particular run's 303.
        fun neededSupplyMoney():Int {
            val saved=v.currentSnapshot()
            val pills=((if(west)6 else 3)-(saved.inventory[AntidoteUse.ID]?:0)).coerceAtLeast(0)*20
            val herbs=(10-(saved.inventory[HerbUse.ID]?:0)).coerceAtLeast(0)*15
            // Budget the peak real cost of the whole buy/equip/restore/sell path,
            // not only the initial weapon. Original random earnings vary.
            val serviceBudget=if(west)0 else run {
                val weapon=v.content.itemDefinitions.getValue("rom.weapon.3")
                val armor=v.content.itemDefinitions.getValue("rom.armor.1")
                val medicine=v.content.itemDefinitions.getValue("rom.medicine.12")
                val inn=v.content.inns.getValue("rom.inn.1").price
                val afterWeapon=weapon.buyPrice!!-weapon.sellPrice!!
                val afterArmor=afterWeapon+armor.buyPrice!!-armor.sellPrice!!
                maxOf(weapon.buyPrice,afterWeapon+armor.buyPrice,
                    afterArmor+medicine.buyPrice!!,afterArmor+medicine.buyPrice-medicine.sellPrice!!+inn)
            }
            return pills+herbs+serviceBudget
        }
        if(v.currentSnapshot().money<neededSupplyMoney()){
            state("normal-earned-supply-start")
            walkTo(0,14);step(Key.LEFT);walkTo(200,130);var trainingSteps=0
            while(v.currentSnapshot().money<neededSupplyMoney()+4){
                assertTrue("Bounded normal supply earnings exhausted; no resource grant",trainingSteps++<3000)
                if(v.currentSnapshot().characters.first().hp<=v.currentSnapshot().characters.first().maxHp/2){
                    walkTo(202,130);assertEquals(0,v.world.mapId);val entry=enterService(0,22)
                    val before=v.currentSnapshot();assertTrue("Earned funds must pay the original inn",before.money>=4)
                    tap(v,center(v.innStayBounds()));assertEquals(before.money-4,v.currentSnapshot().money)
                    assertEquals(v.currentSnapshot().characters.first().maxHp,v.currentSnapshot().characters.first().hp)
                    leaveService(entry);walkTo(0,14);step(Key.LEFT);walkTo(200,130)
                }
                var trainingDirection:Key?=null
                instrumentation.runOnMainSync{
                    val x=v.world.x/16;val y=v.world.y/16
                    val preferred=if(y>130)Key.UP else Key.DOWN
                    trainingDirection=(listOf(preferred)+listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT).filter{it!=preferred}).firstOrNull{key->
                        val nx=x+if(key==Key.RIGHT)1 else if(key==Key.LEFT)-1 else 0
                        val ny=y+if(key==Key.DOWN)1 else if(key==Key.UP)-1 else 0
                        v.world.scene.probeFrom(x,y,key,v.world.terrainMode)==MovementBlock.NONE&&
                            inExistingEncounterRegion(v.content.battle!!,v.world.mapId,nx,ny)&&
                            v.content.exits.none{it.fromMapId==v.world.mapId&&it.triggerX==nx&&it.triggerY==ny}
                    }
                }
                assertNotNull("No legal normal training step; no collision bypass",trainingDirection)
                step(trainingDirection!!)
            }
            walkTo(202,130);assertEquals(0,v.world.mapId);state("normal-earned-supply-complete")
        }
        val supplyEntry=enterService(0,19)
        val antidotes=((if(west)6 else 3)-(v.currentSnapshot().inventory[AntidoteUse.ID]?:0)).coerceAtLeast(0)
        if(antidotes>0)trade(AntidoteUse.ID,true,antidotes)
        run{
            val herbs=(10-(v.currentSnapshot().inventory[HerbUse.ID]?:0)).coerceAtLeast(0)
            assertTrue("Normally earned funds must buy the intended route supply",v.currentSnapshot().money>=herbs*15)
            if(herbs>0)trade(HerbUse.ID,true,herbs)
        }
        state("normal-town-supply-purchased");leaveService(supplyEntry)
        walkTo(0,14);step(Key.LEFT);assertEquals(16,v.world.mapId)
        walkTo(199,130);assertEquals(25,v.world.mapId);state("sea-route-start")
        if(west){
            walkTo(5,24);assertEquals(96,v.world.mapId);assertEquals(15,v.world.x/16);assertEquals(29,v.world.y/16)
            assertEquals(0,v.world.terrainMode);state("palace-entry")
            // Exactly the 224 normal movements/225 points witnessed in
            // world-terrain.json normalContinuation.routeFromEntryToNpc (grid SHA c6a5a1ad...).
            val witnessed="U7 L4 U1 L1 U1 L1 U1 L1 U1 L1 U1 L5 D6 R1 D6 L3 U29 R31 D29 L3 U8 L16 U2 L1 U1 L1 U2 L3 U1 L3 U6 L2 U4 R1 U1 R20 D8 L4 U2 L1 U1 L3"
            var moves=0;var capturedUpper=false
            for(run in witnessed.split(" ")){
                val key=when(run.first()){'U'->Key.UP;'D'->Key.DOWN;'L'->Key.LEFT;else->Key.RIGHT}
                repeat(run.substring(1).toInt()){step(key);moves++
                    assertEquals(96,v.world.mapId)
                    if(v.world.terrainMode==1&&!capturedUpper){state("palace-upper-plane");capturedUpper=true}}
            }
            assertEquals(224,moves);assertTrue(capturedUpper);assertEquals(15,v.world.x/16);assertEquals(9,v.world.y/16)
            assertEquals(0,v.world.terrainMode);state("boss-approach")
            if(v.currentSnapshot().characters.first().hp<v.currentSnapshot().characters.first().maxHp)medicine(HerbUse.ID)
            val beforeBoss=v.currentSnapshot();assertTrue(beforeBoss.flags["rom.event.96.40.2"]!=true)
            tap(v,center(layoutFor(v).buttons.getValue(Key.A)));assertEquals(GameView.Layer.DIALOGUE,v.layer);state("boss-dialogue")
            dialogue();assertEquals(GameView.Layer.BATTLE,v.layer);finishFight();assertEquals(GameView.Layer.DIALOGUE,v.layer)
            val won=v.currentSnapshot();assertEquals(beforeBoss.money+150,won.money)
            assertEquals(beforeBoss.characters.first().experience+80,won.characters.first().experience)
            assertEquals(true,won.flags["rom.event.96.40.2"]);assertEquals(true,won.flags["rom.event.97.39.1"])
            assertTrue("Normally bought herb must actually run during West fight",bossHerbs>0);assertTrue(capturedBattleHerb)
            assertEquals((beforeBoss.inventory[HerbUse.ID]?:0)-bossHerbs,won.inventory[HerbUse.ID]?:0)
            state("victory-dialogue");dialogue();assertEquals(GameView.Layer.MAP,v.layer)
            assertTrue(v.currentSnapshot().flags["rom.event.96.40.2.dialogue.pending"]!=true)
            val settled=v.currentSnapshot();talk();assertEquals(GameView.Layer.MAP,v.layer);assertEquals(settled,v.currentSnapshot())
            state("repeat-no-reward")
            walkTo(15,29);assertEquals(25,v.world.mapId);assertEquals(5,v.world.x/16);assertEquals(24,v.world.y/16)
            assertEquals(0,v.world.terrainMode);state("palace-return")
            // Move off the genuine doorway before re-entry. Pick an actual legal
            // non-exit neighbor; the exit direction is not assumed from its shape.
            val departure=listOf(Key.DOWN,Key.UP,Key.RIGHT,Key.LEFT).first{v.world.scene.probeFrom(5,24,it,0)==MovementBlock.NONE}
            step(departure);walkTo(5,24);assertEquals(96,v.world.mapId)
            walkTo(15,9);val beforeRepeat=v.currentSnapshot()
            tap(v,center(layoutFor(v).buttons.getValue(Key.A)));assertEquals(GameView.Layer.DIALOGUE,v.layer)
            state("reentered-repeat-dialogue");dialogue();assertEquals(GameView.Layer.MAP,v.layer)
            assertEquals(beforeRepeat.copy(direction=Key.UP),v.currentSnapshot());state("reentered-no-reward")
            // Persist an actual upper-plane position reached through normal moves,
            // so external force-stop checks more than the old ground-plane schema.
            walkTo(2,19);assertEquals(1,v.world.terrainMode);state("upper-plane-save-checkpoint")
        }else{
            walkTo(26,14);assertEquals(16,v.world.mapId);assertEquals(186,v.world.x/16);assertEquals(102,v.world.y/16)
            state("northwest-land-arrival");walkTo(191,102);assertEquals(1,v.world.mapId)
            assertEquals(15,v.world.x/16);assertEquals(29,v.world.y/16);state("village-arrival")
            val weaponEntry=enterService(1,17)
            assertEquals(listOf("rom.weapon.2","rom.weapon.3"),currentShop().items)
            assertEquals(200,v.content.itemDefinitions.getValue("rom.weapon.3").buyPrice)
            assertTrue("Normal continuation funds must cover fishbone sword",v.currentSnapshot().money>=200)
            trade("rom.weapon.3",true);state("weapon-purchased");leaveService(weaponEntry)
            val oldGear=v.currentSnapshot();val oldWeapon=oldGear.characters.first().equipment!!.rightHand
            val oldId=v.content.equipmentDefinitions.values.single{it.slot=="rightHand"&&it.originalId==oldWeapon}.itemId
            equip("rom.weapon.3");assertEquals((oldGear.inventory[oldId]?:0)+1,v.currentSnapshot().inventory[oldId]?:0)
            assertEquals((oldGear.inventory["rom.weapon.3"]?:0)-1,v.currentSnapshot().inventory["rom.weapon.3"]?:0)
            state("fishbone-equipped")
            tap(v,center(v.hudBounds()));tap(v,tabPoint(v,1));tap(v,center(v.panelSlotBounds("rightHand")))
            tap(v,center(v.panelPrimaryBounds()));assertEquals(-1,v.currentSnapshot().characters.first().equipment!!.rightHand)
            instrumentation.runOnMainSync{v.handleBack()};equip(oldId)
            assertEquals(oldGear.characters,v.currentSnapshot().characters);assertEquals(oldGear.inventory,v.currentSnapshot().inventory)
            state("fishbone-unloaded-old-gear-restored")
            enterService(1,17);trade("rom.weapon.3",false);state("weapon-sold");leaveService(weaponEntry)
            val armorEntry=enterService(1,18)
            assertEquals(listOf("rom.armor.1","rom.armor.2","rom.armor.28","rom.armor.29"),currentShop().items)
            assertEquals(200,v.content.itemDefinitions.getValue("rom.armor.2").buyPrice)
            assertEquals(500,v.content.itemDefinitions.getValue("rom.armor.29").buyPrice)
            // The expensive boots are displayed from the current merchant's real
            // stock; normal funds are not replaced with a test-only grant.
            tap(v,center(v.shopActionBounds(1)));scrollToShopItem(v,"rom.armor.29")
            val beforeBoot=v.currentSnapshot();tap(v,center(v.shopItemBounds("rom.armor.29")));assertEquals(beforeBoot,v.currentSnapshot())
            state("armor-new-stock")
            tap(v,center(v.shopActionBounds(2)));tap(v,center(v.shopActionBounds(1))) // return list to top by a real mode change
            trade("rom.armor.1",true);trade("rom.armor.1",false);state("armor-bought-and-sold");leaveService(armorEntry)
            val itemEntry=enterService(1,19)
            assertEquals(listOf(HerbUse.ID,AntidoteUse.ID,"rom.medicine.12"),currentShop().items)
            assertEquals(80,v.content.itemDefinitions.getValue("rom.medicine.12").buyPrice)
            trade("rom.medicine.12",true);trade("rom.medicine.12",false);state("medicine-new-stock-traded");leaveService(itemEntry)
            val innEntry=enterService(1,22);assertEquals(15,innEntry.triggerX);assertEquals(21,innEntry.triggerY)
            val service=v.content.inns.getValue(v.activeInnId!!);assertEquals(8,service.price)
            assertTrue("Shared room must resolve village1 service, not town0",service.id!="rom.inn.0")
            val beforeInn=v.currentSnapshot();state("inn-eight-offer")
            instrumentation.runOnMainSync{v.handleBack()};assertEquals(beforeInn,v.currentSnapshot());talk()
            tap(v,center(v.innStayBounds()));assertEquals(GameView.Layer.MAP,v.layer)
            assertEquals(beforeInn.money-8,v.currentSnapshot().money)
            assertEquals(beforeInn.inventory,v.currentSnapshot().inventory);assertEquals(beforeInn.flags,v.currentSnapshot().flags)
            assertEquals(v.currentSnapshot().characters.first().maxHp,v.currentSnapshot().characters.first().hp)
            val paid=v.currentSnapshot();repeat(3){send(v,MotionEvent.ACTION_UP,listOf(center(v.innStayBounds())))};assertEquals(paid,v.currentSnapshot())
            state("inn-paid-caller-checkpoint")
            // External cold test must leave this actual shared interior back to
            // village1(15,21), preserving the paid state and original South flag.
            assertEquals(InteriorContext(1,15,21),v.currentSnapshot().interiorContext)
        }
        assertEquals(true,v.currentSnapshot().flags["rom.event.97.39.1"])
        assertEquals("Source checkpoint file must remain byte-exact",sourceHash,
            java.security.MessageDigest.getInstance("SHA-256").digest(sourceFile.readBytes()).joinToString(""){"%02x".format(it)})
        instrumentation.runOnMainSync{v.persistState()}
        File(root,"world-$label-expected-save.json").writeText(v.currentSnapshot().json().toString())
        state("persisted-for-external-cold-restart",false);instrumentation.runOnMainSync{activity.finish()}
    }
    /** Isolated replay of a real normal checkpoint, not this candidate's main route. */
    fun testControlledNorthRepeatAfterOptionalMapSupply(){
        val fixture=JSONObject(instrumentation.context.assets.open("north-repeat-before-optional-herb.json")
            .bufferedReader(Charsets.UTF_8).use{it.readText()})
        assertEquals("CONTROLLED_REPLAY_OF_VERIFIED_NORMAL_APP_CHECKPOINT",fixture.getString("kind"))
        val recorded=SaveSnapshot.parse(fixture.getJSONObject("snapshot").toString())
        // 46 is the unmodified normal source; 4 is an explicit isolated boundary.
        // At max109, one herb from4 leaves54 and the automatic supply helper
        // would use another on the next step. Reentry must isolate movement.
        fun verify(hp:Int){
        val source=if(hp==46)recorded else recorded.copy(characters=recorded.characters.map{it.copy(hp=hp)})
        val(activity,v)=launch()
        assertTrue("Historical normal checkpoint must validate against actual content",source.validate(v.content))
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(source))}
        val before=v.currentSnapshot();assertEquals(139,before.mapId)
        assertEquals(true,before.flags["rom.map.139.flag.128"])
        assertEquals(true,before.flags["rom.map.139.flag.2"])
        assertEquals(hp,before.characters.single().hp);assertEquals(109,before.characters.single().maxHp)
        val expected=HerbUse.apply(before.characters,before.inventory,"nezha",
            v.content.itemDefinitions.getValue(HerbUse.ID),true)
        assertTrue(expected.applied)
        tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));scrollToItem(v,HerbUse.ID)
        tap(v,center(v.panelItemBounds(HerbUse.ID)));assertEquals(before,v.currentSnapshot())
        tap(v,center(v.panelPrimaryBounds()))
        val supplied=v.currentSnapshot()
        assertEquals(expected.characters,supplied.characters);assertEquals(expected.inventory,supplied.inventory)
        assertEquals(before.money,supplied.money);assertEquals(before.flags,supplied.flags)
        assertEquals(hp+50,supplied.characters.single().hp)
        assertEquals((before.inventory[HerbUse.ID]?:0)-1,supplied.inventory[HerbUse.ID]?:0)
        instrumentation.runOnMainSync{v.handleBack()}
        stickStep(v,Key.DOWN);stickStep(v,Key.UP)
        assertEquals(GameView.Layer.MAP,v.layer);assertEquals(139,v.world.mapId)
        assertEquals(2 to 4,v.world.x/16 to v.world.y/16)
        val repeated=v.currentSnapshot()
        assertEquals(supplied.characters,repeated.characters);assertEquals(supplied.inventory,repeated.inventory)
        assertEquals(supplied.money,repeated.money);assertEquals(supplied.flags,repeated.flags)
        tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
        assertEquals(repeated,v.currentSnapshot());assertEquals(GameView.Layer.MAP,v.layer)
        screenshot(v,"controlled-north-repeat-after-optional-herb-hp$hp")
        instrumentation.runOnMainSync{activity.finish()}
        }
        verify(46);verify(4)
    }
    /** Normal continuation: no fixture mutation beyond byte-exact same-candidate source load. */
    fun testNormalWorldNorthPalaceAndPearlFromVerifiedNanhaiSave(){
        val root=instrumentation.targetContext.getExternalFilesDir(null)
        val sourceFile=File(root,"nanhai-expected-save.json")
        assertTrue("The same candidate's normal Nanhai recording must produce this checkpoint",sourceFile.exists())
        val sourceBytes=sourceFile.readBytes();val source=SaveSnapshot.parse(sourceBytes.toString(Charsets.UTF_8))
        val sourceHash=java.security.MessageDigest.getInstance("SHA-256").digest(sourceBytes).joinToString(""){"%02x".format(it)}
        assertEquals(true,source.flags["rom.event.97.39.1"])
        assertTrue("Normal source must precede North victory",source.flags["rom.map.139.flag.128"]!=true)
        assertEquals(0,source.inventory[WorldItems.ID]?:0)
        val(activity,v)=launch();instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(source))}
        assertEquals("No resources or flags may be changed at continuation load",source,v.currentSnapshot())
        val label="north-palace";val events=org.json.JSONArray();val started=SystemClock.elapsedRealtime()
        var fights=0;var battleHerbs=0;var bossHerbs=0;var capturedIce=false;var capturedBattleHerb=false
        var training=false
        val battleField=GameView::class.java.getDeclaredField("battle").apply{isAccessible=true}
        val presentationField=GameView::class.java.getDeclaredField("battlePresentation").apply{isAccessible=true}
        val committedField=GameView::class.java.getDeclaredField("battleCommitted").apply{isAccessible=true}
        fun state(name:String,capture:Boolean=true){
            if(capture)screenshot(v,"world-$label-$name")
            events.put(org.json.JSONObject().put("name",name).put("elapsedMs",SystemClock.elapsedRealtime()-started)
                .put("androidUptimeMs",SystemClock.elapsedRealtime()).put("snapshot",v.currentSnapshot().json()))
            File(root,"world-$label-normal-index.json").writeText(org.json.JSONObject()
                .put("kind","CONTINUATION_FROM_VERIFIED_NORMAL_NANHAI_SAVE").put("sourceFile",sourceFile.name)
                .put("sourceSha256",sourceHash).put("sourceSnapshot",source.json()).put("stateChangesAtLoad",false)
                .put("events",events).put("fights",fights).put("battleHerbs",battleHerbs).put("bossHerbs",bossHerbs)
                .put("northIceObserved",capturedIce).toString())
        }
        fun medicine(id:String){
            assertEquals(GameView.Layer.MAP,v.layer);val before=v.currentSnapshot()
            assertTrue("Normal supply exhausted: $id; never inject inventory",(before.inventory[id]?:0)>0)
            tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));scrollToItem(v,id)
            tap(v,center(v.panelItemBounds(id)));assertEquals(before,v.currentSnapshot())
            tap(v,center(v.panelPrimaryBounds()));val after=v.currentSnapshot()
            if(id==HerbUse.ID){
                assertEquals(minOf(before.characters.first().maxHp,before.characters.first().hp+50),after.characters.first().hp)
                assertEquals((before.inventory[id]?:0)-1,after.inventory[id]?:0)
            }else{
                assertEquals(0,after.characters.first().statusMask and OriginalStatus.POISON)
                assertEquals(before.characters.first().hp,after.characters.first().hp)
                assertEquals(((before.inventory[id]?:0)-2).coerceAtLeast(0),after.inventory[id]?:0)
            }
            state(if(id==HerbUse.ID)"normal-map-herb" else "normal-map-antidote")
            instrumentation.runOnMainSync{v.handleBack()};assertEquals(GameView.Layer.MAP,v.layer)
        }
        fun supply(){
            if(v.layer!=GameView.Layer.MAP)return
            if(v.currentSnapshot().characters.first().statusMask and OriginalStatus.POISON!=0)medicine(AntidoteUse.ID)
            val s=v.currentSnapshot();val h=s.characters.first()
            if(!training&&h.hp<=h.maxHp/2&&(s.inventory[HerbUse.ID]?:0)>0)medicine(HerbUse.ID)
        }
        fun finishFight(){
            var entered:OpeningBattle?=null
            instrumentation.runOnMainSync{if(v.layer==GameView.Layer.BATTLE)entered=battleField.get(v) as OpeningBattle}
            val initial=entered?:return;val boss=initial.enemies.any{it.definition.id==139};fights++
            if(boss)state("boss-entry")
            val deadline=SystemClock.elapsedRealtime()+240000
            while(true){
                var observed:Triple<OpeningBattle,BattlePresentation,Boolean>?=null
                instrumentation.runOnMainSync{if(v.layer==GameView.Layer.BATTLE)observed=Triple(
                    battleField.get(v) as OpeningBattle,presentationField.get(v) as BattlePresentation,committedField.getBoolean(v))}
                val (fight,presentation,committed)=observed?:break
                assertTrue("Normal $label encounter exceeded budget",SystemClock.elapsedRealtime()<deadline)
                assertTrue("Normal $label defeat; no state repair or forced victory permitted",fight.phase!=BattlePhase.DEFEAT)
                if(boss&&!capturedIce&&presentation.screen==BattlePresentation.Screen.ACTING&&presentation.action?.kind==BattleActionKind.ICE){
                    state("boss-ice-action");capturedIce=true
                }
                if(boss&&!capturedBattleHerb&&presentation.screen==BattlePresentation.Screen.ACTING&&presentation.action?.kind==BattleActionKind.HEAL){
                    val action=presentation.action!!;assertEquals(minOf(fight.hero.maxHp,action.beforeHeroHp+50),action.heroHp)
                    state("boss-herb-action");capturedBattleHerb=true
                }
                if(presentation.screen in listOf(BattlePresentation.Screen.COMMAND,BattlePresentation.Screen.TARGET)){
                    val heal=fight.hero.hp<=fight.hero.maxHp/2||(boss&&bossHerbs==0&&fight.hero.hp<fight.hero.maxHp)
                    if(heal&&v.battleHerbCount()>0){
                        val before=fight.hero;val count=v.battleHerbCount()
                        tap(v,center(v.battleCommandBounds(2)));tap(v,center(v.battleItemBounds(HerbUse.ID)))
                        assertEquals(before,fight.hero);assertEquals(count,v.battleHerbCount())
                        tap(v,center(v.battleItemUseBounds()));assertEquals(count-1,v.battleHerbCount())
                        battleHerbs++;if(boss)bossHerbs++
                    }else if(!boss&&!training)tap(v,center(v.battleCommandBounds(3)))
                    else chooseAttack(v,fight.enemies.first{it.hp>0}.slot)
                }else if(boss&&presentation.screen==BattlePresentation.Screen.RESULT&&committed){
                    state("boss-victory-result");tap(v,center(v.battleResultBounds()))
                }
                SystemClock.sleep(40)
            }
            if(!boss){assertEquals(GameView.Layer.MAP,v.layer);supply()}
        }
        fun step(key:Key){assertEquals(GameView.Layer.MAP,v.layer);stickStep(v,key);finishFight();supply()}
        // Read-only BFS includes the original terrain plane. Every chosen edge is
        // executed through real joystick gestures; no world.tick/restore/teleport.
        fun walkTo(tx:Int,ty:Int){
            val scene=v.world.scene;val start=(v.world.y/16*scene.width+v.world.x/16) to v.world.terrainMode
            val target=ty*scene.width+tx;if(start.first==target)return
            val routeFlags=v.currentSnapshot().flags
            val queue=java.util.ArrayDeque<Pair<Int,Int>>();queue.add(start)
            val parents=mutableMapOf<Pair<Int,Int>,Pair<Pair<Int,Int>,Key>>()
            parents[start]=start to Key.UP;var goal:Pair<Int,Int>?=null
            while(queue.isNotEmpty()&&goal==null){
                val at=queue.removeFirst();val x=at.first%scene.width;val y=at.first/scene.width
                for((key,d)in listOf(Key.UP to (0 to -1),Key.DOWN to (0 to 1),Key.LEFT to (-1 to 0),Key.RIGHT to (1 to 0))){
                    if(scene.probeFrom(x,y,key,at.second)!=MovementBlock.NONE)continue
                    val nx=x+d.first;val ny=y+d.second;val cell=ny*scene.width+nx
                    val next=cell to scene.terrainDecision(x,y,key,at.second).nextMode
                    if(next in parents||(cell!=target&&v.content.exits.any{it.fromMapId==scene.mapId&&it.triggerX==nx&&it.triggerY==ny&&it.edgeDirection==null}))continue
                    if(cell!=target&&v.content.battle?.storyBattles?.values?.any{
                        it.triggersAt(scene.mapId,nx,ny,routeFlags)}==true)continue
                    parents[next]=at to key
                    if(cell==target){goal=next;break};queue.add(next)
                }
            }
            assertNotNull("No original legal $label route map=${scene.mapId} plane=${start.second} to $tx,$ty",goal)
            val keys=mutableListOf<Key>();var cursor=goal!!
            while(cursor!=start){val parent=parents.getValue(cursor);keys.add(parent.second);cursor=parent.first}
            for((index,key)in keys.asReversed().withIndex()){
                assertEquals("Unexpected map before route step",scene.mapId,v.world.mapId);step(key)
                if(v.world.mapId!=scene.mapId)assertEquals("Exit may only occur at the requested goal",keys.lastIndex,index)
                if(v.layer==GameView.Layer.DIALOGUE)assertEquals("Original story may only trigger at requested route endpoint",keys.lastIndex,index)
            }
        }
        fun dialogue(){repeat(24){if(v.layer==GameView.Layer.DIALOGUE)tap(v,Pair(v.width*.5f,v.height*.5f))}}
        fun talk(){tap(v,center(layoutFor(v).buttons.getValue(Key.A)));dialogue()}
        fun enterService(caller:Int,room:Int):MapExit{
            assertEquals(caller,v.world.mapId);val entry=v.content.exits.first{it.fromMapId==caller&&it.toMapId==room}
            if(v.world.x/16==entry.triggerX&&v.world.y/16==entry.triggerY){
                // The actual return lands on the door. Walking to the same cell
                // is zero input; leave it normally before crossing it again.
                val departure=listOf(Key.DOWN,Key.LEFT,Key.RIGHT,Key.UP).first{key->
                    v.world.scene.probeFrom(entry.triggerX,entry.triggerY,key,v.world.terrainMode)==MovementBlock.NONE}
                step(departure);assertEquals(caller,v.world.mapId)
            }
            walkTo(entry.triggerX,entry.triggerY);assertEquals(room,v.world.mapId)
            assertEquals(InteriorContext(caller,entry.triggerX,entry.triggerY),v.currentSnapshot().interiorContext)
            val keeper=v.content.npcs.first{it.mapId==room&&(if(room==22)it.innId!=null else it.shopId!=null)}
            walkTo(keeper.interactionCell!!.first,keeper.interactionCell.second);talk()
            assertEquals(if(room==22)GameView.Layer.INN else GameView.Layer.SHOP,v.layer)
            return entry
        }
        fun leaveService(entry:MapExit){
            if(v.layer==GameView.Layer.SHOP)tap(v,center(v.shopActionBounds(3)))
            if(v.layer==GameView.Layer.INN)instrumentation.runOnMainSync{v.handleBack()}
            val exit=v.content.exits.first{it.fromMapId==entry.toMapId&&it.returnToCaller}
            walkTo(exit.triggerX,exit.triggerY);assertEquals(entry.fromMapId,v.world.mapId)
            assertEquals(entry.triggerX,v.world.x/16);assertEquals(entry.triggerY,v.world.y/16)
            assertNull(v.currentSnapshot().interiorContext)
        }
        fun trade(id:String,buy:Boolean,count:Int=1){
            assertEquals(GameView.Layer.SHOP,v.layer);tap(v,center(v.shopActionBounds(if(buy)1 else 2)))
            scrollToShopItem(v,id);val selected=v.currentSnapshot();tap(v,center(v.shopItemBounds(id)));assertEquals(selected,v.currentSnapshot())
            val item=v.content.itemDefinitions.getValue(id);val price=if(buy)item.buyPrice!! else item.sellPrice!!
            repeat(count){val before=v.currentSnapshot();tap(v,center(v.shopActionBounds(4)))
                assertEquals(before.money+if(buy)-price else price,v.currentSnapshot().money)
                assertEquals((before.inventory[id]?:0)+if(buy)1 else -1,v.currentSnapshot().inventory[id]?:0)
                val after=v.currentSnapshot();send(v,MotionEvent.ACTION_UP,listOf(center(v.shopActionBounds(4))));assertEquals(after,v.currentSnapshot())}
        }
        fun inn(){
            val entry=enterService(0,22);val before=v.currentSnapshot()
            assertTrue("Normal earnings must pay the original inn",before.money>=4)
            tap(v,center(v.innStayBounds()));assertEquals(before.money-4,v.currentSnapshot().money)
            assertEquals(v.currentSnapshot().characters.first().maxHp,v.currentSnapshot().characters.first().hp)
            assertEquals(before.inventory,v.currentSnapshot().inventory);assertEquals(before.flags,v.currentSnapshot().flags)
            leaveService(entry)
        }
        fun replenishTrainingSupplies(){
            // v61's actual normal source had 314 liang, two herbs and no pills.
            // Eight herbs + four pills fit that real budget with original prices;
            // buy through the shop, preserving at least two original inn stays.
            val items=listOf(HerbUse.ID to 8,AntidoteUse.ID to 4)
            val entry=enterService(0,19)
            for((id,target)in items){
                val current=v.currentSnapshot();val price=v.content.itemDefinitions.getValue(id).buyPrice!!
                val count=minOf((target-(current.inventory[id]?:0)).coerceAtLeast(0),
                    ((current.money-8)/price).coerceAtLeast(0))
                if(count>0)trade(id,true,count)
            }
            state("normal-training-supplies-purchased");leaveService(entry)
            assertTrue("Normal earnings must fund training herbs; no inventory grants",
                (v.currentSnapshot().inventory[HerbUse.ID]?:0)>0)
        }
        fun enterTrainingArea(){
            assertEquals(0,v.world.mapId);walkTo(0,14);step(Key.LEFT);walkTo(199,130)
            assertEquals(25,v.world.mapId)
            // The actual shortest entrance-to-entrance path is entirely in zone1.
            // Do not invent intermediate waypoints: 29,43 is an original wall.
            walkTo(5,24);assertEquals(96,v.world.mapId)
            walkTo(15,24)
            assertTrue(v.content.battle!!.zones.any{it.mapId==96&&it.contains(96,15,24)})
        }
        fun returnFromTraining(){
            assertEquals(96,v.world.mapId);walkTo(15,29);assertEquals(25,v.world.mapId)
            walkTo(39,42);assertEquals(16,v.world.mapId)
            walkTo(202,130);assertEquals(0,v.world.mapId)
        }
        state("verified-normal-source-loaded")
        walkTo(15,29);assertEquals(25,v.world.mapId);walkTo(39,42);assertEquals(16,v.world.mapId)
        walkTo(202,130);assertEquals(0,v.world.mapId)
        fun supplyCost():Int {
            val bag=v.currentSnapshot().inventory
            return (10-(bag[HerbUse.ID]?:0)).coerceAtLeast(0)*15+
                (10-(bag[AntidoteUse.ID]?:0)).coerceAtLeast(0)*20
        }
        // A normal player's preparation, not a new game gate. Actual original
        // fights earn every level/coin; original 4-liang inn restores HP. Level 12
        // is this recording's chosen safety margin, not a North access condition.
        if(v.currentSnapshot().characters.first().level<12||v.currentSnapshot().money<supplyCost()+8){
            training=true;state("normal-training-start")
            // Actual West palace zone3 enemies 8/9 have behavior0 (no poison),
            // 11/13 EXP. This normal player's route preserves all enemy rules.
            replenishTrainingSupplies();inn();enterTrainingArea()
            var trainingSteps=0
            while(v.currentSnapshot().characters.first().level<12||v.currentSnapshot().money<supplyCost()+8){
                assertTrue("Bounded normal preparation exhausted at map=${v.world.mapId} cell=${v.world.x/16},${v.world.y/16} level=${v.currentSnapshot().characters.first().level} EXP=${v.currentSnapshot().characters.first().experience}; no resource grants",trainingSteps++<5000)
                if(trainingSteps%64==0)state("normal-training-progress-$trainingSteps")
                if(v.currentSnapshot().characters.first().hp<=v.currentSnapshot().characters.first().maxHp*3/4||
                    (v.currentSnapshot().inventory[HerbUse.ID]?:0)<=3){
                    returnFromTraining();replenishTrainingSupplies();inn();enterTrainingArea()
                }
                var trainingDirection:Key?=null
                instrumentation.runOnMainSync{
                    val x=v.world.x/16;val y=v.world.y/16
                    val preferred=if(y>23)Key.UP else Key.DOWN
                    trainingDirection=(listOf(preferred)+listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT).filter{it!=preferred}).firstOrNull{key->
                        val nx=x+if(key==Key.RIGHT)1 else if(key==Key.LEFT)-1 else 0
                        val ny=y+if(key==Key.DOWN)1 else if(key==Key.UP)-1 else 0
                        v.world.scene.probeFrom(x,y,key,v.world.terrainMode)==MovementBlock.NONE&&
                            v.content.battle!!.zones.any{it.mapId==96&&it.contains(v.world.mapId,nx,ny)}&&
                            v.content.exits.none{it.fromMapId==v.world.mapId&&it.triggerX==nx&&it.triggerY==ny}
                    }
                }
                assertNotNull("No legal normal training step; no collision bypass",trainingDirection)
                step(trainingDirection!!)
            }
            returnFromTraining();training=false;state("normal-training-complete")
        }
        val store=enterService(0,19)
        val pills=(10-(v.currentSnapshot().inventory[AntidoteUse.ID]?:0)).coerceAtLeast(0)
        val herbs=(10-(v.currentSnapshot().inventory[HerbUse.ID]?:0)).coerceAtLeast(0)
        assertTrue(v.currentSnapshot().money>=pills*20+herbs*15+4)
        if(pills>0)trade(AntidoteUse.ID,true,pills)
        if(herbs>0)trade(HerbUse.ID,true,herbs)
        state("normal-supply-purchased");leaveService(store);inn()
        walkTo(0,14);step(Key.LEFT);walkTo(199,130);assertEquals(25,v.world.mapId)
        state("north-sea-route-start")
        walkTo(29,3);assertEquals(98,v.world.mapId)
        assertEquals(7,v.world.x/16);assertEquals(29,v.world.y/16);state("north-palace-entry")
        walkTo(5,6);assertEquals(139,v.world.mapId)
        assertEquals(11,v.world.x/16);assertEquals(25,v.world.y/16);state("north-inner-palace-entry")
        // Stop one tile before the actual automatic trigger to prepare normally;
        // no A press or flags manufacture the story at (2,4).
        walkTo(2,5);assertEquals(GameView.Layer.MAP,v.layer)
        while(v.currentSnapshot().characters.first().hp<v.currentSnapshot().characters.first().maxHp)medicine(HerbUse.ID)
        val beforeBoss=v.currentSnapshot();val northFlag="rom.map.139.flag.128"
        assertTrue(beforeBoss.flags[northFlag]!=true);assertEquals(0,beforeBoss.inventory[WorldItems.ID]?:0)
        walkTo(2,4);assertEquals(GameView.Layer.DIALOGUE,v.layer);state("automatic-chest-guard-dialogue")
        assertEquals(beforeBoss.money,v.currentSnapshot().money)
        assertEquals(beforeBoss.inventory,v.currentSnapshot().inventory)
        dialogue();assertEquals(GameView.Layer.BATTLE,v.layer);finishFight()
        assertEquals(GameView.Layer.DIALOGUE,v.layer)
        val won=v.currentSnapshot();assertEquals(beforeBoss.money+200,won.money)
        assertEquals(beforeBoss.characters.first().experience+110,won.characters.first().experience)
        assertTrue("North flag commits only after victory dialogue",won.flags[northFlag]!=true)
        assertEquals(true,won.flags[northFlag+".dialogue.pending"])
        assertEquals(0,won.inventory[WorldItems.ID]?:0);assertTrue(won.flags["rom.map.139.flag.2"]!=true)
        assertTrue("Bought herbs must actually run in this normal North battle",bossHerbs>0)
        assertTrue(capturedBattleHerb)
        assertEquals((beforeBoss.inventory[HerbUse.ID]?:0)-bossHerbs,won.inventory[HerbUse.ID]?:0)
        // Original optional medicine2 drop is not replaced with a fixed inventory expectation.
        state("victory-dialogue-before-flag");dialogue();assertEquals(GameView.Layer.MAP,v.layer)
        assertEquals(true,v.currentSnapshot().flags[northFlag])
        assertTrue(v.currentSnapshot().flags[northFlag+".dialogue.pending"]!=true)
        assertEquals(won.inventory,v.currentSnapshot().inventory)
        assertEquals(won.money,v.currentSnapshot().money);state("victory-confirmed-no-automatic-pearl")
        val beforeChest=v.currentSnapshot()
        tap(v,center(layoutFor(v).buttons.getValue(Key.A)));assertEquals(GameView.Layer.MAP,v.layer)
        val claimed=v.currentSnapshot();assertEquals(1,claimed.inventory[WorldItems.ID])
        assertEquals(true,claimed.flags["rom.map.139.flag.2"])
        assertEquals(beforeChest.characters,claimed.characters);assertEquals(beforeChest.money,claimed.money)
        assertEquals(beforeChest.inventory+(WorldItems.ID to 1),claimed.inventory)
        assertEquals(beforeChest.flags+("rom.map.139.flag.2" to true),claimed.flags)
        state("pearl-investigated-and-claimed")
        tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
        assertEquals(claimed,v.currentSnapshot());state("repeat-chest-no-grant")
        // A legal map herb can be needed after the real boss. Separate that
        // explicit normal supply from the no-second-reward comparison.
        supply();val beforeReentry=v.currentSnapshot();state("optional-normal-supply-before-trigger-reentry")
        // These two original interior moves must not also call the optional
        // field supply helper; otherwise low HP can legally consume a second herb.
        stickStep(v,Key.DOWN);stickStep(v,Key.UP);assertEquals(GameView.Layer.MAP,v.layer)
        assertEquals(139,v.world.mapId);assertEquals(2 to 4,v.world.x/16 to v.world.y/16)
        assertEquals(beforeReentry.characters,v.currentSnapshot().characters)
        assertEquals(beforeReentry.inventory,v.currentSnapshot().inventory);assertEquals(beforeReentry.money,v.currentSnapshot().money)
        assertEquals(beforeReentry.flags,v.currentSnapshot().flags);state("trigger-reentered-no-battle-or-reward")
        walkTo(11,25);assertEquals(98,v.world.mapId);walkTo(7,29);assertEquals(25,v.world.mapId)
        state("north-palace-return")
        walkTo(45,40);step(Key.RIGHT);assertEquals(25,v.world.mapId);supply()
        assertEquals(46,v.world.x/16);assertEquals(40,v.world.y/16);assertEquals(Key.RIGHT,v.world.direction)
        // Arrive facing the object by a completed rightward move. Existing wall
        // input does not turn the actor, and stickStep rejects blocked movement.
        // This real blocked gesture proves the object remains a barrier before use.
        val stick=layoutFor(v).stick;val middle=center(stick)
        val right=Pair(stick.x+stick.w-2f,middle.second)
        val beforeFacing=v.currentSnapshot()
        send(v,MotionEvent.ACTION_DOWN,listOf(middle));send(v,MotionEvent.ACTION_MOVE,listOf(right))
        SystemClock.sleep(100);send(v,MotionEvent.ACTION_UP,listOf(right))
        assertEquals(beforeFacing,v.currentSnapshot())
        state("whirlpool-blocks-before-pearl")
        tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));scrollToItem(v,WorldItems.ID)
        val beforeUse=v.currentSnapshot();tap(v,center(v.panelItemBounds(WorldItems.ID)))
        assertEquals(beforeUse,v.currentSnapshot());state("pearl-selected-not-consumed")
        tap(v,center(v.panelPrimaryBounds()));val used=v.currentSnapshot()
        assertEquals(beforeUse.inventory,used.inventory);assertEquals(1,used.inventory[WorldItems.ID])
        assertEquals(beforeUse.characters,used.characters);assertEquals(beforeUse.money,used.money)
        val expectedFlags=beforeUse.flags+mapOf("rom.inventory.special.11.used" to true,
            "rom.map.25.flag.1" to true,"rom.map.25.flag.128" to true)
        assertEquals(expectedFlags,used.flags);state("pearl-used-whirlpool-removed")
        send(v,MotionEvent.ACTION_UP,listOf(center(v.panelPrimaryBounds())));assertEquals(used,v.currentSnapshot())
        instrumentation.runOnMainSync{v.handleBack()};assertEquals(GameView.Layer.MAP,v.layer)
        step(Key.RIGHT);assertEquals(47,v.world.x/16);assertEquals(40,v.world.y/16)
        step(Key.RIGHT);assertEquals(48,v.world.x/16);assertEquals(40,v.world.y/16)
        assertEquals(true,v.currentSnapshot().flags[northFlag]);assertEquals(true,v.currentSnapshot().flags["rom.event.97.39.1"])
        assertEquals(1,v.currentSnapshot().inventory[WorldItems.ID]);state("normal-whirlpool-crossed")
        assertEquals("Source checkpoint remains byte-exact",sourceHash,
            java.security.MessageDigest.getInstance("SHA-256").digest(sourceFile.readBytes()).joinToString(""){"%02x".format(it)})
        instrumentation.runOnMainSync{v.persistState()}
        File(root,"world-north-palace-expected-save.json").writeText(v.currentSnapshot().json().toString())
        state("persisted-for-external-cold-restart",false);instrumentation.runOnMainSync{activity.finish()}
    }
    fun testWorldNorthPalacePearlColdStartMatchesNormalSave(){
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"world-north-palace-expected-save.json")
        assertTrue(file.exists());val expected=SaveSnapshot.parse(file.readText());val(activity,v)=launch()
        assertEquals("External recorder must force-stop before this fresh launch",expected,v.currentSnapshot())
        assertEquals(25,v.world.mapId);assertEquals(48,v.world.x/16);assertEquals(40,v.world.y/16)
        for(flag in listOf("rom.event.97.39.1","rom.map.139.flag.128","rom.map.139.flag.2",
            "rom.inventory.special.11.used","rom.map.25.flag.1","rom.map.25.flag.128"))assertEquals(true,expected.flags[flag])
        assertTrue(expected.flags["rom.map.139.flag.128.dialogue.pending"]!=true)
        assertEquals(1,expected.inventory[WorldItems.ID]);screenshot(v,"world-north-palace-cold-flags-items-restored")
        assertEquals(MovementBlock.NONE,v.world.scene.probeFrom(48,40,Key.LEFT,v.world.terrainMode))
        tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));scrollToItem(v,WorldItems.ID)
        tap(v,center(v.panelItemBounds(WorldItems.ID)));assertEquals(expected,v.currentSnapshot())
        screenshot(v,"world-north-palace-cold-pearl-detail")
        instrumentation.runOnMainSync{v.handleBack()};assertEquals(GameView.Layer.MAP,v.layer)
        assertEquals(expected,v.currentSnapshot())
        stickStep(v,Key.LEFT);assertEquals(47,v.world.x/16);assertEquals(40,v.world.y/16)
        val battleField=GameView::class.java.getDeclaredField("battle").apply{isAccessible=true}
        val presentationField=GameView::class.java.getDeclaredField("battlePresentation").apply{isAccessible=true}
        val deadline=SystemClock.elapsedRealtime()+180000
        while(true){
            var observed:Pair<OpeningBattle,BattlePresentation>?=null
            instrumentation.runOnMainSync{if(v.layer==GameView.Layer.BATTLE)observed=
                (battleField.get(v) as OpeningBattle) to (presentationField.get(v) as BattlePresentation)}
            val(fight,presentation)=observed?:break
            assertTrue("Cold crossing encounter must resolve normally",SystemClock.elapsedRealtime()<deadline)
            assertTrue("Cold crossing may not repair a defeat",fight.phase!=BattlePhase.DEFEAT)
            if(presentation.screen in listOf(BattlePresentation.Screen.COMMAND,BattlePresentation.Screen.TARGET)){
                if(fight.hero.hp<=fight.hero.maxHp/2&&v.battleHerbCount()>0){
                    tap(v,center(v.battleCommandBounds(2)));tap(v,center(v.battleItemBounds(HerbUse.ID)))
                    tap(v,center(v.battleItemUseBounds()))
                }else tap(v,center(v.battleCommandBounds(3)))
            }
            SystemClock.sleep(40)
        }
        assertEquals(GameView.Layer.MAP,v.layer);assertEquals(25,v.world.mapId)
        assertEquals(47,v.world.x/16);assertEquals(40,v.world.y/16)
        assertEquals(expected.flags,v.currentSnapshot().flags);assertEquals(1,v.currentSnapshot().inventory[WorldItems.ID])
        screenshot(v,"world-north-palace-cold-whirlpool-still-passable")
        instrumentation.runOnMainSync{v.persistState();activity.finish()}
    }

    /** Same-candidate normal North checkpoint continuation; no state grants or teleports. */
    fun testNormalWorldCave85FromVerifiedNorthPalaceSave(){normalWorldStoryContinuation(false,false)}
    fun testWorldCave85ColdStartAndReentryMatchesNormalSave(){normalWorldStoryContinuation(true,false)}
    fun testNormalWorldEastPalacePartyFromVerifiedCaveSave(){normalWorldStoryContinuation(false,true)}
    fun testWorldEastPartyColdStartMatchesNormalSave(){normalWorldStoryContinuation(true,true)}
    fun testNormalWorldHellVillageServicesFromVerifiedEastPartySave(){normalWorldStoryContinuation(false,true,true)}
    fun testWorldHellVillageColdStartMatchesNormalSave(){normalWorldStoryContinuation(true,true,true)}
    /** Exact failed-run East normal/cold source; no HP, inventory or flag repair. */
    fun testControlledR1VillageTwoPoisonSupplyAndInnFromVerifiedSave(){
        prepareR1VillageTwoVerifiedSource()
        normalWorldStoryContinuation(false,true,true,fixtureLabel="controlled-r1-village2")
        normalWorldStoryContinuation(true,true,true,fixtureLabel="controlled-r1-village2")
    }
    private fun prepareR1VillageTwoVerifiedSource(){
        val fixture=JSONObject(instrumentation.context.assets.open("r1-village2-verified-east.json").bufferedReader().use{it.readText()})
        assertEquals("CONTROLLED_REPLAY_OF_VERIFIED_NORMAL_SAVE",fixture.getString("kind"))
        assertTrue(fixture.getBoolean("sourceNormalAndExternalColdVerified"))
        val bytes=fixture.getString("savedJson").toByteArray(Charsets.UTF_8)
        assertEquals(fixture.getString("sourceSaveSha256"),java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString(""){"%02x".format(it)})
        val source=SaveSnapshot.parse(bytes.toString(Charsets.UTF_8))
        assertEquals(23,source.mapId)
        assertEquals(OriginalStatus.POISON,source.characters.single{it.id=="xiaolongnv"}.statusMask)
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"world-controlled-r1-village2-source.json")
        file.writeBytes(bytes)
    }
    fun testPersonalR1SmokeFromVerifiedEastSave(){
        prepareR1VillageTwoVerifiedSource()
        normalWorldStoryContinuation(false,true,true,fixtureLabel="controlled-r1-village2")
    }
    /** Personal smoke only: same sourced fixture, no mainline-completion claim. */
    fun testPersonalR1SmokeColdRestartMatchesVerifiedSave(){
        normalWorldStoryContinuation(true,true,true,fixtureLabel="controlled-r1-village2")
    }
    /** Only an explicitly sourced R1 replay may migrate this one metadata field.
     * Normal same-candidate continuations and cold starts keep exact equality. */
    private fun expectedReplaySnapshot(source:SaveSnapshot,v:GameView,controlled:Boolean,cold:Boolean):SaveSnapshot {
        assertTrue("Replay source must pass current scene/actor/state validation",source.validate(v.content))
        if(source.contentVersion==v.content.scene.version)return source
        assertTrue("Only first controlled replay may migrate a version marker",controlled&&!cold)
        assertEquals("opening-segment-001-c51-r1",source.contentVersion)
        assertTrue("Only explicitly admitted personal targets may replay old verified bytes",
            v.content.scene.version in listOf("opening-segment-001-c60","opening-segment-001-c61","opening-segment-001-c62"))
        return source.copy(contentVersion=v.content.scene.version)
    }
    private fun replayLoadMetadata(index:JSONObject,source:SaveSnapshot,currentVersion:String):JSONObject = index
        .put("stateChangesAtLoad",source.contentVersion!=currentVersion)
        .put("contentVersionOnlyMigrated",source.contentVersion!=currentVersion)
        .put("gameplayStateChangesAtLoad",false)
        .put("expectedLoadedSnapshot",source.copy(contentVersion=currentVersion).json())
    private fun assertStoryReplaySource(source:SaveSnapshot,cold:Boolean,east:Boolean){
        assertEquals(true,source.flags["rom.event.97.39.1"])
        for(flag in listOf("rom.map.139.flag.128","rom.map.139.flag.2","rom.inventory.special.11.used",
            "rom.map.25.flag.1","rom.map.25.flag.128"))assertEquals(true,source.flags[flag])
        val caveFlag="rom.map.85.flag.128"
        assertEquals(cold||east,source.flags[caveFlag]==true)
        assertTrue(source.flags[caveFlag+".dialogue.pending"]!=true)
        assertEquals(1,source.inventory[WorldItems.ID])
    }
    fun testControlledR1ReplayVersionMarkerBounds(){
        val fixture=JSONObject(instrumentation.context.assets.open("r1-medical-verified-village.json").bufferedReader().use{it.readText()})
        val bytes=fixture.getString("savedJson").toByteArray(Charsets.UTF_8)
        assertEquals(fixture.getString("sourceSaveSha256"),java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString(""){"%02x".format(it)})
        val source=SaveSnapshot.parse(bytes.toString(Charsets.UTF_8));val(activity,v)=launch()
        val expected=source.copy(contentVersion=v.content.scene.version)
        assertEquals(expected,expectedReplaySnapshot(source,v,true,false))
        assertNotNull(runCatching{expectedReplaySnapshot(source,v,false,false)}.exceptionOrNull())
        assertNotNull(runCatching{expectedReplaySnapshot(source,v,true,true)}.exceptionOrNull())
        assertNotNull(runCatching{expectedReplaySnapshot(source.copy(contentVersion="opening-segment-001-c50"),v,true,false)}.exceptionOrNull())
        assertEquals(expected,expectedReplaySnapshot(expected,v,false,true))
        assertEquals(source,SaveSnapshot.parse(bytes.toString(Charsets.UTF_8)))
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testNormalPlayableR1MedicalFromVerifiedVillageSave(){normalPlayableR1Medical(false)}
    fun testPlayableR1MedicalColdStartMatchesNormalSave(){normalPlayableR1Medical(true)}

    /** Isolated replay of exact v73 normal/cold-verified bytes, never current mainline proof. */
    fun testControlledPlayableR1MedicalDoorReentryFromVerifiedSave(){
        val fixture=JSONObject(instrumentation.context.assets.open("r1-medical-verified-village.json").bufferedReader().use{it.readText()})
        assertEquals("CONTROLLED_REPLAY_OF_VERIFIED_NORMAL_SAVE",fixture.getString("kind"))
        val bytes=fixture.getString("savedJson").toByteArray(Charsets.UTF_8)
        assertEquals(fixture.getString("sourceSaveSha256"),java.security.MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString(""){"%02x".format(it)})
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"world-controlled-r1-medical-source.json")
        file.writeBytes(bytes)
        normalPlayableR1Medical(false,"controlled-r1-medical",file.name,fixture.getString("kind"))
        // Activity restart of the actual saved result; the final normal recorder
        // still separately requires an external force-stop/cold-start boundary.
        normalPlayableR1Medical(true,"controlled-r1-medical",file.name,fixture.getString("kind"))
    }

    /** Exact same-candidate village checkpoint, original door and normal touch only. */
    private fun normalPlayableR1Medical(cold:Boolean,label:String="r1-medical",
        normalSourceName:String="world-hell-village2-expected-save.json",kind:String="CONTINUATION_FROM_VERIFIED_SAVE"){
        val root=instrumentation.targetContext.getExternalFilesDir(null)
        val file=File(root,if(cold)"world-$label-expected-save.json"else normalSourceName)
        assertTrue(file.exists());val bytes=file.readBytes();val source=SaveSnapshot.parse(bytes.toString(Charsets.UTF_8))
        val hash=java.security.MessageDigest.getInstance("SHA-256").digest(bytes).joinToString(""){"%02x".format(it)}
        val(activity,v)=launch()
        val expected=expectedReplaySnapshot(source,v,kind=="CONTROLLED_REPLAY_OF_VERIFIED_NORMAL_SAVE",cold)
        assertEquals(2,source.mapId);assertEquals(listOf("nezha","xiaolongnv"),source.characters.map{it.id})
        assertEquals(true,source.flags["rom.map.95.flag.128"])
        if(!cold)instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(source))}
        assertEquals(expected,v.currentSnapshot())
        val events=org.json.JSONArray();val started=SystemClock.elapsedRealtime()
        fun state(name:String){
            val snapshot=v.currentSnapshot()
            events.put(org.json.JSONObject().put("name",name).put("androidUptimeMs",SystemClock.uptimeMillis()).put("elapsedMs",SystemClock.elapsedRealtime()-started)
                .put("snapshot",snapshot.json()))
            File(root,"world-$label-${if(cold)"cold" else "normal"}-index.json").writeText(replayLoadMetadata(org.json.JSONObject()
                .put("kind",if(kind=="CONTROLLED_REPLAY_OF_VERIFIED_NORMAL_SAVE")
                    if(cold)"CONTROLLED_ACTIVITY_RESTART_OF_VERIFIED_SAVE"else kind
                    else if(cold)"EXTERNAL_COLD_RESTART_AND_NORMAL_REENTRY"else kind)
                .put("sourceFile",file.name).put("sourceSha256",hash).put("sourceSnapshot",source.json()),source,expected.contentVersion)
                .put("normalInputsOnly",true).put("events",events).toString())
            screenshot(v,"world-$label-${if(cold)"cold-" else ""}$name")
        }
        fun walk(tx:Int,ty:Int){
            val map=v.world.mapId;var tries=0
            while(v.world.mapId==map&&(v.world.x/16!=tx||v.world.y/16!=ty)){
                assertTrue("R1 original medical path must converge without position edits",tries++<512)
                val scene=v.world.scene;val start=v.world.y/16*scene.width+v.world.x/16;val goal=ty*scene.width+tx
                val queue=java.util.ArrayDeque<Int>();queue.add(start)
                val parents=mutableMapOf<Int,Pair<Int,Key>>();parents[start]=start to Key.UP
                while(queue.isNotEmpty()&&goal !in parents){
                    val at=queue.removeFirst();val x=at%scene.width;val y=at/scene.width
                    for((key,d)in listOf(Key.UP to (0 to -1),Key.DOWN to (0 to 1),Key.LEFT to (-1 to 0),Key.RIGHT to (1 to 0))){
                        if(scene.probeFrom(x,y,key)!=MovementBlock.NONE)continue
                        val nx=x+d.first;val ny=y+d.second;val next=ny*scene.width+nx
                        if(next in parents||(next!=goal&&v.content.exits.any{it.fromMapId==map&&it.triggerX==nx&&it.triggerY==ny&&it.edgeDirection==null}))continue
                        parents[next]=at to key;queue.add(next)
                    }
                }
                assertTrue("Original R1 medical target not reachable: $map $tx,$ty",goal in parents)
                var next=goal
                while(parents.getValue(next).first!=start)next=parents.getValue(next).first
                stickStep(v,parents.getValue(next).second)
                assertEquals("No normal field battle exists in this village/medical room",GameView.Layer.MAP,v.layer)
            }
        }
        state(if(cold)"cold-exact-normal-state"else"verified-village-source")
        for((role,id)in listOf("revival" to "rom.npc.20.1","care" to "rom.npc.20.0")){
            // Original shared-room return lands on the entrance trigger. Merely
            // asking walk() for that same cell emits no input and cannot reenter.
            // Move to a real adjacent legal cell, then walk back through the door.
            if(v.world.mapId==2&&v.world.x/16==22&&v.world.y/16==26){
                val departure=listOf(Key.DOWN,Key.LEFT,Key.RIGHT,Key.UP).firstOrNull{key->
                    val next=when(key){Key.DOWN->22 to 27;Key.LEFT->21 to 26;Key.RIGHT->23 to 26;else->22 to 25}
                    v.world.scene.probeFrom(22,26,key,v.world.terrainMode)==MovementBlock.NONE&&
                        v.content.exits.none{it.fromMapId==2&&it.triggerX==next.first&&it.triggerY==next.second}
                }
                assertNotNull("Original medical door requires a legal step away before reentry",departure)
                stickStep(v,departure!!);assertEquals(2,v.world.mapId)
                assertFalse(v.world.x/16==22&&v.world.y/16==26)
                state("$role-normal-step-away-before-reentry")
            }
            walk(22,26);assertEquals(20,v.world.mapId)
            assertEquals(InteriorContext(2,22,26),v.currentSnapshot().interiorContext)
            val npc=v.content.npcs.single{it.id==id};val cell=npc.interactionCell!!
            walk(cell.first,cell.second)
            tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
            assertEquals(GameView.Layer.INN,v.layer);assertEquals("rom.clinic.2.$role",v.activeClinicId)
            state("$role-real-entry-service")
            val before=v.currentSnapshot()
            tap(v,center(v.clinicTargetBounds("xiaolongnv")))
            assertEquals(before,v.currentSnapshot());state("$role-target-selected-no-side-effect")
            instrumentation.runOnMainSync{v.handleBack()}
            assertEquals(before,v.currentSnapshot());assertEquals(GameView.Layer.MAP,v.layer)
            walk(7,12);assertEquals(2,v.world.mapId)
            assertEquals(22 to 26,v.world.x/16 to v.world.y/16);assertNull(v.currentSnapshot().interiorContext)
            state("$role-original-return-no-charge")
        }
        assertEquals(source.money,v.currentSnapshot().money)
        assertEquals(source.inventory,v.currentSnapshot().inventory)
        assertEquals(source.flags,v.currentSnapshot().flags)
        assertEquals(source.characters.map{it.id},v.currentSnapshot().characters.map{it.id})
        assertTrue(file.readBytes().contentEquals(bytes))
        instrumentation.runOnMainSync{v.persistState()}
        assertEquals(v.currentSnapshot(),SaveSnapshot.parse(instrumentation.targetContext
            .getSharedPreferences("opening-local-save",0).getString("saveJson",null)!!))
        if(!cold)File(root,"world-$label-expected-save.json").writeText(v.currentSnapshot().json().toString())
        state(if(cold)"cold-medical-reentry-and-continue"else"normal-medical-doors-and-cancel-saved")
        instrumentation.runOnMainSync{activity.finish()}
    }
    // Both routes share the same real touch/service/BFS driver. Only their
    // verified source checkpoints and scenario assertions differ.
    fun testNormalWorldFirstHallFromVerifiedHellVillageSave(){normalWorldStoryContinuation(false,true,false,true)}
    fun testNormalWorldFirstHallFromVerifiedMedicalSave(){normalWorldStoryContinuation(false,true,firstHall=true,normalSourceName="world-r1-medical-expected-save.json")}
    fun testWorldFirstHallColdRestartAndRepeatNoReward(){normalWorldStoryContinuation(true,true,false,true)}
    fun testNormalWorldSecondHallFromVerifiedFirstHallSave(){normalWorldStoryContinuation(false,true,false,false,true)}
    fun testWorldSecondHallColdRestartAndRepeatNoReward(){normalWorldStoryContinuation(true,true,false,false,true)}
    fun testNormalWorldHallBatchFromVerifiedSecondHallSave(){normalWorldStoryContinuation(false,true,hallBatch=true)}
    fun testWorldHallBatchColdRestartAndRepeatNoReward(){normalWorldStoryContinuation(true,true,hallBatch=true)}
    fun testNormalWorldFinalHallsAndRebirthFromVerifiedHallBatchSave(){normalWorldStoryContinuation(false,true,rebirth=true)}
    fun testWorldRebirthColdRestartAndContinueMatchesNormalSave(){normalWorldStoryContinuation(true,true,rebirth=true)}
    fun testNormalWorldVillageThreeServicesFromVerifiedRebirthSave(){normalWorldStoryContinuation(false,true,village3=true)}
    fun testWorldVillageThreeColdRestartAndRealReentry(){normalWorldStoryContinuation(true,true,village3=true)}
    fun testNormalMedicalServicesFromVerifiedVillageThreeSave(){normalWorldStoryContinuation(false,true,medical=true)}
    fun testMedicalServicesColdRestartAndReentry(){normalWorldStoryContinuation(true,true,medical=true)}
    fun testNormalContinentBridgeAndZone16FromVerifiedMedicalSave(){normalWorldStoryContinuation(false,true,continentBridge=true)}
    fun testContinentBridgeColdRestartAndRealReturn(){normalWorldStoryContinuation(true,true,continentBridge=true)}
    fun testNormalForest101FromVerifiedContinentBridgeSave(){normalWorldStoryContinuation(false,true,forest101=true)}
    fun testForest101ColdRestartAndRealContinentReturn(){normalWorldStoryContinuation(true,true,forest101=true)}
    fun testNormalTree107FromVerifiedContinentBridgeSave(){normalWorldStoryContinuation(false,true,tree107=true)}
    fun testTree107ColdRestartAndRealReturn(){normalWorldStoryContinuation(true,true,tree107=true)}
    fun testNormalRoom171GiftFromVerifiedTreeSave(){normalWorldStoryContinuation(false,true,room171=true)}
    fun testRoom171GiftColdRestartAndOriginalReturn(){normalWorldStoryContinuation(true,true,room171=true)}
    fun testNormalYangJoinAndThreePartyFromVerifiedRoomSave(){normalWorldStoryContinuation(false,true,yangJoin=true)}
    fun testYangJoinColdRestartAndOriginalTreeReturn(){normalWorldStoryContinuation(true,true,yangJoin=true)}
    fun testNormalVillageFourServicesAndTalkFromVerifiedYangSave(){normalWorldStoryContinuation(false,true,village4=true)}
    fun testVillageFourColdRestartAndOriginalReturn(){normalWorldStoryContinuation(true,true,village4=true)}
    fun testNormalFixedFerryAndIslandFromVerifiedVillageSave(){normalWorldStoryContinuation(false,true,ferry=true)}
    fun testFixedFerryIslandColdRestartAndOriginalReverse(){normalWorldStoryContinuation(true,true,ferry=true)}
    fun testNormalIslandLayersFourVillainsAndChestsFromVerifiedFerrySave(){normalWorldStoryContinuation(false,true,island=true)}
    fun testIslandVictoryColdRestartChestsAndRealReturn(){normalWorldStoryContinuation(true,true,island=true)}
    fun testNormalVillageFiveServicesAndTalkFromVerifiedYangSave(){normalWorldStoryContinuation(false,true,village5=true)}
    fun testVillageFiveColdRestartAndOriginalReturn(){normalWorldStoryContinuation(true,true,village5=true)}
    fun testNormalCave87FlowerStoryFromVerifiedIslandSave(){normalWorldStoryContinuation(false,true,cave87=true)}
    fun testCave87DepartureColdRestartAndOriginalReturn(){normalWorldStoryContinuation(true,true,cave87=true)}
    fun testNormalVillageSixServicesAndTalkFromVerifiedFlowerSave(){normalWorldStoryContinuation(false,true,village6=true)}
    fun testVillageSixColdRestartAndOriginalReturn(){normalWorldStoryContinuation(true,true,village6=true)}
    /** Preserve the prior village4/5 UI paths while keeping JVM method size bounded. */
    private fun normalWestVillageServicesStage(v:GameView,cold:Boolean,source:SaveSnapshot,village5:Boolean,
        walk:(Int,Int)->Unit,step:(Key)->Unit,state:(String,Boolean)->Unit,
        enter:(Int,Int,String?)->MapExit,leave:(MapExit)->Unit,trade:(String,Boolean,Int)->Unit,
        treeWalk:(Triple<Int,Int,Int>)->Unit,dialogue:()->Unit,checkSourceUnchanged:()->Unit,persist:()->Unit){
        val root=instrumentation.targetContext.getExternalFilesDir(null)
            val caller=if(village5)5 else 4
            val label=if(village5)"village5"else"village4"
            val worldDoor=if(village5)107 to 157 else 146 to 150
            val stock=if(village5)listOf(17 to "rom.weapon.8",18 to "rom.armor.13",19 to "rom.medicine.1")else listOf(17 to "rom.weapon.7",18 to "rom.armor.4",19 to HerbUse.ID)
            assertEquals(listOf("nezha","xiaolongnv","yangjian"),source.characters.map{it.id})
            assertEquals(true,source.flags["rom.map.110.flag.128"])
            state(if(cold)"cold-exact-normal-village-save"else"verified-yang-three-party-source-no-grants",true)
            if(!cold){
                assertEquals(110,v.world.mapId);treeWalk(Triple(107,7,13));step(Key.DOWN)
                assertEquals(16,v.world.mapId);assertEquals(169 to 149,v.world.x/16 to v.world.y/16)
                walk(worldDoor.first,worldDoor.second);assertEquals(caller,v.world.mapId);assertEquals(15 to 29,v.world.x/16 to v.world.y/16)
                state("normal-original-west-village-entry-no-optional-talk-lock",true)
                for((room,id)in stock){
                    val entry=enter(caller,room,null);state("normal-original-shop-$room-open",true)
                    trade(id,true,1);state("normal-original-shop-$room-bought",true)
                    trade(id,false,1);state("normal-original-shop-$room-sold",true);leave(entry)
                }
                // Actual 90-tael command, same three-owner business result.
                val innEntry=enter(caller,22,null);val beforeRest=v.currentSnapshot()
                val expectedRest=InnStay.apply(beforeRest.money,beforeRest.characters,v.content.inns.getValue(v.activeInnId!!))
                assertNull("Only normal earned money may pay the actual inn",expectedRest.error)
                tap(v,center(v.innStayBounds()));assertEquals(expectedRest.money,v.currentSnapshot().money)
                assertEquals(expectedRest.characters,v.currentSnapshot().characters)
                assertEquals(beforeRest.inventory,v.currentSnapshot().inventory);assertEquals(beforeRest.flags,v.currentSnapshot().flags)
                state("normal-original-paid-inn-three-owner-result",true);leave(innEntry)
                for(id in listOf("rom.npc.20.0","rom.npc.20.1")){
                    val entry=enter(caller,20,id);val before=v.currentSnapshot()
                    state("normal-original-clinic-${id.substringAfterLast('.')}-view",true)
                    instrumentation.runOnMainSync{v.handleBack()};assertEquals(before,v.currentSnapshot());leave(entry)
                }
                for(index in if(village5)(0..4).toList()else (1..8).toList()){
                    val npc=v.content.npcs.single{it.id=="rom.npc.$caller.$index"}
                    val candidates=listOf(0 to 1,1 to 0,-1 to 0,0 to -1).map{npc.x+it.first to npc.y+it.second}
                    // An individually legal tile can be on the other bridge bank.
                    // Read-only component search follows actual directional edges and
                    // avoids accidentally entering a shared room on the way to talk.
                    val scene=v.world.scene;val reachable=mutableSetOf(v.world.x/16 to v.world.y/16)
                    val queue=java.util.ArrayDeque<Pair<Int,Int>>();queue.add(reachable.single())
                    while(queue.isNotEmpty()){
                        val (x,y)=queue.removeFirst()
                        for((key,d)in listOf(Key.UP to(0 to -1),Key.DOWN to(0 to 1),Key.LEFT to(-1 to 0),Key.RIGHT to(1 to 0))){
                            val next=x+d.first to y+d.second
                            if(scene.probeFrom(x,y,key,v.world.terrainMode)!=MovementBlock.NONE||next in reachable||
                                v.content.exits.any{it.fromMapId==caller&&it.triggerX==next.first&&it.triggerY==next.second&&it.edgeDirection==null})continue
                            reachable.add(next);queue.add(next)
                        }
                    }
                    val at=candidates.firstOrNull{it in reachable}
                    assertNotNull("Original NPC must have a reachable interaction side",at)
                    walk(at!!.first,at.second)
                    val face=when{npc.x<at.first->Key.LEFT;npc.x>at.first->Key.RIGHT;npc.y<at.second->Key.UP;else->Key.DOWN}
                    assertEquals(at,v.world.x/16 to v.world.y/16)
                    val actualUi=GameView::class.java.getDeclaredField("ui").apply{isAccessible=true}.get(v) as ScreenLayout
                    val screen=actualUi.worldToScreen((npc.x*16+8).toFloat(),(npc.y*16+8).toFloat(),v.world.camera(actualUi.viewWidth,actualUi.viewHeight))
                    val before=v.currentSnapshot();tap(v,screen)
                    assertEquals(GameView.Layer.DIALOGUE,v.layer)
                    // Actual NPC tap faces its stable actor before the pure talk proposal.
                    val faced=before.copy(direction=face)
                    val expected=npc.originalTalk?.let{OriginalNpcTalk.begin(faced,it)}
                    if(expected!=null){assertTrue(expected.applied);assertEquals(expected.snapshot,v.currentSnapshot())}
                    else assertEquals(faced,v.currentSnapshot())
                    state("normal-original-resident-$index-first",true);dialogue()
                    val again=v.currentSnapshot();tap(v,screen);dialogue()
                    val repeat=npc.originalTalk?.let{OriginalNpcTalk.begin(again,it)}
                    assertEquals(repeat?.snapshot?:again,v.currentSnapshot())
                    assertEquals(before.money,v.currentSnapshot().money);assertEquals(before.inventory,v.currentSnapshot().inventory)
                    assertEquals(before.characters,v.currentSnapshot().characters)
                }
                fun discoverHiddenMedicine(){
                    val hidden=v.content.npcs.single{it.id=="rom.npc.5.5"};val item=v.content.itemDefinitions.getValue("rom.medicine.1")
                    if((v.currentSnapshot().inventory[item.id]?:0)>=item.maxCount){
                        val store=enter(5,19,null);trade(item.id,false,1);leave(store)
                    }
                    walk(15,8)
                    val stick=layoutFor(v).stick;val middle=center(stick);val up=Pair(middle.first,stick.y+2f)
                    val at=v.currentSnapshot();send(v,MotionEvent.ACTION_DOWN,listOf(middle));send(v,MotionEvent.ACTION_MOVE,listOf(up))
                    SystemClock.sleep(120);send(v,MotionEvent.ACTION_UP,listOf(up))
                    assertEquals(at.x,v.world.x);assertEquals(at.y,v.world.y);assertEquals(Key.UP,v.world.direction)
                    val before=v.currentSnapshot();assertTrue(before.flags["rom.map.5.flag.1"]!=true)
                    val action=center(layoutFor(v).buttons.getValue(Key.A))
                    send(v,MotionEvent.ACTION_DOWN,listOf(action));send(v,MotionEvent.ACTION_CANCEL,listOf(action));assertEquals(before,v.currentSnapshot())
                    tap(v,action);val after=v.currentSnapshot();val expected=WorldItems.openTreasure(before,hidden.treasure!!,item)
                    assertTrue(expected.applied);assertEquals(before.copy(inventory=expected.inventory,flags=expected.flags),after)
                    assertEquals(before.money,after.money);assertEquals(before.characters,after.characters)
                    send(v,MotionEvent.ACTION_UP,listOf(action));assertEquals(after,v.currentSnapshot())
                    tap(v,action);assertEquals(after,v.currentSnapshot())
                    assertEquals(MovementBlock.PHYSICAL,v.world.scene.probeFrom(15,8,Key.UP))
                    state("normal-original-hidden-medicine1-once-still-blocking",true)
                }
                if(village5)discoverHiddenMedicine()
                if(!village5){walk(13,3);state("normal-original-north-bank-without-claimed-ferry",true)}
                checkSourceUnchanged();persist();File(root,"world-$label-expected-save.json").writeText(v.currentSnapshot().json().toString())
                state("normal-original-services-residents-and-save",true)
            }else{
                assertEquals(caller,v.world.mapId);assertNull(v.currentSnapshot().interiorContext)
                val entry=enter(caller,22,null);val before=v.currentSnapshot()
                instrumentation.runOnMainSync{v.handleBack()};assertEquals(before,v.currentSnapshot());leave(entry)
                state("cold-real-inn-reentry-cancel-no-charge",true)
                walk(15,29);if(!village5)step(Key.DOWN);assertEquals(16,v.world.mapId)
                assertEquals(worldDoor,v.world.x/16 to v.world.y/16);state("cold-independent-original-south-return",true)
                step(Key.DOWN);assertEquals(16,v.world.mapId)
                walk(worldDoor.first,worldDoor.second);assertEquals(caller,v.world.mapId)
                assertEquals(15 to 29,v.world.x/16 to v.world.y/16)
                for((flag,value)in source.flags)assertEquals(value,v.currentSnapshot().flags[flag])
                assertEquals(1,v.currentSnapshot().characters.count{it.id=="yangjian"})
                checkSourceUnchanged();persist();state("cold-real-village-reentry-legacy-flags-kept",true)
            }
    }
    /** Exact same-candidate flower save, then ordinary movement/services; no witness injection. */
    fun testNormalNightEightGiftAndDarkCaveFromVerifiedVillageSixSave()=normalWorldStoryContinuation(false,true,night8=true)
    fun testNightEightColdRestartAndOriginalLightReset()=normalWorldStoryContinuation(true,true,night8=true)

    fun testNormalQueenRouteBindingAndHuangFromVerifiedNightEightSave()=normalWorldStoryContinuation(false,true,queen=true)
    fun testQueenHuangColdRestartAndOriginalReturn()=normalWorldStoryContinuation(true,true,queen=true)

    /** Same-candidate earned save. Planning is read-only; all progression uses real UI input. */
    private fun normalQueenStage(v:GameView,cold:Boolean,source:SaveSnapshot,
        walk:(Int,Int)->Unit,step:(Key)->Unit,state:(String,Boolean)->Unit,
        dialogue:()->Unit,finishFight:()->Unit,persist:()->Unit){
        val root=instrumentation.targetContext.getExternalFilesDir(null)
        val victory="rom.map.117.flag.128"
        val gift="rom.special.18"
        assertEquals(1,source.inventory["rom.special.13"])
        assertEquals(true,source.flags["rom.map.87.flag.128"])
        state(if(cold)"cold-exact-queen-huang-completed-save"else"verified-night8-source-no-state-grants",true)
        if(!cold){
            assertEquals(74,v.world.mapId);walk(3,28);assertEquals(16,v.world.mapId)
            step(Key.DOWN);walk(91,134);assertEquals(141,v.world.mapId)
            assertEquals(15 to 29,v.world.x/16 to v.world.y/16)
            state("normal-original-queen-courtyard141-entry",true)
            walk(15,15);assertEquals(115,v.world.mapId);assertEquals(32 to 28,v.world.x/16 to v.world.y/16)
            state("normal-original-queen-city115-real-link",true)
            walk(56,44);assertEquals(141,v.world.mapId);assertEquals(24 to 19,v.world.x/16 to v.world.y/16)
            walk(26,7);assertEquals(117,v.world.mapId);assertEquals(7 to 13,v.world.x/16 to v.world.y/16)
            state("normal-original-queen-palace117-entry",true)
            walk(7,5);assertEquals(GameView.Layer.DIALOGUE,v.layer)
            assertEquals(7 to 5,v.world.x/16 to v.world.y/16)
            state("normal-original-queen-event14-dialogue13",true)
            dialogue();assertEquals(GameView.Layer.BATTLE,v.layer);finishFight()
            assertEquals(GameView.Layer.DIALOGUE,v.layer);assertTrue(v.currentSnapshot().flags[victory]!=true)
            assertEquals(1,v.currentSnapshot().inventory["rom.special.13"])
            state("normal-original-queen-victory-dialogue15-before-flag",true);dialogue()
            assertEquals(GameView.Layer.MAP,v.layer);assertEquals(true,v.currentSnapshot().flags[victory])
            assertEquals(true,v.currentSnapshot().flags["rom.global.7c6.64"])
            state("normal-original-queen-completed-once",true)
            walk(7,4);val huang=v.content.npcs.single{it.id=="rom.npc.117.0"}
            val before=v.currentSnapshot();assertEquals(0,before.inventory[gift]?:0)
            assertTrue("Earned inventory must have the original special-category slot",InventoryCapacity.hasCategorySlot(before.inventory,gift,"special"))
            val ui=layoutFor(v);tap(v,ui.worldToScreen((huang.x*16+8).toFloat(),(huang.y*16+8).toFloat(),v.world.camera(ui.viewWidth,ui.viewHeight)))
            assertEquals(GameView.Layer.DIALOGUE,v.layer)
            val begun=OriginalNpcTalk.begin(before.copy(direction=Key.UP),huang.originalTalk!!,v.content.itemDefinitions.getValue(gift))
            assertTrue(begun.applied);assertEquals(begun.snapshot,v.currentSnapshot());assertEquals(1,v.currentSnapshot().inventory[gift])
            assertTrue(v.currentSnapshot().flags[OriginalNpcTalk.HUANG_COMPLETED_FLAG]!=true)
            state("normal-original-huang-gift-before-dialogue14",true);dialogue()
            val done=OriginalNpcTalk.finishHuang(begun.snapshot,huang.originalTalk!!,"rom.dialogue.127.14")
            assertTrue(done.applied);assertEquals(done.snapshot,v.currentSnapshot())
            assertEquals(true,v.currentSnapshot().flags[OriginalNpcTalk.HUANG_COMPLETED_FLAG])
            assertEquals(true,v.currentSnapshot().flags["rom.global.7fe.128"])
            assertEquals(7 to 4,v.world.x/16 to v.world.y/16)
            persist();File(root,"world-queen117-expected-save.json").writeText(v.currentSnapshot().json().toString())
            state("normal-original-queen-huang-once-and-save",true)
        }else{
            assertEquals(117,v.world.mapId);assertEquals(true,source.flags[victory])
            assertEquals(true,source.flags[OriginalNpcTalk.HUANG_COMPLETED_FLAG]);assertEquals(1,source.inventory[gift])
            assertTrue(source.flags[OriginalNpcTalk.HUANG_PENDING_FLAG]!=true)
            val money=v.currentSnapshot().money;val inventory=v.currentSnapshot().inventory
            walk(7,5);assertEquals(GameView.Layer.MAP,v.layer)
            assertEquals(money,v.currentSnapshot().money);assertEquals(inventory,v.currentSnapshot().inventory)
            state("cold-original-queen-reentry-no-boss-or-gift-repeat",true)
            walk(7,13);assertEquals(141,v.world.mapId)
            walk(24,19);assertEquals(115,v.world.mapId);walk(32,28);assertEquals(141,v.world.mapId)
            walk(15,29);step(Key.DOWN);assertEquals(16,v.world.mapId)
            assertEquals(91 to 134,v.world.x/16 to v.world.y/16)
            assertEquals(true,v.currentSnapshot().flags[victory]);assertEquals(1,v.currentSnapshot().inventory[gift])
            persist();state("cold-independent-queen-original-city-and-world-return",true)
        }
    }

    private fun normalNightEightStage(v:GameView,cold:Boolean,source:SaveSnapshot,
        walk:(Int,Int)->Unit,step:(Key)->Unit,state:(String,Boolean)->Unit,dialogue:()->Unit,persist:()->Unit){
        val root=instrumentation.targetContext.getExternalFilesDir(null)
        val item=v.content.itemDefinitions.getValue(WorldItems.NIGHT_LIGHT_ID)
        fun npcTap(n:StoryNpc){
            val ui=GameView::class.java.getDeclaredField("ui").apply{isAccessible=true}.get(v) as ScreenLayout
            tap(v,ui.worldToScreen((n.x*16+8).toFloat(),(n.y*16+8).toFloat(),v.world.camera(ui.viewWidth,ui.viewHeight)))
        }
        fun useNight(){
            val before=v.currentSnapshot();tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));scrollToItem(v,item.id)
            tap(v,center(v.panelItemBounds(item.id)));assertEquals(before,v.currentSnapshot())
            val action=center(v.panelPrimaryBounds());send(v,MotionEvent.ACTION_DOWN,listOf(action));send(v,MotionEvent.ACTION_CANCEL,listOf(action))
            assertEquals(before,v.currentSnapshot());tap(v,action)
            val expected=WorldItems.useNightLight(before,item,true);assertTrue(expected.applied)
            assertEquals(before.copy(inventory=expected.inventory,flags=expected.flags),v.currentSnapshot())
            val used=v.currentSnapshot();send(v,MotionEvent.ACTION_UP,listOf(action));assertEquals(used,v.currentSnapshot())
            instrumentation.runOnMainSync{v.handleBack()};assertEquals(GameView.Layer.MAP,v.layer)
            assertEquals(1,v.currentSnapshot().inventory[item.id]);assertEquals(true,v.currentSnapshot().flags[WorldItems.NIGHT_LIGHT_FLAG])
        }
        assertEquals(listOf("nezha","xiaolongnv","yangjian"),source.characters.map{it.id})
        assertEquals(true,source.flags["rom.map.87.flag.128"])
        state(if(cold)"cold-exact-earned-night8-and-rope-save"else"verified-village6-source-no-grants",true)
        if(!cold){
            assertEquals(6,v.world.mapId);walk(15,29);step(Key.DOWN);assertEquals(16,v.world.mapId)
            walk(78,158);assertEquals(100,v.world.mapId);state("normal-original-clear-peak-entry",true)
            walk(20,11);assertEquals(164,v.world.mapId);state("normal-original-teacher164-door",true)
            walk(7,5);val teacher=v.content.npcs.single{it.id=="rom.npc.164.1"};val before=v.currentSnapshot()
            assertEquals(0,before.inventory[item.id]?:0);assertTrue(InventoryCapacity.hasCategorySlot(before.inventory,item.id,item.category))
            npcTap(teacher);assertEquals(GameView.Layer.DIALOGUE,v.layer)
            val faced=before.copy(direction=Key.UP);val gift=OriginalNpcTalk.begin(faced,teacher.originalTalk!!,item)
            assertTrue(gift.applied);assertEquals(gift.snapshot,v.currentSnapshot());assertEquals(1,v.currentSnapshot().inventory[item.id])
            state("normal-original-teacher164-earned-night8-dialogue",true);dialogue()
            val paid=v.currentSnapshot();npcTap(teacher);dialogue();assertEquals(paid,v.currentSnapshot())
            state("normal-original-teacher164-repeat-no-second-gift",true)
            walk(7,14);assertEquals(100,v.world.mapId);walk(8,51);assertEquals(16,v.world.mapId)
            walk(70,105);assertEquals(74,v.world.mapId);state("normal-original-dark74-before-light",true)
            assertTrue(v.currentSnapshot().flags[WorldItems.NIGHT_LIGHT_FLAG]!=true);useNight()
            state("normal-original-dark74-night8-no-consumption",true)
            val chest=v.content.npcs.single{it.id=="rom.npc.74.5"};val s=v.world.scene
            val q=java.util.ArrayDeque<Pair<Int,Int>>();val seen=mutableSetOf(v.world.x/16 to v.world.y/16);q.add(seen.single())
            while(q.isNotEmpty()){
                val at=q.removeFirst()
                for(key in listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT)){
                    if(s.probeFrom(at.first,at.second,key)!=null)continue
                    val delta=when(key){Key.UP->0 to -1;Key.DOWN->0 to 1;Key.LEFT->-1 to 0;else->1 to 0}
                    val next=at.first+delta.first to at.second+delta.second
                    if(next.first !in 0 until s.width||next.second !in 0 until s.height)continue
                    if(seen.add(next))q.add(next)
                }
            }
            val side=listOf(chest.x to chest.y+1,chest.x+1 to chest.y,chest.x-1 to chest.y,chest.x to chest.y-1).firstOrNull{it in seen}
            assertNotNull("Original rope chest must have an actually reachable side",side);walk(side!!.first,side.second)
            val beforeChest=v.currentSnapshot();assertTrue(beforeChest.flags[chest.treasure!!.flagId]!=true)
            npcTap(chest);val after=v.currentSnapshot();val expected=WorldItems.openTreasure(beforeChest,chest.treasure!!,v.content.itemDefinitions.getValue("rom.special.13"))
            assertTrue(expected.applied);assertEquals(expected.inventory,after.inventory);assertEquals(expected.flags,after.flags)
            assertEquals(beforeChest.money,after.money);assertEquals(beforeChest.characters,after.characters)
            state("normal-original-dark74-rope13-once",true);npcTap(chest);assertEquals(after,v.currentSnapshot())
            persist();File(root,"world-night8-expected-save.json").writeText(v.currentSnapshot().json().toString())
            state("normal-original-dark74-night8-rope-and-save",true)
        }else{
            assertEquals(74,v.world.mapId);assertEquals(true,source.flags[WorldItems.NIGHT_LIGHT_FLAG]);assertEquals(1,source.inventory[item.id])
            assertEquals(1,source.inventory["rom.special.13"]);assertEquals(true,source.flags["rom.map.74.flag.64"])
            state("cold-original-dark74-lit-scene-and-owned-items",true)
            walk(3,28);assertEquals(16,v.world.mapId);assertEquals(70 to 105,v.world.x/16 to v.world.y/16)
            assertTrue(WorldItems.NIGHT_LIGHT_FLAG !in v.currentSnapshot().flags)
            assertEquals(true,v.currentSnapshot().flags[WorldItems.NIGHT_LIGHT_USED_FLAG]);state("cold-original-dark74-left-retains-night8",true)
            step(Key.DOWN);walk(70,105);assertEquals(74,v.world.mapId);assertTrue(v.currentSnapshot().flags[WorldItems.NIGHT_LIGHT_FLAG]!=true)
            state("cold-original-dark74-reentry-dark-no-second-rope",true);useNight();assertEquals(1,v.currentSnapshot().inventory["rom.special.13"])
            assertEquals(true,v.currentSnapshot().flags["rom.map.74.flag.64"]);persist();state("cold-original-dark74-reused-night8-and-continue",true)
        }
    }

    private fun normalVillageSixStage(v:GameView,cold:Boolean,source:SaveSnapshot,
        walk:(Int,Int)->Unit,step:(Key)->Unit,state:(String,Boolean)->Unit,
        enter:(Int,Int,String?)->MapExit,leave:(MapExit)->Unit,
        trade:(String,Boolean,Int)->Unit,dialogue:()->Unit,persist:()->Unit){
        val root=instrumentation.targetContext.getExternalFilesDir(null)
        assertEquals(listOf("nezha","xiaolongnv","yangjian"),source.characters.map{it.id})
        assertEquals(true,source.flags["rom.map.87.flag.128"])
        state(if(cold)"cold-exact-normal-village6-save"else"verified-flower-source-no-grants",true)
        if(!cold){
            assertEquals(87,v.world.mapId);walk(19,13);assertEquals(5,v.world.mapId)
            walk(15,29);assertEquals(16,v.world.mapId)
            walk(63,135);assertEquals(6,v.world.mapId);assertEquals(15 to 29,v.world.x/16 to v.world.y/16)
            state("normal-original-village6-entry-no-speech-gate",true)
            for((room,id)in listOf(17 to "rom.weapon.9",18 to "rom.armor.31",19 to "rom.medicine.1")){
                val entry=enter(6,room,null);state("normal-original-village6-shop-$room-open",true)
                val item=v.content.itemDefinitions.getValue(id)
                if((v.currentSnapshot().inventory[id]?:0)>=item.maxCount)trade(id,false,1)
                assertTrue("Only actual earned money pays original village6 prices",v.currentSnapshot().money>=item.buyPrice!!)
                trade(id,true,1);state("normal-original-village6-shop-$room-bought",true)
                trade(id,false,1);state("normal-original-village6-shop-$room-sold",true);leave(entry)
            }
            val innEntry=enter(6,22,null);val beforeRest=v.currentSnapshot()
            val rest=InnStay.apply(beforeRest.money,beforeRest.characters,v.content.inns.getValue(v.activeInnId!!))
            assertNull("Normal earnings must cover original 200-liang inn",rest.error)
            tap(v,center(v.innStayBounds()));assertEquals(rest.money,v.currentSnapshot().money)
            assertEquals(rest.characters,v.currentSnapshot().characters)
            assertEquals(beforeRest.inventory,v.currentSnapshot().inventory);assertEquals(beforeRest.flags,v.currentSnapshot().flags)
            state("normal-original-village6-inn-paid",true);leave(innEntry)
            for(id in listOf("rom.npc.20.0","rom.npc.20.1")){
                val entry=enter(6,20,id);val before=v.currentSnapshot()
                state("normal-original-village6-clinic-${id.substringAfterLast('.')}-view",true)
                instrumentation.runOnMainSync{v.handleBack()};assertEquals(before,v.currentSnapshot());leave(entry)
            }
            for(index in listOf(0,1,2,4,5,6,7)){
                val npc=v.content.npcs.single{it.id=="rom.npc.6.$index"}
                val scene=v.world.scene;val reachable=mutableSetOf(v.world.x/16 to v.world.y/16)
                val q=java.util.ArrayDeque<Pair<Int,Int>>();q.add(reachable.single())
                while(q.isNotEmpty()){
                    val(x,y)=q.removeFirst()
                    for((key,d)in listOf(Key.UP to(0 to -1),Key.DOWN to(0 to 1),Key.LEFT to(-1 to 0),Key.RIGHT to(1 to 0))){
                        val next=x+d.first to y+d.second
                        if(scene.probeFrom(x,y,key,v.world.terrainMode)!=MovementBlock.NONE||next in reachable||
                            v.content.exits.any{it.fromMapId==6&&it.triggerX==next.first&&it.triggerY==next.second&&it.edgeDirection==null})continue
                        reachable.add(next);q.add(next)
                    }
                }
                val at=listOf(0 to 1,1 to 0,-1 to 0,0 to -1).map{npc.x+it.first to npc.y+it.second}.firstOrNull{it in reachable}
                assertNotNull("Original village6 NPC must have a reachable side",at);walk(at!!.first,at.second)
                val face=when{npc.x<at.first->Key.LEFT;npc.x>at.first->Key.RIGHT;npc.y<at.second->Key.UP;else->Key.DOWN}
                val ui=GameView::class.java.getDeclaredField("ui").apply{isAccessible=true}.get(v) as ScreenLayout
                val screen=ui.worldToScreen((npc.x*16+8).toFloat(),(npc.y*16+8).toFloat(),v.world.camera(ui.viewWidth,ui.viewHeight))
                repeat(2){visit->
                    val before=v.currentSnapshot();tap(v,screen);assertEquals(GameView.Layer.DIALOGUE,v.layer)
                    val faced=before.copy(direction=face)
                    val expected=npc.originalTalk?.let{OriginalNpcTalk.begin(faced,it)}
                    assertEquals(expected?.snapshot?:faced,v.currentSnapshot())
                    state("normal-original-village6-resident-$index-${if(visit==0)"first"else"repeat"}",true);dialogue()
                    assertEquals(before.money,v.currentSnapshot().money);assertEquals(before.inventory,v.currentSnapshot().inventory)
                    assertEquals(before.characters,v.currentSnapshot().characters)
                }
            }
            val hidden=v.content.npcs.single{it.id=="rom.npc.6.3"};val item=v.content.itemDefinitions.getValue("rom.medicine.1")
            if((v.currentSnapshot().inventory[item.id]?:0)>=item.maxCount){val store=enter(6,19,null);trade(item.id,false,1);leave(store)}
            walk(17,5);assertEquals(MovementBlock.PHYSICAL,v.world.scene.probeFrom(17,5,Key.RIGHT))
            val stick=layoutFor(v).stick;val middle=center(stick);val right=Pair(stick.x+stick.w-2f,middle.second)
            val at=v.currentSnapshot();send(v,MotionEvent.ACTION_DOWN,listOf(middle));send(v,MotionEvent.ACTION_MOVE,listOf(right))
            SystemClock.sleep(120);send(v,MotionEvent.ACTION_UP,listOf(right))
            assertEquals(at.x,v.world.x);assertEquals(at.y,v.world.y);assertEquals(Key.RIGHT,v.world.direction)
            val before=v.currentSnapshot();assertTrue(before.flags["rom.map.6.flag.8"]!=true)
            val action=center(layoutFor(v).buttons.getValue(Key.A))
            send(v,MotionEvent.ACTION_DOWN,listOf(action));send(v,MotionEvent.ACTION_CANCEL,listOf(action));assertEquals(before,v.currentSnapshot())
            tap(v,action);val after=v.currentSnapshot();val expected=WorldItems.openTreasure(before,hidden.treasure!!,item)
            assertTrue(expected.applied);assertEquals(before.copy(inventory=expected.inventory,flags=expected.flags),after)
            send(v,MotionEvent.ACTION_UP,listOf(action));assertEquals(after,v.currentSnapshot())
            tap(v,action);assertEquals(after,v.currentSnapshot());state("normal-original-village6-hidden-medicine1-once",true)
            persist();File(root,"world-village6-expected-save.json").writeText(v.currentSnapshot().json().toString())
            state("normal-original-village6-services-residents-and-save",true)
        }else{
            assertEquals(6,v.world.mapId);assertEquals(true,source.flags["rom.map.6.flag.8"])
            val entry=enter(6,22,null);val before=v.currentSnapshot()
            instrumentation.runOnMainSync{v.handleBack()};assertEquals(before,v.currentSnapshot());leave(entry)
            state("cold-original-village6-inn-cancel-no-charge",true)
            walk(15,29);step(Key.DOWN);assertEquals(16,v.world.mapId)
            assertEquals(63 to 135,v.world.x/16 to v.world.y/16);state("cold-original-village6-independent-return",true)
            val departure=listOf(Key.DOWN,Key.LEFT,Key.UP,Key.RIGHT).first{key->
                val nx=v.world.x/16+if(key==Key.RIGHT)1 else if(key==Key.LEFT)-1 else 0
                val ny=v.world.y/16+if(key==Key.DOWN)1 else if(key==Key.UP)-1 else 0
                v.world.scene.probeFrom(v.world.x/16,v.world.y/16,key,v.world.terrainMode)==MovementBlock.NONE&&
                    v.content.exits.none{it.fromMapId==16&&it.triggerX==nx&&it.triggerY==ny}}
            step(departure);assertEquals(16,v.world.mapId);walk(63,135);assertEquals(6,v.world.mapId)
            assertEquals(15 to 29,v.world.x/16 to v.world.y/16)
            for((flag,value)in source.flags)assertEquals(value,v.currentSnapshot().flags[flag])
            assertEquals(source.inventory,v.currentSnapshot().inventory)
            assertEquals(source.characters.map{it.id},v.currentSnapshot().characters.map{it.id})
            persist();state("cold-original-village6-reentry-no-repeat-pickup",true)
        }
    }
    /** Same-run ordinary island save; callbacks execute existing UI paths only. */
    private fun normalCave87Stage(v:GameView,cold:Boolean,source:SaveSnapshot,
        walk:(Int,Int)->Unit,step:(Key)->Unit,ferry:(Int)->Unit,state:(String,Boolean)->Unit,
        fight:()->Unit,inn:(Int)->Unit,enter:(Int,Int)->MapExit,leave:(MapExit)->Unit,
        trade:(String,Boolean,Int)->Unit,persist:()->Unit){
        val root=instrumentation.targetContext.getExternalFilesDir(null)
        val boss=v.content.battle!!.storyBattles.getValue("rom.npc.87.0");val chain=boss.continuation!!
        assertEquals(listOf("nezha","xiaolongnv","yangjian"),source.characters.map{it.id})
        assertEquals(true,source.flags["rom.map.76.flag.128"])
        assertEquals(true,source.flags["rom.global.7c6.16"])
        state(if(cold)"cold-exact-flower-event-save"else"verified-four-villains-source-no-grants",true)
        if(!cold){
            assertEquals(76,v.world.mapId);assertTrue(source.flags[boss.flagId]!=true)
            for(next in listOf(77,78,79,16)){
                val exit=v.content.exits.first{it.fromMapId==v.world.mapId&&it.toMapId==next}
                walk(exit.triggerX,exit.triggerY);assertEquals(next,v.world.mapId)
            }
            walk(150,135);ferry(46);assertEquals(4,v.world.mapId)
            // Actual village4 stock contains both supplies; town5 has no herb0.
            inn(4);val store=enter(4,19)
            for(id in listOf(HerbUse.ID,AntidoteUse.ID)){
                val count=(10-(v.currentSnapshot().inventory[id]?:0)).coerceAtLeast(0)
                if(count>0){
                    val price=v.content.itemDefinitions.getValue(id).buyPrice!!
                    assertTrue("Use actual earned money, never gift supplies",v.currentSnapshot().money>=count*price)
                    trade(id,true,count)
                }
            }
            leave(store);walk(15,29);step(Key.DOWN);assertEquals(16,v.world.mapId)
            walk(107,157);assertEquals(5,v.world.mapId);state("normal-original-village5-after-island",true)
            walk(7,6);assertEquals(87,v.world.mapId);assertEquals(19 to 13,v.world.x/16 to v.world.y/16)
            state("normal-real-back-cave87-door-and-encounters",true)
            walk(1,8);val before=v.currentSnapshot();step(Key.UP)
            assertEquals(GameView.Layer.DIALOGUE,v.layer);val won=v.currentSnapshot()
            assertEquals(before.money+1600,won.money)
            assertEquals(true,won.flags[boss.pendingFlag]);assertTrue(won.flags[boss.flagId]!=true)
            assertEquals(5 to 5,v.world.x/16 to v.world.y/16)
            state("normal-flower-boss-victory-before-seven-messages",true)
            for(id in chain.dialogueIds){
                assertEquals(GameView.Layer.DIALOGUE,v.layer);assertEquals(id,boss.pendingDialogue(v.currentSnapshot().flags))
                state("normal-original-flower-message-$id",true)
                tap(v,Pair(v.width*.5f,v.height*.5f))
            }
            assertEquals(GameView.Layer.MAP,v.layer);val after=v.currentSnapshot()
            assertEquals(4 to 6,v.world.x/16 to v.world.y/16);assertEquals(Key.RIGHT,v.world.direction)
            assertEquals(true,after.flags[boss.flagId]);assertEquals(true,after.flags["rom.global.7bf.16"])
            assertTrue(after.flags[boss.pendingFlag]!=true)
            assertEquals(won.inventory,after.inventory);assertEquals(won.money,after.money)
            assertEquals(won.characters.single{it.id=="xiaolongnv"}.copy(statusMask=won.characters.single{it.id=="xiaolongnv"}.statusMask or 64),
                after.characters.single{it.id=="xiaolongnv"})
            assertEquals(listOf("nezha","yangjian"),OriginalPartyRules.battleCharacters(after.characters).map{it.id})
            assertFalse(v.content.npcsForState(87,after.flags).any{it.id==boss.npcId})
            persist();File(root,"world-cave87-expected-save.json").writeText(v.currentSnapshot().json().toString())
            state("normal-flower-script-complete-away-record-and-save",true)
        }else{
            assertEquals(87,v.world.mapId);assertEquals(true,source.flags[boss.flagId])
            assertEquals(64,source.characters.single{it.id=="xiaolongnv"}.statusMask and 64)
            walk(1,7);assertEquals(GameView.Layer.MAP,v.layer)
            assertEquals(true,v.currentSnapshot().flags[boss.flagId]);assertTrue(v.currentSnapshot().flags[boss.pendingFlag]!=true)
            state("cold-real-trigger-no-repeat-flower-or-reward",true)
            walk(19,13);assertEquals(5,v.world.mapId);assertEquals(7 to 6,v.world.x/16 to v.world.y/16)
            state("cold-original-independent-return-to-village5",true)
            step(Key.DOWN);walk(7,6);assertEquals(87,v.world.mapId)
            assertFalse(v.content.npcsForState(87,v.currentSnapshot().flags).any{it.id==boss.npcId})
            val away=v.currentSnapshot().characters.single{it.id=="xiaolongnv"}
            val original=source.characters.single{it.id=="xiaolongnv"}
            assertEquals(original.copy(hp=away.hp,statusMask=away.statusMask),away)
            // Original BA30..BA7F still processes poisoned away records; at HP0
            // it writes status20 (not OR20). Do not invent immunity/preserve40.
            if(original.statusMask and OriginalStatus.POISON==0||original.hp==0)assertEquals(original,away)
            else{
                assertTrue(away.hp in 0..original.hp)
                assertEquals(if(away.hp==0)OriginalStatus.DEAD else original.statusMask,away.statusMask)
            }
            assertEquals(if(OriginalPartyRules.present(away))3 else 2,
                OriginalPartyRules.battleCharacters(v.currentSnapshot().characters).size)
            persist();state("cold-original-reentry-away-actor-retained-and-continue",true)
        }
    }
    private fun normalFerryStage(v:GameView,cold:Boolean,source:SaveSnapshot,
        walk:(Int,Int)->Unit,step:(Key)->Unit,ferry:(Int)->Unit,state:(String,Boolean)->Unit,
        actors:()->Int,persist:()->Unit){
        val root=instrumentation.targetContext.getExternalFilesDir(null)
            assertEquals(listOf("nezha","xiaolongnv","yangjian"),source.characters.map{it.id})
            state(if(cold)"cold-exact-normal-island-save"else"verified-village-source-no-state-grants",true)
            if(!cold){
                assertEquals(4,v.world.mapId);walk(11,3);ferry(45)
                assertEquals(16,v.world.mapId);assertEquals(150 to 135,v.world.x/16 to v.world.y/16)
                for(key in listOf(Key.UP,Key.UP,Key.RIGHT,Key.UP,Key.UP,Key.UP,Key.UP,Key.UP,Key.RIGHT,Key.RIGHT))step(key)
                assertEquals(79,v.world.mapId);assertEquals(7 to 12,v.world.x/16 to v.world.y/16)
                state("normal-original-island79-door-and-complete-zone22",true)
                walk(7,11)
                var steps=0
                while(actors()==0){
                    assertTrue("Island actual encounter must occur without forcing RNG",steps++<200)
                    walk(7,10);walk(7,11)
                }
                assertEquals(79,v.world.mapId);assertTrue(actors()>0)
                walk(7,11);persist()
                File(root,"world-ferry-expected-save.json").writeText(v.currentSnapshot().json().toString())
                state("normal-island-natural-three-party-encounter-and-save",true)
            }else{
                assertEquals(79,v.world.mapId);assertEquals(true,source.flags[OriginalFerry.PARKED_FLAG])
                walk(7,12);assertEquals(16,v.world.mapId);assertEquals(153 to 128,v.world.x/16 to v.world.y/16)
                state("cold-independent-island-original-return",true)
                walk(150,135);ferry(46)
                assertEquals(4,v.world.mapId);assertEquals(11 to 3,v.world.x/16 to v.world.y/16)
                assertTrue(v.currentSnapshot().flags[OriginalFerry.PARKED_FLAG]!=true)
                for((flag,value)in source.flags.filterKeys{it!=OriginalFerry.PARKED_FLAG})assertEquals(value,v.currentSnapshot().flags[flag])
                persist();state("cold-original-fixed-reverse-and-village-continue",true)
            }
    }
    private fun normalWorldStoryContinuation(cold:Boolean,east:Boolean,hell:Boolean=false,firstHall:Boolean=false,secondHall:Boolean=false,hallBatch:Boolean=false,rebirth:Boolean=false,village3:Boolean=false,medical:Boolean=false,continentBridge:Boolean=false,forest101:Boolean=false,tree107:Boolean=false,room171:Boolean=false,yangJoin:Boolean=false,village4:Boolean=false,ferry:Boolean=false,island:Boolean=false,village5:Boolean=false,cave87:Boolean=false,village6:Boolean=false,night8:Boolean=false,queen:Boolean=false,fixtureLabel:String?=null,normalSourceName:String?=null){
        val root=instrumentation.targetContext.getExternalFilesDir(null)
        require(fixtureLabel==null || (fixtureLabel=="controlled-r1-village2" && hell && east))
        val label=fixtureLabel?:if(queen)"queen117"else if(night8)"night8"else if(village6)"village6"else if(cave87)"cave87"else if(village5)"village5"else if(island)"island"else if(ferry)"ferry"else if(village4)"village4"else if(yangJoin)"yang-join"else if(room171)"room171"else if(tree107)"tree107"else if(forest101)"forest101"else if(continentBridge)"continent-bridge"else if(medical)"medical"else if(village3)"village3" else if(rebirth)"rebirth" else if(hallBatch)"hall-batch" else if(secondHall)"second-hall" else if(firstHall)"first-hall" else if(hell)"hell-village2" else if(east)"east-palace" else "cave85"
        val sourceFile=File(root,if(cold)"world-$label-expected-save.json" else if(fixtureLabel!=null)"world-$fixtureLabel-source.json" else normalSourceName?:
            if(queen)"world-night8-expected-save.json"else if(night8)"world-village6-expected-save.json"else if(village6)"world-cave87-expected-save.json"else if(cave87)"world-island-expected-save.json"else if(island)"world-ferry-expected-save.json"else if(ferry)"world-village4-expected-save.json"else if(village4||village5)"world-yang-join-expected-save.json"else if(yangJoin)"world-room171-expected-save.json"else if(room171)"world-tree107-expected-save.json"else if(tree107||forest101)"world-continent-bridge-expected-save.json"else if(continentBridge)"world-medical-expected-save.json"else if(medical)"world-village3-expected-save.json"else if(village3)"world-rebirth-expected-save.json" else if(rebirth)"world-hall-batch-expected-save.json" else if(hallBatch)"world-second-hall-expected-save.json" else if(secondHall)"world-first-hall-expected-save.json" else if(firstHall)"world-hell-village2-expected-save.json" else if(hell)"world-east-palace-expected-save.json" else if(east)"world-cave85-expected-save.json" else "world-north-palace-expected-save.json")
        assertTrue("The same candidate's preceding normal recording must produce this checkpoint",sourceFile.exists())
        val sourceBytes=sourceFile.readBytes();val source=SaveSnapshot.parse(sourceBytes.toString(Charsets.UTF_8))
        val sourceHash=java.security.MessageDigest.getInstance("SHA-256").digest(sourceBytes).joinToString(""){"%02x".format(it)}
        assertStoryReplaySource(source,cold,east)
        val caveFlag="rom.map.85.flag.128"
        val(activity,v)=launch()
        if(!cold)instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(source))}
        assertEquals(if(cold)"External force-stop must preserve the complete normal save" else
            "Only the admitted controlled replay version marker may change at load",expectedReplaySnapshot(source,v,fixtureLabel!=null,cold),v.currentSnapshot())
        val events=org.json.JSONArray();val started=SystemClock.elapsedRealtime()
        var fights=0;var battleHerbs=0;var bossHerbs=0;var bossEntries=0
        var capturedBossAttack=false;var capturedBattleHerb=false
        var capturedBossIce=false;var capturedBossSpecial=false;var twoActorBattles=0;var twoActorVictories=0;var threeActorBattles=0;var threeActorVictories=0
        var lastMovementKey=Key.DOWN
        var training=false
        val battleField=GameView::class.java.getDeclaredField("battle").apply{isAccessible=true}
        val presentationField=GameView::class.java.getDeclaredField("battlePresentation").apply{isAccessible=true}
        val committedField=GameView::class.java.getDeclaredField("battleCommitted").apply{isAccessible=true}
        fun state(name:String,capture:Boolean=true){
            if(capture)screenshot(v,"world-$label-$name")
            events.put(org.json.JSONObject().put("name",name).put("elapsedMs",SystemClock.elapsedRealtime()-started)
                .put("androidUptimeMs",SystemClock.elapsedRealtime()).put("snapshot",v.currentSnapshot().json()))
            File(root,"world-$label-${if(cold)"cold" else "normal"}-index.json").writeText(replayLoadMetadata(org.json.JSONObject()
                .put("kind",if(fixtureLabel!=null)if(cold)"CONTROLLED_ACTIVITY_RESTART_OF_VERIFIED_SAVE"else"CONTROLLED_REPLAY_OF_VERIFIED_NORMAL_SAVE"
                    else if(cold)"EXTERNAL_COLD_RESTART_AND_NORMAL_REENTRY" else "CONTINUATION_FROM_VERIFIED_SAVE").put("sourceFile",sourceFile.name)
                .put("sourceSha256",sourceHash).put("sourceSnapshot",source.json()),source,v.content.scene.version)
                .put("events",events).put("fights",fights).put("battleHerbs",battleHerbs).put("bossHerbs",bossHerbs)
                .put("bossEntries",bossEntries).put("bossAttackObserved",capturedBossAttack).put("bossHerbObserved",capturedBattleHerb)
                .put("bossIceObserved",capturedBossIce).put("bossSpecialObserved",capturedBossSpecial).put("twoActorBattles",twoActorBattles)
                .put("twoActorVictories",twoActorVictories).put("threeActorBattles",threeActorBattles).put("threeActorVictories",threeActorVictories).toString())
        }
        fun medicine(id:String,owner:String?=null){
            assertEquals(GameView.Layer.MAP,v.layer);val before=v.currentSnapshot();val target=owner?:before.characters.first().id
            assertTrue("Normal supply exhausted: $id; never inject inventory",(before.inventory[id]?:0)>0)
            tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));if(before.characters.size>1)tap(v,center(v.panelCharacterBounds(target)));scrollToItem(v,id)
            tap(v,center(v.panelItemBounds(id)));assertEquals(before,v.currentSnapshot())
            tap(v,center(v.panelPrimaryBounds()));val after=v.currentSnapshot()
            if(id==HerbUse.ID){
                assertEquals(minOf(before.characters.single{it.id==target}.maxHp,before.characters.single{it.id==target}.hp+50),after.characters.single{it.id==target}.hp)
                assertEquals((before.inventory[id]?:0)-1,after.inventory[id]?:0)
            }else{
                assertEquals(0,after.characters.single{it.id==target}.statusMask and OriginalStatus.POISON)
                assertEquals(before.characters.single{it.id==target}.hp,after.characters.single{it.id==target}.hp)
                assertEquals(((before.inventory[id]?:0)-2).coerceAtLeast(0),after.inventory[id]?:0)
            }
            state(if(id==HerbUse.ID)"normal-map-herb" else "normal-map-antidote")
            instrumentation.runOnMainSync{v.handleBack()};assertEquals(GameView.Layer.MAP,v.layer)
        }
        fun supply(){
            if(v.layer!=GameView.Layer.MAP)return
            if(east||hell||firstHall||secondHall||hallBatch||rebirth||continentBridge||forest101||tree107||room171||yangJoin||village4||ferry||island||village5||cave87||village6||night8||queen){
                for(actor in v.currentSnapshot().characters.filter{it.hp>0&&OriginalPartyRules.present(it)}){
                    if(actor.statusMask and OriginalStatus.POISON!=0&&(v.currentSnapshot().inventory[AntidoteUse.ID]?:0)>=2)medicine(AntidoteUse.ID,actor.id)
                    if(!training&&actor.hp<=actor.maxHp/2&&(v.currentSnapshot().inventory[HerbUse.ID]?:0)>0)medicine(HerbUse.ID,actor.id)
                };return
            }
            if(v.currentSnapshot().characters.first().statusMask and OriginalStatus.POISON!=0)medicine(AntidoteUse.ID)
            val s=v.currentSnapshot();val h=s.characters.first()
            if(!training&&h.hp<=h.maxHp/2&&(s.inventory[HerbUse.ID]?:0)>0)medicine(HerbUse.ID)
        }
        fun finishFight(){
            var entered:OpeningBattle?=null
            instrumentation.runOnMainSync{if(v.layer==GameView.Layer.BATTLE)entered=battleField.get(v) as OpeningBattle}
            val initial=entered?:return;val beforeFight=v.currentSnapshot()
            val boss=initial.enemies.any{if(queen)it.definition.id==157 else if(cave87)it.definition.id==156 else if(island)it.definition.id in 152..155 else if(rebirth)it.definition.id in listOf(150,151) else if(hallBatch)it.definition.id in listOf(144,145,146,147,148,149) else
                it.definition.id==if(secondHall)143 else if(firstHall)142 else if(east)141 else 140};fights++
            if(initial.party.size==2){twoActorBattles++;state("two-actor-natural-encounter")}
            if(initial.party.size==3){threeActorBattles++;state("three-actor-natural-encounter")}
            if(boss){
                assertFalse("A completed story must never start again after cold restart",cold)
                if(rebirth)assertTrue("No repeated final hall battle",++bossEntries<=2)
                else if(hallBatch)assertTrue("No repeated hall battle",++bossEntries<=6)
                else assertEquals("Only the original one-shot encounter may start",1,++bossEntries)
                if(island){
                    assertEquals(listOf(0,2,4,6),initial.enemies.map{it.slot})
                    assertEquals(listOf(152,153,154,155),initial.enemies.map{it.definition.id})
                    assertEquals(listOf(1400,1600,1400,1800),initial.enemies.map{it.definition.hp})
                }else assertEquals(if(rebirth)mapOf(150 to 2500,151 to 3500).getValue(initial.enemies.single().definition.id)
                    else if(hallBatch)mapOf(144 to 850,145 to 1150,146 to 1300,147 to 1540,148 to 1860,149 to 2100).getValue(initial.enemies.single().definition.id)
                    else if(queen)7000 else if(cave87)4500 else if(secondHall)600 else if(firstHall)520 else if(east)400 else 240,initial.enemies.single().definition.hp)
                state("boss-entry")
            }
            var bindingSubmitted=false;var bindingApplied=false;var bindingRoundPresented=false
            val deadline=SystemClock.elapsedRealtime()+if(queen&&boss)600000 else 240000
            while(true){
                var observed:Triple<OpeningBattle,BattlePresentation,Boolean>?=null
                instrumentation.runOnMainSync{if(v.layer==GameView.Layer.BATTLE)observed=Triple(
                    battleField.get(v) as OpeningBattle,presentationField.get(v) as BattlePresentation,committedField.getBoolean(v))}
                val (fight,presentation,committed)=observed?:break
                assertTrue("Normal $label encounter exceeded budget",SystemClock.elapsedRealtime()<deadline)
                assertTrue("Normal $label defeat; no state repair or forced victory permitted",fight.phase!=BattlePhase.DEFEAT)
                val displayedAction=presentation.action
                if((island||queen)&&boss&&presentation.screen==BattlePresentation.Screen.ACTING)bindingRoundPresented=true
                if((island||queen)&&boss&&presentation.screen==BattlePresentation.Screen.ACTING&&displayedAction?.kind==BattleActionKind.SPECIAL&&displayedAction.actorId!=null){
                    assertEquals(1,v.currentSnapshot().inventory[if(queen)"rom.special.13"else"rom.special.9"])
                    bindingApplied=true;state("normal-binding-original-actor-order-effect")
                }
                if(boss&&!capturedBossSpecial&&presentation.screen==BattlePresentation.Screen.ACTING&&displayedAction?.kind==BattleActionKind.SPECIAL){
                    capturedBossSpecial=true;state("boss-original-special-action")
                }
                if(boss&&!capturedBossIce&&presentation.screen==BattlePresentation.Screen.ACTING&&displayedAction?.kind==BattleActionKind.ICE){
                    state("boss-ice-action");capturedBossIce=true
                }
                if(boss&&!capturedBossAttack&&presentation.screen==BattlePresentation.Screen.ACTING&&
                    displayedAction?.let{it.actorSlot!=null&&it.kind==BattleActionKind.DAMAGE}==true){
                    state("boss-physical-action");capturedBossAttack=true
                }
                if(boss&&!capturedBattleHerb&&presentation.screen==BattlePresentation.Screen.ACTING&&displayedAction?.kind==BattleActionKind.HEAL){
                    val healedId=displayedAction.targetId?:fight.hero.id
                    val healed=fight.party.single{it.id==healedId}
                    val shown=displayedAction.partyHp.getValue(healedId)
                    val prior=shown-displayedAction.hpDelta
                    assertEquals(minOf(healed.maxHp,prior+50),shown)
                    if(healedId==fight.hero.id)assertEquals(minOf(fight.hero.maxHp,displayedAction.beforeHeroHp+50),displayedAction.heroHp)
                    state("boss-herb-action");capturedBattleHerb=true
                }
                if(presentation.screen in listOf(BattlePresentation.Screen.COMMAND,BattlePresentation.Screen.TARGET)&&fight.inputHero!=null){
                    // A faster enemy can legitimately kill the caster before its
                    // queued slot. Retry through a NEW normal command after that
                    // presented round; never set the internal protection marker.
                    if((island||queen)&&boss&&bindingRoundPresented&&!bindingApplied){bindingSubmitted=false;bindingRoundPresented=false}
                    val acting=fight.inputHero!!
                    val heal=acting.hp<=acting.maxHp/2||(boss&&bossHerbs==0&&acting.hp<acting.maxHp)
                    if((island||queen)&&boss&&!bindingSubmitted){
                        val id=if(queen)"rom.special.13"else"rom.special.9";val count=v.currentSnapshot().inventory[id]
                        assertEquals(1,count);val previous=fight.inputRevision
                        tap(v,center(v.battleCommandBounds(2)));scrollToBattleItem(v,id)
                        val selected=v.currentSnapshot();tap(v,center(v.battleItemBounds(id)))
                        assertEquals(selected,v.currentSnapshot());assertEquals(previous,fight.inputRevision)
                        val action=center(v.battleItemUseBounds())
                        tap(v,action);assertEquals(previous+1,fight.inputRevision)
                        send(v,MotionEvent.ACTION_UP,listOf(action));assertEquals(previous+1,fight.inputRevision)
                        assertEquals(count,v.currentSnapshot().inventory[id]);bindingSubmitted=true
                        state("normal-real-binding-command-selected-not-consumed")
                    }else if(heal&&v.battleHerbCount()>0){
                        val before=fight.hero;val count=v.battleHerbCount()
                        tap(v,center(v.battleCommandBounds(2)));tap(v,center(v.battleItemBounds(HerbUse.ID)))
                        assertEquals(before,fight.hero);assertEquals(count,v.battleHerbCount())
                        tap(v,center(v.battleItemUseBounds()));assertEquals(count-1,v.battleHerbCount())
                        battleHerbs++;if(boss)bossHerbs++
                    }else if(!boss&&!training)tap(v,center(v.battleCommandBounds(3)))
                    else chooseAttack(v,fight.enemies.first{it.hp>0}.slot)
                }else if(boss&&presentation.screen==BattlePresentation.Screen.RESULT&&committed){
                    state("boss-victory-result");tap(v,center(v.battleResultBounds()))
                }
                SystemClock.sleep(40)
            }
            if(!boss){
                assertEquals(GameView.Layer.MAP,v.layer)
                if(initial.party.size>1&&initial.phase==BattlePhase.VICTORY){
                    if(initial.party.size==2)twoActorVictories++ else if(initial.party.size==3)threeActorVictories++
                    val living=initial.party.filter{it.hp>0}.map{it.id}.toSet()
                    assertTrue(living.isNotEmpty())
                    val xp=(initial.enemies.sumOf{it.definition.experienceReward} and 65535)/living.size
                    val after=v.currentSnapshot()
                    for(actor in beforeFight.characters)assertEquals("Own EXP share for ${actor.id}",
                        actor.experience+if(actor.id in living)xp else 0,after.characters.single{it.id==actor.id}.experience)
                    assertEquals(beforeFight.money+initial.enemies.sumOf{it.definition.moneyReward},after.money)
                    state(if(initial.party.size==3)"three-actor-victory-own-growth-and-reward"else"two-actor-victory-own-growth-and-reward")
                }
                supply()
            }
        }
        fun step(key:Key){assertEquals(GameView.Layer.MAP,v.layer);lastMovementKey=key;stickStep(v,key);finishFight();supply()}
        // Read-only BFS includes the original terrain plane. Every chosen edge is
        // executed through real joystick gestures; no world.tick/restore/teleport.
        fun walkTo(tx:Int,ty:Int){
            val routeMap=v.world.mapId;var replans=0
            while(v.world.mapId==routeMap&&(v.world.x/16!=tx||v.world.y/16!=ty)){
            assertTrue("Normal $label path did not converge; no position repair",replans++<4096)
            val scene=v.world.scene;val start=(v.world.y/16*scene.width+v.world.x/16) to v.world.terrainMode
            val target=ty*scene.width+tx;if(start.first==target)return
            val routeFlags=v.currentSnapshot().flags
            val queue=java.util.ArrayDeque<Pair<Int,Int>>();queue.add(start)
            val parents=mutableMapOf<Pair<Int,Int>,Pair<Pair<Int,Int>,Key>>()
            parents[start]=start to Key.UP;var goal:Pair<Int,Int>?=null
            while(queue.isNotEmpty()&&goal==null){
                val at=queue.removeFirst();val x=at.first%scene.width;val y=at.first/scene.width
                for((key,d)in listOf(Key.UP to (0 to -1),Key.DOWN to (0 to 1),Key.LEFT to (-1 to 0),Key.RIGHT to (1 to 0))){
                    if(scene.probeFrom(x,y,key,at.second)!=MovementBlock.NONE)continue
                    val nx=x+d.first;val ny=y+d.second;val cell=ny*scene.width+nx
                    val next=cell to scene.terrainDecision(x,y,key,at.second).nextMode
                    if(next in parents||(cell!=target&&v.content.exits.any{it.fromMapId==scene.mapId&&it.triggerX==nx&&it.triggerY==ny&&it.edgeDirection==null}))continue
                    if(cell!=target&&v.content.battle?.storyBattles?.values?.any{
                        it.triggersAt(scene.mapId,nx,ny,routeFlags)}==true)continue
                    parents[next]=at to key
                    if(cell==target){goal=next;break};queue.add(next)
                }
            }
            assertNotNull("No original legal $label route map=${scene.mapId} plane=${start.second} to $tx,$ty",goal)
            val keys=mutableListOf<Key>();var cursor=goal!!
            while(cursor!=start){val parent=parents.getValue(cursor);keys.add(parent.second);cursor=parent.first}
            for((index,key)in keys.asReversed().withIndex()){
                assertEquals("Unexpected map before route step",scene.mapId,v.world.mapId)
                val beforeX=v.world.x/16;val beforeY=v.world.y/16;step(key)
                if(v.world.mapId!=scene.mapId){
                    val completed=v.world.lastCompletedStep!!
                    assertEquals(scene.mapId,completed.mapId);assertTrue(completed.transitioned)
                    assertEquals("Exit must be the requested original cell",tx to ty,completed.x to completed.y)
                    return
                }
                if(v.layer==GameView.Layer.DIALOGUE)assertEquals("Original story may only trigger at requested route endpoint",keys.lastIndex,index)
                if(island&&v.layer==GameView.Layer.DIALOGUE){
                    assertEquals(76,scene.mapId);assertEquals(12 to 12,tx to ty)
                    assertEquals(9 to 11,v.world.x/16 to v.world.y/16);return
                }
                val expectedX=beforeX+if(key==Key.RIGHT)1 else if(key==Key.LEFT)-1 else 0
                val expectedY=beforeY+if(key==Key.DOWN)1 else if(key==Key.UP)-1 else 0
                // A held real joystick can travel farther under runner load.
                // Replan from its observed legal location instead of replaying stale keys.
                if(v.world.x/16!=expectedX||v.world.y/16!=expectedY)break
            }
            }
        }
        fun dialogue(){repeat(24){if(v.layer==GameView.Layer.DIALOGUE)tap(v,Pair(v.width*.5f,v.height*.5f))}}
        fun talk(){tap(v,center(layoutFor(v).buttons.getValue(Key.A)));dialogue()}
        fun enterService(caller:Int,room:Int,serviceNpc:String?=null):MapExit{
            assertEquals(caller,v.world.mapId);val entry=v.content.exits.first{it.fromMapId==caller&&it.toMapId==room}
            if(v.world.x/16==entry.triggerX&&v.world.y/16==entry.triggerY){
                // The actual return lands on the door. Walking to the same cell
                // is zero input; leave it normally before crossing it again.
                val departure=listOf(Key.DOWN,Key.LEFT,Key.RIGHT,Key.UP).first{key->
                    v.world.scene.probeFrom(entry.triggerX,entry.triggerY,key,v.world.terrainMode)==MovementBlock.NONE}
                step(departure);assertEquals(caller,v.world.mapId)
            }
            walkTo(entry.triggerX,entry.triggerY);assertEquals(room,v.world.mapId)
            assertEquals(InteriorContext(caller,entry.triggerX,entry.triggerY),v.currentSnapshot().interiorContext)
            val keeper=v.content.npcs.first{it.mapId==room&&(when(room){22->it.innId!=null;20->it.clinicId!=null;else->it.shopId!=null})&&(serviceNpc==null||it.id==serviceNpc)}
            walkTo(keeper.interactionCell!!.first,keeper.interactionCell.second);talk()
            assertEquals(if(room in listOf(20,22))GameView.Layer.INN else GameView.Layer.SHOP,v.layer)
            return entry
        }
        fun leaveService(entry:MapExit){
            if(v.layer==GameView.Layer.SHOP)tap(v,center(v.shopActionBounds(3)))
            if(v.layer==GameView.Layer.INN)instrumentation.runOnMainSync{v.handleBack()}
            val exit=v.content.exits.first{it.fromMapId==entry.toMapId&&it.returnToCaller}
            walkTo(exit.triggerX,exit.triggerY);assertEquals(entry.fromMapId,v.world.mapId)
            assertEquals(entry.triggerX,v.world.x/16);assertEquals(entry.triggerY,v.world.y/16)
            assertNull(v.currentSnapshot().interiorContext)
        }
        fun trade(id:String,buy:Boolean,count:Int=1){
            assertEquals(GameView.Layer.SHOP,v.layer);tap(v,center(v.shopActionBounds(if(buy)1 else 2)))
            scrollToShopItem(v,id);val selected=v.currentSnapshot();tap(v,center(v.shopItemBounds(id)));assertEquals(selected,v.currentSnapshot())
            val item=v.content.itemDefinitions.getValue(id);val price=if(buy)item.buyPrice!! else item.sellPrice!!
            repeat(count){val before=v.currentSnapshot();tap(v,center(v.shopActionBounds(4)))
                assertEquals(before.money+if(buy)-price else price,v.currentSnapshot().money)
                assertEquals((before.inventory[id]?:0)+if(buy)1 else -1,v.currentSnapshot().inventory[id]?:0)
                val after=v.currentSnapshot();send(v,MotionEvent.ACTION_UP,listOf(center(v.shopActionBounds(4))));assertEquals(after,v.currentSnapshot())}
        }
        fun inn(caller:Int=0){
            val entry=enterService(caller,22);val before=v.currentSnapshot()
            val price=v.content.inns.getValue(v.activeInnId!!).price
            assertTrue("Normal earnings must pay the original inn",before.money>=price)
            tap(v,center(v.innStayBounds()));assertEquals(before.money-price,v.currentSnapshot().money)
            assertEquals(v.currentSnapshot().characters.first().maxHp,v.currentSnapshot().characters.first().hp)
            assertEquals(before.inventory,v.currentSnapshot().inventory);assertEquals(before.flags,v.currentSnapshot().flags)
            leaveService(entry)
        }
        fun checkSourceUnchanged(){
            assertEquals("Read-only source checkpoint remains byte-exact",sourceHash,
                java.security.MessageDigest.getInstance("SHA-256").digest(sourceFile.readBytes()).joinToString(""){"%02x".format(it)})
        }
        fun persistChecked(){
            instrumentation.runOnMainSync{v.persistState()}
            val saved=instrumentation.targetContext.getSharedPreferences("opening-local-save",0).getString("saveJson",null)
            assertNotNull("Persist must write the actual normal state",saved)
            assertEquals(v.currentSnapshot(),SaveSnapshot.parse(saved!!))
        }
            fun fixedFerry(event:Int){
                val rule=v.content.ferries.getValue("rom.ferry.$event");val before=v.currentSnapshot()
                assertEquals(rule.start.mapId,before.mapId)
                assertEquals(rule.start.x to rule.start.y,before.x/16 to before.y/16)
                val expectedBegin=OriginalFerry.begin(before,rule,rule.start.direction,v.content.ferries.values)
                assertTrue(expectedBegin.applied)
                var expected=expectedBegin.snapshot
                for(i in rule.legs.indices){
                    val next=OriginalFerry.advance(expected,rule,i,v.content.ferries.values)
                    assertTrue(next.applied);expected=next.snapshot
                }
                assertFalse("Normal supply must survive original boat costs without state repair",OriginalStatus.allDisabled(expected.characters))
                val stick=layoutFor(v).stick;val middle=center(stick)
                val point=if(rule.start.direction==Key.LEFT)Pair(stick.x+2f,middle.second)else Pair(middle.first,stick.y+stick.h-2f)
                send(v,MotionEvent.ACTION_DOWN,listOf(middle));send(v,MotionEvent.ACTION_MOVE,listOf(point))
                val deadline=SystemClock.elapsedRealtime()+15000;var begun=false;var released=false;var done=false;var captured=false
                while(!done){
                    assertTrue("Actual fixed boat command did not settle",SystemClock.elapsedRealtime()<deadline)
                    var observed:SaveSnapshot?=null
                    instrumentation.runOnMainSync{
                        observed=v.currentSnapshot();val pending=OriginalFerry.pending(observed!!.flags,v.content.ferries.values)
                        if(pending!=null){begun=true
                            if(!released){dispatchTouchOnMain(v,MotionEvent.ACTION_UP,listOf(point));released=true}}
                        done=begun&&pending==null
                    }
                    if(begun&&!captured){state("normal-event$event-actual-boat-stage");captured=true}
                    assertEquals(GameView.Layer.MAP,v.layer)
                    if(!done)SystemClock.sleep(15)
                }
                if(!released)send(v,MotionEvent.ACTION_UP,listOf(point))
                assertEquals(expected,v.currentSnapshot())
                state("normal-event$event-exact-original-landing-no-free-heal")
            }
        // Read-only route planning across the actual four floors. Every edge
        // is normal joystick input, including independently recorded stairs.
        fun treeRouteTo(goal:Triple<Int,Int,Int>):List<Key>?{
            val start=Triple(v.world.mapId,v.world.x/16,v.world.y/16)
            val queue=java.util.ArrayDeque<Triple<Int,Int,Int>>();queue.add(start)
            val parents=mutableMapOf<Triple<Int,Int,Int>,Pair<Triple<Int,Int,Int>,Key>>()
            parents[start]=start to Key.UP;val currentFlags=v.currentSnapshot().flags
            while(queue.isNotEmpty()&&goal !in parents){
                val at=queue.removeFirst();val scene=v.content.sceneForState(at.first,currentFlags)!!
                for((key,d)in listOf(Key.UP to(0 to -1),Key.DOWN to(0 to 1),Key.LEFT to(-1 to 0),Key.RIGHT to(1 to 0))){
                    if(scene.probeFrom(at.second,at.third,key)!=MovementBlock.NONE)continue
                    val nx=at.second+d.first;val ny=at.third+d.second
                    val exit=v.content.exits.firstOrNull{it.fromMapId==at.first&&it.triggerX==nx&&it.triggerY==ny&&it.edgeDirection==null&&it.contactActorId==null}
                    val next=if(exit!=null)Triple(exit.toMapId,exit.spawnX,exit.spawnY)else Triple(at.first,nx,ny)
                    if(next.first !in 107..110||next in parents)continue
                    parents[next]=at to key;queue.add(next)
                }
            }
            if(goal !in parents)return null
            val keys=mutableListOf<Key>();var at=goal
            while(at!=start){val p=parents.getValue(at);keys.add(p.second);at=p.first}
            return keys.asReversed()
        }
        fun treeWalkTo(goal:Triple<Int,Int,Int>){
            var replans=0
            while(Triple(v.world.mapId,v.world.x/16,v.world.y/16)!=goal){
                assertTrue("Tree route must converge using normal inputs",replans++<512)
                val keys=treeRouteTo(goal);assertNotNull("No original floor route to $goal",keys)
                // Replan after every real edge: runner load may hold a stick
                // longer, and encounters can change party/flag conditions.
                step(keys!!.first())
            }
        }
        fun interactTreeNpc(id:String){
            val npc=v.content.npcs.single{it.id==id}
            val goal=listOf(0 to 1,1 to 0,-1 to 0,0 to -1).map{Triple(npc.mapId,npc.x+it.first,npc.y+it.second)}
                .firstOrNull{treeRouteTo(it)!=null}
            assertNotNull("Original NPC must have a reachable adjacent cell",goal)
            treeWalkTo(goal!!);talk()
        }
        if(queen){
            normalQueenStage(v,cold,source,::walkTo,::step,::state,::dialogue,::finishFight,::persistChecked)
            checkSourceUnchanged();assertEquals(if(cold)0 else 1,bossEntries)
            instrumentation.runOnMainSync{activity.finish()};return
        }
        if(night8){
            normalNightEightStage(v,cold,source,::walkTo,::step,::state,::dialogue,::persistChecked)
            checkSourceUnchanged();assertEquals(0,bossEntries)
            instrumentation.runOnMainSync{activity.finish()};return
        }
        if(village6){
            normalVillageSixStage(v,cold,source,::walkTo,::step,::state,
                {caller,room,npc->enterService(caller,room,npc)},::leaveService,::trade,::dialogue,::persistChecked)
            checkSourceUnchanged();assertEquals(0,bossEntries)
            instrumentation.runOnMainSync{activity.finish()};return
        }
        if(cave87){
            normalCave87Stage(v,cold,source,::walkTo,::step,::fixedFerry,::state,::finishFight,
                ::inn,{caller,room->enterService(caller,room)},::leaveService,::trade,::persistChecked)
            checkSourceUnchanged();assertEquals(if(cold)0 else 1,bossEntries)
            instrumentation.runOnMainSync{activity.finish()};return
        }
        if(island){
            assertEquals(listOf("nezha","xiaolongnv","yangjian"),source.characters.map{it.id})
            val victory="rom.map.76.flag.128";val witness="rom.global.7c6.16"
            state(if(cold)"cold-exact-island-victory-save"else"verified-ferry-source-no-state-grants")
            // Read-only plane-aware planning, every edge is a real joystick gesture.
            data class Node(val map:Int,val x:Int,val y:Int,val plane:Int)
            fun route(goal:Triple<Int,Int,Int>):List<Key>?{
                val begin=Node(v.world.mapId,v.world.x/16,v.world.y/16,v.world.terrainMode)
                val flags=v.currentSnapshot().flags;val q=java.util.ArrayDeque<Node>();q.add(begin)
                val parents=mutableMapOf<Node,Pair<Node,Key>>();parents[begin]=begin to Key.UP
                var end:Node?=null
                while(q.isNotEmpty()){
                    val a=q.removeFirst();if(Triple(a.map,a.x,a.y)==goal){end=a;break}
                    val m=v.content.sceneForState(a.map,flags)!!
                    for((key,d)in listOf(Key.UP to(0 to -1),Key.DOWN to(0 to 1),Key.LEFT to(-1 to 0),Key.RIGHT to(1 to 0))){
                        if(m.probeFrom(a.x,a.y,key,a.plane)!=MovementBlock.NONE)continue
                        val nx=a.x+d.first;val ny=a.y+d.second
                        val exit=v.content.exits.firstOrNull{it.fromMapId==a.map&&it.triggerX==nx&&it.triggerY==ny&&it.edgeDirection==null}
                        val next=if(exit!=null)Node(exit.toMapId,exit.spawnX,exit.spawnY,0)
                            else Node(a.map,nx,ny,m.terrainDecision(a.x,a.y,key,a.plane).nextMode)
                        if(next.map !in 76..79||next in parents)continue
                        if(Triple(next.map,next.x,next.y)!=goal&&v.content.battle!!.storyBattles.values.any{
                            it.triggersAt(next.map,next.x,next.y,flags)})continue
                        parents[next]=a to key;q.add(next)
                    }
                }
                var a=end?:return null;val keys=mutableListOf<Key>()
                while(a!=begin){val previous=parents.getValue(a);keys.add(previous.second);a=previous.first}
                return keys.asReversed()
            }
            fun islandWalk(goal:Triple<Int,Int,Int>){
                var n=0
                while(Triple(v.world.mapId,v.world.x/16,v.world.y/16)!=goal){
                    assertTrue("Original island route did not settle; no coordinate repair",n++<512)
                    val keys=route(goal);assertNotNull("No real island route to $goal",keys)
                    assertTrue(keys!!.isNotEmpty());step(keys.first())
                }
            }
            fun resident(id:String){
                val npc=v.content.npcs.single{it.id==id};val rule=npc.originalTalk!!
                val goal=listOf(0 to 1,1 to 0,-1 to 0,0 to -1).map{Triple(78,npc.x+it.first,npc.y+it.second)}.firstOrNull{route(it)!=null}
                assertNotNull("Original resident must have a real reachable interaction cell",goal);islandWalk(goal!!)
                val before=v.currentSnapshot();val expected=OriginalNpcTalk.begin(before,rule)
                val actualUi=GameView::class.java.getDeclaredField("ui").apply{isAccessible=true}.get(v) as ScreenLayout
                val point=actualUi.worldToScreen((npc.x*16+8).toFloat(),(npc.y*16+8).toFloat(),v.world.camera(actualUi.viewWidth,actualUi.viewHeight))
                tap(v,point);assertEquals(GameView.Layer.DIALOGUE,v.layer)
                assertEquals(expected.snapshot.flags,v.currentSnapshot().flags)
                assertEquals(before.inventory,v.currentSnapshot().inventory);assertEquals(before.money,v.currentSnapshot().money)
                assertEquals(before.characters,v.currentSnapshot().characters);state("normal-original-resident-$id-${expected.nextDialogue}")
                dialogue();val once=v.currentSnapshot();tap(v,point);dialogue()
                assertEquals(once.flags,v.currentSnapshot().flags);assertEquals(once.money,v.currentSnapshot().money)
                assertEquals(once.inventory,v.currentSnapshot().inventory);assertEquals(once.characters,v.currentSnapshot().characters)
            }
            if(!cold){
                assertEquals(79,v.world.mapId);assertTrue(source.flags[victory]!=true)
                // Source remains the actual same-candidate normal ferry save.
                // Advice is optional; no possession lock is added to the map.
                walkTo(7,12);assertEquals(16,v.world.mapId)
                walkTo(150,135);fixedFerry(46);assertEquals(4,v.world.mapId)
                walkTo(15,29);step(Key.DOWN);assertEquals(16,v.world.mapId)
                walkTo(183,143);assertEquals(99,v.world.mapId);state("normal-five-dragon-original-entry")
                walkTo(13,11);assertEquals(163,v.world.mapId)
                assertTrue(v.currentSnapshot().flags[OriginalNpcTalk.TEACHER_CONTEXT_FLAG]==true)
                assertFalse(10*16+7 in v.world.scene.dynamicObjectCells)
                assertTrue(9*16+7 in v.world.scene.dynamicObjectCells)
                val disciple=v.content.npcsForState(163,v.currentSnapshot().flags).single{it.id=="rom.npc.163.0"}
                assertEquals(7 to 9,disciple.x to disciple.y);assertEquals("rom.dialogue.173.1",disciple.firstDialogue)
                walkTo(7,5);val teacherBefore=v.currentSnapshot()
                assertTrue(teacherBefore.flags["rom.map.163.flag.2"]!=true)
                tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
                assertEquals(GameView.Layer.DIALOGUE,v.layer)
                assertEquals(1,v.currentSnapshot().inventory["rom.special.9"])
                assertEquals(teacherBefore.money,v.currentSnapshot().money)
                assertEquals(teacherBefore.characters,v.currentSnapshot().characters)
                state("normal-teacher-original-gift-before-dialogue");dialogue()
                val teacherOnce=v.currentSnapshot();talk();assertEquals(teacherOnce,v.currentSnapshot())
                state("normal-teacher-repeat-no-second-gift")
                walkTo(7,14);assertEquals(99,v.world.mapId);walkTo(8,51);assertEquals(16,v.world.mapId)
                walkTo(146,150);assertEquals(4,v.world.mapId)
                inn(4)
                val supplyEntry=enterService(4,19)
                val herbs=(10-(v.currentSnapshot().inventory[HerbUse.ID]?:0)).coerceAtLeast(0)
                if(herbs>0)trade(HerbUse.ID,true,herbs)
                leaveService(supplyEntry);walkTo(11,3);fixedFerry(45)
                for(key in listOf(Key.UP,Key.UP,Key.RIGHT,Key.UP,Key.UP,Key.UP,Key.UP,Key.UP,Key.RIGHT,Key.RIGHT))step(key)
                assertEquals(79,v.world.mapId);assertEquals(1,v.currentSnapshot().inventory["rom.special.9"])
                state("normal-earned-gift-and-supply-original-island-return")
                islandWalk(Triple(78,1,12));state("normal-original-first-stair-floor78")
                resident("rom.npc.78.0");resident("rom.npc.78.1")
                islandWalk(Triple(77,3,7));state("normal-original-second-stair-floor77")
                islandWalk(Triple(76,14,13));state("normal-original-floor76-four-actors-and-six-chests")
                walkTo(12,12);assertEquals(GameView.Layer.DIALOGUE,v.layer)
                val story=v.content.battle!!.storyBattles.getValue("rom.npc.76.0");val intro=story.intro!!
                assertEquals(9 to 11,v.world.x/16 to v.world.y/16)
                for(id in intro.continuation.dialogueIds){
                    assertEquals(GameView.Layer.DIALOGUE,v.layer);assertEquals(id,intro.pendingDialogue(v.currentSnapshot().flags))
                    state("normal-original-intro-$id");tap(v,Pair(v.width*.5f,v.height*.5f))
                }
                assertEquals(GameView.Layer.BATTLE,v.layer);finishFight()
                assertEquals(GameView.Layer.MAP,v.layer);assertEquals(1,bossEntries)
                assertEquals(true,v.currentSnapshot().flags[victory]);assertEquals(true,v.currentSnapshot().flags[witness])
                assertFalse(v.currentSnapshot().flags[OriginalNpcTalk.TEACHER_CONTEXT_FLAG]==true)
                assertTrue(v.currentSnapshot().flags[story.pendingFlag]!=true)
                state("normal-four-villains-victory-no-invented-postdialogue")
                for(npc in v.content.npcs.filter{it.mapId==76&&(it.treasure!=null||it.moneyTreasure!=null)}){
                    val goal=listOf(0 to 1,1 to 0,-1 to 0,0 to -1).map{Triple(76,npc.x+it.first,npc.y+it.second)}.firstOrNull{route(it)!=null}
                    assertNotNull("Original chest must be normally reachable",goal);islandWalk(goal!!)
                    val before=v.currentSnapshot();val itemResult=npc.treasure?.let{WorldItems.openTreasure(before,it,v.content.itemDefinitions.getValue(it.itemId))}
                    val moneyResult=npc.moneyTreasure?.let{WorldItems.openMoneyTreasure(before,it)}
                    val actualUi=GameView::class.java.getDeclaredField("ui").apply{isAccessible=true}.get(v) as ScreenLayout
                    val screen=actualUi.worldToScreen((npc.x*16+8).toFloat(),(npc.y*16+8).toFloat(),v.world.camera(actualUi.viewWidth,actualUi.viewHeight));tap(v,screen);dialogue()
                    if(itemResult!=null){assertEquals(itemResult.inventory,v.currentSnapshot().inventory);assertEquals(itemResult.flags,v.currentSnapshot().flags);assertEquals(before.money,v.currentSnapshot().money)}
                    else{assertNotNull(moneyResult);assertEquals(moneyResult!!.snapshot.inventory,v.currentSnapshot().inventory);assertEquals(moneyResult.snapshot.flags,v.currentSnapshot().flags);assertEquals(moneyResult.snapshot.money,v.currentSnapshot().money)}
                    assertEquals(before.characters,v.currentSnapshot().characters)
                    val once=v.currentSnapshot();tap(v,screen);dialogue()
                    assertEquals(once.inventory,v.currentSnapshot().inventory);assertEquals(once.flags,v.currentSnapshot().flags);assertEquals(once.money,v.currentSnapshot().money)
                    state("normal-original-chest-${npc.id}-once-or-capacity-failure")
                }
                islandWalk(Triple(78,1,12));resident("rom.npc.78.0");resident("rom.npc.78.1")
                islandWalk(Triple(76,9,11));checkSourceUnchanged();persistChecked()
                File(root,"world-island-expected-save.json").writeText(v.currentSnapshot().json().toString())
                state("normal-island-original-once-state-and-save")
            }else{
                assertEquals(76,v.world.mapId);assertEquals(true,source.flags[victory]);assertEquals(true,source.flags[witness])
                islandWalk(Triple(76,12,12));assertEquals(GameView.Layer.MAP,v.layer)
                assertEquals(source.flags,v.currentSnapshot().flags);assertEquals(source.inventory,v.currentSnapshot().inventory)
                assertEquals(source.money,v.currentSnapshot().money);state("cold-trigger-cell-no-repeat-battle-or-reward")
                islandWalk(Triple(77,1,2));state("cold-independent-original-floor76-return")
                islandWalk(Triple(76,14,13));state("cold-original-reentry-actors-stay-removed-chests-stay-once")
                assertEquals(source.flags,v.currentSnapshot().flags);assertEquals(source.inventory,v.currentSnapshot().inventory)
                checkSourceUnchanged();persistChecked()
            }
            assertEquals(if(cold)0 else 1,bossEntries);instrumentation.runOnMainSync{activity.finish()};return
        }
        if(ferry){
            normalFerryStage(v,cold,source,::walkTo,::step,::fixedFerry,::state,{threeActorBattles},::persistChecked)
            checkSourceUnchanged();assertEquals(0,bossEntries)
            instrumentation.runOnMainSync{activity.finish()};return
        }
        if(village4||village5){
            normalWestVillageServicesStage(v,cold,source,village5,::walkTo,::step,::state,
                {caller,room,npc->enterService(caller,room,npc)},::leaveService,::trade,::treeWalkTo,
                ::dialogue,::checkSourceUnchanged,::persistChecked)
            assertEquals(0,bossEntries);instrumentation.runOnMainSync{activity.finish()};return
        }
        if(yangJoin){
            assertEquals(1,source.inventory[OriginalYangJoin.ITEM_ID]);assertEquals(true,source.flags["rom.global.7c8.1"])
            state(if(cold)"cold-exact-normal-three-party-save"else"verified-teacher-gift-source-no-state-grants")
            if(!cold){
                assertEquals(171,v.world.mapId);assertEquals(listOf("nezha","xiaolongnv"),source.characters.map{it.id})
                walkTo(7,13);step(Key.DOWN);assertEquals(101,v.world.mapId)
                walkTo(8,51);assertEquals(16,v.world.mapId);assertEquals(213 to 155,v.world.x/16 to v.world.y/16)
                walkTo(168,161);walkTo(168,149);walkTo(169,149);step(Key.RIGHT);assertEquals(107,v.world.mapId)
                treeWalkTo(Triple(110,7,6));step(Key.LEFT)
                assertEquals(7 to 6,v.world.x/16 to v.world.y/16);assertEquals(Key.LEFT,v.world.direction)
                state("normal-real-return-to-yang-facing-no-position-repair")
                val before=v.currentSnapshot();tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2))
                scrollToItem(v,OriginalYangJoin.ITEM_ID);tap(v,center(v.panelItemBounds(OriginalYangJoin.ITEM_ID)))
                assertEquals(before,v.currentSnapshot());tap(v,center(v.panelPrimaryBounds()))
                assertEquals(GameView.Layer.DIALOGUE,v.layer)
                val joined=v.currentSnapshot();val expected=OriginalYangJoin.begin(before,
                    v.content.itemDefinitions.getValue(OriginalYangJoin.ITEM_ID),v.content.worldItemTargets().single{it.id=="rom.npc.110.0"},v.content.joinCharacters["yangjian"],true)
                assertTrue(expected.applied);assertEquals(expected.snapshot,joined)
                assertEquals(before.characters,joined.characters.take(2));assertEquals(before.inventory,joined.inventory)
                assertEquals(3,joined.characters.size);assertEquals(96,joined.characters[2].strength);assertEquals(28,joined.characters[2].agility)
                state("normal-item19-explicit-submit-joins-before-first-text")
                send(v,MotionEvent.ACTION_UP,listOf(Pair(v.width*.5f,v.height*.5f)));assertEquals(joined,v.currentSnapshot())
                dialogue();assertEquals(GameView.Layer.MAP,v.layer);assertEquals(true,v.currentSnapshot().flags["rom.map.110.flag.128"])
                assertEquals(1,v.currentSnapshot().inventory[OriginalYangJoin.ITEM_ID])
                state("normal-both-original-dialogues-complete-no-free-reward")
                walkTo(6,6);assertEquals(6 to 6,v.world.x/16 to v.world.y/16)
                assertFalse(v.content.npcVisible(v.content.npcs.single{it.id=="rom.npc.110.0"},v.currentSnapshot().flags))
                state("normal-empty-context207-removes-actor-and-collision")
                var steps=0
                while(threeActorVictories==0){
                    assertTrue("Original natural three-party encounter must occur without forcing RNG",steps++<160)
                    walkTo(7,6);walkTo(6,6)
                }
                assertTrue(threeActorBattles>0);assertEquals(1,v.currentSnapshot().characters.count{it.id=="yangjian"})
                checkSourceUnchanged();persistChecked();File(root,"world-yang-join-expected-save.json").writeText(v.currentSnapshot().json().toString())
                state("normal-three-actor-battle-own-growth-and-save")
            }else{
                assertEquals(110,v.world.mapId);assertEquals(listOf("nezha","xiaolongnv","yangjian"),source.characters.map{it.id})
                assertEquals(true,source.flags["rom.map.110.flag.128"])
                assertFalse(v.content.npcVisible(v.content.npcs.single{it.id=="rom.npc.110.0"},source.flags))
                treeWalkTo(Triple(107,7,13));step(Key.DOWN);assertEquals(16,v.world.mapId)
                assertEquals(169 to 149,v.world.x/16 to v.world.y/16);state("cold-three-party-original-tree-return")
                step(Key.RIGHT);assertEquals(107,v.world.mapId);treeWalkTo(Triple(110,6,6))
                assertEquals(1,v.currentSnapshot().characters.count{it.id=="yangjian"})
                assertEquals(1,v.currentSnapshot().inventory[OriginalYangJoin.ITEM_ID])
                assertEquals(true,v.currentSnapshot().flags["rom.map.110.flag.128"])
                assertTrue(v.currentSnapshot().flags["rom.event.110.29.dialogue.pending"]!=true)
                checkSourceUnchanged();persistChecked();state("cold-original-reentry-no-duplicate-join-or-reward")
            }
            assertEquals(0,bossEntries);instrumentation.runOnMainSync{activity.finish()};return
        }
        if(room171){
            assertEquals(true,source.flags["rom.global.7c8.1"])
            state(if(cold)"cold-exact-normal-teacher-save"else"verified-tree-source-no-state-grants")
            if(!cold){
                assertEquals(110,v.world.mapId);assertEquals(0,source.inventory["rom.special.19"]?:0)
                treeWalkTo(Triple(107,7,13));step(Key.DOWN)
                assertEquals(16,v.world.mapId);assertEquals(169 to 149,v.world.x/16 to v.world.y/16)
                walkTo(168,149);walkTo(168,161);walkTo(213,155)
                assertEquals(101,v.world.mapId);assertEquals(8 to 51,v.world.x/16 to v.world.y/16)
                walkTo(32,13);step(Key.UP)
                assertEquals(171,v.world.mapId);assertEquals(7 to 14,v.world.x/16 to v.world.y/16)
                state("normal-forest-original-door-and-room")
                walkTo(7,5);step(Key.UP)
                assertEquals(7 to 5,v.world.x/16 to v.world.y/16)
                val before=v.currentSnapshot();val npc=v.content.npcs.single{it.id=="rom.npc.171.1"}
                val item=v.content.itemDefinitions.getValue("rom.special.19")
                val expected=OriginalNpcTalk.begin(before,npc.originalTalk!!,item)
                assertEquals(1,expected.snapshot.inventory[item.id])
                tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
                assertEquals(GameView.Layer.DIALOGUE,v.layer)
                assertEquals(expected.snapshot.inventory,v.currentSnapshot().inventory)
                assertEquals(before.characters,v.currentSnapshot().characters)
                assertEquals(before.money,v.currentSnapshot().money);assertEquals(before.flags,v.currentSnapshot().flags)
                state("normal-teacher-gift-before-dialogue-no-free-join")
                dialogue();val once=v.currentSnapshot();talk()
                assertEquals(once,v.currentSnapshot())
                state("normal-teacher-owned-repeat-no-duplicate-gift")
                checkSourceUnchanged();persistChecked()
                File(root,"world-room171-expected-save.json").writeText(v.currentSnapshot().json().toString())
            }else{
                assertEquals(171,v.world.mapId);assertEquals(1,source.inventory["rom.special.19"])
                walkTo(7,5);step(Key.UP);val before=v.currentSnapshot();talk()
                assertEquals(before,v.currentSnapshot());state("cold-repeat-no-reward-or-party-change")
                walkTo(7,13);step(Key.DOWN)
                assertEquals(101,v.world.mapId);assertEquals(32 to 12,v.world.x/16 to v.world.y/16)
                state("cold-original-room-return-to-forest")
                step(Key.DOWN);walkTo(32,12);assertEquals(171,v.world.mapId)
                walkTo(7,5);step(Key.UP);val beforeRepeat=v.currentSnapshot();talk()
                assertEquals(beforeRepeat,v.currentSnapshot())
                checkSourceUnchanged();persistChecked();state("cold-normal-reentry-no-duplicate-gift-and-save")
            }
            assertEquals(0,bossEntries);instrumentation.runOnMainSync{activity.finish()};return
        }
        if(tree107){
            assertEquals(true,source.flags["rom.map.86.flag.128"])
            state(if(cold)"cold-exact-normal-tree-save"else"verified-bridge-source-no-state-grants")
            if(!cold){
                assertEquals(16,v.world.mapId);assertEquals(238 to 160,v.world.x/16 to v.world.y/16)
                walkTo(239,160);assertEquals(3,v.world.mapId)
                val service=enterService(3,19);val herb=v.content.itemDefinitions.getValue(HerbUse.ID)
                val available=minOf(herb.maxCount-(v.currentSnapshot().inventory[HerbUse.ID]?:0),v.currentSnapshot().money/herb.buyPrice!!)
                if(available>0)trade(HerbUse.ID,true,available)
                leaveService(service);inn(3)
                val exit=v.content.exits.first{it.fromMapId==3&&it.toMapId==16};walkTo(exit.triggerX,exit.triggerY)
                walkTo(235,159);walkTo(231,159);walkTo(230,161);walkTo(168,161);walkTo(168,149);walkTo(169,149)
                val before=v.currentSnapshot();step(Key.RIGHT)
                assertEquals(107,v.world.mapId);assertEquals(7 to 14,v.world.x/16 to v.world.y/16)
                assertEquals(before.inventory,v.currentSnapshot().inventory);assertEquals(before.flags,v.currentSnapshot().flags)
                state("normal-real-western-mainland-actor-contact-entry")
                for(id in listOf("rom.npc.107.1","rom.npc.107.0","rom.npc.108.0")){
                    val npc=v.content.npcs.single{it.id==id};val t=npc.treasure!!;val item=v.content.itemDefinitions.getValue(t.itemId)
                    val goal=listOf(0 to 1,1 to 0,-1 to 0,0 to -1).map{Triple(npc.mapId,npc.x+it.first,npc.y+it.second)}.first{treeRouteTo(it)!=null}
                    treeWalkTo(goal);val beforeChest=v.currentSnapshot();val expected=WorldItems.openTreasure(beforeChest,t,item)
                    talk();assertEquals(expected.inventory,v.currentSnapshot().inventory);assertEquals(expected.flags,v.currentSnapshot().flags)
                    assertEquals(beforeChest.characters,v.currentSnapshot().characters);assertEquals(beforeChest.money,v.currentSnapshot().money)
                    val once=v.currentSnapshot();talk();assertEquals(once.inventory,v.currentSnapshot().inventory);assertEquals(once.flags,v.currentSnapshot().flags)
                    state("normal-original-tree-chest-$id-applied-${expected.applied}")
                }
                treeWalkTo(Triple(110,7,6));val beforeTalk=v.currentSnapshot()
                assertEquals(0,beforeTalk.inventory["rom.special.19"]?:0)
                interactTreeNpc("rom.npc.110.0")
                assertEquals(true,v.currentSnapshot().flags["rom.global.7c8.1"])
                assertTrue(v.currentSnapshot().flags["rom.map.110.flag.2"]!=true)
                assertEquals(beforeTalk.inventory,v.currentSnapshot().inventory);assertEquals(beforeTalk.characters,v.currentSnapshot().characters)
                state("normal-four-floors-and-yang-initial-witness-no-free-join")
                checkSourceUnchanged();persistChecked();File(root,"world-tree107-expected-save.json").writeText(v.currentSnapshot().json().toString())
            }else{
                assertEquals(110,v.world.mapId);assertEquals(true,source.flags["rom.global.7c8.1"])
                interactTreeNpc("rom.npc.110.0");assertEquals(source.flags,v.currentSnapshot().flags)
                treeWalkTo(Triple(107,7,13));step(Key.DOWN)
                assertEquals(16,v.world.mapId);assertEquals(169 to 149,v.world.x/16 to v.world.y/16)
                state("cold-original-independent-tree-return-to-mainland")
                val before=v.currentSnapshot();step(Key.RIGHT);assertEquals(107,v.world.mapId)
                assertEquals(before.flags,v.currentSnapshot().flags);assertEquals(before.inventory,v.currentSnapshot().inventory)
                treeWalkTo(Triple(107,7,13));step(Key.DOWN);assertEquals(16,v.world.mapId)
                checkSourceUnchanged();persistChecked();state("cold-reentry-no-duplicate-grants-and-save")
            }
            assertEquals(0,bossEntries);instrumentation.runOnMainSync{activity.finish()};return
        }
        if(forest101){
            assertEquals(true,source.flags["rom.map.86.flag.128"])
            state(if(cold)"cold-exact-normal-owned-save"else"verified-bridge-source-no-state-grants")
            if(!cold){
                assertEquals(16,v.world.mapId);assertEquals(238 to 160,v.world.x/16 to v.world.y/16)
                // Restock only via the existing actual village shop and paid inn.
                walkTo(239,160);assertEquals(3,v.world.mapId)
                val service=enterService(3,19)
                val herb=v.content.itemDefinitions.getValue(HerbUse.ID)
                val available=minOf(herb.maxCount-(v.currentSnapshot().inventory[HerbUse.ID]?:0),v.currentSnapshot().money/herb.buyPrice!!)
                if(available>0)trade(HerbUse.ID,true,available)
                leaveService(service);inn(3)
                val villageReturn=v.content.exits.first{it.fromMapId==3&&it.toMapId==16}
                walkTo(villageReturn.triggerX,villageReturn.triggerY);assertEquals(16,v.world.mapId)
                walkTo(235,159);walkTo(231,159);walkTo(213,155)
                assertEquals(101,v.world.mapId);assertEquals(8 to 51,v.world.x/16 to v.world.y/16)
                state("real-continent-door-and-original-forest")
                walkTo(32,13);assertEquals(101,v.world.mapId)
                state("normal-forest-full-path-to-next-room-approach")
                assertTrue("Original natural encounters must remain enabled",fights>0)
                checkSourceUnchanged();persistChecked()
                File(root,"world-forest101-expected-save.json").writeText(v.currentSnapshot().json().toString())
                state("normal-forest-save-at-original-next-door-approach")
            }else{
                assertEquals(101,v.world.mapId);assertEquals(32 to 13,v.world.x/16 to v.world.y/16)
                walkTo(8,51);assertEquals(16,v.world.mapId)
                assertEquals(213 to 155,v.world.x/16 to v.world.y/16)
                state("cold-forest-original-return-to-continent")
                walkTo(238,160);for((flag,value)in source.flags)assertEquals(value,v.currentSnapshot().flags[flag])
                checkSourceUnchanged();persistChecked();state("cold-return-and-owned-save")
            }
            assertEquals(0,bossEntries);instrumentation.runOnMainSync{activity.finish()};return
        }
        if(continentBridge){
            assertEquals(16,v.world.mapId);assertEquals(true,source.flags["rom.map.86.flag.128"])
            assertEquals(238 to 160,v.world.x/16 to v.world.y/16)
            state(if(cold)"cold-exact-normal-owned-save"else"verified-medical-source-no-state-grants")
            walkTo(235,159);state("normal-original-eastern-bridge-end")
            walkTo(231,159);state("normal-crossed-class16-bridge-west")
            walkTo(235,159);state("normal-original-bridge-return-east")
            if(!cold){
                // Ordinary joystick movement in the actual original rectangle;
                // neither encounter counter nor RNG/party state is overwritten.
                var traversals=0
                while(fights==0&&traversals<80){
                    walkTo(if(traversals%2==0)231 else 235,159);traversals++
                }
                assertTrue("Actual zone16 encounter must be observed without forcing RNG",fights>0)
                state("normal-zone16-natural-battle-and-owned-result")
            }
            walkTo(238,160);assertEquals(16,v.world.mapId)
            for((flag,value)in source.flags)assertEquals(value,v.currentSnapshot().flags[flag])
            assertEquals(0,bossEntries);checkSourceUnchanged();persistChecked()
            if(!cold)File(root,"world-continent-bridge-expected-save.json").writeText(v.currentSnapshot().json().toString())
            state(if(cold)"cold-bridge-real-both-directions-and-save"else"normal-bridge-zone16-and-save")
            instrumentation.runOnMainSync{activity.finish()};return
        }
        if(medical){
            assertEquals(16,v.world.mapId);assertEquals(true,source.flags["rom.map.86.flag.128"])
            state(if(cold)"cold-exact-medical-source-no-state-grants"else"verified-village3-source-owned-state")
            walkTo(239,160);assertEquals(3,v.world.mapId)
            val summary=JSONObject().put("sourceSha256",sourceHash).put("normalInputsOnly",true)
                .put("revivalNormal","NOT_RUN_NO_DEAD_TARGET").put("poisonNormal","NOT_RUN_NO_TARGET")
                .put("confusionNormal","NOT_RUN_NO_TARGET")
            for((role,npc)in listOf("revival" to "rom.npc.20.1","care" to "rom.npc.20.0")){
                val entry=enterService(3,20,npc);assertEquals("rom.clinic.3.$role",v.activeClinicId)
                state("normal-medical-$role-real-door-and-service")
                val definition=v.content.clinics.getValue(v.activeClinicId!!)
                val options=if(role=="revival")listOf<String?>(null)else listOf("poison","confusion")
                for(option in options){
                    if(option!=null)tap(v,center(v.clinicTreatmentBounds(option)))
                    val before=v.currentSnapshot()
                    val legal=before.characters.firstOrNull{if(role=="revival")ClinicRevival.eligible(it,definition)
                        else ClinicCare.eligible(it,definition.treatments.single{t->t.id==option})}
                    val selected=legal?:before.characters.first()
                    tap(v,center(v.clinicTargetBounds(selected.id)));assertEquals(before,v.currentSnapshot())
                    state("normal-medical-$role-${option?:"revive"}-selected-readonly")
                    if(!cold&&legal!=null){
                        val expected=if(role=="revival")ClinicRevival.apply(before.money,before.characters,legal.id,definition)
                            else ClinicCare.apply(before.money,before.characters,legal.id,definition,option!!)
                        assertTrue("Normal earnings must fund actual requested medical service",expected.applied)
                        tap(v,center(v.clinicReviveBounds()));assertEquals(GameView.Layer.MAP,v.layer)
                        assertEquals(expected.money,v.currentSnapshot().money);assertEquals(expected.characters,v.currentSnapshot().characters)
                        assertEquals(before.inventory,v.currentSnapshot().inventory);assertEquals(before.flags,v.currentSnapshot().flags)
                        summary.put(if(role=="revival")"revivalNormal"else"${option}Normal","PASS")
                        state("normal-medical-$role-${option?:"revive"}-actual-owned-target-treated")
                        // A second supported condition needs another real conversation.
                        if(option!=options.last()){
                            val keeper=v.content.npcs.single{it.id==npc};walkTo(keeper.interactionCell!!.first,keeper.interactionCell.second)
                            talk();assertEquals(GameView.Layer.INN,v.layer)
                        }
                    }else{
                        if(!cold){tap(v,center(v.clinicReviveBounds()));assertEquals(before,v.currentSnapshot())}
                        state("normal-medical-$role-${option?:"revive"}-unneeded-or-cold-cancel-no-charge")
                    }
                }
                leaveService(entry);assertEquals(3,v.world.mapId)
            }
            walkTo(15,29);step(Key.DOWN);assertEquals(16,v.world.mapId)
            assertEquals(239 to 160,v.world.x/16 to v.world.y/16)
            step(Key.LEFT);assertEquals(238 to 160,v.world.x/16 to v.world.y/16)
            for((flag,value)in source.flags)assertEquals(value,v.currentSnapshot().flags[flag])
            assertEquals(0,bossEntries);checkSourceUnchanged();persistChecked()
            if(!cold){
                File(root,"world-medical-expected-save.json").writeText(v.currentSnapshot().json().toString())
                File(root,"world-medical-normal-summary.json").writeText(summary.toString())
            }
            state(if(cold)"cold-medical-reentry-cancel-and-owned-save-kept"else"normal-medical-entries-real-state-and-return-saved")
            instrumentation.runOnMainSync{activity.finish()};return
        }
        if(village3){
            assertEquals(16,v.world.mapId);assertEquals(true,source.flags["rom.map.86.flag.128"])
            assertEquals(listOf("nezha","xiaolongnv"),source.characters.map{it.id})
            state(if(cold)"cold-exact-owned-party-inventory-and-flags" else "verified-rebirth-source-no-state-grants")
            walkTo(239,160);assertEquals(3,v.world.mapId)
            assertEquals(15 to 29,v.world.x/16 to v.world.y/16);state("normal-world-to-village3-original-door")
            if(!cold){
                for((room,id)in listOf(17 to "rom.weapon.6",18 to "rom.armor.12",19 to "rom.medicine.10")){
                    val entry=enterService(3,room);state("normal-shop-$room-open")
                    trade(id,true);state("normal-shop-$room-bought")
                    trade(id,false);state("normal-shop-$room-sold");leaveService(entry)
                }
                val entry=enterService(3,17);trade("rom.weapon.6",true);leaveService(entry)
                val before=v.currentSnapshot();tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2))
                tap(v,center(v.panelCharacterBounds("nezha")));scrollToItem(v,"rom.weapon.6")
                tap(v,center(v.panelItemBounds("rom.weapon.6")));assertEquals(before,v.currentSnapshot())
                tap(v,center(v.panelPrimaryBounds()));val expected=OpeningEquipment.replace(before.characters.single{it.id=="nezha"},before.inventory,
                    v.content.equipmentDefinitions.getValue("rom.weapon.6"),v.content.equipmentDefinitions.values)!!
                assertEquals(expected.first,v.currentSnapshot().characters.single{it.id=="nezha"})
                assertEquals(expected.second,v.currentSnapshot().inventory)
                instrumentation.runOnMainSync{v.handleBack()};state("normal-original-permitted-weapon6-atomic-replacement")
                for(actor in v.currentSnapshot().characters.filter{it.statusMask and OriginalStatus.POISON!=0})medicine(AntidoteUse.ID,actor.id)
                inn(3);assertTrue(v.currentSnapshot().characters.all{it.hp==it.maxHp&&it.mp==it.maxMp})
                state("normal-original40-inn-all-actor-recovery-and-return")
                walkTo(16,20);val beforeTalk=v.currentSnapshot()
                tap(v,center(layoutFor(v).buttons.getValue(Key.A)));assertEquals(GameView.Layer.DIALOGUE,v.layer)
                state("normal-original-village3-resident-dialogue");dialogue()
                assertEquals(beforeTalk.money,v.currentSnapshot().money);assertEquals(beforeTalk.inventory,v.currentSnapshot().inventory)
                assertEquals(beforeTalk.characters,v.currentSnapshot().characters)
            }else{
                val entry=enterService(3,22);val before=v.currentSnapshot()
                instrumentation.runOnMainSync{v.handleBack()};assertEquals(before,v.currentSnapshot())
                leaveService(entry);state("cold-real-service-reentry-cancel-no-charge")
            }
            walkTo(15,29);step(Key.DOWN);assertEquals(16,v.world.mapId)
            assertEquals(239 to 160,v.world.x/16 to v.world.y/16);state("normal-independent-original-world-return")
            step(Key.LEFT);assertEquals(238 to 160,v.world.x/16 to v.world.y/16)
            for((flag,value)in source.flags)assertEquals(value,v.currentSnapshot().flags[flag])
            assertEquals(0,bossEntries);checkSourceUnchanged();persistChecked()
            if(!cold)File(root,"world-$label-expected-save.json").writeText(v.currentSnapshot().json().toString())
            state(if(cold)"cold-complete-real-route-and-service-continuation" else "normal-village3-trades-gear-lodging-dialogue-saved")
            instrumentation.runOnMainSync{activity.finish()};return
        }
        if(hallBatch||rebirth){
            val maps=if(rebirth)listOf(67,68)else listOf(61,62,63,64,65,66)
            assertEquals(listOf("nezha","xiaolongnv"),source.characters.map{it.id})
            assertTrue(source.flags["rom.map.60.flag.2"]==true)
            assertTrue(source.flags["rom.map.60.flag.2.dialogue.pending"]!=true)
            assertEquals(if(rebirth&&cold)16 else 23,v.world.mapId)
            fun leaveLanding(){
                val cell=v.world.x/16 to v.world.y/16
                if(v.content.exits.any{it.fromMapId==v.world.mapId&&it.triggerX==cell.first&&it.triggerY==cell.second}){
                    val key=listOf(Key.DOWN,Key.LEFT,Key.RIGHT,Key.UP).first{
                        v.world.scene.probeFrom(cell.first,cell.second,it,v.world.terrainMode)==MovementBlock.NONE}
                    step(key)
                }
            }
            if(rebirth&&cold){
                val story=v.content.sceneStories.getValue("rom.npc.86.0")
                assertTrue(source.flags[story.flagId]==true);assertTrue(source.flags[story.pendingFlag]!=true)
                assertEquals(238,v.world.x/16);assertEquals(160,v.world.y/16)
                for(mid in maps){val done=v.content.npcs.filter{it.mapId==mid}.mapNotNull{v.content.battle!!.storyBattles[it.id]}.single()
                    assertTrue(done.alreadyWon(source.flags));for(flag in done.victoryFlags)assertTrue(source.flags[flag]==true)}
                state("cold-original-world-next-state-full-party-and-flags")
                // Original 86 -> 16 is not evidence of a reverse portal.
                // Continue from the actual scripted world landing only.
                val key=listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT).first{candidate->
                    val nx=v.world.x/16+(if(candidate==Key.RIGHT)1 else if(candidate==Key.LEFT)-1 else 0)
                    val ny=v.world.y/16+(if(candidate==Key.DOWN)1 else if(candidate==Key.UP)-1 else 0)
                    v.world.scene.probeFrom(v.world.x/16,v.world.y/16,candidate,v.world.terrainMode)==MovementBlock.NONE&&
                        v.content.exits.none{e->e.fromMapId==16&&e.triggerX==nx&&e.triggerY==ny}}
                step(key);assertEquals(16,v.world.mapId);assertEquals(source.flags,v.currentSnapshot().flags)
                assertEquals(0,bossEntries);state("cold-completed-rebirth-no-script-restart-and-normal-move")
                checkSourceUnchanged();persistChecked();state("cold-real-return-and-continue")
                instrumentation.runOnMainSync{activity.finish()};return
            }
            if(!cold){
                // Prepare before crossing the lower bridge in the verified foot mode.
                // Completed halls do not make a reverse supply route exist.
                assertTrue("Use same-candidate first-hall normal preparation, never grant levels",
                    v.currentSnapshot().characters.all{it.level>=32})
                state("normal-earned-preparation-preserved-no-invented-village-return")
            }
            if(cold)for(mid in maps){
                val restoredStory=v.content.npcs.filter{it.mapId==mid}.mapNotNull{v.content.battle!!.storyBattles[it.id]}.single()
                assertTrue(restoredStory.alreadyWon(source.flags));assertTrue(source.flags[restoredStory.pendingFlag]!=true)
                for(flag in restoredStory.victoryFlags)assertTrue(source.flags[flag]==true)
            }
            // Exact external cold snapshot covers all six completed halls;
            // normally re-enter the last one within the existing cold budget.
            for(mid in if(cold)listOf(maps.last())else maps){
                val story=v.content.npcs.filter{it.mapId==mid}.mapNotNull{v.content.battle!!.storyBattles[it.id]}.single()
                val king=v.content.npcs.single{it.id==story.npcId}
                val scene=v.content.scenes.getValue(mid)
                val entry=v.content.exits.single{it.fromMapId==23&&it.toMapId==mid&&it.spawnX==scene.spawnX&&it.spawnY==scene.spawnY}
                val gate=v.content.sceneBarriers.single{it.mapId==mid}
                val exit=v.content.exits.single{it.fromMapId==mid&&it.triggerX==gate.x&&it.triggerY==gate.y}
                if(!cold&&!rebirth&&mid in listOf(62,66)){
                    val zone=if(mid==62)10 else 13;val x=if(zone==10)50 else 51;val y=if(zone==10)60 else 32
                    walkTo(x,y);state("normal-original-zone$zone-reached-after-real-hall-gates")
                    val beforeFights=fights;var steps=0
                    while(fights==beforeFights){
                        assertTrue("Bounded natural zone$zone encounter",steps++<120)
                        walkTo(x,if(v.world.y/16==y)y+1 else y)
                    }
                    state("normal-original-zone$zone-natural-encounter")
                }
                leaveLanding();walkTo(entry.triggerX,entry.triggerY);assertEquals(mid,v.world.mapId)
                assertEquals(entry.spawnX,v.world.x/16);assertEquals(entry.spawnY,v.world.y/16)
                state("normal-map-$mid-original-entry")
                if(!cold){
                    assertFalse(story.alreadyWon(v.currentSnapshot().flags))
                    assertEquals(MovementBlock.PHYSICAL,v.world.scene.blockType(gate.x,gate.y))
                    for(npc in v.content.npcs.filter{it.mapId==mid&&it.treasure!=null}){
                        // Real adjacent approach, no position/flag/inventory writes.
                        walkTo(npc.x,npc.y+1)
                        val before=v.currentSnapshot();val treasure=npc.treasure!!
                        val item=v.content.itemDefinitions.getValue(treasure.itemId)
                        val expected=WorldItems.openTreasure(before,treasure,item)
                        // Two original chests can flank the same approach cell.
                        // A with sideways facing intentionally selects neither;
                        // tap this stable actor through the existing phone path.
                        tapMapActor(v,npc);assertEquals(GameView.Layer.MAP,v.layer)
                        val after=v.currentSnapshot();assertEquals(expected.inventory,after.inventory);assertEquals(expected.flags,after.flags)
                        assertEquals(before.money,after.money);assertEquals(before.characters,after.characters)
                        tapMapActor(v,npc);assertEquals(after.inventory,v.currentSnapshot().inventory);assertEquals(after.flags,v.currentSnapshot().flags)
                        state("normal-map-$mid-chest-${npc.id}-applied-${expected.applied}")
                        if(rebirth&&item.id==WorldItems.FIELD_PROTECTION_ID){
                            assertTrue("Acquire the real protection chest before crossing the hall",expected.applied)
                            tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));scrollToItem(v,item.id)
                            val selected=v.currentSnapshot();tap(v,center(v.panelItemBounds(item.id)));assertEquals(selected,v.currentSnapshot())
                            tap(v,center(v.panelPrimaryBounds()));assertEquals(selected.inventory,v.currentSnapshot().inventory)
                            assertTrue(v.currentSnapshot().flags[WorldItems.FIELD_PENDING_FLAG]==true)
                            instrumentation.runOnMainSync{v.handleBack()};state("normal-earned-protection-used-not-consumed")
                        }
                        val owner=when(item.id){"rom.weapon.19"->"xiaolongnv";"rom.weapon.5"->"nezha";else->null}
                        if(owner!=null&&(v.currentSnapshot().inventory[item.id]?:0)>0){
                            val def=v.content.equipmentDefinitions.getValue(item.id)
                            val current=v.currentSnapshot().characters.single{it.id==owner}
                            if(current.equipment!!.rightHand!=def.originalId){
                                tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));tap(v,center(v.panelCharacterBounds(owner)))
                                scrollToItem(v,item.id);val selected=v.currentSnapshot()
                                tap(v,center(v.panelItemBounds(item.id)));assertEquals(selected,v.currentSnapshot())
                                tap(v,center(v.panelPrimaryBounds()))
                                assertEquals(def.originalId,v.currentSnapshot().characters.single{it.id==owner}.equipment!!.rightHand)
                                assertEquals(selected.money,v.currentSnapshot().money);assertEquals(selected.flags,v.currentSnapshot().flags)
                                instrumentation.runOnMainSync{v.handleBack()};state("normal-equipped-earned-${item.id}")
                            }
                        }
                    }
                }else{
                    assertTrue(story.alreadyWon(v.currentSnapshot().flags));assertTrue(v.currentSnapshot().flags[gate.removedFlagId]==true)
                    assertTrue(v.currentSnapshot().flags[story.pendingFlag]!=true)
                }
                if(rebirth&&mid==67&&!cold){
                    for(room in listOf(69,158,159))for(side in v.content.exits.filter{it.fromMapId==67&&it.toMapId==room}){
                        leaveLanding();walkTo(side.triggerX,side.triggerY);assertEquals(room,v.world.mapId)
                        state("normal-side-room-$room-entry-${side.spawnX}-${side.spawnY}")
                        for(npc in v.content.npcs.filter{it.mapId==room}){
                            val cell=npc.interactionCell?: (npc.x to npc.y+1)
                            walkTo(cell.first,cell.second);val beforeTalk=v.currentSnapshot();talk()
                            assertEquals(GameView.Layer.MAP,v.layer);assertEquals(beforeTalk.money,v.currentSnapshot().money)
                            assertEquals(beforeTalk.inventory,v.currentSnapshot().inventory);assertEquals(beforeTalk.characters,v.currentSnapshot().characters)
                            state("normal-side-dialogue-${npc.id}")
                        }
                        walkTo(side.spawnX,side.spawnY);assertEquals(67,v.world.mapId)
                        assertEquals(side.triggerX,v.world.x/16);assertEquals(side.triggerY,v.world.y/16)
                    }
                    assertTrue(v.currentSnapshot().flags[WorldItems.FIELD_ACTIVE_FLAG]==true)
                }
                walkTo(king.interactionCell!!.first,king.interactionCell.second)
                val before=v.currentSnapshot();talk()
                if(!cold){
                    assertEquals(GameView.Layer.BATTLE,v.layer);finishFight();assertEquals(GameView.Layer.DIALOGUE,v.layer)
                    val enemy=v.content.battle!!.enemies.getValue(story.group.members.single().enemyId)
                    val won=v.currentSnapshot();assertEquals(before.money+enemy.moneyReward,won.money)
                    assertTrue(won.characters.all{it.hp>0})
                    val share=enemy.experienceReward/won.characters.size
                    for(actor in before.characters)assertEquals(actor.experience+share,won.characters.single{it.id==actor.id}.experience)
                    assertTrue(won.flags[story.pendingFlag]==true);state("normal-map-$mid-victory-dialogue")
                    dialogue();assertEquals(GameView.Layer.MAP,v.layer);assertTrue(v.currentSnapshot().flags[story.pendingFlag]!=true)
                }else assertEquals(GameView.Layer.MAP,v.layer)
                val paid=v.currentSnapshot();talk();assertEquals(GameView.Layer.MAP,v.layer)
                assertEquals(paid.money,v.currentSnapshot().money);assertEquals(paid.inventory,v.currentSnapshot().inventory)
                assertEquals(paid.characters,v.currentSnapshot().characters);assertEquals(paid.flags,v.currentSnapshot().flags)
                assertNull(v.world.scene.check(gate.x,gate.y));state("normal-map-$mid-repeat-no-reward")
                if(rebirth&&mid==68){
                    // Last physical step executes the real exit; its auto-scene
                    // immediately restores the traced opening position.
                    walkTo(12,2);val beforeScene=v.currentSnapshot();step(Key.UP)
                    assertEquals(86,v.world.mapId);assertEquals(GameView.Layer.DIALOGUE,v.layer)
                    val sceneStory=v.content.sceneStories.getValue("rom.npc.86.0")
                    val text=GameView::class.java.getDeclaredField("dialogueText").apply{isAccessible=true}
                    assertEquals(7,v.world.x/16);assertEquals(4,v.world.y/16);state("normal-rebirth-original-opening")
                    for((index,id)in sceneStory.continuation.dialogueIds.withIndex()){
                        assertEquals(id,(text.get(v)as StoryText).id);val beforeText=v.currentSnapshot()
                        instrumentation.runOnMainSync{v.handleBack()};assertEquals(beforeText,v.currentSnapshot())
                        assertEquals(GameView.Layer.DIALOGUE,v.layer);state("normal-rebirth-dialogue-$index")
                        var pages=0
                        while(v.layer==GameView.Layer.DIALOGUE&&(text.get(v)as StoryText).id==id){
                            assertTrue("Bounded original scene pages",pages++<32);tap(v,Pair(v.width*.5f,v.height*.5f))}
                        assertTrue(v.currentSnapshot().flags[sceneStory.continuation.stageKey(sceneStory.id,index)]==true)
                        assertEquals(beforeScene.money,v.currentSnapshot().money);assertEquals(beforeScene.inventory,v.currentSnapshot().inventory)
                    }
                    assertEquals(GameView.Layer.MAP,v.layer);assertEquals(16,v.world.mapId)
                    assertEquals(238,v.world.x/16);assertEquals(160,v.world.y/16)
                    assertTrue(v.currentSnapshot().flags[sceneStory.flagId]==true)
                    assertTrue(v.currentSnapshot().flags[sceneStory.pendingFlag]!=true)
                    assertEquals(beforeScene.characters,v.currentSnapshot().characters) // Real antidotes kept both unpoisoned.
                    assertEquals(2,bossEntries);checkSourceUnchanged();persistChecked()
                    File(root,"world-$label-expected-save.json").writeText(v.currentSnapshot().json().toString())
                    state("normal-final-hall-rebirth-next-world-state-saved")
                    instrumentation.runOnMainSync{activity.finish()};return
                }
                walkTo(exit.triggerX,exit.triggerY);assertEquals(23,v.world.mapId)
                assertEquals(exit.spawnX,v.world.x/16);assertEquals(exit.spawnY,v.world.y/16)
                persistChecked();state("normal-map-$mid-original-open-gate-saved")
            }
            assertEquals(if(cold)0 else maps.size,bossEntries);checkSourceUnchanged();persistChecked()
            if(!cold)File(root,"world-$label-expected-save.json").writeText(v.currentSnapshot().json().toString())
            state(if(cold)"cold-full-batch-flags-no-repeat-and-continue" else "normal-full-batch-original-exit-saved")
            instrumentation.runOnMainSync{activity.finish()};return
        }
        if(secondHall){
            assertEquals(listOf("nezha","xiaolongnv"),source.characters.map{it.id})
            for(flag in listOf("rom.map.70.flag.2","rom.map.70.flag.4"))assertEquals(true,source.flags[flag])
            assertTrue(source.flags["rom.map.70.flag.2.dialogue.pending"]!=true)
            val story=v.content.battle!!.storyBattles.getValue("rom.npc.60.1")
            assertEquals(143,story.group.members.single().enemyId)
            assertEquals(23,v.world.mapId)
            if(cold){
                assertTrue(source.flags[story.flagId]==true);assertTrue(source.flags["rom.map.60.flag.4"]==true)
                assertTrue(source.flags[story.pendingFlag]!=true);state("cold-complete-party-flags-and-open-gate")
                val departure=listOf(Key.DOWN,Key.LEFT,Key.RIGHT,Key.UP).first{v.world.scene.probeFrom(v.world.x/16,v.world.y/16,it,v.world.terrainMode)==MovementBlock.NONE}
                step(departure);walkTo(5,70);assertEquals(60,v.world.mapId)
                assertEquals(28,v.world.x/16);assertEquals(22,v.world.y/16);state("cold-original-upper-door-reentry")
                walkTo(27,26);val before=v.currentSnapshot();talk();assertEquals(GameView.Layer.MAP,v.layer)
                val after=v.currentSnapshot();assertEquals(before.money,after.money);assertEquals(before.inventory,after.inventory)
                assertEquals(before.characters,after.characters);assertEquals(before.flags,after.flags);assertEquals(0,bossEntries)
                state("cold-king-repeat-no-second-battle-or-reward")
                walkTo(28,22);assertEquals(23,v.world.mapId);checkSourceUnchanged();persistChecked()
                state("cold-original-open-gate-and-continue");instrumentation.runOnMainSync{activity.finish()};return
            }
            assertTrue(source.flags[story.flagId]!=true);state("verified-normal-first-hall-source-no-state-grants")
            walkTo(6,80);assertEquals(60,v.world.mapId);assertEquals(1,v.world.x/16);assertEquals(28,v.world.y/16)
            state("normal-original-second-hall-entry")
            assertEquals(MovementBlock.PHYSICAL,v.world.scene.blockType(28,22))
            walkTo(6,28);val beforeGuard=v.currentSnapshot();talk();assertEquals(GameView.Layer.MAP,v.layer)
            assertEquals(beforeGuard.money,v.currentSnapshot().money);assertEquals(beforeGuard.inventory,v.currentSnapshot().inventory)
            state("normal-original-second-hall-guard-dialogue")
            walkTo(27,26);val beforeBoss=v.currentSnapshot();talk();assertEquals(GameView.Layer.BATTLE,v.layer)
            finishFight();assertEquals(GameView.Layer.DIALOGUE,v.layer);state("normal-chu-victory-dialogue")
            val won=v.currentSnapshot();assertEquals(beforeBoss.money+380,won.money);assertTrue(won.characters.all{it.hp>0})
            for(actor in beforeBoss.characters)assertEquals(actor.experience+125,won.characters.single{it.id==actor.id}.experience)
            assertTrue(won.flags[story.flagId]==true);assertTrue(won.flags["rom.map.60.flag.4"]==true)
            assertTrue(won.flags[story.pendingFlag]==true);dialogue();assertEquals(GameView.Layer.MAP,v.layer)
            assertTrue(v.currentSnapshot().flags[story.pendingFlag]!=true);assertNull(v.world.scene.check(28,22))
            val paid=v.currentSnapshot();talk();assertEquals(GameView.Layer.MAP,v.layer)
            assertEquals(paid.money,v.currentSnapshot().money);assertEquals(paid.inventory,v.currentSnapshot().inventory)
            assertEquals(paid.characters,v.currentSnapshot().characters);assertEquals(paid.flags,v.currentSnapshot().flags)
            assertEquals(1,bossEntries);state("normal-king-repeat-no-duplicate-reward")
            walkTo(28,22);assertEquals(23,v.world.mapId);assertEquals(5,v.world.x/16);assertEquals(70,v.world.y/16)
            checkSourceUnchanged();persistChecked();File(root,"world-$label-expected-save.json").writeText(v.currentSnapshot().json().toString())
            state("normal-original-second-hall-exit-saved");instrumentation.runOnMainSync{activity.finish()};return
        }
        if(firstHall){
            assertEquals(listOf("nezha","xiaolongnv"),source.characters.map{it.id})
            val story=v.content.battle!!.storyBattles.getValue("rom.npc.70.1")
            assertEquals(142,story.group.members.single().enemyId)
            if(cold){
                assertEquals(23,v.world.mapId);assertTrue(source.flags[story.flagId]==true)
                assertTrue(source.flags["rom.map.70.flag.4"]==true);assertTrue(source.flags[story.pendingFlag]!=true)
                state("cold-full-party-inventory-flags-and-open-gate")
                val departure=listOf(Key.DOWN,Key.LEFT,Key.RIGHT,Key.UP).first{v.world.scene.probeFrom(v.world.x/16,v.world.y/16,it,v.world.terrainMode)==MovementBlock.NONE}
                step(departure);walkTo(43,75);assertEquals(70,v.world.mapId)
                state("cold-original-upper-door-reentry")
                walkTo(28,6);val before=v.currentSnapshot();talk();assertEquals(GameView.Layer.MAP,v.layer)
                val after=v.currentSnapshot();assertEquals(before.money,after.money);assertEquals(before.inventory,after.inventory)
                assertEquals(before.characters,after.characters);assertEquals(before.flags,after.flags);assertEquals(0,bossEntries)
                state("cold-king-repeat-no-second-battle-or-reward")
                walkTo(23,2);assertEquals(23,v.world.mapId);checkSourceUnchanged();persistChecked()
                state("cold-original-open-gate-and-continue");instrumentation.runOnMainSync{activity.finish()};return
            }
            assertEquals(2,v.world.mapId);assertTrue(source.flags[story.flagId]!=true)
            state("verified-normal-village2-source-no-state-grants")
            val gear=listOf("rom.weapon.4","rom.armor.3")
            fun equipped(id:String):Boolean{val d=v.content.equipmentDefinitions.getValue(id);val e=v.currentSnapshot().characters.first().equipment!!
                return(if(d.slot=="rightHand")e.rightHand else e.body)==d.originalId}
            fun buyAndEquip(){
                for(id in gear)if(!equipped(id)&&v.currentSnapshot().money>=v.content.itemDefinitions.getValue(id).buyPrice!!+200){
                    if((v.currentSnapshot().inventory[id]?:0)==0){val entry=enterService(2,if(id==gear.first())17 else 18);trade(id,true);leaveService(entry)}
                    tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));tap(v,center(v.panelCharacterBounds("nezha")));scrollToItem(v,id)
                    val before=v.currentSnapshot();tap(v,center(v.panelItemBounds(id)));assertEquals(before,v.currentSnapshot())
                    tap(v,center(v.panelPrimaryBounds()));assertTrue(equipped(id));instrumentation.runOnMainSync{v.handleBack()};state("normal-purchased-equipped-$id")
                }
            }
            fun restock(){val entry=enterService(2,19)
                for(id in listOf(HerbUse.ID,AntidoteUse.ID)){val qty=(10-(v.currentSnapshot().inventory[id]?:0)).coerceAtLeast(0)
                    val affordable=(v.currentSnapshot().money/v.content.itemDefinitions.getValue(id).buyPrice!!).coerceAtMost(qty)
                    if(affordable>0)trade(id,true,affordable)}
                leaveService(entry);supply()
            }
            buyAndEquip();restock();inn(2);training=true
            walkTo(30,19);assertEquals(23,v.world.mapId);walkTo(54,93)
            var steps=0
            while(!gear.all{equipped(it)}||v.currentSnapshot().characters.any{it.level<32}){
                // Run37364795394 reached map65 legally, but level25/26 exhausted
                // herbs and lost one actor. Keep all alive/share/reward assertions.
                // Isolated HallRoutePreparationTest compares the unchanged c50
                // rules; earn preparation via real battles/paid rest, never inject it.
                // This bot target is optional preparation, not a gameplay entry lock.
                assertTrue("Real first-hall normal preparation exhausted; never grant levels/money",steps++<25000)
                val low=v.currentSnapshot().characters.any{it.hp<=it.maxHp*3/4||it.statusMask and OriginalStatus.POISON!=0}
                val readyMoney=gear.any{!equipped(it)&&v.currentSnapshot().money>=v.content.itemDefinitions.getValue(it).buyPrice!!+200}
                if(low||readyMoney||(v.currentSnapshot().inventory[AntidoteUse.ID]?:0)<4){
                    walkTo(55,91);assertEquals(2,v.world.mapId);buyAndEquip();restock();inn(2)
                    walkTo(30,19);walkTo(54,93)
                }
                step(if(v.world.y/16>93)Key.UP else Key.DOWN)
            }
            walkTo(55,91);assertEquals(2,v.world.mapId);training=false;restock();inn(2)
            assertTrue(gear.all{equipped(it)});assertTrue(v.currentSnapshot().characters.all{it.hp==it.maxHp})
            state("normal-original-supply-gear-training-not-a-story-gate")
            walkTo(30,19);assertEquals(23,v.world.mapId);walkTo(28,80);assertEquals(70,v.world.mapId)
            assertEquals(1,v.world.x/16);assertEquals(13,v.world.y/16);state("normal-first-hall-original-entry")
            assertEquals(MovementBlock.PHYSICAL,v.world.scene.blockType(23,2))
            walkTo(2,6);val beforeGuard=v.currentSnapshot();talk();assertEquals(GameView.Layer.MAP,v.layer)
            assertEquals(beforeGuard.money,v.currentSnapshot().money);assertEquals(beforeGuard.inventory,v.currentSnapshot().inventory)
            state("normal-original-first-hall-guard-dialogue")
            walkTo(28,6);val beforeBoss=v.currentSnapshot();talk();assertEquals(GameView.Layer.BATTLE,v.layer)
            finishFight();assertEquals(GameView.Layer.DIALOGUE,v.layer);state("normal-qin-victory-dialogue")
            val won=v.currentSnapshot();assertEquals(beforeBoss.money+350,won.money)
            assertTrue(won.characters.all{it.hp>0})
            for(actor in beforeBoss.characters)assertEquals(actor.experience+120,won.characters.single{it.id==actor.id}.experience)
            assertTrue(won.flags[story.flagId]==true);assertTrue(won.flags["rom.map.70.flag.4"]==true)
            assertTrue(won.flags[story.pendingFlag]==true);dialogue();assertEquals(GameView.Layer.MAP,v.layer)
            assertTrue(v.currentSnapshot().flags[story.pendingFlag]!=true);assertNull(v.world.scene.check(23,2))
            val paid=v.currentSnapshot();talk();assertEquals(GameView.Layer.MAP,v.layer)
            assertEquals(paid.money,v.currentSnapshot().money);assertEquals(paid.inventory,v.currentSnapshot().inventory)
            assertEquals(paid.characters,v.currentSnapshot().characters);assertEquals(paid.flags,v.currentSnapshot().flags)
            assertEquals(1,bossEntries);state("normal-king-repeat-no-duplicate-reward")
            walkTo(23,2);assertEquals(23,v.world.mapId);assertEquals(43,v.world.x/16);assertEquals(75,v.world.y/16)
            checkSourceUnchanged();persistChecked();File(root,"world-$label-expected-save.json").writeText(v.currentSnapshot().json().toString())
            state("normal-original-first-hall-exit-saved");instrumentation.runOnMainSync{activity.finish()};return
        }
        if(hell){
            assertEquals(listOf("nezha","xiaolongnv"),source.characters.map{it.id})
            assertEquals(true,source.flags["rom.map.95.flag.128"])
            state(if(cold)"cold-complete-party-and-services-save" else "verified-east-party-source")
            if(cold){
                assertEquals(2,v.world.mapId)
                val before=v.currentSnapshot();tap(v,center(v.hudBounds()));tap(v,tabPoint(v,1))
                tap(v,center(v.panelCharacterBounds("xiaolongnv")));assertEquals(before,v.currentSnapshot())
                state("cold-girl-equipment-and-owned-items");instrumentation.runOnMainSync{v.handleBack()}
                val entry=enterService(2,22);val saved=v.currentSnapshot()
                instrumentation.runOnMainSync{v.handleBack()};assertEquals(saved,v.currentSnapshot())
                leaveService(entry);assertEquals(source.flags,v.currentSnapshot().flags)
                checkSourceUnchanged();persistChecked();state("cold-real-inn-reentry-no-free-rest-or-reward")
                instrumentation.runOnMainSync{activity.finish()};return
            }
            assertEquals(23,v.world.mapId)
            walkTo(55,91);assertEquals(2,v.world.mapId);assertEquals(30,v.world.x/16);assertEquals(19,v.world.y/16)
            state("normal-hell-to-village2")
            // Normal one-item buy/sell through each existing touch path. The
            // source money and inventory are never repaired or replenished.
            for((room,id)in listOf(17 to "rom.weapon.3",18 to "rom.armor.29",19 to HerbUse.ID)){
                val entry=enterService(2,room);state("normal-shop-$room-open")
                trade(id,true);state("normal-shop-$room-bought")
                trade(id,false);state("normal-shop-$room-sold");leaveService(entry)
            }
            val medicineEntry=enterService(2,19)
            trade(HerbUse.ID,true,(6-(v.currentSnapshot().inventory[HerbUse.ID]?:0)).coerceAtLeast(0))
            trade(AntidoteUse.ID,true,(4-(v.currentSnapshot().inventory[AntidoteUse.ID]?:0)).coerceAtLeast(0))
            leaveService(medicineEntry)
            // Cure each genuinely poisoned owner with the existing selected-
            // character command, never borrow actor0 HP or mutate party order.
            for(actor in v.currentSnapshot().characters.filter{it.statusMask and OriginalStatus.POISON!=0}){
                val before=v.currentSnapshot();tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2))
                tap(v,center(v.panelCharacterBounds(actor.id)));scrollToItem(v,AntidoteUse.ID)
                tap(v,center(v.panelItemBounds(AntidoteUse.ID)));assertEquals(before,v.currentSnapshot())
                tap(v,center(v.panelPrimaryBounds()));val after=v.currentSnapshot()
                assertEquals(0,after.characters.single{it.id==actor.id}.statusMask and OriginalStatus.POISON)
                assertEquals(before.characters.filter{it.id!=actor.id},after.characters.filter{it.id!=actor.id})
                assertEquals(((before.inventory[AntidoteUse.ID]?:0)-2).coerceAtLeast(0),after.inventory[AntidoteUse.ID]?:0)
                instrumentation.runOnMainSync{v.handleBack()};state("normal-antidote-${actor.id}")
            }
            inn(2);assertTrue("Normal inn preserves excluded states; cure actual owners before shopping: ${v.currentSnapshot().characters}",
                v.currentSnapshot().characters.all{it.hp==it.maxHp&&it.mp==it.maxMp})
            state("normal-original20-inn-two-actor-recovery-and-return")
            walkTo(30,19);assertEquals(23,v.world.mapId);assertEquals(55,v.world.x/16);assertEquals(91,v.world.y/16)
            state("normal-village2-return-to-hell")
            // Later exterior partitions require the original completed halls.
            // Their natural encounters are checked in the hall-batch phase,
            // not reached by pretending exterior23 is one connected field.
            // The independent return spawns on 55,91; 55,92 is a real wall.
            // Depart west, then enter the actual same doorway again.
            step(Key.LEFT);walkTo(55,91);assertEquals(2,v.world.mapId)
            assertEquals(source.flags,v.currentSnapshot().flags);assertEquals(0,bossEntries)
            checkSourceUnchanged();persistChecked()
            File(root,"world-$label-expected-save.json").writeText(v.currentSnapshot().json().toString())
            state("normal-two-actor-extended-hell-and-services-saved")
            instrumentation.runOnMainSync{activity.finish()};return
        }
        if(east){
            val story=v.content.battle!!.storyBattles.getValue("rom.npc.95.0")
            val chain=story.continuation!!
            val mechanism=v.content.mechanisms.single{it.mapId==95}
            fun checkCompleted(){
                val saved=v.currentSnapshot()
                assertEquals(23,saved.mapId);assertEquals(listOf("nezha","xiaolongnv"),saved.characters.map{it.id})
                assertEquals(true,saved.flags[story.flagId]);assertEquals(true,saved.flags["rom.map.95.flag.128"])
                assertTrue(saved.flags[story.pendingFlag]!=true)
                for(i in chain.dialogueIds.indices)assertEquals(true,saved.flags[chain.stageKey(story.id,i)])
                assertEquals(true,saved.flags[mechanism.sessionFlag]);assertEquals(1,saved.inventory[WorldItems.ID])
            }
            if(cold){
                checkCompleted();state("cold-complete-party-and-story-restored")
                val before=v.currentSnapshot()
                tap(v,center(v.hudBounds()));tap(v,tabPoint(v,1));assertEquals(before,v.currentSnapshot())
                state("cold-real-party-equipment-detail");instrumentation.runOnMainSync{v.handleBack()}
                training=true
                val key=listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT).first{v.world.scene.probeFrom(
                    v.world.x/16,v.world.y/16,it,v.world.terrainMode)==MovementBlock.NONE}
                step(key);training=false;checkCompleted()
                assertEquals(source.flags,v.currentSnapshot().flags);assertEquals(0,bossEntries)
                checkSourceUnchanged();persistChecked();state("cold-normal-next-step-no-second-plot-reward")
                instrumentation.runOnMainSync{activity.finish()};return
            }
            assertEquals(16,v.world.mapId);assertEquals(215,v.world.x/16);assertEquals(107,v.world.y/16)
            assertEquals(1,v.currentSnapshot().characters.size)
            state("verified-cave-save-loaded")
            // Return over actual independent exits for optional preparation.
            // The chosen level and equipment are a recording strategy, never a story gate.
            walkTo(215,106);assertEquals(85,v.world.mapId)
            walkTo(30,29);assertEquals(16,v.world.mapId)
            walkTo(213,118);assertEquals(25,v.world.mapId)
            walkTo(26,14);assertEquals(16,v.world.mapId)
            walkTo(191,102);assertEquals(1,v.world.mapId);state("optional-real-village1-supply")
            val gear=listOf("rom.weapon.3","rom.armor.2")
            fun hasEquipped(id:String):Boolean{
                val def=v.content.equipmentDefinitions.getValue(id);val equipment=v.currentSnapshot().characters.first().equipment!!
                return when(def.slot){"rightHand"->equipment.rightHand;"body"->equipment.body;"feet"->equipment.feet;else->-1}==def.originalId
            }
            fun needed():Int=gear.filter{!hasEquipped(it)&&(v.currentSnapshot().inventory[it]?:0)==0}
                .sumOf{v.content.itemDefinitions.getValue(it).buyPrice!!}+64+
                (10-(v.currentSnapshot().inventory[HerbUse.ID]?:0)).coerceAtLeast(0)*15+
                (10-(v.currentSnapshot().inventory[AntidoteUse.ID]?:0)).coerceAtLeast(0)*20
            fun equip(id:String){
                if(hasEquipped(id))return
                tap(v,center(v.hudBounds()));tap(v,tabPoint(v,2));scrollToItem(v,id)
                val before=v.currentSnapshot();tap(v,center(v.panelItemBounds(id)));assertEquals(before,v.currentSnapshot())
                tap(v,center(v.panelPrimaryBounds()));assertTrue("Real equip action must apply $id",hasEquipped(id))
                instrumentation.runOnMainSync{v.handleBack()}
            }
            fun purchaseGear(){
                for(id in gear)if(!hasEquipped(id)&&(v.currentSnapshot().inventory[id]?:0)==0&&
                    v.currentSnapshot().money>=v.content.itemDefinitions.getValue(id).buyPrice!!+16){
                    val room=if(v.content.equipmentDefinitions.getValue(id).slot=="rightHand")17 else 18
                    val entry=enterService(1,room);trade(id,true);leaveService(entry);equip(id);state("normal-equipped-$id")
                }else if(!hasEquipped(id)&&(v.currentSnapshot().inventory[id]?:0)>0)equip(id)
            }
            purchaseGear();inn(1)
            training=true;state("optional-normal-sea-training-start")
            walkTo(15,29);step(Key.DOWN);assertEquals(16,v.world.mapId)
            // (27,14) is original coral class 7, not a training cell.
            // Use the existing connected southern sea, with normal encounters.
            walkTo(186,102);assertEquals(25,v.world.mapId);walkTo(39,41)
            var trainingSteps=0
            while(v.currentSnapshot().characters.first().level<13||v.currentSnapshot().money<needed()){
                assertTrue("Bounded real East preparation exhausted; no injected level or money",trainingSteps++<6000)
                if(v.currentSnapshot().characters.first().hp<=v.currentSnapshot().characters.first().maxHp*3/4){
                    walkTo(26,14);assertEquals(16,v.world.mapId);walkTo(191,102);assertEquals(1,v.world.mapId)
                    purchaseGear();inn(1);walkTo(15,29);step(Key.DOWN);walkTo(186,102);walkTo(39,41)
                }
                var direction:Key?=null
                instrumentation.runOnMainSync{
                    val x=v.world.x/16;val y=v.world.y/16;val preferred=if(y>41)Key.UP else Key.DOWN
                    direction=(listOf(preferred)+listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT).filter{it!=preferred}).firstOrNull{key->
                        val nx=x+if(key==Key.RIGHT)1 else if(key==Key.LEFT)-1 else 0
                        val ny=y+if(key==Key.DOWN)1 else if(key==Key.UP)-1 else 0
                        v.world.scene.probeFrom(x,y,key,v.world.terrainMode)==MovementBlock.NONE&&
                            v.content.battle!!.zones.any{it.contains(25,nx,ny)}&&
                            v.content.exits.none{it.fromMapId==25&&it.triggerX==nx&&it.triggerY==ny}}
                }
                assertNotNull("No real legal sea training neighbor",direction);step(direction!!)
            }
            walkTo(26,14);walkTo(191,102);assertEquals(1,v.world.mapId)
            purchaseGear();assertTrue(gear.all{hasEquipped(it)});training=false
            val store=enterService(1,19)
            for(id in listOf(HerbUse.ID,AntidoteUse.ID)){
                val count=(10-(v.currentSnapshot().inventory[id]?:0)).coerceAtLeast(0);if(count>0)trade(id,true,count)
            }
            leaveService(store);inn(1);state("normal-preparation-purchased-and-saved")
            // The east palace is on the separate eastern sea component.
            // Enter through its observed original mainland door, not across coral.
            walkTo(15,29);step(Key.DOWN)
            // Village1 and the east doorway are separate mainland components.
            // Retrace the already completed original sea/cave passage; a single
            // same-map BFS cannot traverse these independent exits.
            walkTo(186,102);assertEquals(25,v.world.mapId)
            walkTo(53,30);assertEquals(16,v.world.mapId)
            walkTo(212,114);assertEquals(85,v.world.mapId)
            walkTo(2,2);assertEquals(16,v.world.mapId)
            assertEquals(true,v.currentSnapshot().flags[caveFlag])
            assertTrue(v.currentSnapshot().flags[caveFlag+".dialogue.pending"]!=true)
            state("normal-supply-return-through-original-sea-and-completed-cave")
            walkTo(214,110);assertEquals(25,v.world.mapId)
            assertEquals(54,v.world.x/16);assertEquals(22,v.world.y/16)
            walkTo(49,21);assertEquals(95,v.world.mapId)
            assertEquals(13,v.world.x/16);assertEquals(29,v.world.y/16);assertEquals(lastMovementKey,v.world.direction)
            state("east-real-preserved-direction-entry")
            assertTrue(v.currentSnapshot().flags[mechanism.sessionFlag]!=true)
            walkTo(12,21);assertEquals(true,v.currentSnapshot().flags[mechanism.sessionFlag])
            for(change in mechanism.changes){val index=change.y*v.world.scene.width+change.x
                assertEquals(change.toTile,v.world.scene.grid[index]);assertEquals(change.toCollision,v.world.scene.collision[index])}
            state("real-mechanism-opens-nine-cells")
            walkTo(13,4);assertEquals(GameView.Layer.MAP,v.layer)
            while(v.currentSnapshot().characters.first().hp<v.currentSnapshot().characters.first().maxHp)medicine(HerbUse.ID)
            val beforeBoss=v.currentSnapshot();assertTrue(beforeBoss.flags[story.flagId]!=true)
            if(v.world.direction!=Key.UP){step(Key.DOWN);step(Key.UP)}
            tap(v,center(layoutFor(v).buttons.getValue(Key.A)));assertEquals(GameView.Layer.DIALOGUE,v.layer)
            state("east-king-real-intro");dialogue();assertEquals(GameView.Layer.BATTLE,v.layer);finishFight()
            assertEquals(GameView.Layer.DIALOGUE,v.layer)
            val won=v.currentSnapshot();assertEquals(beforeBoss.money+300,won.money)
            assertEquals(beforeBoss.characters.first().experience+184,won.characters.first().experience)
            assertEquals(true,won.flags[story.flagId]);assertEquals(true,won.flags[story.pendingFlag])
            assertEquals(1,won.characters.size);assertEquals((beforeBoss.inventory[HerbUse.ID]?:0)-bossHerbs,won.inventory[HerbUse.ID]?:0)
            assertTrue("Real East ice action must be observed",capturedBossIce);state("east-victory-before-followup")
            val textField=GameView::class.java.getDeclaredField("dialogueText").apply{isAccessible=true}
            for((index,id)in chain.dialogueIds.withIndex()){
                assertEquals(id,(textField.get(v) as StoryText).id);val beforePage=v.currentSnapshot()
                assertEquals(95,beforePage.mapId);assertEquals(1,beforePage.characters.size);state("followup-$index")
                var taps=0
                while(v.layer==GameView.Layer.DIALOGUE&&(textField.get(v) as StoryText).id==id){
                    assertTrue("Bounded actual dialogue pages",taps++<32);tap(v,Pair(v.width*.5f,v.height*.5f))}
                val afterPage=v.currentSnapshot();assertEquals(beforePage.money,afterPage.money);assertEquals(beforePage.inventory,afterPage.inventory)
                assertEquals(beforePage.characters.first(),afterPage.characters.first())
                assertEquals(true,afterPage.flags[chain.stageKey(story.id,index)])
            }
            assertEquals(GameView.Layer.MAP,v.layer);checkCompleted()
            val joined=v.currentSnapshot();assertEquals(54,joined.x/16);assertEquals(92,joined.y/16)
            assertEquals(won.direction,joined.direction);assertEquals(0,joined.encounterSteps)
            assertEquals(v.content.joinCharacters.getValue("xiaolongnv"),joined.characters[1])
            state("real-party-join-and-hell-next-state")
            training=true;var steps=0
            while(twoActorVictories==0){
                assertTrue("Natural zone8 encounter must occur without injection",steps++<100)
                val key=if(v.world.y/16>91)Key.UP else Key.DOWN
                step(key);assertEquals(23,v.world.mapId)
            }
            training=false;checkCompleted();checkSourceUnchanged();persistChecked()
            File(root,"world-$label-expected-save.json").writeText(v.currentSnapshot().json().toString())
            state("normal-two-actor-next-state-saved-for-cold-restart")
            instrumentation.runOnMainSync{activity.finish()};return
        }
        if(cold){
            assertEquals(16,v.world.mapId);assertEquals(215,v.world.x/16);assertEquals(107,v.world.y/16)
            state("cold-full-save-restored")
            // A real completed movement, not walking to the current doorway cell.
            walkTo(215,106);assertEquals(85,v.world.mapId)
            assertEquals(2,v.world.x/16);assertEquals(2,v.world.y/16);state("cold-reentered-north-cave-exit")
            walkTo(2,6);assertEquals(GameView.Layer.MAP,v.layer)
            assertEquals(0,bossEntries);assertEquals(source.money,v.currentSnapshot().money)
            assertEquals(source.characters.first().experience,v.currentSnapshot().characters.first().experience)
            assertEquals(source.flags,v.currentSnapshot().flags);assertEquals(1,v.currentSnapshot().inventory[WorldItems.ID])
            state("cold-original-trigger-no-repeat-battle-or-reward")
            step(Key.DOWN);walkTo(2,6);assertEquals(GameView.Layer.MAP,v.layer)
            assertEquals(source.money,v.currentSnapshot().money)
            assertEquals(source.characters.first().experience,v.currentSnapshot().characters.first().experience)
            assertEquals(source.flags,v.currentSnapshot().flags)
            walkTo(2,2);assertEquals(16,v.world.mapId);walkTo(215,107)
            assertEquals(0,bossEntries);assertEquals(source.flags,v.currentSnapshot().flags)
            assertEquals(source.money,v.currentSnapshot().money)
            assertEquals(source.characters.first().experience,v.currentSnapshot().characters.first().experience)
            // Ordinary escapes and real medicine may change HP/counts on the way;
            // they must not invent a second story reward or erase prior progress.
            checkSourceUnchanged();persistChecked();state("cold-reentry-complete-and-saved")
            instrumentation.runOnMainSync{activity.finish()};return
        }
        assertEquals(25,v.world.mapId);assertEquals(48,v.world.x/16);assertEquals(40,v.world.y/16)
        state("verified-north-pearl-save-loaded")
        // Follow the already opened whirlpool back to the original village route.
        walkTo(39,42);assertEquals(16,v.world.mapId);walkTo(202,130);assertEquals(0,v.world.mapId)
        fun supplyCost():Int {
            val bag=v.currentSnapshot().inventory
            return (10-(bag[HerbUse.ID]?:0)).coerceAtLeast(0)*15+
                (10-(bag[AntidoteUse.ID]?:0)).coerceAtLeast(0)*20
        }
        // This is normal preparation, not a new cave gate. The source already
        // earned level9 and a long sword; every extra coin is earned in real fights.
        if(v.currentSnapshot().characters.first().level<9||v.currentSnapshot().money<supplyCost()+8){
            training=true;state("normal-preparation-start")
            inn();walkTo(0,14);step(Key.LEFT);walkTo(200,130)
            var trainingSteps=0
            while(v.currentSnapshot().characters.first().level<9||v.currentSnapshot().money<supplyCost()+8){
                assertTrue("Bounded normal earnings exhausted; never inject supplies",trainingSteps++<3000)
                if(v.currentSnapshot().characters.first().hp<=v.currentSnapshot().characters.first().maxHp*3/4){
                    walkTo(202,130);assertEquals(0,v.world.mapId);inn()
                    walkTo(0,14);step(Key.LEFT);walkTo(200,130)
                }
                var trainingDirection:Key?=null
                instrumentation.runOnMainSync{
                    val x=v.world.x/16;val y=v.world.y/16
                    val preferred=if(y>130)Key.UP else Key.DOWN
                    trainingDirection=(listOf(preferred)+listOf(Key.UP,Key.DOWN,Key.LEFT,Key.RIGHT).filter{it!=preferred}).firstOrNull{key->
                        val nx=x+if(key==Key.RIGHT)1 else if(key==Key.LEFT)-1 else 0
                        val ny=y+if(key==Key.DOWN)1 else if(key==Key.UP)-1 else 0
                        v.world.scene.probeFrom(x,y,key,v.world.terrainMode)==MovementBlock.NONE&&
                            inExistingEncounterRegion(v.content.battle!!,v.world.mapId,nx,ny)&&
                            v.content.exits.none{it.fromMapId==v.world.mapId&&it.triggerX==nx&&it.triggerY==ny}
                    }
                }
                assertNotNull("No legal normal training step; no collision bypass",trainingDirection)
                step(trainingDirection!!)
            }
            walkTo(202,130);assertEquals(0,v.world.mapId);training=false;state("normal-preparation-complete")
        }
        val store=enterService(0,19)
        val pills=(10-(v.currentSnapshot().inventory[AntidoteUse.ID]?:0)).coerceAtLeast(0)
        val herbs=(10-(v.currentSnapshot().inventory[HerbUse.ID]?:0)).coerceAtLeast(0)
        assertTrue(v.currentSnapshot().money>=pills*20+herbs*15+4)
        if(pills>0)trade(AntidoteUse.ID,true,pills)
        if(herbs>0)trade(HerbUse.ID,true,herbs)
        state("normal-supply-purchased");leaveService(store);inn()
        state("normal-inn-restored-before-route")
        walkTo(0,14);step(Key.LEFT);walkTo(199,130);assertEquals(25,v.world.mapId)
        walkTo(53,30);assertEquals(16,v.world.mapId)
        assertEquals(213,v.world.x/16);assertEquals(118,v.world.y/16);state("east-mainland-real-link")
        walkTo(212,114);assertEquals(85,v.world.mapId)
        assertEquals(30,v.world.x/16);assertEquals(29,v.world.y/16);state("cave-south-entry")
        walkTo(2,7);assertEquals(GameView.Layer.MAP,v.layer)
        while(v.currentSnapshot().characters.first().hp<v.currentSnapshot().characters.first().maxHp)medicine(HerbUse.ID)
        val beforeBoss=v.currentSnapshot();assertTrue(beforeBoss.flags[caveFlag]!=true)
        state("before-original-interception-cell")
        step(Key.UP);assertEquals(2,v.world.x/16);assertEquals(6,v.world.y/16)
        assertEquals(GameView.Layer.DIALOGUE,v.layer)
        val dialogueField=GameView::class.java.getDeclaredField("dialogueText").apply{isAccessible=true}
        assertEquals("rom.dialogue.95.0",(dialogueField.get(v) as StoryText).id)
        val actor=v.content.npcs.single{it.id=="rom.script.85.5.actor.129"};assertTrue(actor.scriptedActor)
        assertEquals(beforeBoss.inventory,v.currentSnapshot().inventory);assertEquals(beforeBoss.money,v.currentSnapshot().money)
        state("automatic-little-dragon-intro")
        dialogue();assertEquals(GameView.Layer.BATTLE,v.layer);finishFight()
        assertEquals(GameView.Layer.DIALOGUE,v.layer)
        val won=v.currentSnapshot()
        assertEquals(beforeBoss.money+240,won.money)
        assertEquals(beforeBoss.characters.first().experience+142,won.characters.first().experience)
        assertTrue("Real victory flag is deferred until its dialogue finishes",won.flags[caveFlag]!=true)
        assertEquals(true,won.flags[caveFlag+".dialogue.pending"])
        assertEquals("rom.dialogue.95.1",(dialogueField.get(v) as StoryText).id)
        assertEquals((beforeBoss.inventory[HerbUse.ID]?:0)-bossHerbs,won.inventory[HerbUse.ID]?:0)
        assertEquals(1,won.inventory[WorldItems.ID]);assertTrue("Normal physical Boss action must be visible",capturedBossAttack)
        // Medicine9 is an actual optional drop: preserve the observed result instead of fixing its RNG.
        for((id,count) in beforeBoss.inventory)if(id!=HerbUse.ID&&id!="rom.medicine.9")assertEquals(count,won.inventory[id])
        state("victory-dialogue-before-flag")
        dialogue();assertEquals(GameView.Layer.MAP,v.layer)
        val confirmed=v.currentSnapshot()
        assertEquals(won.flags-caveFlag-(caveFlag+".dialogue.pending")+(caveFlag to true),confirmed.flags)
        assertEquals(won.inventory,confirmed.inventory);assertEquals(won.characters,confirmed.characters);assertEquals(won.money,confirmed.money)
        state("victory-confirmed-actor-departed")
        step(Key.DOWN);walkTo(2,6);assertEquals(GameView.Layer.MAP,v.layer)
        assertEquals(1,bossEntries);assertEquals(confirmed.flags,v.currentSnapshot().flags)
        assertEquals(confirmed.money,v.currentSnapshot().money)
        assertEquals(confirmed.characters.first().experience,v.currentSnapshot().characters.first().experience)
        state("trigger-reentered-no-second-story")
        walkTo(2,2);assertEquals(16,v.world.mapId)
        assertEquals(215,v.world.x/16);assertEquals(106,v.world.y/16);state("north-cave-exit-real-mainland-link")
        walkTo(215,107);assertEquals(16,v.world.mapId)
        assertEquals(confirmed.flags,v.currentSnapshot().flags);assertEquals(1,v.currentSnapshot().inventory[WorldItems.ID])
        assertEquals(confirmed.money,v.currentSnapshot().money)
        assertEquals(confirmed.characters.first().experience,v.currentSnapshot().characters.first().experience)
        checkSourceUnchanged();persistChecked()
        File(root,"world-cave85-expected-save.json").writeText(v.currentSnapshot().json().toString())
        state("persisted-for-external-cold-restart")
        instrumentation.runOnMainSync{activity.finish()}
    }

    fun testWorldVillageOneColdStartMatchesNormalSave(){
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"world-village1-expected-save.json")
        assertTrue(file.exists());val expected=SaveSnapshot.parse(file.readText());val(activity,v)=launch()
        assertEquals(expected,v.currentSnapshot());assertEquals(22,v.world.mapId)
        assertEquals(InteriorContext(1,15,21),v.currentSnapshot().interiorContext)
        screenshot(v,"world-village1-cold-caller-restored")
        repeat(7){stickStep(v,Key.DOWN)};assertEquals(1,v.world.mapId)
        assertEquals(15,v.world.x/16);assertEquals(21,v.world.y/16);assertNull(v.currentSnapshot().interiorContext)
        assertEquals(expected.characters,v.currentSnapshot().characters);assertEquals(expected.inventory,v.currentSnapshot().inventory)
        assertEquals(expected.money,v.currentSnapshot().money);assertEquals(expected.flags,v.currentSnapshot().flags)
        screenshot(v,"world-village1-cold-returned-correct-village")
        instrumentation.runOnMainSync{v.persistState();activity.finish()}
    }
    fun testWorldWestColdStartMatchesNormalSave(){
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"world-west-expected-save.json")
        assertTrue(file.exists());val expected=SaveSnapshot.parse(file.readText());val(activity,v)=launch()
        assertEquals(expected,v.currentSnapshot());assertEquals(96,v.world.mapId);assertEquals(1,v.world.terrainMode)
        assertEquals(true,expected.flags["rom.event.96.40.2"]);assertEquals(true,expected.flags["rom.event.97.39.1"])
        screenshot(v,"world-west-cold-upper-plane-restored")
        // Opening/closing real HUD proves input and saved detail are usable without
        // claiming that a possible random encounter on the next tile is a save mismatch.
        tap(v,center(v.hudBounds()));assertEquals(GameView.Layer.CHARACTER,v.layer)
        assertEquals(expected,v.currentSnapshot());screenshot(v,"world-west-cold-character-detail")
        instrumentation.runOnMainSync{v.handleBack()};assertEquals(GameView.Layer.MAP,v.layer)
        assertEquals(expected,v.currentSnapshot());stickStep(v,Key.DOWN)
        val f=GameView::class.java.getDeclaredField("battle").apply{isAccessible=true}
        val p=GameView::class.java.getDeclaredField("battlePresentation").apply{isAccessible=true}
        var encountered=false;val deadline=SystemClock.elapsedRealtime()+180000
        while(true){
            var pair:Pair<OpeningBattle,BattlePresentation>?=null
            instrumentation.runOnMainSync{if(v.layer==GameView.Layer.BATTLE)pair=(f.get(v) as OpeningBattle) to (p.get(v) as BattlePresentation)}
            val(fight,presentation)=pair?:break;encountered=true
            assertTrue("Cold continuation must finish its real encounter",SystemClock.elapsedRealtime()<deadline)
            assertTrue("Cold continuation may not repair a defeat",fight.phase!=BattlePhase.DEFEAT)
            if(presentation.screen in listOf(BattlePresentation.Screen.COMMAND,BattlePresentation.Screen.TARGET)){
                if(fight.hero.hp<=fight.hero.maxHp/2&&v.battleHerbCount()>0){
                    tap(v,center(v.battleCommandBounds(2)));tap(v,center(v.battleItemBounds(HerbUse.ID)));tap(v,center(v.battleItemUseBounds()))
                }else chooseAttack(v,fight.enemies.first{it.hp>0}.slot)
            }
            SystemClock.sleep(40)
        }
        assertEquals(GameView.Layer.MAP,v.layer);assertEquals(96,v.world.mapId);assertEquals(1,v.world.terrainMode)
        assertEquals(2,v.world.x/16);assertEquals(20,v.world.y/16);assertEquals(expected.flags,v.currentSnapshot().flags)
        if(!encountered){assertEquals(expected.characters,v.currentSnapshot().characters)
            assertEquals(expected.inventory,v.currentSnapshot().inventory);assertEquals(expected.money,v.currentSnapshot().money)}
        screenshot(v,"world-west-cold-upper-plane-continued")
        File(instrumentation.targetContext.getExternalFilesDir(null),"world-west-cold-index.json").writeText(org.json.JSONObject()
            .put("kind","EXTERNAL_COLD_RESTART_FROM_NORMAL_WEST_SAVE").put("expected",expected.json())
            .put("afterNormalStep",v.currentSnapshot().json()).put("ordinaryEncounter",encountered).toString())
        instrumentation.runOnMainSync{v.persistState();activity.finish()}
    }

    fun testNanhaiColdStartMatchesNormalSave(){
        val expectedFile=File(instrumentation.targetContext.getExternalFilesDir(null),"nanhai-expected-save.json")
        assertTrue("Run continuous normal route before external force-stop",expectedFile.exists())
        val expected=SaveSnapshot.parse(expectedFile.readText());val(activity,v)=launch()
        assertEquals(expected,v.currentSnapshot());assertEquals(true,expected.flags["rom.event.97.39.1"])
        assertTrue(expected.flags["rom.event.97.39.1.dialogue.pending"]!=true)
        stickStep(v,Key.DOWN);assertEquals(97,v.world.mapId)
        assertEquals(expected.characters,v.currentSnapshot().characters);assertEquals(expected.money,v.currentSnapshot().money)
        assertEquals(expected.inventory,v.currentSnapshot().inventory);assertEquals(expected.flags,v.currentSnapshot().flags)
        screenshot(v,"nanhai-cold-restored-continue")
        instrumentation.runOnMainSync{v.persistState();activity.finish()}
    }

    /** Controlled boundary fixture: separate from the continuous normal-play recorder. */
    fun testControlledNanhaiVictoryFlagAndResumeOnce(){
        val(activity,v)=launch();val base=v.currentSnapshot()
        val hero=v.content.initialPlayer.copy(hp=1000,maxHp=1000,strength=250,equipment=EquipmentState(2,-1,1,28))
        instrumentation.runOnMainSync{
            assertTrue(v.restoreSnapshot(base.copy(mapId=97,x=15*16+8,y=4*16+8,direction=Key.UP,
                characters=listOf(hero),inventory=mapOf("rom.weapon.2" to 10),money=123,
                flags=mapOf("opening.intro.seen" to true),encounterSteps=32,interiorContext=null)))
        }
        val f=GameView::class.java.getDeclaredField("battle").apply{isAccessible=true}
        val p=GameView::class.java.getDeclaredField("battlePresentation").apply{isAccessible=true}
        fun completeDialogue(view:GameView){repeat(16){if(view.layer==GameView.Layer.DIALOGUE)tap(view,Pair(view.width*.5f,view.height*.5f))}}
        fun waitCommands(view:GameView=v){
            for(i in 0..500){
                var ready=false
                instrumentation.runOnMainSync{
                    val presentation=p.get(view) as BattlePresentation
                    val committed=GameView::class.java.getDeclaredField("battleCommitted").apply{isAccessible=true}.getBoolean(view)
                    // RESULT and finishBattlePresentation() occur in one UI callback. Observe that callback,
                    // including the real reward/save submission, rather than its transient screen value.
                    ready=presentation.screen !in listOf(BattlePresentation.Screen.ENTRY,BattlePresentation.Screen.ACTING)&&
                        (presentation.screen!=BattlePresentation.Screen.RESULT||committed)
                }
                if(ready)return
                SystemClock.sleep(40)
            }
            fail("Controlled Boss presentation/commit timeout")
        }
        tap(v,center(layoutFor(v).buttons.getValue(Key.A)));assertEquals(GameView.Layer.DIALOGUE,v.layer)
        assertTrue(v.currentSnapshot().flags["rom.event.97.39.1"]!=true)
        completeDialogue(v);assertEquals(GameView.Layer.BATTLE,v.layer);waitCommands()
        val fight=f.get(v) as OpeningBattle;val beforeEscapeHp=fight.hero.hp
        val enemyBox=GameView::class.java.getDeclaredMethod("battleEnemyBox",BattleEnemy::class.java)
            .apply{isAccessible=true}.invoke(v,fight.enemies.single()) as Box
        val targetBox=v.battleTargetBounds(fight.enemies.single().slot)
        assertEquals(128f/112,enemyBox.w/enemyBox.h,.001f)
        assertTrue("Full native Boss sprite fits its independent target cell",enemyBox.x>=targetBox.x&&
            enemyBox.y>=targetBox.y&&enemyBox.x+enemyBox.w<=targetBox.x+targetBox.w&&enemyBox.y+enemyBox.h<=targetBox.y+targetBox.h)
        screenshot(v,"nanhai-controlled-boss-original-origin")
        tap(v,center(v.battleCommandBounds(3)));assertEquals(BattlePresentation.Screen.ACTING,(p.get(v) as BattlePresentation).screen)
        assertEquals(BattlePhase.TARGET,fight.phase)
        assertTrue(v.currentSnapshot().flags["rom.event.97.39.1"]!=true);waitCommands()
        // A valid Boss round can miss. Preserve its RNG/rules rather than assume damage on the first attempt.
        for(i in 0 until 16){
            if(fight.hero.hp<beforeEscapeHp)break
            tap(v,center(v.battleCommandBounds(3)));waitCommands()
            assertEquals(BattlePhase.TARGET,fight.phase)
        }
        assertTrue("An ordinary bounded escape attempt sequence must expose retaliation",fight.hero.hp<beforeEscapeHp)
        for(i in 0..20){
            if(fight.phase!=BattlePhase.TARGET)break
            chooseAttack(v,fight.enemies.single().slot);waitCommands()
        }
        assertEquals(BattlePhase.VICTORY,fight.phase);assertEquals(BattlePresentation.Screen.RESULT,(p.get(v) as BattlePresentation).screen)
        val won=v.currentSnapshot();assertEquals(223,won.money);assertEquals(60,won.characters.first().experience)
        assertEquals(10,won.inventory["rom.weapon.2"]);assertEquals(true,won.flags["rom.event.97.39.1"])
        assertEquals(true,won.flags["rom.event.97.39.1.dialogue.pending"])
        instrumentation.runOnMainSync{v.persistState();activity.finish()}
        val(a2,v2)=launch();completeDialogue(v2)
        assertEquals(GameView.Layer.MAP,v2.layer);assertEquals(won.money,v2.currentSnapshot().money)
        assertEquals(won.characters,v2.currentSnapshot().characters);assertEquals(won.inventory,v2.currentSnapshot().inventory)
        assertEquals(true,v2.currentSnapshot().flags["rom.event.97.39.1"])
        assertTrue(v2.currentSnapshot().flags["rom.event.97.39.1.dialogue.pending"]!=true)
        assertEquals("Original world-resume reset, not Boss initialization reset",0,v2.currentSnapshot().encounterSteps)
        val settled=v2.currentSnapshot();tap(v2,center(layoutFor(v2).buttons.getValue(Key.A)));completeDialogue(v2)
        assertEquals(GameView.Layer.MAP,v2.layer);assertEquals(settled,v2.currentSnapshot())
        // Boss defeat without original manual save uses the preserved new-game branch.
        instrumentation.runOnMainSync{assertTrue(v2.restoreSnapshot(settled.copy(
            characters=listOf(v2.content.initialPlayer.copy(hp=1)),flags=mapOf("opening.intro.seen" to true),inventory=emptyMap(),money=123)))}
        tap(v2,center(layoutFor(v2).buttons.getValue(Key.A)));completeDialogue(v2)
        val p2=p.get(v2) as BattlePresentation
        waitCommands(v2)
        val defeatDeadline=SystemClock.elapsedRealtime()+60000
        val dyingFight=f.get(v2) as OpeningBattle
        while(dyingFight.phase==BattlePhase.TARGET){
            assertTrue("Bounded controlled HP1 defeat flow",SystemClock.elapsedRealtime()<defeatDeadline)
            if(p2.screen in listOf(BattlePresentation.Screen.COMMAND,BattlePresentation.Screen.TARGET))
                chooseAttack(v2,dyingFight.enemies.first{it.hp>0}.slot)
            SystemClock.sleep(40)
        }
        waitCommands(v2)
        assertEquals(BattlePresentation.Screen.RESULT,p2.screen)
        assertEquals(BattlePhase.DEFEAT,dyingFight.phase)
        assertEquals(114,v2.currentSnapshot().mapId);assertEquals(20,v2.currentSnapshot().characters.first().hp)
        assertEquals(0,v2.currentSnapshot().money);assertTrue(v2.currentSnapshot().inventory.isEmpty())
        assertTrue(v2.currentSnapshot().flags["rom.event.97.39.1"]!=true)
        instrumentation.runOnMainSync{a2.finish()}
    }

    /** Controlled gesture/HP fixture, never used as proof of normal route or real stats. */
    fun testControlledWholly08PartyAdvancesWithoutTouchCommand(){
        val(activity,v)=launch();val rules=v.content.battle!!
        val zone=rules.zones.first{it.mapId==23&&it.groups.any{g->g.members.any{it.enemyId==29}}}
        val group=zone.groups.first{it.members.size==1&&it.members.single().enemyId==29}
        val actors=listOf(v.content.initialPlayer,v.content.joinCharacters.getValue("xiaolongnv"))
            .map{it.copy(hp=1000,maxHp=1000,statusMask=8)} // Isolated status fixture only.
        val fight=OpeningBattle(group,rules,actors.first(),0,0)
        fight.configureParty(actors,mapOf("nezha" to 0,"xiaolongnv" to 1),
            actors.associate{it.id to 0},actors.associate{it.id to 0})
        val p=BattlePresentation();val enemyHp=fight.enemies.single().hp
        fun field(name:String,value:Any?){GameView::class.java.getDeclaredField(name).apply{isAccessible=true}.set(v,value)}
        instrumentation.runOnMainSync{
            field("battle",fight);field("battlePresentation",p);field("battleID","controlled-all08")
            field("battleCommitted",false);field("storyBattle",null);field("layer",GameView.Layer.BATTLE)
            v.input.clear();p.tick(400)
        }
        val deadline=SystemClock.elapsedRealtime()+20000
        var advanced=false
        while(SystemClock.elapsedRealtime()<deadline){
            instrumentation.runOnMainSync{advanced=fight.inputRevision>0}
            if(advanced)break
            SystemClock.sleep(40)
        }
        assertTrue("Ready controller must advance original enemy/recovery without a touch",advanced)
        assertEquals(enemyHp,fight.enemies.single().hp)
        assertEquals(0,fight.herbsConsumed)
        screenshot(v,"world-hell-village2-controlled-all08-progress")
        instrumentation.runOnMainSync{activity.finish()}
    }
    fun testControlledMobileBattleTouchAndSnapshots(){
        val(activity,v)=launch();val rules=v.content.battle!!
        val group=rules.groups.first{g->g.members.groupBy{it.enemyId}.values.any{it.size>1}}
        val hero=v.content.initialPlayer.copy(hp=200,maxHp=200)
        val fight=OpeningBattle(group,rules,hero,2);val p=BattlePresentation()
        fun field(name:String,value:Any?){GameView::class.java.getDeclaredField(name).apply{isAccessible=true}.set(v,value)}
        instrumentation.runOnMainSync{
            field("battle",fight);field("battlePresentation",p);field("battleID","controlled-mobile-gesture")
            field("battleCommitted",false);field("storyBattle",null);field("layer",GameView.Layer.BATTLE)
            v.input.clear();p.tick(400)
        }
        val target=fight.enemies.first();val point=center(v.battleTargetBounds(target.slot))
        val before=fight.hero;val enemies=fight.enemies.map{it.hp};val rev=p.revision
        send(v,MotionEvent.ACTION_DOWN,listOf(point));send(v,MotionEvent.ACTION_CANCEL,listOf(point));send(v,MotionEvent.ACTION_UP,listOf(point))
        assertEquals(rev,p.revision);assertEquals(enemies,fight.enemies.map{it.hp})
        send(v,MotionEvent.ACTION_DOWN,listOf(point));send(v,MotionEvent.ACTION_MOVE,listOf(Pair(point.first+100,point.second)));send(v,MotionEvent.ACTION_UP,listOf(point))
        assertEquals(rev,p.revision)
        send(v,MotionEvent.ACTION_DOWN,listOf(point))
        send(v,MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(point,point))
        send(v,MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(point,point));send(v,MotionEvent.ACTION_UP,listOf(point))
        assertEquals(rev,p.revision)
        tap(v,center(v.battleCommandBounds(4)));tap(v,center(v.battleTargetBounds(fight.enemies.last().slot)))
        assertEquals(before,fight.hero);assertEquals(enemies,fight.enemies.map{it.hp});assertEquals(BattlePresentation.Screen.COMMAND,p.screen)
        screenshot(v,"mobile-controlled-independent-information");tap(v,center(v.battleInfoCloseBounds()))
        tap(v,point);assertEquals(before,fight.hero);assertEquals(enemies,fight.enemies.map{it.hp})
        assertEquals(0,fight.inputRevision);assertEquals(BattlePresentation.Screen.COMMAND,p.screen)
        screenshot(v,"mobile-controlled-default-target-information");tap(v,center(v.battleInfoCloseBounds()))
        tap(v,center(v.battleCommandBounds(0)))
        val armedRevision=p.revision
        send(v,MotionEvent.ACTION_DOWN,listOf(point));send(v,MotionEvent.ACTION_CANCEL,listOf(point));send(v,MotionEvent.ACTION_UP,listOf(point))
        assertEquals(armedRevision,p.revision);assertEquals(enemies,fight.enemies.map{it.hp});assertEquals(0,fight.inputRevision)
        tap(v,point);assertEquals(BattlePresentation.Screen.ACTING,p.screen)
        val computed=fight.hero;val computedEnemies=fight.enemies.map{it.hp}
        repeat(10){send(v,MotionEvent.ACTION_UP,listOf(point));tap(v,point)}
        assertEquals(computed,fight.hero);assertEquals(computedEnemies,fight.enemies.map{it.hp})
        screenshot(v,"mobile-controlled-action")
        instrumentation.runOnMainSync{v.active=false};val elapsed=p.elapsedMs;SystemClock.sleep(300);assertEquals(elapsed,p.elapsedMs)
        send(v,MotionEvent.ACTION_UP,listOf(point));instrumentation.runOnMainSync{v.active=true}
        for(i in 0..200){if(p.screen!=BattlePresentation.Screen.ACTING)break;SystemClock.sleep(25)}
        assertEquals(computed,fight.hero);assertEquals(computedEnemies,fight.enemies.map{it.hp})
        assertEquals(BattlePresentation.Screen.COMMAND,p.screen);assertEquals(0,p.command)
        assertEquals(computedEnemies,fight.enemies.map{v.battleVisibleHp(it.slot)})
        instrumentation.runOnMainSync{activity.finish()}
    }
    /** Isolated gesture/scheduler fixture; injected resources are NOT normal-play proof. */
    fun testControlledBindingItemSelectionCancelAndSingleActorCommand(){
        val(activity,v)=launch();val base=v.currentSnapshot();val rules=v.content.battle!!
        val id="rom.special.9";val item=v.content.itemDefinitions.getValue(id)
        val hero=v.content.initialPlayer.copy(hp=2000,maxHp=2000,strength=200,agility=200)
        val girl=v.content.joinCharacters.getValue("xiaolongnv").copy(hp=2000,maxHp=2000,strength=200,agility=100)
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(base.copy(characters=listOf(hero,girl),inventory=mapOf(id to 1,HerbUse.ID to 2))))}
        val checkpoint=v.currentSnapshot();val boss=rules.storyBattles.getValue("rom.npc.76.0")
        val fight=OpeningBattle(boss.group,rules,hero,0,0)
        fight.configureParty(listOf(hero,girl),mapOf(hero.id to 0,girl.id to 1),
            mapOf(hero.id to 0,girl.id to 0),mapOf(hero.id to 0,girl.id to 0))
        val p=BattlePresentation()
        fun field(name:String,value:Any?){GameView::class.java.getDeclaredField(name).apply{isAccessible=true}.set(v,value)}
        instrumentation.runOnMainSync{field("battle",fight);field("battlePresentation",p);field("battleID","controlled-binding-gesture")
            field("battleCommitted",false);field("storyBattle",null);field("layer",GameView.Layer.BATTLE);p.tick(400)}
        assertTrue(fight.bindingAvailable(1,item))
        tap(v,center(v.battleCommandBounds(2)));scrollToBattleItem(v,id);tap(v,center(v.battleItemBounds(id)))
        assertEquals(checkpoint,v.currentSnapshot());assertEquals(0,fight.inputRevision)
        tap(v,center(v.battleItemCloseBounds()));assertEquals(0,fight.inputRevision)
        tap(v,center(v.battleCommandBounds(2)));scrollToBattleItem(v,id);tap(v,center(v.battleItemBounds(id)))
        val action=center(v.battleItemUseBounds())
        send(v,MotionEvent.ACTION_DOWN,listOf(action));send(v,MotionEvent.ACTION_CANCEL,listOf(action));send(v,MotionEvent.ACTION_UP,listOf(action))
        assertEquals(0,fight.inputRevision);assertEquals(checkpoint,v.currentSnapshot())
        tap(v,action);assertEquals(1,fight.inputRevision);assertEquals(girl.id,fight.inputHero!!.id)
        repeat(10){send(v,MotionEvent.ACTION_UP,listOf(action))};assertEquals(1,fight.inputRevision)
        assertEquals(checkpoint,v.currentSnapshot());assertEquals(BattlePresentation.Screen.COMMAND,p.screen)
        instrumentation.runOnMainSync{v.active=false};SystemClock.sleep(200)
        assertEquals(1,fight.inputRevision);assertEquals(checkpoint,v.currentSnapshot())
        instrumentation.runOnMainSync{v.active=true}
        chooseAttack(v,fight.enemies.first().slot)
        assertEquals(BattlePresentation.Screen.ACTING,p.screen)
        assertEquals(BattleActionKind.SPECIAL,p.action!!.kind);assertEquals(hero.id,p.action!!.actorId)
        assertEquals(checkpoint,v.currentSnapshot());assertEquals(0,fight.herbsConsumed)
        assertEquals(checkpoint.inventory,fight.inventoryAfterBattle(checkpoint.inventory))
        screenshot(v,"world-island-controlled-binding-real-touch-single-command")
        instrumentation.runOnMainSync{activity.finish()}
    }
    /** Ordered-item and touch/persistence fixture; no normal-play or real-player-save claim. */
    fun testControlledMobileBattleHerbAndSave(){
        val(activity,v)=launch();val base=v.currentSnapshot();val rules=v.content.battle!!
        val story=rules.storyBattles.getValue("rom.npc.97.0")
        val hero=v.content.initialPlayer.copy(level=8,experience=702,hp=30,maxHp=100,strength=250,agility=30,equipment=EquipmentState(2,-1,0,28))
        instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(base.copy(characters=listOf(hero),inventory=mapOf(HerbUse.ID to 2))))}
        val checkpoint=v.currentSnapshot();val fight=OpeningBattle(story.group,rules,hero,20,3);val p=BattlePresentation()
        fun field(name:String,value:Any?){GameView::class.java.getDeclaredField(name).apply{isAccessible=true}.set(v,value)}
        instrumentation.runOnMainSync{field("battle",fight);field("battlePresentation",p);field("battleID","controlled-mobile-herb")
            field("battleCommitted",false);field("storyBattle",null);field("layer",GameView.Layer.BATTLE);p.tick(400)}
        tap(v,center(v.battleCommandBounds(2)));tap(v,center(v.battleItemBounds(HerbUse.ID)))
        assertEquals(checkpoint,v.currentSnapshot());assertEquals(2,v.battleHerbCount())
        tap(v,center(v.battleItemCloseBounds()));assertEquals(0,fight.herbsConsumed)
        tap(v,center(v.battleCommandBounds(2)));tap(v,center(v.battleItemBounds(HerbUse.ID)))
        val action=center(v.battleItemUseBounds());send(v,MotionEvent.ACTION_DOWN,listOf(action));send(v,MotionEvent.ACTION_CANCEL,listOf(action));send(v,MotionEvent.ACTION_UP,listOf(action))
        assertEquals(0,fight.herbsConsumed)
        tap(v,action);assertEquals(1,fight.herbsConsumed);assertEquals(1,v.battleHerbCount());assertEquals(BattlePresentation.Screen.ACTING,p.screen)
        assertEquals(BattleActionKind.HEAL,p.action!!.kind);assertEquals(80,p.action!!.heroHp)
        repeat(10){send(v,MotionEvent.ACTION_UP,listOf(action));tap(v,action)};assertEquals(1,fight.herbsConsumed)
        assertEquals("Pending HP and item changes must not make a half-saved checkpoint",checkpoint,v.currentSnapshot())
        screenshot(v,"mobile-controlled-battle-herb-heal")
        instrumentation.runOnMainSync{v.active=false};val elapsed=p.elapsedMs;SystemClock.sleep(250);assertEquals(elapsed,p.elapsedMs)
        instrumentation.runOnMainSync{v.active=true}
        for(i in 0..200){if(p.screen!=BattlePresentation.Screen.ACTING)break;SystemClock.sleep(25)}
        assertEquals(BattlePresentation.Screen.COMMAND,p.screen)
        val afterUse=fight.hero.hp;assertTrue(afterUse<=80);assertEquals(1,v.battleHerbCount())
        var committed=false
        repeat(30){
            var target:Int?=null
            instrumentation.runOnMainSync{if(v.layer==GameView.Layer.BATTLE&&p.screen==BattlePresentation.Screen.COMMAND)
                target=fight.enemies.first{it.hp>0}.slot}
            target?.let{chooseAttack(v,it)}
            SystemClock.sleep(250)
            if(p.screen==BattlePresentation.Screen.RESULT)committed=true
        }
        assertTrue(committed);assertEquals(1,v.currentSnapshot().inventory[HerbUse.ID]);assertEquals(fight.hero,v.currentSnapshot().characters.first())
        val saved=v.currentSnapshot();instrumentation.runOnMainSync{v.persistState();activity.finish()}
        val(reopened,restored)=launch();assertEquals(saved,restored.currentSnapshot());instrumentation.runOnMainSync{reopened.finish()}
    }
    fun testMobileBattlePhoneSizeAndLargeFont(){
        val(activity,v)=launch();val font=v.resources.configuration.fontScale;val dp=v.resources.displayMetrics.density
        val screen=instrumentation.uiAutomation.takeScreenshot();assertEquals(2640,screen.width);assertEquals(1216,screen.height)
        val rules=v.content.battle!!;val story=rules.storyBattles.getValue("rom.npc.97.0");val fight=OpeningBattle(story.group,rules,v.content.initialPlayer,2)
        val p=BattlePresentation()
        fun field(name:String,value:Any?){GameView::class.java.getDeclaredField(name).apply{isAccessible=true}.set(v,value)}
        instrumentation.runOnMainSync{field("battle",fight);field("battlePresentation",p);field("battleID","controlled-mobile-font-$font")
            field("battleCommitted",false);field("storyBattle",story);field("layer",GameView.Layer.BATTLE);p.tick(400)}
        fun overlap(a:Box,b:Box)=a.x<b.x+b.w&&a.x+a.w>b.x&&a.y<b.y+b.h&&a.y+a.h>b.y
        val targets=(0..4).map{v.battleCommandBounds(it)}+fight.enemies.map{v.battleTargetBounds(it.slot)}
        targets.forEach{assertTrue(it.w>=48*dp);assertTrue(it.h>=48*dp)}
        for(i in targets.indices)for(j in i+1 until targets.size)assertFalse(overlap(targets[i],targets[j]))
        val eb=GameView::class.java.getDeclaredMethod("battleEnemyBox",BattleEnemy::class.java).apply{isAccessible=true}.invoke(v,fight.enemies.single()) as Box
        val bossTarget=v.battleTargetBounds(fight.enemies.single().slot)
        assertEquals(128f/112,eb.w/eb.h,.001f)
        assertTrue(eb.x>=bossTarget.x&&eb.y>=bossTarget.y&&eb.x+eb.w<=bossTarget.x+bossTarget.w&&eb.y+eb.h<=bossTarget.y+bossTarget.h)
        screenshot(v,"mobile-phone-boss-$font");tap(v,center(v.battleCommandBounds(2)))
        screenshot(v,"mobile-phone-medicine-$font");tap(v,center(v.battleItemBounds(HerbUse.ID)))
        screenshot(v,"mobile-phone-medicine-detail-$font")
        val medicine=GameView::class.java.getDeclaredMethod("battleItemLayout").apply{isAccessible=true}.invoke(v) as TouchModalLayout
        assertTrue("Medicine target/effect/full-HP rule need three readable rows",medicine.detail.h>=(14f*font*1.25f+4)*3*dp)
        assertFalse(overlap(medicine.detail,medicine.primary));assertFalse(overlap(medicine.detail,medicine.close))
        val inventoryBefore=v.battleHerbCount();val heroBefore=fight.hero;val medicineScroll=center(medicine.detail)
        send(v,MotionEvent.ACTION_DOWN,listOf(medicineScroll));send(v,MotionEvent.ACTION_MOVE,listOf(Pair(medicineScroll.first,medicineScroll.second-80*dp)))
        send(v,MotionEvent.ACTION_UP,listOf(Pair(medicineScroll.first,medicineScroll.second-80*dp)))
        assertEquals(inventoryBefore,v.battleHerbCount());assertEquals(heroBefore,fight.hero)
        screenshot(v,"mobile-phone-medicine-detail-scrolled-$font");tap(v,center(v.battleItemCloseBounds()))
        tap(v,center(v.battleCommandBounds(4)))
        val info=GameView::class.java.getDeclaredMethod("battleInfoLayout").apply{isAccessible=true}.invoke(v) as TouchModalLayout
        val infoTarget=v.battleTargetBounds(fight.enemies.single().slot)
        assertTrue(infoTarget.h>=48*dp);assertFalse(overlap(infoTarget,v.battleInfoCloseBounds()))
        screenshot(v,"mobile-phone-info-$font")
        val scroll=center(info.detail)
        send(v,MotionEvent.ACTION_DOWN,listOf(scroll));send(v,MotionEvent.ACTION_MOVE,listOf(Pair(scroll.first,scroll.second-80*dp)))
        send(v,MotionEvent.ACTION_UP,listOf(Pair(scroll.first,scroll.second-80*dp)))
        assertEquals(BattlePresentation.Screen.COMMAND,p.screen);assertEquals(fight.hero.hp,p.action?.heroHp?:fight.hero.hp)
        screenshot(v,"mobile-phone-info-scrolled-$font")
        tap(v,center(v.battleInfoCloseBounds()));instrumentation.runOnMainSync{field("battle",null);field("layer",GameView.Layer.MAP)}
        screenshot(v,"mobile-phone-hud-$font");tap(v,center(v.hudBounds()));screenshot(v,"mobile-phone-growth-$font")
        val detail=(GameView::class.java.getDeclaredMethod("modalLayout").apply{isAccessible=true}.invoke(v) as TouchModalLayout).detail;val start=center(detail)
        send(v,MotionEvent.ACTION_DOWN,listOf(start));send(v,MotionEvent.ACTION_MOVE,listOf(Pair(start.first,start.second-80*dp)))
        send(v,MotionEvent.ACTION_UP,listOf(Pair(start.first,start.second-80*dp)))
        screenshot(v,"mobile-phone-growth-scrolled-$font")
        File(instrumentation.targetContext.getExternalFilesDir(null),"mobile-phone-$font.json").writeText(
            org.json.JSONObject().put("kind","CONTROLLED_LAYOUT_EMULATOR_NOT_REAL_PHONE").put("screenWidth",screen.width).put("screenHeight",screen.height)
                .put("windowWidth",v.width).put("windowHeight",v.height).put("fontScale",font).put("density",dp).put("minTouchDp",48)
                .put("medicineDetailHeightDp",medicine.detail.h/dp).put("medicineTextRowsVisible",medicine.detail.h/((14f*font*1.25f+4)*dp)).toString())
        instrumentation.runOnMainSync{activity.finish()}
    }
    /** Native-data layout fixtures on the actual isolated phone-sized AVD; never normal-route proof. */
    fun testControlledBattlePartyPhoneSizeAndLargeFont(){
        val(activity,v)=launch();val font=v.resources.configuration.fontScale;val dp=v.resources.displayMetrics.density
        val screen=instrumentation.uiAutomation.takeScreenshot()
        assertEquals(2640,screen.width);assertEquals(1216,screen.height)
        assertTrue("Actual GameView must match the phone-sized window",v.width>=2400&&v.height>=1000)
        val c=v.content;val rules=c.battle!!
        val actors=OriginalJiangJoin.FULL_PARTY.map{if(it==c.initialPlayer.id)c.initialPlayer else c.joinCharacters.getValue(it)}
        val six=(rules.groups+rules.zones.flatMap{it.groups}).first{it.members.size==6&&it.members.all{m->m.enemyId in c.enemyGraphics}}
        val boss=rules.storyBattles.getValue("rom.npc.97.0").group
        val base=v.currentSnapshot();val reports=org.json.JSONArray()
        fun field(name:String,value:Any?){GameView::class.java.getDeclaredField(name).apply{isAccessible=true}.set(v,value)}
        fun scene():BattleSceneLayout {
            val layout=GameView::class.java.getDeclaredMethod("battleScene").apply{isAccessible=true}.invoke(v) as? BattleSceneLayout
            assertNotNull("Native phone safe-area layout: font=$font, party=${v.currentSnapshot().characters.size}",layout)
            return layout!!
        }
        fun overlap(a:Box,b:Box)=a.x<b.x+b.w&&a.x+a.w>b.x&&a.y<b.y+b.h&&a.y+a.h>b.y
        fun json(box:Box)=JSONObject().put("x",box.x).put("y",box.y).put("width",box.w).put("height",box.h)
        fun source(count:Int)=base.copy(mapId=7,x=23*16+8,y=7*16+8,direction=Key.DOWN,characters=actors.take(count),
            inventory=mapOf(OriginalYangJoin.ITEM_ID to 1,HerbUse.ID to 7),money=8342,interiorContext=null,terrainMode=0,
            flags=mapOf("opening.intro.seen" to true,OriginalYangJoin.CONTEXT_FLAG to (count>=3),OriginalYangJoin.USED_FLAG to (count>=3),
                OriginalSceneItems.PLAGUE_FLAG to true,OriginalJiangJoin.PANXI_FLAG to true,OriginalJiangJoin.KING_FLAG to true,
                OriginalJiangJoin.PANXI_FOUR_FLAG to (count==4),OriginalJiangJoin.PANXI_THREE_FLAG to (count==3),"rom.map.7.flag.128" to true))
        for(count in 1..4)for(group in listOf(six,boss)){
            val fixture=source(count)
            assertTrue("Layout fixture must satisfy the original actor/context and scene guards: party=$count",fixture.validate(c))
            lateinit var fight:OpeningBattle
            val presentation=BattlePresentation()
            var restored=false
            instrumentation.runOnMainSync{
                field("battle",null);field("layer",GameView.Layer.MAP)
                restored=v.restoreSnapshot(fixture)
                if(restored){
                    fight=GameView::class.java.getDeclaredMethod("createPartyBattle",EncounterGroup::class.java,BattleContent::class.java)
                        .apply{isAccessible=true}.invoke(v,group,rules) as OpeningBattle
                    GameView::class.java.getDeclaredMethod("resetBattleUiSelection").apply{isAccessible=true}.invoke(v)
                    field("battle",fight);field("battlePresentation",presentation);field("battleID","controlled-party-font-$font-$count-${group.id}")
                    field("battleInfoOpen",false);field("battleItemsOpen",false);field("battleCommitted",false);field("storyBattle",null)
                    field("layer",GameView.Layer.BATTLE);presentation.tick(400)
                }
            }
            assertTrue("Restore the validated layout fixture on the actual GameView: party=$count",restored)
            val before=v.currentSnapshot();val layout=scene()
            val targets=layout.partyCards+layout.touch.commands+layout.touch.enemies
            for(box in targets){assertTrue(box.w>=48*dp&&box.h>=48*dp)
                assertTrue(box.x>=0&&box.y>=0&&box.x+box.w<=v.width&&box.y+box.h<=v.height)}
            for(i in targets.indices)for(j in i+1 until targets.size)assertFalse(overlap(targets[i],targets[j]))
            for(enemy in fight.enemies){
                val image=c.enemyGraphics.getValue(enemy.definition.id)
                val graphic=GameView::class.java.getDeclaredMethod("battleEnemyBox",BattleEnemy::class.java).apply{isAccessible=true}.invoke(v,enemy) as Box
                val target=v.battleTargetBounds(enemy.slot)
                assertEquals(image.width.toFloat()/image.height,graphic.w/graphic.h,.001f)
                assertTrue(graphic.x>=target.x&&graphic.y>=target.y&&graphic.x+graphic.w<=target.x+target.w&&graphic.y+graphic.h<=target.y+target.h)
                if(layout.compact){
                    val label=battleEnemySceneLayout(target,dp,font,true,false).label
                    val measured=android.graphics.Paint().apply{textSize=12*v.resources.displayMetrics.scaledDensity}
                    assertTrue("Compact enemy instance number must remain completely visible",measured.measureText("#${enemy.slot+1}")<=label.w)
                }
            }
            screenshot(v,"mobile-party-phone-$font-$count-${group.members.size}-native")
            tap(v,center(v.battleTargetBounds(fight.enemies.last().slot)))
            assertEquals(0,fight.inputRevision);assertEquals(before,v.currentSnapshot())
            tap(v,center(v.battleInfoCloseBounds()))
            if(count==4&&group===six){
                for(hero in fight.party){
                    tap(v,center(v.battlePartyCardBounds(hero.id)))
                    screenshot(v,"mobile-party-phone-$font-hero-${hero.id}")
                    assertEquals(0,fight.inputRevision);assertEquals(before,v.currentSnapshot())
                    tap(v,center(v.battleInfoCloseBounds()))
                }
            }
            reports.put(JSONObject().put("party",org.json.JSONArray(fight.party.map{it.id})).put("groupId",group.id)
                .put("enemies",org.json.JSONArray(fight.enemies.map{it.definition.id})).put("frame",json(layout.touch.frame))
                .put("cards",org.json.JSONArray(layout.partyCards.map{json(it)})).put("enemyField",json(layout.enemyField)).put("allyField",json(layout.allyField))
                .put("compactInstanceNumbers",layout.compact))
        }
        var rewardSourceRestored=false
        instrumentation.runOnMainSync{field("battle",null);field("layer",GameView.Layer.MAP);rewardSourceRestored=v.restoreSnapshot(source(4))}
        assertTrue("Restore the valid four-actor reward fixture",rewardSourceRestored)
        verifyControlledJiangFourActorBattle(v,"-phone-$font")
        File(instrumentation.targetContext.getExternalFilesDir(null),"mobile-party-phone-$font.json").writeText(
            JSONObject().put("kind","CONTROLLED_NATIVE_LAYOUT_EMULATOR_NOT_REAL_PHONE").put("screenWidth",screen.width)
                .put("screenHeight",screen.height).put("windowWidth",v.width).put("windowHeight",v.height).put("fontScale",font)
                .put("density",dp).put("cases",reports).put("fourActorInputAndRewardChecks","PASS").toString())
        instrumentation.runOnMainSync{activity.finish()}
    }

}
