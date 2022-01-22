package commands

import utils.LoopType
import kotlinx.serialization.Serializable

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