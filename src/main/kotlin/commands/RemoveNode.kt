package commands

import kotlinx.serialization.Serializable

@Serializable
data class RemoveNode(val identifier: String, val force: Boolean=false)