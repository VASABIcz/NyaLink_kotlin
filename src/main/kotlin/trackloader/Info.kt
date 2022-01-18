package trackloader

import kotlinx.serialization.Serializable

@Serializable
data class Info(
    val author: String,
    val identifier: String,
    val isSeekable: Boolean,
    val isStream: Boolean,
    val length: Int,
    val position: Int,
    val sourceName: String,
    val title: String,
    val uri: String
)