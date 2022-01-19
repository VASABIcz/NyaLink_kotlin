package lavalink_commands

import kotlinx.serialization.Serializable

@Serializable
data class TrackStuckEvent(val track: String, val mesage: String, val cause: String, val severity: String, val guildId: Long)