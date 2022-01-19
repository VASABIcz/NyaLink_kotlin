package commands

import kotlinx.serialization.Serializable

@Serializable
data class VoiceServerUpdate(
    val endpoint: String,
    val guild_id: String,
    val token: String
)