package org.fengshen.dev

/** Only the existing object's stable identity and interaction pose, without Bitmap/live flags. */
internal data class NavigationObject(val id:String,val mapId:Int,val x:Int,val y:Int,
    val interactionCell:Pair<Int,Int>?=null,val interactionDirection:Key?=null,val service:Boolean=false) {
    fun standsAt(px:Int,py:Int)=interactionCell?.let{it==(px to py)}?:
        (kotlin.math.abs(x-px)+kotlin.math.abs(y-py)==1)
    fun facingAt(px:Int,py:Int):Key? {
        if(!standsAt(px,py))return null
        if(service)return Key.UP // Original shop/clinic/inn openNpc faces up.
        return if(interactionCell==(px to py)&&interactionDirection!=null)interactionDirection
            else facingToward(px,py,NpcCell(id,x,y))
    }
    fun cells():Set<Pair<Int,Int>> {
        val candidates=interactionCell?.let{listOf(it)}?:listOf(x to y-1,x-1 to y,x to y+1,x+1 to y)
        return candidates.filter{facingAt(it.first,it.second)!=null}.toSet()
    }
}
