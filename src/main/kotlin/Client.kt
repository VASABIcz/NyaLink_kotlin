import commands.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.http.cio.websocket.*
import kotlinx.coroutines.*
import kotlinx.serialization.*
import kotlinx.serialization.json.*
import trackloader.Cache


@OptIn(DelicateCoroutinesApi::class)
class Client(var id: Long, var ws: DefaultWebSocketSession, val parser: Json, val cache: Cache) {
    var nodes = HashMap<String, Node>()
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
        val data = frame.readText().trim()
        val d = parse<Command>(data)
        // TODO: 16/01/2022
        println("recived op: ${d?.op}")
        when (d?.op) {
            // debug
            "debug" -> parse<Play>(data)?.also { GlobalScope.launch { play(it) } }
            // nodes
            "add_node" -> parse<AddNode>(data)?.also { GlobalScope.launch { add_node(it) } }
            "remove_node" -> parse<RemoveNode>(data)?.also { GlobalScope.launch { remove_node(it) }}
            // player
            "destroy" -> parse<DestroyPlayer>(data)?.also {}
            "clear" -> parse<Clear>(data)?.also {}
            "skip" -> parse<Skip>(data)?.also { get_player(it.guild)?.stop() }
            "shuffle" -> parse<Shuffle>(data)?.also {}
            "revind" -> parse<Revind>(data)?.also {}
            "play" -> parse<Play>(data)?.also { GlobalScope.launch { play(it) } }
            "pause" -> parse<Pause>(data)?.also {}
            "remove" -> parse<Remove>(data)?.also {}
            "seek" -> parse<Seek>(data)?.also {}
            "loop" -> parse<Loop>(data)?.also {}
            "skip_to" -> parse<SkiTo>(data)?.also {}
            "voice_state_update" -> parse<VoiceStateUpdate>(data)?.also { get_player(it.guild_id.toLong())?.update_voice_state(it) }
            "voice_server_update" -> parse<VoiceServerUpdate>(data)?.also { get_player(it.guild_id.toLong())?.update_voice_server(it) }
            else -> println("unahndled op: ${d?.op} $data")
        }
        println("after parse ${d?.op}")
    }

    suspend fun listen() {
        for (x in ws.incoming) {
            when (x) {
                is Frame.Text -> GlobalScope.launch { handle(x) }
            }
        }
    }

    suspend fun send(text: Any) {
        println("sending to client $text")
        ws.send(text.toString())
    }
    
    suspend fun resume(ws: DefaultWebSocketSession) {
        this.ws = ws
        listen()
    }

    inline fun <reified T> parse(data: String): T? {
        return try {
            parser.decodeFromString<T>(data)
        } catch (t: Throwable) {
            println("failed to parse $t")
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

    fun players(): HashMap<Long, Player> {
        val players = HashMap<Long, Player>()

        available_nodes.forEach() {
            it.players.values.forEach() {
                players.put(it.id, it)
            }
        }

        return players
    }

    suspend fun create_player(id: Long): Player? {
        return best_node_players?.create_player(id)
    }

    suspend fun get_player(id: Long): Player? {
        return players()[id] ?: create_player(id)
    }

    suspend fun play(data: Play) {
        get_player(data.guild)?.fetch_track(data)
    }
}