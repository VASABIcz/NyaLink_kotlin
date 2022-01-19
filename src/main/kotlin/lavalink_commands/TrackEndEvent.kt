package lavalink_commands

import kotlinx.serialization.Serializable

@Serializable
data class TrackEndEvent(val track: String, val reason: String, val guildId: Long)