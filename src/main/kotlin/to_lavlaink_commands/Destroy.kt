package to_lavlaink_commands

import kotlinx.serialization.Serializable

@Serializable
data class Destroy(val op: String, val guildId: String)
