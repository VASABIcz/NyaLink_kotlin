import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

class Node(val args: AddNode, val client: Client) {
    var players = HashMap<Int, Player>()
    lateinit var ws: NodeWebsocket
    val available: Boolean
        get() = ws.connected
    var stats: Stats? = null

    suspend fun connect() {
        ws = NodeWebsocket(this)
        coroutineScope { launch { ws.connect() } }
    }
    
    suspend fun teardown() {
        ws.teardown() // TODO: 16/01/2022 add move players on teardown
        println("teardown node ${args.identifier}")
    }

    suspend fun send(data: String) {
        ws.send(data)
    }

}