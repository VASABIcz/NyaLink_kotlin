package lavalink_commands

import kotlinx.serialization.Serializable

@Serializable
class TrackStartEvent(val track: String, val guildId: Long)