package org.fengshen.dev

import android.content.Intent
import android.os.SystemClock
import android.test.InstrumentationTestCase
import android.view.MotionEvent
import android.view.ViewGroup
import android.graphics.Bitmap
import java.io.File

@Suppress("DEPRECATION")
class TouchTest:IsolatedGameTestCase(){
    private fun launch():Pair<MainActivity,GameView>{
        val activity=instrumentation.startActivitySync(Intent(instrumentation.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        var view:GameView?=null
        // A fresh emulator install may still be dex-optimizing while the content loader runs.
        for(i in 0..800){
            instrumentation.runOnMainSync{view=(activity.findViewById<ViewGroup>(android.R.id.content).getChildAt(0) as? GameView)?.takeIf{it.width>0&&it.height>0}}
            if(view!=null)break
            SystemClock.sleep(50)
        }
        assertNotNull(view)
        instrumentation.runOnMainSync{view!!.active=true;view!!.focused=true}
        if(view!!.layer==GameView.Layer.DIALOGUE)tap(view!!,Pair(view!!.width*.5f,view!!.height*.5f))
        return activity to view!!
    }
    private fun send(v:GameView,action:Int,points:List<Pair<Float,Float>>){
        instrumentation.runOnMainSync{
            val props=Array(points.size){i->MotionEvent.PointerProperties().apply{id=i;toolType=MotionEvent.TOOL_TYPE_FINGER}}
            val coords=Array(points.size){i->MotionEvent.PointerCoords().apply{x=points[i].first;y=points[i].second;pressure=1f;size=1f}}
            val time=SystemClock.uptimeMillis()
            val event=MotionEvent.obtain(time,time,action,points.size,props,coords,0,0,1f,1f,0,0,0,0)
            v.dispatchTouchEvent(event);event.recycle()
        }
    }
    private fun tap(v:GameView,p:Pair<Float,Float>){send(v,MotionEvent.ACTION_DOWN,listOf(p));send(v,MotionEvent.ACTION_UP,listOf(p))}
    private fun layoutFor(v:GameView)=layout(v.width,v.height,v.resources.displayMetrics.density,v.safe,DisplayMode.FULL,ControlConfig())
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
        var beforeMap=0;var beforeSeq=0L
        instrumentation.runOnMainSync{beforeMap=v.world.mapId;beforeSeq=v.world.completedStepSeq}
        send(v,MotionEvent.ACTION_DOWN,listOf(middle));send(v,MotionEvent.ACTION_MOVE,listOf(point))
        var started=false
        for(i in 0..100){
            // World completion sets remaining=0 before dispatching its exit, in the same UI callback.
            // Observe that callback atomically; a background read can see the doorway before the map changes.
            instrumentation.runOnMainSync{started=v.world.remaining>0||v.world.mapId!=beforeMap||
                v.world.completedStepSeq!=beforeSeq||v.layer==GameView.Layer.BATTLE}
            if(started)break
            SystemClock.sleep(5)
        }
        send(v,MotionEvent.ACTION_UP,listOf(point))
        assertTrue("No step: map=$beforeMap x=${v.world.x} y=${v.world.y} message=${v.world.message}",started)
        var remaining=0
        for(i in 0..100){
            instrumentation.runOnMainSync{remaining=v.world.remaining}
            if(remaining==0)break
            SystemClock.sleep(5)
        }
        instrumentation.runOnMainSync{assertEquals("Unfinished normal touch step",0,v.world.remaining)}
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
        instrumentation.runOnMainSync{v.persistState();activity.finish()}
    }
    fun testUpgradeKeepsPreviousSave(){
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"town02-upgrade-source.json")
        assertTrue("Old APK must first export its normal save",file.exists())
        val old=SaveSnapshot.parse(file.readText())
        val(activity,v)=launch()
        assertEquals(old.copy(contentVersion=v.content.scene.version),v.currentSnapshot())
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
    private fun normalTownShops(useHerb:Boolean,touchUx:Boolean=false,innSupply:Boolean=false){
        instrumentation.targetContext.getSharedPreferences("opening-local-save",0).edit().clear().commit()
        val(activity,v)=launch()
        fun capture(name:String){
            SystemClock.sleep(250)
            File(instrumentation.targetContext.getExternalFilesDir(null),"town01-$name.png").outputStream().use{
                instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
        }
        fun finishFight(){
            if(v.layer!=GameView.Layer.BATTLE)return
            val f=GameView::class.java.getDeclaredField("battle").apply{isAccessible=true}
            val p=GameView::class.java.getDeclaredField("battlePresentation").apply{isAccessible=true}
            for(i in 0..1000){
                if(v.layer!=GameView.Layer.BATTLE)break
                val fight=f.get(v) as OpeningBattle;val presentation=p.get(v) as BattlePresentation
                if(presentation.screen !in listOf(BattlePresentation.Screen.ENTRY,BattlePresentation.Screen.ACTING)){
                    assertTrue("Town route must survive normally",fight.phase!=BattlePhase.DEFEAT)
                    if(presentation.screen in listOf(BattlePresentation.Screen.COMMAND,BattlePresentation.Screen.TARGET))
                        tap(v,center(v.battleTargetBounds(fight.enemies.first{it.hp>0}.slot)))
                    else if(presentation.screen==BattlePresentation.Screen.RESULT)SystemClock.sleep(40)
                }
                SystemClock.sleep(40)
            }
            assertEquals(GameView.Layer.MAP,v.layer)
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
        walkTo(202,130);assertEquals("Town entrance at actual ${v.world.x/16},${v.world.y/16}",0,v.world.mapId)
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
                for(i in 0..120){
                    val hero=v.currentSnapshot().characters.first()
                    if(hero.hp in 1 until hero.maxHp)break
                    val key=if(v.world.y/16==130)Key.UP else Key.DOWN
                    step(key)
                }
                assertTrue("Normal encounters must produce a living injured hero",v.currentSnapshot().characters.first().let{it.hp in 1 until it.maxHp})
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
    private fun screenshot(v:GameView,name:String){
        SystemClock.sleep(350)
        File(instrumentation.targetContext.getExternalFilesDir(null),name+".png").outputStream().use{
            instrumentation.uiAutomation.takeScreenshot().compress(Bitmap.CompressFormat.PNG,100,it)}
    }
    private fun scrollToItem(v:GameView,id:String){
        val list=v.panelListBounds();val dp=v.resources.displayMetrics.density
        for(i in 0..60){
            if(v.panelItemBounds(id).h>=48*dp)return
            val distance=minOf(list.h*.64f,64*dp)
            val down=Pair(list.x+list.w*.5f,list.y+list.h*.82f);val up=Pair(down.first,down.second-distance)
            send(v,MotionEvent.ACTION_DOWN,listOf(down));send(v,MotionEvent.ACTION_MOVE,listOf(up));send(v,MotionEvent.ACTION_UP,listOf(up))
        };fail("Item $id never reached an accessible row: row=${v.panelItemBounds(id)}, list=$list, view=${v.width}x${v.height}, density=$dp, font=${v.resources.configuration.fontScale}")
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
                        }else tap(v,center(v.battleTargetBounds(fight.enemies.first{it.hp>0}.slot)))
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
    fun testNormalWorldSeaNorthFromVerifiedNanhaiSave(){
        val file=File(instrumentation.targetContext.getExternalFilesDir(null),"nanhai-expected-save.json")
        assertTrue("Same candidate normal Nanhai flow must first produce its save",file.exists())
        val source=SaveSnapshot.parse(file.readText());assertEquals(true,source.flags["rom.event.97.39.1"])
        val(activity,v)=launch();instrumentation.runOnMainSync{assertTrue(v.restoreSnapshot(source))}
        assertEquals(source,v.currentSnapshot())
        val started=SystemClock.elapsedRealtime();val events=org.json.JSONArray();var fights=0;var battleHerbs=0;var capturedPoison=false
        val f=GameView::class.java.getDeclaredField("battle").apply{isAccessible=true}
        val p=GameView::class.java.getDeclaredField("battlePresentation").apply{isAccessible=true}
        fun state(name:String){
            screenshot(v,"world-north-$name")
            events.put(org.json.JSONObject().put("name",name).put("elapsedMs",SystemClock.elapsedRealtime()-started)
                .put("androidUptimeMs",SystemClock.elapsedRealtime()).put("snapshot",v.currentSnapshot().json()))
            val hash=java.security.MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString(""){"%02x".format(it)}
            File(instrumentation.targetContext.getExternalFilesDir(null),"world-north-normal-index.json").writeText(
                org.json.JSONObject().put("kind","CONTINUATION_FROM_VERIFIED_NORMAL_NANHAI_SAVE")
                    .put("sourceFile","nanhai-expected-save.json").put("sourceSha256",hash).put("sourceSnapshot",source.json())
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
                    }else tap(v,center(v.battleTargetBounds(battle.enemies.first{it.hp>0}.slot)))
                }
                SystemClock.sleep(40)
            }
            assertEquals(GameView.Layer.MAP,v.layer);supply()
        }
        fun step(key:Key){stickStep(v,key);finishFight();supply()}
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
        walkTo(15,29);assertEquals(25,v.world.mapId);walkTo(39,42);assertEquals(16,v.world.mapId)
        walkTo(202,130);assertEquals(0,v.world.mapId)
        val entry=v.content.exits.first{it.fromMapId==0&&it.toMapId==19};walkTo(entry.triggerX,entry.triggerY)
        assertEquals(InteriorContext(0,24,25),v.currentSnapshot().interiorContext)
        val npc=v.content.npcs.first{it.mapId==19&&it.shopId!=null};walkTo(npc.interactionCell!!.first,npc.interactionCell.second)
        talk();assertEquals(GameView.Layer.SHOP,v.layer)
        tap(v,center(v.shopActionBounds(1)));scrollToShopItem(v,AntidoteUse.ID);tap(v,center(v.shopItemBounds(AntidoteUse.ID)))
        val count=minOf(10-(v.currentSnapshot().inventory[AntidoteUse.ID]?:0),v.currentSnapshot().money/20)
        assertTrue("Legitimate Boss/encounter earnings must support antidote supply",count>=2)
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
        File(instrumentation.targetContext.getExternalFilesDir(null),"world-north-expected-save.json").writeText(v.currentSnapshot().json().toString())
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
    private fun normalWorldBatchContinuation(west:Boolean){
        val root=instrumentation.targetContext.getExternalFilesDir(null)
        val sourceFile=File(root,"nanhai-expected-save.json")
        assertTrue("The same candidate's normal Nanhai recording must produce this checkpoint",sourceFile.exists())
        val sourceBytes=sourceFile.readBytes();val source=SaveSnapshot.parse(sourceBytes.toString(Charsets.UTF_8))
        val sourceHash=java.security.MessageDigest.getInstance("SHA-256").digest(sourceBytes).joinToString(""){"%02x".format(it)}
        assertEquals(true,source.flags["rom.event.97.39.1"])
        assertTrue("Normal source must precede West victory",source.flags["rom.event.96.40.2"]!=true)
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
                .put("kind","CONTINUATION_FROM_VERIFIED_NORMAL_NANHAI_SAVE").put("sourceFile",sourceFile.name)
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
            if(h.hp<=h.maxHp/2&&(s.inventory[HerbUse.ID]?:0)>0)medicine(HerbUse.ID)
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
                    val heal=fight.hero.hp<=fight.hero.maxHp/2||(boss&&bossHerbs==0&&fight.hero.hp<fight.hero.maxHp)
                    if(heal&&v.battleHerbCount()>0){
                        val before=fight.hero;val count=v.battleHerbCount()
                        tap(v,center(v.battleCommandBounds(2)));tap(v,center(v.battleItemBounds(HerbUse.ID)))
                        assertEquals(before,fight.hero);assertEquals(count,v.battleHerbCount())
                        tap(v,center(v.battleItemUseBounds()));assertEquals(count-1,v.battleHerbCount())
                        battleHerbs++;if(boss)bossHerbs++
                    }else tap(v,center(v.battleTargetBounds(fight.enemies.first{it.hp>0}.slot)))
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
                assertEquals("Unexpected map before route step",scene.mapId,v.world.mapId);step(key)
                if(v.world.mapId!=scene.mapId)assertEquals("Exit may only occur at the requested goal",keys.lastIndex,index)
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
        walkTo(15,29);assertEquals(25,v.world.mapId);walkTo(39,42);assertEquals(16,v.world.mapId)
        walkTo(202,130);assertEquals(0,v.world.mapId)
        // SecureRandom makes the legitimate prior run's earnings variable. If
        // needed, earn the shortfall in the already verified opening zone and pay
        // the existing inn; do not change money/HP or rely on a particular run's 303.
        fun neededSupplyMoney():Int {
            val saved=v.currentSnapshot()
            val pills=((if(west)6 else 3)-(saved.inventory[AntidoteUse.ID]?:0)).coerceAtLeast(0)*20
            return pills+if(west)(10-(saved.inventory[HerbUse.ID]?:0)).coerceAtLeast(0)*15 else 200
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
                step(if(v.world.y/16==130)Key.DOWN else Key.UP)
            }
            walkTo(202,130);assertEquals(0,v.world.mapId);state("normal-earned-supply-complete")
        }
        val supplyEntry=enterService(0,19)
        val antidotes=((if(west)6 else 3)-(v.currentSnapshot().inventory[AntidoteUse.ID]?:0)).coerceAtLeast(0)
        if(antidotes>0)trade(AntidoteUse.ID,true,antidotes)
        if(west){
            val herbs=(10-(v.currentSnapshot().inventory[HerbUse.ID]?:0)).coerceAtLeast(0)
            assertTrue("Normal South victory funds must buy intended West supply",v.currentSnapshot().money>=herbs*15)
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
                    else tap(v,center(v.battleTargetBounds(fight.enemies.first{it.hp>0}.slot)))
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
        state("verified-normal-source-loaded")
        walkTo(15,29);assertEquals(25,v.world.mapId);walkTo(39,42);assertEquals(16,v.world.mapId)
        walkTo(202,130);assertEquals(0,v.world.mapId)
        fun supplyCost():Int {
            val bag=v.currentSnapshot().inventory
            return (10-(bag[HerbUse.ID]?:0)).coerceAtLeast(0)*15+
                (10-(bag[AntidoteUse.ID]?:0)).coerceAtLeast(0)*20
        }
        // A normal player's preparation, not a new game gate. Actual original
        // fights earn every level/coin; original 4-liang inn restores HP. Level 9
        // is this recording's chosen safety margin, not a North access condition.
        if(v.currentSnapshot().characters.first().level<9||v.currentSnapshot().money<supplyCost()+8){
            training=true;state("normal-training-start")
            inn();walkTo(0,14);step(Key.LEFT);walkTo(200,130)
            var trainingSteps=0
            while(v.currentSnapshot().characters.first().level<9||v.currentSnapshot().money<supplyCost()+8){
                assertTrue("Bounded normal preparation exhausted; no resource grants",trainingSteps++<3000)
                if(v.currentSnapshot().characters.first().hp<=v.currentSnapshot().characters.first().maxHp*3/4){
                    walkTo(202,130);assertEquals(0,v.world.mapId);inn()
                    walkTo(0,14);step(Key.LEFT);walkTo(200,130)
                }
                step(if(v.world.y/16==130)Key.DOWN else Key.UP)
            }
            walkTo(202,130);assertEquals(0,v.world.mapId);training=false;state("normal-training-complete")
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
        step(Key.DOWN);walkTo(2,4);assertEquals(GameView.Layer.MAP,v.layer)
        assertEquals(claimed.characters,v.currentSnapshot().characters)
        assertEquals(claimed.inventory,v.currentSnapshot().inventory);assertEquals(claimed.money,v.currentSnapshot().money)
        assertEquals(claimed.flags,v.currentSnapshot().flags);state("trigger-reentered-no-battle-or-reward")
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
                }else tap(v,center(v.battleTargetBounds(fight.enemies.first{it.hp>0}.slot)))
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
        val box=GameView::class.java.getDeclaredMethod("battleBox").apply{isAccessible=true}.invoke(v) as Box;val scale=box.w/256f
        val fight=f.get(v) as OpeningBattle;val beforeEscapeHp=fight.hero.hp
        val enemyBox=GameView::class.java.getDeclaredMethod("battleEnemyBox",BattleEnemy::class.java)
            .apply{isAccessible=true}.invoke(v,fight.enemies.single()) as Box
        assertEquals(box.x+64*scale,enemyBox.x,.01f);assertEquals(box.y,enemyBox.y,.01f)
        assertTrue("Full real Boss must fit above unchanged controls",enemyBox.y+enemyBox.h<=box.y+148*scale)
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
            tap(v,center(v.battleTargetBounds(fight.enemies.single().slot)));waitCommands()
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
                tap(v2,center(v2.battleTargetBounds(dyingFight.enemies.first{it.hp>0}.slot)))
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
            var target:Pair<Float,Float>?=null
            instrumentation.runOnMainSync{if(v.layer==GameView.Layer.BATTLE&&p.screen==BattlePresentation.Screen.COMMAND)
                target=center(v.battleTargetBounds(fight.enemies.first{it.hp>0}.slot))}
            target?.let{tap(v,it)}
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
        assertEquals(128f/112,eb.w/eb.h,.001f);assertTrue(eb.y+eb.h<=v.battleTargetBounds(fight.enemies.single().slot).y)
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

}
