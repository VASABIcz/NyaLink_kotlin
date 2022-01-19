package to_lavlaink_commands

import commands.VoiceServerUpdate
import kotlinx.serialization.Serializable

@Serializable
data class VoiceUpdate(val op: String, val guildId: String, val sessionId: String, val event: VoiceServerUpdate)