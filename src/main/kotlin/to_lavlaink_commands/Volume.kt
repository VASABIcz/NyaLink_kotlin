package to_lavlaink_commands

import kotlinx.serialization.Serializable

@Serializable
data class Volume(val op: String, val guildId: String, val volume: Int)
