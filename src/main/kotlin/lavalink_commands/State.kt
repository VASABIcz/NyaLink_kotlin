package lavalink_commands

import kotlinx.serialization.Serializable

@Serializable
data class State(
    val connected: Boolean,
    val position: Long = 0,
    val time: Long = 0,
    val ping: Long
)