package commands

import kotlinx.serialization.Serializable

@Serializable
data class Remove(val guild: Double, val index: Int)