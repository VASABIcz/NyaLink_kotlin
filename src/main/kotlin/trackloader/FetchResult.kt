package trackloader

import kotlinx.serialization.Serializable

@Serializable
data class FetchResult(
    val loadType: String,
    val playlistInfo: PlaylistInfo,
    var tracks: List<Track>
)