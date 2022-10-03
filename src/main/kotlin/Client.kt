import commands.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import trackloader.Cache

class Client(var id: Long, var ws: DefaultWebSocketSession, val parser: Json, val cache: Cache) {
    var nodes = HashMap<String, Node>()
    private val scope = CoroutineScope(Dispatchers.Default)
    val ktorClient = HttpClient(CIO) {
        install(WebSockets)
    }

    val availableNodes: MutableCollection<Node>
        get() = nodes.values.filter { it.available }.toMutableList()

    val bestNodePlayers: Node?
        get() = availableNodes.minByOrNull { node -> node.players.size }

    val bestNodeFetch: Node?
        get() = availableNodes.maxByOrNull { node -> node.semaphore.availablePermits }

    val connected: Boolean
        get() = ws.isActive

    suspend fun handle(frame: Frame.Text) {
        val data = frame.readText().trim()
        val d = parse<Command>(data)
        println("recived op: ${d?.op}")
        when (d?.op) {
            // voice
            "voice_state_update" -> parse<VoiceStateUpdate>(data)?.also {
                getPlayer(it.guild_id.toLong())?.update_voice_state(
                    it
                )
            }

            "voice_server_update" -> parse<VoiceServerUpdate>(data)?.also {
                getPlayer(it.guild_id.toLong())?.update_voice_server(
                    it
                )
            }
            // nodes
            "add_node" -> parse<AddNode>(data)?.also { addNode(it) }
            "remove_node" -> parse<RemoveNode>(data)?.also { nodes[it.identifier]?.teardown(it) }
            // player
            "destroy" -> parse<DestroyPlayer>(data)?.also { players()[it.guild]?.teardown() }
            "clear" -> parse<Clear>(data)?.also { players()[it.guild]?.clear() }
            "skip" -> parse<Skip>(data)?.also { getPlayer(it.guild)?.stop() }
            "shuffle" -> parse<Shuffle>(data)?.also { players()[it.guild]?.que?.shuffle() }
            "play" -> parse<Play>(data)?.also { getPlayer(it.guild)?.fetch_track(it) }
            "pause" -> parse<Pause>(data)?.also { players()[it.guild]?.send_pause(it) }
            "seek" -> parse<Seek>(data)?.also { players()[it.guild]?.send_seek(it) }
            "loop" -> parse<Loop>(data)?.also { players()[it.guild]?.que?.setLoop(it.type) }
            "now_playing" -> parse<NowPlaying>(data)?.also { players()[it.guild]?.send_callback(it) }
            "revind" -> parse<Revind>(data)?.also {}
            "skip_to" -> parse<SkipTo>(data)?.also {}
            "remove" -> parse<Remove>(data)?.also { players()[it.guild]?.que?.remove(it.index) }
            else -> println("unahndled op: ${d?.op} $data")
        }
        println("after parse ${d?.op}")
    }

    suspend fun listen() {
        for (x in ws.incoming) {
            when (x) {
                is Frame.Text -> scope.launch { handle(x) }
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

    suspend inline fun <reified T> parse(data: String): T? {
        return cache.parse<T>(data)
    }

    suspend fun addNode(data: AddNode) {
        val node = Node(data, this)
        val x = nodes.putIfAbsent(data.identifier, node)
        if (x == null) {
            node.connect()
        }
    }

    fun players(): HashMap<Long, Player> {
        val players = HashMap<Long, Player>()

        availableNodes.forEach {
            it.players.values.forEach {
                players.put(it.id, it)
            }
        }

        return players
    }

    suspend fun createPlayer(id: Long): Player? {
        return bestNodePlayers?.create_player(id)
    }

    suspend fun getPlayer(id: Long): Player? {
        return players()[id] ?: createPlayer(id)
    }
}