import kotlinx.serialization.*

@Serializable
data class Command(val op: String)

@Serializable
data class DestroyPlayer(val guild: Int)

@Serializable
data class Clear(val guild: Int)

@Serializable
data class Skip(val guild: Int)

@Serializable
data class Shuffle(val guild: Int)

@Serializable
data class Revind(val guild: Int)

@Serializable
data class  Play(val guild: Int, val name: String, val cache: Boolean=true, val requester: Int, val channel: Int)

@Serializable
data class Pause(val guild: Int, val pause: Boolean)

@Serializable
data class Remove(val guild: Int, val index: Int)

@Serializable
data class Seek(val guild: Int, val time: Int)

@Serializable
data class Loop(val guild: Int, val loop: Int) {
    val type: LoopType
        get() {return when (loop) {
            0 -> LoopType.None
            1 -> LoopType.One
            2 -> LoopType.All
            else -> LoopType.None
        }}
}

@Serializable
data class SkiTo(val guild: Int, val time: Int)

@Serializable
data class AddNode(val identifier: String, val password: String, val secure: Boolean=false, val region: String="europe", val host: String, val port: Int)

@Serializable
data class RemoveNode(val identifier: String, val force: Boolean=false)

@Serializable
data class VoiceState(val todo: String)