package commands

import kotlinx.serialization.Serializable

@Serializable
data class Pause(val guild: Long, val pause: Boolean)