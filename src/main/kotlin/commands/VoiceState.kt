package commands

import kotlinx.serialization.Serializable

@Serializable
data class VoiceState(val todo: String)