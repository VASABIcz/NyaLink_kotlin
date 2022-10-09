import commands.*
import io.ktor.client.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.websocket.*
import io.ktor.websocket.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import mu.KotlinLogging
import trackloader.Cache
import trackloader.TrackLoader

class Client(var id: Long, val cache: Cache) {
    private var websockets = mutableListOf<DefaultWebSocketSession>()
    var nodes = HashMap<String, Node>()
    private val logger = KotlinLogging.logger { }
    val scope = CoroutineScope(Dispatchers.IO)
    val json = Json { ignoreUnknownKeys = true }
    val ktorClient = HttpClient(CIO) {
        install(WebSockets)
    }
    val loader = TrackLoader(this)

    val availableNodes: MutableCollection<Node>
        get() = nodes.values.filter { it.isAvailable }.toMutableList()

    val bestNodePlayers: Node?
        get() = availableNodes.minByOrNull { node -> node.players.size }

    val bestNodeFetch: Node?
        get() = availableNodes.maxByOrNull { node -> node.semaphore.availablePermits }

    val playersIds: List<Long>
        get() = availableNodes.flatMap { it.players.values.map { it.id } }
    /*
    val connected: Boolean
        get() = ws.isActive
     */

    private suspend fun handle(frame: Frame.Text) {
        val data = frame.readText().trim()
        val d = parse<Command>(data)
        logger.debug("received op: ${d?.op} $data")
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
            "skip" -> parse<Skip>(data)?.also {
                getPlayer(it.guild)?.stop()
                getPlayer(it.guild)?.doNext()
            }

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
            else -> logger.warn("unahndled op: ${d?.op} $data")
        }
        logger.debug("after parse ${d?.op}")
    }

    suspend fun listen(ws: DefaultWebSocketSession) {
        websockets.add(ws)
        try {
            for (x in ws.incoming) {
                when (x) {
                    is Frame.Text -> scope.launch {
                        println("///////////////////////// START")
                        handle(x)
                        println("///////////////////////// STOP")
                    }

                    else -> {}
                }
            }
        } catch (t: Throwable) {
            t.printStackTrace()
        }
        websockets.remove(ws)
        logger.debug("I client with id $id, disconnecting $websockets")
    }

    suspend fun send(text: Any) {
        logger.debug("sending to client $text")
        for (ws in websockets) {
            ws.send(text.toString())
        }
        logger.debug("after sending to client")
    }

    suspend fun resume(ws: DefaultWebSocketSession) {
        val x = Reconnect(playersIds, "reconnect")
        println("sending reconnect")
        ws.send(Frame.Text(Json.encodeToString(x)))
        listen(ws)
    }

    suspend inline fun <reified T> parse(data: String): T? {
        return utils.parse<T>(data, json)
    }

    suspend fun addNode(data: AddNode) {
        val node = Node(data, this)
        nodes.putIfAbsent(data.identifier, node)
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