package to_lavlaink_commands

import kotlinx.serialization.Serializable

@Serializable
data class Seek(val op: String, val guildId: String, val position: Long)