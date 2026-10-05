package org.fengshen.dev

/** Original B9D2/F598 selection before a new map header is loaded.
 * A retained inventory row with its used bit, not historical ownership or
 * a disease flag, selects the alternative. Restore keeps the saved map ID.
 */
data class OriginalMapArrival(val requestedMapId:Int,val actualMapId:Int,
    val itemId:String,val partyCountBelow:Int,val evidence:String) {
    fun verified()=requestedMapId==171&&actualMapId==172&&itemId=="rom.special.0"&&
        partyCountBelow==4&&evidence==EVIDENCE
    fun resolve(requested:Int,partyCount:Int,inventory:Map<String,Int>,flags:Map<String,Boolean>):Int {
        if(!verified()||requested!=requestedMapId||partyCount !in 1 until partyCountBelow)return requested
        return if(inventory.containsKey(itemId)&&flags["rom.inventory.special.0.used"]==true)actualMapId else requested
    }
    companion object {const val EVIDENCE="game-data/provenance/world-master172-resources.json"}
}
