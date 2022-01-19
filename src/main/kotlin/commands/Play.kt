package commands

import kotlinx.serialization.Serializable

@Serializable
data class Play(val guild: Long, val name: String, val cache: Boolean=true, val requester: Double, val channel: Double)