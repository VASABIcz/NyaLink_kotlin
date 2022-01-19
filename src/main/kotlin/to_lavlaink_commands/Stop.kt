package to_lavlaink_commands

import kotlinx.serialization.Serializable

@Serializable
data class Stop(val op: String, val guildId: Long)