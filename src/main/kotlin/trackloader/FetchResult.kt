package trackloader

import kotlinx.serialization.Serializable

@Serializable
data class FetchException(
    val message: String,
    val severity: String
)


@Serializable
data class PlaylistInfo(
    val name: String,
    val selectedTrack: Int
)

@Serializable
data class FetchResult(
    val loadType: String,
    val tracks: List<Track>,
    val exception: FetchException? = null,
    val selectedTrack: PlaylistInfo? = null
)