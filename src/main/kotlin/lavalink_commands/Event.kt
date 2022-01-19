package lavalink_commands

import kotlinx.serialization.Serializable

@Serializable
data class Event(val op: String, val type: String="")