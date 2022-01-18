package commands

import kotlinx.serialization.Serializable

@Serializable
data class SkiTo(val guild: Double, val time: Int)