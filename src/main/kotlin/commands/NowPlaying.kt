package commands

import kotlinx.serialization.Serializable

@Serializable
data class NowPlaying(val guild: Long, val channel: Long, val requester: Long)
