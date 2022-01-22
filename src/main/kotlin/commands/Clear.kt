package commands

import kotlinx.serialization.Serializable

@Serializable
data class Clear(val guild: Long)