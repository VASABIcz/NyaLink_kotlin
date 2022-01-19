package to_lavlaink_commands

import kotlinx.serialization.Serializable

@Serializable
data class Play(
    // val endTime: String,
    val guildId: String,
    val track: String,
    // val pause: Boolean=false,
    // val startTime: Int=0,
    // val volume: Int=50
    val noReplace: Boolean,
    val op: String,
)