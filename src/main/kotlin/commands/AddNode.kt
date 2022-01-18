package commands

import kotlinx.serialization.Serializable

@Serializable
data class AddNode(val identifier: String, val password: String, val secure: Boolean=false, val region: String="europe", val host: String, val port: Int)