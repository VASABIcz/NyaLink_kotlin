package trackloader

import kotlinx.serialization.Serializable

@Serializable
data class FetchResult(
    val loadType: String,
    var tracks: List<Track>
)