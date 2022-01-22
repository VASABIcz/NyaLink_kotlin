package commands

import kotlinx.serialization.Serializable

@Serializable
data class DestroyPlayer(val guild: Long)