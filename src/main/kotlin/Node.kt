import commands.AddNode
import commands.RemoveNode
import io.ktor.client.request.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import lavalink_commands.Stats
import mu.KotlinLogging
import trackloader.FetchResult


@Serializable
data class NodeStatus(
    val identifier: String,
    val password: String,
    val secure: Boolean = false,
    val region: String = "europe",
    val host: String,
    val port: Int,
    val players: Int,
    val connected: Boolean
)

class Node(val args: AddNode, val client: Client) {
    private val logger = KotlinLogging.logger { }
    private var ws: NodeWebsocket? = null
    val scope = CoroutineScope(Dispatchers.Default)
    var players = HashMap<Long, Player>()
    var statistics: Stats? = null
    val semaphore = Semaphore(4)

    init {
        scope.launch {
            ws = NodeWebsocket(this@Node)
            ws?.connect()
        }
    }

    val isAvailable: Boolean
        get() = ws?.isConnected ?: false

    suspend fun teardown(data: RemoveNode) {
        client.nodes.remove(data.identifier)
        ws?.teardown()
        players.values.forEach {
            it.teardown()
        }
        scope.cancel()
        logger.debug("teardown node ${args.identifier}")
    }

    suspend fun send(data: String) {
        ws?.send(data)
    }

    fun getNodStatus(): NodeStatus {
        return NodeStatus(
            args.identifier,
            args.password,
            args.secure,
            args.region,
            args.host,
            args.port,
            players.size,
            isAvailable
        )
    }

    private suspend fun fetchTrack(track: String): FetchResult? {
        val type = if (args.secure) "https" else "http"
        logger.debug("fetching track $track at node ${args.identifier}")

        repeat(3) {
            val res: HttpResponse = client.ktorClient.request("${type}://${args.host}:${args.port}/loadtracks") {
                method = HttpMethod.Get
                parameter("identifier", track)
                header("Authorization", args.password)
                header("User-Id", client.id.toString())
                header("Client-Name", "NyaLink_kotlin")
            }

            if (res.status.value != 200) {
                logger.debug("track load returned $track ${res.status.value}")
                return@repeat
            }
            val data = res.bodyAsText()
            val parsed = client.parse<FetchResult>(data)
            logger.debug("parsed track response $parsed")
            return parsed
        }
        return null
    }

    suspend fun limitFetch(track: String): FetchResult? {
        semaphore.withPermit {
            return fetchTrack(track)
        }
    }

    fun createPlayer(id: Long): Player {
        val player = Player(this, id)
        players[id] = player
        return player
    }
}