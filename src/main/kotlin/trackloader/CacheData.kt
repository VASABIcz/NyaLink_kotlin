package trackloader

import kotlinx.serialization.Serializable

@Serializable
data class CacheData(val tracks: List<Track>, val timestamp: Long)
