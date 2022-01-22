package commands

import kotlinx.serialization.Serializable

@Serializable
data class Seek(val guild: Long, val time: Long)