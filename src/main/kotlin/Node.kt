import commands.AddNode
import commands.RemoveNode
import io.ktor.client.request.*
import io.ktor.client.statement.*
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.serialization.Serializable
import lavalink_commands.Stats
import mu.KotlinLogging
import trackloader.FetchResult
import java.time.Instant


@Serializable
data class NodeStatus(
    val identifier: String,
    val password: String,
    val secure: Boolean = false,
    val region: String = "europe",
    val host: String,
    val port: Int,
    val connected: Boolean,
    val players: Map<Long, PlayerStatus>,
    val loadPenalty: Double?,
    val canPlay: Boolean,
    val canFetch: Boolean
)


class FailureManagement(private val timeout: Int) {
    @set:Synchronized
    var firstFail: Instant? = null
        private set

    @set:Synchronized
    var numberOfFails = 0
        private set

    @set:Synchronized
    var lastFail: Instant? = null
        private set

    val isFailed: Boolean
        get() = numberOfFails > 2 && lastFail?.plusSeconds(timeout.toLong())?.isBefore(Instant.now()) == true

    fun addFail() {
        if (firstFail == null) {
            firstFail = Instant.now()
            numberOfFails = 1
            lastFail = Instant.now()
        } else {
            numberOfFails += 1
            lastFail = Instant.now()
        }
    }

    fun updateFail() {
        if (firstFail != null && lastFail?.plusSeconds(60 * 10)?.isBefore(Instant.now()) == true) {
            firstFail = null
            lastFail = null
            numberOfFails = 0
        }
    }
}

data class Node(val args: AddNode, val client: Client) {
    private val logger = KotlinLogging.logger { }
    private var ws: NodeWebsocket? = null
    val scope = CoroutineScope(Dispatchers.IO)
    val players = HashMap<Long, Player>()
    var statistics: Stats? = null
    val semaphore = Semaphore(4)
    private val failureManagement = FailureManagement(60 * 10)

    val canFetch: Boolean
        get() = !failureManagement.isFailed && semaphore.availablePermits > 0 && args.supportedSources.isNotEmpty()

    val canPlay: Boolean
        get() = ws?.isConnected ?: false && args.isPlayer

    init {
        if (args.isPlayer) {
            scope.launch {
                ws = NodeWebsocket(this@Node)
                ws?.connect()
            }
        }
    }

    suspend fun teardown(data: RemoveNode, force: Boolean = false) {
        client.nodes.remove(data.identifier)
        ws?.teardown()
        if (force) {
            players.values.forEach {
                it.teardown()
            }
        } else {
            players.values.forEach {
                it.move()
            }
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
            canPlay,
            players.mapValues { PlayerStatus(it.value.que.size, it.value.isPlaying) },
            statistics?.penalty,
            canPlay,
            canFetch
        )
    }

    private suspend fun fetchTrack(track: String): FetchResult? {
        val type = if (args.secure) "https" else "http"
        logger.debug("fetching track $track at node ${args.identifier}")
        failureManagement.updateFail()

        repeat(2) {
            val res: HttpResponse = client.ktorClient.get("${type}://${args.host}:${args.port}/loadtracks") {
                parameter("identifier", track)
                header("Authorization", args.password)
                header("User-Id", client.id.toString())
                header("Client-Name", "NyaLink_kotlin")
            }

            if (res.status.value != 200) {
                logger.warn("track load returned non 200 $track ${res.status.value}")
                delay(200)
                return@repeat
            }

            val parsed = client.parse<FetchResult>(res.bodyAsText())

            if (parsed?.exception != null && parsed.exception.severity != "COMMON") {
                logger.error("lavalink returned exception fetching track $parsed")
                failureManagement.addFail()
                return null
            }
            logger.debug("parsed track response $parsed")
            return parsed
        }
        failureManagement.addFail()
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