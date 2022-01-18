package lavalink_commands

import kotlinx.serialization.Serializable

@Serializable
data class Memory(val reservable: Long, val used: Long, val free: Long, val allocated: Long) // Int is sus