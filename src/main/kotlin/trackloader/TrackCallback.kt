package trackloader

import kotlinx.serialization.Serializable

@Serializable
data class TrackCallback(val op: String, val track: Track, val requester: Long, val channel: Long, val guild: Long)