package commands

import kotlinx.serialization.Serializable
import trackloader.TrackResult

@Serializable
data class PlayCallback(val data: TrackResult, val requester: Long, val channel: Long)