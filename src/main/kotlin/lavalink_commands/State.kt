package lavalink_commands

import kotlinx.serialization.Serializable

@Serializable
data class State(
    val connected: Boolean,
    val position: Int,
    val time: Int
)