package commands

import kotlinx.serialization.Serializable

// TODO need to parse url to create category
enum class TrackSource {
    spotify,
    soundCloud,
    appleMusic,
    deezer,
    youtube
}

@Serializable
data class AddNode(
    val identifier: String,
    val password: String,
    val secure: Boolean = false,
    // TODO it could be possible to create region based players
    // region lookup from VoiceServerUpdate.endpoint
    // having more than one server LOL
    val region: String = "europe",
    val host: String,
    val port: Int,

    var isPlayer: Boolean = true,
    var supportedSources: List<TrackSource> = listOf(TrackSource.youtube, TrackSource.soundCloud)
)