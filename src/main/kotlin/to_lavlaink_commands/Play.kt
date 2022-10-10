package to_lavlaink_commands

import kotlinx.serialization.Serializable

@Serializable
data class Play(
    val guildId: String,
    val track: String,
    val noReplace: Boolean,
    val op: String,
    val startTime: Long = 0,
    // val pause: Boolean=false,
    // val volume: Int=50
    // val endTime: String,
)