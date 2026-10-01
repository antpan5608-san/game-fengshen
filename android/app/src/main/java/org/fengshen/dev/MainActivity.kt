package org.fengshen.dev

import android.app.Activity
import android.app.AlertDialog
import android.graphics.*
import android.os.Bundle
import android.os.SystemClock
import android.view.*
import android.widget.EditText
import android.widget.TextView
import org.json.JSONObject
import java.security.SecureRandom
import kotlin.math.*

class MainActivity:Activity() {
    private var game:GameView?=null
    private val updater by lazy {ApkUpdater(this)}
    private val cloud by lazy {CloudController(this){game}}
    fun checkForUpdates(){updater.check()}
    fun openCloudSave(){cloud.open()}
    fun onLocalSnapshotSaved(snapshot:SaveSnapshot){cloud.onLocalSaved(snapshot)}
    private var resumed=false
    private var destroyed=false
    private var loadStarted=0L
    internal var firstFrameReported=false
    fun firstInteractiveFrame(){if(!firstFrameReported){firstFrameReported=true;Diagnostics.record("content_first_interactive_frame",details=JSONObject().put("firstFrameMs",SystemClock.elapsedRealtime()-loadStarted));Diagnostics.schedule()}}
    override fun onCreate(state:Bundle?) {
        super.onCreate(state)
        loadStarted=SystemClock.elapsedRealtime()
        window.setDecorFitsSystemWindows(false)
        window.attributes=window.attributes.apply {layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES}
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContentView(TextView(this).apply{text="正在校验本地开发内容…";gravity=Gravity.CENTER})
        window.insetsController?.apply {hide(WindowInsets.Type.systemBars());systemBarsBehavior=WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE}
        Thread({
            try {
                val content=ContentLoader.load(AssetSource(assets),{timings->Diagnostics.record("content_load",details=timings.put("success",true))},java.io.File(filesDir,"content-audio"))
                runOnUiThread {
                    if(destroyed)return@runOnUiThread
                    val v=GameView(this,content);game=v
                    val restored=if(state?.getString("version")==content.scene.version && state.containsKey("snapshot"))
                        runCatching{v.restoreSnapshot(SaveSnapshot.parse(state.getString("snapshot")!!))}.getOrDefault(false)
                        else false
                    if(!restored)v.restorePersisted()
                    v.startOpeningIfNeeded()
                    setContentView(v)
                    v.setOnApplyWindowInsetsListener { _,i ->
                        val s=i.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout() or WindowInsets.Type.systemGestures())
                        v.safe=SafeInsets(s.left,s.top,s.right,s.bottom)
                        v.insetsDescription="bars=${i.getInsets(WindowInsets.Type.systemBars())} cutout=${i.displayCutout?.safeInsetLeft?:0}/${i.displayCutout?.safeInsetTop?:0} gestures=${i.getInsets(WindowInsets.Type.systemGestures())}"
                        v.relayout();i
                    }
                    v.requestApplyInsets();v.active=resumed;v.audio.foreground(resumed);cloud.resume()
                }
            } catch(e:Exception) {Diagnostics.record("content_load","ERROR",JSONObject().put("success",false),e.javaClass.simpleName,e.stackTrace.take(12).joinToString("\n"));runOnUiThread {if(!destroyed)setContentView(TextView(this).apply{text="开发内容加载失败，未进入场景。\n${e.message}";gravity=Gravity.CENTER})}}
        },"development-content-loader").start()
    }
    override fun onResume(){super.onResume();resumed=true;game?.active=true;game?.audio?.foreground(true);updater.resumeAfterPermission();Diagnostics.record("app_foreground");Diagnostics.schedule()}
    override fun onPause(){resumed=false;game?.active=false;game?.audio?.foreground(false);game?.persistState();Diagnostics.record("app_background");super.onPause()}
    override fun onDestroy(){destroyed=true;updater.close();game?.active=false;game?.audio?.close();cloud.close();super.onDestroy()}
    override fun onSaveInstanceState(out:Bundle){game?.let{it.finishForLifecycle();out.putString("snapshot",it.currentSnapshot().json().toString());out.putString("version",it.content.scene.version)};super.onSaveInstanceState(out)}
    override fun onWindowFocusChanged(focus:Boolean){super.onWindowFocusChanged(focus);game?.focused=focus}
    @Suppress("DEPRECATION")
    override fun onBackPressed(){if(game?.handleBack()==true)return;super.onBackPressed()}
}

class GameView(private val activity:MainActivity,val content:Content):SurfaceView(activity),SurfaceHolder.Callback,Choreographer.FrameCallback {
    enum class Layer { MAP, MENU, SETTINGS, DIALOGUE, CHARACTER, INVENTORY, BATTLE, SHOP }
    enum class CharacterTab { ATTRIBUTES, EQUIPMENT, ITEMS, MAGIC }
    val world=World(content.scenes,content.exits,114)
    val input=InputState()
    val audio=GameAudio(activity,content.audio).also{it.scene(114)}
    private val clock=FixedClock()
    private val prefs=activity.getSharedPreferences("operation-a-ui",0)
    private val savePrefs=activity.getSharedPreferences("opening-local-save",0)
    private val hadPersistedAtStart=savePrefs.contains("saveJson")||savePrefs.contains("mapId")
    private var savedSnapshot=""
    private var characters=listOf(content.initialPlayer)
    private var inventory:Map<String,Int> = emptyMap()
    private var flags:Map<String,Boolean> = emptyMap()
    private var money=content.initialMoney
    private val encounter=content.battle?.let{OpeningEncounter(it)}
    private val battleRandom=SecureRandom()
    private var processedStepSeq=0L
    private var battle:OpeningBattle?=null
    private var battleCommitted=false
    private var battleID=""
    private var battleMessage=""
    private var battlePresentation=BattlePresentation()
    private var battleTouchRevision=0
    private val battleKeyRevisions=mutableMapOf<Int,Int>()
    private var selectedBattleSlot=0
    private val battleTouch=mutableMapOf<Int,Int>()
    private var dialogueNpc:StoryNpc?=null
    private var dialogueText:StoryText?=null
    private var dialoguePage=0
    private val dialogueTouch=mutableSetOf<Int>()
    private var layoutMapId=114
    var safe=SafeInsets();var insetsDescription="pending"
    private var config=readConfig(prefs.getString("controls-v2","{}")?:"{}")
    private var mode=runCatching{DisplayMode.valueOf(prefs.getString("display-v2","FULL")?:"FULL")}.getOrDefault(DisplayMode.FULL)
    private var debug=prefs.getBoolean("debug",false)
    private var haptic=prefs.getBoolean("haptic",false)
    var active=false;set(v){field=v;if(!v)finishPendingStep();input.clear();panelTouch.clear();clearUxGesture();hudTouch.clear();battleTouch.clear();shopTouch.clear();clearUxGesture();clock.reset()}
    var focused=true;set(v){field=v;if(!v){finishPendingStep();input.clear();panelTouch.clear();clearUxGesture();hudTouch.clear();battleTouch.clear();shopTouch.clear();clearUxGesture();clock.reset()}}
    var layer=Layer.MAP;private set
    val menuOpen get()=layer==Layer.MENU
    var menuSelection=0;private set
    private var surface=false
    private var posted=false
    private var ui=layout(1,1,1f,safe,mode,config,world.scene.width*16,world.scene.height*16)
    private val paint=Paint().apply{isFilterBitmap=false;isAntiAlias=false}
    private val overlayPaint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;typeface=Typeface.create("sans-serif",Typeface.NORMAL)}
    private val menuTouch=mutableMapOf<Int,Int>()
    private val panelTouch=mutableMapOf<Int,Int>()
    private val hudTouch=mutableSetOf<Int>()
    private val npcTouch=mutableMapOf<Int,Triple<String,Float,Float>>()
    private var modalListScroll=0f
    private var modalDetailScroll=0f
    private var modalDetailsOpen=false
    private var candidateSlot:String?=null
    private var uxGesture:ModalGesture?=null
    private var uxBlocked=false
    private var uxRevision=0
    private var uxFeedback=""
    private var uxFeedbackUntil=0L
    private var characterPage=0
    private var equipmentSlot="rightHand"
    private var shop:ShopDefinition?=null
    private var shopMode="ROOT"
    private var selectedShopItemId:String?=null
    private var shopRevision=0
    private var shopMessage=""
    private val shopTouch=mutableMapOf<Int,Pair<Int,Int>>()
    private var panelTab=CharacterTab.ATTRIBUTES
    val activeCharacterTab get()=panelTab
    val selectedCharacterId get()=characters.getOrNull(characterPage)?.id
    private var panelReturnLayer=Layer.MENU
    private var selectedItemId:String?=null
    private var modalDialog:AlertDialog?=null
    private var previousMessage=""
    private var noticeUntil=0L
    init {holder.addCallback(this);isFocusable=true;isFocusableInTouchMode=true;contentDescription="封神全屏地图"
        world.transitionObserver={from,to,success->Diagnostics.record("map_transition","ERROR",JSONObject().put("success",success).put("fromMapId",from).put("mapId",to),"target_map_or_spawn_unavailable")}}
    fun relayout(){ui=layout(width,height,resources.displayMetrics.density,safe,mode,config,world.scene.width*16,world.scene.height*16);layoutMapId=world.mapId;input.clear();menuTouch.clear();panelTouch.clear();clearUxGesture();hudTouch.clear();npcTouch.clear();shopTouch.clear();clearUxGesture();clock.reset()}
    fun currentSnapshot()=SaveSnapshot(content.scene.version,world.mapId,world.x,world.y,world.direction,characters,inventory,flags,money,encounter?.steps?:0)
    fun hasMeaningfulLocalSave():Boolean = hadPersistedAtStart || world.mapId!=114 ||
        world.x!=content.scene.spawnX*16+8 || world.y!=content.scene.spawnY*16+8 || characters!=listOf(content.initialPlayer) ||
        inventory.isNotEmpty() || flags.isNotEmpty() || money!=content.initialMoney
    fun backupBeforeCloudRestore(){savePrefs.getString("saveJson",null)?.let{savePrefs.edit().putString("preCloudRecovery",it).commit()}}
    fun restoreSnapshot(snapshot:SaveSnapshot):Boolean {
        if(!snapshot.validate(content))return false
        clearUxGesture();uxRevision++;world.finishStep();input.clear();clock.reset()
        world.restore(snapshot.mapId,snapshot.x,snapshot.y,0,snapshot.direction)
        audio.scene(world.mapId)
        encounter?.restore(snapshot.encounterSteps);processedStepSeq=world.completedStepSeq
        characters=snapshot.characters.map{hero->
            if(hero.id==content.initialPlayer.id && hero.equipment==null)
                hero.copy(equipment=content.initialPlayer.equipment,maxMp=hero.maxMp?:content.initialPlayer.maxMp)
            else hero
        }
        val migrated=snapshot.inventory.toMutableMap()
        migrated.remove("reference.item.1")?.let{legacy->
            migrated[OpeningEquipment.KNIFE_ID]=(migrated[OpeningEquipment.KNIFE_ID]?:0)+legacy
        }
        inventory=migrated.filterValues{it>0};flags=snapshot.flags;money=snapshot.money
        characterPage=0;selectedItemId=null;candidateSlot=null;resetModalSelection()
        savedSnapshot="";persistState();return true
    }
    fun restorePersisted(){
        val encoded=savePrefs.getString("saveJson",null)
        if(encoded!=null){val restored=runCatching{restoreSnapshot(SaveSnapshot.parse(encoded))}.getOrDefault(false)
            Diagnostics.record("save_load",if(restored)"INFO" else "ERROR",JSONObject().put("success",restored),if(restored)"" else "invalid_local_snapshot")
            if(restored)return
        }else Diagnostics.record("save_load",details=JSONObject().put("success",true),code="new_game_no_save")
        if(savePrefs.getString("contentVersion",null)!=content.scene.version)return
        world.restore(savePrefs.getInt("mapId",114),savePrefs.getInt("x",world.x),savePrefs.getInt("y",world.y),0,
            Key.entries.getOrElse(savePrefs.getInt("direction",Key.DOWN.ordinal)){Key.DOWN})
    }
    fun persistState(){persistStateResult()}
    private fun persistStateResult():Boolean {
        if(world.remaining!=0 || (layer==Layer.BATTLE && !battleCommitted))return false
        val snapshot=currentSnapshot();val encoded=snapshot.json().toString()
        if(encoded==savedSnapshot)return true
        val committed=savePrefs.edit().putString("contentVersion",content.scene.version).putInt("mapId",world.mapId)
            .putInt("x",world.x).putInt("y",world.y).putInt("direction",world.direction.ordinal)
            .putString("saveJson",encoded).commit()
        Diagnostics.record("save_write",if(committed)"INFO" else "ERROR",JSONObject().put("success",committed).put("mapId",world.mapId),if(committed)"" else "local_commit_failed")
        if(committed){savedSnapshot=encoded;activity.onLocalSnapshotSaved(snapshot)}
        return committed
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){relayout()}
    override fun surfaceCreated(h:SurfaceHolder){surface=true;schedule()}
    override fun surfaceChanged(h:SurfaceHolder,format:Int,w:Int,height:Int){relayout()}
    override fun surfaceDestroyed(h:SurfaceHolder){surface=false;input.clear();menuTouch.clear();panelTouch.clear();clearUxGesture();hudTouch.clear();npcTouch.clear();clock.reset();Choreographer.getInstance().removeFrameCallback(this);posted=false}
    private fun schedule(){if(surface&&!posted){posted=true;Choreographer.getInstance().postFrameCallback(this)}}
    override fun doFrame(time:Long){
        posted=false
        if(!surface)return
        if(active&&focused&&layer==Layer.MAP)clock.advance(time){
            if(layer==Layer.MAP){world.tickIntent(input.movementIntent());processCompletedStep()}
        } else if(active&&focused&&layer==Layer.BATTLE)clock.advance(time){
            if(battlePresentation.tick(16))finishBattlePresentation()
        } else clock.reset()
        if(layoutMapId!=world.mapId){ui=layout(width,height,resources.displayMetrics.density,safe,mode,config,world.scene.width*16,world.scene.height*16);layoutMapId=world.mapId}
        if(world.remaining==0)persistState()
        var canvas:Canvas?=null
        try {canvas=holder.lockCanvas();if(canvas!=null)render(canvas)} finally {if(canvas!=null){holder.unlockCanvasAndPost(canvas);if(active&&focused)activity.firstInteractiveFrame()}}
        schedule()
    }
    private fun processCompletedStep(){
        if(processedStepSeq==world.completedStepSeq)return
        processedStepSeq=world.completedStepSeq
        val step=world.lastCompletedStep?:return
        if(step.transitioned){Diagnostics.record("map_transition",details=JSONObject().put("success",true).put("mapId",step.mapId).put("x",step.x).put("y",step.y));audio.scene(world.mapId)}
        val group=encounter?.onCompletedStep(if(step.transitioned)-1 else step.mapId,step.x,step.y){battleRandom.nextInt(256)}?:return
        val rules=content.battle?:return
        persistState() // Stable pre-battle checkpoint; no mid-turn snapshot is written.
        battle=OpeningBattle(group,rules,characters.first(),
            equipmentBonus(characters.first(),"rightHand"),equipmentBonus(characters.first(),"body"))
        selectedBattleSlot=group.members.first().slot
        battleMessage="遭遇敌群 ${group.id} · 选择目标后攻击"
        battleCommitted=false;battlePresentation=BattlePresentation();layer=Layer.BATTLE
        battleID=java.util.UUID.randomUUID().toString()
        Diagnostics.record("battle_start",details=JSONObject().put("battleID",battleID).put("groupId",group.id).put("mapId",world.mapId))
        audio.scene(world.mapId,"battle")
        input.clear();battleTouch.clear();menuTouch.clear();panelTouch.clear();clearUxGesture();hudTouch.clear();npcTouch.clear();clock.reset()
    }
    private fun finishPendingStep():Boolean {
        world.finishStep();processCompletedStep()
        return layer==Layer.BATTLE
    }
    fun finishForLifecycle(){finishPendingStep()}
    private fun attackBattle(slot:Int){
        if(battlePresentation.screen!=BattlePresentation.Screen.TARGET)return
        val current=battle?:return
        val turn=current.attack(slot){battleRandom.nextInt(256)}?:return
        input.clear();battleTouch.clear();battlePresentation.present(turn)
        audio.effect("attack")
    }
    private fun escapeBattle(){
        if(battlePresentation.screen!=BattlePresentation.Screen.COMMAND)return
        val turn=battle?.escape{battleRandom.nextInt(256)}?:return
        input.clear();battleTouch.clear();battlePresentation.present(turn)
    }
    private fun finishBattlePresentation(){
        val current=battle?:return
        battleTouch.clear()
        selectedBattleSlot=current.enemies.firstOrNull{it.hp>0}?.slot?:0
        if(current.phase==BattlePhase.TARGET)return
        if(battleCommitted)return
        when(current.phase){
            BattlePhase.VICTORY->{
                val reward=current.settle(money)?:return
                characters=characters.toMutableList().also{it[0]=reward.character};money=reward.money
                battleMessage="胜利！经验 +${reward.experience}  银两 +${current.enemies.sumOf{it.definition.moneyReward}}"+
                    if(reward.levels.isEmpty())"" else "  等级 ${reward.levels.last()}"
                audio.scene(world.mapId,"victory")
                Diagnostics.record("reward_settlement",details=JSONObject().put("battleID",battleID).put("settlementID",battleID+":reward")
                    .put("experience",reward.experience).put("money",current.enemies.sumOf{it.definition.moneyReward}))
            }
            BattlePhase.ESCAPED->{
                characters=characters.toMutableList().also{it[0]=current.hero}
                battleMessage="逃跑成功" // No rewards, no invented grace period.
            }
            BattlePhase.DEFEAT->{
                if(content.battle?.defeatResetEnabled!=true){battleMessage="原版战败处理尚未开放";return}
                // Target ROM: 0:812D clears runtime RAM, 0:B795 initializes map114 and the opening party.
                characters=listOf(content.initialPlayer);inventory=emptyMap();money=content.initialMoney
                flags=mapOf("opening.intro.seen" to true) // Mobile intro was already shown; no invented repeat scene.
                encounter?.restore(0)
                world.restore(114,content.scene.spawnX*16+8,content.scene.spawnY*16+8,0,Key.DOWN)
                processedStepSeq=world.completedStepSeq
                battleMessage="不幸！全員陣亡了！"
            }
            BattlePhase.TARGET->return
        }
        battleCommitted=true
        Diagnostics.record("battle_end",details=JSONObject().put("battleID",battleID).put("groupId",current.group.id)
            .put("reason",current.phase.name.lowercase()))
        persistState()
    }
    private fun closeBattle(){
        if(battlePresentation.screen==BattlePresentation.Screen.TARGET){battlePresentation.back();battleTouch.clear();return}
        if(battlePresentation.screen!=BattlePresentation.Screen.RESULT||!battleCommitted)return
        battle=null;battleTouch.clear();layer=Layer.MAP;input.clear();clock.reset()
        audio.scene(world.mapId)
    }
    private fun confirmBattle(){
        when(battlePresentation.screen){
            BattlePresentation.Screen.COMMAND->when(battlePresentation.command){0->battlePresentation.targets();4->escapeBattle()}
            BattlePresentation.Screen.TARGET->attackBattle(selectedBattleSlot)
            BattlePresentation.Screen.RESULT->closeBattle()
            else->Unit
        }
        battleTouch.clear()
    }
    private fun runBattleAction(action:Int){
        when(action){
            in 100..104->{battlePresentation.selectCommand(action-100);confirmBattle()}
            in 0..6->selectedBattleSlot=action
            200->confirmBattle()
            201->closeBattle()
        }
    }
    private val menuChoices get()=listOf("继续游戏","角色","物品","设置")
    private fun menuBox():Box {
        val dp=resources.displayMetrics.density
        val w=min(ui.safe.w*.50f,320*dp);val h=min(ui.safe.h*.78f,360*dp)
        return Box(ui.safe.x+(ui.safe.w-w)/2,ui.safe.y+(ui.safe.h-h)/2,w,h)
    }
    private fun menuRow(x:Float,y:Float):Int? {
        val b=menuBox();if(!b.contains(x,y))return null
        val row=((y-b.y-b.h*.22f)/(b.h*.18f)).toInt()
        return row.takeIf{it in menuChoices.indices && y>=b.y+b.h*.22f && y<b.y+b.h*.94f}
    }
    private fun menuCloseBox():Box {val b=menuBox();val d=min(42*resources.displayMetrics.density,b.h*.19f);return Box(b.x+b.w-d-8*resources.displayMetrics.density,b.y+6*resources.displayMetrics.density,d,d)}
    private fun battleBox():Box {
        val thumbLimit=2*(ui.buttons.getValue(Key.B).x-8*resources.displayMetrics.density-ui.safe.x-ui.safe.w/2)/256f
        val scale=min(min(ui.safe.w/256f,ui.safe.h/240f),thumbLimit.coerceAtLeast(ui.safe.w*.35f/256f))
        return Box(ui.safe.x+(ui.safe.w-256*scale)/2,ui.safe.y+(ui.safe.h-240*scale)/2,256*scale,240*scale)
    }
    private fun battleRegion(x:Float,y:Float,w:Float,h:Float):Box {
        val b=battleBox();val scale=b.w/256f
        return Box(b.x+x*scale,b.y+y*scale,w*scale,h*scale)
    }
    private fun battleEnemyBox(enemy:BattleEnemy):Box {
        val graphic=content.enemyGraphics[enemy.definition.id]
        return battleRegion(16f+32*enemy.slot,72f,(graphic?.width?:32).toFloat(),(graphic?.height?:40).toFloat())
    }
    private fun battleAction(x:Float,y:Float):Int? {
        val current=battle?:return null;val screen=battlePresentation.screen
        if(screen in listOf(BattlePresentation.Screen.ENTRY,BattlePresentation.Screen.ACTING))return null
        if(ui.buttons.getValue(Key.A).contains(x,y))return 200
        if(screen==BattlePresentation.Screen.TARGET&&ui.buttons.getValue(Key.B).contains(x,y))return 201
        if(screen==BattlePresentation.Screen.RESULT)return if(battleRegion(10f,76f,228f,74f).contains(x,y))200 else null
        if(screen==BattlePresentation.Screen.COMMAND){
            return listOf(0,4).firstOrNull{battleRegion(12f,150f+it*13,64f,13f).contains(x,y)}?.plus(100)
        }
        if(screen==BattlePresentation.Screen.TARGET){
            // Independent slots even when graphics overlap. Pick the nearest live instance center.
            return current.enemies.filter{it.hp>0&&battleEnemyBox(it).contains(x,y)}
                .minByOrNull{abs(x-(battleEnemyBox(it).x+battleEnemyBox(it).w/2))}?.slot
                ?: current.enemies.filter{it.hp>0}.firstOrNull{enemy->
                    val i=current.enemies.filter{it.hp>0}.indexOf(enemy)
                    battleRegion(14f,151f+i*9,218f,9f).contains(x,y)}?.slot
        }
        return null
    }
    fun visibleMapControls()=layer==Layer.MAP
    private fun nearbyNpcs():List<StoryNpc> {
        val (x,y)=world.destinationCell()
        return content.npcs.filter{it.mapId==world.mapId &&
            (it.interactionCell?.let{p->p==(x to y)} ?: (abs(it.x-x)+abs(it.y-y)==1))}
    }
    private fun interactionTarget():StoryNpc? {
        val merchant=nearbyNpcs().firstOrNull{it.shopId!=null}
        if(merchant!=null)return merchant
        val (x,y)=world.destinationCell()
        val id=interactionTarget(x,y,world.direction,content.npcs.filter{it.mapId==world.mapId}.map{NpcCell(it.id,it.x,it.y)})?.id
        return content.npcs.firstOrNull{it.id==id}
    }
    private fun hitNpc(x:Float,y:Float):StoryNpc? {
        if(!ui.game.contains(x,y))return null
        val camera=world.camera(ui.viewWidth,ui.viewHeight)
        val candidates=nearbyNpcs().map{npc->
            val (sx,sy)=ui.worldToScreen(npc.x*16f,npc.y*16f,camera)
            val size=16*ui.scale;val pad=min(12*resources.displayMetrics.density,size*.24f)
            npc to Box(sx-pad,sy-pad,size+pad*2,size+pad*2)
        }
        return candidates.filter{it.second.contains(x,y)}.minByOrNull{
            val b=it.second;hypot(x-(b.x+b.w/2),y-(b.y+b.h/2))
        }?.first
    }
    private fun openNpc(npc:StoryNpc){
        if(finishPendingStep())return
        val (x,y)=world.destinationCell()
        if(npc.shopId!=null){
            if(npc !in nearbyNpcs())return
            world.face(Key.UP);openShop(content.shops.getValue(npc.shopId));return
        }
        val direction=facingToward(x,y,NpcCell(npc.id,npc.x,npc.y))?:return
        world.face(direction)
        val id=if(flags[npc.id]==true)npc.repeatDialogue?:npc.firstDialogue else npc.firstDialogue
        content.dialogues[id]?.let{openDialogue(it,npc)}
    }
    fun mapControlEnabled(key:Key)=layer==Layer.MAP&&(key==Key.MENU || (key==Key.A && interactionTarget()!=null))
    fun startOpeningIfNeeded(){
        if(world.mapId==114 && world.x==content.scene.spawnX*16+8 && world.y==content.scene.spawnY*16+8 &&
            flags["opening.intro.seen"]!=true)content.intro?.let{openDialogue(it,null)}
    }
    private fun openDialogue(text:StoryText,npc:StoryNpc?){
        if(finishPendingStep())return
        input.clear();menuTouch.clear();panelTouch.clear();clearUxGesture();npcTouch.clear();dialogueTouch.clear();clock.reset()
        dialogueText=text;dialogueNpc=npc;dialoguePage=0;layer=Layer.DIALOGUE
    }
    private fun dialogueLines()=dialogueText?.text?.chunked(24)?.chunked(2)?:emptyList()
    private fun advanceDialogue(){
        if(layer!=Layer.DIALOGUE)return
        val pages=dialogueLines()
        if(dialoguePage+1<pages.size){dialoguePage++;return}
        val npc=dialogueNpc
        if(npc==null)flags=flags+("opening.intro.seen" to true)
        else if(flags[npc.id]!=true){
            for(effect in npc.firstEffects)when(effect.type){
                "money"->money+=effect.amount
                "item"->inventory=inventory+(effect.id!! to ((inventory[effect.id]?:0)+effect.amount))
                else->error("Unsupported story effect ${effect.type}")
            }
            flags=flags+(npc.id to true)
        }
        dismissDialogue();persistState()
    }
    private fun dismissDialogue(){layer=Layer.MAP;dialogueNpc=null;dialogueText=null;dialogueTouch.clear();input.clear();clock.reset()}
    private fun openMenu(){if(layer!=Layer.MAP || finishPendingStep())return;input.clear();menuTouch.clear();hudTouch.clear();npcTouch.clear();clock.reset();menuSelection=0;layer=Layer.MENU}
    private fun closeMenu(){if(layer!=Layer.MENU)return;layer=Layer.MAP;input.clear();menuTouch.clear();panelTouch.clear();clearUxGesture();clock.reset()}
    private fun returnToMenu(){layer=Layer.MENU;input.clear();menuTouch.clear();panelTouch.clear();clearUxGesture();clock.reset()}
    fun handleBack():Boolean {when(layer){Layer.MAP->openMenu();Layer.MENU->closeMenu();Layer.SETTINGS->modalDialog?.dismiss();Layer.DIALOGUE->dismissDialogue();Layer.CHARACTER,Layer.INVENTORY->closePanel();Layer.BATTLE->closeBattle();Layer.SHOP->shopBack()};return true}
    private fun confirmMenu(){
        when(menuSelection){0->closeMenu();1->openPanel(Layer.CHARACTER);2->openPanel(Layer.INVENTORY);3->settings()}
    }
    private fun openPanel(which:Layer){
        if(layer !in listOf(Layer.MAP,Layer.MENU) || which !in listOf(Layer.CHARACTER,Layer.INVENTORY))return
        panelReturnLayer=layer;if(finishPendingStep())return
        input.clear();hudTouch.clear();npcTouch.clear();menuTouch.clear();panelTouch.clear();clearUxGesture();clock.reset()
        characterPage=0;selectedItemId=null;candidateSlot=null;modalListScroll=0f;modalDetailScroll=0f;modalDetailsOpen=false;uxRevision++
        panelTab=if(which==Layer.INVENTORY)CharacterTab.ITEMS else CharacterTab.ATTRIBUTES
        layer=which
    }
    private fun closePanel(){
        if(layer !in listOf(Layer.CHARACTER,Layer.INVENTORY))return
        layer=panelReturnLayer;input.clear();panelTouch.clear();clearUxGesture();hudTouch.clear();clock.reset()
    }
    private fun hudBox():Box {
        val dp=resources.displayMetrics.density
        return Box(ui.safe.x+8*dp,ui.safe.y+8*dp,min(ui.safe.w*.25f,230*dp),min(ui.safe.h*.22f,90*dp))
    }
    fun hudBounds()=hudBox()
    private fun hudPortraitBox():Box {
        val b=hudBox();val dp=resources.displayMetrics.density;val size=min(49*dp,b.h*.68f)
        return Box(b.x+8*dp,b.y+(b.h-size)/2,size,size)
    }
    private fun panelBox():Box {
        val dp=resources.displayMetrics.density
        if(directPanel())return modalLayout().frame
        val inset=8*dp;val w=ui.safe.w*.40f
        return Box(ui.safe.x+inset,ui.safe.y+inset,w,ui.safe.h-inset*2)
    }
    fun characterPanelBounds()=panelBox()
    private fun panelBackBox():Box {
        if(directPanel())return modalLayout().close
        val b=panelBox();val dp=resources.displayMetrics.density;val size=min(46*dp,b.h*.16f)
        return Box(b.x+b.w-size-8*dp,b.y+5*dp,size,size)
    }
    private fun inventoryEntries()=inventory.filterValues{it>0}.toSortedMap().entries.toList()
    private fun partyBox(index:Int):Box {
        if(directPanel())return modalLayout().party.getOrElse(index){Box(0f,0f,0f,0f)}
        val b=panelBox();val dp=resources.displayMetrics.density;val size=min(37*dp,b.h*.095f)
        return Box(b.x+12*dp+index*(size+9*dp),b.y+b.h*.265f,size,size)
    }
    private fun tabBox(index:Int):Box {
        if(directPanel())return modalLayout().tabs[index]
        val b=panelBox();val dp=resources.displayMetrics.density;val w=(b.w-16*dp)/4
        return Box(b.x+8*dp+index*w,b.y+b.h*.405f,w,b.h*.095f)
    }
    private fun panelEquipmentActionBox():Box {
        if(directPanel())return modalLayout().primary
        val b=panelBox();val dp=resources.displayMetrics.density
        return Box(b.x+17*dp,b.y+b.h*.865f,b.w-34*dp,b.h*.065f)
    }
    private fun panelAction(x:Float,y:Float):Int? = when {
        panelBackBox().contains(x,y)->0
        CharacterTab.entries.indices.firstOrNull{tabBox(it).contains(x,y)}!=null->10+CharacterTab.entries.indices.first{tabBox(it).contains(x,y)}
        characters.indices.firstOrNull{partyBox(it).contains(x,y)}!=null->20+characters.indices.first{partyBox(it).contains(x,y)}
        else->null
    }
    private fun equippedDefinition():EquipmentDefinition? {
        val e=characters[characterPage].equipment?:return null
        val id=when(equipmentSlot){"rightHand"->e.rightHand;"leftHand"->e.leftHand;"body"->e.body;else->e.feet}
        return content.equipmentDefinitions.values.firstOrNull{it.slot==equipmentSlot&&it.originalId==id&&it.operationEnabled}
    }
    private fun runPanelAction(action:Int){
        when(action){0->closePanel();in 10..13->runPanelCommand(ModalCommand("tab:${action-10}"))
            in 20..23->characters.getOrNull(action-20)?.let{runPanelCommand(ModalCommand("hero",targetId=it.id))}}
    }
    private fun directPanel()=panelTab in listOf(CharacterTab.EQUIPMENT,CharacterTab.ITEMS)
    private fun modalLayout()=touchModalLayout(ui.safe,resources.displayMetrics.density,
        resources.configuration.fontScale,if(layer==Layer.SHOP)2 else 4,
        if(layer==Layer.SHOP)0 else characters.size,layer!=Layer.SHOP&&panelTab==CharacterTab.EQUIPMENT)
    private fun clearUxGesture(){uxGesture=null;uxBlocked=false}
    private fun resetModalSelection(){clearUxGesture();uxRevision++;modalListScroll=0f;modalDetailScroll=0f;modalDetailsOpen=false}
    private fun panelItems()=inventoryEntries().filter{entry->candidateSlot==null ||
        content.equipmentDefinitions[entry.key]?.let{it.slot==candidateSlot&&OpeningEquipment.replace(characters[characterPage],inventory,it,content.equipmentDefinitions.values)!=null}==true}
    fun panelTabBounds(index:Int)=tabBox(index)
    fun panelPrimaryBounds()=panelEquipmentActionBox()
    fun panelSecondaryBounds()=modalLayout().secondary
    fun panelListBounds()=modalLayout().list
    fun panelItemBounds(id:String)=modalLayout().visibleRow(panelItems().indexOfFirst{it.key==id},modalListScroll)
    fun panelSlotBounds(slot:String)=modalLayout().visibleRow(listOf("rightHand","leftHand","body","feet").indexOf(slot),modalListScroll)
    private fun heroName(id:String)=content.characterDefinitions[id]?.name?:id
    private fun slotName(slot:String)=mapOf("rightHand" to "右手","leftHand" to "左手","body" to "身体","feet" to "脚")[slot]?:slot
    private data class ItemAction(val kind:String?,val text:String,val enabled:Boolean,val reason:String,val target:String?)
    private fun itemAction():ItemAction {
        val id=selectedItemId?:return ItemAction(null,"",false,"请选择物品",null)
        val item=content.itemDefinitions[id]?:return ItemAction(null,"",false,"物品定义尚未接入",null)
        val hero=characters[characterPage];val d=content.equipmentDefinitions[id]
        if(d!=null){
            val result=OpeningEquipment.replace(hero,inventory,d,content.equipmentDefinitions.values)
            val e=hero.equipment
            val oldId=when(d.slot){"rightHand"->e?.rightHand;"body"->e?.body;"feet"->e?.feet;else->null}
            val old=content.equipmentDefinitions.values.firstOrNull{it.slot==d.slot&&it.originalId==oldId}
            val reason=when{!d.operationEnabled->"装备规则尚未实现";hero.id !in d.allowedCharacters->"当前角色不适用，可查看其他已入队队员"
                (inventory[id]?:0)<=0->"已无该物品";e==null->"角色装备状态尚未接入";oldId==null->"槽位操作尚未实现"
                oldId==d.originalId->"已装备同件";oldId!=-1&&old==null->"原装备卸下规则待核"
                old!=null&&(inventory[old.itemId]?:0)>=10->"原装备回包数量已满"
                result==null->"当前装备条件不满足";else->""}
            return ItemAction("equip","装备给${heroName(hero.id)}",result!=null,reason,hero.id)
        }
        if(item.herbUse!=null){
            val living=characters.filter{it.hp>0};val target=if(living.size==1)living.single() else hero
            val mapMenu=panelReturnLayer in listOf(Layer.MAP,Layer.MENU)
            val enabled=HerbUse.available(characters,inventory,target.id,item,mapMenu)
            val reason=when{!mapMenu->"仅支持地图/菜单使用";living.isEmpty()->"当前没有合法的存活目标";(inventory[id]?:0)<=0->"已无该物品";!enabled->"当前目标条件不满足，请选择存活队员";else->""}
            return ItemAction("use","使用于${heroName(target.id)}",enabled,reason,target.id)
        }
        return if(item.category=="medicine")ItemAction("use","使用（待接入）",false,"使用效果和逻辑尚未实现",hero.id)
            else ItemAction(null,"",false,"当前没有已实现的合法操作",null)
    }
    private fun hasEquipmentCandidate()=inventoryEntries().any{entry->content.equipmentDefinitions[entry.key]?.let{
        it.slot==equipmentSlot&&OpeningEquipment.replace(characters[characterPage],inventory,it,content.equipmentDefinitions.values)!=null}==true}
    private fun modalState()=currentSnapshot().json().toString()
    private fun feedback(message:String){modalDetailScroll=0f;uxFeedback=message;uxFeedbackUntil=SystemClock.elapsedRealtime()+3000}
    private fun commitModal(before:SaveSnapshot,message:String){
        if(persistStateResult())feedback(message)
        else {characters=before.characters;inventory=before.inventory;money=before.money;feedback("保存失败，操作未完成")}
        uxRevision++;clearUxGesture()
    }
    private fun panelHit(x:Float,y:Float):ModalCommand? {
        val l=modalLayout();val hero=characters[characterPage]
        if(l.close.contains(x,y))return ModalCommand("close")
        if(!l.wide&&modalDetailsOpen&&l.back.contains(x,y))return ModalCommand("back-list")
        l.tabs.indexOfFirst{it.contains(x,y)}.takeIf{it>=0}?.let{return ModalCommand("tab:$it")}
        if(characters.size>1)l.party.indexOfFirst{it.contains(x,y)}.takeIf{it in characters.indices}?.let{return ModalCommand("hero",targetId=characters[it].id)}
        if(l.wide||!modalDetailsOpen){
            val index=((y-l.list.y+modalListScroll)/l.rowHeight).toInt()
            val visible=l.visibleRow(index,modalListScroll)
            if(l.list.contains(x,y)&&visible.h>=48*resources.displayMetrics.density){
                if(panelTab==CharacterTab.ITEMS)panelItems().getOrNull(index)?.let{return ModalCommand("item",it.key,hero.id,mode=candidateSlot)}
                else listOf("rightHand","leftHand","body","feet").getOrNull(index)?.let{return ModalCommand("slot",targetId=hero.id,slot=it)}
            }
        }
        if(l.wide||modalDetailsOpen){
            if(panelTab==CharacterTab.ITEMS&&l.primary.contains(x,y)){
                val a=itemAction();if(a.enabled&&a.kind!=null)return ModalCommand(a.kind,selectedItemId,a.target,mode=candidateSlot)
            }
            if(panelTab==CharacterTab.EQUIPMENT){
                val d=equippedDefinition()
                if(l.primary.contains(x,y)&&d!=null&&OpeningEquipment.unequip(hero,inventory,d)!=null)return ModalCommand("unequip",d.itemId,hero.id,equipmentSlot)
                if(l.secondary.contains(x,y)&&hasEquipmentCandidate())return ModalCommand("candidates",targetId=hero.id,slot=equipmentSlot)
            }
        }
        return null
    }
    private fun runPanelCommand(cmd:ModalCommand){
        uxRevision++;clearUxGesture()
        when(cmd.kind){
            "close"->closePanel()
            "back-list"->{modalDetailsOpen=false;modalDetailScroll=0f}
            "hero"->{characters.indexOfFirst{it.id==cmd.targetId}.takeIf{it>=0}?.let{characterPage=it};selectedItemId=null;candidateSlot=null;resetModalSelection()}
            "item"->{if(panelItems().none{it.key==cmd.itemId})return;selectedItemId=cmd.itemId;modalDetailsOpen=true;modalDetailScroll=0f}
            "slot"->{equipmentSlot=cmd.slot?:return;modalDetailsOpen=true;modalDetailScroll=0f}
            "candidates"->{candidateSlot=cmd.slot;panelTab=CharacterTab.ITEMS;selectedItemId=null;resetModalSelection()}
            "equip","unequip","use"->{
                val before=currentSnapshot();val id=cmd.itemId?:return;val target=cmd.targetId?:return
                val index=characters.indexOfFirst{it.id==target};if(index<0)return
                val hero=characters[index]
                when(cmd.kind){
                    "use"->{val a=itemAction();if(a.kind!="use"||!a.enabled||a.target!=target||selectedItemId!=id)return
                        val item=content.itemDefinitions[id]?:return
                        val result=HerbUse.apply(characters,inventory,target,item,panelReturnLayer in listOf(Layer.MAP,Layer.MENU))
                        if(!result.applied)return;characters=result.characters;inventory=result.inventory}
                    else->{val d=content.equipmentDefinitions[id]?:return
                        if(cmd.kind=="equip"&&(selectedItemId!=id||hero.id!=selectedCharacterId))return
                        if(cmd.kind=="unequip"&&(cmd.slot!=equipmentSlot||equippedDefinition()?.itemId!=id))return
                        val result=if(cmd.kind=="equip")OpeningEquipment.replace(hero,inventory,d,content.equipmentDefinitions.values) else OpeningEquipment.unequip(hero,inventory,d)
                        if(result==null){feedback("当前装备条件不满足");return}
                        characters=characters.toMutableList().also{it[index]=result.first};inventory=result.second}
                }
                commitModal(before,when(cmd.kind){"use"->"已使用${content.itemDefinitions[id]?.name}";"equip"->"已装备给${heroName(target)}";else->"已卸下并回包"})
                if((inventory[id]?:0)==0)selectedItemId=null
                modalDetailScroll=0f
            }
            else->if(cmd.kind.startsWith("tab:")){panelTab=CharacterTab.entries[cmd.kind.substringAfter(':').toInt()];selectedItemId=null;candidateSlot=null;resetModalSelection()}
        }
    }
    private fun openShop(definition:ShopDefinition){
        if(layer!=Layer.MAP||finishPendingStep())return
        shop=definition;shopMode="BUY";selectedShopItemId=null;shopMessage="";shopRevision++;resetModalSelection()
        input.clear();shopTouch.clear();npcTouch.clear();clock.reset();layer=Layer.SHOP
    }
    private fun shopEntries():List<String> {
        val s=shop?:return emptyList()
        return if(shopMode=="SELL")s.sellItems.filter{(inventory[it]?:0)>0}.sorted() else s.items
    }
    fun shopBounds()=modalLayout().frame
    fun shopItemBounds(id:String)=modalLayout().visibleRow(shopEntries().indexOf(id),modalListScroll)
    fun shopActionBounds(action:Int):Box=when(action){1->modalLayout().tabs[0];2->modalLayout().tabs[1];3,5->modalLayout().close;4->modalLayout().primary;else->Box(0f,0f,0f,0f)}
    private fun shopHit(x:Float,y:Float):ModalCommand? {
        val l=modalLayout();val s=shop?:return null
        if(l.close.contains(x,y))return ModalCommand("shop-close",shopId=s.id,mode=shopMode)
        if(!l.wide&&modalDetailsOpen&&l.back.contains(x,y))return ModalCommand("back-list",shopId=s.id,mode=shopMode)
        l.tabs.indexOfFirst{it.contains(x,y)}.takeIf{it>=0}?.let{return ModalCommand("shop-mode",shopId=s.id,mode=if(it==0)"BUY" else "SELL")}
        if(l.wide||!modalDetailsOpen){
            val index=((y-l.list.y+modalListScroll)/l.rowHeight).toInt()
            if(l.list.contains(x,y)&&l.visibleRow(index,modalListScroll).h>=48*resources.displayMetrics.density)
                shopEntries().getOrNull(index)?.let{return ModalCommand("shop-item",it,shopId=s.id,mode=shopMode)}
        }
        if((l.wide||modalDetailsOpen)&&l.primary.contains(x,y)&&selectedShopItemId in shopEntries())
            return ModalCommand("trade",selectedShopItemId,shopId=s.id,mode=shopMode)
        return null
    }
    private fun shopBack(){
        if(layer!=Layer.SHOP)return
        if(!modalLayout().wide&&modalDetailsOpen){modalDetailsOpen=false;resetModalSelection();return}
        shop=null;layer=Layer.MAP;input.clear();shopTouch.clear();resetModalSelection();clock.reset();persistState()
    }
    private fun runShopCommand(cmd:ModalCommand){
        if(layer!=Layer.SHOP||cmd.shopId!=shop?.id)return
        uxRevision++;shopRevision++;clearUxGesture();input.clear();shopTouch.clear()
        when(cmd.kind){
            "shop-close"->{shop=null;layer=Layer.MAP;resetModalSelection();clock.reset();persistState()}
            "back-list"->{modalDetailsOpen=false;modalDetailScroll=0f}
            "shop-mode"->{shopMode=cmd.mode?:return;selectedShopItemId=null;resetModalSelection()}
            "shop-item"->{if(cmd.mode!=shopMode||cmd.itemId !in shopEntries())return;selectedShopItemId=cmd.itemId;modalDetailsOpen=true;modalDetailScroll=0f}
            "trade"->{
                val id=cmd.itemId?:return;if(cmd.mode!=shopMode||id!=selectedShopItemId||id !in shopEntries())return
                val item=content.itemDefinitions[id]?:return;val s=shop?:return;val before=currentSnapshot()
                val result=if(shopMode=="BUY")TownTrade.buy(money,inventory,s,item) else TownTrade.sell(money,inventory,s,item)
                if(result.error!=null){feedback(result.error);return}
                money=result.money;inventory=result.inventory
                commitModal(before,"${item.name}已${if(shopMode=="BUY")"买入" else "卖出"}1件")
                if(id !in shopEntries()){selectedShopItemId=null;modalDetailsOpen=false}
                modalListScroll=modalListScroll.coerceAtMost(modalLayout().maxScroll(shopEntries().size))
                Diagnostics.record("town_trade",details=JSONObject().put("shopID",s.id).put("itemID",id).put("operation",shopMode).put("success",currentSnapshot()!=before))
            }
        }
    }
    private fun runShopAction(action:Int){
        val s=shop?:return
        when(action){1,2->runShopCommand(ModalCommand("shop-mode",shopId=s.id,mode=if(action==1)"BUY" else "SELL"))
            3,5->shopBack();4->selectedShopItemId?.let{runShopCommand(ModalCommand("trade",it,shopId=s.id,mode=shopMode))}
            7,8->{val ids=shopEntries();if(ids.isNotEmpty()){val old=ids.indexOf(selectedShopItemId);val next=(old+ids.size+if(action==7)-1 else 1)%ids.size
                runShopCommand(ModalCommand("shop-item",ids[next],shopId=s.id,mode=shopMode));modalListScroll=(next*modalLayout().rowHeight).coerceAtMost(modalLayout().maxScroll(ids.size))}}
        }
    }
    private fun modalTouch(e:MotionEvent):Boolean {
        input.clear()
        if(e.actionMasked==MotionEvent.ACTION_CANCEL||!active||!focused){clearUxGesture();return true}
        val l=modalLayout();fun hit(x:Float,y:Float)=if(layer==Layer.SHOP)shopHit(x,y) else panelHit(x,y)
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{clearUxGesture();val x=e.x;val y=e.y
                val area=when{(l.wide||!modalDetailsOpen)&&l.list.contains(x,y)->1;(l.wide||modalDetailsOpen)&&l.detail.contains(x,y)->2;else->0}
                uxGesture=ModalGesture(e.getPointerId(0),x,y,y,hit(x,y),uxRevision,modalState(),area)}
            MotionEvent.ACTION_POINTER_DOWN->{clearUxGesture();uxBlocked=true}
            MotionEvent.ACTION_MOVE->{val g=uxGesture
                if(!uxBlocked&&g!=null){val i=e.findPointerIndex(g.pointer);if(i>=0){val x=e.getX(i);val y=e.getY(i)
                    if(hypot(x-g.x,y-g.y)>ViewConfiguration.get(context).scaledTouchSlop){g.dragged=true;g.command=null}
                    if(g.dragged){val delta=g.lastY-y
                        if(g.scrollArea==1){val count=if(layer==Layer.SHOP)shopEntries().size else if(panelTab==CharacterTab.ITEMS)panelItems().size else 4
                            modalListScroll=(modalListScroll+delta).coerceIn(0f,l.maxScroll(count))}
                        if(g.scrollArea==2)modalDetailScroll=max(0f,modalDetailScroll+delta)
                    };g.lastY=y}}
            }
            MotionEvent.ACTION_UP->{val g=uxGesture;val command=g?.command
                if(!uxBlocked&&g!=null&&!g.dragged&&g.pointer==e.getPointerId(e.actionIndex)&&g.revision==uxRevision&&g.state==modalState()&&command!=null&&command==hit(e.x,e.y)){
                    if(layer==Layer.SHOP)runShopCommand(command) else runPanelCommand(command)
                };clearUxGesture();performClick()}
            MotionEvent.ACTION_POINTER_UP->Unit
        };return true
    }
    private fun touchText(c:Canvas,value:String,b:Box,sp:Float=14f,color:Int=Color.WHITE):Float {
        textPaint.color=color;textPaint.textSize=sp*resources.displayMetrics.scaledDensity
        val lineH=textPaint.textSize*1.25f;var top=b.y;var pending=value
        while(pending.isNotEmpty()){
            var n=0;while(n<pending.length&&pending[n]!='\n'&&textPaint.measureText(pending.substring(0,n+1))<=b.w)n++
            if(n==0&&pending[0]!='\n')n=1
            c.drawText(pending.substring(0,n),b.x,top-textPaint.fontMetrics.ascent,textPaint);top+=lineH
            pending=pending.substring(n).removePrefix("\n")
        };return top-b.y
    }
    private fun touchButton(c:Canvas,b:Box,text:String,enabled:Boolean=true,selected:Boolean=false){
        overlayPaint.color=if(!enabled)0xff454545.toInt() else if(selected)0xff31776e.toInt() else 0xff344b5a.toInt()
        c.drawRect(b.x,b.y,b.x+b.w,b.y+b.h,overlayPaint);c.save();c.clipRect(b.x,b.y,b.x+b.w,b.y+b.h)
        touchText(c,text,Box(b.x+8*resources.displayMetrics.density,b.y+8*resources.displayMetrics.density,b.w-16*resources.displayMetrics.density,b.h),14f);c.restore()
    }
    private fun touchFrame(c:Canvas,title:String,subtitle:String,titles:List<String>,selected:Int){
        val l=modalLayout();val dp=resources.displayMetrics.density;overlayPaint.color=0x99000000.toInt();c.drawRect(0f,0f,width.toFloat(),height.toFloat(),overlayPaint)
        overlayPaint.color=Color.BLACK;c.drawRect(l.frame.x,l.frame.y,l.frame.x+l.frame.w,l.frame.y+l.frame.h,overlayPaint)
        overlayPaint.color=Color.WHITE;overlayPaint.style=Paint.Style.STROKE;overlayPaint.strokeWidth=2*dp;c.drawRect(l.frame.x,l.frame.y,l.frame.x+l.frame.w,l.frame.y+l.frame.h,overlayPaint);overlayPaint.style=Paint.Style.FILL
        val x=l.frame.x+8*dp+if(!l.wide&&modalDetailsOpen)56*dp else 0f
        touchText(c,title+"\n"+subtitle,Box(x,l.frame.y+8*dp,l.close.x-x-8*dp,1f),15f)
        touchButton(c,l.close,"关闭")
        if(!l.wide&&modalDetailsOpen)touchButton(c,l.back,"列表")
        titles.forEachIndexed{i,t->touchButton(c,l.tabs[i],t,selected=i==selected)}
        if(layer!=Layer.SHOP&&characters.size>1)characters.forEachIndexed{i,h->touchButton(c,l.party[i],heroName(h.id),selected=i==characterPage)}
    }
    private fun touchRows(c:Canvas,rows:List<Triple<String,String,Bitmap?>>,selected:String?){
        val l=modalLayout();val dp=resources.displayMetrics.density;c.save();c.clipRect(l.list.x,l.list.y,l.list.x+l.list.w,l.list.y+l.list.h)
        if(rows.isEmpty())touchText(c,if(candidateSlot!=null)"当前无合法装备候选" else "暂无物品",Box(l.list.x+8*dp,l.list.y+8*dp,l.list.w-16*dp,l.list.h),14f)
        rows.forEachIndexed{i,row->val b=l.row(i,modalListScroll);if(b.y+b.h>=l.list.y&&b.y<=l.list.y+l.list.h){
            overlayPaint.color=if(row.first==selected)0xff244d49.toInt() else 0xff171c1d.toInt();c.drawRect(b.x,b.y,b.x+b.w,b.y+b.h-2*dp,overlayPaint)
            val imageW=if(row.third!=null)48*dp else 0f
            row.third?.let{paint.isFilterBitmap=false;c.drawBitmap(it,null,RectF(b.x+4*dp,b.y+8*dp,b.x+44*dp,b.y+48*dp),paint)}
            touchText(c,row.second,Box(b.x+8*dp+imageW,b.y+8*dp,b.w-16*dp-imageW,b.h-16*dp),14f)
        }};c.restore()
    }
    private fun touchDetail(c:Canvas,lines:List<String>,image:Bitmap?=null){
        val b=modalLayout().detail;val dp=resources.displayMetrics.density;c.save();c.clipRect(b.x,b.y,b.x+b.w,b.y+b.h)
        var y=b.y-modalDetailScroll
        if(SystemClock.elapsedRealtime()<uxFeedbackUntil&&uxFeedback.isNotEmpty())y+=touchText(c,uxFeedback,Box(b.x,y,b.w,1f),14f,0xff72d3c5.toInt())+5*dp
        image?.let{paint.isFilterBitmap=false;c.drawBitmap(it,null,RectF(b.x,y,b.x+56*dp,y+56*dp),paint);y+=64*dp}
        for(line in lines)y+=touchText(c,line,Box(b.x,y,b.w,1f),14f)+5*dp
        modalDetailScroll=modalDetailScroll.coerceAtMost(max(0f,y+modalDetailScroll-b.y-b.h));c.restore()
    }
    private fun drawDirectPanel(c:Canvas){
        val l=modalLayout();val hero=characters[characterPage]
        touchFrame(c,"${heroName(hero.id)}  Lv.${hero.level}","HP ${hero.hp}/${hero.maxHp} · MP ${hero.mp}/${hero.maxMp?:"?"} · 银两 $money",listOf("属性","装备","物品","法术"),panelTab.ordinal)
        if(l.wide||!modalDetailsOpen){
            val rows=if(panelTab==CharacterTab.ITEMS)panelItems().map{e->val item=content.itemDefinitions[e.key]
                val status=when{item?.herbUse!=null->if(characters.none{it.hp>0})"无合法目标" else "地图使用";content.equipmentDefinitions[e.key]?.let{OpeningEquipment.replace(hero,inventory,it,content.equipmentDefinitions.values)!=null}==true->"可装备";content.equipmentDefinitions[e.key]?.operationEnabled==true->"查看装备条件";else->"操作待接入"}
                Triple(e.key,"${item?.name?:"未知物品"}\n×${e.value} · $status",item?.preview)}
            else listOf("rightHand","leftHand","body","feet").map{slot->
                val e=hero.equipment;val id=when(slot){"rightHand"->e?.rightHand;"leftHand"->e?.leftHand;"body"->e?.body;else->e?.feet}
                val d=content.equipmentDefinitions.values.firstOrNull{it.slot==slot&&it.originalId==id};val item=d?.let{content.itemDefinitions[it.itemId]}
                Triple(slot,"${slotName(slot)}\n${item?.name?:if(id==-1)"空" else "状态待核"}",item?.preview)}
            touchRows(c,rows,if(panelTab==CharacterTab.ITEMS)selectedItemId else equipmentSlot)
        }
        if(l.wide||modalDetailsOpen){
            if(panelTab==CharacterTab.ITEMS){val item=selectedItemId?.let{content.itemDefinitions[it]};val a=itemAction();val d=selectedItemId?.let{content.equipmentDefinitions[it]}
                val lines=mutableListOf(item?.name?:"点击左侧物品查看详情")
                if(item!=null){lines+="持有 ${inventory[item.id]?:0}";lines+="查看角色：${heroName(hero.id)}"
                    if(d!=null){lines+=when(d.slot){"rightHand"->"武器加成 +${d.attackBonus}";"body"->"防具加成 +${d.defenseBonus}";else->"迴避力 ${d.evasionValue}"}
                        val next=OpeningEquipment.replace(hero,inventory,d,content.equipmentDefinitions.values)?.first
                        if(next!=null)lines+=when(d.slot){"rightHand"->"总攻击 ${hero.strength+equipmentBonus(hero,d.slot)} → ${next.strength+equipmentBonus(next,d.slot)}";"body"->"总防御 ${hero.stamina+equipmentBonus(hero,d.slot)} → ${next.stamina+equipmentBonus(next,d.slot)}";else->"迴避力 ${equipmentBonus(hero,d.slot)} → ${equipmentBonus(next,d.slot)}"}
                        lines+="原装备按现有规则回包"}
                    else{lines+=item.description?:"暂无已确认说明";a.target?.let{id->characters.firstOrNull{it.id==id}?.let{lines+="目标 ${heroName(id)} · HP ${it.hp}/${it.maxHp}"}}}
                    if(a.reason.isNotEmpty())lines+=a.reason
                };touchDetail(c,lines,item?.preview)
                if(a.kind!=null)touchButton(c,l.primary,a.text,a.enabled)
            }else{val d=equippedDefinition();val item=d?.let{content.itemDefinitions[it.itemId]};val removable=d!=null&&OpeningEquipment.unequip(hero,inventory,d)!=null
                touchDetail(c,listOf("${slotName(equipmentSlot)} · ${item?.name?:"空或尚未核验"}","角色 ${heroName(hero.id)}", "总攻击 ${hero.strength+equipmentBonus(hero,"rightHand")}","总防御 ${hero.stamina+equipmentBonus(hero,"body")}",if(d==null)"当前槽位无可卸下的已实现装备" else if(!removable)"当前背包条件不允许回包" else "卸下后回到真实背包"),item?.preview)
                if(d!=null)touchButton(c,l.primary,"卸下 ${item?.name?:"装备"}",removable)
                touchButton(c,l.secondary,if(hasEquipmentCandidate())"选择${slotName(equipmentSlot)}候选" else "无合法候选",hasEquipmentCandidate())}
        }
    }
    private fun drawShop(c:Canvas){
        val l=modalLayout();val item=selectedShopItemId?.let{content.itemDefinitions[it]}
        touchFrame(c,shop?.name?:"商店","银两 $money",listOf("购买","卖出"),if(shopMode=="BUY")0 else 1)
        if(l.wide||!modalDetailsOpen)touchRows(c,shopEntries().map{id->val it=content.itemDefinitions.getValue(id)
            Triple(id,"${it.name}\n持有 ${inventory[id]?:0} · ${if(shopMode=="BUY")it.buyPrice else it.sellPrice}两",it.preview)},selectedShopItemId)
        if(l.wide||modalDetailsOpen){
            val lines=mutableListOf(item?.name?:if(shopEntries().isEmpty())"没有已核实可交易物品" else "点击商品查看详情")
            if(item!=null){lines+="持有 ${inventory[item.id]?:0} · 上限 ${item.maxCount}";lines+="单价 ${if(shopMode=="BUY")item.buyPrice else item.sellPrice} 两"
                content.equipmentDefinitions[item.id]?.let{d->lines+=when(d.slot){"rightHand"->"武器加成 +${d.attackBonus}";"body"->"防具加成 +${d.defenseBonus}";else->"迴避力 ${d.evasionValue}"};lines+="购入不自动装备"}
                if(shopMode=="BUY")lines+=shop?.buyPrompt?:""}
            touchDetail(c,lines,item?.preview)
            touchButton(c,l.primary,if(item==null)"请选择商品" else "${if(shopMode=="BUY")"购买" else "卖出"}1件 · ${if(shopMode=="BUY")item.buyPrice else item.sellPrice}两",item!=null)
        }
    }
    private fun activate(key:Key){
        if(layer==Layer.SHOP){when(key){Key.A->runShopAction(4)
            Key.B,Key.MENU->shopBack();Key.UP->runShopAction(7);Key.DOWN->runShopAction(8);else->Unit};return}
        when(key){
            Key.MENU->when(layer){Layer.MAP->openMenu();Layer.MENU->closeMenu();Layer.CHARACTER,Layer.INVENTORY->closePanel();else->Unit}
            Key.A->when(layer){Layer.MAP->interactionTarget()?.let{openNpc(it)};Layer.MENU->confirmMenu();Layer.DIALOGUE->advanceDialogue();Layer.BATTLE->confirmBattle();Layer.CHARACTER,Layer.INVENTORY->if(directPanel()){val b=modalLayout().primary;panelHit(b.x+b.w/2,b.y+b.h/2)?.let{runPanelCommand(it)}};else->Unit}
            Key.B->when(layer){Layer.MENU->closeMenu();Layer.DIALOGUE->dismissDialogue();Layer.CHARACTER,Layer.INVENTORY->closePanel();Layer.BATTLE->closeBattle();else->Unit}
            Key.START->when(layer){Layer.MAP->openMenu();Layer.MENU->closeMenu();Layer.CHARACTER,Layer.INVENTORY->closePanel();else->Unit}
            else->Unit
        }
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        if(layer==Layer.SHOP || (layer in listOf(Layer.CHARACTER,Layer.INVENTORY)&&directPanel()))return modalTouch(e)
        if(e.actionMasked==MotionEvent.ACTION_CANCEL){input.clear();menuTouch.clear();panelTouch.clear();clearUxGesture();hudTouch.clear();npcTouch.clear();dialogueTouch.clear();battleTouch.clear();shopTouch.clear();clearUxGesture();return true}
        if(!active||!focused||layer==Layer.SETTINGS){input.clear();menuTouch.clear();panelTouch.clear();clearUxGesture();hudTouch.clear();npcTouch.clear();return true}
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN,MotionEvent.ACTION_POINTER_DOWN->{
                val i=e.actionIndex;val id=e.getPointerId(i);val x=e.getX(i);val y=e.getY(i)
                if(layer==Layer.MAP){
                    val button=ui.hitButton(x,y)
                    if(button!=null && mapControlEnabled(button)){
                        if(button==Key.A){if(finishPendingStep())return true;input.clear();npcTouch.clear();shopTouch.clear();clearUxGesture();clock.reset()}
                        input.set(id,button);if(haptic)performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    }
                    else if(ui.stick.contains(x,y)&&input.startStick(id))input.moveStick(id,x,y,ui.stick,config.deadZone)
                    else if(button==null && hudBox().contains(x,y)){
                        if(finishPendingStep())return true
                        input.clear();npcTouch.clear();clock.reset();hudTouch.add(id)
                    }
                    else if(button==null)hitNpc(x,y)?.let{npc->
                        if(finishPendingStep())return true
                        input.clear();npcTouch.clear();clock.reset()
                        npcTouch[id]=Triple(npc.id,x,y)
                    }
                } else if(layer==Layer.MENU){
                    if(menuCloseBox().contains(x,y))menuTouch[id]=-1
                    else menuRow(x,y)?.let{menuSelection=it;menuTouch[id]=it}
                } else if(layer==Layer.CHARACTER || layer==Layer.INVENTORY){
                    panelAction(x,y)?.let{panelTouch[id]=it}
                } else if(layer==Layer.DIALOGUE)dialogueTouch.add(id)
                else if(layer==Layer.BATTLE)battleAction(x,y)?.let{battleTouch[id]=it;battleTouchRevision=battlePresentation.revision}
                else if(layer==Layer.SHOP)Unit
            }
            MotionEvent.ACTION_MOVE->{
                if(layer==Layer.MAP)for(i in 0 until e.pointerCount){
                    val id=e.getPointerId(i);val x=e.getX(i);val y=e.getY(i)
                    if(input.ownsStick(id)){
                        input.moveStick(id,x,y,ui.stick,config.deadZone)
                    } else {
                        val original=input.keyFor(id)
                        if(original!=null && ui.hitButton(x,y)!=original)input.set(id,null)
                    }
                }
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_POINTER_UP->{
                val i=e.actionIndex;val id=e.getPointerId(i);val x=e.getX(i);val y=e.getY(i)
                val selected=menuTouch.remove(id)
                val shopPressed=shopTouch.remove(id)
                val panelSelected=panelTouch.remove(id)
                val wasHud=hudTouch.remove(id)
                val pressedNpc=npcTouch.remove(id)
                val wasDialogue=dialogueTouch.remove(id)
                val battleSelected=battleTouch.remove(id)
                val key=input.keyFor(id)?.takeIf{layer==Layer.MAP&&ui.hitButton(x,y)==it}
                input.release(id)
                if(shopPressed!=null && layer==Layer.SHOP && shopPressed.second==shopRevision && false)runShopAction(shopPressed.first)
                else if(battleSelected!=null && layer==Layer.BATTLE && battleSelected==battleAction(x,y) && battleTouchRevision==battlePresentation.revision){
                    runBattleAction(battleSelected)
                }
                else if(wasDialogue&&layer==Layer.DIALOGUE){advanceDialogue()}
                else if(wasHud && layer==Layer.MAP && hudBox().contains(x,y))openPanel(Layer.CHARACTER)
                else if(pressedNpc!=null && layer==Layer.MAP &&
                    hypot(x-pressedNpc.second,y-pressedNpc.third)<32*resources.displayMetrics.density){
                    nearbyNpcs().firstOrNull{it.id==pressedNpc.first}?.let{openNpc(it)}
                }
                else if(selected!=null&&layer==Layer.MENU){
                    if(selected==-1&&menuCloseBox().contains(x,y))closeMenu()
                    else if(selected==menuRow(x,y)){menuSelection=selected;confirmMenu()}
                } else if(panelSelected!=null && layer in listOf(Layer.CHARACTER,Layer.INVENTORY) &&
                    panelSelected==panelAction(x,y)){runPanelAction(panelSelected)
                } else if(key!=null)activate(key)
                performClick()
            }
        }
        return true
    }
    override fun performClick():Boolean {super.performClick();return true}
    override fun onKeyDown(code:Int,event:KeyEvent):Boolean {
        val key=hardwareKey(code)?:return super.onKeyDown(code,event)
        if(layer==Layer.SETTINGS)return false
        if(layer==Layer.SHOP&&key in listOf(Key.UP,Key.DOWN)){if(event.repeatCount==0)runShopAction(if(key==Key.UP)7 else 8);return true}
        if(layer==Layer.BATTLE && key in listOf(Key.UP,Key.DOWN)){
            if(event.repeatCount==0&&battlePresentation.screen==BattlePresentation.Screen.COMMAND){
                var next=battlePresentation.command
                do{next=(next+if(key==Key.DOWN)1 else 4)%5}while(next !in listOf(0,4))
                battlePresentation.selectCommand(next)
            }else if(event.repeatCount==0&&battlePresentation.screen==BattlePresentation.Screen.TARGET){val living=battle?.enemies?.filter{it.hp>0}?.map{it.slot}?:emptyList()
                if(living.isNotEmpty())selectedBattleSlot=living[(living.indexOf(selectedBattleSlot).coerceAtLeast(0)+if(key==Key.DOWN)1 else living.size-1)%living.size]}
            return true
        }
        if(event.repeatCount==0){
            if(layer==Layer.BATTLE)battleKeyRevisions[code]=battlePresentation.revision
            if(layer==Layer.MAP && key==Key.A && interactionTarget()!=null){if(finishPendingStep())return true;input.clear();clock.reset()}
            input.set(-code,key)
            if(menuOpen){if(key==Key.UP)menuSelection=(menuSelection+menuChoices.size-1)%menuChoices.size;if(key==Key.DOWN)menuSelection=(menuSelection+1)%menuChoices.size}
            if(layer in listOf(Layer.CHARACTER,Layer.INVENTORY)&&directPanel()&&key in listOf(Key.UP,Key.DOWN)){
                val ids=if(panelTab==CharacterTab.ITEMS)panelItems().map{it.key} else listOf("rightHand","leftHand","body","feet")
                if(ids.isNotEmpty()){val at=ids.indexOf(if(panelTab==CharacterTab.ITEMS)selectedItemId else equipmentSlot)
                    val next=(at+ids.size+if(key==Key.UP)-1 else 1)%ids.size
                    runPanelCommand(if(panelTab==CharacterTab.ITEMS)ModalCommand("item",ids[next],selectedCharacterId,mode=candidateSlot) else ModalCommand("slot",targetId=selectedCharacterId,slot=ids[next]))
                    modalListScroll=(next*modalLayout().rowHeight).coerceAtMost(modalLayout().maxScroll(ids.size))}
            }
        }
        return true
    }
    override fun onKeyUp(code:Int,event:KeyEvent):Boolean {
        val key=hardwareKey(code)?:return super.onKeyUp(code,event)
        if(layer==Layer.SETTINGS)return false
        val was=input.keyFor(-code)==key;input.release(-code)
        val sameBattlePhase=layer!=Layer.BATTLE||battleKeyRevisions.remove(code)==battlePresentation.revision
        if(was&&sameBattlePhase&&key in listOf(Key.A,Key.B,Key.START,Key.MENU))activate(key)
        return true
    }
    private fun hardwareKey(c:Int)=when(c){KeyEvent.KEYCODE_DPAD_UP->Key.UP;KeyEvent.KEYCODE_DPAD_DOWN->Key.DOWN;KeyEvent.KEYCODE_DPAD_LEFT->Key.LEFT;KeyEvent.KEYCODE_DPAD_RIGHT->Key.RIGHT;KeyEvent.KEYCODE_BUTTON_A->Key.A;KeyEvent.KEYCODE_BUTTON_B->Key.B;KeyEvent.KEYCODE_MENU->Key.MENU;KeyEvent.KEYCODE_BUTTON_START->Key.START;else->null}

    private fun render(c:Canvas){
        c.drawColor(Color.BLACK)
        val scene=world.scene;val cam=world.camera(ui.viewWidth,ui.viewHeight)
        c.save();c.clipRect(ui.game.x,ui.game.y,ui.game.x+ui.game.w,ui.game.y+ui.game.h)
        c.translate(ui.game.x,ui.game.y);c.scale(ui.scale,ui.scale);c.translate(-cam.x,-cam.y)
        val firstX=max(0,floor(cam.x/16).toInt());val lastX=min(scene.width-1,ceil((cam.x+cam.viewWidth)/16).toInt())
        val firstY=max(0,floor(cam.y/16).toInt());val lastY=min(scene.height-1,ceil((cam.y+cam.viewHeight)/16).toInt())
        for(ty in firstY..lastY)for(tx in firstX..lastX){
            val i=ty*scene.width+tx;val t=scene.grid[i];val x=tx*16f;val y=ty*16f
            paint.color=Color.WHITE;paint.alpha=255
            c.drawBitmap(content.atlases.getValue(world.mapId),Rect(t%16*16,t/16*16,t%16*16+16,t/16*16+16),RectF(x,y,x+16,y+16),paint)
            if(debug){
                if(i !in scene.enabled){paint.color=0x55000000;c.drawRect(x,y,x+16,y+16,paint)}
                else {paint.color=0xffc56cff.toInt();paint.strokeWidth=.4f
                    if(tx==0||i-1 !in scene.enabled)c.drawLine(x,y,x,y+16,paint)
                    if(tx==scene.width-1||i+1 !in scene.enabled)c.drawLine(x+16,y,x+16,y+16,paint)
                    if(i-scene.width !in scene.enabled)c.drawLine(x,y,x+16,y,paint)
                    if(i+scene.width !in scene.enabled)c.drawLine(x,y+16,x+16,y+16,paint)
                }
            }
        }
        paint.color=Color.WHITE;paint.alpha=255
        val actors=content.npcs.filter{it.mapId==world.mapId}.sortedBy{it.y}
        for(npc in actors.filter{it.y*16+8<=world.y})
            c.drawBitmap(npc.sprite,npc.x*16f,npc.y*16f,paint)
        c.drawBitmap(content.sprites.getValue(world.direction),(world.x-8).toFloat(),(world.y-8).toFloat(),paint)
        for(npc in actors.filter{it.y*16+8>world.y})
            c.drawBitmap(npc.sprite,npc.x*16f,npc.y*16f,paint)
        if(layer==Layer.MAP){
            overlayPaint.color=0xff75ded5.toInt();overlayPaint.alpha=230
            for(npc in nearbyNpcs())c.drawCircle(npc.x*16f+8,npc.y*16f-2,1.6f,overlayPaint)
        }
        c.restore()
        when(layer){Layer.MAP->{drawControls(c);drawHud(c)};Layer.MENU->drawMenu(c);Layer.SETTINGS->Unit;Layer.DIALOGUE->drawDialogue(c);
            Layer.CHARACTER,Layer.INVENTORY->drawInfoPanel(c);Layer.BATTLE->drawBattle(c);Layer.SHOP->drawShop(c)}
        if(world.message!=previousMessage){previousMessage=world.message;if(world.message.startsWith("开发边界"))noticeUntil=SystemClock.uptimeMillis()+1800}
        if(layer==Layer.MAP&&SystemClock.uptimeMillis()<noticeUntil){
            val dp=resources.displayMetrics.density;val label=world.message
            textPaint.textSize=13*dp;val tw=textPaint.measureText(label);val tx=(width-tw)/2
            overlayPaint.color=0xb5000000.toInt();c.drawRoundRect(RectF(tx-12*dp,ui.safe.y+8*dp,tx+tw+12*dp,ui.safe.y+34*dp),8*dp,8*dp,overlayPaint)
            c.drawText(label,tx,ui.safe.y+27*dp,textPaint)
        }
        if(debug&&layer==Layer.MAP)drawDebug(c,cam)
    }
    private fun label(c:Canvas,value:String,x:Float,y:Float,sp:Float){textPaint.textSize=sp*resources.displayMetrics.density;c.drawText(value,x-textPaint.measureText(value)/2,y+textPaint.textSize*.34f,textPaint)}
    private fun portrait(c:Canvas,character:CharacterState,box:Box){
        overlayPaint.color=0xff355063.toInt();c.drawRoundRect(RectF(box.x,box.y,box.x+box.w,box.y+box.h),6f,6f,overlayPaint)
        val image=content.characterDefinitions[character.id]?.portrait
        if(image!=null){paint.color=Color.WHITE;paint.alpha=255;paint.isFilterBitmap=false
            c.drawBitmap(image,null,RectF(box.x,box.y,box.x+box.w,box.y+box.h),paint)
        } else {textPaint.color=Color.WHITE;label(c,"?",box.x+box.w/2,box.y+box.h/2,18f)}
    }
    private fun gauge(c:Canvas,box:Box,current:Int,maxValue:Int?,color:Int){
        overlayPaint.color=0xff253742.toInt();c.drawRoundRect(RectF(box.x,box.y,box.x+box.w,box.y+box.h),box.h/2,box.h/2,overlayPaint)
        if(maxValue!=null && maxValue>0){
            val fill=box.w*(current.toFloat()/maxValue).coerceIn(0f,1f)
            overlayPaint.color=color;c.drawRoundRect(RectF(box.x,box.y,box.x+fill,box.y+box.h),box.h/2,box.h/2,overlayPaint)
        }
    }
    private fun drawHud(c:Canvas){
        val hero=characters.firstOrNull()?:return;val b=hudBox();val dp=resources.displayMetrics.density
        overlayPaint.color=0xc923303b.toInt();c.drawRoundRect(RectF(b.x,b.y,b.x+b.w,b.y+b.h),9*dp,9*dp,overlayPaint)
        portrait(c,hero,hudPortraitBox())
        val p=hudPortraitBox();val x=p.x+p.w+8*dp;val right=b.x+b.w-8*dp
        textPaint.color=Color.WHITE;textPaint.textSize=13*dp
        val name=content.characterDefinitions[hero.id]?.name?:hero.id
        c.drawText("$name  Lv.${hero.level}",x,b.y+b.h*.26f,textPaint)
        textPaint.textSize=10*dp
        c.drawText("HP ${hero.hp}/${hero.maxHp}",x,b.y+b.h*.49f,textPaint)
        gauge(c,Box(x,b.y+b.h*.54f,right-x,b.h*.075f),hero.hp,hero.maxHp,0xffc55758.toInt())
        c.drawText("MP ${hero.mp}/${hero.maxMp?:"?"}",x,b.y+b.h*.75f,textPaint)
        gauge(c,Box(x,b.y+b.h*.80f,right-x,b.h*.075f),hero.mp,hero.maxMp,0xff638cce.toInt())
    }
    private fun drawControls(c:Canvas){
        val stick=ui.stick;val alpha=(config.opacity.coerceIn(.2f,.9f)*255).toInt()
        overlayPaint.color=0x28475b;overlayPaint.alpha=alpha
        c.drawCircle(stick.x+stick.w/2,stick.y+stick.h/2,stick.w/2,overlayPaint)
        overlayPaint.color=Color.WHITE;overlayPaint.alpha=(alpha*.72f).toInt();overlayPaint.style=Paint.Style.STROKE;overlayPaint.strokeWidth=max(2f,stick.w*.018f)
        c.drawCircle(stick.x+stick.w/2,stick.y+stick.h/2,stick.w*.43f,overlayPaint);overlayPaint.style=Paint.Style.FILL
        overlayPaint.color=if(input.stickDirection!=null)0xff6ed3cf.toInt() else 0xffb8d0d8.toInt();overlayPaint.alpha=min(235,alpha+60)
        c.drawCircle(stick.x+stick.w/2+input.stickX,stick.y+stick.h/2+input.stickY,stick.w*.21f,overlayPaint)
        for((key,b)in ui.buttons){
            overlayPaint.color=when{!mapControlEnabled(key)->0xff34434c.toInt();input.pressed(key)->0xff44c6b5.toInt();else->0xff263e51.toInt()};overlayPaint.alpha=if(mapControlEnabled(key))alpha else max(115,alpha)
            c.drawCircle(b.x+b.w/2,b.y+b.h/2,b.w/2,overlayPaint)
            val title=when(key){Key.A->"交互";Key.B->"返回";else->"☰"}
            textPaint.color=if(mapControlEnabled(key))Color.WHITE else 0xffa7b4bb.toInt()
            label(c,title,b.x+b.w/2,b.y+b.h/2-(if(key==Key.MENU)0f else b.h*.07f),if(key==Key.MENU)16f else 15f)
            if(key==Key.A||key==Key.B)label(c,key.name,b.x+b.w/2,b.y+b.h*.31f,9f)
            textPaint.color=Color.WHITE
        }
    }
    private fun drawMenu(c:Canvas){
        val dp=resources.displayMetrics.density;val b=menuBox()
        overlayPaint.color=0x96000000.toInt();c.drawRect(0f,0f,width.toFloat(),height.toFloat(),overlayPaint)
        overlayPaint.color=0xe921303b.toInt();c.drawRoundRect(RectF(b.x,b.y,b.x+b.w,b.y+b.h),18*dp,18*dp,overlayPaint)
        label(c,"菜单",b.x+b.w/2,b.y+b.h*.12f,21f)
        val close=menuCloseBox();overlayPaint.color=0x77475b68;c.drawCircle(close.x+close.w/2,close.y+close.h/2,close.w*.42f,overlayPaint)
        label(c,"×",close.x+close.w/2,close.y+close.h/2,20f)
        for(i in menuChoices.indices){
            val cy=b.y+b.h*(.22f+.18f*(i+.5f))
            if(i==menuSelection){overlayPaint.color=0x7735bdb0;c.drawRoundRect(RectF(b.x+12*dp,cy-b.h*.075f,b.x+b.w-12*dp,cy+b.h*.075f),10*dp,10*dp,overlayPaint)}
            label(c,menuChoices[i],b.x+b.w/2,cy,16f)
        }
    }
    private fun equipmentBonus(hero:CharacterState,slot:String):Int {
        val e=hero.equipment?:return 0
        val id=when(slot){"rightHand"->e.rightHand;"body"->e.body;"feet"->e.feet;else->-1}
        val d=content.equipmentDefinitions.values.firstOrNull{it.slot==slot&&it.originalId==id}?:return 0
        return when(slot){"rightHand"->d.attackBonus;"body"->d.defenseBonus;"feet"->d.evasionValue;else->0}
    }
    private fun drawInfoPanel(c:Canvas){
        if(directPanel()){drawDirectPanel(c);return}
        val dp=resources.displayMetrics.density;val b=panelBox();val hero=characters[characterPage]
        overlayPaint.color=0x88000000.toInt();c.drawRect(0f,0f,width.toFloat(),height.toFloat(),overlayPaint)
        overlayPaint.color=Color.BLACK;c.drawRect(b.x,b.y,b.x+b.w,b.y+b.h,overlayPaint)
        overlayPaint.color=Color.WHITE;overlayPaint.style=Paint.Style.STROKE;overlayPaint.strokeWidth=2*dp
        c.drawRect(b.x,b.y,b.x+b.w,b.y+b.h,overlayPaint);overlayPaint.style=Paint.Style.FILL
        val close=panelBackBox();overlayPaint.color=0xff476273.toInt()
        c.drawCircle(close.x+close.w/2,close.y+close.h/2,close.w*.42f,overlayPaint)
        textPaint.color=Color.WHITE;label(c,"×",close.x+close.w/2,close.y+close.h/2,19f)
        val topPortrait=Box(b.x+13*dp,b.y+14*dp,min(56*dp,b.h*.17f),min(56*dp,b.h*.17f))
        portrait(c,hero,topPortrait)
        val x=topPortrait.x+topPortrait.w+10*dp;val right=b.x+b.w-15*dp
        textPaint.color=Color.WHITE;textPaint.textSize=15*dp
        c.drawText("${content.characterDefinitions[hero.id]?.name?:hero.id}  Lv.${hero.level}",x,b.y+b.h*.075f,textPaint)
        textPaint.textSize=11*dp
        c.drawText("HP ${hero.hp}/${hero.maxHp}",x,b.y+b.h*.115f,textPaint)
        gauge(c,Box(x,b.y+b.h*.125f,right-x,b.h*.012f),hero.hp,hero.maxHp,0xffc55758.toInt())
        c.drawText("MP ${hero.mp}/${hero.maxMp?:"?"}",x,b.y+b.h*.165f,textPaint)
        gauge(c,Box(x,b.y+b.h*.175f,right-x,b.h*.012f),hero.mp,hero.maxMp,0xff638cce.toInt())
        c.drawText("EXP ${hero.experience}",x,b.y+b.h*.215f,textPaint)
        for(i in characters.indices){
            val p=partyBox(i);portrait(c,characters[i],p)
            if(i==characterPage){overlayPaint.color=0xff72d3c5.toInt();overlayPaint.style=Paint.Style.STROKE;overlayPaint.strokeWidth=2*dp
                c.drawRoundRect(RectF(p.x,p.y,p.x+p.w,p.y+p.h),5*dp,5*dp,overlayPaint);overlayPaint.style=Paint.Style.FILL}
        }
        val titles=listOf("属性","装备","物品","法术")
        for(i in titles.indices){
            val tab=tabBox(i)
            overlayPaint.color=if(panelTab.ordinal==i)0xff31776e.toInt() else 0xff344b5a.toInt()
            c.drawRoundRect(RectF(tab.x+2*dp,tab.y,tab.x+tab.w-2*dp,tab.y+tab.h),5*dp,5*dp,overlayPaint)
            textPaint.color=Color.WHITE;label(c,titles[i],tab.x+tab.w/2,tab.y+tab.h/2,12f)
        }
        textPaint.color=Color.WHITE;textPaint.textSize=13*dp
        val left=b.x+17*dp
        when(panelTab){
            CharacterTab.ATTRIBUTES->{
                val lines=listOf("等级 ${hero.level}     经验 ${hero.experience}",
                    "HP ${hero.hp}/${hero.maxHp}     MP ${hero.mp}/${hero.maxMp?:"?"}",
                    "攻击力 ${hero.strength+equipmentBonus(hero,"rightHand")}  防御力 ${hero.stamina+equipmentBonus(hero,"body")}",
                    "敏捷 ${hero.agility}     精神 ${hero.spirit}","银两 $money")
                for((i,line)in lines.withIndex())c.drawText(line,left,b.y+b.h*(.565f+i*.075f),textPaint)
            }
            CharacterTab.EQUIPMENT,CharacterTab.ITEMS->Unit // Drawn by the scoped direct modal above.
            CharacterTab.MAGIC->{
                c.drawText("尚未开放",left,b.y+b.h*.59f,textPaint)
                c.drawText("已学法术状态尚未迁移",left,b.y+b.h*.67f,textPaint)
            }
        }
        textPaint.color=Color.WHITE
    }
    private fun drawDialogue(c:Canvas){
        val dp=resources.displayMetrics.density
        val x=ui.safe.x+max(12*dp,ui.safe.w*.08f)
        val w=ui.safe.w-2*max(12*dp,ui.safe.w*.08f)
        val h=min(ui.safe.h*.36f,150*dp)
        val y=ui.safe.y+ui.safe.h-h-12*dp
        overlayPaint.color=0xe921303b.toInt();c.drawRoundRect(RectF(x,y,x+w,y+h),12*dp,12*dp,overlayPaint)
        textPaint.textSize=16*dp;textPaint.color=Color.WHITE
        val lines=dialogueLines().getOrNull(dialoguePage)?:emptyList()
        for((i,line)in lines.withIndex())c.drawText(line,x+18*dp,y+(44+32*i)*dp,textPaint)
        val hint=if(dialoguePage+1<dialogueLines().size)"轻触继续" else "轻触结束"
        textPaint.textSize=12*dp;c.drawText(hint,x+w-textPaint.measureText(hint)-15*dp,y+h-12*dp,textPaint)
    }
    private fun drawBattle(c:Canvas){
        val current=battle?:return;val b=battleBox();val scale=b.w/256f
        val screen=battlePresentation.screen
        c.drawColor(Color.BLACK)
        paint.color=Color.WHITE;paint.alpha=255;paint.isFilterBitmap=false
        content.battleHorizon?.takeIf{screen!=BattlePresentation.Screen.RESULT}?.let{image->
            c.drawBitmap(image,null,RectF(b.x,b.y,b.x+b.w,b.y+32*scale),paint)
        }
        val action=battlePresentation.action
        val hp=action?.heroHp?:current.hero.hp
        for(enemy in current.enemies.filter{screen!=BattlePresentation.Screen.RESULT}){
            val visibleHp=action?.enemyHp?.get(enemy.slot)?:enemy.hp
            val impact=action?.targetSlot==enemy.slot && action.text.contains("受到")
            if(visibleHp<=0&&!impact)continue
            val box=battleEnemyBox(enemy)
            val image=content.enemyGraphics[enemy.definition.id]
            paint.alpha=if(impact&&battlePresentation.elapsedMs/80%2==0L)80 else 255
            if(image!=null)c.drawBitmap(image,null,RectF(box.x,box.y,box.x+box.w,box.y+box.h),paint)
            paint.alpha=255
            if(battlePresentation.screen==BattlePresentation.Screen.TARGET&&enemy.slot==selectedBattleSlot){
                overlayPaint.color=Color.WHITE;overlayPaint.style=Paint.Style.STROKE;overlayPaint.strokeWidth=max(1f,scale)
                c.drawRect(box.x-2*scale,box.y-2*scale,box.x+box.w+2*scale,box.y+box.h+2*scale,overlayPaint)
                overlayPaint.style=Paint.Style.FILL
            }
            if(action?.targetSlot==enemy.slot||action?.actorSlot==enemy.slot){
                textPaint.color=Color.WHITE;textPaint.textSize=9*scale
                c.drawText("▼",box.x+box.w/2-4*scale,box.y-5*scale,textPaint)
            }
        }
        fun border(box:Box){
            overlayPaint.color=Color.BLACK;c.drawRect(box.x,box.y,box.x+box.w,box.y+box.h,overlayPaint)
            overlayPaint.color=Color.WHITE;overlayPaint.style=Paint.Style.STROKE;overlayPaint.strokeWidth=2*scale
            c.drawRect(box.x,box.y,box.x+box.w,box.y+box.h,overlayPaint);overlayPaint.style=Paint.Style.FILL
        }
        fun line(value:String,x:Float,y:Float,size:Float=9f,color:Int=Color.WHITE){
            textPaint.color=color;textPaint.textSize=size*scale
            c.drawText(value,b.x+x*scale,b.y+y*scale,textPaint)
        }
        if(screen==BattlePresentation.Screen.COMMAND){
            border(battleRegion(10f,148f,66f,76f));border(battleRegion(82f,148f,156f,76f))
            val commands=listOf("戰鬥","法術","寶箱","防禦","逃跑")
            commands.forEachIndexed{i,name->line((if(battlePresentation.command==i)"▶" else "  ")+name,13f,160f+13*i,
                color=if(i in listOf(0,4))Color.WHITE else 0xff747474.toInt())}
            line(content.characterDefinitions[current.hero.id]?.name?:current.hero.id,88f,162f)
            line("$hp / ${current.hero.maxHp}",156f,162f)
            line("${current.hero.mp} / ${current.hero.maxMp?:"?"}",156f,177f)
            line("HP",126f,162f);line("MP",126f,177f)
            line("灰色指令尚未开放",88f,215f,8f,0xffaaaaaa.toInt())
        }else if(screen==BattlePresentation.Screen.RESULT){
            // The target ROM clears the scene for its black, white-bordered result message.
            border(battleRegion(10f,76f,228f,74f))
            battleMessage.chunked(22).take(3).forEachIndexed{i,value->line(value,15f,92f+i*15)}
            line("A 继续",15f,142f,8f)
        }else{
            border(battleRegion(10f,148f,228f,76f))
            when(screen){
                BattlePresentation.Screen.TARGET->{
                    current.enemies.filter{it.hp>0}.forEachIndexed{i,e->
                        line((if(e.slot==selectedBattleSlot)"▶ " else "  ")+e.definition.name,15f,159f+9*i,8f)}
                    line("选择目标 · A确认 / B返回",15f,216f,8f)
                }
                BattlePresentation.Screen.ENTRY->line("敌人出现了！",15f,166f)
                BattlePresentation.Screen.ACTING->{
                    (action?.text?:"").chunked(22).take(3).forEachIndexed{i,value->line(value,15f,166f+i*15)}
                    line("${content.characterDefinitions[current.hero.id]?.name?:current.hero.id}  HP $hp / ${current.hero.maxHp}",15f,216f,8f)
                }
                else->Unit
            }
        }
        if(screen==BattlePresentation.Screen.ACTING && action?.targetSlot!=null && action.text.startsWith("攻击")){
            content.battleHero?.let{image->
                val actor=battleRegion(120f,124f,16f,16f)
                c.drawBitmap(image,null,RectF(actor.x,actor.y,actor.x+actor.w,actor.y+actor.h),paint)
            }
        }
        // Reuse the existing A/B geometry and style; no exploration controls participate.
        for(key in listOf(Key.A,Key.B)){
            val box=ui.buttons.getValue(key)
            val enabled=if(key==Key.A)screen in listOf(BattlePresentation.Screen.COMMAND,BattlePresentation.Screen.TARGET,BattlePresentation.Screen.RESULT)
                else screen==BattlePresentation.Screen.TARGET
            overlayPaint.color=if(enabled)0xa0344b5a.toInt() else 0x58344b5a
            c.drawOval(RectF(box.x,box.y,box.x+box.w,box.y+box.h),overlayPaint)
            textPaint.color=if(enabled)Color.WHITE else 0xff909999.toInt()
            label(c,if(key==Key.A)"A 确认" else "B 返回",box.x+box.w/2,box.y+box.h/2,12f)
        }
        textPaint.color=Color.WHITE
    }
    private fun drawDebug(c:Canvas,cam:Camera){
        val dp=resources.displayMetrics.density
        overlayPaint.color=0xaa000000.toInt();c.drawRect(0f,0f,width.toFloat(),ui.safe.y+53*dp,overlayPaint)
        textPaint.textSize=10*dp
        c.drawText("Map ${world.mapId} · ${world.x/16},${world.y/16} · ${world.scene.enabled.size} 格碰撞可行范围 · ${world.message}",ui.safe.x+4*dp,ui.safe.y+15*dp,textPaint)
        c.drawText("${width}×$height density=${resources.displayMetrics.density} safe=$safe cam=${cam.x.toInt()},${cam.y.toInt()} scale=${ui.scale} mode=$mode ${content.scene.version}",ui.safe.x+4*dp,ui.safe.y+32*dp,textPaint)
        overlayPaint.color=Color.CYAN;overlayPaint.style=Paint.Style.STROKE;overlayPaint.strokeWidth=2f
        for(b in listOf(ui.game,ui.safe,ui.stick)+ui.buttons.values)c.drawRect(b.x,b.y,b.x+b.w,b.y+b.h,overlayPaint)
        overlayPaint.style=Paint.Style.FILL
    }
    private fun settings(){
        layer=Layer.SETTINGS;input.clear();menuTouch.clear();clock.reset()
        var child=0
        val choices=arrayOf("显示：${mode.name}（全屏 / 原版比例 / 整数裁切）","开发者调试层：$debug","触觉反馈：$haptic","检查应用更新","封神云存档 / 登录","查看设备与开发范围","摇杆/按钮参数 JSON","恢复默认控件","回到初始位置（仅调试）","声音与诊断上传（${if(Diagnostics.enabled)"上传开启" else "上传关闭"}）")
        modalDialog=AlertDialog.Builder(activity).setTitle("设置 · 操作验证版").setItems(choices){_,i->
            when(i){
                0->{mode=when(mode){DisplayMode.FULL->DisplayMode.ORIGINAL;DisplayMode.ORIGINAL->DisplayMode.INTEGER;DisplayMode.INTEGER->DisplayMode.FULL};prefs.edit().putString("display-v2",mode.name).apply();relayout()}
                1->{debug=!debug;prefs.edit().putBoolean("debug",debug).apply()}
                2->{haptic=!haptic;prefs.edit().putBoolean("haptic",haptic).apply()}
                3->child=3
                4->child=4
                5->child=5
                6->child=6
                7->{config=ControlConfig();prefs.edit().remove("controls-v2").apply();relayout()}
                8->world.reset()
                9->child=9
            }
        }.setNegativeButton("返回",null).setOnDismissListener{
            modalDialog=null
            when(child){
                3->{returnToMenu();activity.checkForUpdates()}
                4->{returnToMenu();activity.openCloudSave()}
                5->showInfo()
                6->editControls()
                9->audioSettings()
                else->returnToMenu()
            }
        }.show()
    }
    private fun audioSettings(){
        layer=Layer.SETTINGS;input.clear();clock.reset()
        val body=android.widget.LinearLayout(activity).apply{orientation=android.widget.LinearLayout.VERTICAL;setPadding(24,8,24,8)}
        fun toggle(title:String,value:Boolean,change:(Boolean)->Unit){body.addView(android.widget.CheckBox(activity).apply{text=title;isChecked=value;setOnCheckedChangeListener{_,checked->change(checked)}})}
        fun volume(title:String,value:Float,change:(Float)->Unit){body.addView(TextView(activity).apply{text=title});body.addView(android.widget.SeekBar(activity).apply{max=100;progress=(value*100).toInt();setOnSeekBarChangeListener(object:android.widget.SeekBar.OnSeekBarChangeListener{override fun onStartTrackingTouch(s:android.widget.SeekBar){};override fun onStopTrackingTouch(s:android.widget.SeekBar){};override fun onProgressChanged(s:android.widget.SeekBar,p:Int,user:Boolean){if(user)change(p/100f)}})})}
        toggle("音乐",audio.musicEnabled){audio.settings(music=it)};volume("音乐音量",audio.musicVolume){audio.settings(mv=it)}
        toggle("音效",audio.effectsEnabled){audio.settings(sfx=it)};volume("音效音量",audio.effectsVolume){audio.settings(sv=it)}
        body.addView(TextView(activity).apply{text="个人测试诊断：仅上传应用版本、设备型号、加载/存档/场景结果及脱敏异常，不上传存档正文、账号密码或系统日志。离线先缓冲；关闭后停止上传并删除待传诊断，游戏进度保留。"})
        toggle("诊断上传",Diagnostics.enabled){Diagnostics.setEnabled(it)}
        modalDialog=AlertDialog.Builder(activity).setTitle("声音与诊断").setView(android.widget.ScrollView(activity).apply{addView(body)}).setNegativeButton("返回",null).setOnDismissListener{modalDialog=null;returnToMenu()}.show()
    }
    private fun showInfo(){
        layer=Layer.SETTINGS;input.clear();clock.reset()
        val metrics=activity.windowManager.currentWindowMetrics.bounds
        modalDialog=AlertDialog.Builder(activity).setTitle("设备信息 / 开发范围").setMessage("${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}\nWindowMetrics=$metrics\nView=${width}×$height density=${resources.displayMetrics.density}\n$insetsDescription\nSafe=$safe\n场景显示区域=${ui.game}\n当前地图=${world.mapId} 世界=${world.scene.width*16}×${world.scene.height*16}，逻辑基线256×240；当前视野=${ui.viewWidth.toInt()}×${ui.viewHeight.toInt()}。\n缩放=${ui.scale} 模式=$mode\n刷新率=${display?.refreshRate}\n内容=${content.scene.version}\n地图114、16、0按当前已核对的步行碰撞类别开放；特殊入口仅在ROM出口位置启用。8名开局NPC可触发ROM文本，仆人赠刀及装备沿用。地图16随机遇敌、地图0的NPC与建筑内部尚未实现；未知碰撞类别保持关闭。\n玩家使用4个实测方向姿态，无行走动画。RGB为FCEUX调色板近似。\n一加13T真机验收：待用户反馈。")
            .setPositiveButton("关闭",null).setOnDismissListener{modalDialog=null;returnToMenu()}.show()
    }
    private fun editControls(){
        layer=Layer.SETTINGS;input.clear();clock.reset()
        val field=EditText(activity).apply{setText(prefs.getString("controls-v2",DEFAULT_CONTROLS));minLines=5}
        val dialog=AlertDialog.Builder(activity).setTitle("摇杆/按钮位置、大小、透明度、死区").setView(field).setPositiveButton("保存",null).setNegativeButton("取消",null).create()
        dialog.setOnShowListener{dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            try{val raw=field.text.toString();JSONObject(raw);config=readConfig(raw);prefs.edit().putString("controls-v2",raw).apply();relayout();dialog.dismiss()}
            catch(e:Exception){field.error="请输入有效 JSON 数值"}
        }}
        dialog.setOnDismissListener{modalDialog=null;returnToMenu()};modalDialog=dialog;dialog.show()
    }
    private fun readConfig(raw:String):ControlConfig {
        val o=try{JSONObject(raw)}catch(_:Exception){JSONObject()}
        fun n(k:String,d:Float,lo:Float=.05f,hi:Float=.95f)=o.optDouble(k,d.toDouble()).toFloat().let{if(it.isFinite())it.coerceIn(lo,hi) else d}
        return ControlConfig(n("stickX",.13f),n("stickY",.79f),n("stickSize",.9f,.4f,1.25f),
            n("mainX",.87f),n("mainY",.78f),n("mainSize",.95f,.4f,1.25f),
            n("secondaryX",.75f),n("secondaryY",.70f),n("secondarySize",.70f,.4f,1.1f),
            n("menuX",.95f),n("menuY",.06f),n("menuSize",.65f,.4f,1.2f),
            n("opacity",.56f,.2f,.9f),n("deadZone",MovementTuning.DEFAULT_DEAD_ZONE,.05f,.8f))
    }
    companion object {const val DEFAULT_CONTROLS="""{"stickX":0.13,"stickY":0.79,"stickSize":0.9,"mainX":0.87,"mainY":0.78,"mainSize":0.95,"secondaryX":0.75,"secondaryY":0.70,"secondarySize":0.70,"menuX":0.95,"menuY":0.06,"menuSize":0.65,"opacity":0.56,"deadZone":0.15}"""}
}
