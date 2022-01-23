package commands

import kotlinx.serialization.Serializable

@Serializable
data class Remove(val guild: Long, val index: Int)