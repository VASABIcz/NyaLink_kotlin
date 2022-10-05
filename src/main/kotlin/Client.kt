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
import mu.KotlinLogging
import trackloader.Cache

class Client(var id: Long, var ws: DefaultWebSocketSession, val cache: Cache) {
    var nodes = HashMap<String, Node>()
    private val logger = KotlinLogging.logger { }
    private val scope = CoroutineScope(Dispatchers.Default)
    val json = Json { ignoreUnknownKeys = true }
    val ktorClient = HttpClient(CIO) {
        install(WebSockets)
    }

    val availableNodes: MutableCollection<Node>
        get() = nodes.values.filter { it.isAvailable }.toMutableList()

    val bestNodePlayers: Node?
        get() = availableNodes.minByOrNull { node -> node.players.size }

    val bestNodeFetch: Node?
        get() = availableNodes.maxByOrNull { node -> node.semaphore.availablePermits }

    val connected: Boolean
        get() = ws.isActive

    private suspend fun handle(frame: Frame.Text) {
        val data = frame.readText().trim()
        val d = parse<Command>(data)
        logger.debug("received op: ${d?.op}")
        when (d?.op) {
            // voice
            "voice_state_update" -> parse<VoiceStateUpdate>(data)?.also {
                getPlayer(it.guild_id.toLong())?.updateVoiceState(
                    it
                )
            }

            "voice_server_update" -> parse<VoiceServerUpdate>(data)?.also {
                getPlayer(it.guild_id.toLong())?.updateVoiceServer(
                    it
                )
            }
            // nodes
            "add_node" -> parse<AddNode>(data)?.also { addNode(it) }
            "remove_node" -> parse<RemoveNode>(data)?.also { nodes[it.identifier]?.teardown(it) }
            // player
            "destroy" -> parse<DestroyPlayer>(data)?.also { getPlayers()[it.guild]?.teardown() }
            "clear" -> parse<Clear>(data)?.also { getPlayers()[it.guild]?.clear() }
            "skip" -> parse<Skip>(data)?.also { getPlayer(it.guild)?.stop() }
            "shuffle" -> parse<Shuffle>(data)?.also { getPlayers()[it.guild]?.que?.shuffle() }
            "play" -> parse<Play>(data)?.also { getPlayer(it.guild)?.fetchTrack(it) }
            "pause" -> parse<Pause>(data)?.also { getPlayers()[it.guild]?.sendPause(it) }
            "seek" -> parse<Seek>(data)?.also { getPlayers()[it.guild]?.sendSeek(it) }
            "loop" -> parse<Loop>(data)?.also { getPlayers()[it.guild]?.que?.setLoop(it.type) }
            "now_playing" -> parse<NowPlaying>(data)?.also { getPlayers()[it.guild]?.sendCallback(it) }
            "revind" -> parse<Revind>(data)?.also {
                // TODO
            }

            "skip_to" -> parse<SkipTo>(data)?.also {
                // TODO
            }

            "remove" -> parse<Remove>(data)?.also { getPlayers()[it.guild]?.que?.remove(it.index) }
            else -> logger.debug("unahndled op: ${d?.op} $data")
        }
        logger.debug("after parse ${d?.op}")
    }

    suspend fun listen() {
        for (x in ws.incoming) {
            when (x) {
                is Frame.Text -> scope.launch { handle(x) }
                else -> {}
            }
        }
    }

    suspend fun send(text: Any) {
        logger.debug("sending to client $text")
        ws.send(text.toString())
    }

    suspend fun resume(ws: DefaultWebSocketSession) {
        this.ws = ws
        listen()
    }

    suspend inline fun <reified T> parse(data: String): T? {
        return utils.parse<T>(data, json)
    }

    suspend fun addNode(data: AddNode) {
        val node = Node(data, this)
        val x = nodes.putIfAbsent(data.identifier, node)
    }

    fun getPlayers(): HashMap<Long, Player> {
        val players = HashMap<Long, Player>()

        availableNodes.forEach {
            it.players.values.forEach {
                players[it.id] = it
            }
        }

        return players
    }

    fun createPlayer(id: Long): Player? {
        return bestNodePlayers?.createPlayer(id)
    }

    fun getPlayer(id: Long): Player? {
        return getPlayers()[id] ?: createPlayer(id)
    }
}