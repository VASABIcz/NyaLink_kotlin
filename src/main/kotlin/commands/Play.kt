package commands

import kotlinx.serialization.Serializable

@Serializable
data class Play(val guild: Double, val name: String, val cache: Boolean=true, val requester: Double, val channel: Double)