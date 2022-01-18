import commands.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*


@OptIn(DelicateCoroutinesApi::class)
class Client(var id: Int, var ws: DefaultWebSocketSession) {
    var nodes = HashMap<String, Node>()
    var pars = Json { ignoreUnknownKeys=true }
    val ktor_client = HttpClient(CIO) {
        install(io.ktor.client.features.websocket.WebSockets)
    }

    val available_nodes: MutableCollection<Node>
        get() = nodes.values.filter { it.available }.toMutableList()

    val best_node_players: Node?
        get() = available_nodes.minByOrNull { node -> node.players.size }

    //todo
    val best_node_fetch: Node?
        get() = available_nodes.maxByOrNull { node -> node.semaphore.availablePermits }

    val connected: Boolean
        get() = ws.isActive

    suspend fun handle(frame: Frame.Text) {
        val data = frame.readText().trim().also { println(it) }
        println("parsing $data")
        val d = parse<Command>(data)
        // TODO: 16/01/2022
        println("recived op: ${d?.op}")
        when (d?.op) {
            // nodes
            "add_node" -> parse<AddNode>(data)?.also { GlobalScope.launch { add_node(it) } }
            "remove_node" -> parse<RemoveNode>(data)?.also { GlobalScope.launch { remove_node(it) }}
            // player
            "destroy" -> parse<DestroyPlayer>(data)?.also {}
            "clear" -> parse<Clear>(data)?.also {}
            "skip" -> parse<Skip>(data)?.also {}
            "shuffle" -> parse<Shuffle>(data)?.also {}
            "revind" -> parse<Revind>(data)?.also {}
            "play" -> parse<Play>(data)?.also {}
            "pause" -> parse<Pause>(data)?.also {}
            "remove" -> parse<Remove>(data)?.also {}
            "seek" -> parse<Seek>(data)?.also {}
            "loop" -> parse<Loop>(data)?.also {}
            "skip_to" -> parse<SkiTo>(data)?.also {}
            "voice_state" -> parse<VoiceState>(data)?.also {}
        }
        println("after parse")
    }

    suspend fun listen() {
        for (x in ws.incoming) {
            println("recived from client $id")
            when (x) {
                is Frame.Text -> GlobalScope.launch { handle(x) }
            }
        }
    }

    suspend fun send(text: Any) {
        println("sending $text")
        ws.send(text.toString())
    }
    
    suspend fun resume(ws: DefaultWebSocketSession) {
        this.ws = ws
        listen()
    }

    inline fun <reified T> parse(data: String): T? {
        return try {
            pars.decodeFromString<T>(data)
        } catch (t: Throwable) {
            println("failed to parse $data")
            null
        }
    }

    suspend fun add_node(data: AddNode) {
        val node = Node(data, this)
        val x = nodes.putIfAbsent(data.identifier, node)
        if (x == null) {
            node.connect()
        }
    }

    suspend fun remove_node(data: RemoveNode) {
        println("removing node ${data.identifier}")

        val node = nodes[data.identifier]
        nodes.remove(data.identifier)
        node?.teardown()
    }
}