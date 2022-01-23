package to_lavlaink_commands

import kotlinx.serialization.Serializable

@Serializable
data class Pause(val op: String, val guildId: String, val pause: Boolean)