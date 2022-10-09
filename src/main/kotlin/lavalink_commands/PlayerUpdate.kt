package lavalink_commands

import kotlinx.serialization.Serializable

@Serializable
data class PlayerUpdate(
    val guildId: Long,
    val state: State,
)