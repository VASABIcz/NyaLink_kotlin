package commands

import kotlinx.serialization.Serializable

@Serializable
data class Command(val op: String)
