package org.fengshen.dev

/** Pure decisions from the target ROM's scoped two- and three-actor battle paths.
 * Values use original actor identity and enemy slot, never an enemy definition ID.
 * This object neither samples randomness nor changes HP, inventory, EXP or saves.
 * See game-data/provenance/world-two-party.json for source spans and limits. */
object OriginalPartyRules {
    /** 1:8B0E..8B47 / 9:927C..92A8: bit40 actors retain their original
     * record and HP but are omitted from the active battle identities. */
    fun present(character:CharacterState)=character.statusMask and 64==0
    fun battleCharacters(roster:List<CharacterState>):List<CharacterState> {
        require(roster.size in 1..3&&roster.map{it.id}.distinct().size==roster.size)
        return roster.filter(::present)
    }
    fun restoreBattleCharacters(roster:List<CharacterState>,active:List<CharacterState>):List<CharacterState> {
        require(active.map{it.id}.distinct().size==active.size)
        require(active.map{it.id}.toSet()==battleCharacters(roster).map{it.id}.toSet())
        val changed=active.associateBy{it.id}
        return roster.map{(changed[it.id]?:it).let{hero->hero.copy(statusMask=hero.statusMask and 247)}}
    }
    data class Actor(val originalActorIndex:Int,val partySlot:Int,val hp:Int,val status:Int,
                     val agility:Int,val command:Int=0) {
        init {
            require(originalActorIndex in 0..2 && partySlot in 0..2)
            require(hp in 0..65535 && status in 0..255 && agility in 0..255 && command in 0..255)
        }
    }
    data class Enemy(val slot:Int,val hp:Int,val agility:Int) {
        init { require(slot in 0..6 && hp in 0..65535 && agility in 0..255) }
    }
    data class Recovery(val statusByActorIndex:Map<Int,Int>,val randomByte:Int)

    private fun checkActors(actors:List<Actor>) {
        require(actors.size in 1..3)
        require(actors.map{it.originalActorIndex}.distinct().size==actors.size)
        require(actors.map{it.partySlot}.sorted()==actors.indices.toList())
    }
    private fun checkEnemies(enemies:List<Enemy>) {
        require(enemies.map{it.slot}.distinct().size==enemies.size)
    }
    private fun checkByte(value:Int) { require(value in 0..255) }

    // 9:B68E / 81EF: input eligibility and action-time HP recheck are distinct.
    fun collectsCommand(actor:Actor):Boolean = actor.status and 0x38==0
    fun canAct(actor:Actor):Boolean = actor.hp>0 && collectsCommand(actor)
    /** 9:A63A/A66A: all HP-zero OR every present party slot carries bit10.
     * A mixed dead/bit10 party does not satisfy the latter original branch. */
    fun defeated(actors:List<Actor>):Boolean {
        checkActors(actors)
        return actors.all{it.hp==0}||actors.all{it.status and OriginalStatus.STATUS_BIT16!=0}
    }

    /** Original scheduler encoding: actorIndex or (0x80 | enemySlot).
     * Stable descending agility; numeric command 3 precedes ordinary actions.
     * Command-3 actors retain original actor order even at different agility.
     * HP/status filtering happens at execution, not while sorting. */
    fun actionOrder(actors:List<Actor>,enemies:List<Enemy>):List<Int> {
        checkActors(actors);checkEnemies(enemies)
        val initial=actors.sortedBy{it.originalActorIndex}.map{it.originalActorIndex}+
            enemies.sortedBy{it.slot}.map{0x80 or it.slot}
        val byActor=actors.associateBy{it.originalActorIndex}
        val byEnemy=enemies.associateBy{it.slot}
        fun priority(id:Int)=id<0x80 && byActor.getValue(id).command==3
        fun agility(id:Int)=if(id<0x80)byActor.getValue(id).agility else byEnemy.getValue(id and 7).agility
        return initial.sortedWith(Comparator { left,right ->
            val lp=priority(left);val rp=priority(right)
            when {
                lp && rp -> 0
                lp -> -1
                rp -> 1
                else -> agility(right).compareTo(agility(left))
            }
        })
    }

    /** Ordinary single-target enemy path (9:8EB2, $693E=0).
     * The caller supplies the SAME byte used for this enemy's AI and hit check.
     * Result is an original actor index, not a transient party-list index. */
    fun enemyTarget(actors:List<Actor>,randomByte:Int):Int? {
        checkActors(actors);checkByte(randomByte)
        val bySlot=actors.associateBy{it.partySlot}
        var candidate=(randomByte ushr 2) and 3
        if(candidate>=actors.size)candidate-=actors.size
        for(slot in (candidate until actors.size)+(0 until actors.size)) {
            val actor=bySlot.getValue(slot)
            if(actor.hp>0)return actor.originalActorIndex
        }
        return null
    }

    /** 9:A5B2: preserve a live target; otherwise use the first live ENEMY in
     * the existing agility order. A definition ID is deliberately not accepted. */
    fun retargetEnemy(selectedSlot:Int,enemies:List<Enemy>,order:List<Int>):Int? {
        require(selectedSlot in 0..6);checkEnemies(enemies)
        require(order.distinct().size==order.size)
        require(order.all{it in 0..3 || it in 0x80..0x86})
        require(enemies.all{(0x80 or it.slot) in order})
        val bySlot=enemies.associateBy{it.slot}
        if((bySlot[selectedSlot]?.hp?:0)>0)return selectedSlot
        return order.firstOrNull{it>=0x80 && (bySlot[it and 7]?.hp?:0)>0}?.and(7)
    }

    /** 9:AE12: sum the original word rewards, share among present HP-positive
     * actors, discard division remainder. Status alone does not exclude them.
     * A no-survivor input safely returns no grants; it is not a valid victory. */
    fun experienceShares(enemyExperience:List<Int>,actors:List<Actor>):Map<Int,Int> {
        checkActors(actors)
        require(enemyExperience.size<=7 && enemyExperience.all{it in 0..65535})
        val eligible=actors.filter{it.hp>0}.sortedBy{it.partySlot}
        if(eligible.isEmpty())return emptyMap()
        val total=enemyExperience.fold(0){sum,xp -> (sum+xp) and 65535}
        val share=total/eligible.size
        return eligible.associate{it.originalActorIndex to share}
    }

    /** 9:A69F: call once after a completed scheduler cycle, not after each action.
     * Each positive active ID's B196 mapping leaves carry=1 for its ROR.
     * Actors share the sequentially rotated byte; recovery clears ALL status bits. */
    fun recoverStatuses(actors:List<Actor>,randomByte:Int):Recovery {
        checkActors(actors);checkByte(randomByte)
        var sharedByte=randomByte
        val statuses=linkedMapOf<Int,Int>()
        for(actor in actors.sortedBy{it.partySlot}) {
            var status=actor.status
            if(status and 0x0c!=0) {
                sharedByte=(sharedByte ushr 1) or 0x80
                if(sharedByte and 1==0)status=0
            }
            statuses[actor.originalActorIndex]=status
        }
        return Recovery(statuses,sharedByte)
    }

    /** 9:8528: 36 threshold bytes per owner, three 12-row blocks.
     * rawLevel is zero-based as stored in the cartridge. Missing owner data is
     * an error; a different actor's table is never used as a fallback. */
    fun physicalMultiplier(originalActorIndex:Int,rawLevel:Int,randomByte:Int,
                           thresholdsByOwner:Map<Int,List<Int>>):Int {
        require(originalActorIndex in 0..2);checkByte(rawLevel);checkByte(randomByte)
        val table=requireNotNull(thresholdsByOwner[originalActorIndex]){"Missing actor multiplier table"}
        require(table.size==36 && table.all{it in 0..255})
        val row=(rawLevel.coerceAtMost(79)/5).coerceAtMost(11)
        return (0..2).firstOrNull{randomByte<table[row+12*it]}?.plus(1)?:4
    }
}
