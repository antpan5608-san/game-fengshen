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
    enum class Layer {FIELD_FAILURE, MAP, MENU, SETTINGS, DIALOGUE, CHARACTER, INVENTORY, BATTLE, SHOP, INN }
    enum class CharacterTab { ATTRIBUTES, EQUIPMENT, ITEMS, MAGIC }
    val world=World(content.scenes,content.exits,114)
    val input=InputState()
    val audio=GameAudio(activity,content.audio).also{it.scene(114)}
    private val clock=FixedClock()
    private val prefs=activity.getSharedPreferences("operation-a-ui",0)
    private val savePrefs=activity.getSharedPreferences("opening-local-save",0)
    private val hadPersistedAtStart=savePrefs.contains("saveJson")||savePrefs.contains("mapId")
    private var localSaveProtected=false
    private var savedSnapshot=""
    private val historyClock=AutoSaveHistoryClock()
    private var historyErrorReported=false
    private var characters=listOf(content.initialPlayer)
    private var inventory:Map<String,Int> = emptyMap()
    private var flags:Map<String,Boolean> = emptyMap()
    private var money=content.initialMoney
    private val encounter=content.battle?.let{OpeningEncounter(it)}
    private val battleRandom=SecureRandom()
    private var processedStepSeq=0L
    private var processedContactSeq=0L
    private var battle:OpeningBattle?=null
    private var battleCommitted=false
    private var storyBattle:StoryBattleDefinition?=null
    private var battleSavePending=false
    private var battleID=""
    private var battleMessage=""
    private var battlePresentation=BattlePresentation()
    private var battleTouchRevision=0
    private val battleKeyRevisions=mutableMapOf<Int,Int>()
    private var selectedBattleSlot=0
    private val battleTouch=mutableMapOf<Int,Int>() // retained private name for historical fixture compatibility
    private var battleGesture:BattleTouchGesture?=null
    private var battleBlocked=false
    private var battleItemsOpen=false
    private var selectedBattleItem:String?=null
    private var selectedBattleTarget:String?=null
    private var battleItemListScroll=0f
    private var battleItemDetailScroll=0f
    private var battleInfoOpen=false
    private var battleInfoHeroId:String?=null
    private var battleAttackSelection:BattleTouchCommand?=null
    private var battleInfoScroll=0f
    private var battleInfoListScroll=0f
    private var battleResultScroll=0f
    private var battleNotice=""
    private var battleResultBefore:CharacterState?=null
    private var battleResultLines:List<String> = emptyList()
    private var battleResultDetails=false
    private var battleResultParty:List<Pair<CharacterState,Int>> = emptyList()
    private val diagnosedExperience=mutableSetOf<String>()
    fun growthProgress(hero:CharacterState)=experienceProgress(hero,content.battle?.growthFor(hero.id)?:emptyList(),
        hero.id,content.battle?.maxLevelFor(hero.id))
    private fun diagnoseExperience(){for(hero in characters){val progress=growthProgress(hero)
        if(progress.status==ExperienceProgress.Status.INVALID){val key="${hero.id}:${hero.level}:${hero.experience}:${progress.reason}"
            if(diagnosedExperience.add(key))Diagnostics.record("experience_state_inconsistent","ERROR",JSONObject()
                .put("character",hero.id).put("level",hero.level).put("experience",hero.experience).put("reason",progress.reason))}}}
    private fun clearBattleGesture(){battleGesture=null;battleBlocked=false;battleTouch.clear()}
    fun battleCommandBounds(index:Int)=battleLayout().commands[index]
    fun battleTargetBounds(slot:Int):Box {
        val i=battle?.enemies?.indexOfFirst{it.slot==slot}?:return Box(0f,0f,0f,0f)
        return if(battleInfoOpen)battleInfoLayout().visibleRow(i,battleInfoListScroll) else battleLayout().enemies.getOrNull(i)?:Box(0f,0f,0f,0f)
    }
    fun battleInfoCloseBounds()=battleInfoLayout().close
    fun battleVisibleHp(slot:Int)=battlePresentation.action?.enemyHp?.get(slot)?:battle?.enemies?.firstOrNull{it.slot==slot}?.hp
    fun battleResultBounds()=battleLayout().result
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
    var active=false;set(v){field=v;if(!v){historyClock.pause();finishPendingStep()};input.clear();panelTouch.clear();clearUxGesture();hudTouch.clear();clearAttackChoice();clearBattleGesture();battlePresentation.invalidateInput();shopTouch.clear();clearUxGesture();clock.reset()}
    var focused=true;set(v){field=v;if(!v){historyClock.pause();finishPendingStep();input.clear();panelTouch.clear();clearUxGesture();hudTouch.clear();clearAttackChoice();clearBattleGesture();battlePresentation.invalidateInput();shopTouch.clear();clearUxGesture();clock.reset()}}
    var layer=Layer.MAP;private set
    val menuOpen get()=layer==Layer.MENU
    var menuSelection=0;private set
    private var surface=false
    private var posted=false
    private var ferryLastStepMs=0L
    private var ferryPaused=false
    private var ui=layout(1,1,1f,safe,mode,config,world.scene.width*16,world.scene.height*16)
    private val paint=Paint().apply{isFilterBitmap=false;isAntiAlias=false}
    private val overlayPaint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply{color=Color.WHITE;typeface=Typeface.create("sans-serif",Typeface.NORMAL)}
    private val battleLinePaint=android.text.TextPaint(Paint.ANTI_ALIAS_FLAG)
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
    private var inn:InnDefinition?=null
    private var clinic:ClinicDefinition?=null
    private var clinicTargetId:String?=null
    private var clinicTreatmentId:String?=null
    val activeClinicId get()=clinic?.id
    val activeInnId get()=inn?.id
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
    private var selectedFieldSpell:String?=null
    private var fieldMagicCaster:String?=null
    private var fieldMagicChoosingTarget=false
    private var modalDialog:AlertDialog?=null
    private var previousMessage=""
    private var noticeUntil=0L
    private var mapNotice=""
    init {holder.addCallback(this);isFocusable=true;isFocusableInTouchMode=true;contentDescription="封神全屏地图"
        world.sceneResolver={mid->content.sceneForState(mid,flags)}
        world.arrivalResolver={mid->content.mapArrivals.fold(mid){target,rule->
            rule.resolve(target,characters.size,inventory,flags)}}
        world.prepareTarget={target->try{content.atlases.getValue(target);true}catch(e:Exception){
            input.clear();Diagnostics.record("scene_load","ERROR",JSONObject().put("mapId",target).put("success",false),e.javaClass.simpleName,e.stackTrace.take(12).joinToString("\n"));false}}
        world.transitionObserver={from,to,success->
            val error=world.transitionFailure
            Diagnostics.record("map_transition","ERROR",JSONObject().put("success",success).put("fromMapId",from).put("mapId",to),
                error?.javaClass?.simpleName?:"target_map_or_spawn_unavailable",error?.stackTrace?.take(12)?.joinToString("\n")?:"")}}
    fun relayout(){clearAttackChoice();clearBattleGesture();battlePresentation.invalidateInput();ui=layout(width,height,resources.displayMetrics.density,safe,mode,config,world.scene.width*16,world.scene.height*16);layoutMapId=world.mapId;input.clear();menuTouch.clear();panelTouch.clear();clearUxGesture();hudTouch.clear();npcTouch.clear();shopTouch.clear();clearUxGesture();clock.reset()}
    fun currentSnapshot()=SaveSnapshot(content.scene.version,world.mapId,world.x,world.y,world.direction,characters,inventory,flags,money,encounter?.steps?:0,world.interiorContext,world.terrainMode)
    fun hasMeaningfulLocalSave():Boolean = hadPersistedAtStart || world.mapId!=114 ||
        world.x!=content.scene.spawnX*16+8 || world.y!=content.scene.spawnY*16+8 || characters!=listOf(content.initialPlayer) ||
        inventory.isNotEmpty() || flags.isNotEmpty() || money!=content.initialMoney
    fun backupBeforeCloudRestore(){savePrefs.getString("saveJson",null)?.let{savePrefs.edit().putString("preCloudRecovery",it).commit()}}
    fun restoreSnapshot(snapshot:SaveSnapshot):Boolean {
        val loaded=snapshot.copy(flags=OriginalNpcTalk.flagsAfterMapLoad(snapshot.mapId,snapshot.characters.size,snapshot.flags))
        if(!applySnapshotState(loaded))return false
        localSaveProtected=false;savedSnapshot="";diagnoseExperience();persistState()
        if(flags[FIELD_FAILURE_FLAG]==true)post{showFieldFailure()}
        return true
    }
    /** Apply a validated proposal in memory; transaction owners decide when to persist. */
    private fun applySnapshotState(snapshot:SaveSnapshot):Boolean {
        if(!snapshot.validate(content))return false
        clearUxGesture();uxRevision++;world.finishStep();input.clear();clock.reset()
        val priorFlags=flags;flags=snapshot.flags
        if(!world.tryRestore(snapshot.mapId,snapshot.x,snapshot.y,0,snapshot.direction,snapshot.resolvedInteriorContext(content),snapshot.terrainMode)){
            flags=priorFlags;return false
        }
        audio.scene(world.mapId)
        encounter?.restore(snapshot.encounterSteps);processedStepSeq=world.completedStepSeq;processedContactSeq=world.contactTransitionSeq
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
        return true
    }
    fun restorePersisted(){
        val encoded=savePrefs.getString("saveJson",null)
        if(encoded!=null){
            localSaveProtected=true
            val result=runCatching{
                val snapshot=SaveSnapshot.parse(encoded)
                // Preserve the first pre-migration JSON before restoreSnapshot can persist a new format.
                // Never replace an existing recoverable backup; failed commits leave the original protected.
                if(snapshot.contentVersion!=content.scene.version && !savePrefs.contains("preContentMigration")){
                    if(!savePrefs.edit().putString("preContentMigration",encoded).commit())
                        throw IllegalStateException("pre_migration_backup_failed")
                }
                restoreSnapshot(snapshot)
            }
            val restored=result.getOrDefault(false)
            Diagnostics.record("save_load",if(restored)"INFO" else "ERROR",JSONObject()
                .put("success",restored).put("existingSaveProtected",!restored)
                .put("cause",result.exceptionOrNull()?.javaClass?.simpleName?:world.transitionFailure?.javaClass?.simpleName?:""),
                if(restored)"" else "invalid_local_snapshot")
            if(!restored)mapNotice="存档未能恢复 · 原存档已保留";return
        }
        if(savePrefs.contains("mapId")){
            localSaveProtected=true
            if(savePrefs.getString("contentVersion",null)!=content.scene.version){
                Diagnostics.record("save_load","ERROR",code="legacy_content_version_unavailable")
                mapNotice="旧存档内容未能恢复 · 原存档已保留";return
            }
            val restored=world.tryRestore(savePrefs.getInt("mapId",114),savePrefs.getInt("x",world.x),savePrefs.getInt("y",world.y),0,
                Key.entries.getOrElse(savePrefs.getInt("direction",Key.DOWN.ordinal)){Key.DOWN})
            if(restored)localSaveProtected=false else mapNotice="旧存档场景未能恢复 · 原存档已保留"
            Diagnostics.record("save_load",if(restored)"INFO" else "ERROR",JSONObject().put("success",restored),
                if(restored)"legacy_position_restored" else "legacy_scene_unavailable")
            return
        }
        Diagnostics.record("save_load",details=JSONObject().put("success",true),code="new_game_no_save")
    }
    fun persistState(){persistStateResult()}
    private fun persistStateResult():Boolean {
        if(localSaveProtected || world.remaining!=0 || (layer==Layer.BATTLE && !battleCommitted))return false
        return writeLocalSnapshot(currentSnapshot())
    }
    private fun writeLocalSnapshot(snapshot:SaveSnapshot,history:List<SaveHistoryEntry>?=null):Boolean {
        val encoded=snapshot.json().toString()
        if(history==null&&encoded==savedSnapshot)return true
        val touched=listOf("contentVersion","mapId","x","y","direction","saveJson",SaveHistory.KEY)
        val old=if(history!=null)savePrefs.all.filterKeys{it in touched}else emptyMap()
        val editor=savePrefs.edit().putString("contentVersion",snapshot.contentVersion).putInt("mapId",snapshot.mapId)
            .putInt("x",snapshot.x).putInt("y",snapshot.y).putInt("direction",snapshot.direction.ordinal)
            .putString("saveJson",encoded)
        if(history!=null)editor.putString(SaveHistory.KEY,SaveHistory.encode(history))
        val committed=editor.commit()
        if(!committed&&history!=null){
            // Android may change its in-memory preferences even when disk commit fails.
            // Restore only the keys touched by this transaction; never clear backups or cloud state.
            val rollback=savePrefs.edit();touched.forEach{rollback.remove(it)}
            old.forEach{(k,v)->when(v){is String->rollback.putString(k,v);is Int->rollback.putInt(k,v)}}
            if(!rollback.commit())localSaveProtected=true
        }
        Diagnostics.record("save_write",if(committed)"INFO" else "ERROR",JSONObject().put("success",committed).put("mapId",snapshot.mapId),if(committed)"" else "local_commit_failed")
        if(committed){savedSnapshot=encoded;activity.onLocalSnapshotSaved(snapshot)}
        return committed
    }
    private fun historySafe()= !localSaveProtected&&world.remaining==0&&battle==null&&
        layer in listOf(Layer.MAP,Layer.MENU,Layer.SETTINGS)&&
        OriginalFerry.pending(flags,content.ferries.values)==null
    fun saveHistoryResult(kind:SaveHistoryEntry.Kind=SaveHistoryEntry.Kind.MANUAL):Boolean {
        if(kind==SaveHistoryEntry.Kind.BEFORE_RESTORE||!historySafe())return false
        val snapshot=currentSnapshot();if(!snapshot.validate(content))return false
        val entries=runCatching{SaveHistory.parse(savePrefs.getString(SaveHistory.KEY,null))}.getOrElse{
            reportHistoryError("invalid_history");return false
        }
        val next=SaveHistory.append(entries,snapshot,kind,System.currentTimeMillis())
        val success=next==entries||writeLocalSnapshot(snapshot,next)
        if(success){historyClock.saved();historyErrorReported=false}
        return success
    }
    fun restoreHistoryResult(id:String):Boolean {
        if(!historySafe())return false
        val before=currentSnapshot()
        val entries=runCatching{SaveHistory.parse(savePrefs.getString(SaveHistory.KEY,null))}.getOrElse{
            reportHistoryError("invalid_history");return false
        }
        val proposal=SaveHistory.prepareRestore(entries,id,before,System.currentTimeMillis()){it.validate(content)}?:return false
        val target=proposal.target.copy(contentVersion=content.scene.version,
            flags=OriginalNpcTalk.flagsAfterMapLoad(proposal.target.mapId,proposal.target.characters.size,proposal.target.flags))
        val status=SaveHistory.applyRestore(proposal.copy(target=target),before,::applySnapshotState,::currentSnapshot){state,history->
            writeLocalSnapshot(state,history)
        }
        if(status!=SaveHistory.RestoreStatus.SAVED){
            if(status==SaveHistory.RestoreStatus.ROLLBACK_FAILED)localSaveProtected=true
            return false
        }
        historyClock.saved();historyErrorReported=false;diagnoseExperience()
        if(flags[FIELD_FAILURE_FLAG]==true)post{showFieldFailure()}
        Diagnostics.record("save_history_restore",details=JSONObject().put("success",true).put("mapId",world.mapId))
        return true
    }
    private fun reportHistoryError(code:String){
        if(!historyErrorReported){historyErrorReported=true;Diagnostics.record("save_history","ERROR",code=code)}
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int){relayout()}
    override fun surfaceCreated(h:SurfaceHolder){surface=true;schedule()}
    override fun surfaceChanged(h:SurfaceHolder,format:Int,w:Int,height:Int){relayout()}
    override fun surfaceDestroyed(h:SurfaceHolder){historyClock.pause();clearAttackChoice();clearBattleGesture();battlePresentation.invalidateInput();surface=false;input.clear();menuTouch.clear();panelTouch.clear();clearUxGesture();hudTouch.clear();npcTouch.clear();clock.reset();Choreographer.getInstance().removeFrameCallback(this);posted=false}
    private fun schedule(){if(surface&&!posted){posted=true;Choreographer.getInstance().postFrameCallback(this)}}
    override fun doFrame(time:Long){
        posted=false
        if(!surface)return
        if(active&&focused&&layer==Layer.MAP&&OriginalFerry.pending(flags,content.ferries.values)!=null){
            clock.reset();advanceFerryIfDue(time/1000000L)
        } else if(active&&focused&&layer==Layer.MAP)clock.advance(time){
            if(layer==Layer.MAP&&OriginalFerry.pending(flags,content.ferries.values)==null){
                if(!beginFreeBoatIfRequested()&&!beginFerryIfRequested(time/1000000L)){
                    world.tickIntent(input.movementIntent());processContactTransition();processCompletedStep()
                }
            }
        } else if(active&&focused&&layer==Layer.BATTLE)clock.advance(time){
            if(!battleInfoOpen&&!battleItemsOpen){
                if(battlePresentation.tick(16))finishBattlePresentation()
                // No touch command exists for a wholly state08 party. The
                // original scheduler still advances enemies and recovery once.
                if(battlePresentation.screen==BattlePresentation.Screen.COMMAND){
                    val current=battle
                    if(current!=null&&current.inputHero==null){
                        val revision=current.inputRevision
                        val turn=current.continueSkippedCommands{battleRandom.nextInt(256)}
                        if(turn!=null)showSubmittedBattleCommand(current,revision,turn)
                    }
                }
                if(battlePresentation.screen==BattlePresentation.Screen.RESULT&&battleCommitted&&
                    (storyBattle==null||storyBattle?.finalizeWithoutDialogue==true)&&
                    !battleSavePending&&!battleResultDetails&&!battleInfoOpen&&!battleItemsOpen&&battlePresentation.resultElapsedMs>=ordinaryResultDuration())closeBattle()
            }
        } else clock.reset()
        if(layoutMapId!=world.mapId){ui=layout(width,height,resources.displayMetrics.density,safe,mode,config,world.scene.width*16,world.scene.height*16);layoutMapId=world.mapId}
        if(world.remaining==0)persistState()
        historyClock.tick(time/1000000L,active&&focused,historySafe()){
            saveHistoryResult(SaveHistoryEntry.Kind.AUTO)
        }
        var canvas:Canvas?=null
        try {canvas=holder.lockCanvas();if(canvas!=null)render(canvas)} finally {if(canvas!=null){holder.unlockCanvasAndPost(canvas);if(active&&focused)activity.firstInteractiveFrame()}}
        schedule()
    }
    private fun commitFerry(before:SaveSnapshot,result:OriginalFerry.Result):Boolean {
        if(!result.applied||!result.snapshot.validate(content)||!applySnapshotState(result.snapshot)){
            ferryPaused=true;input.clear();clock.reset()
            Diagnostics.record("ferry_transition","ERROR",code="invalid_ferry_proposal")
            showNotice("渡船状态未能恢复，原状态已保留");return false
        }
        if(!persistStateResult()){
            if(!applySnapshotState(before))localSaveProtected=true
            ferryPaused=true;input.clear();clock.reset()
            showNotice("保存失败，此前状态已保留，请重新打开游戏重试");return false
        }
        npcTouch.clear();hudTouch.clear();dialogueTouch.clear();clearUxGesture()
        if(OriginalStatus.allDisabled(characters)){
            flags=flags+(FIELD_FAILURE_FLAG to true);showFieldFailure();persistState()
        }
        return true
    }
    private fun beginFerryIfRequested(now:Long):Boolean {
        if(world.remaining!=0)return false
        val key=input.movementIntent()?.primary?:return false
        val before=currentSnapshot()
        val rule=content.ferries.values.firstOrNull{OriginalFerry.matchesContact(before,it,key)}?:return false
        input.clear();clock.reset()
        if(localSaveProtected){showNotice("原存档受保护，不能提交渡船");return true}
        ferryPaused=false
        if(commitFerry(before,OriginalFerry.begin(before,rule,key,content.ferries.values))){
            ferryLastStepMs=now
            Diagnostics.record("ferry_start",details=JSONObject().put("eventId",rule.eventId).put("mapId",world.mapId))
        }
        return true
    }
    private fun beginFreeBoatIfRequested():Boolean {
        if(!content.freeBoatEnabled||world.remaining!=0)return false
        val key=input.movementIntent()?.primary?:return false
        val before=currentSnapshot()
        if(!OriginalBoat.contact(before,key))return false
        input.clear();npcTouch.clear();hudTouch.clear();clock.reset()
        if(localSaveProtected){showNotice("原存档受保护，不能提交船只移动");return true}
        val result=OriginalBoat.transfer(before,key)
        if(!result.applied)return true
        if(!result.snapshot.validate(content)||!applySnapshotState(result.snapshot)){
            showNotice("船只落点不可恢复，原状态已保留");Diagnostics.record("boat_transition","ERROR",code="invalid_boat_proposal");return true
        }
        if(!persistStateResult()){
            if(!applySnapshotState(before))localSaveProtected=true
            showNotice("保存失败，此前状态已保留");return true
        }
        Diagnostics.record("boat_transition",details=JSONObject().put("fromMapId",before.mapId).put("mapId",world.mapId))
        audio.scene(world.mapId)
        if(OriginalStatus.allDisabled(characters)){
            flags=flags+(FIELD_FAILURE_FLAG to true);showFieldFailure();persistState()
        }
        return true
    }
    private fun advanceFerryIfDue(now:Long){
        if(ferryPaused)return
        val rule=OriginalFerry.pending(flags,content.ferries.values)?:return
        val before=currentSnapshot()
        if(OriginalStatus.allDisabled(before.characters)){
            flags=flags+(FIELD_FAILURE_FLAG to true);showFieldFailure();persistState();return
        }
        val stage=rule.stage(flags)
        if(stage==null){ferryPaused=true;Diagnostics.record("ferry_transition","ERROR",code="invalid_ferry_stage");return}
        // Time advances presentation; only this durable command pays a step.
        // Returning from background resumes one step, never elapsed-time catchup.
        if(ferryLastStepMs==0L){ferryLastStepMs=now;return}
        val delay=if(rule.eventId==45&&stage==0)750L else 150L
        if(now-ferryLastStepMs<delay)return
        if(commitFerry(before,OriginalFerry.advance(before,rule,stage,content.ferries.values))){
            ferryLastStepMs=now
            if(OriginalFerry.pending(flags,content.ferries.values)==null){
                Diagnostics.record("ferry_complete",details=JSONObject().put("eventId",rule.eventId).put("mapId",world.mapId)
                    .put("x",world.x/16).put("y",world.y/16))
            }
        }
    }
    private fun processContactTransition(){
        if(processedContactSeq==world.contactTransitionSeq)return
        processedContactSeq=world.contactTransitionSeq
        val exit=world.lastContactExit?:return
        flags=OriginalNpcTalk.flagsAfterMapLoad(world.mapId,characters.size,flags)
        input.clear();npcTouch.clear();hudTouch.clear();clock.reset()
        Diagnostics.record("map_transition",details=JSONObject().put("success",true).put("fromMapId",exit.fromMapId)
            .put("mapId",world.mapId).put("contactActorId",exit.contactActorId).put("x",world.x/16).put("y",world.y/16))
        audio.scene(world.mapId)
        if(exit.resetEncounterSteps)encounter?.restore(0)
        // Contact did not finish a world step: never apply poison, field damage,
        // encounter RNG, or a second reward while presenting this transition.
        openSceneStoryIfNeeded()
    }
    private fun processCompletedStep(){
        if(processedStepSeq==world.completedStepSeq)return
        processedStepSeq=world.completedStepSeq
        val step=world.lastCompletedStep?:return
        if(content.freeBoatEnabled)flags=OriginalBoat.flagsAfterStep(flags,step)
        flags=WorldItems.fieldFlagsAfterStep(flags,step)
        characters=OriginalStatus.step(characters,step.mapId,flags[WorldItems.FIELD_ACTIVE_FLAG]==true)
        if(OriginalStatus.allDisabled(characters)){
            flags=flags+(FIELD_FAILURE_FLAG to true);showFieldFailure();persistState();return
        }
        if(step.transitioned){
            flags=OriginalNpcTalk.flagsAfterMapLoad(world.mapId,characters.size,flags)
            Diagnostics.record("map_transition",details=JSONObject().put("success",true).put("fromMapId",step.mapId)
                .put("mapId",world.mapId).put("x",world.x/16).put("y",world.y/16));audio.scene(world.mapId)
            val exit=content.exits.firstOrNull{it.fromMapId==step.mapId&&it.triggerX==step.x&&it.triggerY==step.y}
            if(exit?.resetEncounterSteps==true){
                encounter?.restore(0)
                // New-scene touch safety: a held old-scene gesture must not advance past the reviewed spawn.
                input.clear();npcTouch.clear();hudTouch.clear()
                openSceneStoryIfNeeded();return
            }
        }
        if(openEntryStoryIfNeeded())return
        if(applySceneMechanism())return
        if(step.suppressEncounter)return
        val group=encounter?.onCompletedStep(if(step.transitioned)-1 else step.mapId,step.x,step.y){battleRandom.nextInt(256)}?:return
        val rules=content.battle?:return
        persistState() // Stable pre-battle checkpoint; no mid-turn snapshot is written.
        storyBattle=null;battleSavePending=false
        battle=createPartyBattle(group,rules)
        selectedBattleSlot=group.members.first().slot
        battleMessage="遭遇敌群 ${group.id} · 选择目标后攻击"
        battleCommitted=false;battlePresentation=BattlePresentation();battleInfoOpen=false;battleItemsOpen=false;selectedBattleItem=null;battleNotice="";battleResultBefore=null;battleResultLines=emptyList();battleResultScroll=0f;resetBattleUiSelection();clearBattleGesture();layer=Layer.BATTLE
        battleID=java.util.UUID.randomUUID().toString()
        Diagnostics.record("battle_start",details=JSONObject().put("battleID",battleID).put("groupId",group.id).put("mapId",world.mapId))
        audio.scene(world.mapId,"battle")
        input.clear();battleTouch.clear();menuTouch.clear();panelTouch.clear();clearUxGesture();hudTouch.clear();npcTouch.clear();clock.reset()
    }
    private val FIELD_FAILURE_FLAG="runtime.field-defeat.pending"
    /** Same verified no-cartridge-manual-save failure branch already used by battles. */
    private fun resetOpeningAfterDefeat(){
        characters=listOf(content.initialPlayer);inventory=emptyMap();money=content.initialMoney
        flags=mapOf("opening.intro.seen" to true);encounter?.restore(0)
        world.restore(114,content.scene.spawnX*16+8,content.scene.spawnY*16+8,0,Key.DOWN)
        processedStepSeq=world.completedStepSeq
        processedContactSeq=world.contactTransitionSeq
    }
    private fun showFieldFailure(){
        if(flags[FIELD_FAILURE_FLAG]!=true||modalDialog!=null)return
        input.clear();clearUxGesture();clearBattleGesture();clock.reset();layer=Layer.FIELD_FAILURE
        val dialog=AlertDialog.Builder(activity).setTitle("不幸！全員陣亡了！")
            .setCancelable(false).setPositiveButton("继续",null).create()
        modalDialog=dialog
        dialog.setOnShowListener{
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
                if(flags[FIELD_FAILURE_FLAG]!=true)return@setOnClickListener
                val before=currentSnapshot();resetOpeningAfterDefeat()
                if(!persistStateResult()){
                    if(!restoreSnapshot(before))localSaveProtected=true
                    dialog.setMessage("保存失败 · 原状态已保留，请重试")
                    return@setOnClickListener
                }
                layer=Layer.MAP;audio.scene(world.mapId);dialog.dismiss();modalDialog=null;clock.reset()
            }
        }
        dialog.show()
    }
    private fun finishPendingStep():Boolean {
        world.finishStep();processCompletedStep()
        return layer==Layer.BATTLE
    }
    fun finishForLifecycle(){finishPendingStep()}
    private fun createPartyBattle(group:EncounterGroup,rules:BattleContent):OpeningBattle =
        OpeningBattle(group,rules,characters.first(),equipmentBonus(characters.first(),"rightHand"),
            equipmentBonus(characters.first(),"body")).also{current->
            current.configureParty(characters,characters.associate{it.id to content.characterDefinitions.getValue(it.id).originalActorIndex},
                characters.associate{it.id to equipmentBonus(it,"rightHand")},characters.associate{it.id to equipmentBonus(it,"body")})
        }
    /** Accepted input can queue the first actor without resolving a turn or consuming RNG. */
    private fun showSubmittedBattleCommand(current:OpeningBattle,beforeRevision:Int,turn:BattleTurn?):Boolean {
        if(turn==null&&current.inputRevision==beforeRevision)return false
        battleAttackSelection=null
        if(turn==null&&battlePresentation.screen==BattlePresentation.Screen.TARGET)battlePresentation.back()
        if(turn==null&&battlePresentation.screen==BattlePresentation.Screen.COMMAND)battlePresentation.selectCommand(0)
        input.clear();clearBattleGesture();battleTouch.clear();battlePresentation.invalidateInput()
        battleNotice=if(turn==null)"${current.inputHero?.let{heroName(it.id)}?:"队伍"}：选择指令" else ""
        if(turn!=null)battlePresentation.present(turn)
        return true
    }
    private fun attackBattle(slot:Int){
        if(battleInfoOpen||battleItemsOpen||battlePresentation.screen !in listOf(BattlePresentation.Screen.COMMAND,BattlePresentation.Screen.TARGET))return
        if(!attackSelected())return
        val current=battle?:return;val revision=current.inputRevision
        val turn=current.attack(slot){battleRandom.nextInt(256)}
        if(showSubmittedBattleCommand(current,revision,turn)&&turn!=null)audio.effect("attack")
    }
    private fun escapeBattle(){
        if(battleInfoOpen||battleItemsOpen||battlePresentation.screen!=BattlePresentation.Screen.COMMAND)return
        val current=battle?:return;val revision=current.inputRevision
        val turn=current.escape{battleRandom.nextInt(256)}
        showSubmittedBattleCommand(current,revision,turn)
    }
    private fun finishBattlePresentation(){
        val current=battle?:return
        battleTouch.clear()
        selectedBattleSlot=current.enemies.firstOrNull{it.hp>0}?.slot?:0
        if(current.phase==BattlePhase.TARGET)return
        if(battleCommitted)return
        when(current.phase){
            BattlePhase.VICTORY->{
                battleResultBefore=current.hero
                val partyBefore=current.party.associateBy{it.id}
                if(storyBattle?.charactersOnVictory(current.charactersAfterBattle())==null&&storyBattle!=null){
                    showNotice("剧情角色状态无法提交，奖励尚未结算");return
                }
                val reward=current.settle(money)?:return
                val gainedMoney=reward.money-money
                battleResultParty=reward.characters.filter{it.id in partyBefore}.map{it to reward.experienceByCharacter.getValue(it.id)}
                characters=storyBattle?.charactersOnVictory(reward.characters)?:reward.characters;money=reward.money
                val loot=BattleAcquisition.apply(current.inventoryAfterBattle(inventory),current.enemies.mapNotNull{it.definition.loot},
                    content.itemDefinitions.mapValues{it.value.category}){battleRandom.nextInt(256)}
                inventory=loot.inventory
                storyBattle?.let{story->
                    // Rewards and durable pending dialogue commit once. Some original events
                    // set their map flag only after the victory text, never on first approach.
                    flags=story.rewardFlags(flags)
                }
                battleMessage="胜利！经验 +${reward.experience}  银两 +$gainedMoney"+
                    (if(reward.levels.isEmpty())"" else "  等级 ${reward.levels.last()}")+
                    loot.acquired.joinToString(""){"  获得 ${content.itemNames[it]?:it}"}+
                    if(loot.skipped.isEmpty())"" else "  物品数量/格数已满，掉落未取得"
                battleResultLines=listOf("胜利！", "总经验 ${reward.experience} · 银两 +$gainedMoney")+
                    reward.characters.filter{it.id in partyBefore}.flatMap{player->val before=partyBefore.getValue(player.id);listOf(
                        "${heroName(player.id)} EXP +${reward.experienceByCharacter.getValue(player.id)} · 累计 ${before.experience} → ${player.experience}",
                        if(before.level==player.level)"等级 ${player.level}" else "升级 ${before.level} → ${player.level}",growthProgress(player).summary)}+
                    loot.acquired.map{"获得 ${content.itemNames[it]?:it}"}+loot.skipped.map{"${content.itemNames[it]?:it}：数量/格数已满，未取得"}
                diagnoseExperience()
                audio.scene(world.mapId,"victory")
                Diagnostics.record("reward_settlement",details=JSONObject().put("battleID",battleID).put("settlementID",battleID+":reward")
                    .put("experience",reward.experience).put("money",current.enemies.sumOf{it.definition.moneyReward}))
            }
            BattlePhase.ESCAPED->{
                inventory=current.inventoryAfterBattle(inventory)
                characters=current.charactersAfterBattle()
                battleMessage="逃跑成功" // No rewards, no invented grace period.
            }
            BattlePhase.DEFEAT->{
                if(content.battle?.defeatResetEnabled!=true){battleMessage="原版战败处理尚未开放";return}
                // Target ROM: 0:812D clears runtime RAM, 0:B795 initializes map114 and the opening party.
                resetOpeningAfterDefeat()
                battleMessage="不幸！全員陣亡了！"
            }
            BattlePhase.TARGET->return
        }
        battleCommitted=true
        Diagnostics.record("battle_end",details=JSONObject().put("battleID",battleID).put("groupId",current.group.id)
            .put("reason",current.phase.name.lowercase()))
        battleSavePending=!persistStateResult()
        if(battleResultLines.isEmpty())battleResultLines=listOf(battleMessage)
        if(battleSavePending){battleMessage+="  保存失败，请重试；尚不能继续";battleResultLines=battleResultLines+"保存失败，请点击重试"}
    }
    private fun ordinaryResultDuration()=maxOf(2200L,battleResultLines.sumOf{it.length}.toLong()*45L)
    private fun closeBattle(){
        val hadSelection=attackSelected();battleAttackSelection=null
        if(hadSelection&&battlePresentation.screen==BattlePresentation.Screen.COMMAND){
            battleNotice="请选择指令；点击敌人查看信息";battlePresentation.invalidateInput();clearBattleGesture();return
        }
        if(battleItemsOpen){battleItemsOpen=false;selectedBattleItem=null;battlePresentation.invalidateInput();clearBattleGesture();return}
        if(battleInfoOpen){battleInfoOpen=false;battlePresentation.resetResultTimer();battlePresentation.invalidateInput();clearBattleGesture();return}
        if(battleResultDetails&&battlePresentation.screen==BattlePresentation.Screen.RESULT){
            battleResultDetails=false;battleResultScroll=0f;battlePresentation.resetResultTimer();battlePresentation.invalidateInput();return
        }
        if(battlePresentation.screen==BattlePresentation.Screen.TARGET){battlePresentation.back();battleTouch.clear();return}
        if(battlePresentation.screen!=BattlePresentation.Screen.RESULT||!battleCommitted)return
        if(battleSavePending){battleSavePending=!persistStateResult();if(battleSavePending)return}
        val story=storyBattle;storyBattle=null
        battle=null;battleResultDetails=false;battleResultParty=emptyList();battleInfoHeroId=null;clearBattleGesture();layer=Layer.MAP;input.clear();clock.reset()
        audio.scene(world.mapId)
        if(story!=null&&flags[story.flagId+".dialogue.pending"]==true){
            val npc=content.npcs.first{it.id==story.npcId}
            openDialogue(content.dialogues.getValue(story.pendingDialogue(flags)),npc)
        }
    }
    private fun confirmBattle(){
        if(battleInfoOpen||battleItemsOpen)return
        when(battlePresentation.screen){
            BattlePresentation.Screen.COMMAND->when(battlePresentation.command){0->{
                val current=battle?:return;battlePresentation.targets()
                battleAttackSelection=BattleTouchCommand(battleID,battlePresentation.revision,"attack-mode")
                    .also{it.actorId=current.inputHero?.id;it.inputRevision=current.inputRevision}
            };4->escapeBattle()}
            BattlePresentation.Screen.TARGET->attackBattle(selectedBattleSlot)
            BattlePresentation.Screen.RESULT->{battleResultDetails=false;closeBattle()}
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
    private fun battleInfoLayout():TouchModalLayout {
        val dp=resources.displayMetrics.density
        val l=touchModalLayout(ui.safe,dp,resources.configuration.fontScale,clinic?.treatments?.size?:0,0,false)
            if(clinic!=null)return l
        val row=max(56f,20*resources.configuration.fontScale+16)*dp
        // Landscape uses the existing two-column geometry, including short safe windows.
        val listW=(l.frame.w-24*dp)*.38f
        val list=Box(l.frame.x+8*dp,l.list.y,listW,l.list.h)
        val detail=Box(list.x+list.w+8*dp,list.y,l.frame.x+l.frame.w-8*dp-(list.x+list.w+8*dp),list.h)
        return l.copy(list=list,detail=detail,rowHeight=row)
    }
    private fun battleScene()=battleSceneLayout(ui.safe,resources.displayMetrics.density,
        resources.configuration.fontScale,battle?.enemies?.size?:1,battle?.party?.size?:1)
    private fun battleLayout()=battleScene()?.touch?:battleTouchLayout(ui.safe,resources.displayMetrics.density,
        resources.configuration.fontScale,battle?.enemies?.size?:1,battle?.party?.size?:1)
    fun battlePartyCardBounds(id:String):Box {
        val current=battle?:return Box(0f,0f,0f,0f)
        val i=current.party.indexOfFirst{it.id==id}
        return battleScene()?.partyCards?.getOrNull(i)?:Box(0f,0f,0f,0f)
    }
    private fun attackSelected():Boolean {
        val selection=battleAttackSelection?:return false;val current=battle?:return false
        return selection.battleId==battleID&&selection.revision==battlePresentation.revision&&
            selection.actorId!=null&&selection.actorId==current.inputHero?.id&&selection.inputRevision==current.inputRevision
    }
    private fun clearAttackChoice(){battleAttackSelection=null
        if(battlePresentation.screen==BattlePresentation.Screen.TARGET)battlePresentation.back()}
    private fun resetBattleUiSelection(){battleAttackSelection=null;battleInfoHeroId=null
        battleResultDetails=false;battleResultParty=emptyList()}
    private fun battleBox():Box {
        val a=battleLayout().arena;val scale=min(a.w/256f,a.h/132f)
        return Box(a.x+(a.w-256*scale)/2,a.y,256*scale,240*scale)
    }
    private fun battleRegion(x:Float,y:Float,w:Float,h:Float):Box {
        val b=battleBox();val scale=b.w/256f
        return Box(b.x+x*scale,b.y+y*scale,w*scale,h*scale)
    }
    private fun battleEnemyBox(enemy:BattleEnemy):Box {
        val graphic=content.enemyGraphics[enemy.definition.id]
        battleScene()?.let{scene->
            val current=battle!!;val cell=scene.touch.enemies[current.enemies.indexOfFirst{it.slot==enemy.slot}]
            val dp=resources.displayMetrics.density
            val font=resources.configuration.fontScale
            val target=battleEnemySceneLayout(cell,dp,font,scene.compact,current.enemies.size==1).graphic
            val w=(graphic?.width?:32).toFloat();val h=(graphic?.height?:40).toFloat()
            val fit=min(target.w/w,target.h/h)
            // The compact large-font arena uses its remaining sprite height;
            // nearest-neighbor drawing keeps the original bitmap unchanged.
            val scale=if(scene.compact&&font>=2f&&fit<2f)fit else if(fit>=1f)floor(fit)else fit
            return Box(target.x+(target.w-w*scale)/2,target.y+(target.h-h*scale)/2,w*scale,h*scale)
        }
        // A captured single-enemy origin is not a shared origin for every instance in a group.
        // Keep the existing slot layout for ordinary groups; the sole Boss retains its origin.
        val origin=content.enemyOrigins[enemy.definition.id]?.takeIf{battle?.enemies?.size==1}
        return battleRegion(origin?.first?.toFloat()?: (16f+32*enemy.slot),origin?.second?.toFloat()?:72f,
            (graphic?.width?:32).toFloat(),(graphic?.height?:40).toFloat())
    }
    private fun battleHit(x:Float,y:Float):BattleTouchCommand? {
        val current=battle?:return null;val screen=battlePresentation.screen;val l=battleLayout()
        fun cmd(kind:String,slot:Int?=null,item:String?=null,target:String?=null)=BattleTouchCommand(battleID,battlePresentation.revision,kind,slot,item,target)
            .also{it.actorId=current.inputHero?.id;it.inputRevision=current.inputRevision}
        if(battleItemsOpen){
            val items=battleItemLayout()
            if(items.close.contains(x,y))return cmd("close-items")
            battleMedicines().firstOrNull{battleItemBounds(it).contains(x,y)&&battleItemBounds(it).h>=48*resources.displayMetrics.density}?.let{return cmd("select-medicine",item=it)}
            val binding=selectedBattleItem?.let{content.itemDefinitions[it]?.battleBindingUse}!=null
            if(!binding)current.party.firstOrNull{battleItemTargetBounds(it.id).contains(x,y)}?.let{return cmd("medicine-target",target=it.id)}
            if(items.primary.contains(x,y))return selectedBattleItem?.let{
                cmd(if(binding)"use-binding"else"use-medicine",item=it,target=if(binding)current.inputHero?.id else battleItemTarget()?.id)}
            return when{items.detail.contains(x,y)->cmd("medicine-scroll");items.list.contains(x,y)->cmd("medicine-list-scroll");else->null}
        }
        if(battleInfoOpen){
            val info=battleInfoLayout()
            if(info.close.contains(x,y))return cmd("close-info")
            if(battleInfoHeroId!=null){
                current.party.withIndex().firstOrNull{(i,_)->val row=info.visibleRow(i,battleInfoListScroll)
                    row.contains(x,y)&&row.h>=48*resources.displayMetrics.density}?.let{return cmd("party-info",target=it.value.id)}
                return when{info.detail.contains(x,y)->cmd("info-scroll");info.list.contains(x,y)->cmd("info-list-scroll");else->null}
            }
            current.enemies.firstOrNull{battleTargetBounds(it.slot).contains(x,y)&&battleTargetBounds(it.slot).h>=48*resources.displayMetrics.density}?.let{return cmd("info-target",it.slot)}
            return when{info.detail.contains(x,y)->cmd("info-scroll",selectedBattleSlot)
                info.list.contains(x,y)->cmd("info-list-scroll");else->null}
        }
        if(screen==BattlePresentation.Screen.RESULT){
            if(!battleCommitted)return null
            battleScene()?.resultFooter?.let{footer->if(footer.contains(x,y))return cmd(if(x<footer.x+footer.w/2)"result-details"else"result")}
            return if(l.result.contains(x,y))cmd(if(battleResultDetails)"result-scroll"else"result") else null
        }
        current.party.firstOrNull{battlePartyCardBounds(it.id).contains(x,y)}?.let{return cmd("party-info",target=it.id)}
        if(screen !in listOf(BattlePresentation.Screen.COMMAND,BattlePresentation.Screen.TARGET))return null
        l.commands.indexOfFirst{it.contains(x,y)}.takeIf{it>=0}?.let{index->return cmd(listOf("attack-mode","magic","items","escape","info")[index])}
        current.enemies.firstOrNull{battleTargetBounds(it.slot).contains(x,y)}?.let{
            return cmd(if(attackSelected()&&it.hp>0)"attack"else"inspect-target",it.slot)}
        // Overlapping original graphics have a separate non-overlapping >=48dp instance row above commands.
        val hit=current.enemies.filter{it.hp>0&&battleEnemyBox(it).contains(x,y)}
        return if(hit.size==1&&battleEnemyBox(hit.single()).w>=48*resources.displayMetrics.density&&
            battleEnemyBox(hit.single()).h>=48*resources.displayMetrics.density)
            cmd(if(attackSelected())"attack"else"inspect-target",hit.single().slot) else null
    }
    private fun submitBattle(cmd:BattleTouchCommand){
        val current=battle?:return
        if(cmd.battleId!=battleID||cmd.revision!=battlePresentation.revision||cmd.actorId!=current.inputHero?.id||
            cmd.inputRevision!=current.inputRevision)return
        clearBattleGesture()
        when(cmd.kind){
            "attack"->{if(!attackSelected())return;selectedBattleSlot=cmd.slot?:return;attackBattle(selectedBattleSlot)}
            "escape"->{clearAttackChoice();escapeBattle()}
            "info","inspect-target"->{clearAttackChoice();battleInfoHeroId=null;cmd.slot?.let{selectedBattleSlot=it}
                battleInfoOpen=true;battleInfoScroll=0f;battleInfoListScroll=0f;battlePresentation.invalidateInput()}
            "party-info"->{if(current.party.none{it.id==cmd.targetId})return
                clearAttackChoice();battleInfoHeroId=cmd.targetId;battleInfoOpen=true;battleInfoScroll=0f;battleInfoListScroll=0f;battlePresentation.invalidateInput()}
            "close-info"->{battleInfoOpen=false;battlePresentation.resetResultTimer();battlePresentation.invalidateInput()}
            "info-target"->{selectedBattleSlot=cmd.slot?:return;battleInfoScroll=0f;battlePresentation.invalidateInput()}
            "info-scroll","info-list-scroll"->Unit
            "result"->{battleResultDetails=false;closeBattle()}
            "result-details"->{battleResultDetails=!battleResultDetails;battleResultScroll=0f;battlePresentation.resetResultTimer();battlePresentation.invalidateInput()}
            "result-scroll"->Unit
            "attack-mode"->{if(current.inputHero==null)return;battleNotice="攻击：请选择存活敌人"
                if(battlePresentation.screen==BattlePresentation.Screen.COMMAND){battlePresentation.selectCommand(0);battlePresentation.targets()}
                else battlePresentation.invalidateInput()
                battleAttackSelection=cmd.copy(revision=battlePresentation.revision).also{it.actorId=cmd.actorId;it.inputRevision=cmd.inputRevision}}
            "magic"->{clearAttackChoice();battleNotice="已学法术状态与执行逻辑尚未迁移";battlePresentation.invalidateInput()}
            "items"->{clearAttackChoice();battleNotice="";battleItemsOpen=true;selectedBattleItem=null;selectedBattleTarget=currentBattleTargetId();battleItemListScroll=0f;battleItemDetailScroll=0f;battlePresentation.invalidateInput()}
            "close-items"->{battleItemsOpen=false;selectedBattleItem=null;battlePresentation.invalidateInput()}
            "select-medicine"->{if(cmd.itemId !in battleMedicines())return;selectedBattleItem=cmd.itemId;battleItemDetailScroll=0f;battlePresentation.invalidateInput()}
            "medicine-target"->{val current=battle?:return;if(current.party.none{it.id==cmd.targetId})return
                selectedBattleTarget=cmd.targetId;battleItemDetailScroll=0f;battlePresentation.invalidateInput()}
            "medicine-scroll","medicine-list-scroll"->Unit
            "use-binding"->{
                val current=battle?:return;val item=content.itemDefinitions[cmd.itemId]?:return
                if(cmd.itemId!=selectedBattleItem||cmd.targetId!=current.inputHero?.id)return
                val revision=current.inputRevision
                val turn=current.useBinding(inventory[item.id]?:0,item,flags["rom.inventory.special.${item.originalId}.used"]==true){battleRandom.nextInt(256)}
                if(!showSubmittedBattleCommand(current,revision,turn)){battleNotice=battleMedicineReason(item.id);battlePresentation.invalidateInput();return}
                battleItemsOpen=false;selectedBattleItem=null
                Diagnostics.record("battle_item",details=JSONObject().put("battleID",battleID).put("itemID",item.id)
                    .put("actorID",cmd.targetId).put("pendingConsumed",0))
            }
            "use-medicine"->{
                val current=battle?:return;val item=content.itemDefinitions[cmd.itemId]?:return
                val targetId=cmd.targetId?:return
                if(cmd.itemId!=selectedBattleItem||targetId!=battleItemTarget()?.id)return
                val revision=current.inputRevision
                val turn=current.useHerb(targetId,inventory[HerbUse.ID]?:0,item){battleRandom.nextInt(256)}
                if(!showSubmittedBattleCommand(current,revision,turn)){battleNotice=battleMedicineReason(item.id);battlePresentation.invalidateInput();return}
                battleItemsOpen=false;selectedBattleItem=null
                Diagnostics.record("battle_item",details=JSONObject().put("battleID",battleID).put("itemID",item.id)
                    .put("targetID",cmd.targetId).put("pendingConsumed",current.herbsConsumed))
            }
        }
    }
    private fun battleTouchEvent(e:MotionEvent):Boolean {
        input.clear()
        if(e.actionMasked==MotionEvent.ACTION_CANCEL||!active||!focused){clearBattleGesture();return true}
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{clearBattleGesture();battleHit(e.x,e.y)?.let{battleGesture=BattleTouchGesture(e.getPointerId(0),e.x,e.y,it)}}
            MotionEvent.ACTION_POINTER_DOWN->{battleGesture=null;battleBlocked=true}
            MotionEvent.ACTION_MOVE->{val g=battleGesture
                if(g!=null){val i=e.findPointerIndex(g.pointer);if(i>=0){
                    val y=e.getY(i)
                    if(hypot(e.getX(i)-g.x,y-g.y)>ViewConfiguration.get(context).scaledTouchSlop)g.cancelled=true
                    if(g.cancelled){
                        if(battleItemsOpen){
                            val item=battleItemLayout()
                            if(item.list.contains(g.x,g.y))battleItemListScroll=(battleItemListScroll+g.lastY-y).coerceIn(0f,item.maxScroll(battleMedicines().size))
                            else if(item.detail.contains(g.x,g.y))battleItemDetailScroll=max(0f,battleItemDetailScroll+g.lastY-y)
                        }else if(battleInfoOpen){
                            val info=battleInfoLayout()
                            val count=if(battleInfoHeroId!=null)battle?.party?.size?:0 else battle?.enemies?.size?:0
                            if(info.list.contains(g.x,g.y))battleInfoListScroll=(battleInfoListScroll+g.lastY-y).coerceIn(0f,info.maxScroll(count))
                            else if(info.detail.contains(g.x,g.y))battleInfoScroll=max(0f,battleInfoScroll+g.lastY-y)
                        }
                        else if(battlePresentation.screen==BattlePresentation.Screen.RESULT)battleResultScroll=max(0f,battleResultScroll+g.lastY-y)
                    };g.lastY=y}}}
            MotionEvent.ACTION_UP->{val g=battleGesture
                if(!battleBlocked&&g!=null&&!g.cancelled&&g.pointer==e.getPointerId(e.actionIndex)&&g.command==battleHit(e.x,e.y))submitBattle(g.command)
                clearBattleGesture();performClick()}
            else->Unit
        };return true
    }
    fun visibleMapControls()=layer==Layer.MAP
    private fun nearbyNpcs():List<StoryNpc> {
        val (x,y)=world.destinationCell()
        return content.npcsForState(world.mapId,flags).filter{content.npcInteractive(it) && content.npcVisible(it,flags) &&
            (it.interactionCell?.let{p->p==(x to y)} ?: (abs(it.x-x)+abs(it.y-y)==1))}
    }
    private fun interactionTarget():StoryNpc? {
        val merchant=nearbyNpcs().firstOrNull{it.shopId!=null||it.innId!=null||it.clinicId!=null}
        if(merchant!=null)return merchant
        val (x,y)=world.destinationCell()
        val originalPoint=nearbyNpcs().firstOrNull{it.interactionDirection!=null&&it.interactionCell==(x to y)}
        if(originalPoint!=null)return originalPoint
        val actors=content.npcsForState(world.mapId,flags)
        val id=interactionTarget(x,y,world.direction,actors.filter{content.npcInteractive(it)&&content.npcVisible(it,flags)}.map{NpcCell(it.id,it.x,it.y)})?.id
        return actors.firstOrNull{it.id==id}
    }
    private fun hitNpc(x:Float,y:Float):StoryNpc? {
        if(!ui.game.contains(x,y))return null
        val camera=world.camera(ui.viewWidth,ui.viewHeight)
        val candidates=nearbyNpcs().filter{!it.hiddenInvestigation}.map{npc->
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
            val bindings=content.serviceBindings.filter{it.npcId==npc.id&&it.interiorMapId==world.mapId}
            val id=if(bindings.isEmpty())npc.shopId else bindings.firstOrNull{it.callerMapId==world.interiorContext?.callerMapId}?.shopId
            val definition=id?.let{content.shops[it]}
            if(definition==null){showNotice("当前村庄的商店数据未接入");return}
            world.face(Key.UP);openShop(definition);return
        }
        if(npc.clinicId!=null){
            if(npc !in nearbyNpcs())return
            val bindings=content.serviceBindings.filter{it.npcId==npc.id&&it.interiorMapId==world.mapId}
            val id=if(bindings.isEmpty())npc.clinicId else bindings.firstOrNull{it.callerMapId==world.interiorContext?.callerMapId}?.clinicId
            val definition=id?.let{content.clinics[it]}
            if(definition==null){showNotice("当前村庄的医疗数据未接入");return}
            world.face(Key.UP);openClinic(definition);return
        }
        if(npc.innId!=null){
            if(npc !in nearbyNpcs())return
            val bindings=content.serviceBindings.filter{it.npcId==npc.id&&it.interiorMapId==world.mapId}
            val id=if(bindings.isEmpty())npc.innId else bindings.firstOrNull{it.callerMapId==world.interiorContext?.callerMapId}?.innId
            val definition=id?.let{content.inns[it]}
            if(definition==null){showNotice("当前村庄的住宿数据未接入");return}
            world.face(Key.UP);openInn(definition);return
        }
        val direction=if(npc.interactionDirection!=null&&npc.interactionCell==(x to y))npc.interactionDirection!!
            else facingToward(x,y,NpcCell(npc.id,npc.x,npc.y))?:return
        world.face(direction)
        content.sceneStories[npc.id]?.takeIf{it.manualActivation}?.let{scene->
            if(npc !in nearbyNpcs())return
            if(localSaveProtected){showNotice("原存档受保护，不能提交剧情");return}
            val before=currentSnapshot()
            if(flags[scene.pendingFlag]==true){
                scene.pendingDialogue(flags)?.let{content.dialogues[it]}?.let{openDialogue(it,npc)}
                return
            }
            if(scene.triggersAt(before)){
                commitStoryFollowup(before,StoryFollowup.begin(before,scene),npc);return
            }
        }
        npc.originalTalk?.let{rule->
            if(localSaveProtected){showNotice("原存档受保护，不能提交剧情");return}
            val before=currentSnapshot()
            commitStoryFollowup(before,OriginalNpcTalk.begin(before,rule,content.itemDefinitions[rule.itemId]),npc);return
        }
        val story=content.battle?.storyBattles?.get(npc.id)?.takeIf{it.activeIn(flags)||it.alreadyWon(flags)}
        npc.moneyTreasure?.let{treasure->
            if(localSaveProtected){showNotice("原存档受保护，不能领取钱箱");return}
            val before=currentSnapshot();val result=WorldItems.openMoneyTreasure(before,treasure)
            if(!result.applied){showNotice(result.error?:"没有取得银两");return}
            money=result.snapshot.money;flags=result.snapshot.flags
            if(persistStateResult())showNotice("获得${money-before.money}两")
            else{money=before.money;flags=before.flags;showNotice("保存失败，未取得银两")}
            clearUxGesture();uxRevision++;return
        }
        // A guarded chest enters its original story battle first. The acquisition
        // transaction only runs after that exact victory flag is committed.
        npc.treasure?.takeIf{story==null||flags[story.flagId]==true}?.let{treasure->
            val item=content.itemDefinitions[treasure.itemId]?:return
            val before=currentSnapshot();val result=WorldItems.openTreasure(before,treasure,item)
            if(!result.applied){showNotice(result.error?:"没有取得物品");return}
            inventory=result.inventory;flags=result.flags
            if(persistStateResult())showNotice("获得${item.name}")
            else {inventory=before.inventory;flags=before.flags;showNotice("保存失败，未取得物品")}
            clearUxGesture();uxRevision++;return
        }
        if(story!=null&&flags[story.pendingFlag]==true){
            openDialogue(content.dialogues.getValue(story.pendingDialogue(flags)),npc);return
        }
        val id=if(flags[story?.flagId?:npc.id]==true)npc.repeatDialogue?:npc.firstDialogue else npc.firstDialogue
        content.dialogues[id]?.let{openDialogue(it,npc)}
    }
    fun mapControlEnabled(key:Key)=layer==Layer.MAP&&(key==Key.MENU || (key==Key.A && interactionTarget()!=null))
    private fun applySceneMechanism():Boolean {
        if(layer!=Layer.MAP)return false
        val mechanism=content.mechanisms.firstOrNull{
            it.triggered(world.mapId,world.x/16,world.y/16,world.remaining==0,flags)}?:return false
        val before=flags
        flags=flags+(mechanism.sessionFlag to true)
        if(!persistStateResult()){
            flags=before;showNotice("保存失败，机关状态已保留，请重新触发");return true
        }
        input.clear();npcTouch.clear();hudTouch.clear();clearUxGesture();clock.reset()
        showNotice("机关已启动")
        return true
    }
    private fun openEntryStoryIfNeeded():Boolean {
        if(layer!=Layer.MAP||world.remaining!=0)return false
        if(openSceneStoryIfNeeded())return true
        val story=content.battle?.storyBattles?.values?.firstOrNull{
            it.triggersAt(world.mapId,world.x/16,world.y/16,flags)}?:return false
        val npc=content.npcs.first{it.id==story.npcId}
        story.intro?.let{intro->
            if(localSaveProtected){showNotice("原存档受保护，不能提交剧情");return true}
            if(flags[intro.flagId]==true){startStoryBattle(story);return true}
            val before=currentSnapshot();commitStoryFollowup(before,StoryFollowup.begin(before,intro),npc);return true
        }
        story.approach?.let{
            if(localSaveProtected){showNotice("原存档受保护，不能提交剧情");return true}
            if(flags[story.approachFlag]!=true){
                val before=currentSnapshot();val result=StoryFollowup.approachBattle(before,story)
                if(!result.applied||!result.snapshot.validate(content)||!applySnapshotState(result.snapshot)){
                    showNotice(result.error?:"剧情前行状态不可恢复");return true
                }
                if(!persistStateResult()){
                    if(!applySnapshotState(before))localSaveProtected=true
                    showNotice("保存失败，剧情前行未提交");return true
                }
                if(OriginalStatus.allDisabled(characters)){
                    flags=flags+(FIELD_FAILURE_FLAG to true);showFieldFailure();persistState();return true
                }
            }
            startStoryBattle(story);return true
        }
        openDialogue(content.dialogues.getValue(npc.firstDialogue),npc)
        return true
    }
    fun startOpeningIfNeeded(){
        if(flags[OriginalJiangJoin.PANXI_PENDING]==true&&OriginalJiangJoin.validPanxiPending(currentSnapshot())){
            content.npcs.firstOrNull{it.id=="rom.npc.7.4"&&it.originalTalk?.actionId==61}?.let{npc->
                OriginalJiangJoin.panxiDialogue(currentSnapshot())?.let{openDialogue(content.dialogues.getValue(it),npc);return}
            }
        }
        if(flags[OriginalNpcTalk.ROOM116_PENDING_FLAG]==true&&OriginalNpcTalk.validRoom116Pending(currentSnapshot())){
            content.npcs.firstOrNull{it.id=="rom.npc.116.0"&&it.originalTalk?.actionId==41}?.let{npc->
                openDialogue(content.dialogues.getValue(OriginalNpcTalk.room116PendingDialogue(flags)),npc);return
            }
        }

        if(flags[OriginalNpcTalk.HUANG_PENDING_FLAG]==true&&OriginalNpcTalk.validHuangPending(currentSnapshot())){
            content.npcs.firstOrNull{it.id=="rom.npc.117.0"&&it.originalTalk?.actionId==43}?.let{npc->
                openDialogue(content.dialogues.getValue(npc.firstDialogue),npc);return
            }
        }

        content.jiangJoin?.let{rule->
            if(flags[rule.pendingFlag]==true&&rule.validPending(currentSnapshot())){
                val stage=rule.continuation.stage(rule.id,flags)?:return@let
                openDialogue(content.dialogues.getValue(rule.continuation.dialogueIds[stage]),
                    if(stage==0)content.npcs.single{it.id==rule.kingNpcId}else null);return
            }
        }
        content.yangJoin()?.let{rule->
            if(flags[rule.pendingFlag]==true){
                val stage=rule.continuation.stage(rule.id,flags)
                if(stage!=null&&rule.validPending(currentSnapshot())){
                    openDialogue(content.dialogues.getValue(rule.continuation.dialogueIds[stage]),content.npcs.single{it.id==rule.npcId});return
                }
            }
        }

        content.sceneItemUses().firstOrNull{flags[it.pendingFlag]==true&&it.validPending(currentSnapshot())}?.let{rule->
            val stage=rule.continuation.stage(rule.id,flags)?:return@let
            openDialogue(content.dialogues.getValue(rule.continuation.dialogueIds[stage]),
                if(rule.locationTarget)null else content.npcs.single{it.id==rule.npcId});return
        }
        val scenePending=content.sceneStories.values.firstOrNull{flags[it.pendingFlag]==true}
        if(scenePending!=null){
            if(OriginalStatus.allDisabled(characters)){
                flags=flags+(FIELD_FAILURE_FLAG to true);showFieldFailure();persistState();return
            }
            scenePending.pendingDialogue(flags)?.let{id->
                openDialogue(content.dialogues.getValue(id),content.npcs.first{it.id==scenePending.npcId});return
            }
        }
        val introPending=content.battle?.storyBattles?.values?.firstOrNull{it.intro?.let{i->flags[i.pendingFlag]}==true}
        if(introPending!=null){
            val intro=introPending.intro!!
            if(OriginalStatus.allDisabled(characters)){flags=flags+(FIELD_FAILURE_FLAG to true);showFieldFailure();persistState();return}
            intro.pendingDialogue(flags)?.let{id->openDialogue(content.dialogues.getValue(id),content.npcs.first{it.id==introPending.npcId});return}
        }
        val pending=content.battle?.storyBattles?.values?.firstOrNull{flags[it.flagId+".dialogue.pending"]==true}
        if(pending!=null){
            val npc=content.npcs.first{it.id==pending.npcId}
            openDialogue(content.dialogues.getValue(pending.pendingDialogue(flags)),npc);return
        }
        if(openEntryStoryIfNeeded())return
        if(world.mapId==114 && world.x==content.scene.spawnX*16+8 && world.y==content.scene.spawnY*16+8 &&
            flags["opening.intro.seen"]!=true)content.intro?.let{openDialogue(it,null)}
    }
    private fun openSceneStoryIfNeeded():Boolean {
        if(layer!=Layer.MAP||world.remaining!=0)return false
        val before=currentSnapshot()
        val story=content.sceneStories.values.firstOrNull{it.automaticallyTriggersAt(before)}?:return false
        if(localSaveProtected){showNotice("原存档受保护，不能提交剧情");return true}
        commitStoryFollowup(before,StoryFollowup.begin(before,story),content.npcs.first{it.id==story.npcId})
        return true
    }
    private fun commitStoryFollowup(before:SaveSnapshot,result:StoryFollowup.Result,npc:StoryNpc?) {
        if(!result.applied){showNotice(result.error?:"剧情状态已变化");return}
        if(!result.snapshot.validate(content)||!applySnapshotState(result.snapshot)){
            showNotice("剧情落点或队伍不可恢复，原状态已保留");return
        }
        if(!persistStateResult()){
            if(!applySnapshotState(before))localSaveProtected=true
            showNotice("保存失败，请重试继续对话");return
        }
        if(npc!=null&&(npc.id in content.sceneStories||content.battle?.storyBattles?.get(npc.id)?.intro!=null)&&OriginalStatus.allDisabled(characters)){
            flags=flags+(FIELD_FAILURE_FLAG to true);showFieldFailure();persistState();return
        }
        if(result.nextDialogue!=null)openDialogue(content.dialogues.getValue(result.nextDialogue),npc)
        else{dismissDialogue();audio.scene(world.mapId)}
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
        if(flags[OriginalJiangJoin.PANXI_PENDING]==true){
            if(localSaveProtected){showNotice("原存档受保护，不能提交剧情");return}
            val before=currentSnapshot()
            commitStoryFollowup(before,OriginalJiangJoin.advancePanxi(before,dialogueText?.id?:""),npc);return
        }
        content.sceneItemUses().firstOrNull{(it.npcId==npc?.id||it.locationTarget&&npc==null)&&flags[it.pendingFlag]==true}?.let{rule->
            if(localSaveProtected){showNotice("原存档受保护，不能提交剧情");return}
            val before=currentSnapshot()
            commitStoryFollowup(before,OriginalSceneItems.advance(before,rule,dialogueText?.id?:""),npc);return
        }
        content.jiangJoin?.takeIf{flags[it.pendingFlag]==true}?.let{rule->
            if(localSaveProtected){showNotice("原存档受保护，不能提交剧情");return}
            val before=currentSnapshot();val result=OriginalJiangJoin.advance(before,rule,dialogueText?.id?:"",
                content.joinCharacters["jiangziya"])
            commitStoryFollowup(before,result,if(result.snapshot.mapId==121)npc else null);return
        }
        content.yangJoin()?.let{rule->
            if(npc?.id==rule.npcId&&flags[rule.pendingFlag]==true){
                if(localSaveProtected){showNotice("原存档受保护，不能提交剧情");return}
                val before=currentSnapshot();commitStoryFollowup(before,StoryFollowup.advance(before,rule,dialogueText?.id?:""),npc);return
            }
        }
        val sceneStory=npc?.let{content.sceneStories[it.id]}
        if(sceneStory!=null&&npc!=null&&flags[sceneStory.pendingFlag]==true){
            if(localSaveProtected){showNotice("原存档受保护，不能提交剧情");return}
            val before=currentSnapshot()
            commitStoryFollowup(before,StoryFollowup.advance(before,sceneStory,dialogueText?.id?:""),npc)
            return
        }
        val story=npc?.let{content.battle?.storyBattles?.get(it.id)}?.takeIf{it.activeIn(flags)||it.alreadyWon(flags)}
        if(story!=null){
            story.intro?.takeIf{flags[it.pendingFlag]==true}?.let{intro->
                if(localSaveProtected){showNotice("原存档受保护，不能提交剧情");return}
                val before=currentSnapshot();commitStoryFollowup(before,StoryFollowup.advance(before,intro,dialogueText?.id?:""),npc)
                if(layer==Layer.MAP&&flags[intro.flagId]==true&&!story.alreadyWon(flags))startStoryBattle(story)
                return
            }
            if(story.alreadyWon(flags)){
                if(story.continuation!=null&&flags[story.pendingFlag]==true){
                    if(localSaveProtected){showNotice("原存档受保护，不能提交剧情");return}
                    val before=currentSnapshot()
                    commitStoryFollowup(before,StoryFollowup.advance(before,story,dialogueText?.id?:"",content.joinCharacters),npc)
                    return
                }
                val before=flags
                if(flags[story.pendingFlag]==true)encounter?.restore(0)
                flags=story.completeDialogue(flags)
                if(!persistStateResult()){flags=before;showNotice("保存失败，请重试继续对话");return}
                dismissDialogue();return
            }
            dismissDialogue()
            if(!persistStateResult()){
                showNotice("保存失败，剧情战斗未开始");return
            }
            startStoryBattle(story);return
        }
        npc?.originalTalk?.let{rule->
            if(rule.actionId==41&&flags[OriginalNpcTalk.ROOM116_PENDING_FLAG]==true){
                if(localSaveProtected){showNotice("原存档受保护，不能提交剧情");return}
                val before=currentSnapshot()
                commitStoryFollowup(before,OriginalNpcTalk.advanceRoom116(before,rule,dialogueText?.id?:""),npc);return
            }
            if(rule.actionId==43){
                if(localSaveProtected){showNotice("原存档受保护，不能提交剧情");return}
                val before=currentSnapshot()
                commitStoryFollowup(before,OriginalNpcTalk.finishHuang(before,rule,dialogueText?.id?:""),npc);return
            }
            dismissDialogue();return
        }
        if(npc?.readOnlyDialogue==true){dismissDialogue();persistState();return}
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
    private fun showNotice(message:String){mapNotice=message;noticeUntil=SystemClock.uptimeMillis()+3500}
    private fun startStoryBattle(story:StoryBattleDefinition){
        val rules=content.battle?:return
        if(!story.activeIn(flags)||story.alreadyWon(flags)||world.remaining!=0||characters.none{it.hp>0})return
        storyBattle=story;battleSavePending=false
        battle=createPartyBattle(story.group,rules)
        selectedBattleSlot=story.group.members.first().slot;battleMessage="${rules.enemies.getValue(story.group.members.first().enemyId).name} · 剧情战斗"
        battleCommitted=false;battlePresentation=BattlePresentation();battleInfoOpen=false;battleItemsOpen=false;selectedBattleItem=null;battleNotice="";battleResultBefore=null;battleResultLines=emptyList();battleResultScroll=0f;resetBattleUiSelection();clearBattleGesture();layer=Layer.BATTLE
        battleID=java.util.UUID.randomUUID().toString()
        Diagnostics.record("battle_start",details=JSONObject().put("battleID",battleID).put("groupId",story.group.id)
            .put("mapId",world.mapId).put("storyBattle",story.id))
        audio.scene(world.mapId,"battle");input.clear();battleTouch.clear();clearUxGesture();clock.reset()
    }
    private fun canDismissDialogue():Boolean {
        // This original invitation owns a durable scripted continuation. Its
        // pending save requires the checked map/pose; do not expose movement
        // while that continuation still owns input.
        if(content.jiangJoin?.let{flags[it.pendingFlag]==true||flags[OriginalJiangJoin.PANXI_PENDING]==true}==true)return false
        if(dialogueNpc?.originalTalk?.actionId==41&&flags[OriginalNpcTalk.ROOM116_PENDING_FLAG]==true)return false
        if(dialogueNpc?.originalTalk?.actionId==43&&flags[OriginalNpcTalk.HUANG_PENDING_FLAG]==true)return false
        dialogueNpc?.let{content.sceneStories[it.id]}?.let{if(flags[it.pendingFlag]==true)return false}
        val story=dialogueNpc?.let{content.battle?.storyBattles?.get(it.id)}?:return true
        return story.entryTrigger==null&&!(story.continuation!=null&&flags[story.pendingFlag]==true)
    }
    private fun dismissDialogue(){layer=Layer.MAP;dialogueNpc=null;dialogueText=null;dialogueTouch.clear();input.clear();clock.reset()}
    private fun openMenu(){if(layer!=Layer.MAP || finishPendingStep())return;input.clear();menuTouch.clear();hudTouch.clear();npcTouch.clear();clock.reset();menuSelection=0;layer=Layer.MENU}
    private fun closeMenu(){if(layer!=Layer.MENU)return;layer=Layer.MAP;input.clear();menuTouch.clear();panelTouch.clear();clearUxGesture();clock.reset()}
    private fun returnToMenu(){layer=Layer.MENU;input.clear();menuTouch.clear();panelTouch.clear();clearUxGesture();clock.reset()}
    fun handleBack():Boolean {if(OriginalFerry.pending(flags,content.ferries.values)!=null)return true;when(layer){Layer.FIELD_FAILURE->Unit;Layer.MAP->openMenu();Layer.MENU->closeMenu();Layer.SETTINGS->modalDialog?.dismiss();Layer.DIALOGUE->{if(canDismissDialogue())dismissDialogue()};Layer.CHARACTER,Layer.INVENTORY->closePanel();Layer.BATTLE->closeBattle();Layer.SHOP->shopBack();Layer.INN->closeInn()};return true}
    private fun confirmMenu(){
        when(menuSelection){0->closeMenu();1->openPanel(Layer.CHARACTER);2->openPanel(Layer.INVENTORY);3->settings()}
    }
    private fun openPanel(which:Layer){
        diagnoseExperience()
        if(layer !in listOf(Layer.MAP,Layer.MENU) || which !in listOf(Layer.CHARACTER,Layer.INVENTORY))return
        panelReturnLayer=layer;if(finishPendingStep())return
        input.clear();hudTouch.clear();npcTouch.clear();menuTouch.clear();panelTouch.clear();clearUxGesture();clock.reset()
        characterPage=0;selectedItemId=null;candidateSlot=null;modalListScroll=0f;modalDetailScroll=0f;modalDetailsOpen=false;uxRevision++
        clearFieldMagic()
        panelTab=if(which==Layer.INVENTORY)CharacterTab.ITEMS else CharacterTab.ATTRIBUTES
        layer=which
    }
    private fun closePanel(){
        if(layer !in listOf(Layer.CHARACTER,Layer.INVENTORY))return
        layer=panelReturnLayer;input.clear();panelTouch.clear();clearUxGesture();hudTouch.clear();clock.reset()
    }
    private fun hudBox():Box {
        val dp=resources.displayMetrics.density;val font=resources.configuration.fontScale
        val w=min(ui.safe.w*.40f,max(230f,145f*font)*dp)
        val h=max(92f,44f*font+30f)*dp
        return Box(ui.safe.x+8*dp,ui.safe.y+8*dp,w,h)
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
    private fun directPanel()=true // All existing character tabs reuse the same scoped touch geometry.
    private fun modalLayout():TouchModalLayout {
        if(layer==Layer.INN){
            val dp=resources.displayMetrics.density
            val l=touchModalLayout(ui.safe,dp,resources.configuration.fontScale,clinic?.treatments?.size?:0,0,false)
            if(clinic!=null)return l
            val gap=max(48f,22*resources.configuration.fontScale+16)*dp
            return l.copy(list=Box(l.list.x,l.list.y-gap,l.list.w,l.list.h+gap),
                detail=Box(l.detail.x,l.detail.y-gap,l.detail.w,l.detail.h+gap))
        }
        val l=touchModalLayout(ui.safe,resources.displayMetrics.density,resources.configuration.fontScale,
            if(layer==Layer.SHOP)2 else 4,if(layer==Layer.SHOP)0 else characters.size,layer!=Layer.SHOP&&panelTab==CharacterTab.EQUIPMENT)
        if(layer!=Layer.SHOP&&(panelTab==CharacterTab.ATTRIBUTES||panelTab==CharacterTab.MAGIC&&fieldSpells().isEmpty())){
            val dp=resources.displayMetrics.density
            return l.copy(list=Box(0f,0f,0f,0f),detail=Box(l.frame.x+8*dp,l.list.y,l.frame.w-16*dp,l.list.h),
                primary=Box(0f,0f,0f,0f),secondary=Box(0f,0f,0f,0f))}
        return l
    }
    private fun clearUxGesture(){uxGesture=null;uxBlocked=false}
    private fun clearFieldMagic(){selectedFieldSpell=null;fieldMagicCaster=null;fieldMagicChoosingTarget=false}
    private fun resetModalSelection(){clearUxGesture();clearFieldMagic();uxRevision++;modalListScroll=0f;modalDetailScroll=0f;modalDetailsOpen=false}
    private fun fieldMagicIndices()=content.characterDefinitions.mapValues{it.value.originalActorIndex}
    private fun fieldMagicHero()=characters.firstOrNull{it.id==fieldMagicCaster}?:characters[characterPage]
    private fun fieldSpells():List<String> = if(content.fieldMagicEnabled&&
        OriginalFieldMagic.learned(fieldMagicHero(),content.characterDefinitions[fieldMagicHero().id]?.originalActorIndex?:-1))
        listOf(OriginalFieldMagic.SPELL_ID) else emptyList()
    private fun fieldMagicReason()=if(!content.fieldMagicEnabled)"法术规则尚未开放" else
        OriginalFieldMagic.unavailable(characters,fieldMagicIndices(),fieldMagicHero().id,characters[characterPage].id,
            selectedFieldSpell?:"",panelReturnLayer in listOf(Layer.MAP,Layer.MENU))
    fun panelSpellBounds(id:String)=modalLayout().visibleRow(fieldSpells().indexOf(id),modalListScroll)
    private fun panelItems()=inventoryEntries().filter{entry->candidateSlot==null ||
        content.equipmentDefinitions[entry.key]?.let{it.slot==candidateSlot&&OpeningEquipment.replace(characters[characterPage],inventory,it,content.equipmentDefinitions.values)!=null}==true}
    fun panelTabBounds(index:Int)=tabBox(index)
    fun panelPrimaryBounds()=panelEquipmentActionBox()
    fun panelSecondaryBounds()=modalLayout().secondary
    fun panelListBounds()=modalLayout().list
    fun panelItemBounds(id:String)=modalLayout().visibleRow(panelItems().indexOfFirst{it.key==id},modalListScroll)
    fun panelSlotBounds(slot:String)=modalLayout().visibleRow(listOf("rightHand","leftHand","body","feet").indexOf(slot),modalListScroll)
    fun panelCharacterBounds(id:String)=modalLayout().party.getOrNull(characters.indexOfFirst{it.id==id})?:Box(0f,0f,0f,0f)
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
        item.nightLightUse?.let{
            val reason=WorldItems.nightLightUnavailable(currentSnapshot(),item,panelReturnLayer in listOf(Layer.MAP,Layer.MENU))
            return ItemAction("night-light","使用${item.name}",reason==null,reason?:"","field-map-74")
        }
        item.fieldProtectionUse?.let{
            val reason=WorldItems.fieldProtectionUnavailable(currentSnapshot(),item,panelReturnLayer in listOf(Layer.MAP,Layer.MENU))
            return ItemAction("field-use","使用${item.name}",reason==null,reason?:"","field-map-67")
        }
        item.worldUse?.let{rule->
            val snapshot=currentSnapshot();val mapMenu=panelReturnLayer in listOf(Layer.MAP,Layer.MENU)
            val target=content.worldItemTargets().asSequence()
                .firstOrNull{WorldItems.available(snapshot,item,rule,it,mapMenu)}
            val reason=if(rule.sceneScript?.locationTarget==true){
                OriginalSceneItems.unavailable(snapshot,item,rule.sceneScript!!.target,mapMenu)?:""
            }else if(rule.yangJoin!=null){
                val original=content.worldItemTargets().firstOrNull{it.id==rule.yangJoin!!.npcId}
                if(original==null)"原版目标尚未接入" else OriginalYangJoin.unavailable(snapshot,item,original,mapMenu)?:""
            }else when{!mapMenu->"仅支持地图/菜单使用";(inventory[id]?:0)<=0->"已无该物品";
                target==null->"请面向可使用的原版对象";else->""}
            return ItemAction("world-use","使用${item.name}",target!=null,reason,target?.id)
        }
        if(MapItemUse.supported(item)){
            val living=if(item.antidoteUse!=null)characters else characters.filter{it.hp>0};val target=if(living.size==1)living.single() else hero
            val mapMenu=panelReturnLayer in listOf(Layer.MAP,Layer.MENU)
            val enabled=MapItemUse.available(characters,inventory,target.id,item,mapMenu)
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
        else {characters=before.characters;inventory=before.inventory;flags=before.flags;money=before.money;feedback("保存失败，操作未完成")}
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
                else if(panelTab==CharacterTab.MAGIC)fieldSpells().getOrNull(index)?.let{return ModalCommand("field-spell",it,fieldMagicHero().id)}
                else if(panelTab==CharacterTab.EQUIPMENT)listOf("rightHand","leftHand","body","feet").getOrNull(index)?.let{return ModalCommand("slot",targetId=hero.id,slot=it)}
            }
        }
        if(l.wide||modalDetailsOpen){
            if(panelTab==CharacterTab.MAGIC&&selectedFieldSpell!=null&&l.primary.contains(x,y)&&fieldMagicReason()==null)
                return ModalCommand(if(fieldMagicChoosingTarget)"cast-field-spell" else "choose-field-magic-target",selectedFieldSpell,hero.id)
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
            "back-list"->{if(panelTab==CharacterTab.MAGIC){fieldMagicCaster?.let{id->characters.indexOfFirst{it.id==id}.takeIf{it>=0}?.let{characterPage=it}};clearFieldMagic()};modalDetailsOpen=false;modalDetailScroll=0f}
            "hero"->{characters.indexOfFirst{it.id==cmd.targetId}.takeIf{it>=0}?.let{characterPage=it};selectedItemId=null;candidateSlot=null
                if(panelTab==CharacterTab.MAGIC&&fieldMagicChoosingTarget)modalDetailScroll=0f else resetModalSelection()}
            "field-spell"->{if(cmd.itemId !in fieldSpells()||cmd.targetId!=fieldMagicHero().id)return
                selectedFieldSpell=cmd.itemId;fieldMagicCaster=fieldMagicHero().id;fieldMagicChoosingTarget=false;modalDetailsOpen=true;modalDetailScroll=0f}
            "choose-field-magic-target"->{if(panelTab!=CharacterTab.MAGIC||selectedFieldSpell!=cmd.itemId||fieldMagicChoosingTarget||fieldMagicReason()!=null)return
                fieldMagicChoosingTarget=true;modalDetailScroll=0f}
            "cast-field-spell"->{
                if(panelTab!=CharacterTab.MAGIC||!fieldMagicChoosingTarget||selectedFieldSpell!=cmd.itemId||selectedCharacterId!=cmd.targetId||fieldMagicReason()!=null)return
                val before=currentSnapshot();val caster=fieldMagicHero().id
                val result=OriginalFieldMagic.apply(characters,fieldMagicIndices(),caster,cmd.targetId?:return,cmd.itemId?:return,
                    panelReturnLayer in listOf(Layer.MAP,Layer.MENU))
                if(!result.applied){feedback(result.error?:"当前不能施法");return}
                characters=result.characters;commitModal(before,"${heroName(caster)}使用${OriginalFieldMagic.NAME}")
                characters.indexOfFirst{it.id==caster}.takeIf{it>=0}?.let{characterPage=it};resetModalSelection()
                modalDetailsOpen=true // Keep the actual save result visible on narrow layouts; a new selection is still required.
            }
            "item"->{if(panelItems().none{it.key==cmd.itemId})return;selectedItemId=cmd.itemId;modalDetailsOpen=true;modalDetailScroll=0f}
            "slot"->{equipmentSlot=cmd.slot?:return;modalDetailsOpen=true;modalDetailScroll=0f}
            "candidates"->{candidateSlot=cmd.slot;panelTab=CharacterTab.ITEMS;selectedItemId=null;resetModalSelection()}
            "night-light"->{
                val id=cmd.itemId?:return;val item=content.itemDefinitions[id]?:return
                val current=itemAction()
                if(selectedItemId!=id||current.kind!="night-light"||!current.enabled||cmd.targetId!=current.target)return
                val before=currentSnapshot()
                val result=WorldItems.useNightLight(before,item,panelReturnLayer in listOf(Layer.MAP,Layer.MENU))
                if(!result.applied){feedback(result.error?:"当前不可使用");return}
                // Verify/decode before committing state; draw never changes the effect.
                try{content.atlasForState(world.mapId,result.flags)}catch(e:Exception){
                    Diagnostics.record("night_light_atlas","ERROR",code=e.javaClass.simpleName,stack=e.stackTrace.take(12).joinToString("\n"));feedback("照明素材加载失败，物品和状态已保留");return
                }
                inventory=result.inventory;flags=result.flags;commitModal(before,"已使用${item.name}")
            }
            "field-use"->{
                val id=cmd.itemId?:return;val item=content.itemDefinitions[id]?:return
                val current=itemAction()
                if(selectedItemId!=id||current.kind!="field-use"||!current.enabled||cmd.targetId!=current.target)return
                val before=currentSnapshot()
                val result=WorldItems.useFieldProtection(before,item,panelReturnLayer in listOf(Layer.MAP,Layer.MENU))
                if(!result.applied){feedback(result.error?:"当前不可使用");return}
                inventory=result.inventory;flags=result.flags;commitModal(before,"已使用${item.name}")
            }
            "world-use"->{
                val id=cmd.itemId?:return;val item=content.itemDefinitions[id]?:return;val rule=item.worldUse?:return
                val current=itemAction()
                if(selectedItemId!=id||current.kind!="world-use"||!current.enabled||current.target!=cmd.targetId)return
                val target=content.worldItemTargets().firstOrNull{it.id==cmd.targetId}?:return
                val before=currentSnapshot()
                if(rule.sceneScript!=null){
                    val result=OriginalSceneItems.begin(before,item,target,panelReturnLayer in listOf(Layer.MAP,Layer.MENU))
                    if(!result.applied){feedback(result.error?:"当前不可使用");return}
                    closePanel();commitStoryFollowup(before,result,
                        if(rule.sceneScript!!.locationTarget)null else content.npcs.single{it.id==target.id});return
                }
                if(rule.yangJoin!=null){
                    val result=OriginalYangJoin.begin(before,item,target,content.joinCharacters["yangjian"],panelReturnLayer in listOf(Layer.MAP,Layer.MENU))
                    if(!result.applied){feedback(result.error?:"当前不可使用");return}
                    closePanel();commitStoryFollowup(before,result,content.npcs.single{it.id==target.id});return
                }
                val result=WorldItems.use(before,item,rule,target,panelReturnLayer in listOf(Layer.MAP,Layer.MENU))
                if(!result.applied){feedback(result.error?:"当前不可使用");return}
                inventory=result.inventory;flags=result.flags
                commitModal(before,"已使用${item.name}")
            }
            "equip","unequip","use"->{
                val before=currentSnapshot();val id=cmd.itemId?:return;val target=cmd.targetId?:return
                val index=characters.indexOfFirst{it.id==target};if(index<0)return
                val hero=characters[index]
                when(cmd.kind){
                    "use"->{val a=itemAction();if(a.kind!="use"||!a.enabled||a.target!=target||selectedItemId!=id)return
                        val item=content.itemDefinitions[id]?:return
                        val result=MapItemUse.apply(characters,inventory,target,item,panelReturnLayer in listOf(Layer.MAP,Layer.MENU))
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
    private fun openInn(definition:InnDefinition){
        if(layer!=Layer.MAP||finishPendingStep())return
        clinic=null;clinicTargetId=null;clinicTreatmentId=null;inn=definition;resetModalSelection();uxFeedback="";input.clear();npcTouch.clear();clock.reset();layer=Layer.INN
    }
    private fun closeInn(){
        inn=null;clinic=null;clinicTargetId=null;clinicTreatmentId=null;layer=Layer.MAP;input.clear();resetModalSelection();clock.reset()
    }
    fun innStayBounds()=modalLayout().primary
    private fun innHit(x:Float,y:Float):ModalCommand? {
        if(clinic!=null)return clinicHit(x,y)
        val s=inn?:return null;val l=modalLayout()
        if(l.close.contains(x,y))return ModalCommand("inn-close",shopId=s.id)
        if(l.primary.contains(x,y))return ModalCommand("inn-stay",shopId=s.id)
        return null
    }
    private fun runInnCommand(cmd:ModalCommand){
        if(clinic!=null){runClinicCommand(cmd);return}
        val s=inn?:return;if(layer!=Layer.INN||cmd.shopId!=s.id)return
        uxRevision++;clearUxGesture();input.clear()
        if(cmd.kind=="inn-close"){closeInn();return}
        if(cmd.kind!="inn-stay")return
        val before=currentSnapshot();val result=InnStay.apply(money,characters,s)
        if(result.error!=null){feedback(result.error);return}
        money=result.money;characters=result.characters
        if(persistStateResult()){
            closeInn();mapNotice="住宿完成 · 支付${s.price}两";noticeUntil=SystemClock.uptimeMillis()+3000
            Diagnostics.record("inn_stay",details=JSONObject().put("serviceID",s.id).put("price",s.price).put("success",true))
        }else{
            money=before.money;characters=before.characters;feedback("保存失败，住宿未完成")
        }
    }
    private fun drawInn(c:Canvas){
        if(clinic!=null){drawClinic(c);return}
        val s=inn?:return;val l=modalLayout();val dp=resources.displayMetrics.density
        touchFrame(c,s.name,"银两 $money",emptyList(),0)
        val rowText=characters.map{h->"${heroName(h.id)} HP ${h.hp}/${h.maxHp} MP ${h.mp}/${h.maxMp?:h.mp}\n"+
            if(InnStay.eligible(h,s))"住宿恢复HP/MP" else "当前状态不会被住宿恢复"}
        val list=if(l.wide)l.list else l.detail
        c.save();c.clipRect(list.x,list.y,list.x+list.w,list.y+list.h)
        touchText(c,(if(l.wide)"" else s.prompt+"\n")+rowText.joinToString("\n"),
            Box(list.x+8*dp,list.y+8*dp-modalDetailScroll,list.w-16*dp,list.h),14f);c.restore()
        if(l.wide){c.save();c.clipRect(l.detail.x,l.detail.y,l.detail.x+l.detail.w,l.detail.y+l.detail.h)
            touchText(c,s.prompt+"\n固定收费 ${s.price}两\n不复活；当前异常状态可能无法通过住宿恢复。\n"+uxFeedback,
                Box(l.detail.x+8*dp,l.detail.y+8*dp-modalDetailScroll,l.detail.w-16*dp,l.detail.h),14f);c.restore()}
        touchButton(c,l.primary,"住宿 · ${s.price}两",money>=s.price)
        if(uxFeedback.isNotEmpty()&&!l.wide)touchText(c,uxFeedback,Box(l.frame.x+8*dp,l.frame.y+48*dp,l.close.x-l.frame.x-16*dp,1f),12f)
    }
    private fun openClinic(definition:ClinicDefinition){
        if(layer!=Layer.MAP||finishPendingStep())return
        inn=null;clinic=definition;clinicTargetId=null;clinicTreatmentId=definition.treatments.firstOrNull()?.id;resetModalSelection();uxFeedback=""
        input.clear();npcTouch.clear();clock.reset();layer=Layer.INN
    }
    fun clinicTargetBounds(id:String)=modalLayout().visibleRow(characters.indexOfFirst{it.id==id},modalListScroll)
    fun clinicReviveBounds()=modalLayout().primary
    fun clinicTreatmentBounds(id:String)=modalLayout().tabs.getOrElse(clinic?.treatments?.indexOfFirst{it.id==id}?:-1){Box(0f,0f,0f,0f)}
    private fun selectedClinicTreatment()=clinic?.treatments?.firstOrNull{it.id==clinicTreatmentId}
    private fun clinicEligible(hero:CharacterState):Boolean {
        val s=clinic?:return false
        return if(s.kind=="REVIVAL")ClinicRevival.eligible(hero,s)else selectedClinicTreatment()?.let{ClinicCare.eligible(hero,it)}?:false
    }
    private fun clinicCost()=clinic?.let{if(it.kind=="REVIVAL")ClinicRevival.fee(money,it)else selectedClinicTreatment()?.price?:0}?:0

    private fun clinicHit(x:Float,y:Float):ModalCommand? {
        val s=clinic?:return null;val l=modalLayout()
        if(l.close.contains(x,y))return ModalCommand("clinic-close",shopId=s.id)
        for(t in s.treatments)if(clinicTreatmentBounds(t.id).contains(x,y))return ModalCommand("clinic-treatment",itemId=t.id,shopId=s.id)
        if(!l.wide&&modalDetailsOpen&&l.back.contains(x,y))return ModalCommand("clinic-list",shopId=s.id)
        if(l.wide||!modalDetailsOpen)for(h in characters){
            if(clinicTargetBounds(h.id).contains(x,y))return ModalCommand("clinic-target",targetId=h.id,shopId=s.id)
        }
        if(l.primary.contains(x,y)&&(l.wide||modalDetailsOpen)){
            val h=characters.firstOrNull{it.id==clinicTargetId}?:return null
            if(clinicEligible(h)&&(s.kind=="REVIVAL"||money>=clinicCost()))return ModalCommand(if(s.kind=="REVIVAL")"clinic-revive"else"clinic-treat",targetId=h.id,itemId=clinicTreatmentId,shopId=s.id)
        }
        return null
    }
    private fun runClinicCommand(cmd:ModalCommand){
        val s=clinic?:return;if(layer!=Layer.INN||cmd.shopId!=s.id)return
        uxRevision++;clearUxGesture();input.clear()
        when(cmd.kind){
            "clinic-close"->closeInn()
            "clinic-list"->{modalDetailsOpen=false;modalDetailScroll=0f}
            "clinic-treatment"->{
                if(s.treatments.none{it.id==cmd.itemId})return
                clinicTreatmentId=cmd.itemId;modalDetailScroll=0f;uxFeedback=""
            }
            "clinic-target"->{
                if(characters.none{it.id==cmd.targetId})return
                clinicTargetId=cmd.targetId;modalDetailsOpen=true;modalDetailScroll=0f;uxFeedback=""
            }
            "clinic-revive","clinic-treat"->{
                val target=cmd.targetId?:return;if(target!=clinicTargetId)return
                val before=currentSnapshot()
                if((cmd.kind=="clinic-revive")!=(s.kind=="REVIVAL"))return
                if(s.kind=="TREATMENT"&&cmd.itemId!=clinicTreatmentId)return
                val result=if(s.kind=="REVIVAL")ClinicRevival.apply(money,characters,target,s)
                    else ClinicCare.apply(money,characters,target,s,cmd.itemId?:return)
                if(!result.applied){feedback(result.error?:"当前不可复活");return}
                money=result.money;characters=result.characters
                if(persistStateResult()){
                    closeInn();mapNotice=if(s.kind=="REVIVAL")"${heroName(target)}已复活 · HP 1"else"${heroName(target)}治疗完成 · ${result.fee}两";noticeUntil=SystemClock.uptimeMillis()+3000
                    Diagnostics.record(if(s.kind=="REVIVAL")"clinic_revival"else"clinic_treatment",details=JSONObject().put("serviceID",s.id).put("targetID",target).put("fee",result.fee).put("success",true))
                }else{money=before.money;characters=before.characters;feedback("保存失败，医疗未完成")}
            }
        }
    }
    private fun drawClinic(c:Canvas){
        val s=clinic?:return;val l=modalLayout();val t=selectedClinicTreatment();val revive=s.kind=="REVIVAL"
        touchFrame(c,s.name,"银两 $money",s.treatments.map{it.name},s.treatments.indexOfFirst{it.id==clinicTreatmentId})
        val rows=characters.map{h->Triple(h.id,"${heroName(h.id)} · HP ${h.hp}/${h.maxHp}\n"+
            if(clinicEligible(h)){if(revive)"可复活"else"可治${t?.name}"}else{if(revive)"还活着，无需复活"else"没有${t?.name}状态"},content.characterDefinitions[h.id]?.portrait)}
        if(l.wide||!modalDetailsOpen)touchRows(c,rows,clinicTargetId)
        val h=characters.firstOrNull{it.id==clinicTargetId};val cost=clinicCost()
        if(l.wide||modalDetailsOpen){
            val lines=mutableListOf(if(revive)"道士复活"else"大夫治疗${t?.name}","${h?.let{heroName(it.id)}?:"请选择队员"}")
            if(revive){
                lines+="复活后 HP 1；MP不变";lines+="功德费 ${cost}两（现有银两1%，最低1两）"
                if(h!=null&&!clinicEligible(h))lines+="这位队员还活着，无需复活"
                if(money==0)lines+="原版零银两特殊结果：复活后银两999999"
                if(money>s.moneyLimit)lines+="原版先将银两限制到999999，再计算功德费"
            }else{
                lines+="只清除${t?.name}；HP、MP和其它状态不变";lines+="医疗费 ${cost}两"
                if(h!=null&&!clinicEligible(h))lines+="这位队员没有${t?.name}状态"
                if(money<cost)lines+="银两不足"
                if(money>s.moneyLimit)lines+="原版治疗后银两上限999999"
            }
            if(uxFeedback.isNotEmpty())lines+=uxFeedback
            touchDetail(c,lines,null)
            val action=if(revive)"复活"else"治${t?.name}于"
            touchButton(c,l.primary,if(h==null)"请选择队员"else"${action}${heroName(h.id)} · ${cost}两",h!=null&&clinicEligible(h)&&(revive||money>=cost))
        }
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
        val l=modalLayout();fun hit(x:Float,y:Float)=when(layer){Layer.SHOP->shopHit(x,y);Layer.INN->innHit(x,y);else->panelHit(x,y)}
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{clearUxGesture();val x=e.x;val y=e.y
                val area=when{clinic!=null&&(l.wide||!modalDetailsOpen)&&l.list.contains(x,y)->1;layer==Layer.INN&&(l.list.contains(x,y)||l.detail.contains(x,y))->2;(l.wide||!modalDetailsOpen)&&l.list.contains(x,y)->1;(l.wide||modalDetailsOpen)&&l.detail.contains(x,y)->2;else->0}
                uxGesture=ModalGesture(e.getPointerId(0),x,y,y,hit(x,y),uxRevision,modalState(),area)}
            MotionEvent.ACTION_POINTER_DOWN->{clearUxGesture();uxBlocked=true}
            MotionEvent.ACTION_MOVE->{val g=uxGesture
                if(!uxBlocked&&g!=null){val i=e.findPointerIndex(g.pointer);if(i>=0){val x=e.getX(i);val y=e.getY(i)
                    if(hypot(x-g.x,y-g.y)>ViewConfiguration.get(context).scaledTouchSlop){g.dragged=true;g.command=null}
                    if(g.dragged){val delta=g.lastY-y
                        if(g.scrollArea==1){val count=if(clinic!=null)characters.size else if(layer==Layer.SHOP)shopEntries().size else if(panelTab==CharacterTab.ITEMS)panelItems().size else if(panelTab==CharacterTab.MAGIC)fieldSpells().size else 4
                            modalListScroll=(modalListScroll+delta).coerceIn(0f,l.maxScroll(count))}
                        if(g.scrollArea==2)modalDetailScroll=max(0f,modalDetailScroll+delta)
                    };g.lastY=y}}
            }
            MotionEvent.ACTION_UP->{val g=uxGesture;val command=g?.command
                if(!uxBlocked&&g!=null&&!g.dragged&&g.pointer==e.getPointerId(e.actionIndex)&&g.revision==uxRevision&&g.state==modalState()&&command!=null&&command==hit(e.x,e.y)){
                    when(layer){Layer.SHOP->runShopCommand(command);Layer.INN->runInnCommand(command);else->runPanelCommand(command)}
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
        val x=l.frame.x+8*dp+if(!l.wide&&modalDetailsOpen)l.back.w+8*dp else 0f
        touchText(c,if(l.compactHeader)title else title+"\n"+subtitle,Box(x,l.frame.y+8*dp,l.close.x-x-8*dp,1f),15f)
        touchButton(c,l.close,"关闭")
        if(!l.wide&&modalDetailsOpen)touchButton(c,l.back,"列表")
        titles.forEachIndexed{i,t->touchButton(c,l.tabs[i],t,selected=i==selected)}
        if(layer !in listOf(Layer.SHOP,Layer.INN)&&characters.size>1)characters.forEachIndexed{i,h->touchButton(c,l.party[i],heroName(h.id),selected=i==characterPage)}
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
        val modal=modalLayout();val b=modal.detail;val dp=resources.displayMetrics.density;c.save();c.clipRect(b.x,b.y,b.x+b.w,b.y+b.h)
        var y=b.y-modalDetailScroll
        if(SystemClock.elapsedRealtime()<uxFeedbackUntil&&uxFeedback.isNotEmpty())y+=touchText(c,uxFeedback,Box(b.x,y,b.w,1f),14f,0xff72d3c5.toInt())+5*dp
        if(modal.compactHeader){
            val summary=if(layer==Layer.SHOP)"银两 $money" else characters[characterPage].let{"HP ${it.hp}/${it.maxHp} · MP ${it.mp}/${it.maxMp?:"?"} · 银两 $money"}
            y+=touchText(c,summary,Box(b.x,y,b.w,1f),14f)+5*dp
        }
        for(line in lines)y+=touchText(c,line,Box(b.x,y,b.w,1f),14f)+5*dp
        image?.let{paint.isFilterBitmap=false;c.drawBitmap(it,null,RectF(b.x,y,b.x+56*dp,y+56*dp),paint);y+=64*dp}
        modalDetailScroll=modalDetailScroll.coerceAtMost(max(0f,y+modalDetailScroll-b.y-b.h));c.restore()
    }
    private fun drawDirectPanel(c:Canvas){
        val l=modalLayout();val hero=characters[characterPage]
        touchFrame(c,"${heroName(hero.id)}  Lv.${hero.level}","HP ${hero.hp}/${hero.maxHp} · MP ${hero.mp}/${hero.maxMp?:"?"} · 银两 $money",listOf("属性","装备","物品","法术"),panelTab.ordinal)
        if(panelTab==CharacterTab.MAGIC){
            val caster=fieldMagicHero();val spells=fieldSpells()
            if(spells.isEmpty()){
                touchDetail(c,listOf(if(content.characterDefinitions[hero.id]?.originalActorIndex==0)"哪吒不会使用法术" else "此角色的法术效果尚未开放",
                    "其他治疗、解毒、战斗及地图法术仍待接入"));return
            }
            if(l.wide||!modalDetailsOpen)touchRows(c,spells.map{Triple(it,"${OriginalFieldMagic.NAME}\nMP ${OriginalFieldMagic.COST} · 地图治疗",null)},selectedFieldSpell)
            if(l.wide||modalDetailsOpen){
                val lines=mutableListOf(if(selectedFieldSpell==null)"请选择法术" else OriginalFieldMagic.NAME,
                    "施法者 ${heroName(caster.id)} · MP ${caster.mp}/${caster.maxMp?:"?"}","消耗 MP ${OriginalFieldMagic.COST}")
                if(fieldMagicChoosingTarget)lines+="请选择上方队员作为目标：${heroName(hero.id)} · HP ${hero.hp}/${hero.maxHp}"
                else lines+="选择法术后，点击选择目标"
                if(selectedFieldSpell!=null)fieldMagicReason()?.let{lines+=it}
                lines+="仅开放地图提神术；其他法术继续接入中"
                touchDetail(c,lines)
                if(selectedFieldSpell!=null)touchButton(c,l.primary,if(fieldMagicChoosingTarget)"使用于${heroName(hero.id)}" else "选择目标",fieldMagicReason()==null)
            };return
        }
        if(panelTab==CharacterTab.ATTRIBUTES){
            val progress=growthProgress(hero)
            val lines=if(panelTab==CharacterTab.MAGIC)listOf("已学法术状态尚未迁移","执行逻辑尚未开放") else
                listOf("当前等级 ${hero.level}",if(progress.status==ExperienceProgress.Status.PROGRESS)"下一等级 ${hero.level+1}" else progress.summary,
                    progress.summary,"累计EXP ${hero.experience}","状态 ${OriginalStatus.label(hero.statusMask)}","HP ${hero.hp}/${hero.maxHp} · MP ${hero.mp}/${hero.maxMp?:"?"}",
                    "总攻击 ${hero.strength+equipmentBonus(hero,"rightHand")} · 总防御 ${hero.stamina+equipmentBonus(hero,"body")}",
                    "敏捷 ${hero.agility} · 精神 ${hero.spirit}","银两 $money")
            touchDetail(c,lines);return
        }
        if(l.wide||!modalDetailsOpen){
            val rows=if(panelTab==CharacterTab.ITEMS)panelItems().map{e->val item=content.itemDefinitions[e.key]
                val status=when{item?.fieldProtectionUse!=null->"场景使用";item?.worldUse!=null->"地图对象使用";item?.let(MapItemUse::supported)==true->if(characters.isEmpty()||(item.antidoteUse==null&&characters.none{it.hp>0}))"无合法目标" else "地图使用";content.equipmentDefinitions[e.key]?.let{OpeningEquipment.replace(hero,inventory,it,content.equipmentDefinitions.values)!=null}==true->"可装备";content.equipmentDefinitions[e.key]?.operationEnabled==true->"查看装备条件";else->"操作待接入"}
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
                touchDetail(c,listOf("${slotName(equipmentSlot)} · ${item?.name?:"空或尚未核验"}","角色 ${heroName(hero.id)}", "总攻击 ${hero.strength+equipmentBonus(hero,"rightHand")}","总防御 ${hero.stamina+equipmentBonus(hero,"body")}",if(d==null)"当前槽位无可卸下的已实现装备" else if(!d.operationEnabled)"此件装备的卸下规则尚未接入" else if(!removable)"当前背包条件不允许回包" else "卸下后回到真实背包"),item?.preview)
                if(d!=null)touchButton(c,l.primary,"卸下 ${item?.name?:"装备"}",removable)
                touchButton(c,l.secondary,if(hasEquipmentCandidate())"选择候选" else "无合法候选",hasEquipmentCandidate())}
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
        if(layer==Layer.INN){when(key){Key.A->if(clinic!=null)clinic?.let{runClinicCommand(ModalCommand(if(it.kind=="REVIVAL")"clinic-revive"else"clinic-treat",targetId=clinicTargetId,itemId=clinicTreatmentId,shopId=it.id))}else inn?.let{runInnCommand(ModalCommand("inn-stay",shopId=it.id))};Key.B,Key.MENU->closeInn();else->Unit};return}
        if(layer==Layer.SHOP){when(key){Key.A->runShopAction(4)
            Key.B,Key.MENU->shopBack();Key.UP->runShopAction(7);Key.DOWN->runShopAction(8);else->Unit};return}
        when(key){
            Key.MENU->when(layer){Layer.MAP->openMenu();Layer.MENU->closeMenu();Layer.CHARACTER,Layer.INVENTORY->closePanel();else->Unit}
            Key.A->when(layer){Layer.MAP->interactionTarget()?.let{openNpc(it)};Layer.MENU->confirmMenu();Layer.DIALOGUE->advanceDialogue();Layer.BATTLE->confirmBattle();Layer.CHARACTER,Layer.INVENTORY->if(directPanel()){val b=modalLayout().primary;panelHit(b.x+b.w/2,b.y+b.h/2)?.let{runPanelCommand(it)}};else->Unit}
            Key.B->when(layer){Layer.MENU->closeMenu();Layer.DIALOGUE->{if(canDismissDialogue())dismissDialogue()};Layer.CHARACTER,Layer.INVENTORY->closePanel();Layer.BATTLE->closeBattle();else->Unit}
            Key.START->when(layer){Layer.MAP->openMenu();Layer.MENU->closeMenu();Layer.CHARACTER,Layer.INVENTORY->closePanel();else->Unit}
            else->Unit
        }
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        if(OriginalFerry.pending(flags,content.ferries.values)!=null){input.clear();npcTouch.clear();hudTouch.clear();return true}
        if(layer==Layer.FIELD_FAILURE)return true
        if(layer==Layer.BATTLE)return battleTouchEvent(e)
        if(layer in listOf(Layer.SHOP,Layer.INN) || (layer in listOf(Layer.CHARACTER,Layer.INVENTORY)&&directPanel()))return modalTouch(e)
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
                else if(layer==Layer.BATTLE)Unit
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
                else if(battleSelected!=null && layer==Layer.BATTLE && false && battleTouchRevision==battlePresentation.revision){
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
        if(OriginalFerry.pending(flags,content.ferries.values)!=null){input.clear();return true}
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
        val mapAtlas=content.atlasForState(world.mapId,flags)
        for(ty in firstY..lastY)for(tx in firstX..lastX){
            val i=ty*scene.width+tx;val t=scene.grid[i];val x=tx*16f;val y=ty*16f
            paint.color=Color.WHITE;paint.alpha=255
            c.drawBitmap(mapAtlas,Rect(t%16*16,t/16*16,t%16*16+16,t/16*16+16),RectF(x,y,x+16,y+16),paint)
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
        val actors=content.npcsForState(world.mapId,flags).filter{
            (content.npcVisible(it,flags)||(layer==Layer.DIALOGUE&&dialogueNpc?.id==it.id))&&
            (!it.scriptedActor||it.id in content.sceneStories||(layer==Layer.DIALOGUE&&dialogueNpc?.id==it.id))}.sortedBy{it.y}
        val objects=content.mapObjects.filter{it.mapId==world.mapId&&it.itemTarget?.let{t->flags[t.removedFlagId]!=true}!=false}
        for(obj in objects.filter{it.y*16+8<=world.y})c.drawBitmap(obj.sprite,obj.x*16f,obj.y*16f,paint)
        for(npc in actors.filter{it.y*16+8<=world.y})
            c.drawBitmap(if(npc.treasure?.let{flags[it.flagId]}==true||npc.moneyTreasure?.let{flags[it.flagId]}==true)npc.openedSprite?:npc.sprite else npc.sprite,npc.x*16f,npc.y*16f,paint)
        val ferry=OriginalFerry.pending(flags,content.ferries.values)
        if(world.mapId==16&&flags[OriginalFerry.PARKED_FLAG]==true&&ferry==null)
            content.ferrySprites["rom.ferry.46"]?.let{c.drawBitmap(it,150*16f,136*16f,paint)}
        if(content.freeBoatEnabled&&world.mapId==16&&world.terrainMode==0)
            OriginalBoat.parked(flags)?.let{p->OriginalBoat.parkedDirection(flags)?.let{key->
                content.freeBoatSprites[key]?.let{c.drawBitmap(it,p.first*16f,p.second*16f,paint)}
            }}
        val actor=if(content.freeBoatEnabled&&world.terrainMode==OriginalBoat.MODE)
            content.freeBoatSprites.getValue(world.direction)
            else if(ferry!=null&&world.mapId==16&&(ferry.stage(flags)?:0)>0)
            content.ferrySprites.getValue(ferry.id)else content.sprites.getValue(world.direction)
        c.drawBitmap(actor,(world.x-8).toFloat(),(world.y-8).toFloat(),paint)
        for(obj in objects.filter{it.y*16+8>world.y})c.drawBitmap(obj.sprite,obj.x*16f,obj.y*16f,paint)
        for(npc in actors.filter{it.y*16+8>world.y})
            c.drawBitmap(if(npc.treasure?.let{flags[it.flagId]}==true||npc.moneyTreasure?.let{flags[it.flagId]}==true)npc.openedSprite?:npc.sprite else npc.sprite,npc.x*16f,npc.y*16f,paint)
        if(layer==Layer.MAP){
            overlayPaint.color=0xff75ded5.toInt();overlayPaint.alpha=230
            for(npc in nearbyNpcs())c.drawCircle(npc.x*16f+8,npc.y*16f-2,1.6f,overlayPaint)
        }
        c.restore()
        when(layer){Layer.FIELD_FAILURE->drawHud(c);Layer.MAP->{if(ferry==null)drawControls(c)else label(c,"乘船中",ui.safe.x+ui.safe.w/2,ui.safe.y+24*resources.displayMetrics.density,14f);drawHud(c)};Layer.MENU->drawMenu(c);Layer.SETTINGS->Unit;Layer.DIALOGUE->drawDialogue(c);
            Layer.CHARACTER,Layer.INVENTORY->drawInfoPanel(c);Layer.BATTLE->drawBattle(c);Layer.SHOP->drawShop(c);Layer.INN->drawInn(c)}
        if(world.message!=previousMessage){previousMessage=world.message;if(world.message.startsWith("开发边界")){mapNotice=world.message;noticeUntil=SystemClock.uptimeMillis()+1800}}
        if(layer==Layer.MAP&&SystemClock.uptimeMillis()<noticeUntil){
            val dp=resources.displayMetrics.density;val label=mapNotice
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
        val big=resources.configuration.fontScale>1.3f;val p=hudPortraitBox()
        overlayPaint.color=0xc923303b.toInt();c.drawRoundRect(RectF(b.x,b.y,b.x+b.w,b.y+b.h),9*dp,9*dp,overlayPaint)
        if(!big)portrait(c,hero,p)
        val x=if(big)b.x+8*dp else p.x+p.w+8*dp;val right=b.x+b.w-8*dp
        var y=b.y+5*dp
        y+=touchText(c,"${heroName(hero.id)} Lv.${hero.level}"+if(hero.statusMask==0)"" else " · ${OriginalStatus.label(hero.statusMask)}",Box(x,y,right-x,1f),11f)
        y+=touchText(c,"HP ${hero.hp}/${hero.maxHp} · MP ${hero.mp}/${hero.maxMp?:"?"}",Box(x,y,right-x,1f),10f)
        gauge(c,Box(x,y+2*dp,(right-x)*.48f,4*dp),hero.hp,hero.maxHp,0xffc55758.toInt())
        gauge(c,Box(x+(right-x)*.52f,y+2*dp,(right-x)*.48f,4*dp),hero.mp,hero.maxMp,0xff638cce.toInt());y+=9*dp
        val progress=growthProgress(hero)
        val text=if(progress.status==ExperienceProgress.Status.PROGRESS)"EXP ${progress.earned}/${progress.span}" else if(progress.status==ExperienceProgress.Status.INVALID)"EXP 状态异常" else "EXP · ${progress.summary}"
        y+=touchText(c,text,Box(x,y,right-x,1f),10f)
        gauge(c,Box(x,y+2*dp,right-x,4*dp),progress.earned?:0,progress.span,0xffcfab54.toInt())
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
        if(slot=="rightHand")OriginalYangJoin.initialHandContribution(currentSnapshot(),hero)?.let{return it}
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
        val current=battle?:return;val l=battleLayout();val b=battleBox();val scale=b.w/256f
        val screen=battlePresentation.screen;val action=battlePresentation.action;val dp=resources.displayMetrics.density
        if(battleInfoOpen){drawBattleInformation(c,current);return}
        if(battleItemsOpen){drawBattleItems(c,current);return}
        battleScene()?.let{drawBattleScene(c,current,it);return}
        c.drawColor(Color.BLACK);paint.color=Color.WHITE;paint.alpha=255;paint.isFilterBitmap=false
        val horizon=if(current.enemies.any{it.definition.id in content.blackBattleEnemyIds})null else content.battleHorizons[world.mapId]?:content.battleHorizon
        horizon?.takeIf{screen!=BattlePresentation.Screen.RESULT}?.let{image->c.drawBitmap(image,null,RectF(b.x,b.y,b.x+b.w,b.y+32*scale),paint)}
        for(enemy in current.enemies){
            val hp=battleVisibleHp(enemy.slot)?:0;val impact=action?.targetSlot==enemy.slot&&action.kind==BattleActionKind.DAMAGE
            if(screen!=BattlePresentation.Screen.RESULT&&(hp>0||impact)){
                val box=battleEnemyBox(enemy);val moving=action?.actorSlot==enemy.slot&&action.kind in listOf(BattleActionKind.ATTACK,BattleActionKind.ICE,BattleActionKind.SPECIAL)
                val shift=if(moving)sin(battlePresentation.elapsedMs.toDouble()/battlePresentation.actionDurationMs*Math.PI).toFloat()*3*dp else 0f
                paint.alpha=if(impact&&battlePresentation.elapsedMs/80%2==0L)80 else 255
                content.enemyGraphics[enemy.definition.id]?.let{c.drawBitmap(it,null,RectF(box.x+shift,box.y,box.x+box.w+shift,box.y+box.h),paint)};paint.alpha=255
            }
            val row=battleTargetBounds(enemy.slot);val selected=selectedBattleSlot==enemy.slot
            overlayPaint.color=if(selected)0xff244d49.toInt() else 0xff18252e.toInt();c.drawRect(row.x,row.y,row.x+row.w,row.y+row.h,overlayPaint)
            val same=current.enemies.count{it.definition.id==enemy.definition.id}>1
            val title=enemy.definition.name+(if(same)" ${enemy.slot+1}" else "")+(if(hp<=0)" · 倒下" else "")
            touchText(c,title,Box(row.x+6*dp,row.y+4*dp,row.w-12*dp,1f),12f,if(hp>0)Color.WHITE else 0xff929292.toInt())
            gauge(c,Box(row.x+6*dp,row.y+row.h-9*dp,row.w-12*dp,5*dp),hp,enemy.definition.hp,0xffc55758.toInt())
            if(action?.actorSlot==enemy.slot||action?.targetSlot==enemy.slot){overlayPaint.color=0xffcfab54.toInt();c.drawRect(row.x,row.y,row.x+3*dp,row.y+row.h,overlayPaint)}
        }
        if(screen==BattlePresentation.Screen.RESULT){
            overlayPaint.color=Color.BLACK;c.drawRect(l.result.x,l.result.y,l.result.x+l.result.w,l.result.y+l.result.h,overlayPaint)
            c.save();c.clipRect(l.result.x,l.result.y,l.result.x+l.result.w,l.result.y+l.result.h)
            var y=l.result.y+8*dp-battleResultScroll
            for(line in battleResultLines)y+=touchText(c,line,Box(l.result.x+12*dp,y,l.result.w-24*dp,1f),14f)+4*dp
            c.restore()
            val hint=if(battleSavePending)"保存失败：点击重试" else if(storyBattle!=null)"点击继续剧情" else "奖励已结算，稍后自动继续"
            touchText(c,hint,Box(l.status.x+6*dp,l.status.y+6*dp,l.status.w-12*dp,1f),12f)
            return
        }
        if(action?.kind==BattleActionKind.ATTACK&&action.actorSlot==null){
            (if(action.actorId==null||action.actorId==content.initialPlayer.id)content.battleHero else content.characterDefinitions[action.actorId]?.portrait)?.let{image->
                val actor=battleRegion(120f,112f,16f,16f)
                val shift=sin(battlePresentation.elapsedMs.toDouble()/battlePresentation.actionDurationMs*Math.PI).toFloat()*3*dp
                c.drawBitmap(image,null,RectF(actor.x,actor.y-shift,actor.x+actor.w,actor.y+actor.h-shift),paint)
            }
        }
        val message=if(battleNotice.isNotEmpty())battleNotice else when(screen){
            BattlePresentation.Screen.ENTRY->"敌人出现了！"
            BattlePresentation.Screen.ACTING->action?.let{step->
                val actor=if(step.kind in listOf(BattleActionKind.DAMAGE,BattleActionKind.STATUS,BattleActionKind.HEAL))step.targetId else step.actorId
                (if(current.party.size>1&&actor!=null)heroName(actor)+" · " else "")+step.text}?:""
            else->"${current.inputHero?.let{heroName(it.id)}?:"队伍"}：先选指令；点击敌人查看信息"}
        val partyLines=current.party.map{player->
            val hp=action?.partyHp?.get(player.id)?:if(player.id==current.hero.id)action?.heroHp?:player.hp else player.hp
            val status=action?.partyStatus?.get(player.id)?:if(player.id==current.hero.id)action?.heroStatusMask?:player.statusMask else player.statusMask
            "${heroName(player.id)}${if(status==0)"" else " · ${OriginalStatus.label(status)}"} HP $hp/${player.maxHp} · MP ${player.mp}/${player.maxMp?:"?"}"
        }
        touchText(c,(partyLines+message).joinToString("\n"),Box(l.status.x+6*dp,l.status.y+3*dp,l.status.w-12*dp,l.status.h),12f)
        val waiting=screen in listOf(BattlePresentation.Screen.COMMAND,BattlePresentation.Screen.TARGET)
        listOf("攻击","法术","物品","逃跑","信息").forEachIndexed{i,title->touchButton(c,l.commands[i],title,waiting&&i!=1)}
        if(action?.kind in listOf(BattleActionKind.DAMAGE,BattleActionKind.MISS,BattleActionKind.HEAL)){
            val target=action?.targetSlot?.let{slot->current.enemies.firstOrNull{it.slot==slot}?.let{battleEnemyBox(it)}}?:l.arena
            val text=if(action?.kind==BattleActionKind.MISS)"MISS" else (action?.hpDelta?:0).toString()
            touchText(c,text,Box(target.x+target.w/2,target.y+target.h*.4f,120*dp,1f),18f,if(action?.kind==BattleActionKind.HEAL)0xff72d3c5.toInt() else 0xffffb3a7.toInt())
        }
        if(action?.kind==BattleActionKind.ICE){overlayPaint.color=0x4484cafa;c.drawRect(l.arena.x,l.arena.y,l.arena.x+l.arena.w,l.arena.y+l.arena.h,overlayPaint)}
        if(action?.kind==BattleActionKind.SPECIAL){overlayPaint.color=0x33ffffff;c.drawRect(l.arena.x,l.arena.y,l.arena.x+l.arena.w,l.arena.y+l.arena.h,overlayPaint)}
        textPaint.color=Color.WHITE
    }
    /** Single-line labels keep full details in the independent scrollable layer. */
    private fun battleLine(c:Canvas,value:String,box:Box,sp:Float=12f,color:Int=Color.WHITE){
        textPaint.textSize=sp*resources.displayMetrics.scaledDensity;textPaint.color=color
        battleLinePaint.set(textPaint)
        val text=android.text.TextUtils.ellipsize(value,battleLinePaint,max(1f,box.w),android.text.TextUtils.TruncateAt.END).toString()
        c.save();c.clipRect(box.x,box.y,box.x+box.w,box.y+box.h)
        c.drawText(text,box.x,box.y-textPaint.fontMetrics.ascent,textPaint);c.restore()
    }
    private fun drawBattleScene(c:Canvas,current:OpeningBattle,scene:BattleSceneLayout){
        val l=scene.touch;val dp=resources.displayMetrics.density;val font=resources.configuration.fontScale
        val screen=battlePresentation.screen;val action=battlePresentation.action
        c.drawColor(Color.BLACK);overlayPaint.alpha=255;paint.alpha=255;paint.isFilterBitmap=false
        if(screen==BattlePresentation.Screen.RESULT){drawBattleSceneResult(c,current,scene);return}
        val blackScene=current.enemies.any{it.definition.id in content.blackBattleEnemyIds}
        overlayPaint.color=if(blackScene)Color.BLACK else 0xff111c22.toInt();c.drawRect(l.arena.x,l.arena.y,l.arena.x+l.arena.w,l.arena.y+l.arena.h,overlayPaint)
        val horizon=if(blackScene)null
            else content.battleHorizons[world.mapId]?:content.battleHorizon
        horizon?.let{image->val scale=min(l.arena.w/image.width,l.arena.h/image.height)
            val x=l.arena.x+(l.arena.w-image.width*scale)/2
            c.drawBitmap(image,null,RectF(x,l.arena.y,x+image.width*scale,l.arena.y+image.height*scale),paint)}
        val message=when{
            screen==BattlePresentation.Screen.ENTRY->"敌人出现了！"
            screen==BattlePresentation.Screen.ACTING->action?.let{step->
                val id=if(step.kind in listOf(BattleActionKind.DAMAGE,BattleActionKind.STATUS,BattleActionKind.HEAL))step.targetId else step.actorId
                (if(current.party.size>1&&id!=null)heroName(id)+" · "else"")+step.text}?:"行动中"
            attackSelected()->"${current.inputHero?.let{heroName(it.id)}?:"队伍"} · 攻击：请选择存活敌人"
            battleNotice.isNotEmpty()->battleNotice
            else->"${current.inputHero?.let{heroName(it.id)}?:"队伍"} · 请选择指令；点击敌人查看信息"}
        battleLine(c,message,scene.prompt,13f)
        for((i,enemy)in current.enemies.withIndex()){
            val cell=l.enemies[i];val hp=battleVisibleHp(enemy.slot)?:0
            overlayPaint.color=if(selectedBattleSlot==enemy.slot)0xdd244d49.toInt()else if(blackScene)0xcc000000.toInt()else 0xcc18252e.toInt()
            c.drawRect(cell.x,cell.y,cell.x+cell.w,cell.y+cell.h,overlayPaint)
            val sprite=battleEnemyBox(enemy)
            val impact=action?.targetSlot==enemy.slot&&action.kind==BattleActionKind.DAMAGE
            if(hp>0||impact){
                val shift=if(action?.actorSlot==enemy.slot)sin(battlePresentation.elapsedMs.toDouble()/battlePresentation.actionDurationMs*Math.PI).toFloat()*3*dp else 0f
                paint.alpha=if(impact&&battlePresentation.elapsedMs/80%2==0L)80 else 255
                content.enemyGraphics[enemy.definition.id]?.let{c.drawBitmap(it,null,RectF(sprite.x+shift,sprite.y,sprite.x+sprite.w+shift,sprite.y+sprite.h),paint)}
                paint.alpha=255
            }
            val parts=battleEnemySceneLayout(cell,dp,font,scene.compact,current.enemies.size==1)
            val name=if(scene.compact)"#${enemy.slot+1}"
                else (if(current.enemies.size>1)"#${enemy.slot+1} "else"")+enemy.definition.name
            battleLine(c,if(hp>0||scene.compact)name else "$name · 倒下",parts.label,12f,
                if(hp>0)Color.WHITE else 0xff88969c.toInt())
            gauge(c,parts.gauge,hp,enemy.definition.hp,0xffc55758.toInt())
        }
        for((i,hero)in current.party.withIndex()){
            val view=battlePartyView(hero,heroName(hero.id),action,current.inputHero?.id,current.hero.id)
            val field=scene.allySprites[i];val size=min(48*dp,min(field.w-8*dp,field.h-8*dp)).coerceAtLeast(dp)
            val moving=action?.kind==BattleActionKind.ATTACK&&action.actorSlot==null&&
                (action.actorId==hero.id||(action.actorId==null&&hero.id==current.hero.id))
            val shift=if(moving)sin(battlePresentation.elapsedMs.toDouble()/battlePresentation.actionDurationMs*Math.PI).toFloat()*3*dp else 0f
            val sprite=Box(field.x+(field.w-size)/2,field.y+(field.h-size)/2-shift,size,size)
            if(view.hp>0)portrait(c,hero,sprite)
            if(action?.targetId==hero.id){overlayPaint.color=0xffcfab54.toInt();overlayPaint.style=Paint.Style.STROKE
                overlayPaint.strokeWidth=2*dp;c.drawRect(sprite.x,sprite.y,sprite.x+sprite.w,sprite.y+sprite.h,overlayPaint)
                overlayPaint.style=Paint.Style.FILL}
            val card=scene.partyCards[i]
            overlayPaint.color=if(view.active)0xff244d49.toInt()else 0xff18252e.toInt()
            c.drawRect(card.x,card.y,card.x+card.w,card.y+card.h,overlayPaint)
            if(view.active){overlayPaint.color=0xffcfab54.toInt();c.drawRect(card.x,card.y,card.x+3*dp,card.y+card.h,overlayPaint)}
            val icon=Box(card.x+6*dp,card.y+4*dp,24*dp,24*dp);portrait(c,hero,icon)
            val title="${view.name} Lv.${view.level}"+(if(view.status==0)""else" · ${OriginalStatus.label(view.status)}")
            battleLine(c,title,Box(icon.x+icon.w+6*dp,card.y+4*dp,card.w-42*dp,max(24*dp,17*font*dp)),13f)
            val x=card.x+8*dp;val w=card.w-16*dp;val y=card.y+card.h-max(18f,14*font)*dp-4*dp
            battleLine(c,"HP ${view.hp}",Box(x,y,w*.58f,max(18f,14*font)*dp),11f)
            battleLine(c,"MP ${view.mp}",Box(x+w*.6f,y,w*.4f,max(18f,14*font)*dp),11f)
            gauge(c,Box(x,card.y+card.h-5*dp,w*.56f,3*dp),view.hp,view.maxHp,0xffc55758.toInt())
            gauge(c,Box(x+w*.6f,card.y+card.h-5*dp,w*.4f,3*dp),view.mp,view.maxMp,0xff638cce.toInt())
        }
        val waiting=screen in listOf(BattlePresentation.Screen.COMMAND,BattlePresentation.Screen.TARGET)
        listOf("攻击","法术","物品","逃跑","信息").forEachIndexed{i,title->
            touchButton(c,l.commands[i],title,waiting&&i!=1,selected=i==0&&attackSelected())}
        if(action?.kind in listOf(BattleActionKind.DAMAGE,BattleActionKind.MISS,BattleActionKind.HEAL)){
            val target=action?.targetSlot?.let{slot->current.enemies.firstOrNull{it.slot==slot}?.let{battleEnemyBox(it)}}
                ?:scene.allySprites.getOrNull(current.party.indexOfFirst{it.id==action?.targetId})?:scene.allyField
            battleLine(c,if(action?.kind==BattleActionKind.MISS)"MISS"else(action?.hpDelta?:0).toString(),
                Box(target.x,target.y,target.w,max(28*dp,24*font*dp)),16f,
                if(action?.kind==BattleActionKind.HEAL)0xff72d3c5.toInt()else 0xffffb3a7.toInt())
        }
        if(action?.kind==BattleActionKind.ICE){overlayPaint.color=0x4484cafa
            c.drawRect(l.arena.x,l.arena.y,l.arena.x+l.arena.w,l.arena.y+l.arena.h,overlayPaint)}
        if(action?.kind==BattleActionKind.SPECIAL){overlayPaint.color=0x33ffffff
            c.drawRect(l.arena.x,l.arena.y,l.arena.x+l.arena.w,l.arena.y+l.arena.h,overlayPaint)}
        overlayPaint.alpha=255
    }
    private fun drawBattleSceneResult(c:Canvas,current:OpeningBattle,scene:BattleSceneLayout){
        val l=scene.touch;val dp=resources.displayMetrics.density;val font=resources.configuration.fontScale
        if(battleResultDetails){
            c.save();c.clipRect(l.result.x,l.result.y,l.result.x+l.result.w,l.result.y+l.result.h)
            var y=l.result.y+8*dp-battleResultScroll
            for(line in battleResultLines)y+=touchText(c,line,Box(l.result.x+12*dp,y,l.result.w-24*dp,1f),14f)+4*dp
            battleResultScroll=battleResultScroll.coerceAtMost(max(0f,y+battleResultScroll-l.result.y-l.result.h));c.restore()
        }else{
            val title=battleResultLines.firstOrNull()?:battleMessage
            val summary=battleResultLines.getOrNull(1)?:""
            val titleH=max(24f,19*font)*dp;val summaryH=max(24f,18*font)*dp
            battleLine(c,title,Box(l.result.x+8*dp,l.result.y+4*dp,l.result.w-16*dp,titleH),15f)
            val summaryY=l.result.y+titleH+8*dp;val top=summaryY+summaryH+4*dp
            battleLine(c,summary,Box(l.result.x+8*dp,summaryY,l.result.w-16*dp,summaryH),12f)
            val entries=battleResultParty
            val columns=if(entries.size==1)1 else 2;val rows=max(1,(entries.size+columns-1)/columns)
            val cw=(l.result.w-12*dp)/columns;val ch=(l.result.y+l.result.h-top-8*dp)/rows
            for((i,entry)in entries.withIndex()){
                val(hero,gain)=entry;val card=Box(l.result.x+4*dp+(i%columns)*cw,top+(i/columns)*ch,cw-4*dp,ch-4*dp)
                overlayPaint.color=0xff18252e.toInt();c.drawRect(card.x,card.y,card.x+card.w,card.y+card.h,overlayPaint)
                val icon=Box(card.x+6*dp,card.y+6*dp,24*dp,24*dp);portrait(c,hero,icon)
                val nameH=max(24f,13*font*1.25f)*dp;val gainH=max(20f,12*font*1.25f)*dp
                battleLine(c,"${heroName(hero.id)} Lv.${hero.level}",Box(icon.x+30*dp,card.y+2*dp,card.w-42*dp,nameH),13f)
                val gainY=max(card.y+2*dp+nameH,icon.y+icon.h+2*dp)
                battleLine(c,"EXP +$gain · 累计 ${hero.experience}",Box(card.x+8*dp,gainY,card.w-16*dp,gainH),12f)
                if(ch>max(90f,68*font)*dp)battleLine(c,growthProgress(hero).summary,
                    Box(card.x+8*dp,card.y+max(60f,42*font)*dp,card.w-16*dp,max(24f,18*font)*dp),11f)
            }
        }
        val footer=scene.resultFooter;val left=Box(footer.x,footer.y,(footer.w-8*dp)/2,footer.h)
        val right=Box(left.x+left.w+8*dp,footer.y,left.w,footer.h)
        touchButton(c,left,if(battleResultDetails)"奖励摘要"else"奖励详情",battleCommitted)
        touchButton(c,right,if(battleSavePending)"重试保存"else if(storyBattle!=null)"继续剧情"else"继续",battleCommitted)
    }
    fun battleHerbCount()=max(0,(inventory[HerbUse.ID]?:0)-(battle?.herbsConsumed?:0))
    private fun battleMedicines()=content.itemDefinitions.values.filter{(it.category=="medicine"||it.battleBindingUse!=null)&&
        ((inventory[it.id]?:0)>0||it.id==HerbUse.ID)}.map{it.id}.sorted()
    private fun battleMedicineScene():BattleMedicineSceneLayout? {
        if(selectedBattleItem?.let{content.itemDefinitions[it]?.battleBindingUse}!=null)return null
        return battleMedicineSceneLayout(ui.safe,resources.displayMetrics.density,
            resources.configuration.fontScale,battle?.party?.size?:1)
    }
    private fun battleItemLayout():TouchModalLayout {
        battleMedicineScene()?.let{return it.modal}
        val dp=resources.displayMetrics.density;val font=resources.configuration.fontScale
        val l=touchModalLayout(ui.safe,dp,font,0,0,false)
        val header=max(96f,(15f+12f)*font*1.25f+28f)*dp+
            battlePartyTargetHeader(dp,font,if(selectedBattleItem?.let{content.itemDefinitions[it]?.battleBindingUse}!=null)1 else battle?.party?.size?:1)
        val y=l.frame.y+header+8*dp;val bottom=l.frame.y+l.frame.h-8*dp
        return l.copy(list=l.list.copy(y=y,h=bottom-y),detail=l.detail.copy(y=y,h=max(1f,l.primary.y-8*dp-y)))
    }
    private fun currentBattleTargetId()=battle?.inputHero?.id?:battle?.party?.firstOrNull{it.hp>0}?.id
    private fun battleItemTarget()=battle?.party?.firstOrNull{it.id==selectedBattleTarget}?:battle?.party?.firstOrNull{it.id==currentBattleTargetId()}
    fun battleItemTargetBounds(id:String):Box {
        val party=battle?.party?:return Box(0f,0f,0f,0f);val i=party.indexOfFirst{it.id==id}
        if(party.size<=1||i<0)return Box(0f,0f,0f,0f)
        battleMedicineScene()?.let{return it.targets[i]}
        val l=battleItemLayout();val dp=resources.displayMetrics.density;val font=resources.configuration.fontScale
        return battlePartyTargetBoxes(l.frame,l.list.y,dp,font,party.size)[i]
    }
    fun battleItemBounds(id:String)=battleItemLayout().visibleRow(battleMedicines().indexOf(id),battleItemListScroll)
    fun battleItemUseBounds()=battleItemLayout().primary
    fun battleItemCloseBounds()=battleItemLayout().close
    private fun battleMedicineReason(id:String):String {
        if(content.itemDefinitions[id]?.battleBindingUse!=null){
            val item=content.itemDefinitions.getValue(id);val use=item.battleBindingUse!!
            if((inventory[id]?:0)!=1)return "没有${item.name}"
            if(flags["rom.inventory.special.${item.originalId}.used"]==true)return "此秘宝已有原版使用标记"
            val current=battle?:return "当前不在战斗中"
            return if(current.bindingAvailable(inventory[id]?:0,item))"轮到当前角色时困住${use.targetLabel}；保留数量"else "当前不是已支持的${use.targetLabel}战斗或输入阶段"
        }
        if(id!=HerbUse.ID)return "此物品的战斗效果尚未实现"
        if(battleHerbCount()<=0)return "没有药草库存"
        val current=battle?:return "当前不在战斗"
        val target=battleItemTarget()?:return "当前没有合法目标"
        if(target.statusMask and OriginalStatus.DEAD!=0)return "此目标已倒下：确认仍消耗1份，药草不能复活"
        val item=content.itemDefinitions[id]?:return "物品定义未接入"
        return if(current.herbAvailable(target.id,inventory[id]?:0,item))"确认消耗1份；按敏捷顺序恢复HP，占用一次行动" else "此场景的物品逻辑未接入或角色状态不一致"
    }
    private fun drawBattleItems(c:Canvas,current:OpeningBattle){
        val l=battleItemLayout();val dp=resources.displayMetrics.density;c.drawColor(Color.BLACK)
        val scene=battleMedicineScene()
        touchText(c,if(scene==null)"战斗物品"else"战斗物品 · 选择不消耗，使用才提交",
            Box(l.frame.x+8*dp,l.frame.y+8*dp,l.close.x-l.frame.x-16*dp,1f),15f)
        if(scene==null)touchText(c,"点列表查看 · 使用才提交",Box(l.frame.x+8*dp,l.frame.y+l.close.h+12*dp,l.frame.w-16*dp,1f),12f)
        touchButton(c,l.close,"关闭")
        if(current.party.size>1&&selectedBattleItem?.let{content.itemDefinitions[it]?.battleBindingUse}==null)for(player in current.party){
            val box=battleItemTargetBounds(player.id)
            if(scene==null)touchButton(c,box,"${if(battleItemTarget()?.id==player.id)"✓ " else ""}${heroName(player.id)} · HP ${player.hp}/${player.maxHp}")
            else{
                touchButton(c,box,"",selected=battleItemTarget()?.id==player.id)
                val font=resources.configuration.fontScale
                battleLine(c,heroName(player.id),Box(box.x+8*dp,box.y+4*dp,box.w-16*dp,max(24f,17*font)*dp),14f)
                battleLine(c,"HP ${player.hp}/${player.maxHp}",Box(box.x+8*dp,box.y+box.h-max(18f,14*font)*dp-4*dp,
                    box.w-16*dp,max(18f,14*font)*dp),12f)
            }
        }
        c.save();c.clipRect(l.list.x,l.list.y,l.list.x+l.list.w,l.list.y+l.list.h)
        for((i,id) in battleMedicines().withIndex()){
            val row=l.row(i,battleItemListScroll);overlayPaint.color=if(id==selectedBattleItem)0xff244d49.toInt() else 0xff18252e.toInt()
            c.drawRect(row.x,row.y,row.x+row.w,row.y+row.h,overlayPaint)
            val count=if(id==HerbUse.ID)battleHerbCount() else inventory[id]?:0
            touchText(c,"${content.itemNames[id]?:id} ×$count",Box(row.x+8*dp,row.y+8*dp,row.w-16*dp,1f),14f)
        };c.restore()
        val id=selectedBattleItem;val item=id?.let{content.itemDefinitions[it]}
        val target=battleItemTarget()
        val binding=item?.battleBindingUse!=null
        val enabled=item!=null&&if(binding)current.bindingAvailable(inventory[item.id]?:0,item,flags["rom.inventory.special.${item.originalId}.used"]==true)
            else target!=null&&current.herbAvailable(target.id,inventory[HerbUse.ID]?:0,item)
        val allLines=if(id==null)listOf("选择物品后查看效果与合法目标", "只浏览、取消或滑动不会消耗物品") else if(binding)listOf(
            "${current.inputHero?.id?.let{heroName(it)}?:"当前没有合法角色"} · 本场${item?.battleBindingUse?.targetLabel}",
            "困住${item?.battleBindingUse?.targetLabel}，解除原伤害保护", "使用耗本次行动；数量保留",battleMedicineReason(id),
            "取消无副作用；敌人按原顺序行动")else listOf(
            target?.let{"${heroName(it.id)} · HP ${it.hp}/${it.maxHp}"}?:"当前没有合法目标",
            if(id==HerbUse.ID)"HP +50 · 不超过上限" else "效果尚未实现",
            if(id==HerbUse.ID)"满HP仍消耗1份" else "当前不能使用",
            battleMedicineReason(id),"取消不消耗；敌人按顺序行动")
        val lines=if(scene!=null&&id!=null&&!binding)allLines.drop(1) else allLines
        c.save();c.clipRect(l.detail.x,l.detail.y,l.detail.x+l.detail.w,l.detail.y+l.detail.h)
        var y=l.detail.y-battleItemDetailScroll
        for(line in lines)y+=touchText(c,line,Box(l.detail.x,y,l.detail.w,1f),14f)+4*dp
        if(battleNotice.isNotEmpty())y+=touchText(c,battleNotice,Box(l.detail.x,y,l.detail.w,1f),14f)+4*dp
        battleItemDetailScroll=battleItemDetailScroll.coerceAtMost(max(0f,y+battleItemDetailScroll-l.detail.y-l.detail.h));c.restore()
        touchButton(c,l.primary,if(id==null)"先选择物品" else if(binding)"对${item?.battleBindingUse?.targetLabel}使用"else target?.let{"使用于${heroName(it.id)}"}?:"没有合法目标",enabled)
    }
    private fun drawBattleInformation(c:Canvas,current:OpeningBattle){
        if(battleInfoHeroId!=null){drawBattlePartyInformation(c,current);return}
        val l=battleInfoLayout();val dp=resources.displayMetrics.density
        c.drawColor(Color.BLACK)
        touchText(c,"敌人信息",Box(l.frame.x+8*dp,l.frame.y+8*dp,l.close.x-l.frame.x-16*dp,1f),15f)
        touchText(c,"上下滑动查看 · 不消耗行动",Box(l.frame.x+8*dp,l.frame.y+l.close.h+12*dp,l.frame.w-16*dp,1f),12f)
        touchButton(c,l.close,"关闭")
        c.save();c.clipRect(l.list.x,l.list.y,l.list.x+l.list.w,l.list.y+l.list.h)
        for((i,enemy) in current.enemies.withIndex()){val row=l.row(i,battleInfoListScroll)
            overlayPaint.color=if(enemy.slot==selectedBattleSlot)0xff244d49.toInt() else 0xff18252e.toInt()
            c.drawRect(row.x,row.y,row.x+row.w,row.y+row.h,overlayPaint)
            touchText(c,enemy.definition.name+" ${enemy.slot+1}",Box(row.x+6*dp,row.y+4*dp,row.w-12*dp,1f),12f)
            gauge(c,Box(row.x+6*dp,row.y+row.h-9*dp,row.w-12*dp,5*dp),battleVisibleHp(enemy.slot)?:0,enemy.definition.hp,0xffc55758.toInt())
        };c.restore()
        val enemy=current.enemies.firstOrNull{it.slot==selectedBattleSlot}?:current.enemies.first()
        val lines=listOf(enemy.definition.name+" · 实例 ${enemy.slot+1}","HP ${battleVisibleHp(enemy.slot)}/${enemy.definition.hp}",
            "攻击 ${enemy.definition.attack} · 防御 ${enemy.definition.defense}",
            content.battle?.enemyAgility?.get(enemy.definition.id)?.let{"敏捷 $it"}?:"敏捷数据未接入",
            if(enemy.definition.id in 4..7)"名称有来源暂定；属性来自现有数据" else "HP/属性显示为移动端信息增强")
        c.save();c.clipRect(l.detail.x,l.detail.y,l.detail.x+l.detail.w,l.detail.y+l.detail.h)
        var y=l.detail.y-battleInfoScroll
        for(line in lines)y+=touchText(c,line,Box(l.detail.x,y,l.detail.w,1f),14f)+4*dp
        battleInfoScroll=battleInfoScroll.coerceAtMost(max(0f,y+battleInfoScroll-l.detail.y-l.detail.h));c.restore()
    }
    private fun drawBattlePartyInformation(c:Canvas,current:OpeningBattle){
        val l=battleInfoLayout();val dp=resources.displayMetrics.density
        c.drawColor(Color.BLACK);overlayPaint.alpha=255
        battleLine(c,"队员信息",Box(l.frame.x+8*dp,l.frame.y+8*dp,l.close.x-l.frame.x-16*dp,l.close.h),15f)
        touchButton(c,l.close,"关闭")
        c.save();c.clipRect(l.list.x,l.list.y,l.list.x+l.list.w,l.list.y+l.list.h)
        for((i,hero)in current.party.withIndex()){
            val row=l.row(i,battleInfoListScroll)
            overlayPaint.color=if(hero.id==battleInfoHeroId)0xff244d49.toInt()else 0xff18252e.toInt()
            c.drawRect(row.x,row.y,row.x+row.w,row.y+row.h,overlayPaint)
            val icon=Box(row.x+6*dp,row.y+6*dp,24*dp,24*dp);portrait(c,hero,icon)
            battleLine(c,heroName(hero.id),Box(icon.x+30*dp,row.y+4*dp,row.w-42*dp,row.h-8*dp),13f)
        };c.restore()
        val hero=current.party.firstOrNull{it.id==battleInfoHeroId}?:return
        val view=battlePartyView(hero,heroName(hero.id),battlePresentation.action,current.inputHero?.id,current.hero.id)
        val lines=listOf("${view.name} · 等级 ${view.level}","HP ${view.hp}/${view.maxHp}","MP ${view.mp}/${view.maxMp?:"上限待接入"}",
            "状态 ${OriginalStatus.label(view.status)}",growthProgress(hero).summary,"累计经验 ${hero.experience}",
            "攻击 ${hero.strength+equipmentBonus(hero,"rightHand")} · 防御 ${hero.stamina+equipmentBonus(hero,"body")}",
            "敏捷 ${hero.agility} · 精神 ${hero.spirit}","只读当前行动，不消耗物品或行动")
        c.save();c.clipRect(l.detail.x,l.detail.y,l.detail.x+l.detail.w,l.detail.y+l.detail.h)
        var y=l.detail.y-battleInfoScroll
        for(line in lines)y+=touchText(c,line,Box(l.detail.x,y,l.detail.w,1f),14f)+4*dp
        battleInfoScroll=battleInfoScroll.coerceAtMost(max(0f,y+battleInfoScroll-l.detail.y-l.detail.h));c.restore()
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
        val choices=arrayOf("显示：${mode.name}（全屏 / 原版比例 / 整数裁切）","开发者调试层：$debug","触觉反馈：$haptic","检查应用更新","封神云存档 / 登录","查看设备与开发范围","摇杆/按钮参数 JSON","恢复默认控件","回到初始位置（仅调试）","声音与诊断上传（${if(Diagnostics.enabled)"上传开启" else "上传关闭"}）","存档 / 回档")
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
                10->child=10
            }
        }.setNegativeButton("返回",null).setOnDismissListener{
            modalDialog=null
            when(child){
                3->{returnToMenu();activity.checkForUpdates()}
                4->{returnToMenu();activity.openCloudSave()}
                5->showInfo()
                6->editControls()
                9->audioSettings()
                10->openSaveHistory()
                else->returnToMenu()
            }
        }.show()
    }
    private fun historyLabel(e:SaveHistoryEntry):String {
        val time=java.text.SimpleDateFormat("MM-dd HH:mm:ss",java.util.Locale.getDefault()).format(java.util.Date(e.timeMillis))
        val heroes=e.snapshot.characters.joinToString(" / "){"${heroName(it.id)} Lv.${it.level}"}
        return "${e.kind.label} · $time\n地图 ${e.snapshot.mapId} · $heroes"
    }
    private fun openSaveHistory(){
        layer=Layer.SETTINGS;input.clear();clearUxGesture();menuTouch.clear();clock.reset()
        val entries=runCatching{SaveHistory.parse(savePrefs.getString(SaveHistory.KEY,null))}.getOrElse{
            reportHistoryError("invalid_history");modalDialog=AlertDialog.Builder(activity).setTitle("存档 / 回档")
                .setMessage("历史记录无法读取，当前进度和原记录已保留。")
                .setPositiveButton("返回",null).setOnDismissListener{modalDialog=null;returnToMenu()}.show();return
        }
        var selected:String?=null;var manual=false
        val builder=AlertDialog.Builder(activity).setTitle("存档 / 回档 · 最新20档")
        if(entries.isEmpty())builder.setMessage("还没有历史存档。当前自动续玩存档保持不变。")
        else builder.setItems(entries.map{historyLabel(it)}.toTypedArray()){_,i->selected=entries[i].id}
        modalDialog=builder.setNeutralButton("手动存档"){_,_->manual=true}.setNegativeButton("返回",null)
            .setOnDismissListener{
                modalDialog=null;returnToMenu()
                if(manual){
                    val success=saveHistoryResult();showNotice(if(success)"手动存档已保存" else "存档失败，当前进度已保留")
                    openSaveHistory()
                }else selected?.let{id->confirmHistoryRestore(id,entries.single{it.id==id})}
            }.show()
    }
    private fun confirmHistoryRestore(id:String,entry:SaveHistoryEntry){
        layer=Layer.SETTINGS;input.clear();clock.reset()
        var success=false
        modalDialog=AlertDialog.Builder(activity).setTitle("确认回档")
            .setMessage("${historyLabel(entry)}\n\n回档前会保存当前完整进度。云同步沿用当前设置。")
            .setNegativeButton("取消",null).setPositiveButton("回档",null)
            .setOnDismissListener{modalDialog=null;returnToMenu();if(success){closeMenu();showNotice("回档成功，已保存")}}.create()
        val dialog=modalDialog!!;dialog.show()
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener{
            success=restoreHistoryResult(id)
            if(success)dialog.dismiss()else dialog.setMessage("回档失败，当前进度和历史记录已保留。请取消后检查存档。")
        }
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
