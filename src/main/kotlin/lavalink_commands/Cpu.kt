package lavalink_commands

import kotlinx.serialization.Serializable

@Serializable
data class Cpu(val cores: Int, val systemLoad: Double, val lavalinkLoad: Double)