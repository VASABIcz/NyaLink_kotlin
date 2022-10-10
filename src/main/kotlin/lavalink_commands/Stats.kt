package lavalink_commands

import kotlinx.serialization.Serializable
import kotlin.math.pow

// FIXME deficit frames are negative not sure if that means good thing
@Serializable
data class FrameStats(val sent: Int, val deficit: Int, val nulled: Int)

@Serializable
data class Stats(
    val playingPlayers: Int,
    val op: String,
    val memory: Memory,
    val players: Int,
    val cpu: Cpu,
    val uptime: Long,
    val frameStats: FrameStats? = null
) {
    val playerPenalty = playingPlayers
    val cpuPenalty = 1.05.pow((100 * cpu.systemLoad) * 10 - 10)
    val nullFramePenalty = frameStats?.nulled?.let { 1.03.pow(500 * it / 3000) * 600 - 600 } ?: 0.0
    val deficitFramePenalty = frameStats?.nulled?.let { 1.03.pow(500 * it / 3000) * 600 - 600 } ?: 0.0

    val penalty = playerPenalty + cpuPenalty + nullFramePenalty + deficitFramePenalty
}