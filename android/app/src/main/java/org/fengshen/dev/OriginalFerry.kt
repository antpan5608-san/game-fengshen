package org.fengshen.dev

/** Two original fixed ferry scripts. No free sea movement or inventory reward. */
data class FerryLeg(val mapId:Int,val x:Int,val y:Int,val direction:Key)
data class FerryDefinition(val id:String,val eventId:Int,val start:FerryLeg,val contactX:Int,val contactY:Int,
    val legs:List<FerryLeg>,val spriteAsset:String,val evidence:String) {
    val pendingFlag get()="rom.event.$eventId.ferry.pending"
    val failedFirstFlag get()="rom.event.$eventId.ferry.first-step-failed"
    fun stageFlag(i:Int)="rom.event.$eventId.ferry.step.$i"
    fun stage(flags:Map<String,Boolean>):Int? {
        if(flags[pendingFlag]!=true)return null
        val used=legs.indices.map{flags[stageFlag(it)]==true}
        val n=used.takeWhile{it}.size
        if(used.drop(n).any{it}||flags.any{(k,v)->v&&k.startsWith("rom.event.$eventId.ferry.step.")&&
            k !in legs.indices.map{stageFlag(it)}})return null
        return n
    }
    fun position(flags:Map<String,Boolean>):FerryLeg? {
        val n=stage(flags)?:return null
        if(n==1&&flags[failedFirstFlag]==true&&eventId==45)return FerryLeg(16,10,3,Key.LEFT)
        return if(n==0)start else legs[n-1]
    }
}

object OriginalFerry {
    const val PARKED_FLAG="rom.transport.ferry.island.parked"
    data class Result(val snapshot:SaveSnapshot,val applied:Boolean=false,val error:String?=null)
    fun originalLegs(event:Int):List<FerryLeg> = when(event){
        45->listOf(FerryLeg(16,146,148,Key.UP))+
            (147 downTo 142).map{FerryLeg(16,146,it,Key.UP)}+
            (147..150).map{FerryLeg(16,it,142,Key.RIGHT)}+
            (141 downTo 136).map{FerryLeg(16,150,it,Key.UP)}+FerryLeg(16,150,135,Key.UP)
        46->listOf(FerryLeg(16,150,136,Key.DOWN))+
            (137..142).map{FerryLeg(16,150,it,Key.DOWN)}+
            (149 downTo 146).map{FerryLeg(16,it,142,Key.LEFT)}+
            (143..148).map{FerryLeg(16,146,it,Key.DOWN)}+FerryLeg(4,11,3,Key.RIGHT)
        else->emptyList()
    }
    fun verified(rule:FerryDefinition)=rule.eventId in setOf(45,46)&&rule.id=="rom.ferry.${rule.eventId}"&&
        rule.evidence=="game-data/provenance/world-ferry-original.json"&&rule.spriteAsset=="actor-218-ferry.png"&&
        rule.legs==originalLegs(rule.eventId)&&
        rule.start==(if(rule.eventId==45)FerryLeg(4,11,3,Key.LEFT)else FerryLeg(16,150,135,Key.DOWN))&&
        (rule.contactX to rule.contactY)==(if(rule.eventId==45)10 to 3 else 150 to 136)
    fun pending(flags:Map<String,Boolean>,definitions:Collection<FerryDefinition>):FerryDefinition?=
        definitions.singleOrNull{flags[it.pendingFlag]==true}
    fun matchesContact(before:SaveSnapshot,rule:FerryDefinition,key:Key)=
        before.mapId==rule.start.mapId&&before.x==rule.start.x*16+8&&before.y==rule.start.y*16+8&&
        before.terrainMode==0&&before.interiorContext==null&&key==rule.start.direction&&
        (rule.eventId!=46||before.flags[PARKED_FLAG]==true)
    fun validPending(before:SaveSnapshot,rules:Collection<FerryDefinition>):Boolean {
        val active=rules.filter{before.flags[it.pendingFlag]==true}
        if(active.isEmpty())return true
        if(active.size!=1)return false
        val rule=active.single();if(!verified(rule))return false;val stage=rule.stage(before.flags)?:return false
        val p=rule.position(before.flags)?:return false
        if(before.terrainMode!=0||before.interiorContext!=null||before.mapId!=p.mapId||before.x!=p.x*16+8||before.y!=p.y*16+8||before.direction!=p.direction)return false
        if(before.flags[rule.failedFirstFlag]==true&&(rule.eventId!=45||stage!=1||!OriginalStatus.allDisabled(before.characters)))return false
        return stage<rule.legs.size||OriginalStatus.allDisabled(before.characters)
    }
    fun begin(before:SaveSnapshot,rule:FerryDefinition,key:Key,rules:Collection<FerryDefinition>):Result {
        if(rule !in rules||!verified(rule)||!matchesContact(before,rule,key)||
            rules.any{before.flags[it.pendingFlag]==true}||OriginalStatus.allDisabled(before.characters)||
            before.flags[rule.failedFirstFlag]==true||rule.legs.indices.any{before.flags[rule.stageFlag(it)]==true})
            return Result(before,error="原版渡船条件或状态已变化")
        return Result(before.copy(direction=key,flags=before.flags+(rule.pendingFlag to true)),true)
    }
    fun advance(before:SaveSnapshot,rule:FerryDefinition,expectedStage:Int,rules:Collection<FerryDefinition>):Result {
        if(!validPending(before,rules)||pending(before.flags,rules)!=rule||rule.stage(before.flags)!=expectedStage||
            expectedStage !in rule.legs.indices||OriginalStatus.allDisabled(before.characters))
            return Result(before,error="渡船阶段已变化，不能重复提交")
        val destination=rule.legs[expectedStage]
        // Every traced completed step applies the existing status command once.
        // The script controls transport: no field encounter or random byte is consumed.
        val party=OriginalStatus.step(before.characters,before.mapId)
        val dead=OriginalStatus.allDisabled(party)
        var flags=before.flags+(rule.stageFlag(expectedStage) to true)
        var p=destination
        var steps=(before.encounterSteps+1) and 255
        if(rule.eventId==45&&expectedStage==0){
            if(dead){p=FerryLeg(16,10,3,Key.LEFT);flags=flags+(rule.failedFirstFlag to true)}
            else {steps=0;flags=flags-WorldItems.FIELD_PENDING_FLAG}
        }
        if(rule.eventId==45&&expectedStage==16)flags=flags+(PARKED_FLAG to true)
        if(rule.eventId==46&&expectedStage==17){steps=if(dead)1 else 0;flags=flags-WorldItems.FIELD_PENDING_FLAG}
        if(expectedStage==17&&!dead){
            steps=0;flags=(flags+(PARKED_FLAG to (rule.eventId==45)))-rule.pendingFlag-rule.failedFirstFlag
            for(i in rule.legs.indices)flags=flags-rule.stageFlag(i)
        }
        return Result(before.copy(mapId=p.mapId,x=p.x*16+8,y=p.y*16+8,direction=p.direction,
            characters=party,flags=flags,encounterSteps=steps,interiorContext=null),true)
    }
    /** Only the exact durable script point is exempted, never other water or walls. */
    fun sceneView(base:Scene,flags:Map<String,Boolean>,rules:Collection<FerryDefinition>):Scene {
        var result=base
        if(base.mapId==16&&base.width>150&&base.height>135&&rules.any{verified(it)}){
            val dock=135*base.width+150
            if(base.collision[dock]==25)result=base.copy(enabled=base.enabled+dock,transitionCells=base.transitionCells+dock)
        }
        val rule=pending(flags,rules)?:return result;if(!verified(rule))return result;val p=rule.position(flags)?:return result
        if(p.mapId!=base.mapId||p.x !in 0 until base.width||p.y !in 0 until base.height)return result
        val cell=p.y*base.width+p.x
        return result.copy(enabled=result.enabled+cell,transitionCells=result.transitionCells+cell,
            dynamicObjectCells=result.dynamicObjectCells-cell)
    }
}
