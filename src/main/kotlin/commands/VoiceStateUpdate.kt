package commands

import kotlinx.serialization.Serializable


@Serializable
data class VoiceStateUpdate(
    val channel_id: String?,
    val deaf: Boolean,
    val guild_id: String,
    // val member: Member,
    val mute: Boolean,
    // val request_to_speak_timestamp: String,
    // val self_deaf: Boolean,
    // val self_mute: Boolean,
    // val self_video: Boolean,
    val session_id: String,
    // val suppress: Boolean,
    val user_id: Long

)