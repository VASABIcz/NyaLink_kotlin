package commands

import kotlinx.serialization.Serializable

@Serializable
data class Seek(val guild: Double, val time: Int)