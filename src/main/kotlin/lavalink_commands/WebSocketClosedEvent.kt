package lavalink_commands

import kotlinx.serialization.Serializable

@Serializable
data class WebSocketClosedEvent(val guildId: Long, val code: Int, val reason: String, val byRemote: Boolean)