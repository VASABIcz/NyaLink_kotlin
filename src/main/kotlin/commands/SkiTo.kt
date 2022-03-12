package commands

import kotlinx.serialization.Serializable

@Serializable
data class SkipTo(val guild: Long, val time: Int)