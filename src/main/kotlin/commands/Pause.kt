package commands

import kotlinx.serialization.Serializable

@Serializable
data class Pause(val guild: Double, val pause: Boolean)