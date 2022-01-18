package trackloader

import kotlinx.serialization.Serializable

@Serializable
data class Track(
    val info: Info,
    val track: String
)