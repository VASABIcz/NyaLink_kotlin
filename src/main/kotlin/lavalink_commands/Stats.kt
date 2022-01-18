package lavalink_commands

import kotlinx.serialization.Serializable

@Serializable
data class Stats (
    val playingPlayers : Int,
    val op : String,
    val memory : Memory,
    val players : Int,
    val cpu : Cpu,
    val uptime : Int
)