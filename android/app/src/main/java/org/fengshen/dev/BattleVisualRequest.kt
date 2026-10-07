package org.fengshen.dev

/** Copy identities before asynchronous preparation; no mutable battle or world is retained. */
class BattleVisualRequest(actorIds:Collection<String>,enemyIds:Collection<Int>,
    val mapId:Int,val blackScene:Boolean) {
    val actorIds:Set<String> =actorIds.toSet()
    val enemyIds:Set<Int> =enemyIds.toSet()
}

/** Exact reviewed mappings only. Unknown identities keep their existing native fallback. */
internal class BattleVisualSelection(private val actors:Map<String,List<String>>,
    private val enemies:Map<Int,String>,private val backgrounds:Map<String,String>,
    private val maps:Map<Int,String>) {
    fun battle(request:BattleVisualRequest):Set<String> {
        val names=linkedSetOf<String>()
        for(id in request.actorIds)names.addAll(actors[id].orEmpty())
        for(id in request.enemyIds)enemies[id]?.let{names.add(it)}
        val background=if(request.blackScene)"cave" else maps[request.mapId]
        backgrounds[background]?.let{names.add(it)}
        return names.toSet()
    }
}
