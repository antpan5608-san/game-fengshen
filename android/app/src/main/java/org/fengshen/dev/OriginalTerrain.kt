package org.fengshen.dev

/** Target ROM tileset 4: $6815 selects the lower/upper terrain plane.
 * No transport or flying rule is inferred from these two witnessed modes. */
data class TerrainDecision(val block:MovementBlock,val nextMode:Int,val suppressEncounter:Boolean=false)
object OriginalTerrain {
    const val PALACE=4
    const val CAVE_GROUND=3
    private val caveGroundClasses=setOf(0,1,2,3,4,5,6,7,8,9,10,11,13,14,17,18,19,20,21,23)
    private val caveUpperClasses=setOf(10,11,13,14,17,18,23)
    fun supported(profile:Int?,mode:Int)=mode==0||((profile==PALACE||profile==CAVE_GROUND)&&mode==1)
    fun standing(profile:Int?,collision:Int,mode:Int):Boolean {
        if(profile==CAVE_GROUND)return when(mode){
            0->collision in caveGroundClasses&&collision !in setOf(1,14)
            1->collision in caveUpperClasses
            else->false
        }
        if(profile!=PALACE)return mode==0
        return when(mode){
            0->collision in setOf(0,2,4,5,6,7,8)
            1->collision in setOf(3,5,6,7,8)
            else->false
        }
    }
    fun step(profile:Int?,source:Int,target:Int,key:Key,mode:Int):TerrainDecision {
        if(profile==CAVE_GROUND){
            require(mode in 0..1)
            if(source !in caveGroundClasses||target !in caveGroundClasses)return TerrainDecision(MovementBlock.DEVELOPMENT,mode)
            // Original source23 vertical stair/portal branch precedes the target wall check.
            if(source==23){
                if(key !in setOf(Key.UP,Key.DOWN))return TerrainDecision(MovementBlock.PHYSICAL,mode)
                return TerrainDecision(MovementBlock.NONE,if(target==14)1 else 0)
            }
            if(mode==1){
                // CC92/D099: bridge-state transitions (20..22) remain outside this scope.
                if(source in setOf(20,21))return TerrainDecision(MovementBlock.DEVELOPMENT,mode)
                return TerrainDecision(if(source in caveUpperClasses&&target in caveUpperClasses)
                    MovementBlock.NONE else MovementBlock.PHYSICAL,mode)
            }
            val blocked=when(source){
                2->key==Key.LEFT
                3->key==Key.RIGHT
                5->key in setOf(Key.DOWN,Key.LEFT)
                6->key==Key.UP
                7->key==Key.DOWN
                9->key in setOf(Key.DOWN,Key.RIGHT)
                4->key in setOf(Key.UP,Key.LEFT)
                8->key in setOf(Key.UP,Key.RIGHT)
                10->key in setOf(Key.UP,Key.DOWN)
                11->key in setOf(Key.LEFT,Key.RIGHT)
                else->false
            }
            // $9B controls sprite occlusion (874C), not the encounter gate (module11 C0B3).
            // Do not suppress encounters on class19 or the new upper-plane classes.
            return TerrainDecision(if(blocked||target in setOf(1,14))MovementBlock.PHYSICAL else MovementBlock.NONE,0)
        }
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
