package org.fengshen.dev

/** Target ROM tileset 4: $6815 selects the lower/upper terrain plane.
 * No transport or flying rule is inferred from these two witnessed modes. */
data class TerrainDecision(val block:MovementBlock,val nextMode:Int,val suppressEncounter:Boolean=false)
object OriginalTerrain {
    const val PALACE=4
    fun supported(profile:Int?,mode:Int)=mode==0||(profile==PALACE&&mode==1)
    fun standing(profile:Int?,collision:Int,mode:Int):Boolean {
        if(profile!=PALACE)return mode==0
        return when(mode){
            0->collision in setOf(0,2,4,5,6,7,8)
            1->collision in setOf(3,5,6,7,8)
            else->false
        }
    }
    fun step(profile:Int?,source:Int,target:Int,key:Key,mode:Int):TerrainDecision {
        require(profile==PALACE&&mode in 0..1)
        if(source !in 0..8||target !in 0..8)return TerrainDecision(MovementBlock.DEVELOPMENT,mode)
        // D132..D150: the stair's vertical departure selects its terrain plane.
        if(source==8){
            if(key!=Key.UP&&key!=Key.DOWN)return TerrainDecision(MovementBlock.PHYSICAL,mode)
            return TerrainDecision(MovementBlock.NONE,if(key==Key.UP)0 else 1)
        }
        if(mode==0){
            if(target==1||target==3||source in setOf(4,7)&&key in setOf(Key.LEFT,Key.RIGHT))
                return TerrainDecision(MovementBlock.PHYSICAL,mode)
            return TerrainDecision(MovementBlock.NONE,mode,source==2||source==5&&key==Key.LEFT||source==6&&key==Key.RIGHT)
        }
        if(target !in setOf(3,5,6,7,8))return TerrainDecision(MovementBlock.PHYSICAL,mode)
        return TerrainDecision(MovementBlock.NONE,mode,source in setOf(5,6,7))
    }
}
