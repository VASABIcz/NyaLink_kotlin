package commands

import kotlinx.serialization.Serializable

@Serializable
data class SkiTo(val guild: Long, val time: Int)