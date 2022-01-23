package trackloader

import kotlinx.serialization.Serializable

@Serializable
data class Info(
    val author: String,
    val identifier: String,
    val isSeekable: Boolean,
    val isStream: Boolean,
    val length: Long,
    val position: Long,
    val sourceName: String,
    val title: String,
    val uri: String
)