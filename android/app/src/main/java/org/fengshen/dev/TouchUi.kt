package org.fengshen.dev

import kotlin.math.*

/** Geometry only for the authorized item/equipment/shop modals, not a UI framework. */
data class TouchModalLayout(val frame:Box,val close:Box,val back:Box,val tabs:List<Box>,
    val party:List<Box>,val list:Box,val detail:Box,val primary:Box,val secondary:Box,
    val rowHeight:Float,val wide:Boolean,val compactHeader:Boolean) {
    fun row(index:Int,offset:Float)=Box(list.x,list.y+index*rowHeight-offset,list.w,rowHeight)
    fun visibleRow(index:Int,offset:Float):Box {
        val b=row(index,offset);val top=max(b.y,list.y);val bottom=min(b.y+b.h,list.y+list.h)
        return Box(b.x,top,b.w,max(0f,bottom-top))
    }
    fun maxScroll(count:Int)=max(0f,count*rowHeight-list.h)
}
fun touchModalLayout(safe:Box,dp:Float,fontScale:Float,tabs:Int,party:Int,secondary:Boolean):TouchModalLayout {
    val pad=8*dp;val f=Box(safe.x+pad,safe.y+pad,max(1f,safe.w-2*pad),max(1f,safe.h-2*pad))
    val regularHeader=max(72f,48*fontScale+16)*dp;val tabH=max(48f,22*fontScale+16)*dp
    val actionH=max(48f,17.5f*fontScale+16)*dp
    // Three-character party names need their scaled text width, not a fixed48dp square.
    val partyW=if(party>1)min(max(48f,44*fontScale+16)*dp,(f.w-2*pad-(party-1)*pad)/party)else 48*dp
    val partyLines=ceil(42*fontScale/max(1f,partyW/dp-16))
    val partyH=if(party>1)max(actionH,(17.5f*fontScale*partyLines+16)*dp)else 0f
    val compact=f.h-regularHeader-partyH-tabH-3*pad < actionH+(17.5f*fontScale+8)*dp
    val header=if(compact)max(72f,18.75f*fontScale+16)*dp else regularHeader
    val closeW=max(48f,28*fontScale+16)*dp
    val ts=(0 until tabs).map{Box(f.x+pad+it*(f.w-2*pad)/tabs,f.y+header+partyH,(f.w-2*pad)/tabs,tabH)}
    val ps=(0 until party).map{Box(f.x+pad+it*(partyW+pad),f.y+header,partyW,partyH)}
    val y=f.y+header+partyH+tabH+pad;val h=max(48*dp,f.y+f.h-pad-y)
    val wide=f.w>=560*dp;val listW=if(wide)(f.w-3*pad)*.46f else f.w-2*pad
    val list=Box(f.x+pad,y,listW,h);val rx=if(wide)list.x+list.w+pad else list.x
    val rw=if(wide)f.x+f.w-pad-rx else listW;val ah=actionH
    val bw=if(secondary)(rw-pad)/2 else rw
    val p=Box(rx,y+h-ah,bw,ah);val s=Box(rx+bw+pad,p.y,bw,ah)
    val d=Box(rx,y,rw,max(1f,p.y-pad-y))
    return TouchModalLayout(f,Box(f.x+f.w-pad-closeW,f.y+pad,closeW,actionH),
        Box(f.x+pad,f.y+pad,closeW,actionH),ts,ps,list,d,p,s,max(72f,80*fontScale)*dp,wide,compact)
}

/** Identity captured at DOWN; row indices and delayed callbacks never own transactions. */
data class ModalCommand(val kind:String,val itemId:String?=null,val targetId:String?=null,
    val slot:String?=null,val shopId:String?=null,val mode:String?=null)
data class ModalGesture(val pointer:Int,val x:Float,val y:Float,var lastY:Float,
    var command:ModalCommand?,val revision:Int,val state:String,val scrollArea:Int,var dragged:Boolean=false)
