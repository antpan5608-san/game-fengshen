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
    private fun tabPoint(v:GameView,index:Int):Pair<Float,Float>{
        val b=v.characterPanelBounds();val dp=v.resources.displayMetrics.density;val w=(b.w-16*dp)/4
        return Pair(b.x+8*dp+w*(index+.5f),b.y+b.h*.4525f)
    }
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
        val beforeMap=v.world.mapId
        send(v,MotionEvent.ACTION_DOWN,listOf(middle));send(v,MotionEvent.ACTION_MOVE,listOf(point))
        var started=false
        for(i in 0..100){
            if(v.world.remaining>0 || v.world.mapId!=beforeMap){started=true;break}
            SystemClock.sleep(5)
        }
        send(v,MotionEvent.ACTION_UP,listOf(point))
        assertTrue("No step: map=$beforeMap x=${v.world.x} y=${v.world.y} message=${v.world.message}",
            started || v.layer==GameView.Layer.BATTLE)
        for(i in 0..100){if(v.world.remaining==0)break;SystemClock.sleep(5)}
        assertEquals(0,v.world.remaining)
    }
    fun testNormalOpeningRouteGiftAndMap16Encounter(){normalOpeningBattle(false)}
    fun testNormalOpeningEscapeAndDefeat(){normalOpeningBattle(true)}
    fun testNormalTownShopsBuySellAndReturn(){normalTownShops(false)}
    fun testNormalHerbSupplyLoop(){normalTownShops(true)}
    fun testHerbColdStartMatchesNormalSave(){
        val expectedFile=File(instrumentation.targetContext.getExternalFilesDir(null),"town02-expected-save.json")
        assertTrue("Run the normal supply test before an external force-stop",expectedFile.exists())
        val expected=SaveSnapshot.parse(expectedFile.readText())
        val(activity,v)=launch()
        assertEquals(expected,v.currentSnapshot())
        assertEquals(0,v.currentSnapshot().inventory[HerbUse.ID]?:0)
        instrumentation.runOnMainSync{activity.finish()}
    }
    private fun normalTownShops(useHerb:Boolean){
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
                    tap(v,center(layoutFor(v).buttons.getValue(Key.A)))
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
            walkTo(entry.triggerX,entry.triggerY);assertEquals(mid,v.world.mapId)
            val keeper=v.content.npcs.first{it.mapId==mid};walkTo(keeper.interactionCell!!.first,keeper.interactionCell.second)
            talk();assertEquals(GameView.Layer.SHOP,v.layer);assertFalse(v.visibleMapControls());assertNull(v.input.direction())
            fun action(i:Int)=tap(v,center(v.shopActionBounds(i)))
            action(1);if(mid==17)action(8);capture("shop$mid")
            val definition=v.content.shops.values.first{it.mapId==mid};val item=v.content.itemDefinitions.getValue(definition.items[if(mid==17)1 else 0])
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
            repeat(10){action(4)};assertEquals(once,v.currentSnapshot())
            assertEquals(before.characters,once.characters) // Native purchase does not auto-equip.
            capture("bought$mid");action(6);action(5)
            if(mid==19 && !useHerb){
                action(2);capture("sell");val prior=v.currentSnapshot();action(4)
                assertEquals(prior.money+7,v.currentSnapshot().money)
                assertEquals((prior.inventory[item.id]?:0)-1,v.currentSnapshot().inventory[item.id]?:0)
                val sold=v.currentSnapshot();repeat(10){action(4)};assertEquals(sold,v.currentSnapshot())
                action(6);action(5)
            }
            action(3);assertEquals(GameView.Layer.MAP,v.layer)
            val exit=v.content.exits.first{it.fromMapId==mid};walkTo(exit.triggerX,exit.triggerY)
            assertEquals(0,v.world.mapId);assertEquals(entry.triggerX,v.world.x/16);assertEquals(entry.triggerY,v.world.y/16)
            capture("returned$mid")
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
            val row=Pair(panel.x+panel.w*.5f,panel.y+panel.h*(.555f+index*.075f))
            val action=Pair(panel.x+panel.w*.5f,panel.y+panel.h*.8975f)
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
            repeat(10){tap(v,action)};assertEquals(used,v.currentSnapshot())
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
        tap(v,Pair(panel.x+panel.w*.5f,panel.y+panel.h*.8975f)) // Existing explicit remove action.
        assertEquals(-1,v.currentSnapshot().characters.first().equipment!!.rightHand)
        tap(v,tabPoint(v,2));capture("inventory")
        val index=v.currentSnapshot().inventory.filterValues{it>0}.toSortedMap().keys.indexOf("rom.weapon.1")
        assertTrue(index in 0..3)
        tap(v,Pair(panel.x+panel.w*.5f,panel.y+panel.h*(.555f+index*.075f)))
        tap(v,Pair(panel.x+panel.w*.5f,panel.y+panel.h*.8975f))
        assertEquals(1,v.currentSnapshot().characters.first().equipment!!.rightHand)
        assertEquals(2,v.currentSnapshot().inventory[OpeningEquipment.KNIFE_ID])
        assertEquals(0,v.currentSnapshot().inventory["rom.weapon.1"]?:0)
        tap(v,tabPoint(v,1));capture("equipped-handknife")
        instrumentation.runOnMainSync{v.handleBack()}
        instrumentation.runOnMainSync{v.persistState();activity.finish()}
    }
    fun testControlledOneHitAndSameKindInstances(){
        val(activity,v)=launch();val rules=v.content.battle!!
        fun field(name:String,value:Any){GameView::class.java.getDeclaredField(name).apply{isAccessible=true}.set(v,value)}
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
        val panel=v.characterPanelBounds();val action=Pair(panel.x+panel.w/2,panel.y+panel.h*.8975f)
        tap(v,action)
        assertEquals(-1,v.currentSnapshot().characters.first().equipment?.rightHand)
        assertEquals(2,v.currentSnapshot().inventory[OpeningEquipment.KNIFE_ID])
        tap(v,action);assertEquals(2,v.currentSnapshot().inventory[OpeningEquipment.KNIFE_ID])
        tap(v,tabPoint(v,2))
        tap(v,Pair(panel.x+panel.w*.25f,panel.y+panel.h*.555f))
        tap(v,action)
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
}
