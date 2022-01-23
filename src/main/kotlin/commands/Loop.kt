package commands

import kotlinx.serialization.Serializable
import utils.LoopType

@Serializable
data class Loop(val guild: Long, val loop: Int) {
    val type: LoopType
        get() {return when (loop) {
            0 -> LoopType.None
            1 -> LoopType.One
            2 -> LoopType.All
            else -> LoopType.None
        }}
}