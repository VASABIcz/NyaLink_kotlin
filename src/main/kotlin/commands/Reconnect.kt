package commands

import kotlinx.serialization.Serializable

@Serializable
data class Reconnect(
    val channels: List<Long>,
    val op: String
)