package commands

import kotlinx.serialization.Serializable

@Serializable
data class Skip(val guild: Long)