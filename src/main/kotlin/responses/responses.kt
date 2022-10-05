package responses

import kotlinx.serialization.Serializable
import trackloader.Track

@Serializable
data class TrackStart(
    val track: Track,
    val guildId: Long,
    val op: String
)

@Serializable
data class TrackEnd(
    val track: Track,
    val guildId: Long,
    val op: String
)